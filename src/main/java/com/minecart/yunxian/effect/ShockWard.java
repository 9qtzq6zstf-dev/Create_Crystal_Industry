package com.minecart.yunxian.effect;

import com.minecart.yunxian.util.ShockImmunityHelper;

import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * 全套 {@code shock_immune} 盔甲挡下闪电伤害。
 * <p>
 * <b>一个拦截点盖住两件事</b>：{@link SlurryShock} 泡在电流浆里挨的那 20 点，和原版真实落雷
 * （雷雨天劈中、三叉戟引雷）那 5 点——两者用的是<b>同一个伤害类型</b>
 * {@code minecraft:lightning_bolt}。所以这里只认伤害类型，不认伤害是谁给的，也不去区分伤害大小。
 * <p>
 * 钩子选 {@link LivingIncomingDamageEvent} 而不是自己每 tick 查血：它在 {@code LivingEntity#hurt}
 * 的<b>无敌帧检查之后、伤害减免之前</b>触发（NeoForge 的 {@code CommonHooks#onEntityIncomingDamage}），
 * 取消掉 {@code hurt} 就直接返回——护甲不掉耐久、不触发受击动画与击退，是这个需求想要的干净结果。
 * 放在减免之前也意味着不管伤害类型原本无视多少护甲都拦得住，正好对症（{@code lightning_bolt} 本就无视护甲）。
 * <p>
 * <b>只挡伤害，不挡着火</b>：原版 {@code LightningBolt} 在 {@code hurt} 之外还会
 * {@code setSecondsOnFire(8)}，那是另一条路径，本类管不着。需求只提了伤害，且全套下界合金自带抗火组件，
 * 实际很难烧起来；真要连着火一起挡，得另找钩子。
 */
public final class ShockWard {

    private ShockWard() {
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!event.getSource().is(DamageTypes.LIGHTNING_BOLT)) {
            return; // 绝大多数伤害都不是闪电，先短路
        }
        if (!ShockImmunityHelper.isWearingFullSet(event.getEntity())) {
            return;
        }
        event.setCanceled(true);
    }
}
