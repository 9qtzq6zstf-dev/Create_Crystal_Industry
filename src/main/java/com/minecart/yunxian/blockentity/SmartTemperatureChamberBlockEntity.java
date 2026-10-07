package com.minecart.yunxian.blockentity;

import java.util.ArrayList;
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
 * <p>
 * <b>共享容量</b>：同一个 Y 上、四向相邻的一批温控室，会被切成若干块<b>矩形</b>
 * （反复取面积最大的全满矩形，剩下的再切，见 {@link SmartTemperatureChamberBlock#groupAt}），
 * 每块各自算一组。垂直方向不算，形状不必是一个矩形——3x3 缺一角会切出它里面那块 2x3。
 * 整组共享一只 {@code 台数 x 1000 mB} 的大罐：
 * <ul>
 *   <li>够不够烧看的是<b>整组</b>的总量，所以组里任意一格补到料，整组当 tick 一起点着；</li>
 *   <li>管子接到任意一格都能把整组灌满（自己先涨、溢出给同组其它格，见 {@link #fill}）；</li>
 *   <li>但<b>不互相匀料</b>：不烧的时候每格就是自己原来那些，不会自己摊平；
 *       燃料只在被烧掉的那一刻离开某一格（见 {@link #drainFromGroup}）。</li>
 * </ul>
 * 每台各按自己的周期烧，所以 N 台就是 N 倍的消耗速率：总续航与单台一样（都是 1600 tick），
 * 多出来的只是"能一口气装 N 桶、少补 N-1 次料"。
 */
public class SmartTemperatureChamberBlockEntity extends BlockEntity implements IFluidHandler, IHaveGoggleInformation {

    /** 罐容量：正好一桶，玩家拿桶补一次就知道该补多少 */
    public static final int CAPACITY = 1000;

    /** 一个消耗周期扣多少 mB */
    public static final int DRAIN_AMOUNT = 5;

    /** 两个消耗周期之间隔多少 tick */
    public static final int DRAIN_INTERVAL_TICKS = 8;

    /**
     * 组信息（成员列表与外接矩形）缓存多少 tick。
     * <p>
     * 只缓存<b>结构</b>：成员是哪些、总共多少容量。燃料量每 tick 现加（成员就那几个，加一遍很便宜），
     * 所以补进去一桶会立刻点亮，不会因为缓存而迟一个周期。结构变动最迟这么久之后被看到。
     */
    private static final int GROUP_CACHE_TICKS = DRAIN_INTERVAL_TICKS;

    private final FluidTank tank;

    /**
     * 距下一个消耗周期还有多少 tick。刻意<b>不落盘</b>：区块重载最坏也就多扣一个周期，
     * 不值得为它往存档里加一个字段。
     */
    private int drainCooldown;

    /** 结构缓存：哪些格子算一组、这一组一共能装多少。过期即重算，见 {@link #group()} */
    @Nullable
    private Group groupCache;
    private int groupCacheTtl;

    /**
     * 组内总燃料与总容量。<b>这两个数是同步给客户端的</b>（写进方块实体包），
     * 护目镜在客户端只能读这两个——客户端没法自己扫一遍全组去加总。
     * 服务端每 tick 重新写一遍，变了才发包。
     */
    private int sharedFuel;
    private int sharedCapacity = CAPACITY;

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
        // （比如结构调整过之后，两者对不上）
        groupCache = null;
        groupCacheTtl = 0;
        // 只在服务端重算：客户端读的是同步过来的 sharedFuel，扫描既白费又可能撞上还没就绪的区块
        if (level != null && !level.isClientSide) {
            updateHeatState(isFueled());
        }
    }

    /** 这一<b>组</b>还够不够再烧一个周期（不是本格自己的罐） */
    public boolean isFueled() {
        return groupFuel() >= DRAIN_AMOUNT;
    }

    /** 本格自己罐里的量 */
    public int fluidAmount() {
        return tank.getFluidAmount();
    }

    /**
     * 每 tick 调一次（由方块那边的 ticker 转进来，只会是服务端）。
     * <p>
     * 顺序有讲究：先把这一组的总量算出来、同步给客户端并顶掉方块状态，
     * 再结算自己这一份消耗。没燃料时把倒计时清零，这样补进去的液体<b>立刻</b>开始烧，
     * 而不是先干等一整个周期。
     */
    public void serverTick() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (groupCacheTtl > 0) {
            groupCacheTtl--;
        }

        Group group = group();
        int fuel = groupFuel();
        boolean fueled = fuel >= DRAIN_AMOUNT;

        if (fuel != sharedFuel || group.capacity() != sharedCapacity) {
            sharedFuel = fuel;
            sharedCapacity = group.capacity();
            setChanged();
            // 组内任何一格补料/烧掉，这一格显示的总数都要跟着变，所以这里自己发包
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        updateHeatState(fueled);

        if (!fueled) {
            drainCooldown = 0;
            return;
        }
        // 先自减再判：从 8 一路减到 0 才扣一次，正好 8 tick 一个周期
        if (--drainCooldown > 0) {
            return;
        }
        drainCooldown = DRAIN_INTERVAL_TICKS;
        // 组内每一台都按自己的周期烧，所以 N 台就是 N 倍的消耗速率——
        // 总续航与单台一样，多出来的只是"能一口气装 N 桶、少补 N-1 次料"
        drainFromGroup(DRAIN_AMOUNT, FluidAction.EXECUTE);
    }

    /**
     * 自己这一组：连通域切出来的一块矩形，见
     * {@link SmartTemperatureChamberBlock#groupAt}。整块连通域切不出任何东西时退化成自己一格。
     * <p>
     * 结果按 {@link #GROUP_CACHE_TICKS} 缓存。只缓存结构，容量由成员数直接算出，
     * 燃料量不缓存（见 {@link #groupFuel()}）。
     */
    private Group group() {
        if (groupCache == null || groupCacheTtl <= 0) {
            groupCache = computeGroup();
            groupCacheTtl = GROUP_CACHE_TICKS;
        }
        return groupCache;
    }

    /**
     * 分组规则在 {@link SmartTemperatureChamberBlock#groupAt} 里，与客户端的连接材质共用同一个——
     * 那边要按"只有共享容量的几台之间才连"来画纹理，两边算出不一样的结果就会对不上。
     */
    private Group computeGroup() {
        List<BlockPos> members = SmartTemperatureChamberBlock.groupAt(level, worldPosition);
        return new Group(members, members.size() * CAPACITY);
    }

    /**
     * 组内总燃料：把各格罐里的量加起来。
     * <p>
     * <b>每 tick 现算，不跟结构一起缓存</b>——这样往任意一格补一桶，整组当 tick 就会点亮。
     * 成员就那么几个（上限 {@link SmartTemperatureChamberBlock#MAX_COMPONENT_CELLS}），
     * 加一遍是几十次数组访问，很便宜。
     */
    private int groupFuel() {
        int total = 0;
        for (BlockPos pos : group().members()) {
            if (level != null
                    && level.getBlockEntity(pos) instanceof SmartTemperatureChamberBlockEntity member) {
                total += member.tank.getFluidAmount();
            }
        }
        return total;
    }

    /**
     * 从<b>整组</b>里扣掉一份消耗：先扣自己那一格，不够再按成员顺序从同组的其它格补齐。
     * <p>
     * 这是与"互相匀料"最大的区别——燃料只在<b>被烧掉</b>的那一刻才离开某一格，
     * 平时谁也不往谁那儿挪：不烧的时候每格的液位就是自己原来的样子，不会自己摊平。
     */
    private FluidStack drainFromGroup(int amount, FluidAction action) {
        FluidStack total = FluidStack.EMPTY;
        int remaining = amount;
        for (BlockPos pos : group().members()) {
            if (remaining <= 0) {
                break;
            }
            if (level != null
                    && level.getBlockEntity(pos) instanceof SmartTemperatureChamberBlockEntity member) {
                FluidStack got = member.tank.drain(remaining, action);
                if (got.isEmpty()) {
                    continue;
                }
                remaining -= got.getAmount();
                total = total.isEmpty()
                        ? got
                        : total.copyWithAmount(total.getAmount() + got.getAmount());
            }
        }
        return total;
    }

    /** 结构缓存：成员列表 + 这一组的总容量 */
    private record Group(List<BlockPos> members, int capacity) {
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
    private void updateHeatState(boolean fueled) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(SmartTemperatureChamberBlock.HEAT_LEVEL)) {
            return;
        }
        HeatLevel desired = fueled ? HeatLevel.SEETHING : HeatLevel.NONE;
        if (state.getValue(SmartTemperatureChamberBlock.HEAT_LEVEL) == desired) {
            return;
        }
        level.setBlockAndUpdate(worldPosition,
                state.setValue(SmartTemperatureChamberBlock.HEAT_LEVEL, desired));
        // 刚点着 / 刚烧完都要告诉工作盆一声，不然上面那台空转着的机器得等它自己的周期才反应
        SmartTemperatureChamberBlock.notifyBasinAbove(level, worldPosition);
    }

    /**
     * 比较器读液位：空组 0，其余按比例给 1–15（与 {@code FluidTankBuddingBlockEntity} 同一个算法，
     * 空与非空要能区分开）。液位不是方块状态，所以现查方块实体。
     * <p>
     * 读的是<b>组</b>的总量：同组每一台给出的信号因此是一样的，比较器摆在哪一格都读得出"这一组还剩多少"。
     * 比较器每 tick 读一次，读的是服务端的真实值（{@code sharedFuel} 每 tick 由服务端重算），
     * 不像护目镜那样依赖同步包。
     */
    public static int comparatorSignal(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof SmartTemperatureChamberBlockEntity chamber)
                || chamber.sharedFuel <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(15.0F * chamber.sharedFuel / chamber.sharedCapacity));
    }

    // ==================== 流体能力 ====================

    /*
     * 对外这一格就是<b>整组那一只大罐</b>：容量报整组的、存量报整组的总和，灌与抽也都在整组上做。
     * 管道、储罐表、注液器看到的因此都是一只 N x 1000 mB 的罐，而不是"某一块方块的 1000 mB"。
     * 只有真正的存放还是各格各放（这样结构拆分/合并不会丢料），那属于实现细节。
     */

    @Override
    public int getTanks() {
        return tank.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tankIndex) {
        return sharedFuel <= 0
                ? FluidStack.EMPTY
                : new FluidStack(ModFluids.FLAMMABLE_ICE_SLURRY.get(), sharedFuel);
    }

    @Override
    public int getTankCapacity(int tankIndex) {
        return sharedCapacity;
    }

    @Override
    public boolean isFluidValid(int tankIndex, FluidStack stack) {
        return tank.isFluidValid(tankIndex, stack);
    }

    /**
     * 灌料：<b>自己先灌满，溢出的再依次灌同组的其它格</b>。
     * <p>
     * 这一步是"共享容量"真正成立的地方——一根管子接在这组任意一台上，都能把这组灌到
     * {@code 台数 x 1000}，而不是灌满 1000 就把管子憋住。管口那一格先涨，看着也自然。
     * <p>
     * 只有真往世界里添料时才动别的格子，平时不烧就不挪（与"互相匀料"的区别见
     * {@link #drainFromGroup}）。
     */
    @Override
    public int fill(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()) {
            return 0;
        }
        int accepted = 0;
        int remaining = resource.getAmount();
        // 自己排最前，然后按成员顺序；group().members() 本身不含顺序保证，所以显式拼一份
        List<BlockPos> order = new ArrayList<>(group().members().size());
        order.add(worldPosition);
        for (BlockPos pos : group().members()) {
            if (!pos.equals(worldPosition)) {
                order.add(pos);
            }
        }
        for (BlockPos pos : order) {
            if (remaining <= 0) {
                break;
            }
            if (level != null
                    && level.getBlockEntity(pos) instanceof SmartTemperatureChamberBlockEntity member) {
                int got = member.tank.fill(resource.copyWithAmount(remaining), action);
                accepted += got;
                remaining -= got;
            }
        }
        return accepted;
    }

    /** 只认可燃冰沙（与罐的校验同一个判据，按 FluidType 比，静止/流动都算） */
    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        if (resource.isEmpty()
                || resource.getFluidType() != ModFluids.FLAMMABLE_ICE_SLURRY_TYPE.get()) {
            return FluidStack.EMPTY;
        }
        return drainFromGroup(resource.getAmount(), action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return drainFromGroup(maxDrain, action);
    }

    // ==================== 存档与同步 ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
        tag.putInt("SharedFuel", sharedFuel);
        tag.putInt("SharedCapacity", sharedCapacity);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
        // 客户端要靠这两个数画护目镜；服务端下一 tick 就会自己重算覆盖掉
        sharedFuel = tag.getInt("SharedFuel");
        sharedCapacity = tag.contains("SharedCapacity") ? tag.getInt("SharedCapacity") : CAPACITY;
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
            // 组内总量变了，护目镜上显示的也要跟着变
            sharedFuel = groupFuel();
            updateHeatState(isFueled());
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
        // 报的是<b>整组</b>的数：组里每一台看到的都一样，玩家才不用挨个去问
        int amount = sharedFuel;
        int capacity = sharedCapacity;
        boolean fueled = amount >= DRAIN_AMOUNT;
        // 罐只收一种流体，所以直接报流体类型的名字——
        // 不能再问"本格罐里那份"的名字了：总量可能全在隔壁几格里，本格的罐是空的
        Component fluidLabel = ModFluids.FLAMMABLE_ICE_SLURRY_TYPE.get().getDescription();

        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.temperature_chamber.fluid",
                                fluidLabel, amount, capacity)
                        .withStyle(fueled ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);
        if (capacity > CAPACITY) {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.goggles.temperature_chamber.shared",
                                    capacity / CAPACITY, capacity)
                            .withStyle(ChatFormatting.GRAY))
                    .forGoggles(tooltip, 1);
        }
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.temperature_chamber.fluid_cost",
                                DRAIN_AMOUNT, DRAIN_INTERVAL_TICKS)
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);

        // 有燃料时不额外报一句"正在供热"：这台机器的定位是"把温度控制成配方要的样子"，
        // 加热只是其中一半，写"供热"会把话说过头（绿字液位本身已经说明它在正常工作）
        if (!fueled) {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.goggles.temperature_chamber.fluid_too_little")
                            .withStyle(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }
        return true;
    }
}
