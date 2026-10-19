package com.minecart.yunxian.util;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.windcharge.AbstractWindCharge;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.level.ExplosionKnockbackEvent;

/**
 * 「风弹不许把我吹动」这一层的免疫，判定沿用 {@link FanImmunityHelper}（{@code fan_immune} 标签
 * 与「气流抵抗」附魔都算）。
 * <p>
 * <b>为什么挂爆炸事件而不是风弹自身</b>：风弹的击退根本不由风弹施加——{@code WindCharge#explode}
 * 只是开一次爆炸（{@code Level#explode}，半径 1.2，击退倍率 1.22 写在它的伤害计算器里），
 * 真正把玩家推走的是 {@code Explosion} 算完每个实体的击退速度后那一句
 * {@code entity.setDeltaMovement(entity.getDeltaMovement().add(vec31))}。
 * NeoForge 在那句之前留了 {@link ExplosionKnockbackEvent}，把速度改成零就等于没被推——
 * 顺带把「风弹直击命中的 1 点伤害」留着（那是 {@code onHitEntity} 里的 {@code hurt}，与击退无关）。
 * <p>
 * 只认 {@link AbstractWindCharge} 的爆炸：TNT、苦力怕、末地水晶这些照旧推人。
 */
public final class WindKnockbackGuard {

    private WindKnockbackGuard() {
    }

    public static void onExplosionKnockback(ExplosionKnockbackEvent event) {
        if (!(event.getExplosion().getDirectSourceEntity() instanceof AbstractWindCharge)) {
            return;
        }
        Entity entity = event.getAffectedEntity();
        if (FanImmunityHelper.isImmune(entity)) {
            event.setKnockbackVelocity(Vec3.ZERO);
        }
    }
}
