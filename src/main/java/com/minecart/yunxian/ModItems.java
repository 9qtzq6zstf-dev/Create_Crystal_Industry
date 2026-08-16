package com.minecart.yunxian;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    // 创建物品的延迟注册器
    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Yunxian.MODID);

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}