package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class MechanicalCleanerMenu extends AbstractContainerMenu {
    public static final int SLOTS = 27;

    /** 上方容器面板高度（自绘贴图区域） */
    public static final int CONTAINER_PANEL_HEIGHT = 83;
    /** Create 玩家物品栏面板高 108，内部三行起始 +14、快捷栏 +68 */
    public static final int INVENTORY_ROW_START = CONTAINER_PANEL_HEIGHT + 14; // 97
    public static final int HOTBAR_Y = CONTAINER_PANEL_HEIGHT + 68;            // 151

    private final ItemStackHandler inventory;
    private final BlockPos pos;

    // 服务端：createMenu 调用（带真实库存）
    public MechanicalCleanerMenu(int id, Inventory playerInventory, ItemStackHandler inventory, BlockPos pos) {
        super(ModMenus.MECHANICAL_CLEANER.get(), id);
        this.inventory = inventory;
        this.pos = pos;
        addSlots(playerInventory);
    }

    // 客户端/网络包工厂：从 RegistryFriendlyByteBuf 读 BlockPos
    public MechanicalCleanerMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(id, playerInventory, new ItemStackHandler(SLOTS), extraData.readBlockPos());
    }

    private void addSlots(Inventory playerInventory) {
        // 主容器 27 格（3×9），潜影盒式布局：第一行 y=18，位于自绘容器面板内
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new SlotItemHandler(inventory, col + row * 9, 8 + col * 18, 18 + row * 18));
            }
        }

        // 玩家背包 27 格（Create 面板内 +14/+32/+50）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_ROW_START + row * 18));
            }
        }
        // 快捷栏 9 格（Create 面板内 +68）
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, HOTBAR_Y));
        }
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack itemstack = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            itemstack = stack.copy();
            if (index < SLOTS) {
                if (!this.moveItemStackTo(stack, SLOTS, SLOTS + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(stack, 0, SLOTS, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == itemstack.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return itemstack;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}