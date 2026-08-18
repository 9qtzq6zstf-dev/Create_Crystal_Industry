package com.minecart.yunxian;

import com.minecart.yunxian.client.ClientSetup;
import com.minecart.yunxian.client.ModRenderers;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod(Yunxian.MODID)
public class Yunxian {
    public static final String MODID = "create_crystal_industry";

    public Yunxian(IEventBus modEventBus, ModContainer container) {
        // 注册方块和物品
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCapabilities.register(modEventBus);
        ModPackets.register(modEventBus);
        ModMenus.register(modEventBus);
        ModRenderers.register(modEventBus);
        modEventBus.addListener(Yunxian::registerNetworking);
        modEventBus.register(ClientSetup.class);


        // 注册创造模式物品栏
        ModCreativeTabs.register(modEventBus);
    }
    public static void registerNetworking(
            RegisterPayloadHandlersEvent event
    ) {
        var registrar = event.registrar("1");

        registrar.playToServer(
                SmartDrillFilterPacket.TYPE,
                SmartDrillFilterPacket.STREAM_CODEC,
                SmartDrillFilterPacket::handle
        );
    }

}