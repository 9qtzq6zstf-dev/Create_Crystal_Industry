package com.minecart.yunxian.client;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.menu.EchoSpyglassFilterMenu;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class EchoSpyglassFilterScreen extends AbstractContainerScreen<EchoSpyglassFilterMenu> {

    // 纹理引用
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "textures/gui/echo_spyglass_filter.png");

    private static final ResourceLocation CREATE_INVENTORY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");

    // 尺寸常量
    private static final int MAIN_HEIGHT = 88;
    private static final int INVENTORY_HEIGHT = 108;
    private static final int TOTAL_HEIGHT = MAIN_HEIGHT + INVENTORY_HEIGHT;

    // 主界面上移
    private static final int MOVE_UP = 6;

    // 幽灵槽位置
    private static final int FILTER_SLOT_X = 79;
    private static final int FILTER_SLOT_Y = 22;
    private static final int SLOT_SIZE = 18;

    // ========== 修改：按钮位置 ==========
    // 原 BUTTON_Y = 63，上移 8 像素 → 57
    private static final int BUTTON_X = 149;
    private static final int BUTTON_Y = 57;   // ✅ 上移 8 像素（原 63 - 8）
    private static final int BUTTON_SIZE = 18;

    public EchoSpyglassFilterScreen(EchoSpyglassFilterMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageHeight = TOTAL_HEIGHT;
        this.imageWidth = 182;
    }

    @Override
    protected void init() {
        super.init();
        this.imageHeight = TOTAL_HEIGHT;

        // ---- 标题位置 ----
        this.titleLabelY = -2;
        int filterCenterX = FILTER_SLOT_X + SLOT_SIZE / 2;
        int titleWidth = this.font.width(this.title);
        this.titleLabelX = filterCenterX - titleWidth / 2;

        // ---- "物品栏"标签 ----
        this.inventoryLabelX = 8;
        this.inventoryLabelY = MAIN_HEIGHT + 6;

        // ============================================================
        // 只保留“确定”按钮（已移除叉号/退出按钮）
        // ============================================================
        IconButton confirmButton = new IconButton(
                this.leftPos + BUTTON_X,
                this.topPos + BUTTON_Y,        // ✅ 新坐标 Y = 55
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(() -> {
            // 确认逻辑：保存筛选设置
            // 例如：menu.saveFilter();
            this.onClose();
        });

        // ---- 将按钮添加到界面 ----
        this.addRenderableWidget(confirmButton);
        // ❌ 退出按钮已移除，不再添加
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // 1. 绘制主界面纹理（上方），Y坐标上移 MOVE_UP 像素
        int mainY = this.topPos - MOVE_UP;
        graphics.blit(TEXTURE, this.leftPos, mainY, 0, 0, this.imageWidth, MAIN_HEIGHT);

        // 2. 绘制机械动力的背包纹理（下方），保持原位置
        int inventoryY = this.topPos + MAIN_HEIGHT;
        graphics.blit(CREATE_INVENTORY_TEXTURE, this.leftPos, inventoryY, 0, 0, 176, INVENTORY_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 绘制标题
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        // 绘制物品栏标签
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}