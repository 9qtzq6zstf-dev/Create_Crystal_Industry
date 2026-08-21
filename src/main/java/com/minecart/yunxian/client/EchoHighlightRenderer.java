package com.minecart.yunxian.client;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.item.EchoSpyglassItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.List;

@EventBusSubscriber(modid = Yunxian.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class EchoHighlightRenderer {

    private EchoHighlightRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null
                || !player.isUsingItem()
                || !(player.getUseItem().getItem() instanceof EchoSpyglassItem)) {
            return;
        }

        List<BlockPos> ores = EchoHighlightClient.positionsFor(mc.level.dimension());
        if (ores.isEmpty()) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();

        poseStack.pushPose();
        poseStack.translate(-camera.x, -camera.y, -camera.z);

        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.ECHO_ORE_OVERLAY);

        RenderSystem.disableDepthTest();

        for (BlockPos pos : ores) {
            AABB box = new AABB(pos);
            if (event.getFrustum() != null && !event.getFrustum().isVisible(box)) {
                continue;
            }
            LevelRenderer.renderLineBox(poseStack, consumer, box, 1.0F, 0.67F, 0.08F, 0.9F);
        }

        buffers.endBatch(ModRenderTypes.ECHO_ORE_OVERLAY);
        RenderSystem.enableDepthTest();

        poseStack.popPose();
        buffers.endBatch(ModRenderTypes.ECHO_ORE_OVERLAY);
    }
}