package com.minecart.yunxian.blockentity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.block.AcceleratorBlock;
import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public class AcceleratorBlockEntity extends BlockEntity implements IEnergyStorage, IHaveGoggleInformation {
    private static final int MAX_ENERGY = 10_000;
    private static final int MAX_RECEIVE = 100;
    private static final int ENERGY_COST_PER_OPERATION = 100;

    /**
     * 一次成功的催生<b>最多</b>对 6 个方向各施加一次 randomTick（= Direction.values().length）。
     * 实际只落在会吃随机刻的邻格上（见 {@code YunxianAdvancements#acceleratedRandomTick}），
     * 所以这是上限；护目镜按上限显示，即"满配"时的倍率。
     */
    private static final float RANDOM_TICKS_PER_TICK = Direction.values().length;

    /**
     * 相连催生器网络拓扑的版本号。任一方块被放下/拆掉、所在区块加载/卸载都会 +1；
     * 每个方块实体缓存一份网络成员，靠它判断缓存是否过期。
     */
    private static long topologyVersion;

    /**
     * 相连的催生器共享一个电量池：网络成员 = 六向相邻的 {@link AcceleratorBlockEntity} 连通域。
     * 电量仍按格存在各自的 {@link #energyStorage} 里，对外求和、对内统一扣，不再在相邻方块之间搬运电力。
     */
    private record Network(long version, List<AcceleratorBlockEntity> members) {
    }

    private int tickCounter;

    /**
     * 单格储能。读档时整体替换（带初始电量），
     * 不能走 {@code receiveEnergy}——那会被构造时的 maxReceive=100 截断。
     */
    private EnergyStorage energyStorage;

    /** 当前网络缓存；版本号与 {@link #topologyVersion} 不一致时重建 */
    @Nullable
    private Network cachedNetwork;

    /** 「催生器挨着原版紫水晶母岩 / 作物运转」两个成就的一次性门闩（不持久化：重登后重扫一次而已） */
    private boolean sawVanillaAmethyst;
    private boolean sawCrops;

    public AcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCELERATOR.get(), pos, state);
        energyStorage = new EnergyStorage(MAX_ENERGY, MAX_RECEIVE, MAX_ENERGY);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 两次催生之间的 tick 数由配置决定（默认 1 = 每 tick 一次）；电力按「每次催生」结算
        tickCounter++;
        if (tickCounter % ModConfig.Common.acceleratorIntervalTicks() != 0) {
            return;
        }

        int stored = getEnergyStored();

        // 完全没有能量：关闭并返回
        if (stored <= 0) {
            if (state.getValue(AcceleratorBlock.POWERED)) {
                level.setBlock(pos, state.setValue(AcceleratorBlock.POWERED, false), 3);
            }
            return;
        }

        // 残电不足一次操作：直接扣光到 0，不执行催生
        if (stored < ENERGY_COST_PER_OPERATION) {
            drainNetwork(stored);
            if (state.getValue(AcceleratorBlock.POWERED)) {
                level.setBlock(pos, state.setValue(AcceleratorBlock.POWERED, false), 3);
            }
            setChanged();
            return;
        }

        // 足额：正常消耗并催生
        if (!state.getValue(AcceleratorBlock.POWERED)) {
            level.setBlock(pos, state.setValue(AcceleratorBlock.POWERED, true), 3);
            // 从"停着"变成"转着"的那一刻：第一次通电运转
            YunxianAdvancements.awardNear(serverLevel, pos, YunxianAdvancements.ACCEL_RUNNING);
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            // 走 acceleratedRandomTick 而不是直接 randomTick：生长监听器靠它区分"催出来的"与"自然长的"；
            // 邻格不吃随机刻（空气、石头等）时它自己会跳过，与原版随机刻同一套判定
            YunxianAdvancements.acceleratedRandomTick(serverLevel, neighborPos);
        }

        // 邻面有没有"催生器也管用"的东西（原版紫水晶母岩、作物/树苗）。
        // 各自只在真的发出去过之后才停止扫描——发成就很便宜，但没必要每 tick 重复扫
        if (!sawVanillaAmethyst) {
            sawVanillaAmethyst = YunxianAdvancements.awardForVanillaAmethyst(serverLevel, pos);
        }
        if (!sawCrops) {
            sawCrops = YunxianAdvancements.awardForCrops(serverLevel, pos);
        }

        drainNetwork(ENERGY_COST_PER_OPERATION);
        setChanged();
    }

    /**
     * 把相连网络里所有催生器的电量加在一起。
     * <p>
     * 客户端没有同步过来的电量，直接退回本地存储；服务端才走 BFS 缓存。
     */
    @Override
    public int getEnergyStored() {
        if (level == null || level.isClientSide) {
            return energyStorage.getEnergyStored();
        }
        int total = 0;
        for (AcceleratorBlockEntity member : network().members()) {
            if (member.isRemoved()) {
                continue;
            }
            total += member.energyStorage.getEnergyStored();
        }
        return total;
    }

    /** 网络的总容量：每格 {@link #MAX_ENERGY}。 */
    @Override
    public int getMaxEnergyStored() {
        if (level == null || level.isClientSide) {
            return energyStorage.getMaxEnergyStored();
        }
        int count = 0;
        for (AcceleratorBlockEntity member : network().members()) {
            if (!member.isRemoved()) {
                count++;
            }
        }
        return MAX_ENERGY * count;
    }

    /**
     * 外部输入的电按 BFS 顺序摊到网络里各格（自身优先）。
     * <p>
     * 每个成员仍受自身 {@code maxReceive}（{@link #MAX_RECEIVE}）限制，
     * 所以一次调用最多能收 {@code 100 × 网络格数}——网络越大，输入口越大；
     * 不能像以前那样把整网单次输入也卡在 {@link #MAX_RECEIVE}，否则一个连接器
     * 无论连了多少格，都只能按一个催生器的速度灌电。
     */
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (maxReceive <= 0) {
            return 0;
        }
        if (level == null || level.isClientSide) {
            int received = energyStorage.receiveEnergy(maxReceive, simulate);
            if (!simulate && received > 0) {
                setChanged();
            }
            return received;
        }

        int remaining = maxReceive;
        int accepted = 0;
        for (AcceleratorBlockEntity member : network().members()) {
            if (remaining <= 0) {
                break;
            }
            if (member.isRemoved()) {
                continue;
            }
            int received = member.energyStorage.receiveEnergy(remaining, simulate);
            if (received > 0) {
                remaining -= received;
                accepted += received;
                if (!simulate) {
                    member.setChanged();
                }
            }
        }
        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    /** 从网络里扣电（自身优先），每扣到一格就标脏，拆分/合并时各格电量仍然各自正确。 */
    private void drainNetwork(int amount) {
        int remaining = amount;
        for (AcceleratorBlockEntity member : network().members()) {
            if (remaining <= 0) {
                break;
            }
            if (member.isRemoved()) {
                continue;
            }
            int drained = member.energyStorage.extractEnergy(remaining, false);
            if (drained > 0) {
                remaining -= drained;
                member.setChanged();
            }
        }
    }

    /** 取当前网络；缓存按 {@link #topologyVersion} 失效，重建后把同一份缓存分给所有成员。 */
    private Network network() {
        if (level == null || level.isClientSide || isRemoved()) {
            return new Network(topologyVersion, List.of(this));
        }
        if (cachedNetwork != null && cachedNetwork.version() == topologyVersion) {
            return cachedNetwork;
        }

        List<AcceleratorBlockEntity> members = collectNetwork();
        Network network = new Network(topologyVersion, members);
        for (AcceleratorBlockEntity member : members) {
            member.cachedNetwork = network;
        }
        return network;
    }

    /**
     * 六向 BFS 收集相连的催生器。
     * <p>
     * 只走已经加载的区块：{@link Level#getBlockEntity} 在未加载区块上会强制加载，
     * 大型网络的 BFS 绝不能因此把世界边界整片拉进来。
     */
    private List<AcceleratorBlockEntity> collectNetwork() {
        List<AcceleratorBlockEntity> members = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        members.add(this);
        seen.add(worldPosition);
        queue.add(worldPosition);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!seen.add(next) || !level.hasChunkAt(next)) {
                    continue;
                }
                if (level.getBlockEntity(next) instanceof AcceleratorBlockEntity other && !other.isRemoved()) {
                    members.add(other);
                    queue.add(next);
                }
            }
        }
        return members;
    }

    /** 网络拓扑变了：任一方块放下/拆掉、区块加载/卸载都调用它，让所有缓存失效。 */
    public static void invalidateNetworkTopology() {
        topologyVersion++;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            invalidateNetworkTopology();
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            invalidateNetworkTopology();
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 不要在 CreateLang.translate 里传自家 key —— 它会被强制加 "create." 前缀，
        // 改用 Component.translatable 原样解析自家命名空间
        boolean running = getBlockState().getValue(AcceleratorBlock.POWERED);

        // 运行中每个吃随机刻的邻面、每 interval tick 必定触发一次 randomTick（确定值，非期望值）；
        // 这里按 6 个邻面都合格算，是上限
        int interval = ModConfig.Common.acceleratorIntervalTicks();
        float per20 = running
                ? Math.round(RANDOM_TICKS_PER_TICK * 20f / interval * 10f) / 10f
                : 0f;

        // 状态：工作中 / 已停止
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.status_label")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.translatable(running
                                ? "create_crystal_industry.goggles.status.working"
                                : "create_crystal_industry.goggles.status.idle")
                        .withStyle(ChatFormatting.WHITE))
                .forGoggles(tooltip, 1);

        // 工作速度：平均每 20 tick 施加的随机刻数量
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.work_speed_label")
                        .withStyle(ChatFormatting.GRAY))
                .add(CreateLang.number(per20)
                        .style(ChatFormatting.WHITE))
                .add(Component.literal(" "))
                .add(Component.translatable("create_crystal_industry.goggles.work_speed_unit")
                        .withStyle(ChatFormatting.DARK_GRAY))
                .forGoggles(tooltip, 1);

        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) {
            // EnergyStorage 的构造会把初始电量夹到 [0, capacity]；这样读档不会被 maxReceive 截成 100
            energyStorage = new EnergyStorage(MAX_ENERGY, MAX_RECEIVE, MAX_ENERGY, tag.getInt("Energy"));
        }
    }

    @Nullable
    public IEnergyStorage getEnergyCapability(@Nullable Direction side) {
        return this;
    }
}
