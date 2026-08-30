package com.minecart.yunxian;

import com.minecart.yunxian.client.ModRenderers;
import com.minecart.yunxian.item.NightVisionGogglesItem;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.api.stress.BlockStressValues;
import com.simibubi.create.content.equipment.goggles.GogglesItem;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

@Mod(Yunxian.MODID)
public class Yunxian {
    public static final String MODID = "create_crystal_industry";

    public Yunxian(IEventBus modEventBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, com.minecart.yunxian.config.ModConfig.Common.SPEC);
        container.registerConfig(ModConfig.Type.CLIENT, com.minecart.yunxian.config.ModConfig.Client.SPEC); // ← 新增

        ModBlocks.register(modEventBus);
        ModItems.register(modEventBus);
        EchoAttachments.ATTACHMENT_TYPES.register(modEventBus);
        ModMenus.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModCapabilities.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        modEventBus.addListener(Yunxian::commonSetup);
        ModRenderers.register(modEventBus);
        ModFeatures.register(modEventBus);
        ModArmInteractionPointTypes.register(modEventBus);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            BlockStressValues.IMPACTS.register(ModBlocks.SMART_DRILL.get(), () -> 8.0);
            BlockStressValues.IMPACTS.register(ModBlocks.MECHANICAL_ACCELERATOR.get(), () -> 128.0); // ← 新增
            BlockStressValues.IMPACTS.register(ModBlocks.MECHANICAL_CLEANER.get(), () -> 4.0);
            MovementBehaviour.REGISTRY.register(
                    ModBlocks.SMART_DRILL.get(),
                    new SmartDrillMovementBehaviour()
            );
            GogglesItem.addIsWearingPredicate(player ->
                    NightVisionWearHelper.isWearingGoggles(player));
        });
    }
}