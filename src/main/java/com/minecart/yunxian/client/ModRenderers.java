package com.minecart.yunxian.client;

import com.minecart.yunxian.ModBlockEntities;
import com.minecart.yunxian.ModItems;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.model.NightVisionGogglesModel;

import com.minecart.yunxian.integration.curios.CuriosClientIntegration;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;

public class ModRenderers {

    private static final ModelResourceLocation NIGHT_VISION_GOGGLES_3D =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/night_vision_goggles/night_vision_goggles"), "standalone");
    private static final ModelResourceLocation NIGHT_VISION_GOGGLES_3D_ON =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/night_vision_goggles/night_vision_goggles_on"), "standalone");
    private static final ModelResourceLocation NIGHT_VISION_GOGGLES_ITEM =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "night_vision_goggles"), "inventory");

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModRenderers::onClientSetup);
        modEventBus.addListener(ModRenderers::onAddLayers);
        modEventBus.addListener(ModRenderers::onRegisterAdditional);
        modEventBus.addListener(ModRenderers::onModifyBakingResult);
        if (FMLEnvironment.dist.isClient()) {
            EchoHighlightRenderer.register();
        }
        EchoSpyglassScopeOverlay.register();
        CameraSync.register();
        EchoSpyglassUseRenderer.register();
        modEventBus.addListener(ModRenderers::onRegisterItemDecorations);
    }

    private static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(NIGHT_VISION_GOGGLES_3D);
        event.register(NIGHT_VISION_GOGGLES_3D_ON);
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel itemModel = event.getModels().get(NIGHT_VISION_GOGGLES_ITEM);
        BakedModel goggles3d = event.getModels().get(NIGHT_VISION_GOGGLES_3D);
        BakedModel goggles3dOn = event.getModels().get(NIGHT_VISION_GOGGLES_3D_ON);
        if (itemModel != null && goggles3d != null && goggles3dOn != null) {
            event.getModels().put(NIGHT_VISION_GOGGLES_ITEM,
                    new NightVisionGogglesModel(itemModel, goggles3d, goggles3dOn));
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SimpleBlockEntityVisualizer
                    .builder(ModBlockEntities.SMART_DRILL.get())
                    .factory(OrientedRotatingVisual.of(SmartDrillRenderer.HEAD))
                    .skipVanillaRender(be -> false)
                    .apply();

            SimpleBlockEntityVisualizer
                    .builder(ModBlockEntities.MECHANICAL_ACCELERATOR.get())
                    .factory(OrientedRotatingVisual.of(MechanicalAcceleratorRenderer.SHAFT))
                    .skipVanillaRender(be -> true)
                    .apply();

            ItemProperties.register(
                    ModItems.ECHO_SPYGLASS.get(),
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "using"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                                    ? 1.0F : 0.0F);
            // ★ 软依赖门控：客户端 + Curios 已加载才注册首饰栏渲染器
            if (ModList.get().isLoaded("curios")) {
                CuriosClientIntegration.registerRenderers();
            }
        });
    }

    // ========== 恢复原样：不加也不移除任何头部渲染层 ==========
    private static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (PlayerSkin.Model skin : event.getSkins()) {
            PlayerRenderer renderer = (PlayerRenderer) event.getSkin(skin);
            renderer.addLayer(new EchoSpyglassHeadLayer(renderer));
        }
    }

    private static void onRegisterItemDecorations(RegisterItemDecorationsEvent event) {
        event.register(ModItems.ECHO_SPYGLASS.get(), new EchoSpyglassGuiDecorator());
    }
}