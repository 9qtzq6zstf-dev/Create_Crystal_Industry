package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class FlammableIceBuddingBlock extends GenericBuddingBlock {
    private static final Direction[] DIRECTIONS = Direction.values();

    public FlammableIceBuddingBlock(int growthChance, Properties properties, Block smallBud, Block mediumBud,
                                    Block largeBud, Block cluster) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(growthChance) != 0) {
            return;
        }

        Direction side = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos neighborPos = pos.relative(side);
        BlockState neighborState = level.getBlockState(neighborPos);

        Block nextBlock = null;
        if (canGrowAt(neighborState)) {
            nextBlock = smallBud;
        } else if (neighborState.is(smallBud) && sameFacing(neighborState, side)) {
            nextBlock = mediumBud;
        } else if (neighborState.is(mediumBud) && sameFacing(neighborState, side)) {
            nextBlock = largeBud;
        } else if (neighborState.is(largeBud) && sameFacing(neighborState, side)) {
            nextBlock = cluster;
        }

        if (nextBlock != null) {
            BlockState newState = nextBlock.defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, side)
                    .setValue(AmethystClusterBlock.WATERLOGGED,
                            neighborState.getFluidState().getType() == Fluids.WATER);
            level.setBlockAndUpdate(neighborPos, newState);
        }
    }

    // ★ 修复：必须是"空位"且是水源方块才算可长新芽
    // 这样 waterlogged 的芽/簇不会误判为新芽位置，能正常进阶
    private static boolean canGrowAt(BlockState state) {
        return (state.isAir() || state.canBeReplaced())
                && state.getFluidState().getType() == Fluids.WATER
                && state.getFluidState().isSource();
    }

    private static boolean sameFacing(BlockState state, Direction side) {
        return state.getValue(AmethystClusterBlock.FACING) == side;
    }
}