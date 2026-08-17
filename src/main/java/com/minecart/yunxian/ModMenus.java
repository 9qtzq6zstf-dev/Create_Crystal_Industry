package com.minecart.yunxian;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, Yunxian.MODID);

    public static final Supplier<MenuType<SmartDrillMenu>> SMART_DRILL =
            MENUS.register("smart_drill",
                    () -> IMenuTypeExtension.create(
                            (windowId, inv, data) -> {
                                // 实际应通过 data 传递 BlockPos，此处简化
                                return new SmartDrillMenu(windowId, inv, null, new SimpleContainerData(1));
                            }
                    ));

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);  // ← 必须调用，否则菜单不会被注册
    }
}