package com.minecart.yunxian.client;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class EchoHighlightClient {

    private static final List<BlockPos> POSITIONS = new ArrayList<>();
    private static ResourceKey<Level> dimension = Level.OVERWORLD;

    private EchoHighlightClient() {
    }

    public static void replace(ResourceKey<Level> dim, List<BlockPos> positions) {
        dimension = dim;
        POSITIONS.clear();
        POSITIONS.addAll(positions);
    }

    // 维度对不上就不显示，防止传送/跨维度后旧坐标错位
    public static List<BlockPos> positionsFor(ResourceKey<Level> currentDimension) {
        if (!currentDimension.equals(dimension)) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(POSITIONS);
    }
}