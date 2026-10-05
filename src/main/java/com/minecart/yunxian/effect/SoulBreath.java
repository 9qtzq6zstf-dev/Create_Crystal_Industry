package com.minecart.yunxian.effect;

import com.minecart.yunxian.particle.SoulFlowParticleData;
import com.minecart.yunxian.registry.ModEffects;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 「灵魂火喷流」：喝过可燃冰圣代、身上还带着「可燃气体」（{@link ModEffects#FLAMMABLE_GAS}）时，
 * <b>潜行 + 空手 + 右键</b>向前喷出一道蓝火。
 * <p>
 * <b>开关挂在「可燃气体」而不是「冰封」上</b>：两者都是那一口圣代给的，但时长不同——
 * 冰封 10 秒，可燃气体 60 秒。冻结只是喝下去的代价，喷火才是这件玩具的正题，
 * 所以冻完的那 50 秒里照样能喷。
 * <p>
 * 这道火复刻 Create 鼓风机的<b>缠魂</b>气流：被喷到的掉落物走 {@link FanProcessing#applyProcessing}
 * （沙 → 灵魂沙、木头 → 诡异木这类缠魂配方），被喷到的生物照跑
 * {@link AllFanProcessingTypes.HauntingType#affectEntity}（失明 + 缓慢，马 → 骷髅马）。
 * <b>此外</b>还额外造成伤害并点燃——这一层抄的不是缠魂，是 Create 的<b>高炉</b>气流
 * （{@code BlastingType#affectEntity}：{@code !fireImmune()} 就 {@code igniteForSeconds} + {@code hurt}），
 * 因为原版缠魂本身不伤也不烧。
 * <p>
 * <b>为什么「按住」要靠心跳包</b>：原版空手对空气右键一个包都不发，服务端拿不到这个输入，
 * 所以由客户端（{@code SoulBreathClient}）在按住期间每隔约 4 tick 发一次心跳，服务端每收到一次就把
 * 到期时刻推到「当前刻 + {@link #BEAM_TICKS}」。松手<b>不需要专门通知</b>：心跳一停，
 * 到期时刻不再被推后，火束自己在不到 {@link #BEAM_TICKS} 的时间里灭掉。
 * <p>
 * <b>{@link #BEAM_TICKS} 就在「连得上」和「停得快」之间取平衡</b>：它必须明显大于心跳间隔（约 4 tick），
 * 否则丢一次包就断流；又不能太大，否则松手后火还拖着一截。8 大约是心跳间隔的两倍——
 * 容忍一次丢包，松手后的余焰也在 0.4 秒以内。顺带地，它让<b>快速点一下</b>也能喷出一小股
 * （客户端那一下按下就发心跳，之后虽然松手不再发，火也已经烧起来了），
 * 而如果改成「松手立刻停」，点击会因为客户端下一 tick 就报停而几乎什么都喷不出来。
 * <p>
 * <b>每 tick 都打、不自己排冷却</b>：伤害走原版 {@code inFire}（和灵魂火方块同一条），每 tick 调一次
 * {@code hurt}，靠原版 20 tick 无敌帧自然节流成「约 2 点 / 11 tick」。别改成自己记 10 tick 冷却——
 * 那会和无敌帧的判定撞在一起，静默丢掉一半伤害。
 * <p>
 * <b>两个副作用，都是故意的</b>：
 * <ul>
 *   <li>潜行 + 空手右键本来会照常走方块的 {@code useWithoutItem}（原版只在<i>手里有东西</i>时才因潜行跳过
 *       方块交互），所以客户端在触发时<b>取消了这次输入</b>。代价是「可燃气体」的整整一分钟里潜行空手右键
 *       点不了按钮/拉杆、也收不回地上的圣代方块——这是「瞄哪里都喷」换来的。</li>
 *   <li>伤害类型 {@code inFire} 带 {@code is_fire} 标签，所以圣代自己给的 20 秒抗火恰好能挡下这束火：
 *       头 20 秒里两个喝过圣代的人互喷谁也烧不动谁。但抗火比可燃气体短——后 40 秒里就是实打实的伤害了。</li>
 * </ul>
 */
public final class SoulBreath {

    /** 射程（格）。比原版 4.5 格触及距离长，才像一口气而不是一次点击 */
    private static final double RANGE = 6.0;

    /** 火束半径（格）：判定时再加上生物自身半个宽度，所以个子大的更容易被扫到 */
    private static final double RADIUS = 0.75;

    /** 起点从眼睛往前挪这么多，避免射线起点卡在自家碰撞箱里 */
    private static final double ORIGIN_FORWARD = 0.25;

    /**
     * 一次心跳能让火束多活多久（tick）。
     * <p>
     * 每次收到心跳就把到期时刻推到「当前刻 + 这个数」，所以它同时是<b>松手后的余焰</b>和
     * <b>丢包容忍度</b>。心跳间隔约 4 tick，取 8 = 两倍：丢一次心跳不断流，松手后最多再烧 0.4 秒。
     * 想要更短的余焰就往下调，但别低于 6——那已经贴着心跳间隔了。
     */
    private static final int BEAM_TICKS = 8;

    /** 每 tick 沿火束撒几段火焰。灵魂火粒子没有初速、会自己往上飘，撒成一条线正好成束 */
    private static final int FLAME_STEPS = 6;

    /** 每颗粒子的随机偏移，让火束有毛边而不是拿尺子画的 */
    private static final double PARTICLE_JITTER = 0.05;

    /**
     * 每 tick 撒几颗「气流」粒子。它与火焰粒子是两种观感：火焰是不动的蓝点，
     * 气流是往前飘的青灰雾（见 {@code ModParticles} 里的 soul_flow）。
     */
    private static final int FLOW_STEPS = 5;

    /**
     * 气流往前飘的速度（格/tick）。
     * <p>
     * 手感对齐 Create 风道里那颗粒子的移动量（约 1/8 格每 tick 再乘衰减）：
     * 配上 0.96 的摩擦与 40 tick 的寿命，一颗大约往前飘 2~3 格就淡掉。
     */
    private static final double FLOW_SPEED = 0.18;

    /**
     * 粒子整条线往下挪这么多格。
     * <p>
     * 判定那条线仍在眼睛高度（打哪儿算哪儿），但<b>画出来的</b>那条线要沉到胸口——
     * 从眼睛平着喷出去的话，第一人称下整束火正好糊在准星上，什么都看不见。
     */
    private static final double PARTICLE_DROP = 0.35;

    /** 粒子起点再往前推这么多格，让最近的一颗也离镜头远一点 */
    private static final double PARTICLE_FORWARD = 0.5;

    /** 伤害：2 点，与原版灵魂火方块同档 */
    private static final float DAMAGE = 2.0F;

    /** 点燃时长（秒）。每 tick 刷新；{@code igniteForSeconds} 只抬高不压低，所以不必自己判重 */
    private static final float IGNITE_SECONDS = 5.0F;

    /** 环境音间隔（tick） */
    private static final int SOUND_INTERVAL = 10;

    /** 到期时刻在玩家持久化数据里的键名；前缀避开别的模组 */
    private static final String EXPIRY_KEY = "CreateCrystalIndustrySoulBreath";

    private SoulBreath() {
    }

    /**
     * 心跳入口，由 {@code SoulBreathPayload} 在服务端调用：把火束的寿命再往后推一格。
     * <p>
     * 客户端已经判过同样的条件，这里再从头校验一遍是<b>防作弊的那一份</b>——「有可燃气体、在潜行、主手空」
     * 三件事服务端都看得到，伪造心跳续不出更长的火，也喷不出本来没资格喷的火。
     */
    public static void refresh(ServerPlayer player) {
        if (!isWielding(player)) {
            return;
        }
        player.getPersistentData().putLong(EXPIRY_KEY,
                player.serverLevel().getGameTime() + BEAM_TICKS);
    }

    /**
     * 每 tick 的推进。挂在 {@link PlayerTickEvent.Post} 上，两边都会被叫到，
     * 所以第一行的 {@code instanceof ServerPlayer} 既做了类型转换、也当了「只在服务端跑」的门。
     * <p>
     * 到期时刻存在实体持久化数据里（跟 {@code ElectrifiedZap} 同一个套路，不新增 attachment）：
     * 值自会过期，没喷过火的玩家读出来是 0，永远落在「已到期」那一侧。
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (level.getGameTime() >= player.getPersistentData().getLong(EXPIRY_KEY)) {
            return;
        }
        // 心跳空档里状态可能已经变了（松了潜行键、手里拿了东西、可燃气体到点），
        // 别等客户端的下一个包——服务端自己每 tick 复核，不通过就当场灭掉
        if (!isWielding(player)) {
            player.getPersistentData().putLong(EXPIRY_KEY, 0L);
            return;
        }
        breathe(level, player);
    }

    /** 喷火的前置条件：客户端与服务端共用同一套（服务端那份才是权威） */
    private static boolean isWielding(ServerPlayer player) {
        return player.hasEffect(ModEffects.FLAMMABLE_GAS)
                && player.isShiftKeyDown()
                && player.getMainHandItem().isEmpty()
                && !player.isSpectator()
                && player.isAlive();
    }

    /**
     * 这一 tick 的火束：算一条被方块截断的射线，沿途结算实体、撒粒子。
     * <p>
     * <b>「打哪儿」和「看着像哪儿」是两条线</b>：判定用眼睛高度那条（瞄得准），
     * 画出来的那条整体下沉到胸口（不糊镜头），见 {@link #PARTICLE_DROP}。
     */
    private static void breathe(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 from = player.getEyePosition().add(look.scale(ORIGIN_FORWARD));
        Vec3 to = from.add(look.scale(RANGE));

        // 撞墙即止：火束不该穿墙去烧后面的东西
        BlockHitResult hit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();

        applyToEntities(level, player, from, end);

        Vec3 visualFrom = from.add(0.0, -PARTICLE_DROP, 0.0).add(look.scale(PARTICLE_FORWARD));
        Vec3 visualEnd = end.add(0.0, -PARTICLE_DROP, 0.0);
        spawnParticles(level, visualFrom, visualEnd, look);
    }

    /**
     * 结算火束里的实体。
     * <p>
     * 先用包住整条线段的盒子粗筛（{@code getEntities} 传了施法者，他已被排除），再逐个做
     * <b>点到线段</b>的距离判定——盒子是个立方体，光靠粗筛会把四个角上离火束老远的实体也捞进来。
     * <p>
     * 掉落物与其它实体分两路：掉落物只做缠魂加工（别把它点着了，那是我们要加工的东西）；
     * 其它实体先跑 Create 的缠魂实体效果，再补伤害与点燃。
     */
    private static void applyToEntities(ServerLevel level, ServerPlayer caster, Vec3 from, Vec3 end) {
        AABB search = new AABB(from, end).inflate(RADIUS + 1.0);
        for (Entity entity : level.getEntities(caster, search, EntitySelector.NO_SPECTATORS)) {
            if (!entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            double reach = RADIUS + entity.getBbWidth() * 0.5;
            if (distanceToSegmentSqr(entity.getBoundingBox().getCenter(), from, end) > reach * reach) {
                continue;
            }

            if (entity instanceof ItemEntity item) {
                // 每 tick 一次，与真鼓风机同节奏：加工计时按 tick 递减，攒满才转化
                if (FanProcessing.canProcess(item, AllFanProcessingTypes.HAUNTING)) {
                    FanProcessing.applyProcessing(item, AllFanProcessingTypes.HAUNTING);
                }
                continue;
            }

            AllFanProcessingTypes.HAUNTING.affectEntity(entity, level);
            if (entity instanceof LivingEntity living && !living.fireImmune()) {
                living.igniteForSeconds(IGNITE_SECONDS);
                living.hurt(level.damageSources().inFire(), DAMAGE);
            }
        }
    }

    /**
     * 撒粒子：蓝火焰 + 向前流动的青灰气流。
     * <p>
     * 逐点发而不是用 {@code sendParticles} 的扩散参数凑一团：那个参数给的是立方体范围，
     * 得到的是雾，而这里要的是<b>一束</b>（和 {@code ElectrifiedZap#drawArc} 同一个理由）。
     * <p>
     * 两种粒子分工不同：{@code SOUL_FIRE_FLAME} 是「灵魂火」本身（原版那种停在原地飘的蓝焰），
     * {@code soul_flow} 是鼓风机缠魂气流的复刻（会往前飞的青灰雾，见 {@code SoulFlowParticle}）。
     * 后者能带上方向，靠的是把速度<b>写进粒子数据</b>里随包发出去——原版广播通道只给得起对称的
     * 随机抖动，给不了方向，这条见 {@link SoulFlowParticleData} 的类注释。
     * <p>
     * 开销明账：每 tick 约 {@value #FLAME_STEPS} + {@value #FLOW_STEPS} + 2 次广播，
     * 只发给 32 格内的玩家。
     */
    private static void spawnParticles(ServerLevel level, Vec3 from, Vec3 end, Vec3 look) {
        RandomSource random = level.random;

        for (int i = 0; i < FLAME_STEPS; i++) {
            Vec3 point = from.lerp(end, (i + random.nextDouble()) / FLAME_STEPS);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    point.x, point.y, point.z, 1,
                    PARTICLE_JITTER, PARTICLE_JITTER, PARTICLE_JITTER, 0.0);
        }

        SoulFlowParticleData flow = SoulFlowParticleData.along(look.scale(FLOW_SPEED));
        for (int i = 0; i < FLOW_STEPS; i++) {
            Vec3 point = from.lerp(end, (i + random.nextDouble()) / FLOW_STEPS);
            level.sendParticles(flow,
                    point.x, point.y, point.z, 1,
                    PARTICLE_JITTER, PARTICLE_JITTER, PARTICLE_JITTER, 0.0);
        }

        Vec3 smoke = from.lerp(end, random.nextDouble());
        level.sendParticles(ParticleTypes.SMOKE,
                smoke.x, smoke.y, smoke.z, 1,
                PARTICLE_JITTER, PARTICLE_JITTER, PARTICLE_JITTER, 0.0);

        if (level.getGameTime() % SOUND_INTERVAL == 0) {
            level.playSound(null, from.x, from.y, from.z, SoundEvents.SOUL_ESCAPE.value(),
                    SoundSource.PLAYERS, 0.5F, 1.2F);
        }
    }

    /** 点到线段的距离平方。线段退化成一点时要走距离公式，否则除法会炸 */
    private static double distanceToSegmentSqr(Vec3 point, Vec3 start, Vec3 end) {
        Vec3 span = end.subtract(start);
        double spanSqr = span.lengthSqr();
        if (spanSqr < 1.0E-7) {
            return point.distanceToSqr(start);
        }
        double t = Mth.clamp(point.subtract(start).dot(span) / spanSqr, 0.0, 1.0);
        return point.distanceToSqr(start.add(span.scale(t)));
    }
}
