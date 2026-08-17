package com.minecart.yunxian.client;

import com.minecart.yunxian.ModMenus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = "yunxian", value = Dist.CLIENT) // 去掉 bus 参数，避免弃用警告
public class ModScreens {
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.SMART_DRILL.get(), SmartDrillScreen::new);
    }
}