package com.minecart.yunxian.client.mechanical;

import com.minecart.yunxian.blockentity.SmartPressureReliefHatchBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * 智能泄压舱口的方块实体渲染：与 {@link PressureReliefHatchRenderer} 做的是同一件事——
 * 把侧面过滤槽里那件过滤物品画出来。
 * <p>
 * 之所以要单独一个类而不是复用：方块实体渲染器是按类型注册的，而
 * {@code BlockEntityRenderer<T>} 的泛型不协变，父类那个
 * {@code BlockEntityRenderer<PressureReliefHatchBlockEntity>} 喂不进智能舱口的类型槽。
 */
public class SmartPressureReliefHatchRenderer implements BlockEntityRenderer<SmartPressureReliefHatchBlockEntity> {

    public SmartPressureReliefHatchRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(SmartPressureReliefHatchBlockEntity be, float partialTicks, PoseStack ms,
                       MultiBufferSource buffer, int light, int overlay) {
        FilteringRenderer.renderOnBlockEntity(be, partialTicks, ms, buffer, light, overlay);
    }
}
