package com.minecart.yunxian;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class MechanicalCleanerBlockEntity extends KineticBlockEntity implements MenuProvider {

    public static final int INVENTORY_SIZE = 27;

    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE);

    public MechanicalCleanerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_CLEANER.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    // ===== 容器 UI =====

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.create_crystal_industry.mechanical_cleaner");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new MechanicalCleanerMenu(id, playerInventory, inventory, worldPosition);
    }

    // ===== NBT 持久化（Create 的 write/read，saveAdditional 是 final 不能覆写） =====

    @Override
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (!clientPacket) {
            tag.put("Inventory", inventory.serializeNBT(registries));
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            return;
        }
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
    }

    // ===== 红石锁（保持不变） =====

    private boolean isRedstoneLocked() {
        return getBlockState().getValue(MechanicalCleanerBlock.POWERED);
    }

    @Override
    public float getSpeed() {
        if (isRedstoneLocked())
            return 0;
        return super.getSpeed();
    }

    public float getTrueSpeed() {
        return super.getSpeed();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide)
            return;

        if (getSpeed() == 0)
            return;

        // TODO: 吸尘/清理功能（等确认）
    }
}