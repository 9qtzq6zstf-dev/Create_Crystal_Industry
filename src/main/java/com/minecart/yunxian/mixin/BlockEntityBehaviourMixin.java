package com.minecart.yunxian.mixin;

import com.minecart.yunxian.client.ResonanceFilterHolderTracker;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 让每个过滤行为每 tick 自报一次家门，交给客户端的
 * {@link ResonanceFilterHolderTracker} —— 用来描出"哪些元件上插着本网络的共振过滤器"。
 * <p>
 * <b>为什么注入在 {@code BlockEntityBehaviour} 而不是 {@code FilteringBehaviour}：</b>
 * {@code FilteringBehaviour} 自己<b>没有</b>声明 {@code tick()}（它继承基类那个），
 * 注入一个没在自己类里声明的方法会直接失败，所以只能挂在基类上、再用 {@code instanceof} 收窄。
 * 基类的 {@code tick()} 每 tick 会被每个行为调一次，这里只多一次 instanceof，判定不过立刻返回。
 * <p>
 * <b>只放 mixins.json 的 {@code "client"} 段</b>：跟踪表与描边都是纯客户端展示，
 * 专用服务端上这个注入根本不该存在（它引用的 {@code ResonanceFilterHolderTracker} 也是客户端类）。
 */
@Mixin(BlockEntityBehaviour.class)
public abstract class BlockEntityBehaviourMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void yunxian$trackResonanceFilterHolder(CallbackInfo ci) {
        if ((Object) this instanceof FilteringBehaviour filtering)
            ResonanceFilterHolderTracker.track(filtering);
    }
}
