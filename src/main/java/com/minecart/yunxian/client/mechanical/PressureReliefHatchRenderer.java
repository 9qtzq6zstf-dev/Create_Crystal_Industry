package com.minecart.yunxian.client.mechanical;

import com.minecart.yunxian.blockentity.PressureReliefHatchBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * 泄压舱口的方块实体渲染：只干一件事——把侧面过滤槽里的过滤物品画出来。
 * <p>
 * 方块本体（安装板 + 网罩）走普通的方块模型，不归这里管；这里只管那一件悬在网罩侧面的
 * 过滤物品。方法与吸尘器完全一致（{@link FilteringRenderer#renderOnBlockEntity}），
 * 差别只是吸尘器那边还要顺带画扇叶与吸入幻影。
 */
public class PressureReliefHatchRenderer implements BlockEntityRenderer<PressureReliefHatchBlockEntity> {

    public PressureReliefHatchRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(PressureReliefHatchBlockEntity be, float partialTicks, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);
    }
}
