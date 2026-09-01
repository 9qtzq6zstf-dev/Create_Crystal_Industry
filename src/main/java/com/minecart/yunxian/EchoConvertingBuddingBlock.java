package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class EchoConvertingBuddingBlock extends GenericBuddingBlock {

    private static final int CONVERSION_CHANCE = 4;
    private static final int CONVERSION_RADIUS = 2;

    /**
     * 生长格允许生长的最高光照：光照亮度必须 < 7（即 0–6）才会生长，光照 ≥ 7 停止生长。
     */
    private static final int GROWTH_LIGHT_THRESHOLD = 7;

    /**
     * 召唤监守者的检测半径（格）。与幽匿尖啸体一致：以母岩为中心 ±48 格内已有监守者则不重复召唤。
     */
    private static final double WARDEN_CHECK_RADIUS = 48.0;

    private static final TagKey<Block> ECHO_CONVERTIBLE = TagKey.create(
            Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "echo_convertible")
    );

    public EchoConvertingBuddingBlock(int growthChance, Properties properties,
                                      Block smallBud, Block mediumBud, Block largeBud, Block cluster) {
        super(growthChance, properties, smallBud, mediumBud, largeBud, cluster);
        // 默认 false：玩家放置的母岩不会召唤监守者；自然生成的由世界生成置为 true
        this.registerDefaultState(this.stateDefinition.any().setValue(BlockStateProperties.CAN_SUMMON, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(BlockStateProperties.CAN_SUMMON);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // 生长交给父类（父类先检查生长格光照 < 阈值 才长）
        super.randomTick(state, level, pos, random);

        // 幽匿转化保持不变
        if (random.nextInt(CONVERSION_CHANCE) == 0) {
            tryConvertNearby(level, pos, random);
        }
    }

    /**
     * 生长格光照亮度必须 < GROWTH_LIGHT_THRESHOLD 才允许长出/晋级晶簇。
     */
    @Override
    protected boolean canGrowAtLight(ServerLevel level, BlockPos neighborPos) {
        return level.getMaxLocalRawBrightness(neighborPos) < GROWTH_LIGHT_THRESHOLD;
    }

    /**
     * 自然生成的母岩（can_summon=true）被玩家破坏时，在周围召唤一只监守者。
     * 玩家放置的母岩 can_summon=false，不会召唤。
     * 注意：NeoForge 里返回 false 表示「不破坏方块」，所以必须返回 super 的结果。
     */
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player,
                                       boolean willHarvest, FluidState fluid) {
        boolean result = super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);

        if (state.getValue(BlockStateProperties.CAN_SUMMON) && level instanceof ServerLevel serverLevel) {
            trySummonWarden(serverLevel, pos);
        }
        return result;
    }

    private void trySummonWarden(ServerLevel level, BlockPos pos) {
        // 与幽匿尖啸体（SculkShriekerBlock）完全一致的检测方式：
        // 以母岩为中心 ±48 格内已有监守者则不重复召唤。
        // getEntitiesOfClass(Class, AABB, Predicate<? super T>) 接受 Predicate<Entity>，
        // 所以 EntitySelector.NO_SPECTATORS 可以直接传，无需强转。
        AABB checkArea = new AABB(pos).inflate(WARDEN_CHECK_RADIUS);
        if (!level.getEntitiesOfClass(Warden.class, checkArea, EntitySelector.NO_SPECTATORS).isEmpty()) {
            return;
        }

        BlockPos spawnPos = findWardenSpawnPos(level, pos);
        Warden warden = EntityType.WARDEN.create(level);
        if (warden == null) {
            return;
        }
        warden.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, 0.0F, 0.0F);
        warden.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.TRIGGERED, null);
        level.addFreshEntityWithPassengers(warden);
    }

    /**
     * 在母岩周围（±3 水平、±1 垂直）找一个空气格，要求下方是完整方块。
     */
    private static BlockPos findWardenSpawnPos(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 10; i++) {
            int x = pos.getX() + level.getRandom().nextInt(7) - 3;
            int y = pos.getY() + level.getRandom().nextInt(3) - 1;
            int z = pos.getZ() + level.getRandom().nextInt(7) - 3;
            mutablePos.set(x, y, z);
            BlockPos below = mutablePos.below();
            if (level.getBlockState(mutablePos).isAir()
                    && level.getBlockState(below).isCollisionShapeFullBlock(level, below)) {
                return mutablePos.immutable();
            }
        }
        return pos.immutable(); // 兜底：直接生成在母岩位置
    }

    private void tryConvertNearby(ServerLevel level, BlockPos centerPos, RandomSource random) {
        int r = CONVERSION_RADIUS;
        BlockPos targetPos = centerPos.offset(
                random.nextInt(2 * r + 1) - r,
                random.nextInt(2 * r + 1) - r,
                random.nextInt(2 * r + 1) - r
        );
        if (targetPos.equals(centerPos)) {
            return;
        }

        BlockState targetState = level.getBlockState(targetPos);
        if (targetState.is(ECHO_CONVERTIBLE)) {
            level.setBlockAndUpdate(targetPos, Blocks.SCULK.defaultBlockState());
        }
    }
}