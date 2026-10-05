package com.minecart.yunxian.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * 可燃冰圣代：一杯放在地上的可燃冰，顶着熔岩点缀。三件事：
 * <ol>
 *   <li><b>右键收回</b>：空手右键收进背包，潜行右键另放一个。手里拿着别的东西时本方块当自己不存在，
 *       回 {@code SKIP_DEFAULT_BLOCK_INTERACTION} 而不是 {@code PASS_TO_DEFAULT}——后者会把控制权
 *       转给 {@code useWithoutItem}，于是拿着扳手右键也会把圣代收走；</li>
 *   <li><b>冻住周围的水</b>：每 {@link #FREEZE_TICKS 秒}把半径 {@link #FREEZE_RADIUS} 内的水面
 *       换成霜冰，参数照抄原版冰霜行者；</li>
 *   <li><b>像蜡烛一样能点着</b>：打火石／火焰弹点着、着火的弹射物也能点着，点着后冒蓝火、发蓝光，
 *       空手右键吹灭（吹灭优先于收回，所以亮着时要收两次手）。</li>
 * </ol>
 * <b>潜行在本类里不需要判断</b>：原版潜行时会整个跳过方块的 {@code useItemOn} 与
 * {@code useWithoutItem}、把控制权交给手持物品（{@code ServerPlayerGameMode#useItemOn}
 * 的 flag1，客户端 {@code MultiPlayerGameMode#performUseItemOn} 同理），
 * 所以「潜行 = 放置 / 不潜行 = 交互」这条分工是原版白送的。
 */
public class FlammableSundaeBlock extends Block {

    /**
     * 点着状态。用原版的 {@code LIT}：打火石/火焰弹那套是原版按<b>方块状态</b>认的，
     * 换名字的话 {@code #getToolModifiedState} 那条路要自己接，得不偿失。
     */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** 点着时的亮度，对齐蓝火（灵魂火）那一档 */
    public static final int LIT_LIGHT = 10;

    /**
     * 结霜半径，对齐原版冰霜行者 I（{@code enchantment/frost_walker.json} 里 replace_disk 的
     * {@code radius = base 3.0}）。改这个数就等于换冰霜行者等级。
     */
    private static final int FREEZE_RADIUS = 3;

    /** 多久扫一次周围的水面（tick） */
    private static final int FREEZE_TICKS = 20;

    /**
     * 火焰粒子的落点，取自 {@code block.json} 里那两块熔岩（贴图 {@code #1}）的顶面中点，
     * 坐标是方块内的相对位置。熔岩是这根"蜡烛的芯"，火就冒在它上面。
     */
    private static final Vec3[] FLAME_OFFSETS = {
            new Vec3(0.55, 0.585, 0.39),
            new Vec3(0.62, 0.585, 0.45),
            new Vec3(0.375, 0.42, 0.625),
    };

    /**
     * 包围盒，取自 {@code block.json} 的实际范围：x/z 4.25~11.75，顶到最上面那颗冰球的 9.25。
     */
    private static final VoxelShape SHAPE = Block.box(4.25, 0.0, 4.25, 11.75, 9.25, 11.75);

    public FlammableSundaeBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(LIT));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    // ==================== 右键交互 ====================

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            // 空手：转给 useWithoutItem，由它决定「吹灭还是收走」（原版只在 PASS_TO_DEFAULT 且主手时才转）
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (stack.is(this.asItem())) {
            takeBack(level, pos, player);
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        // 手里是别的物品：交给那把工具照常干活，不收圣代
        return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 空手右键：亮着就先吹灭，没亮才收走。
     * <p>
     * 顺序是刻意的——「像蜡烛一样」意味着空手右键该能把它吹灭，但同一个手势又早被定成了「收回」。
     * 让吹灭优先，两件事就都成立，代价只是亮着的时候要收两次（先吹灭、再拿走），
     * 而这恰好也是蜡烛的手感。
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hitResult) {
        if (state.getValue(LIT)) {
            extinguish(player, state, level, pos);
        } else {
            takeBack(level, pos, player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** 把方块收回玩家背包；装不下就丢在脚下。只在服务端动世界。 */
    private void takeBack(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return;
        }
        ItemStack sundae = new ItemStack(this.asItem());
        level.removeBlock(pos, false);
        if (!player.getInventory().add(sundae)) {
            player.drop(sundae, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.2F, 1.0F);
    }

    // ==================== 点燃 / 吹灭 ====================

    /**
     * 打火石、火焰弹点着走这里。
     * <p>
     * NeoForge 把「点燃」做成了物品能力 {@link ItemAbilities#FIRESTARTER_LIGHT}：
     * 原版的 {@code FlintAndSteelItem}／{@code FireChargeItem} 都只是拿这个能力去问方块
     * （{@code blockstate.getToolModifiedState(context, FIRESTARTER_LIGHT, false)}），
     * 由方块自己给出「点着之后的状态」——所以这里返回一个 {@code LIT=true} 的状态就够了，
     * 音效、{@code gameEvent}、打火石的耐久都是物品那边顺手做完的，也不需要把本方块塞进
     * {@code minecraft:candles} 之类的标签。
     */
    @Override
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility itemAbility,
                                           boolean simulate) {
        if (itemAbility != ItemAbilities.FIRESTARTER_LIGHT || state.getValue(LIT)) {
            return null;
        }
        if (!context.getItemInHand().canPerformAction(itemAbility)) {
            return null;
        }
        return state.setValue(LIT, true);
    }

    /** 着火的箭之类打上来也能点着（照 {@code AbstractCandleBlock}） */
    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        if (!level.isClientSide && projectile.isOnFire() && !state.getValue(LIT)) {
            level.setBlock(hit.getBlockPos(), state.setValue(LIT, true), Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    /** 吹灭：冒一缕烟、响一声。粒子与音效都不分端（客户端的 useWithoutItem 也会跑到这里） */
    private static void extinguish(Player player, BlockState state, Level level, BlockPos pos) {
        // UPDATE_ALL_IMMEDIATE 与原版蜡烛一致：点亮/吹灭会改亮度，要立刻把光照更新发出去
        level.setBlock(pos, state.setValue(LIT, false), Block.UPDATE_ALL_IMMEDIATE);
        for (Vec3 offset : FLAME_OFFSETS) {
            level.addParticle(ParticleTypes.SMOKE,
                    pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z, 0.0, 0.1, 0.0);
        }
        level.playSound(null, pos, SoundEvents.CANDLE_EXTINGUISH, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
    }

    /** 点着后冒蓝火：把 {@code AbstractCandleBlock} 那套火焰/烟/环境音照抄一份，火焰换成灵魂火色 */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        for (Vec3 offset : FLAME_OFFSETS) {
            level.addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z, 0.0, 0.0, 0.0);
        }
        if (random.nextFloat() < 0.3F) {
            Vec3 offset = FLAME_OFFSETS[random.nextInt(FLAME_OFFSETS.length)];
            level.addParticle(ParticleTypes.SMOKE,
                    pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z, 0.0, 0.0, 0.0);
            if (random.nextFloat() < 0.17F) {
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        SoundEvents.CANDLE_AMBIENT, SoundSource.BLOCKS,
                        1.0F + random.nextFloat(), random.nextFloat() * 0.7F + 0.3F, false);
            }
        }
    }

    // ==================== 冰霜行者 ====================

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        level.scheduleTick(pos, this, FREEZE_TICKS);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        freezeAround(level, pos);
        level.scheduleTick(pos, this, FREEZE_TICKS);
    }

    /**
     * 把周围的水面冻成霜冰。
     * <p>
     * 条件和半径都照抄原版冰霜行者（{@code enchantment/frost_walker.json} 里的
     * {@code replace_disk}）：底层与本体都是水、<b>正上方是空气</b>（这条是为了别冻到屋顶下、
     * 墙里的水），换成 {@code minecraft:frosted_ice}。
     * <p>
     * 用霜冰而不是普通冰是刻意的：霜冰自带融化逻辑（{@code FrostedIceBlock#onPlace} 会自己
     * 排一个融化刻），所以这效果不会永久改写地形，冻上又化开正是冰霜行者的样子。
     * <p>
     * <b>只冻水源</b>（{@code isSource()}），比原版窄一格：原版连流动的水一起冻，
     * 但那会打断水道与机械动力的流体装置，对装饰方块来说不值当。想和原版看齐就把下面那条
     * {@code isSource()} 去掉。
     */
    private static void freezeAround(ServerLevel level, BlockPos pos) {
        BlockState frostedIce = Blocks.FROSTED_ICE.defaultBlockState();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -FREEZE_RADIUS; dx <= FREEZE_RADIUS; dx++) {
            for (int dz = -FREEZE_RADIUS; dz <= FREEZE_RADIUS; dz++) {
                if (dx * dx + dz * dz > FREEZE_RADIUS * FREEZE_RADIUS) {
                    continue; // 圆形而不是方形，和 replace_disk 的 radius 一致
                }
                for (int dy = -1; dy <= 0; dy++) {
                    cursor.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (!level.getBlockState(cursor).is(Blocks.WATER) || !level.getFluidState(cursor).isSource()) {
                        continue;
                    }
                    if (!level.getBlockState(cursor.above()).isAir()) {
                        continue;
                    }
                    // 取不可变副本：gameEvent 那边可能把位置存起来（振动），不能让它拿着这个会被改的游标
                    BlockPos frozen = cursor.immutable();
                    level.setBlockAndUpdate(frozen, frostedIce);
                    level.gameEvent(null, GameEvent.BLOCK_PLACE, frozen);
                }
            }
        }
    }
}
