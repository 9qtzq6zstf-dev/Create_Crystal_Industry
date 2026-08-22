package com.minecart.yunxian.client;

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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

public final class EchoHighlightRenderer {

    private EchoHighlightRenderer() {
    }

    /** 客户端初始化时调用一次（放在 ModRenderers.register 里） */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EchoHighlightRenderer::onRenderLevel);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        // AFTER_PARTICLES：世界空间内、且晚于所有方块与粒子的阶段
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

        // ===== 关键：显式关闭深度测试与深度写入，强制穿墙 =====
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        try {
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.ECHO_ORE_OVERLAY);

            for (BlockPos pos : ores) {
                AABB box = new AABB(pos);
                if (event.getFrustum() != null && !event.getFrustum().isVisible(box)) {
                    continue;
                }
                LevelRenderer.renderLineBox(poseStack, consumer, box, 1.0F, 0.67F, 0.08F, 0.9F);
            }

            buffers.endBatch(ModRenderTypes.ECHO_ORE_OVERLAY);
        } finally {
            // 无论正常还是异常，都恢复深度状态，避免影响天气等后续渲染
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
        }

        poseStack.popPose();
    }
}