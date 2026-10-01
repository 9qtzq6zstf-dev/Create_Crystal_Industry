package com.minecart.yunxian.effect;

import com.minecart.yunxian.registry.ModDamageTypes;
import com.minecart.yunxian.util.ShockImmunityHelper;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 全套 {@code shock_immune} 盔甲挡下「电」的伤害。
 * <p>
 * <b>一个拦截点盖住三件事</b>，它们分属两个伤害类型，判定见 {@link #isElectric}：
 * {@link SlurryShock} 泡在电流浆里挨的那 20 点，与<b>原版真实落雷</b>（雷雨天劈中、三叉戟引雷）
 * 那 5 点，用的是原版 {@code minecraft:lightning_bolt}；{@link ElectrifiedZap} 的放电用的是
 * 本模组自己的 {@code create_crystal_industry:electric_shock}。所以这里只认伤害类型，
 * 不认伤害是谁给的，也不去区分伤害大小。
 * <p>
 * 钩子选 {@link LivingIncomingDamageEvent} 而不是自己每 tick 查血：它在 {@code LivingEntity#hurt}
 * 的<b>无敌帧检查之后、伤害减免之前</b>触发（NeoForge 的 {@code CommonHooks#onEntityIncomingDamage}），
 * 取消掉 {@code hurt} 就直接返回——护甲不掉耐久、不触发受击动画与击退，是这个需求想要的干净结果。
 * 放在减免之前也意味着不管伤害类型原本无视多少护甲都拦得住，而放电恰恰是<b>无视护甲</b>的
 * （登记在 {@code minecraft:bypasses_armor} 里），正好对症。
 * <p>
 * <b>只挡伤害，不挡着火</b>：原版 {@code LightningBolt} 在 {@code hurt} 之外还会
 * {@code setSecondsOnFire(8)}，那是另一条路径，本类管不着。需求只提了伤害，且全套下界合金自带抗火组件，
 * 实际很难烧起来；真要连着火一起挡，得另找钩子。
 */
public final class ShockWard {

    private ShockWard() {
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!isElectric(event.getSource())) {
            return; // 绝大多数伤害都不是「电」，先短路
        }
        if (!ShockImmunityHelper.isWearingFullSet(event.getEntity())) {
            return;
        }
        event.setCanceled(true);
    }

    /**
     * 这份「电」是否算数。
     * <p>
     * <b>这是一个白名单，新增电击伤害类型必须往这里加一条。</b>盔甲认的是「电」这个概念，
     * 而判定只能按伤害类型来——所以漏登记不会报错、不会崩，只会让盔甲对着那种电击
     * <b>静默失效</b>（和「母岩漏进 {@code c:budding_blocks} 会让 AE2 催生器静默失效」是同一类坑）。
     * 目前三条：
     * <ul>
     *   <li><b>原版 {@code minecraft:lightning_bolt}</b> —— 雷雨天劈中、三叉戟引雷。
     *       本模组自己的电击已经不再用它，但原版那些还得认；</li>
     *   <li><b>{@code electric_shock}</b> —— 电流浆那 20 点（{@link SlurryShock}）；</li>
     *   <li><b>{@code electric_discharge}</b> —— 感电生物放电（{@link ElectrifiedZap}）。</li>
     * </ul>
     * 后两条见 {@link ModDamageTypes}（它们刻意共用同一个死亡讯息，但标签不同，所以是两条类型）。
     */
    private static boolean isElectric(DamageSource source) {
        return source.is(DamageTypes.LIGHTNING_BOLT)
                || source.is(ModDamageTypes.ELECTRIC_SHOCK)
                || source.is(ModDamageTypes.ELECTRIC_DISCHARGE);
    }
}
