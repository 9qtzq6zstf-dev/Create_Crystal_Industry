package com.minecart.yunxian;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class FanImmunityHelper {
    private FanImmunityHelper() {}

    /** 玩家任意一件盔甲带 fan_immune 标签即视为免疫 */
    public static boolean isImmune(Entity entity) {
        if (entity instanceof Player player) {
            for (ItemStack stack : player.getArmorSlots()) {
                if (stack.is(ModTags.FAN_IMMUNE))
                    return true;
            }
        }
        return false;
    }
}