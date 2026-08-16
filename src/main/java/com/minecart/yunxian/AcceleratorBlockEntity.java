package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
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

    // ===== 能量存储 =====
    private final EnergyStorage energyStorage;

    // ===== 定时器 =====
    private static final int INTERVAL_TICKS = 10;
    private int tickCounter = 0;

    public AcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ACCELERATOR.get(), pos, state);
        this.energyStorage = new EnergyStorage(MAX_ENERGY, MAX_RECEIVE, 0);
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
            return;
        }

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);

            if (!(neighborState.getBlock() instanceof YunxianBuddingBlock)) {
                continue;
            }

            energyStorage.extractEnergy(ENERGY_COST_PER_OPERATION, false);
            simulateBuddingGrowth(serverLevel, neighborPos, neighborState, serverLevel.random);
        }
    }

    private void simulateBuddingGrowth(ServerLevel level, BlockPos budPos, BlockState budState, RandomSource random) {
        if (!level.getFluidState(budPos).isEmpty()) {
            return;
        }

        if (random.nextInt(1) != 0) {
            return;
        }

        Direction direction = Direction.getRandom(random);
        BlockPos targetPos = budPos.relative(direction);
        BlockState targetState = level.getBlockState(targetPos);

        int clusterCount = 0;
        for (Direction dir : Direction.values()) {
            BlockPos neighborPos = targetPos.relative(dir);
            BlockState neighborState = level.getBlockState(neighborPos);
            Block block = neighborState.getBlock();
            if (block == ModBlocks.YUNXIAN_SMALL_BUD.get() ||
                    block == ModBlocks.YUNXIAN_MEDIUM_BUD.get() ||
                    block == ModBlocks.YUNXIAN_LARGE_BUD.get() ||
                    block == ModBlocks.YUNXIAN_CLUSTER.get()) {
                clusterCount++;
            }
        }
        if (clusterCount >= 4) {
            return;
        }

        Block nextBlock = null;
        Block targetBlock = targetState.getBlock();

        if (targetState.isAir() || targetState.canBeReplaced()) {
            nextBlock = ModBlocks.YUNXIAN_SMALL_BUD.get();
        } else if (targetBlock == ModBlocks.YUNXIAN_SMALL_BUD.get()) {
            nextBlock = ModBlocks.YUNXIAN_MEDIUM_BUD.get();
        } else if (targetBlock == ModBlocks.YUNXIAN_MEDIUM_BUD.get()) {
            nextBlock = ModBlocks.YUNXIAN_LARGE_BUD.get();
        } else if (targetBlock == ModBlocks.YUNXIAN_LARGE_BUD.get()) {
            nextBlock = ModBlocks.YUNXIAN_CLUSTER.get();
        } else {
            return;
        }

        BlockState newState = nextBlock.defaultBlockState()
                .setValue(AmethystClusterBlock.FACING, direction)
                .setValue(AmethystClusterBlock.WATERLOGGED, false);

        level.setBlockAndUpdate(targetPos, newState);
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