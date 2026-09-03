package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

public class GenericBuddingBlock extends BuddingAmethystBlock implements EntityBlock {
    private static final Direction[] DIRECTIONS = Direction.values();

    protected final int growthChance;
    protected final Block smallBud;    // ★ private → protected
    protected final Block mediumBud;   // ★ private → protected
    protected final Block largeBud;    // ★ private → protected
    protected final Block cluster;

    public GenericBuddingBlock(int growthChance, Properties properties, Block smallBud, Block mediumBud,
                               Block largeBud, Block cluster) {
        super(properties);
        this.growthChance = growthChance;
        this.smallBud = smallBud;
        this.mediumBud = mediumBud;
        this.largeBud = largeBud;
        this.cluster = cluster;
    }

    // 纯展示用共享 BE：不 tick、不存数据，仅支撑“当前生长速度”护目镜信息。
    // 子类（EchoConvertingBuddingBlock / FlammableIceBuddingBlock）已自行 override，
    // 仍使用各自的专用 BE，不受此方法影响。
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BuddingGrowthBlockEntity(pos, state);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!level.getFluidState(pos).isEmpty() || random.nextInt(growthChance) != 0) {
            return;
        }

        Direction side = DIRECTIONS[random.nextInt(DIRECTIONS.length)];
        BlockPos neighborPos = pos.relative(side);

        // ★ 新增：光照钩子。判断的是“晶簇将要生长的相邻格”，不是母岩自身
        //   （母岩是不透明方块，自身位置光照恒为 0，不能作为依据）。
        if (!canGrowAtLight(level, neighborPos)) {
            return;
        }

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

    /**
     * ★ 新增：子类可覆写以限制晶簇生长所需的光照。默认无限制（保持所有母岩原行为）。
     */
    protected boolean canGrowAtLight(ServerLevel level, BlockPos neighborPos) {
        return true;
    }

    private static boolean canGrowAt(BlockState state) {
        return state.isAir() || state.canBeReplaced();
    }

    private static boolean sameFacing(BlockState state, Direction side) {
        return state.getValue(AmethystClusterBlock.FACING) == side;
    }
}