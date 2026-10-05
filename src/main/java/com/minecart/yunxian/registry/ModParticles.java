package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.particle.FlameFlowParticleType;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的粒子类型。目前只有一个：{@code flame_flow}，火焰吐息里那股从嘴边喷出去的火与气。
 * <p>
 * <b>为什么非要自己注册一个</b>：Create 鼓风机气流的 {@code AirFlowParticle} 抄不了——它的工厂会拿落点去找
 * {@code IAirCurrentSource} 方块实体，找不到就把自己移除，所以它离了真正的鼓风机存在不了。
 * 而原版又没有任何「能带方向地广播」的粒子通道（见 {@link com.minecart.yunxian.particle.FlameFlowParticleData}
 * 的类注释），于是只能自己造一个。
 * <p>
 * 贴图不另找素材：{@code particles/flame_flow.json} 直接引用<b>原版</b>的 {@code minecraft:generic_0..7}——
 * 那正是 Create 自己 {@code air_flow.json} 用的同一组 sprite，观感天然一致。
 * 客户端那半（粒子本体与它的注册）在 {@code client/particle} 下。
 */
public final class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, Yunxian.MODID);

    public static final DeferredHolder<ParticleType<?>, FlameFlowParticleType> FLAME_FLOW =
            PARTICLE_TYPES.register("flame_flow", FlameFlowParticleType::new);

    private ModParticles() {
    }

    public static void register(IEventBus modEventBus) {
        PARTICLE_TYPES.register(modEventBus);
    }
}
