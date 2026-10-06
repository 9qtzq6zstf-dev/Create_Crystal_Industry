package com.minecart.yunxian.effect;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「灼寒」：冻着的冰在烧。喝下可燃冰沙瓶或可燃冰圣代之后，既像扎进细雪里那样冻起来，
 * 又持续被灼烧。
 * <p>
 * 冻结那一半整个继承 {@link FrozenEffect}（霜花、冰心、发抖、免掉那份冻伤，全是它那套），
 * 本类只多做一件事：让它在烧。
 * <p>
 * <b>「在烧」只立那个同步标志，不碰 {@code remainingFireTicks}。</b>这是踩过坑才定的：
 * <ul>
 *   <li><b>真点火会响。</b>{@code Entity#baseTick} 的着火分支在清冻结值时会发一次
 *       {@code levelEvent(1009, pos, 1)}，而客户端 {@code LevelRenderer} 处理 1009 是分两支的——
 *       {@code data == 0} 播 {@code FIRE_EXTINGUISH}，{@code data == 1} 播
 *       {@code GENERIC_EXTINGUISH_FIRE}。<b>两支都会响</b>，于是每 tick 清一次就是每 tick 响一次。</li>
 *   <li><b>真点火会和冻结打架。</b>同一个分支顺手把冻结值清零，每 tick 都是"原版清零 → 我们再顶回去"。</li>
 * </ul>
 * 而玩家看到的那层火其实只认<b>另一个东西</b>：客户端 {@code Entity#isOnFire()} 是
 * {@code remainingFireTicks > 0 || (客户端 && getSharedFlag(0))}，第一人称的火焰由
 * {@code ScreenEffectRenderer} 拿 {@code player.isOnFire()} 当开关、第三人称那层由
 * {@code displayFireAnimation()} 决定，两个查的都是它。所以服务端只要每 tick
 * {@link Entity#setSharedFlagOnFire}(true) 把那个标志立起来，<b>外观照给</b>，
 * 而服务端的 {@code remainingFireTicks} 始终是 0 —— 原版那条分支整条不执行，
 * 既没有 1009 可发，也没有人来清零冻结值。
 * <p>
 * 顺序上刚好也站得住：{@code Entity#baseTick} 每 tick 会把标志按 {@code remainingFireTicks > 0}
 * 重算一遍（我们这里恒为 0，所以它先写成 false），而效果 tick 跑在 {@code LivingEntity#baseTick}
 * 的 {@code tickEffects()} 里、即那次重算<b>之后</b>，所以最终写进去的是我们的 true。
 * 效果一停，下一 tick 原版那次重算自然把标志抹掉，不需要收尾。
 * <p>
 * <b>灼烧伤害自己打</b>，口径与原版着火一致：每 {@value #BURN_INTERVAL_TICKS} tick 扣 1 点，
 * 伤害类型同样是 {@code onFire}（死亡信息是"被烧死"，抗火照样免疫——圣代已经不额外给抗火了）。
 * 火焰的粒子不归这里管：效果自带的粒子会被客户端以固定速度 (1,1,1) 甩出去（原版
 * {@code LivingEntity#tickEffects} 写死的），
 * 所以本类只用父类那个雪花粒子；要"身上冒火星"请见 {@link FlameBreathEffect}。
 */
public class ScorchingColdEffect extends FrozenEffect {

    /**
     * 隔多少 tick 烧一下。
     * <p>
     * 取 20 就是原版着火的速率：每 1000 ms 扣半颗心，200 tick 的效果里合计 10 点（5 颗心）。
     * 想改痛不痛就动这一个数——调大 = 间隔变长 = 更不痛。
     */
    private static final int BURN_INTERVAL_TICKS = 20;

    /** 每次烧掉多少点：原版着火也是 1 点 */
    private static final float BURN_DAMAGE = 1.0F;

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // 这一颗不着火，所以没人来清零，照常顶就行（父类那条"着火就让位"的岔路在这里用不上）
        pushFrozenVisuals(entity);

        // 抗火生物（烈焰人这类）既点不着也不该白挨这一下
        if (entity.level() instanceof ServerLevel && !entity.fireImmune()) {
            entity.setSharedFlagOnFire(true);

            if (entity.tickCount % BURN_INTERVAL_TICKS == 0) {
                entity.hurt(entity.damageSources().onFire(), BURN_DAMAGE);
            }
        }
        return true;
    }
}
