package com.minecart.yunxian.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「感电」：靠近弧光石系列方块或电流浆会获得的状态。
 * <p>
 * 施加逻辑在 {@link ElectrifiedAura}（什么时候得）；<b>实际效果在 {@link ElectrifiedZap}</b>
 * ——带电的生物挨打时会放电，自己再吃一发 4 点雷击，并顺带电到旁边一个同样带电的生物。
 * 效果本身没有 {@code applyEffectTick} 之类的每刻行为：它是「挨打时才会响」的，挂在受伤事件上，
 * 不是挂在状态自己的 tick 上（那条路每刻都要跑，且判不出"刚被打"）。
 * <p>
 * 分类取「中性」：它既不是纯增益也不是纯减益——挨打会多掉血（减益），
 * 但也能把电传给旁边的敌人（增益）。等哪天要给它加属性修正再考虑改分类。
 */
public class ElectrifiedEffect extends MobEffect {

    /** 粒子颜色：电青，与弧光石同色系 */
    public static final int COLOR = 0x7FE9FF;

    public ElectrifiedEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }
}
