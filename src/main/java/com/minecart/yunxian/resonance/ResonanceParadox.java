package com.minecart.yunxian.resonance;

import java.util.UUID;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.item.ResonanceFilterItem;
import com.minecart.yunxian.registry.ModDataComponents;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 彩蛋：共振过滤器被放回<b>它自己所在的网络</b>时的「因果律溶解」。
 * <p>
 * 这种摆法在判定那边是个无限自指（台面上的过滤器去读网络，网络又读到它自己），
 * {@code ResonanceFilterItemStack} 会用重入护栏把它判成"读不到"——过滤器静默失效。
 * 这里给那个沉默的失败一个说法：设个自指，过滤器当场把自己炸掉。
 * <p>
 * <b>为什么由台子每 tick 主动查、而不是在拦截那一刻顺手做</b>：
 * <ul>
 *   <li>拦截发生在判定的深调用栈里（漏斗、溜槽、工作盆都在问），那个位置既不该改世界、
 *       也拿不到"这格台子在哪"；而台子自己每 tick 都看得到台面，天然是这事的负责方；</li>
 *   <li>自指是<b>摆法本身</b>就成立的，不取决于有没有机器来问。等机器来问才炸的话，
 *       一个没人用的网络里摆着死循环会一直安安静静，那就不像"当场炸"了。</li>
 * </ul>
 * 判定那边的护栏照旧留着：这一 tick 之内东西还在台面上，机器仍可能问到它；
 * 而且两个网络互相指向（A→B→A）的环压根不经过这里 —— 那不是自指，是两台子对读，
 * 仍旧由护栏静默挡下。
 */
public final class ResonanceParadox {

    /**
     * 击退半径。取 4 是照 TNT 来的：站在旁边的人会被推开，但按下面的算法掉不了血。
     */
    private static final float RADIUS = 4.0F;

    /**
     * 只有击退的爆炸。
     * <p>
     * 原版爆炸对实体是「先 {@code hurt} 再推」两件事，这里把伤害那一问答成 false，
     * 推力就单独留下来了。方块那边不用管：调用处传的是
     * {@link Level.ExplosionInteraction#NONE}，本来就不碰方块。
     */
    private static final ExplosionDamageCalculator KNOCKBACK_ONLY = new ExplosionDamageCalculator() {

        @Override
        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
            return false;
        }
    };

    /**
     * 台面上那个过滤器是不是「接回了自己所在的网络」——也就是会无限自指的那一种。
     * <p>
     * 只看这一个条件，不看它是怎么到台面上的（玩家放的、机械臂放的、传送带送的都算）。
     * 没接入任何网络的共振过滤器不算：它读不出东西，但也不会绕回自己。
     */
    public static boolean matches(ItemStack onTable, UUID tableNetwork) {
        return onTable.getItem() instanceof ResonanceFilterItem
                && tableNetwork.equals(onTable.get(ModDataComponents.RESONANCE_NETWORK.get()));
    }

    /**
     * 自指的那一刻：在台子上方炸一下，并把隐藏成就发给附近的玩家。
     * <p>
     * 用的还是原版那套爆炸（{@code level.explode}），所以音效、粒子、脚下的震动、
     * 以及推力的距离衰减都是现成的 —— 区别只在它不伤方块也不伤人。
     * <p>
     * <b>爆心在台子正上方一格，不在台子内部。</b> 原版算推力要乘一个
     * {@code Explosion.getSeenPercent}（从爆心朝实体眼睛打一条射线，看有多少比例没被方块挡住），
     * 爆心埋在台子方块里的话，连站在旁边的人都被台子本体挡掉，推力直接归零 ——
     * 炸了个响，人却纹丝不动。
     */
    public static void detonate(ServerLevel level, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos.above());

        // 传 source = null：没有"谁干的"。伤害源只为接口完整而给（下面的计算器把它挡掉了）。
        level.explode(null, Explosion.getDefaultDamageSource(level, null), KNOCKBACK_ONLY,
                center.x(), center.y(), center.z(), RADIUS, false, Level.ExplosionInteraction.NONE);

        // 成就按台子的位置发，和爆心偏移无关
        YunxianAdvancements.awardNear(level, pos, YunxianAdvancements.RESONANCE_PARADOX);
    }

    private ResonanceParadox() {
    }
}
