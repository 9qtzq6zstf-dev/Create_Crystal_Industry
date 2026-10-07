package com.minecart.yunxian.effect;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「冰霜行者」：喝下可燃冰沙瓶或可燃冰圣代之后，脚下这片水面会被踩成冰。
 * <p>
 * 这里复刻的是原版<b>冰霜行者附魔</b>的行为，但本模组刻意不走附魔那条路：
 * 附魔只能挂在靴子上、还得占一个附魔位（与原版互斥组冲突），而这两件东西要的是
 * 「喝下去这一段时间里能过水」，喝完就没了——状态效果才是对的载体。
 * <p>
 * 1.21 起原版那套已经整份搬进了数据包：{@code data/minecraft/enchantment/frost_walker.json}
 * 里写的是 {@code location_changed} 触发器 + {@code replace_disk} 效果。本类逐条对齐它：
 * <ul>
 *   <li><b>半径</b>：{@code 3 + 等级}，钳在 16 以内（原版 {@code clamped{min:0,max:16}} 包着
 *       {@code linear{base:3, per_level_above_first:1}}，等级 1 即半径 3）；</li>
 *   <li><b>圆心</b>：{@code offset [0,-1,0]}，即<b>脚下那一格</b>。冰是结在「与脚同高的一圈水面」上，
 *       不是结在脚下——站在冰上时脚底那格已经是冰了，往前铺的才是真正要冻的水；</li>
 *   <li><b>条件</b>：该格<b>上方是空气</b>、本身是水、且无遮挡。少了「上方空气」这一条，
 *       冰会长在水下、把整片水域从下往上冻死；</li>
 *   <li><b>只在着地时生效</b>（原版那个 {@code is_on_ground} 条件）：泡在水里游是没有冰的，
 *       这也正好构成那条自洽的循环——半径 3 格先替你在前方结冰，你踩上冰算着地，冰再往前铺。</li>
 * </ul>
 * 与原版的一处<b>刻意差别</b>：只冻<b>水源</b>（{@code getFluidState().isSource()}）。原版数据包那个
 * {@code matching_fluids: water} 连流动的水一起认，那会把一条水渠当场掐断；玩家要过河，不是要断河。
 * <p>
 * 图标文件是 {@code mob_effect/frost_walker.png}（当前是 frozen.png 的占位副本），改 id 时记得连它一起改，
 * 否则 HUD 上会变成紫黑块。
 */
public class FrostWalkerEffect extends MobEffect {

    /** 粒子颜色：冰蓝，比方块那半边「冰封」的霜白要蓝一点，好区分 */
    public static final int COLOR = 0x8FE4FF;

    /** 冻结半径基础值：与附魔等级 1 的冰霜行者一致 */
    private static final int BASE_RADIUS = 3;

    /** 半径上限：原版 replace_disk 把半径钳在 16 */
    private static final int MAX_RADIUS = 16;

    /** 结出来的冰：原版给的是 age 0 的霜冰，defaultBlockState 正好是它 */
    private static final BlockState FROSTED_ICE = Blocks.FROSTED_ICE.defaultBlockState();

    public FrostWalkerEffect() {
        super(MobEffectCategory.BENEFICIAL, COLOR, ParticleTypes.SNOWFLAKE);
    }

    /**
     * 必须每 tick 都响。
     * <p>
     * 1.21 的默认实现是 {@code return false}（每 tick 只判定一次、不调用 {@code applyEffectTick}）。
     * 原版那条规则是「移动即触发」，靠的是位置变化事件；效果这边没有那类事件，只能每 tick 主动扫一圈，
     * 否则走两步才结一格、根本铺不成路。
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // 只在服务端真正改方块；着地条件与排水那条理由见类注释
        if (entity.level() instanceof ServerLevel level && entity.onGround()) {
            freezeNearby(level, entity, amplifier);
        }
        return true;
    }

    /** 扫脚下一圈的方，把水面的水换成霜冰 */
    private static void freezeNearby(ServerLevel level, LivingEntity entity, int amplifier) {
        int radius = Math.min(MAX_RADIUS, BASE_RADIUS + amplifier);
        BlockPos center = entity.blockPosition().below();
        BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();

        // betweenClosed 复用同一个可变坐标，所以每一圈都得当场用掉，不能存起来
        for (BlockPos pos : BlockPos.betweenClosed(
                center.offset(-radius, 0, -radius), center.offset(radius, 0, radius))) {
            above.set(pos.getX(), pos.getY() + 1, pos.getZ());
            if (!level.getBlockState(above).isAir()) {
                continue;   // 水面之下 / 被方块盖住：结冰会把整片水从下往上冻住
            }
            BlockState state = level.getBlockState(pos);
            if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()) {
                continue;   // 只冻水源，流动的水不动（理由见类注释）
            }
            level.setBlockAndUpdate(pos, FROSTED_ICE);
        }
    }
}
