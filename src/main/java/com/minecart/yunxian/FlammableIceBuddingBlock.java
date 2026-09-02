package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

public class FlammableIceBuddingBlock extends GenericBuddingBlock implements EntityBlock {
    private static final Direction[] DIRECTIONS = Direction.values();

    public FlammableIceBuddingBlock(int growthChance, Properties properties, Block smallBud, Block mediumBud,
                                    Block largeBud, Block cluster) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster);
    }

    // 纯展示用 BE：不参与任何生长逻辑，仅支撑护目镜信息显示
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FlammableIceBuddingBlockEntity(pos, state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextInt(growthChance) != 0) {
            return;
        }

        Direction side = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos neighborPos = pos.relative(side);
        BlockState neighborState = level.getBlockState(neighborPos);

        // ★ 修复：可燃冰只能在水中生长——长新芽与芽体进阶都要求目标格含水。
        //   waterlogged 的芽/簇其流体状态即水（inWater = true），不受影响；
        //   一旦芽体脱离水，进阶即停止。
        boolean inWater = neighborState.getFluidState().getType() == Fluids.WATER;

        Block nextBlock = null;
        if (inWater && canGrowNewBud(neighborState)) {
            nextBlock = smallBud;
        } else if (inWater && neighborState.is(smallBud) && sameFacing(neighborState, side)) {
            nextBlock = mediumBud;
        } else if (inWater && neighborState.is(mediumBud) && sameFacing(neighborState, side)) {
            nextBlock = largeBud;
        } else if (inWater && neighborState.is(largeBud) && sameFacing(neighborState, side)) {
            nextBlock = cluster;
        }

        if (nextBlock != null) {
            BlockState newState = nextBlock.defaultBlockState()
                    .setValue(AmethystClusterBlock.FACING, side)
                    .setValue(AmethystClusterBlock.WATERLOGGED, inWater);
            level.setBlockAndUpdate(neighborPos, newState);
        }
    }

    /**
     * 长新芽的条件：空位且为水源方块。
     * waterlogged 的芽/簇不可被替换（!isAir && !canBeReplaced），
     * 因此不会误判为新芽位置，会正常落入下方进阶分支。
     */
    private static boolean canGrowNewBud(BlockState state) {
        return (state.isAir() || state.canBeReplaced())
                && state.getFluidState().getType() == Fluids.WATER
                && state.getFluidState().isSource();
    }

    private static boolean sameFacing(BlockState state, Direction side) {
        return state.getValue(AmethystClusterBlock.FACING) == side;
    }
}