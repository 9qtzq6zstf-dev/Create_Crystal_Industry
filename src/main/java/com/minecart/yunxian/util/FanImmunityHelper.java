package com.minecart.yunxian.util;

import com.minecart.yunxian.registry.ModEnchantments;
import com.minecart.yunxian.registry.ModTags;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * 「这股风推不动我」的判定：任意一件盔甲带 {@link ModTags#FAN_IMMUNE} 标签，
 * 或带「气流抵抗」附魔（{@link ModEnchantments#AIRFLOW_RESISTANCE}），就算免疫。
 * <p>
 * 判定只有一处，三个调用点都走它：鼓风机的 {@code AirCurrentMixin}、分散网的
 * {@code NozzleBlockEntityMixin}，以及风弹击退的 {@link WindKnockbackGuard}。
 * <p>
 * 只对<b>玩家</b>生效：气流对生物本来就不该有隐形免疫，而「穿盔甲」这件事对生物只是掉落物。
 */
public final class FanImmunityHelper {
    private FanImmunityHelper() {}

    /** 玩家任意一件盔甲带 fan_immune 标签、或带「气流抵抗」附魔，即视为免疫 */
    public static boolean isImmune(Entity entity) {
        if (entity instanceof Player player) {
            for (ItemStack stack : player.getArmorSlots()) {
                if (stack.is(ModTags.FAN_IMMUNE) || hasAirflowResistance(stack))
                    return true;
            }
        }
        return false;
    }

    /**
     * 附魔判定走<b>物品里存着的附魔表</b>，不做注册表查询——{@code EnchantmentHelper} 查附魔等级要先拿
     * {@code Holder}，而这里只要「有没有这一条」，用 {@link Holder#is(net.minecraft.resources.ResourceKey)}
     * 比对键就够，客户端也不会因为附魔注册表还没同步而漏判。
     */
    private static boolean hasAirflowResistance(ItemStack stack) {
        if (stack.isEmpty())
            return false;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (Holder<Enchantment> enchantment : enchantments.keySet()) {
            if (enchantment.is(ModEnchantments.AIRFLOW_RESISTANCE))
                return true;
        }
        return false;
    }
}
