package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.effect.ElectrifiedEffect;
import com.minecart.yunxian.effect.FlammableGasEffect;
import com.minecart.yunxian.effect.FrozenEffect;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的状态效果：「感电」（{@code electrified}）、「冰封」（{@code frozen}）与「可燃气体」（{@code flammable_gas}）。
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
     * 「冰封」：喝下可燃冰圣代后像扎进细雪里一样冻起来。表现全部借原版的冻结值，
     * 自己只负责往上顶（见 {@link FrozenEffect}），连带的那份冻伤也在那边拦掉。
     */
    public static final DeferredHolder<MobEffect, FrozenEffect> FROZEN =
            MOB_EFFECTS.register("frozen", FrozenEffect::new);

    /**
     * 「可燃气体」：喝下可燃冰圣代后还能喷灵魂火的那一分钟。
     * <p>
     * 与「冰封」刻意的分开的：冰封是 10 秒的代价，喷火是 60 秒的正题，
     * 各自调时长（见 {@link FlammableGasEffect}）。
     */
    public static final DeferredHolder<MobEffect, FlammableGasEffect> FLAMMABLE_GAS =
            MOB_EFFECTS.register("flammable_gas", FlammableGasEffect::new);

    private ModEffects() {
    }

    public static void register(IEventBus modEventBus) {
        MOB_EFFECTS.register(modEventBus);
    }
}
