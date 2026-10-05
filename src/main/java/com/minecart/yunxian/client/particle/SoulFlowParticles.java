package com.minecart.yunxian.client.particle;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.registry.ModParticles;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * 给 {@code soul_flow} 挂上客户端那半——粒子类型本身在注册表里只是个 id，
 * 谁来画、画成什么样靠这里登记。走 {@code registerSpriteSet} 而不是 {@code registerSpecial}：
 * 粒子要用 {@code particles/soul_flow.json} 里那组 sprite（8 帧），得让游戏把它切好递进来。
 * <p>
 * 整个类只在客户端加载（{@code value = Dist.CLIENT}），专用服务端不会碰到
 * {@code ParticleEngine} 这些客户端类。
 */
@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class SoulFlowParticles {

    private SoulFlowParticles() {
    }

    @SubscribeEvent
    public static void registerProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SOUL_FLOW.get(), SoulFlowParticle.Provider::new);
    }
}
