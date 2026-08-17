package com.minecart.yunxian;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;

public class SmartDrillBlockEntity extends KineticBlockEntity implements MenuProvider {

    private final ItemStackHandler filterInventory = new ItemStackHandler(1) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }
    };
    private boolean silkTouchMode = false;
    private final SimpleContainerData data = new SimpleContainerData(1);

    private int breakProgress = 0;
    private BlockPos targetPos = null;
    private int totalTicks = 0;
    private int lastBreakStage = -1;

    public SmartDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_DRILL.get(), pos, state);
    }

    public ItemStackHandler getFilterInventory() {
        return filterInventory;
    }

    public boolean getSilkTouchMode() {
        return silkTouchMode;
    }

    public void setSilkTouchMode(boolean mode) {
        this.silkTouchMode = mode;
        data.set(0, mode ? 1 : 0);
        // 同步到方块状态
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(SmartDrillBlock.SILK_TOUCH)) {
                level.setBlock(worldPosition, state.setValue(SmartDrillBlock.SILK_TOUCH, mode), 3);
            }
        }
        setChanged();
    }

    @Override
    public void tick() {
        super.tick();

        if (level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        // 从方块状态同步模式（外部修改）
        syncSilkTouchFromState();

        if (overStressed || getSpeed() == 0) {
            resetProgress();
            return;
        }

        Direction facing = getBlockState().getValue(SmartDrillBlock.FACING);
        BlockPos currentTarget = worldPosition.relative(facing);
        BlockState targetState = level.getBlockState(currentTarget);

        if (!currentTarget.equals(targetPos)) {
            resetProgress();
            targetPos = currentTarget;
        }

        float hardness = targetState.getDestroySpeed(level, targetPos);
        if (targetState.isAir() || hardness < 0) {
            resetProgress();
            return;
        }

        ItemStack filter = filterInventory.getStackInSlot(0);
        if (!filter.isEmpty()) {
            ItemStack targetItem = targetState.getBlock().asItem().getDefaultInstance();
            if (!ItemStack.isSameItem(filter, targetItem)) {
                resetProgress();
                return;
            }
        }

        float rpm = Math.abs(getSpeed());
        if (rpm == 0) {
            resetProgress();
            return;
        }

        totalTicks = (int) ((45 * hardness) / rpm * 20);
        totalTicks = Math.max(1, totalTicks);

        breakProgress++;

        // 挖掘音效
        if (breakProgress % 5 == 0) {
            level.playSound(null, targetPos, targetState.getSoundType().getHitSound(),
                    SoundSource.BLOCKS, 0.5f, 1.0f);
        }

        // 更新裂纹
        int currentStage = (int) (10.0f * breakProgress / totalTicks);
        currentStage = Math.min(currentStage, 9);
        if (currentStage != lastBreakStage && currentStage < 10) {
            int fakePlayerId = worldPosition.hashCode();
            serverLevel.destroyBlockProgress(fakePlayerId, targetPos, currentStage);
            lastBreakStage = currentStage;
        }

        if (breakProgress >= totalTicks) {
            serverLevel.destroyBlockProgress(worldPosition.hashCode(), targetPos, -1);
            destroyBlock(targetState, targetPos);
            resetProgress();
        }
    }

    private void destroyBlock(BlockState state, BlockPos pos) {
        boolean silk = getBlockState().getValue(SmartDrillBlock.SILK_TOUCH);
        if (silk) {
            // 精准采集：掉落自身
            level.destroyBlock(pos, false);
            ItemStack drop = new ItemStack(state.getBlock());
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(
                    level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, drop
            ));
        } else {
            level.destroyBlock(pos, true);
        }
    }

    private void resetProgress() {
        if (targetPos != null && level instanceof ServerLevel serverLevel) {
            serverLevel.destroyBlockProgress(worldPosition.hashCode(), targetPos, -1);
        }
        breakProgress = 0;
        lastBreakStage = -1;
    }

    private void syncSilkTouchFromState() {
        if (level != null) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(SmartDrillBlock.SILK_TOUCH)) {
                boolean stateMode = state.getValue(SmartDrillBlock.SILK_TOUCH);
                if (this.silkTouchMode != stateMode) {
                    this.silkTouchMode = stateMode;
                    data.set(0, stateMode ? 1 : 0);
                    setChanged();
                }
            }
        }
    }

    // ========== NBT ==========
    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        compound.put("FilterInventory", filterInventory.serializeNBT(registries));
        compound.putBoolean("SilkTouchMode", silkTouchMode);
    }

    @Override
    public void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        filterInventory.deserializeNBT(registries, compound.getCompound("FilterInventory"));
        silkTouchMode = compound.getBoolean("SilkTouchMode");
        data.set(0, silkTouchMode ? 1 : 0);
        // 同步到方块状态（服务端）
        if (level != null && !level.isClientSide) {
            BlockState state = level.getBlockState(worldPosition);
            if (state.hasProperty(SmartDrillBlock.SILK_TOUCH)) {
                level.setBlock(worldPosition, state.setValue(SmartDrillBlock.SILK_TOUCH, silkTouchMode), 3);
            }
        }
    }

    // ========== MenuProvider ==========
    @Override
    public Component getDisplayName() {
        return Component.translatable("block.yunxian.smart_drill");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new SmartDrillMenu(id, inventory, this, data);
    }

    public SimpleContainer getInventoryForDrop() {
        SimpleContainer container = new SimpleContainer(1);
        for (int i = 0; i < filterInventory.getSlots(); i++) {
            container.setItem(i, filterInventory.getStackInSlot(i));
        }
        return container;
    }
}