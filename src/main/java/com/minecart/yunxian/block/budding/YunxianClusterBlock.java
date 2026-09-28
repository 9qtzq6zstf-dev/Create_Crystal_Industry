package com.minecart.yunxian.block.budding;

import com.minecart.yunxian.effect.ArclightSource;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;

public class YunxianClusterBlock extends AmethystClusterBlock {

    @SuppressWarnings("unused")
    private final String stageKey;

    /** 弧光石家族为 true：见 {@code BuddingFamily.Appearance#sparkParticles()} */
    private final boolean sparks;

    /**
     * 参数即原版 {@code AmethystClusterBlock} 的那两个：碰撞箱由它们算出，
     * 取值见 {@code BuddingFamilies.Stage}（照抄原版，别自己改比例）。
     */
    public YunxianClusterBlock(float height, float aabbOffset, Properties properties, String stageKey,
                               boolean sparks) {
        super(height, aabbOffset, properties);
        this.stageKey = stageKey;
        this.sparks = sparks;
    }

    /**
     * 芽/簇周围的电火花。走原版 {@link net.minecraft.world.level.block.Block#animateTick}：
     * 只在客户端、只对玩家附近的方块随机调用，所以这里不需要任何"离玩家多远"的判断。
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (sparks) {
            ArclightSource.maybeSpark(level, pos, random);
        }
    }
}
