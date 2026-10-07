package com.minecart.yunxian.blockentity;

import java.util.List;
import java.util.function.Predicate;

import com.minecart.yunxian.block.SmartTemperatureChamberBlock;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.minecart.yunxian.registry.ModFluids;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * 智能温控室的方块实体：一只可燃冰沙的罐子，烧它给上方的工作盆供热。
 * <p>
 * <b>罐是主人，方块状态是投影</b>。罐里有没有足够烧一个周期的量，直接决定方块状态上的
 * {@code blaze} 是不是 {@link HeatLevel#SEETHING}（工作盆只读那一位，见
 * {@link SmartTemperatureChamberBlock}）。两边不会各说各话，因为状态永远是从罐现算出来的。
 * <p>
 * 写法照 {@code FluidTankBuddingBlockEntity}：罐写在匿名子类里，好让<b>所有</b>写入路径
 * （管道、桶、消耗）都走同一个 {@code onContentsChanged}——漏一次同步就会让护目镜显示旧数值。
 * <p>
 * <b>消耗节奏</b>：每 {@link #DRAIN_INTERVAL_TICKS} tick 扣 {@link #DRAIN_AMOUNT} mB
 * （≈0.625 mB/tick，一桶 1000 mB 约 80 秒）。这两个数字照抄本模组既有的燃料登记
 * {@code data/create_crystal_industry/data_maps/fluid/liquid_fuel.json} 里可燃冰沙那一笔
 * （{@code amountConsumedPerTick: 5} / {@code burnTime: 8}），让这块方块与"拿它当烈焰人燃烧室燃料"
 * 的经济性对得上。<b>注意那个文件是给 Create: Liquid Fuel 看的，只在那位软依赖存在时生效</b>，
 * 本方块不依赖它、自己实现扣费。
 * <p>
 * 只要罐里还有够烧一个周期的量就<b>持续</b>扣，不管上方工作盆有没有在炼——与烈焰人燃烧室
 * 烧燃料的行为一致。罐里不足一个周期时把余量清空并转冷，不卡零头。
 */
public class SmartTemperatureChamberBlockEntity extends BlockEntity implements IFluidHandler, IHaveGoggleInformation {

    /** 罐容量：正好一桶，玩家拿桶补一次就知道该补多少 */
    public static final int CAPACITY = 1000;

    /** 一个消耗周期扣多少 mB */
    public static final int DRAIN_AMOUNT = 5;

    /** 两个消耗周期之间隔多少 tick */
    public static final int DRAIN_INTERVAL_TICKS = 8;

    /**
     * 相邻温控室之间、每 tick 每一对最多匀过去多少 mB。
     * <p>
     * 取得比消耗速率（0.625 mB/tick）大两个数量级，是为了让"一桶倒给一排"这种情形
     * 一两秒内就摊平；真正稳态时两边一样多，这条通路一次都不会走（见 {@link #shareFuelWithNeighbors}）。
     */
    private static final int MAX_SHARE_PER_TICK = 50;

    private final FluidTank tank;

    /**
     * 距下一个消耗周期还有多少 tick。刻意<b>不落盘</b>：区块重载最坏也就多扣一个周期，
     * 不值得为它往存档里加一个字段。
     */
    private int drainCooldown;

    public SmartTemperatureChamberBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_TEMPERATURE_CHAMBER.get(), pos, state);
        this.tank = new FluidTank(CAPACITY, acceptedFluid()) {
            @Override
            protected void onContentsChanged() {
                onTankChanged();
            }
        };
    }

    /**
     * 认哪种流体：按 <b>FluidType</b> 判，所以静止与流动两份变体都算
     * （它们共用 {@code FLAMMABLE_ICE_SLURRY_TYPE}），管道推来的不管是哪一份都能进罐。
     * <p>
     * 写成静态方法取的是「调用时才查注册表」——流体类型注册晚于方块，
     * 在构造器里取出来会撞上未绑定的注册表。
     */
    private static Predicate<FluidStack> acceptedFluid() {
        return stack -> stack.getFluidType() == ModFluids.FLAMMABLE_ICE_SLURRY_TYPE.get();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // 读档后按罐里的实际情况重算一次：状态本来就存在区块里，这里只是兜底
        // （比如配置/存档被动过之后，两者对不上）
        updateHeatState();
    }

    /** 罐里够不够再烧一个周期 */
    public boolean isFueled() {
        return tank.getFluidAmount() >= DRAIN_AMOUNT;
    }

    public int fluidAmount() {
        return tank.getFluidAmount();
    }

    /**
     * 每 tick 调一次（由方块那边的 ticker 转进来，只会是服务端）。
     * <p>
     * 没燃料时顺手把倒计时清零，这样补进去的液体<b>立刻</b>开始烧，而不是先干等一整个周期。
     */
    public void serverTick() {
        if (level == null || level.isClientSide) {
            return;
        }
        // 先跟邻居匀一次，再结算自己的消耗：这样一桶倒给一排时，最远的那台同 tick 就能点着
        shareFuelWithNeighbors();

        if (!isFueled()) {
            drainCooldown = 0;
            return;
        }
        // 先自减再判：从 8 一路减到 0 才扣一次，正好 8 tick 一个周期
        if (--drainCooldown > 0) {
            return;
        }
        drainCooldown = DRAIN_INTERVAL_TICKS;
        // EXECUTE 会经 onContentsChanged 落盘、同步给客户端，并顺手把方块状态顶到当前档位
        tank.drain(DRAIN_AMOUNT, FluidAction.EXECUTE);
    }

    /**
     * 相邻温控室互相匀燃料：存量多的一端往少的一端推，直到两边一样多。
     * <p>
     * 效果上，连在一起的一排就是<b>一只大罐子</b>——总燃料是各罐之和，而每台都在按同一速率烧，
     * 所以烧完的时间和不连时一模一样（N 台各 1000 mB，就多出 N 倍的缓冲、少补 N-1 次料）。
     * 一根管子接到这排的任意一台，整排都会跟着点上。
     * <p>
     * 写法照 {@code AcceleratorBlockEntity#transmitToNeighbors}（相邻催生器互传电力），
     * 三处关键判定都在注释里标了出来，别随手改：
     * <ul>
     *   <li><b>只由多的一端推</b>：同一时刻一对里不可能两端都"更多"，所以每 tick 每对至多传一次，
     *       不会两边对推；</li>
     *   <li><b>传差值的一半</b>：防过冲。一次全推过去会让两边角色调个个儿，下一 tick 又推回来，
     *       来回震荡；</li>
     *   <li><b>再压一个每 tick 上限</b>：让"一桶倒给一排"摊平得快一点。</li>
     * </ul>
     * 稳态时两边等量，第一条判定就把它挡掉了——这条通路一次都不走，所以不会有每 tick 的
     * 落盘与同步开销（{@code drain}/{@code fill} 都会经 {@code onContentsChanged}）。
     */
    private void shareFuelWithNeighbors() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (Direction dir : Direction.values()) {
            if (!(level.getBlockEntity(worldPosition.relative(dir))
                    instanceof SmartTemperatureChamberBlockEntity other) || other.isRemoved()) {
                continue;
            }

            int mine = fluidAmount();
            int theirs = other.fluidAmount();
            if (mine <= theirs) {
                continue;
            }

            int room = CAPACITY - theirs;
            if (room <= 0) {
                continue;
            }

            int amount = Math.min(Math.min(mine, room),
                    Math.min(MAX_SHARE_PER_TICK, (mine - theirs) / 2));
            if (amount <= 0) {
                continue;
            }

            FluidStack moved = drain(amount, FluidAction.EXECUTE);
            if (moved.isEmpty()) {
                continue;
            }
            int accepted = other.fill(moved, FluidAction.EXECUTE);
            if (accepted < moved.getAmount()) {
                // 理论上进不去（缺口是刚算的、两边又是同一种流体），真发生就还回罐里，别让燃料蒸发
                fill(moved.copyWithAmount(moved.getAmount() - accepted), FluidAction.EXECUTE);
            }
        }
    }

    /**
     * 把方块状态上的热量档位顶成与罐里实时一致。只在真的变了才写：
     * {@code setBlock} 会顶一次光照重算与客户端更新，白写就是白发。
     * <p>
     * 用 {@code setBlockAndUpdate}（标志位 3）而不是 {@code UPDATE_CLIENTS}——我们同时改了
     * 亮度（烧着 15、不烧 0），而亮度只有走完整更新才会重算。Create 自己改燃烧室档位
     * （{@code BlazeBurnerBlockEntity#setBlockHeat}）用的也是这一个。
     * <p>
     * 不怕把罐写坏：只换状态不换方块时原版会保留方块实体，也不会重入 {@code onContentsChanged}。
     */
    private void updateHeatState() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(SmartTemperatureChamberBlock.HEAT_LEVEL)) {
            return;
        }
        HeatLevel desired = isFueled() ? HeatLevel.SEETHING : HeatLevel.NONE;
        if (state.getValue(SmartTemperatureChamberBlock.HEAT_LEVEL) == desired) {
            return;
        }
        level.setBlockAndUpdate(worldPosition,
                state.setValue(SmartTemperatureChamberBlock.HEAT_LEVEL, desired));
        // 刚点着 / 刚烧完都要告诉工作盆一声，不然上面那台空转着的机器得等它自己的周期才反应
        SmartTemperatureChamberBlock.notifyBasinAbove(level, worldPosition);
    }

    /**
     * 比较器读液位：空罐 0，其余按比例给 1–15（与 {@code FluidTankBuddingBlockEntity} 同一个算法，
     * 空与非空要能区分开）。液位不是方块状态，所以现查方块实体。
     */
    public static int comparatorSignal(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SmartTemperatureChamberBlockEntity chamber)) {
            return 0;
        }
        int amount = chamber.fluidAmount();
        if (amount <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(15.0F * amount / CAPACITY));
    }

    // ==================== 流体能力 ====================

    @Override
    public int getTanks() {
        return tank.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tankIndex) {
        return tank.getFluidInTank(tankIndex);
    }

    @Override
    public int getTankCapacity(int tankIndex) {
        return tank.getTankCapacity(tankIndex);
    }

    @Override
    public boolean isFluidValid(int tankIndex, FluidStack stack) {
        return tank.isFluidValid(tankIndex, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return tank.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return tank.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return tank.drain(maxDrain, action);
    }

    // ==================== 存档与同步 ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
    }

    /** 方块实体包带上整罐流体：护目镜浮窗在客户端读它（罐只有一桶，整份发不心疼） */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * 罐内容变的统一收口处（管道、桶、消耗都从这里过）。
     * <p>
     * 两件事缺一不可：{@link #updateHeatState()} 是给方块状态与上方工作盆的，
     * {@code sendBlockUpdated} 才是护目镜要读的液位。只 {@code setChanged()} 的话护目镜会一直显示旧数值。
     * <p>
     * 读取同步包走的是 {@code loadAdditional}（直接写字段、不经这里），所以这条判断平时不会命中；
     * 留着是兜底。
     */
    private void onTankChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            updateHeatState();
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ==================== 护目镜 ====================

    /**
     * 两行：液位（够烧绿、不够红），以及不够时把原因说明白。
     * <p>
     * 流体的名字报<b>罐里那一份的实名</b>（{@code FluidStack#getHoverName}），空罐时退回流体类型
     * 自己的名字——罐只收一种流体，但空着的时候总得有个名字可显示。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        int amount = fluidAmount();
        boolean fueled = isFueled();
        Component fluidLabel = amount > 0
                ? tank.getFluid().getHoverName()
                : ModFluids.FLAMMABLE_ICE_SLURRY_TYPE.get().getDescription();

        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.temperature_chamber.fluid",
                                fluidLabel, amount, CAPACITY)
                        .withStyle(fueled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.temperature_chamber.fluid_cost",
                                DRAIN_AMOUNT, DRAIN_INTERVAL_TICKS)
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);

        if (fueled) {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.goggles.temperature_chamber.heated")
                            .withStyle(ChatFormatting.GREEN))
                    .forGoggles(tooltip, 1);
        } else {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.goggles.temperature_chamber.fluid_too_little")
                            .withStyle(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }
        return true;
    }
}
