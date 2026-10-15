package com.minecart.yunxian.client.mechanical;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.depot.DepotRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * 把台面上的物品画出来，直接转发给 Create 的置物台渲染器。
 * <p>
 * {@code DepotRenderer} 本身的泛型写死成 {@code DepotBlockEntity}，不能直接注册给我们的方块实体；
 * 但它的核心 {@code DepotRenderer.renderItemsOf(...)} 是 {@code public static}、第一个参数就是
 * {@code SmartBlockEntity}，所以照样能复用 —— Create 官方的 {@code StationRenderer} 与
 * {@code EjectorRenderer} 也是这么转发的。
 * <p>
 * 另外在台子上方画网络名字牌，见 {@link #renderNetworkName}。
 */
public class ResonanceTableRenderer extends SafeBlockEntityRenderer<ResonanceTableBlockEntity> {

    /**
     * 名字牌缩放。{@code 0.025} 是原生名牌（{@code EntityRenderer#renderNameTag}）用的尺度，
     * 也是 Create 的 {@code SmartBlockEntityRenderer#renderNameplateOnHover} 用的那个。
     */
    private static final float NAME_SCALE = 0.025f;

    /** 名字牌的可见距离（格）。原生名牌是 64 格，这里跟着它。 */
    private static final double NAME_VISIBLE_DISTANCE_SQ = 64 * 64;

    public ResonanceTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(ResonanceTableBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource bufferSource, int light, int overlay) {
        DepotRenderer.renderItemsOf(be, partialTicks, ms, bufferSource, light, overlay, be.depotBehaviour);
        renderNetworkName(be, ms, bufferSource, light);
    }

    /**
     * 台子上方的网络名字牌（没有名字就不画）。
     * <p>
     * 画法逐行照抄原生名牌与 Create 的 {@code SmartBlockEntityRenderer#renderNameplateOnHover}：
     * 朝向相机、{@code 0.025} 缩放、两遍 —— {@code SEE_THROUGH} 那遍带半透明底，
     * 隔着方块也看得见；{@code NORMAL} 那遍是正面那层实心字。
     * <p>
     * 与 Create 那个方法只有两点不同，都是故意的：
     * <ul>
     *   <li><b>不要求玩家正看着它</b> —— 名字是给整片基地看的（Create 那个是悬停才出现的提示）；</li>
     *   <li>多一道视锥剔除：字画在方块上方，只按方块剔除的话，方块刚滑出屏幕字就先没了
     *       （渲染包围盒那边也一起撑开了，见 {@code ResonanceTableBlockEntity#createRenderBoundingBox()}）。</li>
     * </ul>
     */
    private void renderNetworkName(ResonanceTableBlockEntity be, PoseStack ms, MultiBufferSource buffer, int light) {
        String name = be.getNetworkName();
        if (name.isEmpty() || be.isVirtual())
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null)
            return;

        Vec3 labelPos = Vec3.atCenterOf(be.getBlockPos())
                .add(0, ResonanceTableBlockEntity.NAME_TAG_HEIGHT, 0);
        if (mc.player.distanceToSqr(labelPos) > NAME_VISIBLE_DISTANCE_SQ)
            return;
        if (shouldCullItem(labelPos, be.getLevel()))
            return;

        ms.pushPose();
        ms.translate(0.5, ResonanceTableBlockEntity.NAME_TAG_HEIGHT, 0.5);
        ms.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        ms.scale(NAME_SCALE, -NAME_SCALE, NAME_SCALE);

        Matrix4f matrix = ms.last().pose();
        Font font = mc.font;
        float x = -font.width(name) / 2f;
        int background = (int) (mc.options.getBackgroundOpacity(0.25f) * 255.0f) << 24;
        font.drawInBatch(name, x, 0, 553648127, false, matrix, buffer, Font.DisplayMode.SEE_THROUGH, background, light);
        font.drawInBatch(name, x, 0, -1, false, matrix, buffer, Font.DisplayMode.NORMAL, 0, light);
        ms.popPose();
    }
}
