package com.minecart.yunxian.menu;

import com.minecart.yunxian.ModMenus;
import com.minecart.yunxian.item.EchoSpyglassItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

public class EchoSpyglassFilterMenu extends AbstractContainerMenu {

    // 玩家槽位整体下移偏移量（已保留，不影响过滤槽）
    private static final int PLAYER_SLOTS_Y_OFFSET = 22;

    private final InteractionHand hand;
    private final Inventory playerInventory;
    private final SimpleContainer filterContainer;

    // 客户端构造
    public EchoSpyglassFilterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readEnum(InteractionHand.class));
    }

    // 服务端构造
    public EchoSpyglassFilterMenu(int containerId, Inventory playerInventory, InteractionHand hand) {
        super(ModMenus.ECHO_FILTER_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.hand = hand;
        this.filterContainer = loadFilter(playerInventory.player.getItemInHand(hand));

        // ===== 幽灵槽：mayPlace / mayPickup 全部 false =====
        // ✅ 过滤槽位置已修改为 (79, 22)（原 56, 17 → 右移23，下移5）
        this.addSlot(new Slot(filterContainer, 0, 79, 22) {   // ← 坐标已修改
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }

            @Override
            public boolean mayPickup(Player player) {
                return false;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });

        // 主物品栏 1..27 ↔ 背包索引 9..35（已下移22像素）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9,
                        8 + col * 18,
                        84 + row * 18 + PLAYER_SLOTS_Y_OFFSET));
            }
        }

        // 快捷栏 28..36 ↔ 背包索引 0..8（已下移22像素）
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col,
                    8 + col * 18,
                    142 + PLAYER_SLOTS_Y_OFFSET));
        }
    }

    // ==================== 点击拦截：过滤槽 ====================
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId != 0 || slotId >= this.slots.size()) {
            super.clicked(slotId, button, clickType, player);
            return;
        }

        Slot filterSlot = this.slots.get(0);
        ItemStack cursor = this.getCarried();

        switch (clickType) {
            case PICKUP -> {
                if (cursor.isEmpty()) {
                    filterSlot.set(ItemStack.EMPTY);
                } else if (EchoSpyglassItem.isGhostAllowed(cursor)) {
                    ItemStack copy = cursor.copy();
                    copy.setCount(1);
                    filterSlot.set(copy);
                }
            }
            case THROW -> filterSlot.set(ItemStack.EMPTY);
            case QUICK_MOVE -> filterSlot.set(ItemStack.EMPTY);
            case SWAP -> {
                ItemStack swapSource;
                if (button >= 0 && button < 9) {
                    swapSource = player.getInventory().getItem(button);
                } else {
                    swapSource = player.getOffhandItem();
                }

                if (swapSource.isEmpty()) {
                    filterSlot.set(ItemStack.EMPTY);
                } else if (EchoSpyglassItem.isGhostAllowed(swapSource)) {
                    ItemStack copy = swapSource.copy();
                    copy.setCount(1);
                    filterSlot.set(copy);
                }
            }
            case QUICK_CRAFT, PICKUP_ALL, CLONE -> { }
            default -> { }
        }
    }

    // ==================== shift 快速转移 ====================

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == 0) {
            this.slots.get(0).set(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();

        if (EchoSpyglassItem.isGhostAllowed(stack)) {
            ItemStack copy = stack.copy();
            copy.setCount(1);
            this.slots.get(0).set(copy);
            return ItemStack.EMPTY;
        }

        boolean moved = index < 28
                ? this.moveItemStackTo(stack, 28, 37, false)
                : this.moveItemStackTo(stack, 1, 28, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }
        slot.setChanged();
        return stack;
    }

    // ==================== 幽灵写入入口 ====================

    public Slot getFilterSlot() {
        return this.slots.get(0);
    }

    public void setVirtualFilter(ItemStack requested) {
        if (requested == null || requested.isEmpty()) {
            this.slots.get(0).set(ItemStack.EMPTY);
            return;
        }
        if (!EchoSpyglassItem.isGhostAllowed(requested)) {
            return;
        }
        ItemStack copy = requested.copy();
        copy.setCount(1);
        this.slots.get(0).set(copy);
    }

    // ==================== 持久化 ====================

    private static SimpleContainer loadFilter(ItemStack spyglass) {
        SimpleContainer container = new SimpleContainer(1);
        if (spyglass.getItem() instanceof EchoSpyglassItem) {
            ItemContainerContents contents = spyglass.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
            List<ItemStack> items = contents.stream().toList();
            if (!items.isEmpty()) {
                container.setItem(0, items.get(0));
            }
        }
        return container;
    }

    @Override
    public void slotsChanged(Container container) {
        if (container == this.filterContainer) {
            saveFilter();
        }
        super.slotsChanged(container);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        saveFilter();
    }

    private void saveFilter() {
        if (playerInventory.player.level().isClientSide) {
            return;
        }
        ItemStack held = playerInventory.player.getItemInHand(hand);
        if (!(held.getItem() instanceof EchoSpyglassItem)) {
            return;
        }
        held.set(DataComponents.CONTAINER,
                ItemContainerContents.fromItems(List.of(filterContainer.getItem(0))));
        playerInventory.player.setItemInHand(hand, held);
        playerInventory.setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(hand).getItem() instanceof EchoSpyglassItem;
    }
}