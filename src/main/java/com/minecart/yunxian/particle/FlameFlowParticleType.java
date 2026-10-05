package com.minecart.yunxian.particle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 「火焰气流」的粒子类型。原版把这件事拆成两个方法：{@link #codec()}（数据包/存档）与
 * {@link #streamCodec()}（网络），两个都转交给 {@link FlameFlowParticleData} 里的常量。
 * <p>
 * 没用原版 {@code SimpleParticleType} 那样的一行 {@code StreamCodec.unit}——那种只适合「不带数据的粒子」，
 * 而这一颗存在的理由正是要带数据（速度与颜色）。
 */
public class FlameFlowParticleType extends ParticleType<FlameFlowParticleData> {

    public FlameFlowParticleType() {
        // 不绕过原版的粒子数量上限：喷火是常规玩法，不该享有「爆炸」那种无视上限的特权
        super(false);
    }

    @Override
    public MapCodec<FlameFlowParticleData> codec() {
        return FlameFlowParticleData.CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, FlameFlowParticleData> streamCodec() {
        return FlameFlowParticleData.STREAM_CODEC;
    }
}
