package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageType;

/**
 * 本模组用到的伤害类型。
 * <p>
 * 1.21 里伤害类型是<b>数据包注册表</b>——没有能在代码里 {@code DeferredRegister} 的静态注册表，
 * 定义写在 {@code data/create_crystal_industry/damage_type/<名>.json} 里。所以本类只有引用它的
 * {@link ResourceKey}，没有注册动作，也不需要挂到模组事件总线上。
 */
public final class ModDamageTypes {

    /**
     * 本模组的电击伤害有<b>两条类型</b>，两者的差别只有一处：<b>要不要跳过受击无敌帧</b>。
     * 穿甲是两条都有的（都登记在 {@code minecraft:bypasses_armor} 里，都是这个模组意义上的
     * 「真实伤害」）。
     * <p>
     * 之所以非拆不可，是因为两种电击对无敌帧的需求正好相反：<b>电流浆靠无敌帧限速</b>
     * （{@link #ELECTRIC_SHOCK} 那条，每 tick 调一次 {@code hurt}，靠原版约 10 tick 的无敌帧
     * 压到每秒约 2 次）；<b>放电则必须跳过无敌帧</b>（{@link #ELECTRIC_DISCHARGE} 那条，
     * 它是在目标刚挨完打的结算尾巴上打出去的，不跳就整个被吞掉）。合成一条的话，
     * 电流浆会变成每 tick 20 点——比现在强十倍，也直接推翻它自己注释里写的频率。
     * <p>
     * <b>两条类型共用同一个 {@code message_id}</b>（{@code electricShock}），所以玩家看到的
     * 死亡讯息完全一样，拆类型不会拆出第二套文案。死亡讯息用的是原版那两支结构：
     * <ul>
     *   <li>没有击杀归属 → {@code death.attack.electricShock}（1 个参数）→「xxx死于电刑」；</li>
     *   <li>有击杀归属 → {@code death.attack.electricShock.player}（2 个参数）→「xxx被xxx造成的闪电劈死了」。</li>
     * </ul>
     * 这两支由原版自己按「伤害源上有没有实体」和 {@code getKillCredit()} 选，
     * 所以放电的伤害源刻意<b>不挂任何实体</b>，见 {@code ElectrifiedZap#zap}。
     * <p>
     * <b>新增类型时必须同步改 {@code effect/ShockWard}</b>：{@code shock_immune} 盔甲的免疫
     * 是按类型白名单判的，漏登记不会报错，只会让那件盔甲对着这种电击<b>静默失效</b>。
     */
    public static final ResourceKey<DamageType> ELECTRIC_SHOCK = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "electric_shock"));

    /**
     * 「放电」：感电生物挨打时电出去的那一发（见 {@code effect/ElectrifiedZap}）。
     * <p>
     * 与 {@link #ELECTRIC_SHOCK} 的唯一区别是它多登记了一个
     * {@code minecraft:bypasses_cooldown}——理由见那边的注释。两条类型共用同一个
     * {@code message_id}，死亡讯息一样。
     */
    public static final ResourceKey<DamageType> ELECTRIC_DISCHARGE = ResourceKey.create(
            Registries.DAMAGE_TYPE,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "electric_discharge"));

    private ModDamageTypes() {
    }
}
