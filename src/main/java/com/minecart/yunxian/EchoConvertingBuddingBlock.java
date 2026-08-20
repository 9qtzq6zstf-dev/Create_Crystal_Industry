package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class EchoConvertingBuddingBlock extends GenericBuddingBlock {

    /**
     * 转化触发概率：每次 randomTick 有 1/CHANCE 概率转化一个方块。
     * 普通母岩是 1/20（OreConvertingBuddingBlock 里的 ORE_CONVERSION_CHANCE），
     * 回响母岩更快，这里取 1/4（约快 5 倍）。想更快改成 2 或 1，更慢则调大。
     */
    private static final int CONVERSION_CHANCE = 4;

    /**
     * 转化半径：普通母岩是 1（3x3x3），回响母岩大一格，取 2（5x5x5）。
     */
    private static final int CONVERSION_RADIUS = 2;

    /**
     * 可被回响转化的方块（土类与石类）。
     * 内容由 data/create_crystal_industry/tags/block/echo_convertible.json 定义，可用数据包调整。
     */
    private static final TagKey<Block> ECHO_CONVERTIBLE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "echo_convertible")
    );

    public EchoConvertingBuddingBlock(int growthChance, Properties properties,
                                      Block smallBud, Block mediumBud, Block largeBud, Block cluster) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 先执行水晶生长逻辑（与普通母岩完全一致）
        super.randomTick(state, level, pos, random);

        // 再执行幽匿转化（范围更大、速度更快）
        if (random.nextInt(CONVERSION_CHANCE) == 0) {
            tryConvertNearby(level, pos, random);
        }
    }

    private void tryConvertNearby(ServerLevel level, BlockPos centerPos, RandomSource random) {
        int r = CONVERSION_RADIUS;
        BlockPos targetPos = centerPos.offset(
                random.nextInt(2 * r + 1) - r,   // -2..2
                random.nextInt(2 * r + 1) - r,   // -2..2
                random.nextInt(2 * r + 1) - r    // -2..2
        );
        if (targetPos.equals(centerPos)) {
            return;  // 不转化母岩自身
        }

        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.is(ECHO_CONVERTIBLE)) {
            level.setBlockAndUpdate(targetPos, Blocks.SCULK.defaultBlockState());
        }
    }
}