package com.minecart.yunxian.mixin;

import java.util.List;

import com.minecart.yunxian.FanImmunityHelper;
import com.simibubi.create.content.kinetics.fan.NozzleBlockEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NozzleBlockEntity.class)
public abstract class NozzleBlockEntityMixin {

    @Shadow
    private List<Entity> pushingEntities;

    // ① 兜底：即使免疫玩家进了列表，也不被 setDeltaMovement 施力
    @Redirect(method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
    private void yunxian$dontPushImmunePlayers(Entity entity, Vec3 motion) {
        if (!FanImmunityHelper.isImmune(entity))
            entity.setDeltaMovement(motion);
    }

    // ② 关键：lazyTick 每 5 tick 会把范围内实体 add 回 pushingEntities，
    //    在这里拦掉，免疫玩家永远进不了列表 → fallDistance=0 / hurtMarked=true 也不会执行
    @Redirect(method = "lazyTick",
            at = @At(value = "INVOKE",
                    target = "Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean yunxian$dontAddImmuneToPushing(List<Entity> list, Object element) {
        if (element instanceof Entity entity && FanImmunityHelper.isImmune(entity))
            return false;
        return list.add((Entity) element);
    }

    // ③ 清理历史残留：如果玩家穿鞋前已经在 pushingEntities 里，先清一次（之后不会再被加回）
    @Inject(method = "tick", at = @At("HEAD"))
    private void yunxian$removeImmunePlayers(CallbackInfo ci) {
        pushingEntities.removeIf(FanImmunityHelper::isImmune);
    }
}