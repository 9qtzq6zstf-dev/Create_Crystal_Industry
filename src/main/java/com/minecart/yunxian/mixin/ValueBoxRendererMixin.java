package com.minecart.yunxian.mixin;

import com.minecart.yunxian.client.sundae.FlammableSundaeFrameRenderer;
import com.minecart.yunxian.item.FlammableSundaeItem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 可燃冰圣代在机械动力「数值框」里改用平面贴图。
 * <p>
 * 圣代的物品模型是 {@code neoforge:separate_transforms}：{@code fixed} 视角用方块模型
 * （放在桌布、桌面、置物台、传送带上时立着），其余视角用 {@code item/generated} 的平面贴图。
 * 但机械动力的数值框（方块上的过滤槽、工厂仪表的过滤槽、红石链接上显示的物品）渲染物品时
 * <b>也是</b> FIXED 视角 —— 见 {@code ValueBoxRenderer#renderItemIntoValueBox} 第 36 行，
 * 于是圣代在槽位里也立成了方块。
 * <p>
 * 这里只把圣代换成平面模型 {@link FlammableSundaeFrameRenderer#FLAT_MODEL}（展示框用的那份，
 * 自带 {@code fixed} 半格缩放），其余物品完全不受影响。桌布/桌面那条路
 * （{@code DepotRenderer}/{@code TableClothRenderer}）不经过数值框，依旧渲染方块。
 * <p>
 * <b>不要</b>改成拦截 {@code ItemRenderer#render} 按视角统一压平：那样会连桌布上的方块一起压掉。
 */
@Mixin(ValueBoxRenderer.class)
public abstract class ValueBoxRendererMixin {

    @Inject(method = "renderItemIntoValueBox", at = @At("HEAD"), cancellable = true)
    private static void yunxian$renderSundaeFlatInValueBox(ItemStack filter, PoseStack ms, MultiBufferSource buffer,
            int light, int overlay, CallbackInfo ci) {
        if (!(filter.getItem() instanceof FlammableSundaeItem))
            return;

        Minecraft mc = Minecraft.getInstance();
        BakedModel flat = mc.getModelManager().getModel(FlammableSundaeFrameRenderer.FLAT_MODEL);

        // 缩放与 z 偏移照抄 ValueBoxRenderer 里「非方块物品」那一支：
        // item/generated 的 display.fixed 是 scale 1，只有再乘上 .5 才和其它平面物品一样大。
        ms.pushPose();
        ms.scale(.5f + 1 / 64f, .5f + 1 / 64f, .5f + 1 / 64f);
        ms.translate(0, 0, -.15f);
        mc.getItemRenderer().render(filter, ItemDisplayContext.FIXED, false, ms, buffer, light, overlay, flat);
        ms.popPose();
        ci.cancel();
    }
}
