package com.minecart.yunxian.integration.curios;

import com.minecart.yunxian.ModItems;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

public final class CuriosIntegration {
    private CuriosIntegration() {}

    public static void registerCapabilities() {
        CuriosApi.registerCurio(ModItems.NIGHT_VISION_GOGGLES.get(), CURIOS_ITEM);
    }

    private static final ICurioItem CURIOS_ITEM = new ICurioItem() {};

    public static boolean isEquipped(Player player) {
        return CuriosApi.getCuriosInventory(player)
                .map(inv -> inv.isEquipped(ModItems.NIGHT_VISION_GOGGLES.get()))
                .orElse(false);
    }
}