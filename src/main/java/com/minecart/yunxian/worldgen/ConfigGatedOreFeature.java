package com.minecart.yunxian.worldgen;

import com.mojang.serialization.Codec;
import com.minecart.yunxian.config.ModConfig;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;

public class ConfigGatedOreFeature extends OreFeature {
    private final String configKey;

    public ConfigGatedOreFeature(Codec<OreConfiguration> codec, String configKey) {
        super(codec);
        this.configKey = configKey;
    }

    @Override
    public boolean place(FeaturePlaceContext<OreConfiguration> context) {
        if (!ModConfig.Common.enabled(configKey)) {
            return false;
        }
        return super.place(context);
    }
}