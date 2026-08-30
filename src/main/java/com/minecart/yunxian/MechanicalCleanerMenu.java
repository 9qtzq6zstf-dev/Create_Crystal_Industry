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

    // ==================== 槽位几何常量 ====================

    /** 单个槽位的边长（标准格子 18×18px） */
    private static final int SLOT_SIZE = 18;

    /** 容器与玩家背包的行数（3 行） */
    private static final int SLOT_ROWS = 3;

    /** 快捷栏格数（9 格） */
    private static final int HOTBAR_COUNT = 9;

    /** 玩家背包在玩家库存中的起始索引（0-8 为快捷栏，9 起为背包） */
    private static final int PLAYER_INVENTORY_START = 9;

    // ==================== 吸尘器容器槽位位置 ====================

    /** 容器第一列槽位相对 GUI 左边缘的 X（对应贴图槽位框） */
    public static final int CONTAINER_SLOT_X = 25;

    /** 容器第一行槽位相对 GUI 顶部的 Y：容器面板上移 10px 后（原 24 − 10 = 14） */
    public static final int CONTAINER_ROW0_Y = 14;

    // ==================== 玩家槽位位置 ====================

    /** 玩家第一列槽位相对 GUI 左边缘的 X：比容器列右移 8px，对齐 Create 面板内部槽位（33） */
    public static final int PLAYER_SLOT_X = 33;

    /** 玩家背包第一行相对 GUI 顶部的 Y：玩家面板顶部 115 + 面板内首行偏移 18 = 133 */
    public static final int PLAYER_ROW0_Y = 133;

    /** 快捷栏相对 GUI 顶部的 Y：玩家面板顶部 115 + 面板内快捷栏偏移 76 = 191 */
    public static final int HOTBAR_Y = 191;

    // ==================== 菜单数据 ====================

    /** 吸尘器库存（服务端为真实库存，客户端为占位空容器） */
    private final ItemStackHandler inventory;

    /** 方块位置：用于 stillValid 与按钮包回写 */
    private final BlockPos pos;

    /** 当前吸取距离（格数）：1~8 */
    private final int suckRange;

    // ---- 服务端：由方块实体 createMenu 调用，携带真实库存与吸取距离 ----
    public MechanicalCleanerMenu(int id, Inventory playerInventory, ItemStackHandler inventory, BlockPos pos, int suckRange) {
        super(ModMenus.MECHANICAL_CLEANER.get(), id);
        this.inventory = inventory;
        this.pos = pos;
        this.suckRange = suckRange;
        addSlots(playerInventory);
    }

    // ---- 客户端：由网络包工厂调用，从 RegistryFriendlyByteBuf 读出方块位置与吸取距离 ----
    public MechanicalCleanerMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        this(id, playerInventory, new ItemStackHandler(SLOTS), extraData.readBlockPos(), extraData.readInt());
    }

    public int getSuckRange() {
        return suckRange;
    }

    /**
     * 滚轮配置回调：客户端点击包到达服务端后，
     * 以按钮 id 作为新的吸取距离写回方块实体。
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (player.level().getBlockEntity(pos) instanceof MechanicalCleanerBlockEntity be) {
            be.setSuckRange(id);
            return true;
        }
        return false;
    }

    /**
     * 布置三组槽位：
     * 1. 吸尘器容器 27 格（3×9）
     * 2. 玩家背包 27 格（3×9）
     * 3. 玩家快捷栏 9 格（1×9）
     */
    private void addSlots(Inventory playerInventory) {
        // 1. 吸尘器容器：列从 CONTAINER_SLOT_X 起，行从 CONTAINER_ROW0_Y 起
        for (int row = 0; row < SLOT_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int x = CONTAINER_SLOT_X + col * SLOT_SIZE;
                int y = CONTAINER_ROW0_Y + row * SLOT_SIZE;
                int index = col + row * 9; // 按行优先排列
                addSlot(new SlotItemHandler(inventory, index, x, y));
            }
        }

        // 2. 玩家背包：列从 PLAYER_SLOT_X 起，行从 PLAYER_ROW0_Y 起；库存索引从 9 开始
        for (int row = 0; row < SLOT_ROWS; row++) {
            for (int col = 0; col < 9; col++) {
                int x = PLAYER_SLOT_X + col * SLOT_SIZE;
                int y = PLAYER_ROW0_Y + row * SLOT_SIZE;
                int index = PLAYER_INVENTORY_START + col + row * 9;
                addSlot(new Slot(playerInventory, index, x, y));
            }
        }

        // 3. 玩家快捷栏：单行，列从 PLAYER_SLOT_X 起，位于 HOTBAR_Y
        for (int col = 0; col < HOTBAR_COUNT; col++) {
            int x = PLAYER_SLOT_X + col * SLOT_SIZE;
            addSlot(new Slot(playerInventory, col, x, HOTBAR_Y));
        }
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    /**
     * 快捷移动：
     * - 从容器取出的物品移入玩家区（槽位 27~62）
     * - 从玩家区取出的物品移入容器（槽位 0~26）
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index < SLOTS) {
                // 容器 → 玩家
                if (!this.moveItemStackTo(stack, SLOTS, SLOTS + 36, true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                // 玩家 → 容器
                if (!this.moveItemStackTo(stack, 0, SLOTS, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (stack.isEmpty()) {
                slot.set(ItemStack.EMPTY);
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

    /** 玩家需在方块 8 格（平方距离 64）内才能继续操作 */
    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64.0;
    }
}