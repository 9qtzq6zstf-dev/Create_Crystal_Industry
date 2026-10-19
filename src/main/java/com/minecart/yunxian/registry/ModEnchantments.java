package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * 附魔的键。
 * <p>
 * 1.21 的附魔是<b>数据驱动</b>的（{@code data/<命名空间>/enchantment/<id>.json}，与生物群系、结构同一个
 * 数据包注册表 {@link Registries#ENCHANTMENT}），所以这里没有 {@code DeferredRegister}，
 * 只留 {@link ResourceKey} 给判定侧引用。真正让附魔生效的三个东西是：
 * <ul>
 *   <li>数据包 JSON —— 名字、适配物品、代价、权重（{@code data/create_crystal_industry/enchantment/airflow_resistance.json}）；</li>
 *   <li>语言文件里的 {@code enchantment.create_crystal_industry.airflow_resistance} —— 显示名；</li>
 *   <li>本模组自己的判定 —— 原版没有「免疫气流」这种效果组件，所以在
 *       {@code util/FanImmunityHelper} 里按附魔等级判定，与 {@link ModTags#FAN_IMMUNE} 标签等价。</li>
 * </ul>
 */
public final class ModEnchantments {

    private ModEnchantments() {
    }

    /**
     * 「气流抵抗」：盔甲上任意一件带此附魔，即免疫鼓风机与分散网的风力推动，也免疫风弹的击退。
     * <p>
     * 键名与数据包文件名必须一致，改一边就要改另一边。
     */
    public static final ResourceKey<Enchantment> AIRFLOW_RESISTANCE = ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "airflow_resistance"));
}
