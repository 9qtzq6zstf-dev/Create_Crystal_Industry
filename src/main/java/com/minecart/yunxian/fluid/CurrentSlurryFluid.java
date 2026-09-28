package com.minecart.yunxian.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * 电流浆的流体本体：除了「能淌」之外，还负责<b>液面上飞火星</b>。
 * <p>
 * 粒子写在 {@link #animateTick} 里，这是原版给流体配粒子的正规写法（原版
 * {@code LavaFluid.animateTick} 就是这么撒岩浆火星的），好处是<b>只对玩家附近的液面调用</b>——
 * 不需要自己扫区块，也不会出现"远处也在算"的浪费。两条粒子：
 * <ul>
 *   <li><b>飞出的火星</b>：照岩浆的结构（1/N 概率、液面之上、带初速），只是把岩浆粒子换成
 *       {@code electric_spark}，初速往上给足，看着就是被电出来的火星；</li>
 *   <li><b>贴着液面的细碎火花</b>：小概率、几乎不动的火花，让液面本身在"滋滋"闪。</li>
 * </ul>
 * 没做岩浆那种"噼啪"音效：手上没有合适的电击音源，硬套岩浆音效反而串味；要做的话在这里补
 * {@code level.playLocalSound(...)} 即可。
 * <p>
 * {@link Source} 与 {@link Flowing} 必须各写一份 {@code animateTick}：NeoForge 的这两个类
 * 是兄弟关系，没有共同的可继承基类（不像原版 {@code LavaFluid.Flowing extends LavaFluid}），
 * 所以粒子逻辑抽在本类的静态方法里，两边只留一行调用。
 */
public final class CurrentSlurryFluid {

    /** 火星从液面飞出的概率（每次 animateTick 判定，原版岩浆是 1/100，我们的浆更活跃一点） */
    private static final int EMBER_CHANCE = 60;

    /** 贴着液面闪烁的细碎火花概率 */
    private static final int FLICKER_CHANCE = 8;

    private CurrentSlurryFluid() {
    }

    static void animateSlurry(Level level, BlockPos pos, RandomSource random) {
        BlockPos above = pos.above();
        // 与岩浆同款前提：液面之上是空气（或至少不是实心方块）才看得见火花
        if (!level.getBlockState(above).isAir() || level.getBlockState(above).isSolidRender(level, above)) {
            return;
        }

        if (random.nextInt(EMBER_CHANCE) == 0) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + 1.0;
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                    (random.nextDouble() - 0.5) * 0.08,
                    0.10 + random.nextDouble() * 0.08,
                    (random.nextDouble() - 0.5) * 0.08);
        }

        if (random.nextInt(FLICKER_CHANCE) == 0) {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + 0.9 + random.nextDouble() * 0.15;
            double z = pos.getZ() + random.nextDouble();
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z, 0.0, 0.01, 0.0);
        }
    }

    /** 源（静止）流体 */
    public static class Source extends BaseFlowingFluid.Source {
        public Source(Properties properties) {
            super(properties);
        }

        @Override
        public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
            animateSlurry(level, pos, random);
        }
    }

    /** 流动变体 */
    public static class Flowing extends BaseFlowingFluid.Flowing {
        public Flowing(Properties properties) {
            super(properties);
        }

        @Override
        public void animateTick(Level level, BlockPos pos, FluidState state, RandomSource random) {
            animateSlurry(level, pos, random);
        }
    }
}
