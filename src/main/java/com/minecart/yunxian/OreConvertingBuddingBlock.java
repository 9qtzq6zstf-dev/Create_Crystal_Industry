package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class OreConvertingBuddingBlock extends GenericBuddingBlock {

    // 矿石转化概率
    private static final int ORE_CONVERSION_CHANCE = 20;

    // 转化的矿石（石头→stoneOre，深板岩→deepslateOre）
    private final Block stoneOre;
    private final Block deepslateOre;

    public OreConvertingBuddingBlock(int growthChance, Properties properties,
                                     Block smallBud, Block mediumBud, Block largeBud, Block cluster,
                                     Block stoneOre, Block deepslateOre) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster);
        this.stoneOre = stoneOre;
        this.deepslateOre = deepslateOre;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 执行原有的水晶生长逻辑
        super.randomTick(state, level, pos, random);

        // 矿石转化逻辑
        if (random.nextInt(ORE_CONVERSION_CHANCE) == 0) {
            tryConvertNearbyStone(level, pos, random);
        }
    }

    private void tryConvertNearbyStone(ServerLevel level, BlockPos centerPos, RandomSource random) {
        int dx = random.nextInt(3) - 1;
        int dy = random.nextInt(3) - 1;
        int dz = random.nextInt(3) - 1;
        BlockPos targetPos = centerPos.offset(dx, dy, dz);

        if (targetPos.equals(centerPos)) {
            return;
        }

        BlockState targetState = level.getBlockState(targetPos);
        Block targetBlock = targetState.getBlock();

        BlockState newState = null;
        if (targetBlock == Blocks.STONE) {
            newState = stoneOre.defaultBlockState();
        } else if (targetBlock == Blocks.DEEPSLATE) {
            newState = deepslateOre.defaultBlockState();
        } else {
            return;
        }

        level.setBlockAndUpdate(targetPos, newState);
    }
}