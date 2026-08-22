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
        // vanilla 的一切真实放入/取出路径都被这两个 false 挡住；
        // 槽内容只有 clicked()/quickMoveStack()/setVirtualFilter() 的显式 set() 能改变。
        this.addSlot(new Slot(filterContainer, 0, 56, 17) {
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

        // 主物品栏 1..27 ↔ 背包索引 9..35
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 快捷栏 28..36 ↔ 背包索引 0..8
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
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
                    filterSlot.set(ItemStack.EMPTY);                     // 空手点击：清除
                } else if (EchoSpyglassItem.isGhostAllowed(cursor)) {
                    ItemStack copy = cursor.copy();
                    copy.setCount(1);
                    filterSlot.set(copy);                                // 存入副本，光标分毫不动
                }
            }
            case THROW -> filterSlot.set(ItemStack.EMPTY);               // Q：只清除，不产生掉落物
            case QUICK_MOVE -> filterSlot.set(ItemStack.EMPTY);          // shift 点过滤槽：清除
            case SWAP -> {                                               // 数字键 / 副手交换
                ItemStack swapSource;
                if (button >= 0 && button < 9) {
                    swapSource = player.getInventory().getItem(button);  // 快捷栏第 button 格
                } else {
                    swapSource = player.getOffhandItem();                // button == 40：副手
                }

                if (swapSource.isEmpty()) {
                    filterSlot.set(ItemStack.EMPTY);
                } else if (EchoSpyglassItem.isGhostAllowed(swapSource)) {
                    ItemStack copy = swapSource.copy();
                    copy.setCount(1);
                    filterSlot.set(copy);                                // 副本入槽，源物品不动
                }
            }
            case QUICK_CRAFT, PICKUP_ALL, CLONE -> { }                   // 拖拽/双击收集/创造中键：不参与
            default -> { }
        }
        // 不调用 super：这个槽的一切 vanilla 操作都被短路
    }

    // ==================== shift 快速转移 ====================

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == 0) {
            // shift 点击过滤槽：仅清除幽灵
            this.slots.get(0).set(ItemStack.EMPTY);
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();

        if (EchoSpyglassItem.isGhostAllowed(stack)) {
            // 幽灵转移：副本写入过滤槽，源堆叠原封不动
            ItemStack copy = stack.copy();
            copy.setCount(1);
            this.slots.get(0).set(copy);
            return ItemStack.EMPTY;   // 返回空 = 未移动任何真实物品
        }

        // 不允许的物品：仅在背包内部移动（目标范围不含过滤槽）
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

    // ==================== 幽灵写入入口（服务端，供以后 JEI 调用） ====================

    public Slot getFilterSlot() {
        return this.slots.get(0);
    }

    /** 服务端入口：把任意来源的物品设为虚拟过滤 */
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