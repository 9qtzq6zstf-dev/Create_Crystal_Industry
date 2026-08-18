package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class OreConvertingBuddingBlock extends GenericBuddingBlock {
    private static final int ORE_CONVERSION_CHANCE = 20;

    private final Block stoneOre;
    private final Block deepslateOre;

    public OreConvertingBuddingBlock(int growthChance, Properties properties, Block smallBud, Block mediumBud,
                                     Block largeBud, Block cluster, Block stoneOre, Block deepslateOre) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster);
        this.stoneOre = stoneOre;
        this.deepslateOre = deepslateOre;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (random.nextInt(ORE_CONVERSION_CHANCE) == 0) {
            tryConvertNearbyStone(level, pos, random);
        }
    }

    private void tryConvertNearbyStone(ServerLevel level, BlockPos centerPos, RandomSource random) {
        BlockPos targetPos = centerPos.offset(
                random.nextInt(3) - 1,
                random.nextInt(3) - 1,
                random.nextInt(3) - 1
        );
        if (targetPos.equals(centerPos)) {
            return;
        }

        Block targetBlock = level.getBlockState(targetPos).getBlock();
        if (targetBlock == Blocks.STONE) {
            level.setBlockAndUpdate(targetPos, stoneOre.defaultBlockState());
        } else if (targetBlock == Blocks.DEEPSLATE) {
            level.setBlockAndUpdate(targetPos, deepslateOre.defaultBlockState());
        }
    }
}
