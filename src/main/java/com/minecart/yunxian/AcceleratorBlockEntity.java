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
    private static final int INTERVAL_TICKS = 1;

    /** 相邻催生器之间每 tick 的最大传输量 */
    private static final int MAX_TRANSFER_PER_TICK = 500;

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

        transmitToNeighbors();   // 相邻催生器互传电力

        tickCounter++;
        if (tickCounter % INTERVAL_TICKS != 0) {
            return;
        }

        int stored = energyStorage.getEnergyStored();

        // 完全没有能量：关闭并返回
        if (stored <= 0) {
            if (state.getValue(AcceleratorBlock.POWERED)) {
                level.setBlock(pos, state.setValue(AcceleratorBlock.POWERED, false), 3);
            }
            return;
        }

        // 残电不足一次操作：直接扣光到 0，不执行催生
        if (stored < ENERGY_COST_PER_OPERATION) {
            energyStorage.extractEnergy(stored, false);
            if (state.getValue(AcceleratorBlock.POWERED)) {
                level.setBlock(pos, state.setValue(AcceleratorBlock.POWERED, false), 3);
            }
            setChanged();
            return;
        }

        // 足额：正常消耗并催生
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

    private void transmitToNeighbors() {
        if (level == null || level.isClientSide)
            return;

        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(dir);
            BlockEntity neighbor = level.getBlockEntity(neighborPos);
            if (!(neighbor instanceof AcceleratorBlockEntity other))
                continue;
            if (other.isRemoved())
                continue;

            int myStored = energyStorage.getEnergyStored();
            int otherStored = other.getEnergyStored();

            // 只由能量多的一端向少的一端推；
            // 同一时刻一对里不可能两端都“更多”，因此每 tick 每对至多发生一次传输
            if (myStored <= otherStored)
                continue;

            int deficit = other.getMaxEnergyStored() - otherStored;
            if (deficit <= 0)
                continue;

            // 传输量 = min(邻居缺口, 我的存量, 每tick上限, 差值一半)
            // 差值一半防止单次过冲导致两边角色互换、来回震荡
            int remaining = Math.min(
                    Math.min(deficit, Math.min(myStored, MAX_TRANSFER_PER_TICK)),
                    (myStored - otherStored) / 2
            );
            if (remaining <= 0)
                continue;

            // 循环调用 receiveEnergy，应对邻居 maxReceive=100 的单次上限
            int acceptedTotal = 0;
            while (remaining > 0) {
                int accepted = other.receiveEnergy(remaining, false);
                if (accepted <= 0)
                    break;
                acceptedTotal += accepted;
                remaining -= accepted;
            }

            if (acceptedTotal > 0) {
                energyStorage.extractEnergy(acceptedTotal, false);
                setChanged();
            }
        }
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