package com.minecart.yunxian.block;

import com.minecart.yunxian.registry.ModEffects;
import com.minecart.yunxian.registry.ModItems;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
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
import net.minecraft.world.phys.Vec3;

/**
 * 可燃冰沙方块：整块照抄原版细雪——踩上去会陷、能踩着皮革靴走、能从上方落穿，
 * 这些全在 {@link PowderSnowBlock} 里。
 * <p>
 * 改了三处，都是因为<b>它不是细雪</b>：
 * <ul>
 *   <li><b>踏进来给什么</b>：细雪那套照顶冻结值把人冻住，这里换成「灼寒」，
 *       掉进来的物品则直接点着（见 {@link #entityInside}）；</li>
 *   <li><b>「舀」</b>：细雪块把「空桶右键舀起来」做成了 {@code PowderSnowBlock#pickupBlock}
 *       （它 {@code implements BucketPickup}），但那里<b>写死了返回细雪桶</b>，
 *       这里让它还我们自己的桶；</li>
 *   <li><b>鼠标中键选取</b>：这个方块没有物品形态，得手写 {@link #getCloneItemStack}。</li>
 * </ul>
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

    /**
     * 掉进来的<b>物品</b>点着多久（秒）。
     * <p>
     * 8 秒是照抄原版火方块的量（{@code BaseFireBlock#entityInside} 给的就是 8 秒），
     * 所以"掉进可燃冰沙里烧起来"和"掉进火里烧起来"是同一档。
     * 在里面时烧完就补，所以真正的效果是：掉进去就一直烧，离开之后再烧 8 秒。
     * <p>
     * 掉下去的代价照旧走原版：着火每 20 tick 扣 1 点，而物品的血量是 5
     * （{@code ItemEntity}），也就是<b>烧满 5 秒就被烧没了</b>——
     * 除非它带着 {@code FIRE_RESISTANT}（下界合金那一档），那种连火都点不着
     * （{@code ItemEntity#fireImmune} 会认这个组件）。
     */
    private static final float ITEM_BURN_SECONDS = 8.0F;

    public FlammableIceSlurryBlock(Properties properties) {
        super(properties);
    }

    /**
     * 踏进来的效果是<b>「灼寒」</b>（冻着，同时烧着），不是细雪那套。
     * <b>掉进来的物品则直接点着</b>（见 {@link #ITEM_BURN_SECONDS}）——活物有那颗效果管着，
     * 物品没有效果可言，只能给真火。
     * <p>
     * <b>整段自己写，不走 {@code super.entityInside}。</b>细雪那三件事这个方块一件都不要，
     * 而其中最要命的那件（烧方块）是<b>撤销不了的</b>——只能是"别做"，不能是"做完再改回来"：
     * <ul>
     *   <li><b>着火的东西把方块毁掉</b>：原版那句 {@code entity.isOnFire() && ... →
     *       level.destroyBlock(pos, false)} 是「雪被烧化」。摆在这里就是：掉进来的物品刚被点着，
     *       回头就把可燃冰沙烧穿一个洞。</li>
     *   <li><b>{@code setIsInPowderSnow(true)}</b>：细雪冻人的全部入口。
     *       {@code LivingEntity#aiStep} 见它就把冻结值每 tick 往上顶，一路顶到满冻线，
     *       屏幕霜花、冰心、发抖、每 40 tick 那点冻伤全跟着来。标志立着就是撒谎；
     *       而且立着的话 {@code FrozenEffect#onIncomingDamage} 里那条「真踩在细雪里就不豁免冻伤」
     *       会把「灼寒」自己那份冻伤也放行。它同时是「雪能灭火」的判据
     *       （{@code Entity#move} 末尾 {@code isOnFire() && isInPowderSnow} 就把火清成负的免疫期）。</li>
     *   <li><b>{@code setSharedFlagOnFire(false)}</b>：「雪把身上的火压灭」的外观，
     *       清的是同步给客户端那个着火标志。</li>
     * </ul>
     * 这三样去掉之后，留下「陷进去的手感」与「雪花粒子」照抄原版——它们才是这个方块真正想要的性格，
     * 与"给什么状态效果"无关。
     * <p>
     * <b>顺带</b>：既然没人再来清那个着火标志，也就不必在这里往回摆了——原来那两行
     * "先 super 再撤销"的写法就是为了它。现在唯一的写入者是 {@code Entity#baseTick}（按真火算）
     * 与「灼寒」的 {@code applyEffectTick}（按效果算），两者先后有序，各管各的。
     */
    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        // 陷进去：逐字照抄 PowderSnowBlock#entityInside 的头一段
        if (entity instanceof LivingEntity && entity.getInBlockState().is(this)) {
            entity.makeStuckInBlock(state, new Vec3(0.9F, 1.5, 0.9F));
            if (level.isClientSide) {
                RandomSource random = level.getRandom();
                boolean moved = entity.xOld != entity.getX() || entity.zOld != entity.getZ();
                if (moved && random.nextBoolean()) {
                    level.addParticle(ParticleTypes.SNOWFLAKE,
                            entity.getX(), pos.getY() + 1.0, entity.getZ(),
                            Mth.randomBetween(random, -1.0F, 1.0F) * 0.083333336F, 0.05F,
                            Mth.randomBetween(random, -1.0F, 1.0F) * 0.083333336F);
                }
            }
        }

        if (level.isClientSide) {
            return;
        }

        // 掉进来的物品点着它，时长照抄火方块（BaseFireBlock#entityInside 也是 8 秒）。
        // 活物不走这条：它们拿的是「灼寒」，那层火是效果自己的事（见 ScorchingColdEffect）
        //
        // 只在烧完时才补，**不要每 tick 都点**：着火的伤害判据是
        // Entity#baseTick 里的 remainingFireTicks % 20 == 0，而 igniteForSeconds(8) 会把值
        // 直接设成 160——160 正是 20 的倍数，于是每补一次下一个 tick 就白挨一下。
        // entityInside 对掉落中的物品是每 tick 都调的（ItemEntity 里 move 的条件），
        // 每 tick 都补就成了"每 tick 扣 1 点"，物品血量只有 5，四分之一秒就没了。
        // 按"烧完才补"写，值正常往下数，才是原版那档每 20 tick 扣 1 点。
        if (entity instanceof ItemEntity item && !item.fireImmune()
                && item.getRemainingFireTicks() <= 0) {
            item.igniteForSeconds(ITEM_BURN_SECONDS);
        }

        if (entity instanceof LivingEntity living) {
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
