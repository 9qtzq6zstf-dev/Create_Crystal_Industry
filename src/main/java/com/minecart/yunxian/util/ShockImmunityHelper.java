package com.minecart.yunxian.util;

import com.minecart.yunxian.registry.ModTags;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/**
 * 「全套带电击免疫的盔甲」的判定，判定条件是 {@link ModTags#SHOCK_IMMUNE}。
 * <p>
 * 与 {@link FanImmunityHelper} 只差一个「几件才算数」：那个是<b>任意一件</b>即免疫（鼓风机不能把你吹走
 * 也说得过去），这个是<b>四个盔甲槽全部</b>带标签才算——电击免疫是整身的事，指望一顶头盔挡住雷击不像话。
 * 所以两个判定各写一份，没有共用。
 * <p>
 * 判定看得见的只有「槽位 + 标签」：<b>不要求四件同材质</b>。锁链头盔配下界合金胸甲一样算全套，
 * 因为标签是同一张，规则也只有一条。机械动力那套本来就靠原版下界合金护腿补位（见 ModTags 里的注释），
 * 更是必须允许混搭。
 * <p>
 * 收 {@link LivingEntity} 而不是 {@code Player}：判定本身对生物一视同仁（给僵尸穿全套锁链甲也该免疫），
 * 而且两个调用点（{@code effect/ElectrifiedAura}、{@code effect/ShockWard}）本来就是按生物处理的。
 */
public final class ShockImmunityHelper {

    private ShockImmunityHelper() {
    }

    /** 四个盔甲槽（头 / 胸 / 腿 / 脚）全部是带 shock_immune 标签的物品 */
    public static boolean isWearingFullSet(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue; // 主手 / 副手 / 身上 / 鞍：都不是盔甲槽
            }
            if (!entity.getItemBySlot(slot).is(ModTags.SHOCK_IMMUNE)) {
                return false;
            }
        }
        return true;
    }
}
