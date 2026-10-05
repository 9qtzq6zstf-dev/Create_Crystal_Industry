package com.minecart.yunxian.particle;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/**
 * 「灵魂火气流」的粒子类型。原版把这件事拆成两个方法：{@link #codec()}（数据包/存档）与
 * {@link #streamCodec()}（网络），两个都转交给 {@link SoulFlowParticleData} 里的常量。
 * <p>
 * 没有像原版 {@code SimpleParticleType} 那样用 {@code StreamCodec.unit}——它那个是「不带数据的粒子」
 * 专用，而这里正是要带数据（方向）才存在的。
 */
public class SoulFlowParticleType extends ParticleType<SoulFlowParticleData> {

    public SoulFlowParticleType() {
        // 别绕过原版的粒子数量上限：喷火是常规玩法，不该享有「爆炸」那种无视上限的特权
        super(false);
    }

    @Override
    public MapCodec<SoulFlowParticleData> codec() {
        return SoulFlowParticleData.CODEC;
    }

    @Override
    public StreamCodec<? super RegistryFriendlyByteBuf, SoulFlowParticleData> streamCodec() {
        return SoulFlowParticleData.STREAM_CODEC;
    }
}
