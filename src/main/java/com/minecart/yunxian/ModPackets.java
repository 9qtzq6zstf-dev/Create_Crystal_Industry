package com.minecart.yunxian;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ModPackets {
    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterPayloadHandlersEvent.class, event -> {
            PayloadRegistrar registrar = event.registrar("1");
            registrar.playToServer(
                    SilkTouchModePacket.TYPE,
                    SilkTouchModePacket.STREAM_CODEC,
                    (packet, context) -> {
                        context.enqueueWork(() -> {
                            var level = context.player().level();
                            if (level.getBlockEntity(packet.pos()) instanceof SmartDrillBlockEntity be) {
                                be.setSilkTouchMode(packet.mode());
                            }
                        });
                    }
            );
        });
    }
}