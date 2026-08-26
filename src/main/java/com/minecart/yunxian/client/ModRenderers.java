package com.minecart.yunxian.client;

import com.minecart.yunxian.ModBlockEntities;
import com.minecart.yunxian.ModItems;
import com.minecart.yunxian.Yunxian;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;
import net.neoforged.fml.loading.FMLEnvironment;

public class ModRenderers {

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModRenderers::onClientSetup);
        modEventBus.addListener(ModRenderers::onAddLayers);   // ← 新增
        if (FMLEnvironment.dist.isClient()) {
            EchoHighlightRenderer.register();
        }
        EchoSculkOverlay.register();
        EchoSpyglassScopeOverlay.register();
        CameraSync.register();
        EchoSpyglassUseRenderer.register();
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            // Flywheel / Create Visual
            SimpleBlockEntityVisualizer
                    .builder(ModBlockEntities.SMART_DRILL.get())
                    .factory(OrientedRotatingVisual.of(SmartDrillRenderer.HEAD))
                    .skipVanillaRender(be -> false)
                    .apply();

            // ← 新增：使用中谓词。entity.getUseItem() == stack 与原版判断方式一致
            ItemProperties.register(
                    ModItems.ECHO_SPYGLASS.get(),
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "using"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                                    ? 1.0F : 0.0F);
        });
    }

    // ← 新增：给两种玩家皮肤渲染器追加头部锚定层
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = (PlayerRenderer) event.getSkin(skin);
            renderer.addLayer(new EchoSpyglassHeadLayer(renderer));
        }
    }
}