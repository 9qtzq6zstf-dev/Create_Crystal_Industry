package com.minecart.yunxian.blockentity.budding;

import java.util.List;

import com.minecart.yunxian.registry.ModBlockEntities;
import com.minecart.yunxian.util.BuddingGrowthHelper;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * 弧光石母岩的能量方块实体：内部 1 M FE 的「电池 + 生长燃料箱」。
 * <p>
 * 与流体罐（{@code FluidTankBuddingBlockEntity}）同一套路，只是燃料从流体换成 FE：能力注册见
 * {@code ModCapabilities}（Create Addition 的电缆、任何认 NeoForge 能量能力的机器都能灌进来），
 * 每次成功生长扣 {@link #COST_PER_GROWTH}。
 * <p>
 * <b>只进不出</b>：{@link #canExtract()} 为 false、{@code extractEnergy} 恒返回 0。
 * 它是「电变物质」的转换器，不是电池——要存电有本模组的水晶电池。想让 FE 抽得出来，
 * 把这两处改成正常实现即可。
 * <p>
 * 客户端同步与熔岩罐同理（方块实体数据要自己顶一次方块更新才会发给客户端）：
 * 护目镜读的是同步过来的电量。
 */
public class ArclightBuddingBlockEntity extends BlockEntity implements IHaveGoggleInformation, IEnergyStorage {

    /** 内部缓存：1 M FE */
    public static final int CAPACITY = 1_000_000;

    /** 每次成功生长消耗的 FE */
    public static final int COST_PER_GROWTH = 10_000;

    /** 每 tick 最多从外部吸收多少：正好是一次生长的量，即「一秒能充满一次生长」 */
    public static final int MAX_RECEIVE = 10_000;

    private int energy;

    public ArclightBuddingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCLIGHT_BUDDING.get(), pos, state);
    }

    /**
     * 尝试为一次生长扣掉一份 FE。
     *
     * @return 电量不足 {@link #COST_PER_GROWTH} 时返回 false，表示放弃这次生长
     */
    public boolean tryConsumeGrowthCost() {
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (energy < COST_PER_GROWTH) {
            return false;
        }
        setEnergy(energy - COST_PER_GROWTH);
        return true;
    }

    /**
     * 把电量一次灌满。给「失活母岩被雷劈中 → 变成弧光石母岩」用（见 {@code budding/LightningActivation}）：
     * 闪电是白给的启动资金，所以新生的母岩直接满电，不用玩家先接电缆。
     * <p>
     * 别拿 {@link #receiveEnergy} 循环一百次去凑：那条路每次最多收 {@link #MAX_RECEIVE}，
     * 而 {@code MAX_RECEIVE} 是「每 tick 从外部吸多少」的限流，管的是外面灌进来的电，
     * 不该管我们自己给的这一下。
     */
    public void setEnergyToFull() {
        setEnergy(CAPACITY);
    }

    /** 改电量并落盘 + 同步给客户端（护目镜在客户端读这个值） */
    private void setEnergy(int value) {
        int clamped = Math.clamp(value, 0, CAPACITY);
        if (clamped == energy) {
            return;
        }
        energy = clamped;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ==================== 能量能力 ====================

    /** 供 {@code ModCapabilities} 注册能力用（与电力催生器同一约定） */
    public IEnergyStorage getEnergyCapability(@Nullable Direction side) {
        return this;
    }

    @Override
    public int receiveEnergy(int toReceive, boolean simulate) {
        if (toReceive <= 0 || !canReceive()) {
            return 0;
        }
        int accepted = Math.min(Math.min(toReceive, MAX_RECEIVE), CAPACITY - energy);
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            setEnergy(energy + accepted);
        }
        return accepted;
    }

    /** 只进不出：见类注释 */
    @Override
    public int extractEnergy(int toExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return energy;
    }

    @Override
    public int getMaxEnergyStored() {
        return CAPACITY;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    // ==================== 存档与同步 ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // 直接写字段而不是走 setEnergy：读档/读包期间不该再发方块更新
        energy = Math.clamp(tag.getInt("Energy"), 0, CAPACITY);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ==================== 护目镜 ====================

    /**
     * 本家族不挑维度与群系，所以不调 {@code appendGrowthEnvironment}；
     * 哪天给它加了环境要求，记得在这里补一行（那条链专用 BE 得各自记着，共用的 BE 才会代劳）。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level != null) {
            BuddingGrowthHelper.appendGrowthTooltip(level, worldPosition, tooltip);
        }
        boolean enough = energy >= COST_PER_GROWTH;
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.arclight_budding.energy", energy, CAPACITY)
                        .withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.arclight_budding.cost", COST_PER_GROWTH)
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);
        if (!enough) {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.arclight_budding.too_little")
                            .withStyle(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }
        return true;
    }
}
