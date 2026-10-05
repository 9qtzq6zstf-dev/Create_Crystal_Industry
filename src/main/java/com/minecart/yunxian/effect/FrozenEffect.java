package com.minecart.yunxian.effect;

import com.minecart.yunxian.registry.ModEffects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 「冰封」：可燃冰圣代喝下去之后，像一头扎进细雪里那样冻起来。
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
    private static final int CLIMB_PER_TICK = 3;

    /**
     * 稳态时把冻结值顶到满冻线上方这么多个点。
     * <p>
     * 原版稍后扣掉那 2 点，落点正好压在满冻线上；少一格（比如直接顶到线上）就会变成
     * 线上 2 点、渲染时 138 —— 霜花掉到 98%，冰心与发抖则整个没有。
     */
    private static final int HOLD_ABOVE_FREEZE_LINE = 2;

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
        // 着火时不顶：原版自己就规定「着火不结冰」（Entity#baseTick 的着火分支每 tick 清零冻结），
        // 而且清零那一下会顺带播一次灭火音效。硬顶回去不只是白费劲，还会把那声音放成机关枪——
        // 所以这里让位，火灭了再接着冻。
        if (entity.isOnFire()) {
            return true;
        }
        int target = entity.getTicksRequiredToFreeze() + HOLD_ABOVE_FREEZE_LINE;
        int frozen = entity.getTicksFrozen();
        if (frozen < target) {
            entity.setTicksFrozen(Math.min(frozen + CLIMB_PER_TICK, target));
        }
        return true;
    }

    /**
     * 免掉「冰封」自己造成的那份冻伤。
     * <p>
     * 只认伤害类型（{@code #minecraft:is_freezing}，而不是写死 {@code minecraft:freeze}），
     * 这样别的模组新增的冻结伤害也一并认下；<b>真的踩在细雪里除外</b>——那笔账是细雪收的，
     * 与这颗圣代无关，照扣。
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypeTags.IS_FREEZING)) {
            return; // 绝大多数伤害都不是冻伤，先短路
        }
        LivingEntity entity = event.getEntity();
        if (entity.isInPowderSnow || !entity.hasEffect(ModEffects.FROZEN)) {
            return;
        }
        event.setCanceled(true);
    }
}
