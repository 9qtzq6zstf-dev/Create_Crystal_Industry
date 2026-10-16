package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.fan.NozzleBlock;
import com.simibubi.create.content.kinetics.fan.NozzleBlockEntity;
import com.simibubi.create.content.logistics.box.PackageEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import org.jetbrains.annotations.Nullable;

/**
 * 掉落物"直接入库存"：物品实体即将进入世界时，若它落在某台吸入中的吸尘器的吸取覆盖里，
 * 就跳掉实体这一步，直接把物品收进那台吸尘器的库存（{@link MechanicalCleanerBlockEntity#tryAbsorbDrop}）。
 * <p>
 * 为什么值得做：覆盖里的掉落物本来就在同一个 tick 被收走，实体只活了一瞬间，却要走完一整套实体流程
 * （登记 section、同步给附近客户端、每 tick 参与碰撞与吸取扫描）。母岩/晶簇这类产线一次破坏就掉一份，
 * 省下来的都是纯开销。
 * <p>
 * 为什么钩在 {@link EntityJoinLevelEvent} 而不是掉落事件：掉落物的来源太多
 * （原版掉落表走 {@code BlockDropsEvent}、智能钻头自己 popResource、脚本方块走
 * {@link com.minecart.yunxian.registry.ScriptedBlockDrops}、别的模组的机器各写各的），
 * 但终点都是"一个 ItemEntity 进入世界"，钩在这里一次覆盖全部，且不用改动任何一条掉落路径。
 * <p>
 * <b>怎么找到那台吸尘器</b>：这里认的覆盖必须和 {@link MechanicalCleanerBlockEntity} 按 tick
 * 收实体时用的那两套一一对上，否则就会出现"该直吸的没直吸、照常生成实体再被吸走"：
 * <ol>
 *   <li><b>轴线范围</b>——{@link #absorbByAxis}。没有喷嘴（或点不在喷嘴场里）时，风场边界
 *       （{@code airCurrent.bounds}）是一条从吸尘器出发、沿朝向的直线（见 {@code rebuildAirBounds}），
 *       所以掉落物沿六个轴向任意方向最多 20 格（{@link MechanicalCleanerBlockEntity#SUCK_RANGE_MAX}）
 *       必定能碰到那台吸尘器所在的方块。逐格查方块状态，只有命中吸尘器才取方块实体，
 *       最坏 6 × 20 次调色板查询、命中即返回。</li>
 *   <li><b>喷嘴（分散网）场</b>——{@link #absorbByNozzle}。正前方挂着喷嘴时，风场是以喷嘴为中心、
 *       半径 = 气流长度的<b>立方体</b>（Create 的 {@code NozzleBlockEntity.calcRange()} 给的边长），
 *       场内的点根本不在任何一条过吸尘器的轴线上，沿六轴外扫永远扫不到那台机器。所以反向找：
 *       以落点为中心扫一个半径 {@code SUCK_RANGE_MAX} 的立方体找喷嘴，沿着喷嘴朝向把背后那一格的
 *       机器点出来。喷嘴只能贴在气流出口面（{@code NozzleBlock.canSurvive}），所以它背后紧邻的
 *       一格必定是那台风源。</li>
 * </ol>
 * 已知不覆盖（保持原样：掉落物照常生成实体，随后由吸尘器收走，只是没省下这个实体）：
 * <b>无动力的吸尘器</b>——它没有风场，靠 {@link MechanicalCleanerBlockEntity#collectItemsPassive}
 * 只捡正前方紧贴的一格（{@link MechanicalCleanerBlockEntity#PASSIVE_SUCK_RANGE}）。
 * 那条路不收在这里，因为接管的前提是"这一 tick 本来就会被收走"，而无动力时
 * {@code getSpeed()} 恒为 0，连按 tick 收实体都要等到有动力才发生。
 */
public final class CleanerDropAbsorption {

    /** 事件在每次实体入场时都会跑一遍，方向数组先存好，省掉 values() 的克隆 */
    private static final Direction[] DIRECTIONS = Direction.values();

    private CleanerDropAbsorption() {
    }

    /** 事件入口（在 {@code Yunxian} 的构造器里挂到游戏事件总线上） */
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        // 只处理服务端新生成的物品：客户端不持有库存，从存档读回来的实体本来就已经在吸尘器的账上了
        if (!(event.getLevel() instanceof ServerLevel level) || event.loadedFromDisk()) {
            return;
        }
        if (!ModConfig.Common.CLEANER_DIRECT_ABSORB.get()) {
            return;
        }

        // 包裹走的是 Create 的 PackageEntity（LivingEntity，不是 ItemEntity），同样接管：
        // 吸尘器按 tick 扫的那条路也能收到它，这里只是让它和前两类物品一样"压根不生成实体"
        if (event.getEntity() instanceof PackageEntity packageEntity) {
            ItemStack box = packageEntity.getBox();
            if (!box.isEmpty() && absorbIntoSuction(level, packageEntity.position(), box)) {
                packageEntity.discard();
                event.setCanceled(true);
            }
            return;
        }

        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }

        ItemStack stack = itemEntity.getItem();
        if (stack.isEmpty()) {
            return;
        }

        if (absorbIntoSuction(level, itemEntity.position(), stack)) {
            event.setCanceled(true);
        }
    }

    /**
     * 从掉落物所在位置找出能收下它的那台吸尘器。先沿六个轴逐格找；走不通（点在喷嘴场里，
     * 不在任何过吸尘器的轴线上）时再单独给周围的喷嘴一次机会，见 {@link #absorbByAxis}
     * 与 {@link #absorbByNozzle}。
     */
    private static boolean absorbIntoSuction(ServerLevel level, Vec3 point, ItemStack stack) {
        return absorbByAxis(level, point, stack) || absorbByNozzle(level, point, stack);
    }

    /**
     * 沿六个轴向逐格找吸尘器方块，找到第一台能收下的就收走。
     * <p>
     * 为什么"逐格找方块"这一条管得住轴线风场：没喷嘴时风场是一条以吸尘器为起点、沿朝向的
     * 直线（{@code airCurrent.bounds}），掉落物沿六轴任意方向外扫，最远 20 格
     * （{@link MechanicalCleanerBlockEntity#SUCK_RANGE_MAX}）内必定碰到那台吸尘器方块。
     * <p>
     * 每个方向上遇到未加载的区块就停——风场不会跨过未加载的区块，方块实体也取不到。
     */
    private static boolean absorbByAxis(ServerLevel level, Vec3 point, ItemStack stack) {
        Block block = ModBlocks.MECHANICAL_CLEANER.get();
        BlockPos origin = BlockPos.containing(point);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (Direction direction : DIRECTIONS) {
            cursor.set(origin);
            for (int step = 1; step <= MechanicalCleanerBlockEntity.SUCK_RANGE_MAX; step++) {
                cursor.move(direction);
                if (!level.isLoaded(cursor)) {
                    break;
                }
                if (!level.getBlockState(cursor).is(block)) {
                    continue;
                }
                // 判定（轴线边界 / 喷嘴场 / 视线 / 过滤 / 容量）全在吸尘器那边，
                // 与按 tick 收实体那条路共用同一套口径
                if (level.getBlockEntity(cursor) instanceof MechanicalCleanerBlockEntity cleaner
                        && cleaner.tryAbsorbDrop(point, stack, cleaner.getMaxDistance())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * 喷嘴（分散网）场里那一类落点：以掉落物为中心扫一个 {@code 2r+1} 见方的立方体找喷嘴
     * （{@code r} = {@link MechanicalCleanerBlockEntity#SUCK_RANGE_MAX}），碰到就顺着喷嘴朝向
     * 把背后那台机器点出来再问一次。
     * <p>
     * 为什么非要有这一条：喷嘴把背后机器的一小段气流铺成一颗以它为中心的立方体场，
     * 场里的点（尤其侧向那些舱口的落点）不在任何一条过吸尘器的轴线上，
     * {@link #absorbByAxis} 逐格外扫永远碰不到那台机器——这正是"正前方的能拦、偏侧向的拦不住"的成因。
     * <p>
     * 为什么扫立方体而不是只查邻格：喷嘴贴的是气流出口面，离掉落物最远可以有 {@code r} 格
     * （半径就是 {@code r}），邻格压根包不住，早期按"六邻格之一必是喷嘴"写的证明是错的。
     * 半径直接取 {@code SUCK_RANGE_MAX}（20）而不是去问每台机器：它是风场半径的上限
     * （滑块的 {@code SUCK_RANGE_MAX}），扫满才不漏；真在场里的点会由
     * {@code tryAbsorbDrop} 用机器自己的半径复判一次，所以扫宽了只是多几次查询，不会误收。
     * <p>
     * 代价：一格喷嘴都没有时要走满整颗立方体（最坏 6.9 万次方块状态查询），但这条只在轴线那条路
     * 空手而归时才走——掉落物要么本来就落在轴线风场里（由 {@link #absorbByAxis} 提前返回），
     * 要么确实孤零零地待在喷嘴场里，后者才是真正需要花这个钱的情况。
     */
    private static boolean absorbByNozzle(ServerLevel level, Vec3 point, ItemStack stack) {
        BlockPos origin = BlockPos.containing(point);
        BlockPos nozzlePos = findNearbyNozzle(level, origin);
        if (nozzlePos == null) {
            return false;
        }
        BlockState state = level.getBlockState(nozzlePos);
        Direction awayFromFan = state.getValue(NozzleBlock.FACING);
        BlockPos cleanerPos = nozzlePos.relative(awayFromFan.getOpposite());
        if (!(level.getBlockEntity(cleanerPos) instanceof MechanicalCleanerBlockEntity cleaner)) {
            return false;
        }
        return cleaner.tryAbsorbDrop(point, stack, cleaner.getMaxDistance());
    }

    /**
     * 在以 {@code origin} 为中心、半径 {@link MechanicalCleanerBlockEntity#SUCK_RANGE_MAX} 的立方体里
     * 找一格喷嘴，没有就返回 {@code null}（未加载的区块直接跳过，取不到方块实体）。
     * <p>
     * 取哪一格都行：落点是否真在场里、有没有视线，都由 {@link MechanicalCleanerBlockEntity#tryAbsorbDrop}
     * 用<b>那台机器自己的</b>半径复判一次，所以这里只要把场上挂着的喷嘴点出来就够了，不必求最近。
     * <p>
     * 写成"整颗立方体一趟走完"而不是由内向外逐层扫：两者效果一样，而这一趟的方块状态查询来自
     * 区块的调色板缓存、{@code isLoaded} 也只是查已加载表，6.9 万次在"轴线那条路已经空手而归"
     * 的少数落点上完全付得起；分层写法在本仓库实测更容易写错（棱上的格子会被重复访问）。
     */
    @Nullable
    private static BlockPos findNearbyNozzle(ServerLevel level, BlockPos origin) {
        int r = MechanicalCleanerBlockEntity.SUCK_RANGE_MAX;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos cursor = origin.offset(dx, dy, dz);
                    // 先比方块状态（调色板查询，很便宜），确认是喷嘴才值得去取方块实体
                    if (level.isLoaded(cursor) && level.getBlockState(cursor).is(AllBlocks.NOZZLE.get())
                            && level.getBlockEntity(cursor) instanceof NozzleBlockEntity) {
                        return cursor;
                    }
                }
            }
        }
        return null;
    }
}
