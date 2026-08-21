package com.minecart.yunxian.network;

import com.minecart.yunxian.Yunxian;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = Yunxian.MODID)
public final class ModNetwork {

    private ModNetwork() {
    }

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(
                EchoRevealPayload.TYPE,
                EchoRevealPayload.STREAM_CODEC,
                EchoRevealPayload::handle
        );
    }
}