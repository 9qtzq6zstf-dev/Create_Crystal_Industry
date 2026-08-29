package com.minecart.yunxian;

import com.minecart.yunxian.integration.curios.CuriosIntegration;
import com.minecart.yunxian.item.NightVisionGogglesItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

public final class NightVisionWearHelper {
    private NightVisionWearHelper() {}

    public static boolean isWearingGoggles(Player player) {
        // 头盔槽
        if (player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof NightVisionGogglesItem) {
            return true;
        }
        // 首饰栏（软依赖：只有 Curios 加载了才走这段，避免类加载崩溃）
        if (ModList.get().isLoaded("curios")) {
            return CuriosIntegration.isEquipped(player);
        }
        return false;
    }
}