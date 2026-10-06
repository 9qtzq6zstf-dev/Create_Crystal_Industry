package com.minecart.yunxian.item;

import com.minecart.yunxian.registry.ModBlocks;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 可燃冰沙桶：倒出来的是<b>方块</b>，不是流体——和原版细雪桶一个道理（细雪压根没有流体）。
 * <p>
 * <b>为什么继承 {@link BucketItem} 而不是照抄原版的 {@code SolidBucketItem}</b>：
 * <ul>
 *   <li>倒出方块只要求覆盖 {@link #emptyContents}，两个基类都做得到；</li>
 *   <li>但 NeoForge 只给「<b>恰好是</b> {@code BucketItem} 类」的物品自动挂流体能力
 *       （{@code CapabilityHooks} 里是精确相等），{@code SolidBucketItem} 是 {@code BlockItem}，
 *       永远不在那一批里。继承 {@code BucketItem} 才能自己把能力补上
 *       （见 {@code ModCapabilities}），管道与注液器才灌得进来；</li>
 *   <li>顺带白拿 {@code BucketItem} 的整条 {@code use} 流程（射线段、空桶回收、创造模式不消耗）。</li>
 * </ul>
 * <b>覆盖的是五参那个重载</b>：{@code BucketItem#use} 调的是它，四参那个在 NeoForge 里已经标了
 * {@code @Deprecated} 并且只是转发到五参——照着原版 {@code SolidBucketItem} 覆盖四参的话，
 * 这里的逻辑<b>一次都不会被调用</b>，表现成「桶点了没反应」。
 */
public class FlammableIceSlurryBucketItem extends BucketItem {

    public FlammableIceSlurryBucketItem(Fluid content, Properties properties) {
        super(content, properties);
    }

    /**
     * 放置方块。判定与音效逐字照抄原版 {@code SolidBucketItem}：只放得进空气格，
     * 音效用细雪那一档，并报一次 {@code FLUID_PLACE} 关卡事件（幽匿感测体认这个）。
     */
    @Override
    public boolean emptyContents(@Nullable Player player, Level level, BlockPos pos,
                                 @Nullable BlockHitResult result, @Nullable ItemStack container) {
        if (!level.isInWorldBounds(pos) || !level.isEmptyBlock(pos)) {
            return false;
        }
        BlockState state = ModBlocks.FLAMMABLE_ICE_SLURRY.get().defaultBlockState();
        if (!level.isClientSide()) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        level.gameEvent(player, GameEvent.FLUID_PLACE, pos);
        level.playSound(player, pos, SoundEvents.BUCKET_EMPTY_POWDER_SNOW, SoundSource.BLOCKS, 1.0F, 1.0F);
        return true;
    }
}
