package com.minecart.yunxian;

import com.minecart.yunxian.worldgen.ConfigGatedOreFeature;
import com.minecart.yunxian.worldgen.OreBuddingVeinConfiguration;
import com.minecart.yunxian.worldgen.OreBuddingVeinFeature;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModFeatures {
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(BuiltInRegistries.FEATURE, Yunxian.MODID);

    public static final DeferredHolder<Feature<?>, OreBuddingVeinFeature> ORE_BUDDING_VEIN =
            FEATURES.register("ore_budding_vein",
                    () -> new OreBuddingVeinFeature(OreBuddingVeinConfiguration.CODEC));

    public static final DeferredHolder<Feature<?>, ConfigGatedOreFeature> ECHO_BUDDING_IN_SCULK =
            FEATURES.register("echo_budding_in_sculk",
                    () -> new ConfigGatedOreFeature(OreConfiguration.CODEC, "echo"));

    private ModFeatures() {
    }

    public static void register(IEventBus modEventBus) {
        FEATURES.register(modEventBus);
    }
}