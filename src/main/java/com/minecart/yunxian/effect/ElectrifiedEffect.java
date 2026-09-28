package com.minecart.yunxian.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「感电」：靠近弧光石系列方块或电流浆会获得的状态。
 * <p>
 * <b>目前只有状态本身，没有任何实际效果</b>——它的具体作用（加伤害？减速？给电弧石充能？）
 * 还没定，等定了在这里补 {@code applyEffectTick}/{@code addAttributeModifiers} 之类的重写即可。
 * 施加逻辑不在本类，见 {@link ElectrifiedAura}。
 * <p>
 * 分类取「中性」也只是占位（既不算增益也不算减益），将来效果定了多半要改成
 * {@link MobEffectCategory#BENEFICIAL} 或 {@link MobEffectCategory#HARMFUL}。
 */
public class ElectrifiedEffect extends MobEffect {

    /** 粒子颜色：电青，与弧光石同色系 */
    public static final int COLOR = 0x7FE9FF;

    public ElectrifiedEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }
}
