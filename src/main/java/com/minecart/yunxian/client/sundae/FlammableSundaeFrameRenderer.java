package com.minecart.yunxian.client.sundae;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.item.FlammableSundaeItem;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.event.RenderItemInFrameEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 展示框里的可燃冰圣代改用平面贴图。
 * <p>
 * <b>为什么非要另开一个钩子</b>：原版展示框渲染物品用的是 {@code ItemDisplayContext.FIXED}，
 * 而机械动力的置物台/传送带/桌布用的<b>也是</b> {@code FIXED}（见
 * {@code DepotRenderer#renderItem} 等）。物品模型 JSON 只能按视角分模型，
 * 分不出"被谁拿着 FIXED"——想让台面上是 3D、展柜里是平面，就只能在这条渲染路径上下手。
 * <p>
 * 钩子选 NeoForge 的 {@link RenderItemInFrameEvent}（和 {@link com.minecart.yunxian.client.echo.EchoSpyglassFrameRenderer}
 * 同一个），不用 mixin：取消掉原版那一趟（它拿 FIXED 视角 = 3D 模型），换成自己用平面模型重画一遍。
 * <p>
 * <b>两个必须自己补的细节</b>：
 * <ul>
 *   <li><b>0.5 的缩放</b>：原版那步 {@code poseStack.scale(0.5F, …)} 写在事件<b>之后</b>，
 *       取消掉就没人做了，不补的话平面贴图会大一圈；</li>
 *   <li><b>发光展示框的满亮</b>：原版用 {@code getLightVal}(私有) 在发光框上换成
 *       {@link LightTexture#FULL_BRIGHT}，这里照抄那条规则（发光框是单独一种实体类型）。</li>
 * </ul>
 * 视角仍传 {@code FIXED}——平面模型自己的 {@code display.fixed}（{@code item/generated} 那套
 * {@code rotation [0,180,0]}）正是原版平面物品挂进展示框时的朝向，这样才和普通物品一模一样。
 */
public final class FlammableSundaeFrameRenderer {

    /**
     * 平面模型，按 "standalone" 变体单独注册一份（见 {@link #register()} 的调用点）。
     * <p>
     * 它虽然已经作为物品模型的 {@code base} 被烘焙过，但那是别人的子模型，
     * {@code ModelManager} 里查不到——所以要在 {@code ModelEvent.RegisterAdditional} 里
     * 单独登记，路径 = {@code models/} 下的相对路径（文件在 {@code models/item/} 下）。
     * 变体必须是 "standalone"：NeoForge 1.21.1 对 sideload 模型强制这个要求，写 "inventory" 会直接抛。
     */
    public static final ModelResourceLocation FLAT_MODEL =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "item/flammable_sundae_flat"),
                    "standalone");

    private FlammableSundaeFrameRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FlammableSundaeFrameRenderer::onRenderItemInFrame);
    }

    public static void onRenderItemInFrame(RenderItemInFrameEvent event) {
        if (!(event.getItemStack().getItem() instanceof FlammableSundaeItem)) {
            return; // 绝大多数物品都不是圣代，先短路
        }
        // 挡掉原版那一趟：它走 FIXED 视角，会拿到 3D 方块模型
        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        BakedModel flat = mc.getModelManager().getModel(FLAT_MODEL);
        int light = event.getItemFrameEntity().getType() == EntityType.GLOW_ITEM_FRAME
                ? LightTexture.FULL_BRIGHT
                : event.getPackedLight();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        mc.getItemRenderer().render(
                event.getItemStack(),
                ItemDisplayContext.FIXED,
                false,
                poseStack,
                event.getMultiBufferSource(),
                light,
                OverlayTexture.NO_OVERLAY,
                flat);
        poseStack.popPose();
    }
}
