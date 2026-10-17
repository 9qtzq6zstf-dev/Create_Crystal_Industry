package com.minecart.yunxian.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

/**
 * 喷嘴（分散网）场的登记表：把"前方挂着喷嘴、并且正在吸入"的吸尘器记下来，
 * 供 {@link CleanerDropAbsorption} 在物品实体入场时反查——而不是去世界里扫一颗立方体。
 * <p>
 * <b>为什么需要这张表</b>：喷嘴把机器的一小段气流铺成一颗以自己为中心的立方体场，
 * 场里的落点不在任何一条过吸尘器的轴线上，沿轴逐格找（{@link CleanerDropAbsorption#absorbByAxis}）
 * 碰不到那台机器。早先的做法是以落点为中心扫半径 20 的立方体找喷嘴，即 41³ = 68,921 格；
 * 但那个事件对<b>世界里每一个</b>物品实体入场都会跑，而轴线那条路对绝大多数落点都是落空的
 * （矿洞里挖掉的方块、怪物掉落、离吸尘器 20 格以外的任何物品），等于是每次入场都付 6.9 万格，
 * 结论还多半是"附近根本没有喷嘴"。
 * <p>
 * 登记表把这份代价挪到了机器那一侧：吸尘器本来每 tick 就要算一次自己的喷嘴覆盖
 * （{@link MechanicalCleanerBlockEntity#collectItemsFromNozzle}），顺手登记一次是零成本的。
 * <p>
 * <b>只登记"真的挂着喷嘴且在吸入"的机器</b>，所以绝大多数存档里这张表是<b>空的</b>——
 * 没装分散网就一台都不会登记，反查时一次 {@code isEmpty()} 就返回。
 * 而"收不收得下"（满仓停摆、红石锁、过滤、加工催化剂）一概不在这里判，留给
 * {@link MechanicalCleanerBlockEntity#tryAbsorbDrop}，这样登记表不会随库存和红石状态抖动。
 * <p>
 * 生命周期：机器自己 tick 时登记、气流重建时对账（每 {@code fanBlockCheckRate} 30 tick 一次）；
 * 机器被拆掉或所在区块卸载后残留的项，由反查那一侧顺手剔掉——Create 的 {@code SmartBlockEntity}
 * 把 {@code setRemoved()} 定成了 final，拿不到那个钩子，所以这里走"懒清理"而不是"注销"。
 * {@link #of(Level)} 拿到的就是可改的活表，全表只有"机器登记"与"反查清理"两处写。
 */
public final class NozzleFieldIndex {

    /**
     * 维度 → （吸尘器位置 → 它正前方的喷嘴位置）。
     * <p>
     * 按维度分开是因为不同世界的同一坐标毫无关系；用 {@link Level#dimension()} 而不是
     * {@code ServerLevel} 实例做键，存档重进后旧项会落在同一个桶里，但反查时的"区块没加载"与
     * {@code tryAbsorbDrop} 的复判都会把它们筛掉，不会误收。
     */
    private static final Map<ResourceKey<Level>, Map<BlockPos, BlockPos>> FIELDS = new HashMap<>();

    private NozzleFieldIndex() {
    }

    /**
     * 登记一台"前方有喷嘴、正在吸入"的吸尘器。重复调用是幂等的，可以每 tick 调。
     * 客户端侧直接忽略——这张表只服务服务端的掉落物接管。
     */
    public static void add(Level level, BlockPos cleanerPos, BlockPos nozzlePos) {
        if (level == null || level.isClientSide) {
            return;
        }
        of(level).put(cleanerPos, nozzlePos);
    }

    /** 注销一台吸尘器（被拆掉、或前方喷嘴没了 / 切成吹出模式） */
    public static void remove(Level level, BlockPos cleanerPos) {
        if (level == null || level.isClientSide) {
            return;
        }
        Map<BlockPos, BlockPos> fields = FIELDS.get(level.dimension());
        if (fields != null) {
            fields.remove(cleanerPos);
        }
    }

    /**
     * 该维度当前的登记表：<b>活表</b>，反查时可以顺手剔掉失效项。绝不返回 {@code null}。
     * <p>
     * 返回活表而不是快照，是为了让"区块卸载后的残留项"有地方清——否则这张表会随玩家走过的地方
     * 只长不缩。调用方只需要 iterate + {@code Iterator#remove}，不必自己维护生命周期。
     */
    public static Map<BlockPos, BlockPos> of(Level level) {
        return FIELDS.computeIfAbsent(level.dimension(), key -> new HashMap<>(4));
    }
}
