package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.block.ResonanceTableBlock;
import com.minecart.yunxian.mixin.DepotBehaviourAccessor;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * 共振台的方块实体。
 * <p>
 * <b>组合</b>一个 Create 的 {@link DepotBehaviour}，而不是去子类化 {@code DepotBlockEntity}：
 * 官方自己就是这么复用的（{@code StationBlockEntity} 和 {@code EjectorBlockEntity} 都组合它），
 * 而且 {@code DepotBlockEntity.depotBehaviour} 字段是<b>包私有</b>的，跨包的子类根本拿不到它。
 * <p>
 * 组合之后，「物品放上台面 / 从台面取走」的整条链路全部免费：右键交互走
 * {@code SharedDepotBlockMethods}，机械臂与传送带走 {@code addSubBehaviours} 里注册的
 * {@code DirectBeltInputBehaviour}，外部容器走 {@code depotBehaviour.itemHandler}（能力注册见
 * {@code ModCapabilities}），台面上物品的位移动画由 {@code DepotRenderer.renderItemsOf} 负责。
 * <p>
 * <b>网络那头直接用 Create 的 {@link LogisticallyLinkedBehaviour}</b>（也就是库存链接/打包机
 * 用的那一套）：它本身就是「UUID → 一组方块实体」的全局注册表，还带好了一套生命周期
 * （区块卸载/方块被拆会自动从表里掉出去、用弱引用、靠 lazyTick 续期）。关键是把
 * {@code global} 传 <b>false</b>：这样它完全不去碰 {@code Create.LOGISTICS}（见它的
 * {@code initialize}/{@code unload}/{@code destroy} 里那些 {@code if (... && global)} 分支），
 * 只剩我们要的「按 id 找同伙」这一件事，也不会有库存链接那套权限/红石语义。
 * <p>
 * 每个共振台<b>刚放下时</b>会在构造器里拿到一个随机的新 id，也就是"每放一个台子就是一个新网络"；
 * 要并进别的网络，用手持的共振过滤器右键它（见 {@code ResonanceFilterItem#useOn}）。
 */
public class ResonanceTableBlockEntity extends SmartBlockEntity {

    public DepotBehaviour depotBehaviour;
    public LogisticallyLinkedBehaviour linkBehaviour;
    public ScrollValueBehaviour maxStackSize;

    /** 上一次看到的充能状态。只在它翻转的那一刻动手，见 {@link #tick()} */
    private boolean powered;
    /** 充能瞬间冻结下来的那一份台面物品；没充能时是空的 */
    private ItemStack lockedFilter = ItemStack.EMPTY;

    public ResonanceTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONANCE_TABLE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(depotBehaviour = new DepotBehaviour(this));

        // 四个侧面的滑块，控制这张台子最多堆多少个物品。整条链路都是 Create 现成的：
        // 值框由 ScrollValueRenderer 每 tick 遍历"玩家正看着的方块实体"自动画出来，
        // 交互由 ValueSettingsInputHandler 的 RightClickBlock 监听器接，都不用我们自己挂钩子；
        // 我们只要给出行为 + 一个 Sided 的槽位变换（数值本身由 ScrollValueBehaviour 自己存 NBT）。
        // 写法和加权弹射器的 maxStackSize 逐项一致（含 0 = "*" 即不限）。
        maxStackSize = new ScrollValueBehaviour(
                CreateLang.translateDirect("create_crystal_industry.resonance_table.stack_size"),
                this, new ResonanceTableSlot())
                .between(0, 64)
                .withFormatter(i -> i == 0 ? "*" : String.valueOf(i));
        behaviours.add(maxStackSize);

        // maxStackSize 字段是包私有的，跨包只能走访问器，见 DepotBehaviourAccessor。
        //
        // 两点必须和加权弹射器对齐，否则台子根本攒不住东西：
        // 1. 把 0（即滑块上的 "*"）折成 64 再交出去。DepotBehaviour 自己内部就把 0 当 64 算
        //    （getRemainingSpace 里那句 `maxStackSize.get() == 0 ? 64 : ...`），但
        //    DepotItemHandler.getSlotLimit 会把这个值【原样】当成槽位上限返回 —— 而
        //    ScrollValueBehaviour 的初值就是 0，于是刚放下的台子槽位上限是 0，
        //    漏斗走 ItemHandlerHelper.insertItemStacked 时一件都插不进来。
        // 2. enableMerging()：不开的话 canMergeItems() 恒为 false，insertItem / isOccupied
        //    只要台子上已经有东西就一律拒绝，台子永远只装得下第一次那一下 ——
        //    滑块也就完全不起作用了。弹射器是靠这个开关才能攒到滑块设定值的。
        ((DepotBehaviourAccessor) (Object) depotBehaviour).setMaxStackSize(
                () -> maxStackSize.getValue() == 0 ? 64 : maxStackSize.getValue());
        depotBehaviour.enableMerging();

        depotBehaviour.addSubBehaviours(behaviours);
        // global=false：不注册进 Create 的全局物流管理器，纯粹当作"按 id 找同伙"的注册表用
        behaviours.add(linkBehaviour = new LogisticallyLinkedBehaviour(this, false));
    }

    /**
     * 四个侧面的值框位置。
     * <p>
     * 只放开水平四个方向：台子顶上就是放物品的地方，把值框也画到顶面会挡着。
     */
    private static class ResonanceTableSlot extends ValueBoxTransform.Sided {

        @Override
        protected Vec3 getSouthLocation() {
            // 面中央、贴着侧面外沿。y 就是 6 —— 和 Create 加权弹射器的槽位同一个高度
            return VecHelper.voxelSpace(8, 6, 15.5);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis().isHorizontal();
        }
    }

    /**
     * 红石充能时冻结当前过滤。
     * <p>
     * 只认「翻转的那一刻」：充能瞬间把台面物品<b>抄一份</b>存起来，之后不管台面上怎么变，
     * 过滤器读到的都是这一份；取消充能就丢掉快照、恢复实时。用抄本而不是记住"读哪个物品"，
     * 是因为台面上的东西可能被拿走甚至换掉，抄本才是真正意义上的"冻结"。
     * <p>
     * 用 tick 里比较缓存的标志位、而不是去挂钩子：方块那边换状态的路子有好几条
     * （玩家放、邻居更新、区块加载时读档），逐条挂容易漏；比较标志位这一条对所有路都成立。
     * 只在服务端跑 —— 客户端的 {@code lockedFilter} 由 NBT 同步过来，见 {@link #write}。
     */
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;

        boolean nowPowered = getBlockState().getValue(ResonanceTableBlock.POWERED);
        if (nowPowered == powered)
            return;

        powered = nowPowered;
        lockedFilter = nowPowered ? depotBehaviour.getHeldItemStack().copy() : ItemStack.EMPTY;
        setChanged();
        sendData();
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Powered", powered);
        tag.put("LockedFilter", lockedFilter.saveOptional(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        powered = tag.getBoolean("Powered");
        lockedFilter = ItemStack.parseOptional(registries, tag.getCompound("LockedFilter"));
    }

    /**
     * 共振过滤器要读的那一样东西：台面当前放着的物品；台子被红石充能时改读冻结下来的那一份。
     * <p>
     * 只取主物品（{@code heldItem}），不含 {@code processingOutputBuffer} 那 8 格加工缓冲 ——
     * 那是置物台给「压印机在上方加工」用的，和过滤语义无关。
     * <p>
     * 充能时快照若为空（台子空着就被充能），返回空 —— 而空台面在过滤器那边的语义是
     * <b>不加限制</b>，所以那样等于把这一格冻结成"什么都放行"。
     */
    public ItemStack getFilterSource() {
        if (getBlockState().getValue(ResonanceTableBlock.POWERED))
            return lockedFilter;
        return depotBehaviour.getHeldItemStack();
    }

    /** 本台所在网络的 id。新放下的台子各有一个随机 id，也就是默认自成一个网络。 */
    public UUID getNetwork() {
        return linkBehaviour.freqId;
    }

    /**
     * 把本台并进另一个网络。
     * <p>
     * 顺序很重要：必须<b>先</b>用旧 id 把自己从注册表里摘掉，再改 id。否则 {@code LINKS} 里那个
     * 旧 id 下的条目要等 400 tick 才自然过期，这段时间本台会<b>同时出现在两个网络里</b>，
     * 旧网络的过滤器会莫名其妙地继续读到本台的东西。
     * <p>
     * 摘掉之后再 {@code keepAlive} 一次，让它在<b>本 tick</b> 就挂到新 id 下（否则要等下一次
     * lazyTick，默认 10 tick 之后才生效）。
     */
    public void joinNetwork(UUID network) {
        if (linkBehaviour.freqId.equals(network))
            return;
        LogisticallyLinkedBehaviour.remove(linkBehaviour);
        linkBehaviour.freqId = network;
        LogisticallyLinkedBehaviour.keepAlive(linkBehaviour);
        setChanged();
        sendData();
    }
}
