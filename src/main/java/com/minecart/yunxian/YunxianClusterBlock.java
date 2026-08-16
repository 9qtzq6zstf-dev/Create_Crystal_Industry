package com.minecart.yunxian;

import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

public class YunxianClusterBlock extends AmethystClusterBlock {
    // 虽然不再用于掉落，但可能用于其他地方（比如日志或调试）
    private final String stageKey;

    public YunxianClusterBlock(int stage, int height, BlockBehaviour.Properties properties, String stageKey) {
        super(stage, height, properties);
        this.stageKey = stageKey;
    }

    // 不再重写 getDrops，完全使用战利品表系统
}