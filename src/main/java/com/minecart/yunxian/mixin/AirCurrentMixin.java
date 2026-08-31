package com.minecart.yunxian.mixin;

import java.util.List;

import com.minecart.yunxian.FanImmunityHelper;
import com.simibubi.create.content.kinetics.fan.AirCurrent;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AirCurrent.class)
public abstract class AirCurrentMixin {

    @Shadow
    protected List<Entity> caughtEntities;

    @Inject(method = "tickAffectedEntities", at = @At("HEAD"))
    private void yunxian$removeFanImmunePlayers(Level world, CallbackInfo ci) {
        int before = caughtEntities.size();
        caughtEntities.removeIf(FanImmunityHelper::isImmune);
        // 临时调试: 确认 Mixin 是否真的触发（生效后删掉这两行）
        if (caughtEntities.size() != before)
            System.out.println("[FanImmunity] AirCurrent 移除了 "
                    + (before - caughtEntities.size()) + " 个免疫实体");
    }
}