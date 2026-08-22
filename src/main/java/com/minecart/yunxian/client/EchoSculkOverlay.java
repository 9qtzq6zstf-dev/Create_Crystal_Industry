package com.minecart.yunxian.client;

import com.minecart.yunxian.item.EchoSpyglassItem;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class EchoSculkOverlay {

    /** 幽匿贴图达到的最大透明度，0~1 */
    private static final float MAX_ALPHA = 0.20F;

    /** 淡入速度：每秒增加的 alpha。0.8 ≈ 0.25 秒淡入完成 */
    private static final float FADE_IN_SPEED = 0.8F;

    /** 淡出速度：每秒减少的 alpha。0.4 ≈ 0.5 秒淡出完成 */
    private static final float FADE_OUT_SPEED = 0.4F;

    /** 单个贴图平铺的尺寸（GUI 缩放后的像素） */
    private static final int TILE_SIZE = 64;

    // ===== 平滑状态 =====
    private static float currentAlpha = 0.0F;
    private static long lastNanos = 0L;

    private EchoSculkOverlay() {
    }

    /** 客户端初始化时调用一次（放在 ModRenderers.register 里） */
    public static void register() {
        NeoForge.EVENT_BUS.addListener(EchoSculkOverlay::onRenderGui);
    }

    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }

        // ===== 更新平滑 alpha（基于真实时间，帧率无关，绕开 DeltaTracker API） =====
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

        // 完全淡出后不画任何东西
        if (currentAlpha <= 0.005F) {
            return;
        }

        TextureAtlasSprite sculk = mc.getBlockRenderer().getBlockModelShaper()
                .getParticleIcon(Blocks.SCULK.defaultBlockState());

        GuiGraphics graphics = event.getGuiGraphics();
        int width = graphics.guiWidth();
        int height = graphics.guiHeight();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, currentAlpha);
        try {
            for (int x = 0; x < width; x += TILE_SIZE) {
                for (int y = 0; y < height; y += TILE_SIZE) {
                    int w = Math.min(TILE_SIZE, width - x);
                    int h = Math.min(TILE_SIZE, height - y);
                    graphics.blit(x, y, 0, w, h, sculk);
                }
            }
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}