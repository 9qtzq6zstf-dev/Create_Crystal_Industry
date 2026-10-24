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
 * 分类取「负面」：多掉的那几滴血是实打实落在自己身上的，传给旁边敌人的那点附带收益
 * 抵不回来，玩家的感受就是"中了感电得躲着别挨打"，所以算坏事——提示框里显示成红字。
 * <p>
 * 分类只影响提示框文字颜色与第三方模组按分类做的判定（比如"只清负面/只留正面"这类），
 * 不含任何玩法逻辑：放电本身在 {@link ElectrifiedZap}，与分类无关。
 */
public class ElectrifiedEffect extends MobEffect {

    /** 粒子颜色：电青，与弧光石同色系 */
    public static final int COLOR = 0x7FE9FF;

    public ElectrifiedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }
}
