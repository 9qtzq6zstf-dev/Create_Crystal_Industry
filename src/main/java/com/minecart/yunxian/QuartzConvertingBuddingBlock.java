package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class QuartzConvertingBuddingBlock extends GenericBuddingBlock {

    /**
     * 闪长岩 → 平滑石英：每次 randomTick 有 1/DIORITE_CONVERSION_CHANCE 触发一次尝试。
     */
    private static final int DIORITE_CONVERSION_CHANCE = 20;

    /**
     * 平滑石英 → 本母岩：每次 randomTick 有 1/SMOOTH_QUARTZ_SPREAD_CHANCE 触发一次尝试。
     */
    private static final int SMOOTH_QUARTZ_SPREAD_CHANCE = 25000;

    public QuartzConvertingBuddingBlock(int growthChance, Properties properties, Block smallBud, Block mediumBud,
                                        Block largeBud, Block cluster) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);

        if (random.nextInt(DIORITE_CONVERSION_CHANCE) == 0) {
            tryConvertNearbyDiorite(level, pos, random);
        }

        if (random.nextInt(SMOOTH_QUARTZ_SPREAD_CHANCE) == 0) {
            trySpreadToSmoothQuartz(level, pos, random);
        }
    }

    private void tryConvertNearbyDiorite(ServerLevel level, BlockPos centerPos, RandomSource random) {
        BlockPos targetPos = centerPos.offset(
                random.nextInt(3) - 1,
                random.nextInt(3) - 1,
                random.nextInt(3) - 1
        );
        if (targetPos.equals(centerPos)) {
            return;
        }

        if (level.getBlockState(targetPos).is(Blocks.NETHERRACK)) {
            level.setBlockAndUpdate(targetPos, Blocks.NETHER_QUARTZ_ORE.defaultBlockState());
        }
    }

    private void trySpreadToSmoothQuartz(ServerLevel level, BlockPos centerPos, RandomSource random) {
        BlockPos targetPos = centerPos.offset(
                random.nextInt(3) - 1,
                random.nextInt(3) - 1,
                random.nextInt(3) - 1
        );
        if (targetPos.equals(centerPos)) {
            return;
        }

        if (level.getBlockState(targetPos).is(Blocks.SMOOTH_QUARTZ)) {
            level.setBlockAndUpdate(targetPos, defaultBlockState());
        }
    }
}