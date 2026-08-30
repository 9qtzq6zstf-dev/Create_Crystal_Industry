package com.minecart.yunxian.client;

import com.minecart.yunxian.MechanicalCleanerMenu;
import com.minecart.yunxian.Yunxian;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MechanicalCleanerScreen extends AbstractContainerScreen<MechanicalCleanerMenu> {

    /** 自绘容器面板贴图（只画上半部分，不含玩家物品栏） */
    private static final ResourceLocation CONTAINER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                    "textures/gui/container/mechanical_cleaner.png");

    /** 机械动力玩家物品栏贴图 */
    private static final ResourceLocation CREATE_INVENTORY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");

    private static final int CONTAINER_HEIGHT = 83;
    private static final int INVENTORY_HEIGHT = 108;

    public MechanicalCleanerScreen(MechanicalCleanerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = CONTAINER_HEIGHT + INVENTORY_HEIGHT; // 191
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (this.width - this.imageWidth) / 2;
        int y = (this.height - this.imageHeight) / 2;

        // 1. 容器面板：自绘贴图，只取顶部 CONTAINER_HEIGHT 像素
        guiGraphics.blit(CONTAINER_TEXTURE, x, y, 0, 0, this.imageWidth, CONTAINER_HEIGHT);

        // 2. 玩家物品栏：机械动力贴图，放在容器面板正下方
        guiGraphics.blit(CREATE_INVENTORY_TEXTURE, x, y + CONTAINER_HEIGHT, 0, 0, 176, INVENTORY_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 只画标题（Create 面板贴图自带 "Inventory" 标签，不必再画）
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }
}