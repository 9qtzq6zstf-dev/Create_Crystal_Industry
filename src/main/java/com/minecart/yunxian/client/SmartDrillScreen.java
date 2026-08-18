package com.minecart.yunxian.client;

import com.minecart.yunxian.SilkTouchModePacket;
import com.minecart.yunxian.SmartDrillFilterPacket;
import com.minecart.yunxian.SmartDrillMenu;
import com.minecart.yunxian.SmartDrillBlockEntity;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;

public class SmartDrillScreen extends AbstractContainerScreen<SmartDrillMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.parse(
                    "create_crystal_industry:textures/gui/smart_drill.png"
            );

    private Button toggleButton;

    // 过滤槽的位置
    private static final int FILTER_X = 80;
    private static final int FILTER_Y = 35;
    private static final int FILTER_SIZE = 18;

    public SmartDrillScreen(
            SmartDrillMenu menu,
            Inventory playerInv,
            Component title
    ) {
        super(menu, playerInv, title);

        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();

        // =========================
        // 精准采集 / 普通模式按钮
        // =========================

        this.toggleButton = Button.builder(
                        Component.literal(""),
                        b -> {
                            boolean current = menu.getSilkTouchMode();
                            boolean newMode = !current;

                            PacketDistributor.sendToServer(
                                    new SilkTouchModePacket(
                                            menu.getBlockPos(),
                                            newMode
                                    )
                            );
                        }
                )
                .bounds(
                        leftPos + 80,
                        topPos + 10,
                        40,
                        20
                )
                .build();

        this.addRenderableWidget(toggleButton);

        updateButtonText();
    }

    private void updateButtonText() {
        if (toggleButton != null) {
            boolean mode = menu.getSilkTouchMode();

            toggleButton.setMessage(
                    Component.literal(
                            mode ? "精准" : "普通"
                    )
            );
        }
    }

    // =========================================================
    // 绘制
    // =========================================================

    @Override
    public void render(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        this.renderBackground(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        super.render(
                guiGraphics,
                mouseX,
                mouseY,
                partialTick
        );

        // 绘制过滤槽
        renderFilterSlot(
                guiGraphics,
                mouseX,
                mouseY
        );

        this.renderTooltip(
                guiGraphics,
                mouseX,
                mouseY
        );

        updateButtonText();
    }

    @Override
    protected void renderBg(
            GuiGraphics guiGraphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
        guiGraphics.blit(
                TEXTURE,
                leftPos,
                topPos,
                0,
                0,
                imageWidth,
                imageHeight
        );
    }

    // =========================================================
    // 过滤栏
    // =========================================================

    private void renderFilterSlot(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY
    ) {
        int x = leftPos + FILTER_X;
        int y = topPos + FILTER_Y;

        // 槽位背景
        guiGraphics.fill(
                x,
                y,
                x + FILTER_SIZE,
                y + FILTER_SIZE,
                0xFF8B8B8B
        );

        // 槽位内部
        guiGraphics.fill(
                x + 1,
                y + 1,
                x + FILTER_SIZE - 1,
                y + FILTER_SIZE - 1,
                0xFF373737
        );

        if (minecraft == null || minecraft.level == null) {
            return;
        }

        BlockEntity blockEntity =
                minecraft.level.getBlockEntity(menu.getBlockPos());

        if (!(blockEntity instanceof SmartDrillBlockEntity be)) {
            return;
        }

        if (be.getFiltering() == null) {
            return;
        }

        ItemStack filter =
                be.getFiltering().getFilter();

        if (filter.isEmpty()) {
            return;
        }

        // 绘制过滤物品
        guiGraphics.renderItem(
                filter,
                x + 1,
                y + 1
        );

        // 绘制数量
        guiGraphics.renderItemDecorations(
                this.font,
                filter,
                x + 1,
                y + 1
        );

        // 鼠标悬停 Tooltip
        if (isMouseOverFilter(mouseX, mouseY)) {
            guiGraphics.renderTooltip(
                    this.font,
                    filter,
                    mouseX,
                    mouseY
            );
        }
    }



    private boolean isMouseOverFilter(
            double mouseX,
            double mouseY
    ) {
        int x = leftPos + FILTER_X;
        int y = topPos + FILTER_Y;

        return mouseX >= x
                && mouseX < x + FILTER_SIZE
                && mouseY >= y
                && mouseY < y + FILTER_SIZE;
    }

    // =========================================================
    // 鼠标点击
    // =========================================================

    @Override
    public boolean mouseClicked(
            double mouseX,
            double mouseY,
            int button
    ) {
        if (isMouseOverFilter(mouseX, mouseY)) {

            /*
             * 左键：
             * 使用玩家当前手中的物品设置过滤器
             */
            if (button == 0) {

                ItemStack held =
                        this.minecraft.player.getMainHandItem();

                if (!held.isEmpty()) {

                    PacketDistributor.sendToServer(
                            new SmartDrillFilterPacket(
                                    menu.getBlockPos(),
                                    held.copyWithCount(1)
                            )
                    );

                    return true;
                }
            }

            /*
             * 右键：
             * 清除过滤器
             */
            if (button == 1) {

                PacketDistributor.sendToServer(
                        new SmartDrillFilterPacket(
                                menu.getBlockPos(),
                                ItemStack.EMPTY
                        )
                );

                return true;
            }
        }

        return super.mouseClicked(
                mouseX,
                mouseY,
                button
        );
    }
}