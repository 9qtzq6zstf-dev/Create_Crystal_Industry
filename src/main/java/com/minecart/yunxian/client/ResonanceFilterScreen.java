package com.minecart.yunxian.client;

import com.google.common.collect.ImmutableList;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.menu.ResonanceFilterMenu;
import com.minecart.yunxian.network.SetNetworkNamePayload;
import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.menu.AbstractSimiContainerScreen;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.element.GuiGameElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

/**
 * 共振过滤器的界面。
 * <p>
 * 版式整套照 Create <b>包裹过滤器</b>那一套来（我们的面板贴图本身就是它那张 218×79 换了个标题条颜色）：
 * 同样的 {@code setWindowOffset(-11, 7)}、同样的「面板 + 4 像素间隙 + Create 自带的
 * {@code player_inventory.png}」（位置由 {@code getLeftOfCentered} 算，物品栏标签交给
 * {@code renderPlayerInventory} 画）、同样的标题落点、右侧同样由 {@link GuiGameElement} 渲染手持物品。
 * <p>
 * 面板上两处内容也一一对应：左边那格固定渲染一台共振台（对应包裹过滤器里那个箱子，
 * 位置都是 (16,23)），右边那条长条是名字输入框（对应那边的收件地址框）。
 * <p>
 * 物品栏贴图里的格子必须与菜单槽位对齐，所以这里的窗口尺寸 / 偏移一改，
 * 就要同步改 {@link ResonanceFilterMenu#PLAYER_SLOT_X} 与
 * {@link ResonanceFilterMenu#PLAYER_ROW0_Y}。
 */
public class ResonanceFilterScreen extends AbstractSimiContainerScreen<ResonanceFilterMenu> {

    // ==================== 贴图资源 ====================

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "textures/gui/resonance_filter.png");

    // ==================== 面板 ====================

    /** 面板贴图裁切尺寸：y=0..78 的那块，含右侧伸出的箭头 */
    public static final int PANEL_WIDTH = 218;
    public static final int PANEL_HEIGHT = 79;

    // ==================== 玩家物品栏 ====================

    /** 面板与物品栏之间留的间隙，以及面板下方物品栏贴图的尺寸 —— 照 Create */
    public static final int PANEL_GAP = 4;
    public static final int INVENTORY_TEXTURE_WIDTH = 176;
    public static final int INVENTORY_TEXTURE_HEIGHT = 108;

    // ==================== GUI 总体尺寸 ====================

    public static final int GUI_WIDTH = Math.max(PANEL_WIDTH, INVENTORY_TEXTURE_WIDTH);
    public static final int GUI_HEIGHT = PANEL_HEIGHT + PANEL_GAP + INVENTORY_TEXTURE_HEIGHT;

    /**
     * 窗口整体偏移，两个数照抄 {@code PackageFilterScreen#init}（面板尺寸与它一致）。
     * 面板因此落在屏幕中心偏左 11、偏下 7 的位置，而物品栏靠
     * {@code getLeftOfCentered} 回到屏幕正中 —— 这正是 Create 那两种过滤器给人的观感。
     */
    private static final int WINDOW_X_OFFSET = -11;
    private static final int WINDOW_Y_OFFSET = 7;

    // ==================== 固定的共振台 ====================

    /**
     * 面板左边那格里固定渲染的共振台，位置照 {@code PackageFilterScreen} 渲染包裹箱子那一处
     * —— 那里正是贴图上画好的那个 18×18 格子（内容区 16×16，物品渲染刚好占满）。
     */
    private static final int TABLE_RENDER_X = 16;
    private static final int TABLE_RENDER_Y = 23;
    private static final int TABLE_RENDER_Z = 0;

    // ==================== 名字输入框 ====================

    /**
     * 长条里的名字输入框，位置与垂直尺寸照 {@code PackageFilterScreen} 的收件地址框
     * （那边是 (44,28,129,9)，宽只到 129 是因为 177 处还要摆一个剪贴板图标，我们没有那个东西，
     * 所以按长条宽度铺到 145）。输入框本身不画底：长条的底是贴图画的。
     */
    private static final int NAME_BOX_X = 44;
    private static final int NAME_BOX_Y = 28;
    private static final int NAME_BOX_WIDTH = 145;
    private static final int NAME_BOX_HEIGHT = 9;
    private static final int NAME_BOX_TEXT_COLOR = 0xFFFFFF;

    private static final String HINT_KEY = "create_crystal_industry.resonance_filter.network_name.hint";
    private static final String UNLINKED_KEY = "create_crystal_industry.resonance_filter.network_name.unlinked";

    // ==================== 确认按钮 ====================

    /** 贴图上画的那个勾的位置（185,55）的 18×18 —— 必须与之一致，否则会露出贴图里的示意按钮 */
    private static final int CONFIRM_BUTTON_X = 185;
    private static final int CONFIRM_BUTTON_Y = 55;

    // ==================== 标题 ====================

    /** 标题写在贴图顶部的红条里（红条本身是 y=2..12） */
    private static final int TITLE_COLOR = 0x404040;

    // ==================== 右侧手持物品渲染 ====================

    /** 位置系数与缩放照抄 {@code AbstractFilterScreen#renderBg}，只是把背景尺寸换成我们的面板 */
    private static final int ITEM_RENDER_X = PANEL_WIDTH + 8;
    private static final int ITEM_RENDER_Y = PANEL_HEIGHT - 52;
    private static final int ITEM_RENDER_Z = -200;
    private static final float ITEM_RENDER_SCALE = 4;

    /** 让 JEI 避开的物品渲染区域宽高，同样照抄 Create */
    private static final int ITEM_AREA_WIDTH = 80;
    private static final int ITEM_AREA_HEIGHT = 48;

    private EditBox nameBox;

    public ResonanceFilterScreen(ResonanceFilterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void init() {
        // AbstractSimiContainerScreen 要求：setWindowSize / setWindowOffset 必须在 super.init() 之前调用
        setWindowSize(GUI_WIDTH, GUI_HEIGHT);
        setWindowOffset(WINDOW_X_OFFSET, WINDOW_Y_OFFSET);
        super.init();

        // 贴图上画了勾的位置叠一个真正的 Create 按钮，和机械吸尘器那边一样
        IconButton confirmButton = new IconButton(
                this.leftPos + CONFIRM_BUTTON_X,
                this.topPos + CONFIRM_BUTTON_Y,
                AllIcons.I_CONFIRM
        );
        confirmButton.withCallback(this::onClose);
        this.addRenderableWidget(confirmButton);

        nameBox = new EditBox(this.font,
                this.leftPos + NAME_BOX_X, this.topPos + NAME_BOX_Y,
                NAME_BOX_WIDTH, NAME_BOX_HEIGHT, Component.empty());
        nameBox.setBordered(false);          // 长条的底归贴图，输入框只画文字（Create 的 AddressEditBox 同理）
        nameBox.setTextColor(NAME_BOX_TEXT_COLOR);
        nameBox.setMaxLength(SetNetworkNamePayload.MAX_LENGTH);
        nameBox.setValue(menu.networkName());
        nameBox.setEditable(menu.isLinked());   // 没接入网络就没有名字可起
        nameBox.setHint(Component.translatable(menu.isLinked() ? HINT_KEY : UNLINKED_KEY));
        nameBox.setResponder(name -> {
            menu.setNetworkName(name);
            PacketDistributor.sendToServer(new SetNetworkNamePayload(name));
        });
        this.addRenderableWidget(nameBox);
        this.setFocused(nameBox);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = this.leftPos;
        int y = this.topPos;

        graphics.blit(TEXTURE, x, y, 0, 0, PANEL_WIDTH, PANEL_HEIGHT);

        // 格子里那台固定的共振台 —— 界面上它不随任何东西变，只是说明这张网是谁的
        GuiGameElement.of(ModBlocks.RESONANCE_TABLE.get())
                .<GuiGameElement.GuiRenderBuilder>at(x + TABLE_RENDER_X, y + TABLE_RENDER_Y, TABLE_RENDER_Z)
                .render(graphics);

        renderPlayerInventory(graphics, getLeftOfCentered(INVENTORY_TEXTURE_WIDTH),
                y + PANEL_HEIGHT + PANEL_GAP);

        graphics.drawString(font, title, x + (PANEL_WIDTH - 8) / 2 - font.width(title) / 2, y + 4,
                TITLE_COLOR, false);

        // 面板右侧那件转着的手持物品 —— 和 Create 的过滤器一模一样的那一件
        GuiGameElement.of(menu.contentHolder)
                .<GuiGameElement.GuiRenderBuilder>at(x + ITEM_RENDER_X, y + ITEM_RENDER_Y, ITEM_RENDER_Z)
                .scale(ITEM_RENDER_SCALE)
                .render(graphics);
    }

    /**
     * 右侧渲染的那件物品伸在面板之外，得告诉 JEI 别把物品栏压上来。
     * 区域算法照抄 Create：贴着面板右缘、从面板底部往上 40 像素开始的一块。
     */
    @Override
    public List<Rect2i> getExtraAreas() {
        return ImmutableList.of(new Rect2i(
                this.leftPos + PANEL_WIDTH,
                this.topPos + PANEL_HEIGHT - 40,
                ITEM_AREA_WIDTH, ITEM_AREA_HEIGHT));
    }
}
