package com.minecart.yunxian.client;

import com.minecart.yunxian.item.EchoSpyglassItem;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;

public final class EchoSculkOverlay {

    /** 幽匿贴图达到的最大透明度，0~1 */
    private static final float MAX_ALPHA = 0.40F;

    /** 淡入速度：每秒增加的 alpha */
    private static final float FADE_IN_SPEED = 1.0F;

    /** 淡出速度：每秒减少的 alpha */
    private static final float FADE_OUT_SPEED = 0.8F;

    /** 单个贴图平铺的尺寸（GUI 缩放后的像素） */
    private static final int TILE_SIZE = 32;

    // ===== 平滑状态 =====
    private static float currentAlpha = 0.0F;
    private static long lastNanos = 0L;

    private EchoSculkOverlay() {
    }

    /** 客户端初始化时调用一次（放在 ModRenderers.register 里） */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EchoSculkOverlay::onRenderLevel);
    }

    public static void onRenderLevel(RenderLevelStageEvent event) {
        // 画在 AFTER_TRANSLUCENT_BLOCKS：早于矿石线框的 AFTER_PARTICLES，
        // 因此线框永远画在幽匿覆盖层之上。
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // ===== 平滑 alpha（基于真实时间，帧率无关） =====
        long now = System.nanoTime();
        float deltaSeconds = lastNanos == 0L ? 0.0F : (now - lastNanos) / 1_000_000_000.0F;
        lastNanos = now;

        boolean scoping = player.isUsingItem()
                && player.getUseItem().getItem() instanceof EchoSpyglassItem;
        float target = scoping ? MAX_ALPHA : 0.0F;

        if (currentAlpha < target) {
            currentAlpha = Math.min(target, currentAlpha + deltaSeconds * FADE_IN_SPEED);
        } else if (currentAlpha > target) {
            currentAlpha = Math.max(target, currentAlpha - deltaSeconds * FADE_OUT_SPEED);
        }

        if (currentAlpha <= 0.005F) {
            return;
        }

        TextureAtlasSprite sculk = mc.getBlockRenderer().getBlockModelShaper()
                .getParticleIcon(Blocks.SCULK.defaultBlockState());
        int width = mc.getWindow().getGuiScaledWidth();
        int height = mc.getWindow().getGuiScaledHeight();

        // ===== 保存当前投影状态，切到 GUI 正交投影 =====
        Matrix4f oldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting oldSorting = RenderSystem.getVertexSorting();

        RenderSystem.getModelViewStack().pushMatrix();
        RenderSystem.getModelViewStack().identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(
                new Matrix4f().setOrtho(0.0F, (float) width, (float) height, 0.0F, -1000.0F, 1000.0F),
                VertexSorting.ORTHOGRAPHIC_Z);

        try {
            Matrix4f identity = new Matrix4f();
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            VertexConsumer consumer = buffers.getBuffer(ModRenderTypes.SCULK_OVERLAY);

            float u0 = sculk.getU0();
            float v0 = sculk.getV0();
            float du = sculk.getU1() - u0;
            float dv = sculk.getV1() - v0;

            for (int x = 0; x < width; x += TILE_SIZE) {
                for (int y = 0; y < height; y += TILE_SIZE) {
                    int w = Math.min(TILE_SIZE, width - x);
                    int h = Math.min(TILE_SIZE, height - y);

                    // 边缘格子用部分 UV，避免拉伸
                    float u1 = u0 + du * (w / (float) TILE_SIZE);
                    float v1 = v0 + dv * (h / (float) TILE_SIZE);

                    consumer.addVertex(identity, x, y, 0).setUv(u0, v0);
                    consumer.addVertex(identity, x + w, y, 0).setUv(u1, v0);
                    consumer.addVertex(identity, x + w, y + h, 0).setUv(u1, v1);
                    consumer.addVertex(identity, x, y + h, 0).setUv(u0, v1);
                }
            }

            // 透明度经全局 shaderColor 传给 POSITION_TEX_SHADER 的 ColorModulator
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, currentAlpha);
            buffers.endBatch(ModRenderTypes.SCULK_OVERLAY);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            // 恢复世界渲染的模型视图与投影矩阵（含排序），不影响后续管线
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
        }
    }
}