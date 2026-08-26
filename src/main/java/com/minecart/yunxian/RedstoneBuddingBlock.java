package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

public class RedstoneBuddingBlock extends OreConvertingBuddingBlock {
    public RedstoneBuddingBlock(int growthChance, BlockBehaviour.Properties properties,
                                Block smallBud, Block mediumBud, Block largeBud, Block cluster,
                                Supplier<Block> stoneOre, Supplier<Block> deepslateOre,
                                Supplier<Block> rawOreBlock) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster,
                stoneOre, deepslateOre, rawOreBlock);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 15;
    }
}