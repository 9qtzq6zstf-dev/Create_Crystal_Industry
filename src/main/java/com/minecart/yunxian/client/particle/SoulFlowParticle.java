package com.minecart.yunxian.client.particle;

import com.minecart.yunxian.particle.SoulFlowParticleData;

import net.createmod.catnip.theme.Color;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 灵魂火喷流里那道向前流动的青灰气流——Create 鼓风机缠魂气流那身外观的复刻版。
 * <p>
 * <b>抄的是外观，不是代码</b>：Create 的 {@code AirFlowParticle} 用不了（它靠
 * {@code IAirCurrentSource} 方块实体活着，见 {@code ModParticles} 的类注释），所以这边重写了一份
 * 只有它那几处「看得见」的设定：
 * <ul>
 *   <li>贴图是 {@code minecraft:generic_0..7}——和 Create 的 {@code air_flow.json} 同一组；</li>
 *   <li>颜色照抄 {@code HauntingType#morphAirFlow} 的 {@code Color.mixColors(0x0, 0x126568, random)}：
 *       每颗在「近黑 → 青灰」之间随机取一档，一片粒子叠起来才有深浅；</li>
 *   <li>不透明度拉满（原版气流是半透明的白雾，缠魂那档是 α=1 的实色）；</li>
 *   <li>{@code quadSize} 缩到 0.75、{@code hasPhysics} 关掉、寿命 40 —— 都是它的原设定。</li>
 * </ul>
 * <b>区别在动力来源</b>：Create 那颗粒子每 tick 去问风道「我该往哪飘」，这边直接把方向写在
 * {@link SoulFlowParticleData} 里（服务端广播时随包一起过来），于是离了鼓风机也能飞。
 * 速度沿用它的手感（约 1/8 格每 tick，再乘衰减），配合 {@code SimpleAnimatedParticle} 后半程的
 * 淡出，看起来就是一股往前飘散的气。
 */
@OnlyIn(Dist.CLIENT)
public class SoulFlowParticle extends SimpleAnimatedParticle {

    /** 寿命（tick）：与 Create 的 AirFlowParticle 一致 */
    private static final int LIFETIME = 40;

    protected SoulFlowParticle(ClientLevel level, double x, double y, double z,
                               double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z, sprites, 0.0F);
        this.lifetime = LIFETIME;
        // 关掉方块碰撞：这是股气，不该被墙挡住后停在原地
        this.hasPhysics = false;
        // 比 SimpleAnimatedParticle 默认的 0.91 缓一点，气才飘得远
        this.friction = 0.96F;
        this.quadSize *= 0.75F;
        this.setSpriteFromAge(sprites);
        this.setColor(Color.mixColors(0x0, 0x126568, this.random.nextFloat()));
        this.setAlpha(1.0F);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
    }

    public static class Provider implements ParticleProvider<SoulFlowParticleData> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SoulFlowParticleData data, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            // 忽略 xSpeed/ySpeed/zSpeed：那是 sendParticles 给的高斯随机抖动，没有方向。
            // 真正的速度在 data 里（见 SoulFlowParticleData 的类注释）
            return new SoulFlowParticle(level, x, y, z, data.dx(), data.dy(), data.dz(), this.sprites);
        }
    }
}
