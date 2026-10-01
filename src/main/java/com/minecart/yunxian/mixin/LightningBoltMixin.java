package com.minecart.yunxian.mixin;

import com.minecart.yunxian.budding.LightningActivation;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LightningBolt;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让闪电能激活失活弧光石母岩（判定在 {@link LightningActivation}）。
 * <p>
 * <b>为什么非走 mixin 不可</b>：原版对"闪电打到方块"只开了一个口子——
 * {@code LightningBolt#powerLightningRod} 里 {@code blockstate.getBlock() instanceof LightningRodBlock}
 * 才调 {@code onLightningStrike}，普通方块完全不参与；NeoForge 也只补了实体侧的
 * {@code EntityStruckByLightningEvent}，没有任何方块层事件。所以想拿到"闪电落在某格"这个时刻，
 * 只能注入原版。
 * <p>
 * <b>注入点选 {@code powerLightningRod} 的 HEAD</b>：它是闪电在服务端的唯一结算点，
 * 由 {@code tick()} 里 {@code life == 2} 且非 visualOnly 的分支无条件调用（避雷针的判定在它内部），
 * 所以一次注入同时覆盖"劈在方块上"和"劈在避雷针上"两种情况（见 {@link LightningActivation} 的类注释）。
 * 挂在 <b>HEAD</b> 而不是 {@code tick()} 里的调用处：HEAD 的语义是"落点已经定了、原版还没动手"，
 * 位置稳定，也不会和后面 {@code clearCopperOnLightningStrike} 那些方块操作抢顺序。
 * <p>
 * 本类登记在 {@code create_crystal_industry.mixins.json} 的 {@code "mixins"} 段（不是 {@code "client"}）：
 * 判定必须在服务端跑，否则玩家那边永远看不到转化。
 */
@Mixin(LightningBolt.class)
public abstract class LightningBoltMixin {

    /**
     * 原版 {@code LightningBolt#getStrikePosition()} 是私有的，这里借 {@code @Invoker} 直接要过来，
     * 免得把 {@code BlockPos.containing(x, y - 1e-6, z)} 那段实现细节抄成第二份。
     */
    @Invoker("getStrikePosition")
    protected abstract BlockPos yunxian$getStrikePosition();

    @Inject(method = "powerLightningRod", at = @At("HEAD"))
    private void yunxian$activateInactiveBudding(CallbackInfo ci) {
        LightningActivation.onStrikeResolved(
                ((LightningBolt) (Object) this).level(), this.yunxian$getStrikePosition());
    }
}
