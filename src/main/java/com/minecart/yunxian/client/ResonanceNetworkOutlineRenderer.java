package com.minecart.yunxian.client;

import com.minecart.yunxian.item.ResonanceFilterItem;
import com.minecart.yunxian.item.ResonanceTableItem;
import com.minecart.yunxian.registry.ModDataComponents;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.outliner.Outliner;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.UUID;

/**
 * 手持「已接入网络」的共振过滤器或共振台时，把<b>同一个网络里的东西</b>全部用线框描出来：
 * <ul>
 *   <li>网络里的每一张共振台；</li>
 *   <li>过滤槽里插着本网络共振过滤器的每一个元件（漏斗 / 溜槽 / 工作盆……）。</li>
 * </ul>
 * 前者来自 Create 的网络注册表，后者来自 {@link ResonanceFilterHolderTracker}。
 * <p>
 * 用的是 Catnip（Create 的公共库）自带的 {@link Outliner} —— 也就是 Create 给机械手选交互点
 * 和物流网络用的同一套。这里只负责<b>每 tick 喂一次</b>，渲染由 Catnip 的客户端事件做
 * （已反汇编确认：{@code PonderClient.onRenderWorld} 无条件调 {@code Outliner.renderOutlines}）。
 * <p>
 * 配色照抄 Create 对物流网络的做法：两档颜色交替闪，一眼能看出"这几样东西是一伙的"。
 * 每一格只描一条紧贴方块形状的长方体，见 {@link #outline}。
 */
public final class ResonanceNetworkOutlineRenderer {

    /** 网络成员两档交替色，取自 Create 的 LogisticallyLinkedClientHandler */
    private static final int COLOR_A = 0x708DAD;
    private static final int COLOR_B = 0x90ADCD;

    /** 超出这个距离的成员不描（和 Create 一样），免得半个世界都在闪 */
    private static final int MAX_DISTANCE = 64;

    /** Outliner 的槽位键前缀：共振台与"插着过滤器的元件"各用一套，互不覆盖 */
    private static final Object TABLE_KEY = "create_crystal_industry:resonance_table";
    private static final Object HOLDER_KEY = "create_crystal_industry:resonance_filter_holder";

    private ResonanceNetworkOutlineRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ResonanceNetworkOutlineRenderer::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null)
            return;

        UUID network = networkOfHeld(player.getMainHandItem());
        if (network == null)
            return;

        int color = AnimationTickHolder.getTicks() % 16 < 8 ? COLOR_A : COLOR_B;

        // 网络里的共振台。客户端读的是 CLIENT_LINKS 那张表，由各台子 lazyTick 里的 keepAlive 维护
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(network, false, true)) {
            // 核一次 id：客户端那张表里的旧条目要等 400 tick 才自然过期
            // （Create 的 remove() 只清服务端那张表），不查的话刚并网的台子会在旧网络里多闪 20 秒
            if (!link.freqId.equals(network))
                continue;

            SmartBlockEntity be = link.blockEntity;
            // 只描当前关卡：装进装置的方块实体活在一个虚拟关卡里，它的坐标在真实世界里没有意义
            if (be.getLevel() != level)
                continue;
            if (!player.canInteractWithBlock(be.getBlockPos(), MAX_DISTANCE))
                continue;

            outline(level, be.getBlockPos(), TABLE_KEY, color);
        }

        // 过滤槽里插着本网络共振过滤器的元件（漏斗/溜槽/工作盆……）
        for (FilteringBehaviour filtering : ResonanceFilterHolderTracker.getForNetwork(network)) {
            // 同上：装置里的方块实体不描
            if (filtering.blockEntity.getLevel() != level)
                continue;

            BlockPos pos = filtering.getPos();
            if (!player.canInteractWithBlock(pos, MAX_DISTANCE))
                continue;

            outline(level, pos, HOLDER_KEY, color);
        }
    }

    /**
     * 手持的若是「已接入网络」的共振过滤器或共振台，返回它的网络 id，否则 null。
     * 没接入网络的物品什么都不描 —— 它本来就不属于任何网络。
     */
    private static UUID networkOfHeld(ItemStack held) {
        if (!(held.getItem() instanceof ResonanceFilterItem) && !(held.getItem() instanceof ResonanceTableItem))
            return null;
        return held.get(ModDataComponents.RESONANCE_NETWORK.get());
    }

    /**
     * 每格只描<b>一条</b>框：紧贴这个方块自己形状的长方体。
     * <p>
     * 取 {@code getShape(...).bounds()} —— Create 的机器把 {@code getShape} 写得和模型对得上，
     * 所以这就是"贴着建模"的那一圈。漏斗这类还会伸到格子外面（出料口在 z=18），
     * 紧贴之后框会跟着探出去，这是对的。
     * <p>
     * <b>两件不要改成的事：</b>
     * <ol>
     *   <li><b>别用整格 {@code new AABB(pos)}</b> —— 机器基本都填不满一格，框会明显偏大，
     *       而且完全看不出方块长什么样。</li>
     *   <li><b>别按 {@code toAabbs()} 逐个子盒去描</b> —— 工作盆、漏斗这种复杂建模的碰撞形状
     *       是十几个子盒拼的，一格上会叠出十几条框，糊成一团。要的是它们的<b>并集长方体</b>，
     *       也就是 {@code bounds()}。</li>
     * </ol>
     * 形状为空的方块（纯靠方块实体渲染、没有模型的）回落到整格，免得什么都不画。
     * <p>
     * {@code inflate(-1/128f)} 是往里收一丝，避免和方块自己的面 z-fighting —— Create
     * 画网络成员时也是这么收的。
     */
    private static void outline(Level level, BlockPos pos, Object keyPrefix, int color) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir())
            return;   // 网络表里可能有刚被拆掉、还没过期的条目

        VoxelShape shape = state.getShape(level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);

        Outliner.getInstance()
                .showAABB(Pair.of(keyPrefix, pos), box.inflate(-1 / 128f), 2)
                .lineWidth(1 / 32f)
                .disableLineNormals()
                .colored(color);
    }
}
