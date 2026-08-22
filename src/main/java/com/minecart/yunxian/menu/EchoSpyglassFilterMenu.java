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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.List;

public class EchoSpyglassFilterMenu extends AbstractContainerMenu {

    private final InteractionHand hand;
    private final Inventory playerInventory;
    private final SimpleContainer filterContainer;

    // 客户端构造：从同步数据里读出手
    public EchoSpyglassFilterMenu(int containerId, Inventory playerInventory, FriendlyByteBuf extraData) {
        this(containerId, playerInventory, extraData.readEnum(InteractionHand.class));
    }

    // 服务端构造
    public EchoSpyglassFilterMenu(int containerId, Inventory playerInventory, InteractionHand hand) {
        super(ModMenus.ECHO_FILTER_MENU.get(), containerId);
        this.playerInventory = playerInventory;
        this.hand = hand;
        this.filterContainer = loadFilter(playerInventory.player.getItemInHand(hand));

        // 0: 过滤器槽
        this.addSlot(new Slot(filterContainer, 0, 56, 17) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EchoSpyglassItem.canBeFilter(stack);
            }
        });

        // 1-27: 玩家主物品栏
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        // 28-36: 快捷栏
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }
    }

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

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index == 0) {
                if (!this.moveItemStackTo(stack, 1, 37, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (EchoSpyglassItem.canBeFilter(stack)) {
                    if (!this.moveItemStackTo(stack, 0, 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (index < 28) {
                    if (!this.moveItemStackTo(stack, 28, 37, false)) {
                        return ItemStack.EMPTY;
                    }
                } else {
                    if (!this.moveItemStackTo(stack, 1, 28, false)) {
                        return ItemStack.EMPTY;
                    }
                }
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }
}