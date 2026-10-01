package com.minecart.yunxian.effect;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.minecart.yunxian.registry.ModDamageTypes;
import com.minecart.yunxian.registry.ModEffects;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * 「感电」的实际效果：带电的生物挨打时会放电——自己再吃一发雷击，并顺带电到旁边一个同样带电的生物，
 * 两者之间拉一道电火花。触发规则（谁算「带电」、谁会被电到）见 {@code ElectrifiedAura}。
 * <p>
 * <b>会沿着排一路传下去</b>：传给同伴的那一发同样是「受到伤害」，所以同伴也会进一次本方法、
 * 再往它旁边的下一个传——A 电 B、B 电 C、C 电 D……每一个被电到的（除了带头那个）都挨两发：
 * 前一发是从邻居传过来的，后一发是它自己放电时的自伤。链<b>不会回头</b>，也不会绕圈，
 * 因为刚放过电的生物已经进了冷却，同伴找目标时会跳过它；长度另有 {@link #MAX_CHAIN_LENGTH} 兜底。
 * <p>
 * <b>钩子选在受伤结算之后</b>（{@link LivingDamageEvent.Post}）而不是之前：这里是「确实掉了血」的时刻，
 * 所以「受到伤害」这四个字是字面成立的——被无敌帧或护盾整个吃掉的攻击不会放电，
 * 不用自己再判一遍。掉血量就在 {@link LivingDamageEvent.Post#getNewDamage()} 里。
 * <p>
 * <b>伤害类型是自己的</b>（{@link ModDamageTypes#ELECTRIC_DISCHARGE}），不是原版的
 * {@code lightning_bolt}。本方法是在受害者那次受伤结算的尾巴上被调起来的，那时原版已经把
 * {@code invulnerableTime} 顶到了 20——直接打 4 点会被「无敌帧中、又没比上次重」那条判定整个吞掉。
 * 与其在代码里挪无敌帧，不如把这条类型登记进 {@code minecraft:bypasses_cooldown} 与
 * {@code minecraft:bypasses_armor}，让原版自己放行：放电必定落地，而且无视护甲。
 * 不这么做就得去改原版 {@code lightning_bolt} 的标签，那会连带改掉原版真实落雷。
 * <p>
 * <b>跟 {@code shock_immune} 盔甲的关系</b>：主要的一层是「根本进不来」——
 * {@code ElectrifiedAura} 会把全套穿戴者身上的「感电」直接摘掉，所以他没有这个状态，
 * 既不会放电、也不会被链条选为同伴。放出来的电击是自定义类型，{@link ShockWard} 认两种类型
 * （原版 {@code lightning_bolt} 与本模组的 {@code electric_shock}）作为第二层保险：
 * 穿上全套的那一瞬间效果还没被摘掉的短短几 tick 里，自己挨的那发照样会被拦下。
 */
public final class ElectrifiedZap {

    /** 每次放电的伤害：4 点（两颗心）。刻意不大——它是个"顺手补刀"而不是独立输出手段 */
    private static final float ZAP_DAMAGE = 4.0F;

    /**
     * 找同伴的半径（格）。取得比感电判定半径（{@code ElectrifiedAura.RADIUS} = 3）大一点：
     * 两个生物各自只要在某个弧光石/电流浆的 3 格内就会带电，所以它们之间最多能差出 6 格，
     * 5 格基本能把"站在一起挨电"的那一对圈进来。
     */
    private static final double PARTNER_RADIUS = 5.0;

    /**
     * 放电冷却（tick）：一个生物放过一次电之后，这么多 tick 内不再放电、也不会被链条选为同伴。
     * <p>
     * 它<b>不是</b>用来断链的——链条本来就该沿着走（A 电 B、B 电 C）。它管两件事：
     * <ul>
     *   <li><b>挡住自伤回头</b>：放电会给放电者自己来一发雷击，而那一发又会触发一次本方法。
     *       动手之前先盖章，重入的那次看到冷却就直接返回，不会自己电自己没完；</li>
     *   <li><b>当节流阀</b>：没有它，被围殴时每一下挨打都会放一次电，多打几下就是成吨的附加伤害。
     *       一秒一次是"偶尔被电到"的手感，而不是"每刀都带雷"。</li>
     * </ul>
     * 顺带地，它让链条<b>不能走回头路</b>：刚放完电的那个已经在冷却里，同伴找目标时会跳过它，
     * 所以链只会往前传，同一个生物整条链里最多挨一次（递归因此必然收敛）。
     */
    private static final int COOLDOWN_TICKS = 20;

    /**
     * 一条链最多带上几个生物。
     * <p>
     * 链条是<b>递归</b>的：A 电 B 是在 A 的受伤回调里同步调 {@code hurt} 触发的，B 电 C 又嵌在
     * B 的里面。冷却虽然保证了不绕圈（递归必然终止），但"一排站着 50 只"就会一口气嵌 50 层，
     * 栈深跟着链长走。这个上限把最坏情况钉死，顺便也是个平衡旋钮：
     * 想让它只电到隔壁那一只就把它改成 2。
     */
    private static final int MAX_CHAIN_LENGTH = 16;

    /** 电火花连线的取样点数（含两端之间，两端本身不画） */
    private static final int ARC_SEGMENTS = 10;

    /** 每颗火花的随机偏移，让连线看着是"噼啪"的而不是拿尺子画的 */
    private static final double ARC_JITTER = 0.15;

    /**
     * 当前这条链已经传到第几个生物了。
     * <p>
     * 用 {@link ThreadLocal} 而不是普通静态字段：链条是一串同步嵌套调用，同一个线程里数得清；
     * 而 NeoForge 的多个维度各在自己的线程上跑，普通静态字段会被别的维度同时改坏。
     */
    private static final ThreadLocal<Integer> CHAIN_DEPTH = ThreadLocal.withInitial(() -> 0);

    /** 冷却时间戳在实体持久化数据里的键名；前缀避开别的模组 */
    private static final String COOLDOWN_KEY = "CreateCrystalIndustryElectrifiedZap";

    private ElectrifiedZap() {
    }

    public static void onDamagePost(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (event.getNewDamage() <= 0.0F) {
            return; // 被无敌帧/护盾整个吃掉的攻击不算「受到伤害」
        }
        if (!victim.hasEffect(ModEffects.ELECTRIFIED)) {
            return; // 只有带电的才会放电
        }
        if (!(victim.level() instanceof ServerLevel level)) {
            return; // 只在服务端结算
        }
        if (isOnCooldown(level, victim)) {
            return;
        }

        int depth = CHAIN_DEPTH.get();
        if (depth >= MAX_CHAIN_LENGTH) {
            return; // 这条链已经够长了，到此为止
        }

        LivingEntity partner = findPartner(level, victim);

        // 只给「正在放电的这个」盖章，**不给同伴盖**：同伴待会儿会因为自己挨的那发雷击再进一次
        // 本方法，那时它才盖章、并接着往下一个传——闪电链就是这么沿着一排走下去的。
        // （早先给同伴也盖了章，结果链只能走一跳：A 电 B 之后 B 已经在冷却里，B 就再也传不到 C。）
        // 给自己盖的这一章挡的是自己那发自伤回头再触发一次；必须赶在 zap 之前盖。
        stamp(level, victim);

        CHAIN_DEPTH.set(depth + 1);
        try {
            zap(level, victim);
            if (partner != null) {
                zap(level, partner);
                drawArc(level, victim, partner);
            }
        } finally {
            // 恢复而不是减一：finally 在每一层各跑一次，逐层退回原来的深度
            CHAIN_DEPTH.set(depth);
        }
    }

    /**
     * 给目标来一发电击。
     * <p>
     * 伤害源<b>刻意不挂任何实体</b>（{@code source(damageTypeKey)} 那个单参重载）。原版
     * {@code DamageSource#getLocalizedDeathMessage} 就是按「有没有实体」分支的：没有实体时它退回去看
     * 受害者的击杀归属，于是「死于电刑」和「被某人造成的闪电劈死」这两句由原版自己挑，
     * 我们只管把两条语言键都写上。挂了实体的话走的会是另一支（拿实体名当参数），
     * 那两句就只剩一句能写。
     * <p>
     * 无敌帧不用管了：{@link ModDamageTypes#ELECTRIC_DISCHARGE} 登记在
     * {@code minecraft:bypasses_cooldown} 里，原版会跳过「无敌帧中、且没比上次重」那道判定，
     * 伤害必定落地。护甲同理，它登记在 {@code bypasses_armor} 里。
     * <p>
     * 这里用的是 {@code ELECTRIC_DISCHARGE} 而不是 {@code ELECTRIC_SHOCK}：后者是电流浆那条，
     * 刻意<b>不</b>跳无敌帧（它靠无敌帧限速），拿它来放电的话这一发会被整个吞掉。
     * 两条类型的区别只在这一处，死亡讯息共用。
     */
    private static void zap(ServerLevel level, LivingEntity target) {
        if (target.isDeadOrDying()) {
            return; // 已经倒下就别再补刀了（电击会再触发一次死亡结算）
        }
        target.hurt(level.damageSources().source(ModDamageTypes.ELECTRIC_DISCHARGE), ZAP_DAMAGE);
    }

    /**
     * 在附近找一个同样带电、且自己也没在冷却里的生物，取最近的那个。
     * <p>
     * 排除冷却中的目标，是链条「不回头的往一个方向走、链上的生物各挨一次」这条性质的来源：
     * 轮到 B 找目标时，刚才电它的 A 已经盖过章，于是被跳过，B 只能往 C 那边传。
     * 一排生物之间距离相近时，被跳过的那只自然就被排除在候选之外，
     * 不会出现「B 电回 A、A 再电回 B」。
     */
    @Nullable
    private static LivingEntity findPartner(ServerLevel level, LivingEntity self) {
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                self.getBoundingBox().inflate(PARTNER_RADIUS),
                other -> other != self
                        && other.isAlive()
                        && other.hasEffect(ModEffects.ELECTRIFIED)
                        && !isOnCooldown(level, other));

        LivingEntity closest = null;
        double bestDistance = Double.MAX_VALUE;
        for (LivingEntity candidate : candidates) {
            double distance = candidate.distanceToSqr(self);
            if (distance < bestDistance) {
                bestDistance = distance;
                closest = candidate;
            }
        }
        return closest;
    }

    /**
     * 在两个生物的眼睛之间拉一道电火花。
     * <p>
     * 逐点发粒子而不是用 {@code sendParticles} 的扩散参数凑一团：那个参数给的是立方体范围，
     * 只会得到一团雾，而这里要的是<b>一条线</b>。点不多（{@value #ARC_SEGMENTS} 个）且整条链
     * 受冷却限制在一秒一次，逐点发包的开销可以忽略。
     */
    private static void drawArc(ServerLevel level, LivingEntity from, LivingEntity to) {
        Vec3 start = from.getEyePosition();
        Vec3 end = to.getEyePosition();
        for (int i = 1; i < ARC_SEGMENTS; i++) {
            Vec3 point = start.lerp(end, (double) i / ARC_SEGMENTS);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    point.x, point.y, point.z, 1, ARC_JITTER, ARC_JITTER, ARC_JITTER, 0.0);
        }
    }

    /**
     * 该生物是否还在放电冷却里。
     * <p>
     * 时间戳存在实体的持久化数据（NeoForge 给每个实体挂的 {@code CompoundTag}）里，跟 Create 的
     * {@code NetheriteDivingHandler} 是同一个套路：不用注册 attachment，值本身也"会自己过期"——
     * 存的是游戏刻，而游戏刻只增不减，所以存档重载之后旧时间戳照样只是"很久以前"。
     * 没放过电的实体身上没有这个键，读出来是 0，也就永远不会被误判成冷却中。
     */
    private static boolean isOnCooldown(ServerLevel level, LivingEntity entity) {
        long lastZap = entity.getPersistentData().getLong(COOLDOWN_KEY);
        return level.getGameTime() - lastZap < COOLDOWN_TICKS;
    }

    private static void stamp(ServerLevel level, LivingEntity entity) {
        entity.getPersistentData().putLong(COOLDOWN_KEY, level.getGameTime());
    }
}
