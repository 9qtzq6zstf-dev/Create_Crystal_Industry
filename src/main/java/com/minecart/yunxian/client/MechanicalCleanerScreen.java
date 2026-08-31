package com.minecart.yunxian.client;

import com.minecart.yunxian.MechanicalCleanerBlockEntity;
import com.minecart.yunxian.MechanicalCleanerMenu;
import com.minecart.yunxian.Yunxian;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import com.simibubi.create.foundation.gui.widget.Label;
import com.simibubi.create.foundation.gui.widget.ScrollInput;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class MechanicalCleanerScreen extends AbstractContainerScreen<MechanicalCleanerMenu> {

    // ==================== 贴图资源 ====================

    /** 自绘容器面板贴图（218×125，底部 10px 透明充当与玩家面板的间隔） */
    private static final ResourceLocation CONTAINER_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                    "textures/gui/container/mechanical_cleaner.png");

    /** 机械动力玩家物品栏贴图（176×108） */
    private static final ResourceLocation CREATE_INVENTORY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");

    // ==================== GUI 总体尺寸 ====================

    /** 整个 GUI 的宽度（等于容器面板贴图宽度，218px） */
    public static final int GUI_WIDTH = 218;

    /** 整个 GUI 的高度：233 = 容器贴图高 125 + 玩家面板高 108 */
    public static final int GUI_HEIGHT = 233;

    // ==================== 容器面板（自绘贴图） ====================

    /** 容器面板的实际内容高度（不含底部透明间隔），115px */
    public static final int CONTAINER_CONTENT_HEIGHT = 115;

    /** 容器贴图总高度：内容 115 + 底部透明间隔 10 = 125px */
    public static final int CONTAINER_TEXTURE_HEIGHT = 125;

    /** 容器面板与玩家面板之间的透明间隔高度，10px */
    public static final int PANEL_GAP = 10;

    /** 容器面板相对 GUI 顶部的上移量：上移 PANEL_GAP，把底部透明区露出作为间隔 */
    public static final int CONTAINER_PANEL_Y_OFFSET = -PANEL_GAP;

    // ==================== 玩家物品栏面板（Create 贴图） ====================

    /** 玩家面板贴图尺寸：176×108 */
    public static final int INVENTORY_TEXTURE_WIDTH = 176;
    public static final int INVENTORY_TEXTURE_HEIGHT = 108;

    /** 玩家面板相对 GUI 顶部的 Y 坐标：紧贴容器内容底部（= 容器内容高度 115） */
    public static final int PLAYER_PANEL_Y = 115;

    /** 玩家面板相对 GUI 左边缘的 X 偏移：25，使面板内部槽位（+8）与容器槽位列（33）对齐 */
    public static final int INVENTORY_TEXTURE_X = 25;

    // ==================== 标题位置 ====================

    /** 标题相对 GUI 顶部的 Y 坐标：容器面板已上移 10px，标题跟随上移，故为负值 */
    public static final int TITLE_LABEL_Y = -6;

    // ==================== 确认按钮位置 ====================

    /** 按钮边长（Create 图标按钮固定 18×18px） */
    private static final int BUTTON_SIZE = 18;

    /** 按钮相对 GUI 左边缘的 X：对应容器贴图内 x=186 */
    private static final int BUTTON_X = 185;

    /** 按钮相对 GUI 顶部的 Y：容器贴图内 y=92，减去容器面板上移量 10 → 92 - 10 = 82 */
    private static final int BUTTON_Y = 81;

    // ==================== 吸取距离配置栏（确认键左方） ====================

    /** 滚轮配置栏：位于确认键左方，与确认键同高 */
    private static final int RANGE_INPUT_X = 130;
    private static final int RANGE_INPUT_Y = 82;
    private static final int RANGE_INPUT_WIDTH = 50;
    private static final int RANGE_INPUT_HEIGHT = 18;

    /** 吸取距离范围：1~20（withRange 的 max 排他，故写 21；上限与鼓风机 256 转速最大距离一致） */
    private static final int RANGE_MIN = 1;
    private static final int RANGE_MAX_EXCLUSIVE = MechanicalCleanerBlockEntity.SUCK_RANGE_MAX + 1; // 21

    // ==================== 吸取距离数值标签 ====================

    /** 数值标签相对 GUI 左边缘的 X：与配置栏左对齐 */
    private static final int RANGE_LABEL_X = RANGE_INPUT_X;

    /** 数值标签相对 GUI 顶部的 Y */
    private static final int RANGE_LABEL_Y = RANGE_INPUT_Y;

    /** 数值标签文本颜色：与标题一致 */
    private static final int RANGE_LABEL_COLOR = 0x404040;

    // ==================== 吹出数量配置栏（格数配置左侧 30px） ====================

    /** 吹出数量滚轮：位于格数配置栏左侧，间隔 30px；与格数配置同宽同高 */
    private static final int EJECT_INPUT_X = RANGE_INPUT_X - RANGE_INPUT_WIDTH - 30; // 130 - 50 - 30 = 50
    private static final int EJECT_INPUT_Y = RANGE_INPUT_Y;
    private static final int EJECT_INPUT_WIDTH = RANGE_INPUT_WIDTH;
    private static final int EJECT_INPUT_HEIGHT = RANGE_INPUT_HEIGHT;

    /** 吹出数量范围：1~64（withRange 的 max 排他，故写 65） */
    private static final int EJECT_MIN = 1;
    private static final int EJECT_MAX_EXCLUSIVE = MechanicalCleanerBlockEntity.EJECT_AMOUNT_MAX + 1; // 65

    /** 吹出数量标签：与数量配置栏左对齐、同高 */
    private static final int EJECT_LABEL_X = EJECT_INPUT_X;
    private static final int EJECT_LABEL_Y = EJECT_INPUT_Y;

    private ScrollInput rangeInput;
    private ScrollInput ejectInput;

    public MechanicalCleanerScreen(MechanicalCleanerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();

        // 标题水平居中于 GUI；垂直方向随容器面板上移（TITLE_LABEL_Y）
        this.titleLabelY = TITLE_LABEL_Y;
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;

        // ---- 确认按钮 ----
        IconButton confirmButton = new IconButton(
                this.leftPos + BUTTON_X,
                this.topPos + BUTTON_Y,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::onClose);
        this.addRenderableWidget(confirmButton);

        // ---- 吸取距离数值标签 + 滚轮配置（确认键左方） ----
        Label rangeLabel = new Label(
                this.leftPos + RANGE_LABEL_X,
                this.topPos + RANGE_LABEL_Y,
                Component.empty())
                .colored(RANGE_LABEL_COLOR)
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
                    // 格数滚轮：按钮 id 直接是 value（1~20）
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, value);
                    }
                });
        rangeInput.setState(this.menu.getSuckRange());
        this.addRenderableWidget(rangeInput);

        // ---- 吹出数量数值标签 + 滚轮配置（格数配置左侧 30px） ----
        Label ejectLabel = new Label(
                this.leftPos + EJECT_LABEL_X,
                this.topPos + EJECT_LABEL_Y,
                Component.empty())
                .colored(RANGE_LABEL_COLOR)
                .withShadow();
        this.addRenderableWidget(ejectLabel);

        ejectInput = new ScrollInput(
                this.leftPos + EJECT_INPUT_X,
                this.topPos + EJECT_INPUT_Y,
                EJECT_INPUT_WIDTH,
                EJECT_INPUT_HEIGHT)
                .withRange(EJECT_MIN, EJECT_MAX_EXCLUSIVE)
                .titled(Component.translatable("create_crystal_industry.mechanical_cleaner.amount"))
                .format(value -> Component.translatable(
                        "create_crystal_industry.mechanical_cleaner.amount.value", value))
                .writingTo(ejectLabel)
                .calling(value -> {
                    // 数量滚轮：按钮 id = 100 + value，与服务端 clickMenuButton 分流对应
                    if (this.minecraft != null && this.minecraft.player != null) {
                        this.minecraft.gameMode.handleInventoryButtonClick(
                                this.menu.containerId, MechanicalCleanerMenu.EJECT_BUTTON_OFFSET + value);
                    }
                });
        ejectInput.setState(this.menu.getEjectAmount());
        this.addRenderableWidget(ejectInput);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        // GUI 左上角（基于屏幕居中）
        int guiLeft = (this.width - this.imageWidth) / 2;
        int guiTop = (this.height - this.imageHeight) / 2;

        // 1. 容器面板：整体上移，露出底部透明区作为两面板的间隔
        int containerPanelY = guiTop + CONTAINER_PANEL_Y_OFFSET;
        guiGraphics.blit(CONTAINER_TEXTURE, guiLeft, containerPanelY, 0, 0, GUI_WIDTH, CONTAINER_TEXTURE_HEIGHT);

        // 2. 玩家物品栏：紧贴容器内容底部，水平偏移使槽位列与容器对齐
        int inventoryPanelX = guiLeft + INVENTORY_TEXTURE_X;
        int inventoryPanelY = guiTop + PLAYER_PANEL_Y;
        guiGraphics.blit(CREATE_INVENTORY_TEXTURE, inventoryPanelX, inventoryPanelY,
                0, 0, INVENTORY_TEXTURE_WIDTH, INVENTORY_TEXTURE_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        // 玩家物品栏标签已印在 Create 贴图上，这里只画容器标题
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }
}