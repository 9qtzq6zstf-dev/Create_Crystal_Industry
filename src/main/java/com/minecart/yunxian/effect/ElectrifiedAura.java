package com.minecart.yunxian.effect;

import com.minecart.yunxian.registry.ModEffects;
import com.minecart.yunxian.util.ShockImmunityHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * 「感电」的施加规则：生物待在**弧光石系列方块**（母岩 + 三级芽 + 晶簇）或**电流浆**附近即获得，
 * 离开约 3 秒后自然消退（效果本身做什么见 {@link ElectrifiedZap}）。
 * <p>
 * 判定放在<b>实体 tick 侧</b>而不是方块侧：方块没有每 tick 的钩子（随机刻太稀疏，撑不起"待着就有"的手感），
 * 而生物数量有限、扫描半径又小，代价可控。两处细节都是为了便宜：
 * <ul>
 *   <li>按实体错开（{@code tickCount + id}），每个生物每 {@link #CHECK_INTERVAL} tick 才扫一次；</li>
 *   <li>扫到的每个位置交给 {@link ArclightSource#isSource}（内部先 {@code isAir} 短路，
 *       再拿方块对象查集合），与客户端冒火花的判定共用同一份逻辑。</li>
 * </ul>
 * <p>
 * <b>全套 {@code shock_immune} 盔甲的生物完全不获得</b>（判定见 {@link ShockImmunityHelper}），
 * 已经带在身上的也会被摘掉。判定这一份就够：{@link ArclightSource#isSource} 涵盖母岩 + 三级芽 +
 * 晶簇 + 电流浆，所以免疫不必改判定源，在这里拦一道即可。
 */
public final class ElectrifiedAura {

    /** 判定半径（格），以生物所在格为中心扫 (2R+1)³ 的立方体 */
    private static final int RADIUS = 3;

    /** 每个生物每隔这么多 tick 检查一次（按实体错开，不是所有生物挤在同一 tick） */
    private static final int CHECK_INTERVAL = 10;

    /** 每次施加的时长：比检查间隔长得多，所以"待着不走"时效果不会一闪一闪 */
    private static final int DURATION = 60;

    private ElectrifiedAura() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        if (!(living.level() instanceof ServerLevel level)) {
            return; // 只在服务端施加
        }
        if ((living.tickCount + living.getId()) % CHECK_INTERVAL != 0) {
            return;
        }
        if (ShockImmunityHelper.isWearingFullSet(living)) {
            // 全套 shock_immune 盔甲：既然不该获得，身上已有的也顺手摘掉——
            // 否则穿着甲走进场时残留的那几秒会让人觉得"免疫没生效"。
            // 放在间歇闸门之后：那 10 tick 的延迟无所谓，而这个判定不必每 tick 白跑。
            if (living.hasEffect(ModEffects.ELECTRIFIED)) {
                living.removeEffect(ModEffects.ELECTRIFIED);
            }
            return;
        }
        if (nearSource(level, living.blockPosition())) {
            // ambient=true 让粒子更淡；visible=true 才有粒子提示
            living.addEffect(new MobEffectInstance(ModEffects.ELECTRIFIED, DURATION, 0, true, true));
        }
    }

    private static boolean nearSource(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dy = -RADIUS; dy <= RADIUS; dy++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    if (ArclightSource.isSource(level, cursor.setWithOffset(center, dx, dy, dz))) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
