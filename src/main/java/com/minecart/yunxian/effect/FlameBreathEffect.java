package com.minecart.yunxian.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「火焰吐息」：喝下可燃冰圣代之后，接下来这一分钟里都能朝前喷火。
 * <p>
 * <b>它自己什么都不做</b>，只是一张许可证——{@link FlameBreath} 拿它当喷火的开关：
 * 只要身上有它，按住潜行键就会朝前喷火。
 * 之所以单独拆一个效果、而不是继续挂在「冰封」上：两者的时长本来就不是一回事。
 * 冰封是喝下去那一下的代价（10 秒），喷火才是这件玩具的正题（60 秒），
 * 合用一个效果就没法各自调时长。
 * <p>
 * 名字与管喷火的那个 {@link FlameBreath} 是一套的：这个类管「有没有资格」，
 * 那个类管「喷出来是什么样」。图标文件名跟着效果 id 走（{@code mob_effect/flame_breath.png}），
 * 改 id 时记得连它一起改，否则 HUD 上会变成紫黑块。
 * <p>
 * 走的是两参构造（不带粒子）：一分钟里在玩家身上不停冒粒子太吵，
 * 而且「身上带着一团火」这件事本来就没有什么好看的视觉。
 */
public class FlameBreathEffect extends MobEffect {

    /** 颜色：暖琥珀色，和喷出来的那口黄火对上 */
    public static final int COLOR = 0xFFB347;

    public FlameBreathEffect() {
        super(MobEffectCategory.NEUTRAL, COLOR);
    }
}
