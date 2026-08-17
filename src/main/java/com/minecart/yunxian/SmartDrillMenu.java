package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public class SmartDrillMenu extends AbstractContainerMenu {
    private final IItemHandler filterHandler;
    private final ContainerData data;
    private final BlockPos blockPos;

    public SmartDrillMenu(int id, Inventory playerInv, SmartDrillBlockEntity be, ContainerData data) {
        super(ModMenus.SMART_DRILL.get(), id);
        this.filterHandler = be != null ? be.getFilterInventory() : null;
        this.data = data;
        this.blockPos = be != null ? be.getBlockPos() : BlockPos.ZERO;

        if (this.filterHandler != null) {
            addSlot(new SlotItemHandler(filterHandler, 0, 80, 35));
        }

        // 玩家物品栏
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public boolean getSilkTouchMode() {
        return data.get(0) == 1;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }
}