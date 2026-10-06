package com.minecart.yunxian.block;

import com.minecart.yunxian.registry.ModEffects;
import com.minecart.yunxian.registry.ModItems;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PowderSnowBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

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

    /**
     * 踏进来持续多久。
     * <p>
     * 比 {@code entityInside} 的调用间隔长得多（那个是每 tick 一次），所以待在里面时效果一直续着不闪，
     * 走出去之后余下这么一点点。3 秒是按"够让它烧你一下"定的：灼烧每 20 tick 扣 1 点，
     * 3 秒最多再补 3 点，不至于走出去还要疼半天。
     */
    private static final int EFFECT_TICKS = 60;

    public FlammableIceSlurryBlock(Properties properties) {
        super(properties);
    }

    /**
     * 踏进来的效果是<b>「灼寒」</b>（冻着，同时烧着），不是细雪那套。
     * <p>
     * 先照常走一遍 {@code super.entityInside}——陷进去的手感、雪花粒子、着火时把方块烧掉这些
     * 照样要，那是方块本身的性格，与"给什么状态效果"无关。
     * <p>
     * <b>但立刻把 {@code isInPowderSnow} 收回来。</b>原版那一行是细雪冻人的全部入口：
     * {@code LivingEntity#aiStep} 见它就把冻结值每 tick 往上顶，一路顶到满冻线，
     * 屏幕霜花、冰心、发抖、每 40 tick 那点冻伤全跟着来。这个方块<b>不是细雪</b>，
     * 标志立着就是撒谎；而且立着的话 {@code FrozenEffect#onIncomingDamage} 里那条
     * 「真踩在细雪里就不豁免冻伤」会把「灼寒」自己那份冻伤也放行，等于白给。
     * 收回之后冻结值只能由「灼寒」那份效果来顶，账目干净。
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        super.entityInside(state, level, pos, entity);
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        living.setIsInPowderSnow(false);

        if (!level.isClientSide) {
            // 每 tick 续一次；LivingEntity#addEffect 对已有的同类效果只是把时长抬回去，
            // 不会重复播"获得效果"那一套
            living.addEffect(new MobEffectInstance(ModEffects.SCORCHING_COLD, EFFECT_TICKS));
        }
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

    /**
     * 鼠标中键「选取方块」拿到的是可燃冰沙<b>桶</b>。
     * <p>
     * 这个方块和细雪一样刻意没有物品形态（{@code BLOCKS.register} 而不是 {@code registerBlock}，
     * 见 {@link com.minecart.yunxian.registry.ModBlocks}），于是默认实现
     * {@code new ItemStack(this)} 拿到的是空气——创造模式里中键点它什么都不会发生，
     * 因为客户端 {@code Minecraft#pickBlock} 见 {@code isEmpty()} 就直接 return 了。
     * <p>
     * 覆盖的是<b>五参那个</b>（{@code IBlockExtension#getCloneItemStack(BlockState, HitResult,
     * LevelReader, BlockPos, Player)}），客户端 {@code Minecraft#pickBlock} 直接调的就是它这一路。
     * 别去覆盖三参那个 {@code getCloneItemStack(LevelReader, BlockPos, BlockState)}：它被 NeoForge 标了
     * {@code @Deprecated //Forge: Use more sensitive version}（原版那些没有物品的方块——花盆、旗帜、
     * 发光方块——覆盖的都是它），覆盖它编译会带一条 deprecation 警告，而且那个版本拿不到准心信息。
     * <p>
     * 生存模式里这条只负责"背包里有桶时把它切到手上"，没有就什么也不做，与原版一致。
     */
    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level,
                                       BlockPos pos, Player player) {
        return new ItemStack(ModItems.FLAMMABLE_ICE_SLURRY_BUCKET.get());
    }
}
