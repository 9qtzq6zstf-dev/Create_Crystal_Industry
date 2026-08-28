package com.minecart.yunxian.client;

import com.minecart.yunxian.ModBlockEntities;
import com.minecart.yunxian.ModItems;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.model.NightVisionGogglesModel;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

import com.simibubi.create.content.kinetics.base.OrientedRotatingVisual;

public class ModRenderers {

    // 附加 3D 模型（standalone 变体，RegisterAdditional 硬性要求）
    private static final ModelResourceLocation NIGHT_VISION_GOGGLES_3D =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/night_vision_goggles/night_vision_goggles"), "standalone");

    // 物品自身模型（inventory 变体）
    private static final ModelResourceLocation NIGHT_VISION_GOGGLES_ITEM =
            new ModelResourceLocation(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "night_vision_goggles"), "inventory");

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModRenderers::onClientSetup);
        modEventBus.addListener(ModRenderers::onAddLayers);
        modEventBus.addListener(ModRenderers::onRegisterAdditional);   // 注册附加 3D 模型
        modEventBus.addListener(ModRenderers::onModifyBakingResult);   // 替换为 NightVisionGogglesModel
        if (FMLEnvironment.dist.isClient()) {
            EchoHighlightRenderer.register();
        }
        EchoSpyglassScopeOverlay.register();
        CameraSync.register();
        EchoSpyglassUseRenderer.register();
        modEventBus.addListener(ModRenderers::onRegisterItemDecorations);
    }

    // ---- 模型事件 ----

    private static void onRegisterAdditional(ModelEvent.RegisterAdditional event) {
        event.register(NIGHT_VISION_GOGGLES_3D);
    }

    private static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        BakedModel itemModel = event.getModels().get(NIGHT_VISION_GOGGLES_ITEM);
        BakedModel goggles3d = event.getModels().get(NIGHT_VISION_GOGGLES_3D);
        if (itemModel != null && goggles3d != null) {
            event.getModels().put(NIGHT_VISION_GOGGLES_ITEM, new NightVisionGogglesModel(itemModel, goggles3d));
        }
    }

    // ---- 原有方法（保持不变）----

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            SimpleBlockEntityVisualizer
                    .builder(ModBlockEntities.SMART_DRILL.get())
                    .factory(OrientedRotatingVisual.of(SmartDrillRenderer.HEAD))
                    .skipVanillaRender(be -> false)
                    .apply();

            ItemProperties.register(
                    ModItems.ECHO_SPYGLASS.get(),
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "using"),
                    (stack, level, entity, seed) ->
                            entity != null && entity.isUsingItem() && entity.getUseItem() == stack
                                    ? 1.0F : 0.0F);
        });
    }

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