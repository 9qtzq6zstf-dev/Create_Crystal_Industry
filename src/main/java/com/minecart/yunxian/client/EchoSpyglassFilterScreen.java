package com.minecart.yunxian.client;

import com.minecart.yunxian.ModItems;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.menu.EchoSpyglassFilterMenu;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class EchoSpyglassFilterScreen extends AbstractContainerScreen<EchoSpyglassFilterMenu> {

    // 纹理引用
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "textures/gui/echo_spyglass_filter.png");

    private static final ResourceLocation CREATE_INVENTORY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/player_inventory.png");

    // ========== 平面贴图参数（与 3D 模型完全独立） ==========
    private static final ResourceLocation ECHO_OUTLINE_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "textures/item/echo_spyglass.png");
    // 贴图渲染的目标宽度（像素），高度按原图宽高比自动缩放
    private static final int TEXTURE_SIZE = 50;
    // 贴图中心点相对 GUI 左上角的位置（像素）
    private static final int TEXTURE_X = 210;
    private static final int TEXTURE_Y = 60;

    // 贴图实际像素尺寸缓存：-1 = 尚未读取，0 = 读取失败
    private int texWidth = -1;
    private int texHeight = -1;

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

    // ========== 3D 模型参数（与贴图完全独立） ==========
    // 模型中心点相对 GUI 左上角的位置（像素）
    private static final int ITEM_X = 210;
    private static final int ITEM_Y = 130;
    // 模型显示倍率（1.0 = 16 像素）
    private static final float ITEM_SCALE = 5F;

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
        // 只保留"确定"按钮（已移除叉号/退出按钮）
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

        // 3. 在背景之上绘制回响望远镜（贴图或 3D 模型，由配置决定）
        this.renderEchoSpyglass(graphics);
    }

    /**
     * 根据配置决定渲染 3D 模型还是平面贴图。
     * 配置键：spyglassGuiRenderModel（ModConfig.Client），默认 false = 贴图。
     */
    private void renderEchoSpyglass(GuiGraphics graphics) {
        if (ModConfig.Client.SPYGLASS_GUI_RENDER_MODEL.get()) {
            this.renderEchoSpyglassModel(graphics);
        } else {
            this.renderEchoSpyglassTexture(graphics);
        }
    }

    /**
     * 渲染 3D 物品模型。
     * 位置/大小由 ITEM_X, ITEM_Y, ITEM_SCALE 控制。
     * 注意：模型顶点是 0~16 方块单位，ItemRenderer 内部会 /16，
     * 所以乘 16 补回来；中心在 (0.5,0.5,0.5)。
     */
    private void renderEchoSpyglassModel(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        ItemStack stack = new ItemStack(ModItems.ECHO_SPYGLASS.get());

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(this.leftPos + ITEM_X, this.topPos + ITEM_Y, 100);

        pose.scale(ITEM_SCALE * 16.0F, ITEM_SCALE * 16.0F, ITEM_SCALE * 16.0F);

        // 自由度1：绕X轴 → 躺平/俯仰（镜头朝向玩家）
        pose.mulPose(Axis.XP.rotationDegrees(-45));
        // 自由度2：绕Y轴 → 水平转向（3/4侧视角）
        pose.mulPose(Axis.YP.rotationDegrees(45));
        // 自由度3：绕Z轴 → 屏幕平面内歪斜
        pose.mulPose(Axis.ZP.rotationDegrees(85));

        pose.translate(-0.5F, -0.5F, -0.5F);

        itemRenderer.renderStatic(stack, ItemDisplayContext.NONE,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                pose, mc.renderBuffers().bufferSource(), mc.level, 0);

        graphics.flush();
        pose.popPose();
    }

    /**
     * 渲染平面贴图。
     * 位置/大小由 TEXTURE_X, TEXTURE_Y, TEXTURE_SIZE 控制，
     * 与 3D 模型的 ITEM_X/ITEM_Y/ITEM_SCALE 完全独立。
     */
    private void renderEchoSpyglassTexture(GuiGraphics graphics) {
        this.ensureTextureSize();
        if (this.texWidth <= 0 || this.texHeight <= 0) {
            return; // 贴图缺失或读取失败：不绘制，避免紫黑块
        }

        // 保持宽高比：宽度固定 TEXTURE_SIZE，高度等比缩放
        int w = TEXTURE_SIZE;
        int h = Math.max(1, (int) Math.round((float) this.texHeight * TEXTURE_SIZE / this.texWidth));

        // 以 (TEXTURE_X, TEXTURE_Y) 为中心
        int x = this.leftPos + TEXTURE_X - w / 2;
        int y = this.topPos + TEXTURE_Y - h / 2;

        // 用 PoseStack 把 1:1 像素绘制缩放到目标 w×h
        // （blit 的 9 参重载按原图 1:1 画，缩放交给 PoseStack，避免用不存在的缩放 blit 重载）
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0F);
        pose.scale((float) w / this.texWidth, (float) h / this.texHeight, 1.0F);
        graphics.blit(ECHO_OUTLINE_TEXTURE, 0, 0, 0, 0, this.texWidth, this.texHeight, this.texWidth, this.texHeight);
        pose.popPose();
    }

    /**
     * 从资源里读取贴图的真实像素尺寸，只读一次并缓存。
     * 1.21.1 的 AbstractTexture 没有 getWidth/getHeight，所以需要解码一次 PNG 拿尺寸。
     */
    private void ensureTextureSize() {
        if (this.texWidth != -1) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Optional<Resource> opt = mc.getResourceManager().getResource(ECHO_OUTLINE_TEXTURE);
        if (opt.isPresent()) {
            try (InputStream in = opt.get().open();
                 NativeImage img = NativeImage.read(in)) {
                this.texWidth = img.getWidth();
                this.texHeight = img.getHeight();
                return;
            } catch (IOException e) {
                // 读取失败，落到下面置 0
            }
        }
        this.texWidth = 0;
        this.texHeight = 0;
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 绘制标题
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
        // 绘制物品栏标签
        graphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0x404040, false);
    }
}