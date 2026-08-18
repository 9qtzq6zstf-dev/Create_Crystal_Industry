package com.minecart.yunxian;

import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class YunxianClusterBlock extends AmethystClusterBlock {
    @SuppressWarnings("unused")
    private final String stageKey;

    public YunxianClusterBlock(int stage, int height, Properties properties, String stageKey) {
        super(stage, height, properties);
        this.stageKey = stageKey;
    }
}
