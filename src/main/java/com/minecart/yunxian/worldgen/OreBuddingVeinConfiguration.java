package com.minecart.yunxian.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import java.util.List;

public record OreBuddingVeinConfiguration(
        BlockState buddingState,
        List<RuleTest> hosts,
        List<HostOre> ores,
        BlockState extraBlockState,
        int oreCount,
        int extraCount,
        int radius) implements FeatureConfiguration {

    public static final Codec<OreBuddingVeinConfiguration> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.fieldOf("budding_state").forGetter(OreBuddingVeinConfiguration::buddingState),
            RuleTest.CODEC.listOf().fieldOf("hosts").forGetter(OreBuddingVeinConfiguration::hosts),
            HostOre.CODEC.listOf().fieldOf("ores").forGetter(OreBuddingVeinConfiguration::ores),
            BlockState.CODEC.fieldOf("extra_block_state").forGetter(OreBuddingVeinConfiguration::extraBlockState),
            Codec.intRange(0, 64).fieldOf("ore_count").forGetter(OreBuddingVeinConfiguration::oreCount),
            Codec.intRange(0, 16).fieldOf("extra_count").forGetter(OreBuddingVeinConfiguration::extraCount),
            Codec.intRange(1, 8).fieldOf("radius").forGetter(OreBuddingVeinConfiguration::radius)
    ).apply(instance, OreBuddingVeinConfiguration::new));

    public record HostOre(RuleTest target, BlockState state) {
        public static final Codec<HostOre> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                RuleTest.CODEC.fieldOf("target").forGetter(HostOre::target),
                BlockState.CODEC.fieldOf("state").forGetter(HostOre::state)
        ).apply(instance, HostOre::new));
    }
}