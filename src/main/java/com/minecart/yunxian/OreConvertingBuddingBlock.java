package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class OreConvertingBuddingBlock extends GenericBuddingBlock {
    private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.ore_budding");

    private static final int ORE_CONVERSION_CHANCE = 20;

    /**
     * 粗矿块传播为母岩的概率：每次 randomTick 有 1/RAW_SPREAD_CHANCE 触发一次传播尝试。
     */
    private static final int RAW_SPREAD_CHANCE = 25000;

    private final Supplier<Block> stoneOre;
    private final Supplier<Block> deepslateOre;
    private final Supplier<Block> rawOreBlock;

    private Block resolvedStoneOre;
    private Block resolvedDeepslateOre;
    private Block resolvedRawOreBlock;

    public OreConvertingBuddingBlock(int growthChance, Properties properties, Block smallBud, Block mediumBud,
                                     Block largeBud, Block cluster,
                                     Supplier<Block> stoneOre, Supplier<Block> deepslateOre,
                                     Supplier<Block> rawOreBlock) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster);
        this.stoneOre = stoneOre;
        this.deepslateOre = deepslateOre;
        this.rawOreBlock = rawOreBlock;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);

        // 原有：石头/深板岩 → 对应矿石
        if (random.nextInt(ORE_CONVERSION_CHANCE) == 0) {
            tryConvertNearbyStone(level, pos, random);
        }

        // 新增：对应粗矿块 → 本母岩（极低概率）
        if (random.nextInt(RAW_SPREAD_CHANCE) == 0) {
            trySpreadToRawOre(level, pos, random);
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
        Block stoneOre = stoneOre();
        Block deepslateOre = deepslateOre();

        if (stoneOre != Blocks.AIR && targetBlock == Blocks.STONE) {
            level.setBlockAndUpdate(targetPos, stoneOre.defaultBlockState());
        } else if (deepslateOre != Blocks.AIR && targetBlock == Blocks.DEEPSLATE) {
            level.setBlockAndUpdate(targetPos, deepslateOre.defaultBlockState());
        }
    }

    /**
     * 与石头→矿石逻辑一致：3x3x3（半径1）内随机一格，
     * 若是本母岩对应的粗矿块，则转化为本母岩自身。
     */
    private void trySpreadToRawOre(ServerLevel level, BlockPos centerPos, RandomSource random) {
        Block rawOreBlock = rawOreBlock();
        if (rawOreBlock == Blocks.AIR) {
            return;  // 解析失败时彻底禁用，绝不把空气当匹配目标
        }

        BlockPos targetPos = centerPos.offset(
                random.nextInt(3) - 1,
                random.nextInt(3) - 1,
                random.nextInt(3) - 1
        );
        if (targetPos.equals(centerPos)) {
            return;
        }

        if (level.getBlockState(targetPos).is(rawOreBlock)) {
            level.setBlockAndUpdate(targetPos, defaultBlockState());
        }
    }

    // ---- 延迟解析 + 缓存：首次随机刻时注册表已冻结，查到的结果才是真实方块 ----

    private Block stoneOre() {
        if (resolvedStoneOre == null) {
            resolvedStoneOre = resolve(stoneOre, "stone ore");
        }
        return resolvedStoneOre;
    }

    private Block deepslateOre() {
        if (resolvedDeepslateOre == null) {
            resolvedDeepslateOre = resolve(deepslateOre, "deepslate ore");
        }
        return resolvedDeepslateOre;
    }

    private Block rawOreBlock() {
        if (resolvedRawOreBlock == null) {
            resolvedRawOreBlock = resolve(rawOreBlock, "raw ore block");
        }
        return resolvedRawOreBlock;
    }

    private static Block resolve(Supplier<Block> supplier, String description) {
        Block block = supplier.get();
        if (block == Blocks.AIR) {
            LOGGER.error("[OreBudding] 未能解析目标方块（{}）——资源位置写错或该方块不存在，相关转化已禁用", description);
        }
        return block;
    }
}