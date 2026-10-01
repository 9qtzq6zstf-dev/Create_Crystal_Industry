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
 * 框是紧贴方块形状的长方体，整个框<b>共用同一个颜色</b>；那个颜色随时间在玫瑰石英色带上连续滑动，
 * 所以是整体的渐变，不闪也不跳档。见 {@link #outline} 与 {@link #rampColor}。
 */
public final class ResonanceNetworkOutlineRenderer {

    /**
     * 整框颜色在色带上滑动的速度（每 tick 走过的色带比例）。
     * <p>
     * 整个框<b>共用同一个颜色</b>，颜色随时间在这条玫瑰石英色带上连续滑动，不跳档。
     * {@code 0.01} ≈ 100 tick（5 秒）走完一整圈色带，相邻两色之间约 0.8 秒。
     * 想更快就把这个数调大（它和周期成反比），设成 0 就是固定不动的单色框。
     */
    private static final float COLOR_CYCLE_PER_TICK = 0.01f;

    /**
     * 玫瑰石英色带，从深到浅，末位回到首位循环。
     * 采样自本模组的玫瑰石英晶簇贴图与 Create 玫瑰石英物品的本体色，不是凭色调的。
     */
    private static final int[] RAMP = {
            0x6B1F3B,   // 最深的酒红
            0x9D3964,   // Create 玫瑰石英物品本体色
            0xCB4872,   // 晶簇中间调
            0xF45974,   // 亮玫瑰红（最偏红石的一档）
            0xFF8E8A,   // 浅珊瑚
            0xFFD5BD,   // 高光米白
    };

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

        int color = rampColor(AnimationTickHolder.getTicks() * COLOR_CYCLE_PER_TICK);

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
     * 画一个框：<b>紧贴这个方块自己形状</b>的长方体，整个框共用同一个颜色，
     * 那个颜色是按时间在色带上取的，见 {@link #rampColor}。
     * <p>
     * 框的大小取 {@code getShape(...).bounds()} —— Create 的机器把 {@code getShape} 写得和模型
     * 对得上，所以这就是"贴着建模"的那一圈。漏斗这类还会伸到格子外面（出料口在 z=18），
     * 紧贴之后框会跟着探出去，这是对的。<b>两件不要改成的事：</b>
     * <ol>
     *   <li><b>别用整格 {@code new AABB(pos)}</b> —— 机器基本都填不满一格，框会明显偏大，
     *       而且完全看不出方块长什么样。</li>
     *   <li><b>别按 {@code toAabbs()} 逐个子盒去描</b> —— 工作盆、漏斗这种复杂建模的碰撞形状
     *       是十几个子盒拼的，一格上会叠出十几条框，糊成一团。要的是它们的<b>并集长方体</b>，
     *       也就是 {@code bounds()}。</li>
     * </ol>
     * 形状为空的方块（纯靠方块实体渲染、没有模型的）回落到整格，免得什么都不画。
     * {@code inflate(-1/128f)} 是往里收一丝，避免和方块自己的面 z-fighting —— Create
     * 画网络成员时也是这么收的。
     * <p>
     * <b>必须用 {@code showAABB}（catnip 的 {@code AABBOutline}），不要拆成 12 条
     * {@code showLine}。</b>踩过的两个坑都在这里：
     * <ol>
     *   <li>拆成 12 条线时，<b>垂直于地面的那四条明显偏暗</b>。{@code showAABB} 走的是
     *       {@code AABBOutline}，它认 {@code disableLineNormals()} 并据此统一法线；
     *       而 {@code showLine} 那条路（{@code LineOutline}）几乎不碰法线，实体着色器就会
     *       按每个四边形自己的朝向做方向性着色，竖直的棱自然和水平的不一样亮。</li>
     *   <li>拆成 12 条线时，三条棱在角点处的那一小块会互相重叠，而描边是不写深度的，
     *       谁压在上面取决于绘制顺序，角点上就一直闪。一个 {@code showAABB} 没这个问题。</li>
     * </ol>
     * 这两条也是 Create 自己画物流网络时用 {@code showAABB} 而不是拼线的原因。
     */
    private static void outline(Level level, BlockPos pos, Object keyPrefix, int color) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir())
            return;   // 网络表里可能有刚被拆掉、还没过期的条目

        VoxelShape shape = state.getShape(level, pos);
        // bounds() 拿的是形状的并集长方体：不能按 toAabbs() 逐个子盒描（工作盆/漏斗那种复杂建模
        // 会叠出十几条框），也不能用整格（机器基本填不满一格）。
        AABB box = (shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos)).inflate(-1 / 128f);

        Outliner.getInstance()
                .showAABB(Pair.of(keyPrefix, pos), box, 2)
                .lineWidth(1 / 32f)
                .disableLineNormals()
                .colored(color);
    }

    /**
     * 在玫瑰石英色带上取一个颜色。{@code t} 会先绕到 0..1，所以色带首尾相接、循环不断。
     * <p>
     * 相邻两个档位之间做线性插值，所以是<b>连续滑动</b>而不是一档一档地跳。
     */
    private static int rampColor(float t) {
        t = t - (float) Math.floor(t);

        float scaled = t * RAMP.length;
        int i = (int) scaled % RAMP.length;
        int j = (i + 1) % RAMP.length;
        float f = scaled - (float) Math.floor(scaled);

        return lerpChannel(RAMP[i] >> 16 & 255, RAMP[j] >> 16 & 255, f) << 16
                | lerpChannel(RAMP[i] >> 8 & 255, RAMP[j] >> 8 & 255, f) << 8
                | lerpChannel(RAMP[i] & 255, RAMP[j] & 255, f);
    }

    private static int lerpChannel(int a, int b, float f) {
        return Math.max(0, Math.min(255, Math.round(a + (b - a) * f)));
    }
}
