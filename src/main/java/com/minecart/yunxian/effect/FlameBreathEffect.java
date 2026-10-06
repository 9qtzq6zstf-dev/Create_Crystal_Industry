package com.minecart.yunxian.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「火焰吐息」：喝下可燃冰圣代之后，接下来这一分钟里都能朝前喷火，身上还一直冒着火星。
 * <p>
 * <b>喷火本身不在这里</b>——{@link FlameBreath} 拿这颗效果当开关：只要身上有它，按住潜行键
 * 就会朝前喷火。本类只管「身上那圈火星」这点视觉。
 * 之所以单独拆一个效果、而不是继续挂在「冰封」上：两者的时长本来就不是一回事。
 * 冰封是喝下去那一下的代价（10 秒），喷火才是这件玩具的正题（60 秒），合用一个效果就没法各自调时长。
 * <p>
 * 身上的火有两层，各管一件事：
 * <ul>
 *   <li><b>效果自带的那个粒子</b>换成 {@link ParticleTypes#LAVA}，把默认的"药水粒子"顶掉。
 *       两参构造器给的是 {@code ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 颜色)}——
 *       就是那颗围着人转的琥珀色药水漩涡。换成 LAVA 之后<b>无论谁发放这颗效果</b>
 *       （吃圣代也好、{@code /effect give} 也好）看到的都是火。</li>
 *   <li><b>火焰形状的那些</b>走 {@code ServerLevel#sendParticles}，见下面
 *       {@link #applyEffectTick}。</li>
 * </ul>
 * <b>效果自带的那个粒子为什么不能直接用 {@code ParticleTypes.FLAME}</b>：原版
 * {@code LivingEntity#tickEffects} 撒它时速度是<b>写死的 (1, 1, 1)</b>——位置随机、速度不随机。
 * 而 {@code FlameParticle} 继承 {@code RisingParticle}，那个构造器只把速度乘 0.01 再加回去
 * （等于原样保留），配 0.96 的阻尼 ≈ 能斜着飞二三十格，实测就是"好几个粒子斜着射到天上去"。
 * {@code ParticleTypes.LAVA} 则相反：它的 provider <b>把传进来的速度整个丢掉</b>
 * （{@code new LavaParticle(level, x, y, z)}，自己给一点向上的初速），所以在这里是安全的。
 * <p>
 * 名字与管喷火的那个 {@link FlameBreath} 是一套的：这个类管「有没有资格」+ 身上的火，
 * 那个类管「喷出来是什么样」。图标文件名跟着效果 id 走（{@code mob_effect/flame_breath.png}），
 * 改 id 时记得连它一起改，否则 HUD 上会变成紫黑块。
 */
public class FlameBreathEffect extends MobEffect {

    /** 颜色：暖琥珀色，和喷出来的那口黄火对上 */
    public static final int COLOR = 0xFFB347;

    /** 每隔这么多 tick 补一轮火星。一分钟里按这个密度撒，是"身上在烧"又不至于糊住视线的一档 */
    private static final int PARTICLE_INTERVAL_TICKS = 4;

    /** 每轮撒几颗 */
    private static final int PARTICLE_COUNT = 2;

    /**
     * 粒子的初速度。
     * <p>
     * <b>必须是 0</b>：这个参数是喂给"随机方向 × 该值"的，给大了就是斜着飞出去。
     * 0 就是原地浮起再自然消散。
     */
    private static final double PARTICLE_SPEED = 0.0;

    public FlameBreathEffect() {
        // 三参，把默认那颗药水漩涡换成火（挑 LAVA 的理由见类注释：它会丢掉传进来的速度）
        super(MobEffectCategory.NEUTRAL, COLOR, ParticleTypes.LAVA);
    }

    /**
     * 必须每 tick 都响。
     * <p>
     * 1.21 的默认实现是 {@code return false}（每 tick 只判定一次、不调用 {@code applyEffectTick}），
     * 不覆写的话下面那段一次都不会跑。
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // 只让服务端撒：sendParticles 会广播给附近玩家，本地玩家自己也看得见
        if (entity.level() instanceof ServerLevel level
                && entity.tickCount % PARTICLE_INTERVAL_TICKS == 0) {
            level.sendParticles(ParticleTypes.FLAME,
                    entity.getX(), entity.getY() + entity.getBbHeight() * 0.5, entity.getZ(),
                    PARTICLE_COUNT,
                    entity.getBbWidth() * 0.4, entity.getBbHeight() * 0.35, entity.getBbWidth() * 0.4,
                    PARTICLE_SPEED);
        }
        return true;
    }
}
