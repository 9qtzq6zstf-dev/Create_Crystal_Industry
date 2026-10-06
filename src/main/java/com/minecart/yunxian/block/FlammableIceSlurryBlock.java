package com.minecart.yunxian.block;

import com.minecart.yunxian.registry.ModItems;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 可燃冰沙方块：整块照抄原版细雪——踩上去会陷、掉进去会冻、能踩着皮革靴走、能从上方落穿，
 * 这些全在 {@link PowderSnowBlock} 里，本类一个字都不用写。
 * <p>
 * <b>唯一要改的是「舀」：</b>细雪块把「空桶右键舀起来」做成了
 * {@code PowderSnowBlock#pickupBlock}（它 {@code implements BucketPickup}），但那里
 * <b>写死了返回细雪桶</b>。所以这里只覆盖这一个方法，让它还我们自己的桶。
 * 这条路与原版流体系统无关——细雪和水不一样，它压根没有流体。
 */
public class FlammableIceSlurryBlock extends PowderSnowBlock {

    public FlammableIceSlurryBlock(Properties properties) {
        super(properties);
    }

    /**
     * 空桶右键舀走一格，换回可燃冰沙桶。
     * <p>
     * 前两行逐字照抄细雪：把方块换成空气、再报一次「方块被破坏」的关卡事件（2001 是原版
     * {@code DestroyBlockProgress} 那条，用来放碎块音效与粒子）。
     */
    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL_IMMEDIATE);
        if (!level.isClientSide()) {
            level.levelEvent(2001, pos, Block.getId(state));
        }
        return new ItemStack(ModItems.FLAMMABLE_ICE_SLURRY_BUCKET.get());
    }
}
