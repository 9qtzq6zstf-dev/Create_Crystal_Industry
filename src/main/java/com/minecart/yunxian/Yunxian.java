package com.minecart.yunxian;

import com.minecart.yunxian.client.ModRenderers;
import com.simibubi.create.api.stress.BlockStressValues;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(Yunxian.MODID)
public class Yunxian {
    public static final String MODID = "create_crystal_industry";

    public Yunxian(IEventBus modEventBus, ModContainer container) {
        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCapabilities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(Yunxian::commonSetup);
        ModRenderers.register(modEventBus);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() ->
                BlockStressValues.IMPACTS.register(ModBlocks.SMART_DRILL.get(), () -> 4.0));
    }
}
