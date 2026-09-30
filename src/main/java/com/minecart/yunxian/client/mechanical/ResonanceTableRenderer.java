package com.minecart.yunxian.client.mechanical;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.content.logistics.depot.DepotRenderer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * 把台面上的物品画出来，直接转发给 Create 的置物台渲染器。
 * <p>
 * {@code DepotRenderer} 本身的泛型写死成 {@code DepotBlockEntity}，不能直接注册给我们的方块实体；
 * 但它的核心 {@code DepotRenderer.renderItemsOf(...)} 是 {@code public static}、第一个参数就是
 * {@code SmartBlockEntity}，所以照样能复用 —— Create 官方的 {@code StationRenderer} 与
 * {@code EjectorRenderer} 也是这么转发的。
 */
public class ResonanceTableRenderer extends SafeBlockEntityRenderer<ResonanceTableBlockEntity> {

    public ResonanceTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(ResonanceTableBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource bufferSource, int light, int overlay) {
        DepotRenderer.renderItemsOf(be, partialTicks, ms, bufferSource, light, overlay, be.depotBehaviour);
    }
}
