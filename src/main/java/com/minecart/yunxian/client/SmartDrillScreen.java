package com.minecart.yunxian.client;

import com.minecart.yunxian.SilkTouchModePacket;
import com.minecart.yunxian.SmartDrillMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

public class SmartDrillScreen extends AbstractContainerScreen<SmartDrillMenu> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("yunxian:textures/gui/smart_drill.png");
    private Button toggleButton;

    public SmartDrillScreen(SmartDrillMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();

        this.toggleButton = Button.builder(
                        Component.literal(""), // 初始文字，稍后更新
                        b -> {
                            boolean current = menu.getSilkTouchMode();
                            boolean newMode = !current;
                            PacketDistributor.sendToServer(new SilkTouchModePacket(menu.getBlockPos(), newMode));
                        }
                )
                .bounds(leftPos + 80, topPos + 10, 40, 20)
                .build();

        // 按钮默认自带背景，无需额外设置
        this.addRenderableWidget(toggleButton);
        updateButtonText();
    }

    private void updateButtonText() {
        if (toggleButton != null) {
            boolean mode = menu.getSilkTouchMode();
            toggleButton.setMessage(Component.literal(mode ? "精准" : "普通"));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderTooltip(guiGraphics, mouseX, mouseY);
        // 每帧更新按钮文字（同步模式状态）
        updateButtonText();
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
    }
}