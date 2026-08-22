package com.minecart.yunxian.client;

import com.minecart.yunxian.ModMenus;
import com.minecart.yunxian.Yunxian;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class ModMenuScreens {

    private ModMenuScreens() {
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.ECHO_FILTER_MENU.get(), EchoSpyglassFilterScreen::new);
    }
}