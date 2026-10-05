package com.minecart.yunxian.particle;

import com.minecart.yunxian.registry.ModParticles;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * 「灵魂火气流」粒子的数据：一颗要往哪儿飞。
 * <p>
 * <b>为什么要自带速度</b>：原版把粒子从服务端广播出去只有
 * {@code ServerLevel#sendParticles} 一条路，而它的 {@code xDist/yDist/zDist/speed} 四个参数给的是
 * 「以落点为中心的高斯散布」，<b>速度是对称随机的、没有方向</b>——半颗往前半颗往后，凑不成一束。
 * 想让粒子真的朝一个方向流动，就只能把方向塞进粒子自己的数据里，由客户端那半
 * （{@code SoulFlowParticle}）读出来当速度用。
 * <p>
 * 落点仍然由 {@code sendParticles} 的坐标给：同一份数据（同方向）配不同落点，
 * 连起来就是一条流动的束。
 */
public record SoulFlowParticleData(double dx, double dy, double dz) implements ParticleOptions {

    public static final MapCodec<SoulFlowParticleData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.fieldOf("dx").forGetter(SoulFlowParticleData::dx),
            Codec.DOUBLE.fieldOf("dy").forGetter(SoulFlowParticleData::dy),
            Codec.DOUBLE.fieldOf("dz").forGetter(SoulFlowParticleData::dz)
    ).apply(instance, SoulFlowParticleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, SoulFlowParticleData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, SoulFlowParticleData::dx,
                    ByteBufCodecs.DOUBLE, SoulFlowParticleData::dy,
                    ByteBufCodecs.DOUBLE, SoulFlowParticleData::dz,
                    SoulFlowParticleData::new);

    /** 按方向向量造一份数据（速度大小由向量长度定） */
    public static SoulFlowParticleData along(Vec3 velocity) {
        return new SoulFlowParticleData(velocity.x, velocity.y, velocity.z);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.SOUL_FLOW.get();
    }
}
