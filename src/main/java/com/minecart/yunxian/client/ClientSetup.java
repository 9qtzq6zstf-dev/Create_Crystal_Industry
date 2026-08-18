package com.minecart.yunxian.client;

import com.minecart.yunxian.ModMenus;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

public class ClientSetup {

    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(
                ModMenus.SMART_DRILL.get(),
                SmartDrillScreen::new
        );
    }
}