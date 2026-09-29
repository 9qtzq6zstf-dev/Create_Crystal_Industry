package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;

/**
 * 掉落物"直接入库存"：物品实体即将进入世界时，若它落在某台吸入中的吸尘器风场内，
 * 就跳掉实体这一步，直接把物品收进那台吸尘器的库存（{@link MechanicalCleanerBlockEntity#tryAbsorbDrop}）。
 * <p>
 * 为什么值得做：风场里的掉落物本来就在同一个 tick 被 {@code collectItemsInFlow} 收走，
 * 实体只活了一瞬间，却要走完一整套实体流程（登记 section、同步给附近客户端、每 tick 参与碰撞与吸取扫描）。
 * 母岩/晶簇这类产线一次破坏就掉一份，省下来的都是纯开销。
 * <p>
 * 为什么钩在 {@link EntityJoinLevelEvent} 而不是掉落事件：掉落物的来源太多
 * （原版掉落表走 {@code BlockDropsEvent}、智能钻头自己 popResource、脚本方块走
 * {@link com.minecart.yunxian.registry.ScriptedBlockDrops}、别的模组的机器各写各的），
 * 但终点都是"一个 ItemEntity 进入世界"，钩在这里一次覆盖全部，且不用改动任何一条掉落路径。
 * <p>
 * <b>反向查风场</b>：风场边界（{@code airCurrent.bounds}）是一条从吸尘器出发、沿朝向的直线
 * （见 {@code rebuildAirBounds}），所以掉落物沿六个轴向任意方向最多 20 格（{@link MechanicalCleanerBlockEntity#SUCK_RANGE_MAX}）
 * 必定能碰到那台吸尘器所在的方块。逐格查方块状态，只有命中吸尘器才取方块实体，
 * 最坏 6 × 20 次调色板查询、命中即返回。
 * <p>
 * 已知不覆盖（两种都保持原样：照常生成实体，随后由吸尘器收走，只是没省下这个实体）：
 * <ul>
 *   <li>喷嘴（分散网）把风场变成以自身为中心的球体，球内但不在轴线上的掉落物查不到；</li>
 *   <li>没有动力的吸尘器没有风场边界，此时靠 {@code collectItemsPassive} 按距离被动吸取，
 *       那部分掉落物照常生成。</li>
 * </ul>
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
        if (!(event.getEntity() instanceof ItemEntity itemEntity)) {
            return;
        }
        if (!ModConfig.Common.CLEANER_DIRECT_ABSORB.get()) {
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
     * 从掉落物所在位置沿六个轴向找风场，找到第一台能收下它的吸尘器就直接收走。
     * 任一轴向上遇到未加载的区块就停止该方向——风场不会跨过未加载的区块。
     */
    private static boolean absorbIntoSuction(ServerLevel level, Vec3 point, ItemStack stack) {
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
                if (level.getBlockEntity(cursor) instanceof MechanicalCleanerBlockEntity cleaner
                        && cleaner.tryAbsorbDrop(point, stack)) {
                    return true;
                }
            }
        }
        return false;
    }
}
