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

    // ===== 定时器配置（每 tick 工作） =====
    private static final int INTERVAL_TICKS = 1;      // 每 tick 执行一次
    // 不再使用概率过滤，每次都调用 randomTick

    private int tickCounter = 0;
    private final EnergyStorage energyStorage;

    public AcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCELERATOR.get(), pos, state);
        this.energyStorage = new EnergyStorage(MAX_ENERGY, MAX_RECEIVE, 0);
    }

    // ===== 核心 tick 逻辑 =====
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (level.isClientSide() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        // 1. 定时器（每 tick 都工作）
        tickCounter++;
        if (tickCounter % INTERVAL_TICKS != 0) {
            return;
        }

        // 2. 能量检查
        if (energyStorage.getEnergyStored() < ENERGY_COST_PER_OPERATION) {
            // 能量不足时，更新 POWERED 为 false
            if (state.getValue(AcceleratorBlock.POWERED)) {
                level.setBlock(pos, state.setValue(AcceleratorBlock.POWERED, false), 3);
            }
            return;
        }

        // 3. 同步 POWERED 状态（能量充足时显示激活）
        if (!state.getValue(AcceleratorBlock.POWERED)) {
            level.setBlock(pos, state.setValue(AcceleratorBlock.POWERED, true), 3);
        }

        // 4. 对周围六个方向施加随机刻（无概率过滤）
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);

            // 调用 randomTick，由方块自己决定是否生长
            neighborState.randomTick(serverLevel, neighborPos, serverLevel.random);
        }

        // 5. 消耗能量（固定消耗，无论成功与否）
        energyStorage.extractEnergy(ENERGY_COST_PER_OPERATION, false);
    }

    // ===== IEnergyStorage 实现 =====
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        return energyStorage.receiveEnergy(maxReceive, simulate);
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0; // 不允许外部提取能量
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

    // ===== 获取 Capability（用于其他模组连接） =====
    @Nullable
    public IEnergyStorage getEnergyCapability(@Nullable Direction side) {
        return this;
    }
}