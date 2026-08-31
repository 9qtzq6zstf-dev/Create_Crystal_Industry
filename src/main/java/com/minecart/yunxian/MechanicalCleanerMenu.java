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

    /** 吸尘器容器格数：3 行 × 9 列 = 27 */
    public static final int SLOTS = 27;

    /** 按钮 id：风向切换（每按一次切换正/反） */
    public static final int DIRECTION_BUTTON_ID = 200;

    /** 按钮 id 偏移：风力格数滚轮通过 300 + value 区分 */
    public static final int RANGE_BUTTON_OFFSET = 300;

    // ==================== 槽位几何常量 ====================

    private static final int SLOT_SIZE = 18;
    private static final int SLOT_ROWS = 3;
    private static final int HOTBAR_COUNT = 9;
    private static final int PLAYER_INVENTORY_START = 9;

    // ==================== 吸尘器容器槽位位置 ====================

    public static final int CONTAINER_SLOT_X = 25;
    public static final int CONTAINER_ROW0_Y = 14;

    // ==================== 玩家槽位位置 ====================

    public static final int PLAYER_SLOT_X = 33;
    public static final int PLAYER_ROW0_Y = 133;
    public static final int HOTBAR_Y = 191;

    // ==================== 菜单数据 ====================

    /** 吸尘器库存（服务端为真实库存，客户端为占位空容器） */
    private final ItemStackHandler inventory;

    /** 方块位置：用于 stillValid 与按钮包回写 */
    private final BlockPos pos;

    /** 当前风力格数：1~20 */
    private final int suckRange;

    /** 当前风向 */
    private final MechanicalCleanerFilterBehaviour.RotationDirection direction;

    // ---- 服务端 ----
    public MechanicalCleanerMenu(int id, Inventory playerInventory, ItemStackHandler inventory, BlockPos pos,
                                 int suckRange,
                                 MechanicalCleanerFilterBehaviour.RotationDirection direction) {
        super(ModMenus.MECHANICAL_CLEANER.get(), id);
        this.inventory = inventory;
        this.pos = pos;
        this.suckRange = suckRange;
        this.direction = direction;
        addSlots(playerInventory);
    }

    // ---- 客户端：从网络包读出 位置 + 风力格数 + 风向 ----
    public MechanicalCleanerMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(id, playerInventory, new ItemStackHandler(SLOTS), extraData.readBlockPos(),
                extraData.readInt(),
                extraData.readBoolean() ? MechanicalCleanerFilterBehaviour.RotationDirection.REVERSED
                        : MechanicalCleanerFilterBehaviour.RotationDirection.NORMAL);
    }

    public int getSuckRange() {
        return suckRange;
    }

    public MechanicalCleanerFilterBehaviour.RotationDirection getDirection() {
        return direction;
    }

    /**
     * 按钮回调分流：
     * - id == DIRECTION_BUTTON_ID：切换风向
     * - id >= RANGE_BUTTON_OFFSET：风力格数（300 + 1~20）
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().getBlockEntity(pos) instanceof MechanicalCleanerBlockEntity be) {
            if (id == DIRECTION_BUTTON_ID) {
                be.toggleDirection();
            } else if (id >= RANGE_BUTTON_OFFSET) {
                be.setSuckRange(id - RANGE_BUTTON_OFFSET);
            } else {
                return false;
            }
            return true;
        }
        return false;
    }

    private void addSlots(Inventory playerInventory) {
        // 1. 吸尘器容器 27 格
        for (int row = 0; row < SLOT_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int x = CONTAINER_SLOT_X + col * SLOT_SIZE;
                int y = CONTAINER_ROW0_Y + row * SLOT_SIZE;
                int index = col + row * 9;
                addSlot(new SlotItemHandler(inventory, index, x, y));
            }
        }
        // 2. 玩家背包 27 格
        for (int row = 0; row < SLOT_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int x = PLAYER_SLOT_X + col * SLOT_SIZE;
                int y = PLAYER_ROW0_Y + row * SLOT_SIZE;
                int index = PLAYER_INVENTORY_START + col + row * 9;
                addSlot(new Slot(playerInventory, index, x, y));
            }
        }
        // 3. 快捷栏 9 格
        for (int col = 0; col < HOTBAR_COUNT; col++) {
            int x = PLAYER_SLOT_X + col * SLOT_SIZE;
            addSlot(new Slot(playerInventory, col, x, HOTBAR_Y));
        }
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index < SLOTS) {
                if (!this.moveItemStackTo(stack, SLOTS, SLOTS + 36, true))
                    return ItemStack.EMPTY;
            } else {
                if (!this.moveItemStackTo(stack, 0, SLOTS, false))
                    return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount())
                return ItemStack.EMPTY;
            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}