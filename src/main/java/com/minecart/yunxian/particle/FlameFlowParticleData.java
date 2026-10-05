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
 * 「火焰气流」粒子的数据：这颗要往哪儿飞、什么颜色。
 * <p>
 * <b>为什么要自带速度</b>：原版把粒子从服务端广播出去只有
 * {@code ServerLevel#sendParticles} 一条路，而它的 {@code xDist/yDist/zDist/speed} 四个参数给的是
 * 「以落点为中心的高斯散布」，<b>速度是对称随机的、没有方向</b>——半颗往前半颗往后，凑不出往前喷的火。
 * 想让粒子真的从一点朝一个方向飞出去，就只能把速度塞进粒子自己的数据里，
 * 由客户端那半（{@code FlameFlowParticle}）读出来当初始速度。
 * <p>
 * <b>颜色也一起带</b>：同一颗粒子靠不同的 {@code argb} 分别充当橙色的气流、亮黄的火焰、暗灰的烟，
 * 于是三个角色共用一套贴图、一个类型、一个 provider。
 * 高位那个字节是不透明度（{@code >> 24}），低三位是 RGB——揉进一个 int 是因为
 * {@link StreamCodec#composite} 的参数对数是有限的，能省一个字段就省一个。
 */
public record FlameFlowParticleData(double dx, double dy, double dz, int argb) implements ParticleOptions {

    public static final MapCodec<FlameFlowParticleData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.DOUBLE.fieldOf("dx").forGetter(FlameFlowParticleData::dx),
            Codec.DOUBLE.fieldOf("dy").forGetter(FlameFlowParticleData::dy),
            Codec.DOUBLE.fieldOf("dz").forGetter(FlameFlowParticleData::dz),
            Codec.INT.fieldOf("argb").forGetter(FlameFlowParticleData::argb)
    ).apply(instance, FlameFlowParticleData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, FlameFlowParticleData> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.DOUBLE, FlameFlowParticleData::dx,
                    ByteBufCodecs.DOUBLE, FlameFlowParticleData::dy,
                    ByteBufCodecs.DOUBLE, FlameFlowParticleData::dz,
                    ByteBufCodecs.VAR_INT, FlameFlowParticleData::argb,
                    FlameFlowParticleData::new);

    /** 按速度向量与颜色造一份数据 */
    public static FlameFlowParticleData of(Vec3 velocity, int argb) {
        return new FlameFlowParticleData(velocity.x, velocity.y, velocity.z, argb);
    }

    @Override
    public ParticleType<?> getType() {
        return ModParticles.FLAME_FLOW.get();
    }
}
