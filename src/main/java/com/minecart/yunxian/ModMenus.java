package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(
                    Registries.MENU,
                    Yunxian.MODID
            );

    public static final Supplier<MenuType<SmartDrillMenu>> SMART_DRILL =
            MENUS.register(
                    "smart_drill",
                    () -> IMenuTypeExtension.create(
                            (windowId, playerInv, buffer) -> {

                                BlockPos pos =
                                        buffer.readBlockPos();

                                return new SmartDrillMenu(
                                        windowId,
                                        playerInv,
                                        pos
                                );
                            }
                    )
            );

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}