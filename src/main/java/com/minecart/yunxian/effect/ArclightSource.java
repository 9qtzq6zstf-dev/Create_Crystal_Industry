package com.minecart.yunxian.effect;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.registry.ModFluids;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.registries.DeferredBlock;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/**
 * 「弧光石的场」——<b>弧光石系列方块</b>（母岩 + 三级芽 + 晶簇）与<b>电流浆</b>的统一定义，
 * 以及方块上那几颗电火花。
 * <p>
 * 判定只写这一份：服务端的 {@link ElectrifiedAura}（给生物挂「感电」）用它；
 * 粒子由各方块自己的 {@code animateTick} 调 {@link #maybeSpark}（原版写法，只在客户端、
 * 只对玩家附近的方块调用，不需要自己扫区块）。
 * <p>
 * 只认<b>真淌在世界里</b>的电流浆：装在储罐、管道、工作盆里的不算——要判那些得挨个翻方块实体，
 * 而这些调用点每几 tick 就要扫一大片位置，划不来。
 */
public final class ArclightSource {

    /** 每次 {@code animateTick} 撒火花的概率（1/N） */
    private static final int SPARK_CHANCE = 5;

    /** 懒解析的弧光石家族方块表：首次用到时注册表早已冻结 */
    private static Set<Block> familyBlocks;

    private ArclightSource() {
    }

    /**
     * 按概率在方块上方撒一颗电火花。调用点只应是各方块的 {@code animateTick}（客户端）。
     * <p>
     * 位置刻意放在<b>方块顶面之上</b>（y 偏移 0.9–1.3）而不是方块内部：spawn 在体积里的话，
     * 粒子会被方块自身的几何挡住——从某些角度看就是"这颗晶体没有粒子"，
     * 而晶簇本来就是一圈薄薄的交叉面，挡得格外彻底。
     */
    public static void maybeSpark(Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(SPARK_CHANCE) != 0) {
            return;
        }
        double x = pos.getX() + 0.2 + random.nextDouble() * 0.6;
        double y = pos.getY() + 0.9 + random.nextDouble() * 0.4;
        double z = pos.getZ() + 0.2 + random.nextDouble() * 0.6;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                (random.nextDouble() - 0.5) * 0.03,
                0.01 + random.nextDouble() * 0.02,
                (random.nextDouble() - 0.5) * 0.03);
    }

    /** 该位置是弧光石系列方块，或是电流浆 */
    public static boolean isSource(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) {
            return false; // 绝大多数位置都是空气，先短路
        }
        if (familyBlocks().contains(state.getBlock())) {
            return true;
        }
        return state.getBlock() instanceof LiquidBlock
                && state.getFluidState().getFluidType() == ModFluids.CURRENT_SLURRY_TYPE.get();
    }

    private static Set<Block> familyBlocks() {
        Set<Block> cached = familyBlocks;
        if (cached == null) {
            List<DeferredBlock<Block>> blocks = BuddingFamilies.ARCLIGHT.blocks();
            cached = blocks.stream().map(DeferredBlock::get).collect(Collectors.toUnmodifiableSet());
            familyBlocks = cached;
        }
        return cached;
    }
}
