package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

public class AcceleratorBlockEntity extends BlockEntity implements IEnergyStorage {
    private static final int MAX_ENERGY = 10_000;
    private static final int MAX_RECEIVE = 100;
    private static final int ENERGY_COST_PER_OPERATION = 100;
    private static final int INTERVAL_TICKS = 3;

    private int tickCounter;
    private final EnergyStorage energyStorage;

    public AcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCELERATOR.get(), pos, state);
        energyStorage = new EnergyStorage(MAX_ENERGY, MAX_RECEIVE, MAX_ENERGY);
    }

    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        tickCounter++;
        if (tickCounter % INTERVAL_TICKS != 0) {
            return;
        }

        if (energyStorage.getEnergyStored() < ENERGY_COST_PER_OPERATION) {
            if (state.getValue(AcceleratorBlock.POWERED)) {
                level.setBlock(pos, state.setValue(AcceleratorBlock.POWERED, false), 3);
            }
            return;
        }

        if (!state.getValue(AcceleratorBlock.POWERED)) {
            level.setBlock(pos, state.setValue(AcceleratorBlock.POWERED, true), 3);
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            level.getBlockState(neighborPos).randomTick(serverLevel, neighborPos, serverLevel.random);
        }

        energyStorage.extractEnergy(ENERGY_COST_PER_OPERATION, false);
        setChanged();
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        int received = energyStorage.receiveEnergy(maxReceive, simulate);
        if (!simulate && received > 0) {
            setChanged();
        }
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    @Override
    public int getMaxEnergyStored() {
        return energyStorage.getMaxEnergyStored();
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) {
            int stored = Math.min(tag.getInt("Energy"), energyStorage.getMaxEnergyStored());
            energyStorage.extractEnergy(energyStorage.getEnergyStored(), false);
            energyStorage.receiveEnergy(stored, false);
        }
    }

    @Nullable
    public IEnergyStorage getEnergyCapability(@Nullable Direction side) {
        return this;
    }
}
