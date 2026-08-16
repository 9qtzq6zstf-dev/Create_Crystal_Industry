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

    // ===== 能量配置 =====
    private static final int MAX_ENERGY = 1000;
    private static final int MAX_RECEIVE = 100;
    private static final int ENERGY_COST_PER_OPERATION = 10;

    // ===== 定时器配置 =====
    private static final int INTERVAL_TICKS = 1;

    private int tickCounter = 0;
    private final EnergyStorage energyStorage;

    public AcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCELERATOR.get(), pos, state);
        this.energyStorage = new EnergyStorage(MAX_ENERGY, MAX_RECEIVE, MAX_ENERGY);
    }

    // ===== 核心 tick 逻辑 =====
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
            BlockState neighborState = level.getBlockState(neighborPos);
            neighborState.randomTick(serverLevel, neighborPos, serverLevel.random);
        }

        energyStorage.extractEnergy(ENERGY_COST_PER_OPERATION, false);
    }

    // ===== IEnergyStorage 实现 =====
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return energyStorage.receiveEnergy(maxReceive, simulate);
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

    // ===== 数据持久化 =====
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Energy")) {
            energyStorage.receiveEnergy(tag.getInt("Energy"), false);
        }
    }

    // ===== 获取 Capability =====
    @Nullable
    public IEnergyStorage getEnergyCapability(@Nullable Direction side) {
        return this;
    }
}