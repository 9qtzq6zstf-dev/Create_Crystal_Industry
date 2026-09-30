package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

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

    public ResonanceTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONANCE_TABLE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(depotBehaviour = new DepotBehaviour(this));
        depotBehaviour.addSubBehaviours(behaviours);
        // global=false：不注册进 Create 的全局物流管理器，纯粹当作"按 id 找同伙"的注册表用
        behaviours.add(linkBehaviour = new LogisticallyLinkedBehaviour(this, false));
    }

    /**
     * 共振过滤器要读的那一样东西：台面当前放着的物品。
     * <p>
     * 只取主物品（{@code heldItem}），不含 {@code processingOutputBuffer} 那 8 格加工缓冲 ——
     * 那是置物台给「压印机在上方加工」用的，和过滤语义无关。
     */
    public ItemStack getFilterSource() {
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
