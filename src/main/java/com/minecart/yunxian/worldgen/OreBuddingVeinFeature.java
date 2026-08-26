package com.minecart.yunxian.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest;

import java.util.List;

public class OreBuddingVeinFeature extends Feature<OreBuddingVeinConfiguration> {

    public OreBuddingVeinFeature(Codec<OreBuddingVeinConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<OreBuddingVeinConfiguration> context) {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        BlockPos origin = context.origin();
        OreBuddingVeinConfiguration config = context.config();

        // 在采样点附近找一个可替换的岩石方块作为母岩位置。
        BlockPos buddingPos = findHostNear(level, random, config, origin);
        if (buddingPos == null) {
            return false;
        }

        // 每次成功放置 = 恰好一个母岩。
        level.setBlock(buddingPos, config.buddingState(), 2);

        // 按石头/深板岩自动选择矿石变种，撒在母岩周围。
        for (int i = 0; i < config.oreCount(); i++) {
            BlockPos.MutableBlockPos pos = randomRimPos(random, config, buddingPos);
            if (!level.ensureCanWrite(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            for (OreBuddingVeinConfiguration.HostOre ore : config.ores()) {
                if (ore.target().test(state, random)) {
                    level.setBlock(pos, ore.state(), 2);
                    break;
                }
            }
        }

        // 撒粗金属块（或红石块）。
        for (int i = 0; i < config.extraCount(); i++) {
            BlockPos.MutableBlockPos pos = randomRimPos(random, config, buddingPos);
            if (!level.ensureCanWrite(pos)) {
                continue;
            }
            if (isHost(level.getBlockState(pos), config.hosts(), random)) {
                level.setBlock(pos, config.extraBlockState(), 2);
            }
        }

        return true;
    }

    private static BlockPos findHostNear(WorldGenLevel level, RandomSource random,
                                         OreBuddingVeinConfiguration config, BlockPos origin) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int radius = config.radius();
        for (int attempt = 0; attempt < 12; attempt++) {
            pos.set(origin.getX() + random.nextInt(radius * 2 + 1) - radius,
                    origin.getY() + random.nextInt(radius * 2 + 1) - radius,
                    origin.getZ() + random.nextInt(radius * 2 + 1) - radius);
            if (level.ensureCanWrite(pos) && isHost(level.getBlockState(pos), config.hosts(), random)) {
                return pos.immutable();
            }
        }
        return null;
    }

    private static BlockPos.MutableBlockPos randomRimPos(RandomSource random,
                                                         OreBuddingVeinConfiguration config,
                                                         BlockPos center) {
        int radius = config.radius();
        return new BlockPos.MutableBlockPos(
                center.getX() + random.nextInt(radius * 2 + 1) - radius,
                center.getY() + random.nextInt(radius * 2 + 1) - radius,
                center.getZ() + random.nextInt(radius * 2 + 1) - radius
        );
    }

    private static boolean isHost(BlockState state, List<RuleTest> hosts, RandomSource random) {
        for (RuleTest host : hosts) {
            if (host.test(state, random)) {
                return true;
            }
        }
        return false;
    }
}