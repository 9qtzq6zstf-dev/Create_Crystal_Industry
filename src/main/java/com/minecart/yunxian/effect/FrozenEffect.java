package com.minecart.yunxian.effect;

import com.minecart.yunxian.registry.ModEffects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 「冰封」：像一头扎进细雪里那样冻起来。
 * <p>
 * 外观不自己画——原版细雪那套表现全部挂在实体自己的冻结值
 * （{@code Entity#getTicksFrozen()}）上，本效果只负责把这个值往上顶，剩下白送：
 * <ul>
 *   <li><b>屏幕霜花</b>：{@code Gui} 用 {@code Player#getPercentFrozen()} 当不透明度；</li>
 *   <li><b>冰心</b>：{@code Gui} 在 {@code Player#isFullyFrozen()} 时换成冻住的心；</li>
 *   <li><b>人物发抖</b>：{@code LivingEntityRenderer#isShaking} 就是 {@code isFullyFrozen()}；</li>
 *   <li><b>冻伤</b>：{@code LivingEntity#aiStep} 里每 40 tick 扣 1 点（这条由 {@link #onIncomingDamage} 免掉）。</li>
 * </ul>
 * <p>
 * <b>为什么要顶到满冻线之上、又为什么要把伤害拦掉</b>：上面四条里有三条要求冻结值
 * {@code >= getTicksRequiredToFreeze()}（默认 140）——而扣血那条用的<b>是同一个阈值</b>，
 * 且判定就写在每 tick 扣减之后。也就是说「冻得够像」和「不掉血」在原版里是同一件事，
 * 想只留前者就只能把伤害那一份拿掉：服务端照常走到那一行，我们在事件里把它取消。
 * <p>
 * <b>这个类是「顶冻结值」这套机制的出处，{@link ScorchingColdEffect} 继承它</b>：两者外观一模一样，
 * 区别只在于要不要额外灼烧。本类只有冻，所以着火时让位（见 {@link #applyEffectTick}）——
 * 反正着火本身会把冻结值清零，争不来；子类则要"一边冻一边烧"，但它不碰原版的着火状态，
 * 见那边的类注释。所以「往上顶」那一段被提成了 {@link #pushFrozenVisuals}，两边共用。
 * <p>
 * 同理，{@link #onIncomingDamage} 也<b>同时认两颗效果</b>：只要身上挂着其中之一就免掉冻伤
 * （冻结值是两者都在顶的，那笔账自然也该两者一起还）。
 */
public class FrozenEffect extends MobEffect {

    /** 粒子颜色：霜白偏冰蓝。粒子本身换成了雪花，见构造函数 */
    public static final int COLOR = 0xBFE9FF;

    /**
     * 每 tick 往上顶的点数。
     * <p>
     * 原版 {@code LivingEntity#aiStep} 对「不在细雪里」的生物每 tick 扣 2 点，这里补回来再 +1，
     * 于是净增速与原版细雪<b>完全一致</b>：140 ÷ 1 = 7 秒冻透。想改冻结快慢就动这个数
     * （改动量 = 这个数 - 2）。
     */
    protected static final int CLIMB_PER_TICK = 3;

    /**
     * 稳态时把冻结值顶到满冻线上方这么多个点。
     * <p>
     * 原版稍后扣掉那 2 点，落点正好压在满冻线上；少一格（比如直接顶到线上）就会变成
     * 线上 2 点、渲染时 138 —— 霜花掉到 98%，冰心与发抖则整个没有。
     */
    protected static final int HOLD_ABOVE_FREEZE_LINE = 2;

    public FrozenEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR, ParticleTypes.SNOWFLAKE);
    }

    /**
     * 必须每 tick 都响。
     * <p>
     * 1.21 的默认实现是 {@code return false}（每 tick 只判定一次、不调用 {@code applyEffectTick}），
     * 而原版每 tick 都在扣冻结值，漏一拍就顶不住。
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // 着火时让位：原版 {@code Entity#baseTick} 的着火分支每 tick 会把冻结值清零，
        // 而且顺手发一次 {@code levelEvent(1009, pos, 1)}——客户端那边 data == 1 播的是
        // {@code GENERIC_EXTINGUISH_FIRE}，<b>是会响的</b>（data == 0 那支才是 FIRE_EXTINGUISH）。
        // 硬顶回去就等于每 tick 响一次灭火声，所以本类不争这一口气：火灭了再接着冻。
        // {@link ScorchingColdEffect} 不走向这个岔路，但它也<b>不碰原版的着火状态</b>——
        // 它自己打灼烧伤害，正是为了躲开这条"清零 + 出声"。
        if (entity.isOnFire()) {
            return true;
        }
        pushFrozenVisuals(entity);
        return true;
    }

    /**
     * 把实体的冻结值往上顶到满冻线上方（见 {@link #HOLD_ABOVE_FREEZE_LINE}）。
     * <p>
     * 提到方法里是为了让 {@link ScorchingColdEffect} 复用——它同样要顶，而且因为它不让实体着火，
     * 这里压根没人来清零，顶上去就是稳的。
     * 调用时机很关键：必须落在原版那次扣减<b>之后</b>。这一点由 {@code LivingEntity#baseTick}
     * 的调用顺序保证：{@code super.baseTick()}（原版那套冻结/着火处理）先跑，
     * {@code tickEffects()}（也就是这里）后跑。
     */
    protected void pushFrozenVisuals(LivingEntity entity) {
        int target = entity.getTicksRequiredToFreeze() + HOLD_ABOVE_FREEZE_LINE;
        int frozen = entity.getTicksFrozen();
        if (frozen < target) {
            entity.setTicksFrozen(Math.min(frozen + CLIMB_PER_TICK, target));
        }
    }

    /**
     * 免掉「冻透外观」连带的那份冻伤，{@code frozen} 与 {@code scorching_cold} 共用。
     * <p>
     * 只认伤害类型（{@code #minecraft:is_freezing}，而不是写死 {@code minecraft:freeze}），
     * 这样别的模组新增的冻结伤害也一并认下；<b>真的踩在细雪里除外</b>——那笔账是细雪收的，
     * 与这两颗效果无关，照扣。
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_FREEZING)) {
            return; // 绝大多数伤害都不是冻伤，先短路
        }
        LivingEntity entity = event.getEntity();
        if (entity.isInPowderSnow) {
            return;
        }
        if (!entity.hasEffect(ModEffects.FROZEN) && !entity.hasEffect(ModEffects.SCORCHING_COLD)) {
            return;
        }
        event.setCanceled(true);
    }
}
