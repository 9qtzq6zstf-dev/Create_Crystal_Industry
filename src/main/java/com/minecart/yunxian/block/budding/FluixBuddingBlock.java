package com.minecart.yunxian.block.budding;

import com.minecart.yunxian.blockentity.budding.FluixBuddingBlockEntity;
import com.minecart.yunxian.registry.ModBlockEntities;
import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * 福鲁伊克斯母岩（仅在 AE2 加载时注册）。
 * <p>
 * AE2 没有福鲁伊克斯矿石，故不做“石头→矿石”转化；
 * 两条生长路径——(a) 芽→簇 通用生长；(b) 相邻 ae2:fluix_block → 本母岩 的再生传播——
 * 都要求母岩作为已激活的 ME 网格节点并成功扣除 AE 能量，否则本次不生长。
 */
public class FluixBuddingBlock extends GenericBuddingBlock {
    private static final int FLUIX_SPREAD_CHANCE = 25000;

    private final Supplier<Block> fluixBlockSupplier;
    private Block resolvedFluixBlock;

    public FluixBuddingBlock(int growthChance, BlockBehaviour.Properties properties,
                             Block smallBud, Block mediumBud, Block largeBud, Block cluster,
                             Supplier<Block> fluixBlockSupplier) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster);
        this.fluixBlockSupplier = fluixBlockSupplier;
    }

    /** 福鲁伊克斯母岩使用专用 BE（持有 ME 网格节点），不再使用共享展示 BE。 */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return ModBlockEntities.FLUIX_BUDDING.get().create(pos, state);
    }

    /** 芽→簇 生长路径的付费钩子：GenericBuddingBlock 在放置下一阶段前调用。 */
    @Override
    protected boolean payGrowthEnergy(ServerLevel level, BlockPos pos, BlockPos neighborPos) {
        if (level.getBlockEntity(pos) instanceof FluixBuddingBlockEntity be) {
            return be.tryConsumeGrowthEnergy();
        }
        return false; // 理论不应发生；拿不到 BE 就不放行，避免“免费生长”
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.randomTick(state, level, pos, random);

        // 再生传播：fluix_block → 本母岩
        if (random.nextInt(FLUIX_SPREAD_CHANCE) == 0) {
            trySpread(level, pos, random);
        }
    }

    private void trySpread(ServerLevel level, BlockPos centerPos, RandomSource random) {
        Block fluixBlock = resolvedFluixBlock();
        if (fluixBlock == null || fluixBlock == Blocks.AIR) {
            return; // 解析失败时彻底禁用
        }

        BlockPos targetPos = centerPos.offset(
                random.nextInt(3) - 1,
                random.nextInt(3) - 1,
                random.nextInt(3) - 1
        );
        if (targetPos.equals(centerPos)) {
            return;
        }

        if (level.getBlockState(targetPos).is(fluixBlock)) {
            // 传播同样需要母岩作为激活网格节点并扣除 AE
            if (level.getBlockEntity(centerPos) instanceof FluixBuddingBlockEntity be
                    && be.tryConsumeGrowthEnergy()) {
                level.setBlockAndUpdate(targetPos, defaultBlockState());
            }
        }
    }

    private Block resolvedFluixBlock() {
        if (resolvedFluixBlock == null) {
            resolvedFluixBlock = fluixBlockSupplier.get();
        }
        return resolvedFluixBlock;
    }
}