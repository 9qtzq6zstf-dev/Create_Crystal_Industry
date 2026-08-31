package com.minecart.yunxian.client;

import com.minecart.yunxian.MechanicalCleanerBlockEntity;
import com.minecart.yunxian.MechanicalCleanerFilterBehaviour.RotationDirection;
import com.minecart.yunxian.MechanicalCleanerMenu;
import com.minecart.yunxian.Yunxian;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import net.createmod.catnip.gui.element.ScreenElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MechanicalCleanerScreen extends AbstractContainerScreen<MechanicalCleanerMenu> {

    // ==================== 贴图资源 ====================
    /** 机械动力图标大图（256×256） */
    private static final ResourceLocation CREATE_ICONS_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/icons.png");

    /** 风向按钮图标：取自 Create icons.png 的 (129,65) 起 16×16 区域 */
    private static final ScreenElement DIRECTION_ICON = new ScreenElement() {
        @Override
        public void render(GuiGraphics graphics, int x, int y) {
            graphics.blit(CREATE_ICONS_TEXTURE, x, y, 128, 64, 16, 16);
        }
    };
    private static final ResourceLocation CONTAINER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                    "textures/gui/container/mechanical_cleaner.png");
    private static final ResourceLocation CREATE_INVENTORY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");

    // ==================== GUI 总体尺寸 ====================

    public static final int GUI_WIDTH = 218;
    public static final int GUI_HEIGHT = 233;

    // ==================== 容器面板 ====================

    public static final int CONTAINER_CONTENT_HEIGHT = 115;
    public static final int CONTAINER_TEXTURE_HEIGHT = 125;
    public static final int PANEL_GAP = 10;
    public static final int CONTAINER_PANEL_Y_OFFSET = -PANEL_GAP;

    // ==================== 玩家物品栏面板 ====================

    public static final int INVENTORY_TEXTURE_WIDTH = 176;
    public static final int INVENTORY_TEXTURE_HEIGHT = 108;
    public static final int PLAYER_PANEL_Y = 115;
    public static final int INVENTORY_TEXTURE_X = 25;

    // ==================== 标题位置 ====================

    public static final int TITLE_LABEL_Y = -6;

    // ==================== 确认按钮（保持不变） ====================

    private static final int BUTTON_X = 185;
    private static final int BUTTON_Y = 81;

    // ==================== 风力格数配置栏（GUI 鼠标滚轮调整） ====================

    private static final int RANGE_INPUT_X = 35;
    private static final int RANGE_INPUT_Y = 86;
    private static final int RANGE_INPUT_WIDTH = 50;
    private static final int RANGE_INPUT_HEIGHT = 10;
    private static final int RANGE_MIN = MechanicalCleanerBlockEntity.SUCK_RANGE_MIN;
    private static final int RANGE_MAX_EXCLUSIVE = MechanicalCleanerBlockEntity.SUCK_RANGE_MAX + 1;
    private static final int RANGE_LABEL_X = RANGE_INPUT_X;
    private static final int RANGE_LABEL_Y = RANGE_INPUT_Y;
    private static final int LABEL_COLOR = 0xFFFFFF;

    // ==================== 风向切换按钮（独立于确认按钮） ====================

    /** 风向切换按钮：位于风力格数栏与确认按钮之间（仿确认按钮尺寸 18×18） */
    private static final int DIRECTION_BUTTON_X = 7;
    private static final int DIRECTION_BUTTON_Y = 81;

    private ScrollInput rangeInput;
    private IconButton directionButton;
    private boolean reversed;

    public MechanicalCleanerScreen(MechanicalCleanerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        this.titleLabelY = TITLE_LABEL_Y;
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;

        // ---- 确认按钮（原样，不动） ----
        IconButton confirmButton = new IconButton(
                this.leftPos + BUTTON_X,
                this.topPos + BUTTON_Y,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::onClose);
        this.addRenderableWidget(confirmButton);

        // ---- 风力格数滚轮（GUI 鼠标滚轮调整） ----
        Label rangeLabel = new Label(
                this.leftPos + RANGE_LABEL_X,
                this.topPos + RANGE_LABEL_Y,
                Component.empty())
                .colored(LABEL_COLOR)
                .withShadow();
        this.addRenderableWidget(rangeLabel);

        rangeInput = new ScrollInput(
                this.leftPos + RANGE_INPUT_X,
                this.topPos + RANGE_INPUT_Y,
                RANGE_INPUT_WIDTH,
                RANGE_INPUT_HEIGHT)
                .withRange(RANGE_MIN, RANGE_MAX_EXCLUSIVE)
                .titled(Component.translatable("create_crystal_industry.mechanical_cleaner.range"))
                .format(value -> Component.translatable(
                        "create_crystal_industry.mechanical_cleaner.range.value", value))
                .writingTo(rangeLabel)
                .calling(value -> {
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(
                                this.menu.containerId, MechanicalCleanerMenu.RANGE_BUTTON_OFFSET + value);
                    }
                });
        rangeInput.setState(this.menu.getSuckRange());
        this.addRenderableWidget(rangeInput);

        // ---- 风向切换按钮（独立于确认按钮） ----
        reversed = this.menu.getDirection() == RotationDirection.REVERSED;
        directionButton = new IconButton(
                this.leftPos + DIRECTION_BUTTON_X,
                this.topPos + DIRECTION_BUTTON_Y,
                getDirectionIcon());
        refreshDirectionButton();
        directionButton.withCallback(() -> {
            reversed = !reversed;
            refreshDirectionButton();
            if (this.minecraft != null && this.minecraft.player != null) {
                this.minecraft.gameMode.handleInventoryButtonClick(
                        this.menu.containerId, MechanicalCleanerMenu.DIRECTION_BUTTON_ID);
            }
        });
        this.addRenderableWidget(directionButton);
    }

    private ScreenElement getDirectionIcon() {
        return DIRECTION_ICON;
    }

    private void refreshDirectionButton() {
        directionButton.setIcon(getDirectionIcon());
        directionButton.setToolTip(Component.translatable(reversed
                ? "create_crystal_industry.mechanical_cleaner.direction.reversed"
                : "create_crystal_industry.mechanical_cleaner.direction.normal"));
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int guiLeft = (this.width - this.imageWidth) / 2;
        int guiTop = (this.height - this.imageHeight) / 2;

        int containerPanelY = guiTop + CONTAINER_PANEL_Y_OFFSET;
        guiGraphics.blit(CONTAINER_TEXTURE, guiLeft, containerPanelY, 0, 0, GUI_WIDTH, CONTAINER_TEXTURE_HEIGHT);

        int inventoryPanelX = guiLeft + INVENTORY_TEXTURE_X;
        int inventoryPanelY = guiTop + PLAYER_PANEL_Y;
        guiGraphics.blit(CREATE_INVENTORY_TEXTURE, inventoryPanelX, inventoryPanelY,
                0, 0, INVENTORY_TEXTURE_WIDTH, INVENTORY_TEXTURE_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }
}