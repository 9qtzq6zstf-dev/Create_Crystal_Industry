package com.minecart.yunxian.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「可燃气体」：喝下可燃冰圣代之后，肚子里积的那股气在这一分钟里都能点着。
 * <p>
 * <b>它自己什么都不做</b>，只是一张许可证——{@link FlameBreath} 拿它当喷火的开关：
 * 只要身上有它，按住潜行键就会朝前喷火。
 * 之所以单独拆一个效果、而不是继续挂在「冰封」上：两者的时长本来就不是一回事。
 * 冰封是喝下去那一下的代价（10 秒），喷火才是这件玩具的正题（60 秒），
 * 合用一个效果就没法各自调时长。
 * <p>
 * 走的是两参构造（不带粒子）：一分钟里在玩家身上不停冒粒子太吵，
 * 而且「有可燃气体」这件事本来就没有什么好看的视觉。
 */
public class FlammableGasEffect extends MobEffect {

    /** 颜色：暖琥珀色，和喷出来的那口黄火对上 */
    public static final int COLOR = 0xFFB347;

    public FlammableGasEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }
}
