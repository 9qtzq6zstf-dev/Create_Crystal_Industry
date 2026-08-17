package com.minecart.yunxian;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

@Mod(Yunxian.MODID)
public class Yunxian {
    public static final String MODID = "yunxian";

    public Yunxian(IEventBus modEventBus, ModContainer container) {
        // 注册方块和物品
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCapabilities.register(modEventBus);
        ModPackets.register(modEventBus);
        ModMenus.register(modEventBus);


        // 注册创造模式物品栏
        ModCreativeTabs.register(modEventBus);
    }
}