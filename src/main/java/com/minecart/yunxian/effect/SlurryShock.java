package com.minecart.yunxian.effect;

import com.minecart.yunxian.registry.ModFluids;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 电流浆的电击：泡在浆里的生物持续挨雷劈。
 * <p>
 * 伤害用的是原版的 <b>闪电伤害类型</b>（{@code minecraft:lightning_bolt}）——它无视护甲，
 * 这正是「雷击伤害」该有的手感。
 * <p>
 * 两个刻意的选择：
 * <ul>
 *   <li><b>碰着就算，不要求没顶</b>：与岩浆同一尺度（{@code isInFluidType} 即"有任何一点泡在里面"）。
 *       玩家倒一桶浆出来只有一格深，若要求整个人没入，这功能在实战里等于不存在。</li>
 *   <li><b>每 tick 都调 {@code hurt}，不自己算冷却</b>：原版受伤后的无敌帧（约 10 tick）会把实际
 *       频率压到约 2 次/秒，与岩浆一致；自己再叠一层计时反而会与无敌帧打架。</li>
 * </ul>
 * 只作用于 {@link LivingEntity}（生物与玩家）：掉落物、船之类不是"被电击"的合理对象。
 */
public final class SlurryShock {

    /**
     * 每次伤害：20 点（十颗心）——刻意比原版闪电劈中的 5 点重得多。
     * 配合无视护甲的伤害类型，泡进去基本等于当场毙命，这是要的效果而不是意外。
     */
    private static final float DAMAGE = 20.0F;

    private SlurryShock() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (!(living.level() instanceof ServerLevel level)) {
            return; // 只在服务端结算
        }
        if (!living.isInFluidType(ModFluids.CURRENT_SLURRY_TYPE.get())) {
            return;
        }
        living.hurt(level.damageSources().lightningBolt(), DAMAGE);
    }
}
