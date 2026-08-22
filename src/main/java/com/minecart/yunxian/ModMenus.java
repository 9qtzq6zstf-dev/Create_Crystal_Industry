package com.minecart.yunxian;

import com.minecart.yunxian.menu.EchoSpyglassFilterMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Yunxian.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<EchoSpyglassFilterMenu>> ECHO_FILTER_MENU =
            MENUS.register("echo_spyglass_filter",
                    () -> IMenuTypeExtension.create(EchoSpyglassFilterMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}