package com.minecart.yunxian.client;

import com.minecart.yunxian.ModBlockEntities;
import com.minecart.yunxian.Yunxian;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;

public class ModRenderers {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModRenderers::onClientSetup);
    }

    private static void onClientSetup(FMLClientSetupEvent event) {

        event.enqueueWork(() -> {

            // 普通 BlockEntity Renderer
            BlockEntityRenderers.register(
                    ModBlockEntities.SMART_DRILL.get(),
                    SmartDrillRenderer::new
            );

            // Flywheel / Create Visual
            SimpleBlockEntityVisualizer
                    .builder(ModBlockEntities.SMART_DRILL.get())
                    .factory(
                            OrientedRotatingVisual.of(
                                    SmartDrillRenderer.HEAD
                            )
                    )
                    .skipVanillaRender(be -> false)
                    .apply();
        });
    }
}