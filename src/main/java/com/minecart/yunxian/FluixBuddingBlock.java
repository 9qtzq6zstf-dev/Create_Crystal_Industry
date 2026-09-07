package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * 福鲁伊克斯母岩：仅在 AE2 加载时由 ModBlocks 注册。
 * AE2 无福鲁伊克斯矿石，故不做“石头→矿石”转化；
 * 保留通用生长 + 相邻 ae2:fluix_block → 本母岩 的再生传播，
 * 对齐 OreConvertingBuddingBlock 的粗矿块传播（1/25000）。
 */
public class FluixBuddingBlock extends GenericBuddingBlock {
    private static final int FLUIX_SPREAD_CHANCE = 25000;

    private final Supplier<Block> fluixBlockSupplier;
    private Block resolvedFluixBlock;

    public FluixBuddingBlock(int growthChance, BlockBehaviour.Properties properties,
                             Block smallBud, Block mediumBud, Block largeBud, Block cluster,
                             Supplier<Block> fluixBlockSupplier) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster);
        this.fluixBlockSupplier = fluixBlockSupplier;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);
        if (random.nextInt(FLUIX_SPREAD_CHANCE) == 0) {
            trySpread(level, pos, random);
        }
    }

    private void trySpread(ServerLevel level, BlockPos centerPos, RandomSource random) {
        Block fluixBlock = resolvedFluixBlock();
        if (fluixBlock == null || fluixBlock == Blocks.AIR) {
            return;
        }
        BlockPos targetPos = centerPos.offset(
                random.nextInt(3) - 1,
                random.nextInt(3) - 1,
                random.nextInt(3) - 1
        );
        if (targetPos.equals(centerPos)) {
            return;
        }
        if (level.getBlockState(targetPos).is(fluixBlock)) {
            level.setBlockAndUpdate(targetPos, defaultBlockState());
        }
    }

    private Block resolvedFluixBlock() {
        if (resolvedFluixBlock == null) {
            resolvedFluixBlock = fluixBlockSupplier.get();
        }
        return resolvedFluixBlock;
    }
}