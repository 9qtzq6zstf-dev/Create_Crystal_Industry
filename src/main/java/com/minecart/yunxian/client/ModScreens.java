package com.minecart.yunxian.client;

import com.minecart.yunxian.ModMenus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ModScreens {

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {

        event.register(
                ModMenus.SMART_DRILL.get(),
                SmartDrillScreen::new
        );
    }
}