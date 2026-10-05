package com.minecart.yunxian.client.particle;

import com.minecart.yunxian.particle.FlameFlowParticleData;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SimpleAnimatedParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 火焰吐息里的一颗粒子：从嘴边出发、顺着一个方向飞出去、飞够远才开始淡。
 * <p>
 * <b>外观抄的是 Create 鼓风机气流</b>：贴图用原版 {@code minecraft:generic_0..7}——那正是 Create
 * {@code air_flow.json} 用的同一组（它那颗粒子本身抄不了，见 {@code ModParticles} 的类注释，所以这边重写）。
 * 颜色由服务端随数据发过来，三种角色共用这一个类：
 * <ul>
 *   <li><b>橙色气流</b>：{@code mixColors(0xFF4400, 0xFF8855, …)} + α 0.5，照抄 Create
 *       {@code BlastingType#morphAirFlow}（高炉那档气流就是半透明的橙红色）；</li>
 *   <li><b>亮黄火星</b>：不透明的暖黄，负责「这是一团火」的第一眼印象；</li>
 *   <li><b>暗灰的烟</b>：α 更低，垫在底下给整股火一点厚度。</li>
 * </ul>
 * <b>会撞墙</b>：{@code hasPhysics} 开着（原版默认就是 true，Create 那颗粒子反而关掉了它）——
 * 那颗粒子待在风道里，风道本身已经被方块框住了；这里没有风道，只能靠碰撞，否则火会穿墙。
 * <p>
 * <b>淡出按「飞了多远」算，不按「活了多久」</b>：这是刻意的——按时间淡的话，速度有随机、
 * 慢的那些还没飞到射程尽头就开始变淡，看起来就是「十二格处火已经没了」。
 * 钉在距离上，无论快慢都在 {@value #FADE_START} 格处才开始淡、{@value #FADE_END} 格处淡完，
 * 于是整束火的「浓」正好铺满判定射程。
 * <p>
 * 贴图也跟着距离走（近处小张、远处大张），顺带复刻 Create 气流「越远越散」的那点意思。
 */
@OnlyIn(Dist.CLIENT)
public class FlameFlowParticle extends SimpleAnimatedParticle {

    /**
     * 兜底寿命（tick）。
     * <p>
     * 正常情况下粒子在飞够 {@link #FADE_END} 之前就自己收掉了，这个数只是防呆——
     * 得取得足够大，让最慢的那些（出射速度带着负随机）也来得及飞完，
     * 否则它们会在半空中被硬生生掐掉。
     */
    private static final int LIFETIME = 100;

    /** 飞够这么远才开始淡（格）。与 {@code FlameBreath} 的射程 12 对齐 */
    private static final double FADE_START = 12.0;

    /** 淡到没有时已经飞了这么远（格），到这里直接收掉 */
    private static final double FADE_END = 15.0;

    /** 这颗自己带着的不透明度，淡出要在它基础上往下压 */
    private final float baseAlpha;

    /** 出火口，用来量「飞了多远」 */
    private final double spawnX;
    private final double spawnY;
    private final double spawnZ;

    protected FlameFlowParticle(ClientLevel level, double x, double y, double z,
                                double dx, double dy, double dz, int argb, SpriteSet sprites) {
        super(level, x, y, z, sprites, 0.0F);
        this.lifetime = LIFETIME;
        this.hasPhysics = true;
        // 比 SimpleAnimatedParticle 默认的 0.91 缓得多：要的是「一股喷出去的气」，
        // 而不是「丢出去很快就停的小石子」。它决定的是「飞够 12 格要多久」，不是能飞多远
        this.friction = 0.98F;
        this.quadSize *= 0.75F;
        this.baseAlpha = (argb >>> 24) / 255.0F;
        this.spawnX = x;
        this.spawnY = y;
        this.spawnZ = z;
        this.setSprite(sprites.get(7, 8));
        this.setColor(argb & 0xFFFFFF);
        this.setAlpha(this.baseAlpha);
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
    }

    /**
     * 自己接管淡出、贴图与收尾。
     * <p>
     * 为什么不直接用 {@code SimpleAnimatedParticle} 那套：它后半程会把 alpha <b>从 1.0 开始</b>往下压
     * （橙色气流那个 α=0.5 会被直接抹掉），而且它按<b>年龄</b>淡、按<b>年龄</b>换贴图——
     * 这里两样都要按<b>距离</b>来（见类注释）。
     */
    @Override
    public void tick() {
        super.tick();

        // 撞墙停住之后位置不再变，「飞了多远」就永远涨不到淡出线，会在墙上留一块亮的。
        // super 已经把 xo/yo/zo 记成了移动前的位置，两者相等就说明这一 tick 压根没挪动
        if (this.x == this.xo && this.y == this.yo && this.z == this.zo) {
            this.remove();
            return;
        }

        double traveled = Mth.length(this.x - this.spawnX, this.y - this.spawnY, this.z - this.spawnZ);
        if (traveled >= FADE_END) {
            this.remove();
            return;
        }

        // 贴图随距离推进，索引 7 朝出火口那一侧（与 Create 的 selectSprite(7) 同向）
        int index = 7 - Mth.clamp((int) (traveled / FADE_END * 8.0), 0, 7);
        this.setSprite(this.sprites.get(index, 8));

        float fade = traveled <= FADE_START ? 1.0F
                : (float) (1.0 - (traveled - FADE_START) / (FADE_END - FADE_START));
        this.setAlpha(this.baseAlpha * fade);
    }

    public static class Provider implements ParticleProvider<FlameFlowParticleData> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(FlameFlowParticleData data, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            // 忽略 xSpeed/ySpeed/zSpeed：那是 sendParticles 给的高斯随机抖动，没有方向。
            // 真正的速度在 data 里（见 FlameFlowParticleData 的类注释）
            return new FlameFlowParticle(level, x, y, z, data.dx(), data.dy(), data.dz(),
                    data.argb(), this.sprites);
        }
    }
}
