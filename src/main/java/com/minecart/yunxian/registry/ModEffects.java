package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.effect.ElectrifiedEffect;
import com.minecart.yunxian.effect.FlameBreathEffect;
import com.minecart.yunxian.effect.FrostWalkerEffect;
import com.minecart.yunxian.effect.FrozenEffect;
import com.minecart.yunxian.effect.ScorchingColdEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的状态效果：「感电」（{@code electrified}）、「冰封」（{@code frozen}）、
 * 「灼寒」（{@code scorching_cold}）、「火焰吐息」（{@code flame_breath}）与
 * 「冰霜行者」（{@code frost_walker}）。
 * <p>
 * 名字来自默认规则：{@code MobEffect#getDescriptionId()} 是
 * {@code effect.<命名空间>.<注册名>}，所以语言文件里的键是
 * {@code effect.create_crystal_industry.electrified}。
 * <p>
 * 图标走 <b>atlas</b>，光把 PNG 放进 {@code textures/mob_effect/} 是不够的：
 * 还得有 {@code assets/create_crystal_industry/atlases/mob_effects.json} 把这个目录
 * 加进 {@code mob_effects} 图集，否则游戏里画不出来（写法见该文件，参照 JEI 的
 * {@code atlases/gui.json}）。
 */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Yunxian.MODID);

    public static final DeferredHolder<MobEffect, ElectrifiedEffect> ELECTRIFIED =
            MOB_EFFECTS.register("electrified", ElectrifiedEffect::new);

    /**
     * 「冰封」：像扎进细雪里一样冻起来。表现全部借原版的冻结值，
     * 自己只负责往上顶（见 {@link FrozenEffect}），连带的那份冻伤也在那边拦掉。
     * <p>
     * <b>现在没有任何东西发放它了</b>——可燃冰沙瓶与可燃冰圣代给的都是「灼寒」。
     * 注册项留着是刻意的：存档里已经带着这个效果的生物不会变成「未知效果」，
     * KubeJS 或整合包引用 {@code create_crystal_industry:frozen} 也不会断。
     */
    public static final DeferredHolder<MobEffect, FrozenEffect> FROZEN =
            MOB_EFFECTS.register("frozen", FrozenEffect::new);

    /**
     * 「灼寒」：冻着的同时还烧起来，可燃冰沙瓶与可燃冰圣代现在给的就是它。
     * <p>
     * 冻结那一半与「冰封」共用同一套机制（因此它是 {@link FrozenEffect} 的子类），
     * 多出来的只有「着火」——见 {@link ScorchingColdEffect}。
     */
    public static final DeferredHolder<MobEffect, ScorchingColdEffect> SCORCHING_COLD =
            MOB_EFFECTS.register("scorching_cold", ScorchingColdEffect::new);

    /**
     * 「火焰吐息」：喝下可燃冰圣代后还能喷火的那一分钟。
     * <p>
     * 与「冰封」刻意的分开的：冰封是 10 秒的代价，喷火是 60 秒的正题，
     * 各自调时长（见 {@link FlameBreathEffect}）。
     */
    public static final DeferredHolder<MobEffect, FlameBreathEffect> FLAME_BREATH =
            MOB_EFFECTS.register("flame_breath", FlameBreathEffect::new);

    /**
     * 「冰霜行者」：脚下这片水面被踩成冰，可燃冰沙瓶与可燃冰圣代都发它。
     * <p>
     * 与「冰封 / 灼寒」是两回事：那两颗冻的是<b>人</b>（顶实体的冻结值，见 {@link FrozenEffect}），
     * 这颗冻的是<b>水</b>——人身上不挂霜花，只是走过的地方留下一条冰路。
     * 复刻原版附魔那套行为的全部理由见 {@link FrostWalkerEffect}。
     */
    public static final DeferredHolder<MobEffect, FrostWalkerEffect> FROST_WALKER =
            MOB_EFFECTS.register("frost_walker", FrostWalkerEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
