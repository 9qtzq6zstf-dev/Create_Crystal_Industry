package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class SmartDrillMenu extends AbstractContainerMenu {

    private final BlockPos blockPos;
    private final ContainerData data;

    // 服务端创建
    public SmartDrillMenu(
            int id,
            Inventory playerInv,
            SmartDrillBlockEntity be,
            ContainerData data
    ) {
        super(ModMenus.SMART_DRILL.get(), id);

        this.blockPos = be.getBlockPos();
        this.data = data;

        addPlayerInventory(playerInv);

        addDataSlots(data);
    }

    // 客户端创建
    public SmartDrillMenu(
            int id,
            Inventory playerInv,
            BlockPos blockPos
    ) {
        super(ModMenus.SMART_DRILL.get(), id);

        this.blockPos = blockPos;
        this.data = new SimpleContainerData(1);

        addPlayerInventory(playerInv);

        addDataSlots(data);
    }

    private void addPlayerInventory(Inventory playerInv) {

        // 玩家背包
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {

                addSlot(
                        new Slot(
                                playerInv,
                                col + row * 9 + 9,
                                8 + col * 18,
                                84 + row * 18
                        )
                );
            }
        }

        // 快捷栏
        for (int col = 0; col < 9; col++) {

            addSlot(
                    new Slot(
                            playerInv,
                            col,
                            8 + col * 18,
                            142
                    )
            );
        }
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public boolean getSilkTouchMode() {
        return data.get(0) == 1;
    }

    @Override
    public ItemStack quickMoveStack(
            Player player,
            int index
    ) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {

        return player.distanceToSqr(
                blockPos.getX() + 0.5,
                blockPos.getY() + 0.5,
                blockPos.getZ() + 0.5
        ) < 64;
    }
}