package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

/**
 * 云仙母岩方块
 * 生长逻辑灵感来源于 BuddingCrystals 模组 (LGPL)
 * @see <a href="https://github.com/lluiscx/BuddingCrystals">BuddingCrystals</a>
 */
public class YunxianBuddingBlock extends BuddingAmethystBlock {
    private static final Direction[] DIRECTIONS = Direction.values();

    // 生长概率：每次随机刻有 1/growthChance 的概率尝试生长
    private final int growthChance;

    public YunxianBuddingBlock(int growthChance, Properties properties) {
        super(properties);
        this.growthChance = growthChance;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 如果被水淹没则不生长
        if (!level.getFluidState(pos).isEmpty()) {
            return;
        }

        // 概率判定
        if (random.nextInt(growthChance) != 0) {
            return;
        }

        // 随机选择一个方向
        Direction side = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos neighborPos = pos.relative(side);
        BlockState neighborState = level.getBlockState(neighborPos);

        Block nextBlock = null;

        // 判断当前邻居方块的类型，决定下一个要生长的阶段
        if (canGrowAt(neighborState)) {
            // 空位 → 小芽
            nextBlock = ModBlocks.ROSE_QUARTZ_SMALL_BUD.get();
        } else if (neighborState.is(ModBlocks.ROSE_QUARTZ_SMALL_BUD.get()) && sameFacing(neighborState, side)) {
            // 小芽（朝向正确）→ 中芽
            nextBlock = ModBlocks.ROSE_QUARTZ_MEDIUM_BUD.get();
        } else if (neighborState.is(ModBlocks.ROSE_QUARTZ_MEDIUM_BUD.get()) && sameFacing(neighborState, side)) {
            // 中芽（朝向正确）→ 大芽
            nextBlock = ModBlocks.ROSE_QUARTZ_LARGE_BUD.get();
        } else if (neighborState.is(ModBlocks.ROSE_QUARTZ_LARGE_BUD.get()) && sameFacing(neighborState, side)) {
            // 大芽（朝向正确）→ 完整簇
            nextBlock = ModBlocks.ROSE_QUARTZ_CLUSTER.get();
        }

        // 如果有下一个阶段，则更新方块
        if (nextBlock != null) {
            BlockState newState = nextBlock.defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, side)
                    .setValue(AmethystClusterBlock.WATERLOGGED,
                            neighborState.getFluidState().getType() == Fluids.WATER);
            level.setBlockAndUpdate(neighborPos, newState);
        }
    }

    /**
     * 检查给定方块状态是否可以作为水晶生长的起始位置（空气或可替换方块）
     */
    private static boolean canGrowAt(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    /**
     * 检查芽/簇的朝向是否与母岩尝试生长的方向一致
     */
    private static boolean sameFacing(BlockState state, Direction side) {
        return state.getValue(AmethystClusterBlock.FACING) == side;
    }
}