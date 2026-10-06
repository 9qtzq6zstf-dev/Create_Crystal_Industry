package com.minecart.yunxian.effect;

import com.minecart.yunxian.particle.FlameFlowParticleData;
import com.minecart.yunxian.registry.ModEffects;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * 「火焰吐息」：喝过可燃冰圣代、身上还带着 {@link ModEffects#FLAME_BREATH} 这个效果时，
 * <b>按住潜行键</b>就朝正前方喷出一道火，射程 {@value #RANGE} 格。
 * <p>
 * <b>为什么是「潜行即喷」而不是右键</b>：潜行状态在服务端<b>本来就是同步过来的</b>
 * （{@code ServerboundPlayerInputPacket}），所以整件事在服务端自洽——不需要网络包、
 * 不需要客户端去拦输入、更不会像早先那样把潜行空手右键的方块交互盖掉。
 * 代价是那 60 秒里想单纯蹲一下也会喷火。
 * <p>
 * <b>这道火复刻 Create 鼓风机的<u>高炉</u>气流</b>（不是缠魂）：被喷到的掉落物走
 * {@link FanProcessing#applyProcessing}（原版熔炼配方，沙 → 玻璃这类），
 * <b>传送带与置物台上托着的物品也一样</b>（见 {@link #applyToTransportedItems}），被喷到的生物跑
 * {@link AllFanProcessingTypes.BlastingType#affectEntity}——那里面已经自带了
 * 「{@code !fireImmune()} 就 {@code igniteForSeconds(10)} + {@code hurt(fanLava, 4)}」，
 * 所以本类<b>不再自己补伤害</b>，否则就是同一口气里烧两遍。
 * <p>
 * <b>粒子全部从嘴边出发再飞</b>：不再像早先那样沿着火线在半空撒点。于是每一颗都必须是「自带速度」的
 * 自定义粒子（{@code flame_flow}）——原版广播通道给不了方向，这条见
 * {@link FlameFlowParticleData} 的类注释。落点只有嘴一个，扩散靠的是<b>出射方向在一个圆锥里随机</b>，
 * 所以凑近看是一股往前张开的火，而不是一条细线。
 * <p>
 * <b>「烧到哪」和「火喷到哪」是同一个锥</b>：锥尖就在<b>嘴</b>上（粒子正是从那儿出去的），轴是准心方向，
 * 半角 {@value #CONE_DEGREES} 度。于是「火焰粒子覆盖到的地方才被熔炼」是字面成立的，
 * 不会出现半空里看着没火、东西却在掉耐久的地方。方块截断仍走眼睛那条射线（瞄得准），
 * 用它的长度给锥封顶——墙后面的照旧烧不到。
 */
public final class FlameBreath {

    /** 射程（格）。判定用，也决定了出射速度该给多大 */
    private static final double RANGE = 12.0;

    /** 判定射线的起点从眼睛往前挪这么多，避免起点卡在自家碰撞箱里 */
    private static final double ORIGIN_FORWARD = 0.25;

    /**
     * 粒子起点：眼睛往下这么多格。
     * <p>
     * 眼睛大致就是嘴的高度再往上一点，所以这一下把出火口压到嘴上；顺带也躲开了第一人称镜头
     * （从眼睛平着喷，整束火正好糊在准星上）。
     */
    private static final double MOUTH_DROP = 0.3;

    /** 粒子起点再往前挪这么多格，让最近的一颗离脸远一点 */
    private static final double MOUTH_FORWARD = 0.4;

    /** 每 tick 从嘴边喷出几颗 */
    private static final int PARTICLES_PER_TICK = 8;

    /**
     * 出射方向相对准心的最大偏角（度）——这就是「扩散」的来源。
     * <p>
     * 十度在射程尽头张开成半径约 2.1 格的一个圆（{@code 12 × tan(10°)}），
     * 近处仍是一股细流、远处才散开，正是「一口气喷出去」的形状。想更聚拢就往小调。
     * <p>
     * <b>这不只是画面上那个锥，它就是判定范围本身</b>——被熔炼/被烧到的地方，
     * 和粒子实际铺到的地方是同一个锥（见 {@link #insideCone}）。
     */
    private static final double CONE_DEGREES = 10.0;

    /** {@link #CONE_DEGREES} 的正切，锥里的半径判断每 tick 要用好几次 */
    private static final double CONE_TAN = Math.tan(Math.toRadians(CONE_DEGREES));

    /**
     * 锥尖附近那一段的最小半径（格）。
     * <p>
     * 正经的锥在顶点收成一个点，而粒子是从嘴边一小片区域喷出来的（出火口本身有随机抖动），
     * 完全不给余量的话，贴脸踩着的东西反而扫不到。这点余量就当作出火口的宽度。
     */
    private static final double CONE_CORE = 0.35;

    /**
     * 出射速度（格/tick）。
     * <p>
     * 它现在决定的<b>不是</b>「能飞多远」——火铺多满由 {@code FlameFlowParticle} 的淡出线按距离钉死
     * （与 {@value #RANGE} 对齐），这里只管「飞满全程要多久」：配合 0.98 的摩擦，0.5 的速度约 32 tick 到位，
     * 看着就是一股稳稳推出去的火。调小会显得火推不出去，调大则像霰弹。
     */
    private static final double PARTICLE_SPEED = 0.5;

    /** 出射速度的随机幅度（±这个比例），让一束火有快有慢而不是整整齐齐 */
    private static final double SPEED_VARIANCE = 0.25;

    /** 出火口那一点上的随机抖动，避免所有粒子从一个数学点里冒出来 */
    private static final double MUZZLE_JITTER = 0.08;

    /** 火声的间隔（tick）。原版火焰方块的环境音差不多也是这个密度 */
    private static final int SOUND_INTERVAL = 20;

    /**
     * 沿火束轴心隔多远查一格方块（格），用来找传送带 / 置物台。
     * <p>
     * 取半格而不是整格：斜着往下喷时整格的取样会从两格方块之间穿过去、漏掉底下的传送带。
     * 代价只是每 tick 多几次方块查询（{@value #RANGE} 格最多 25 个取样点）。
     */
    private static final double BEAM_SAMPLE_STEP = 0.5;

    /**
     * 橙色气流：α 0.5 + {@code mixColors(0xFF4400, 0xFF8855, …)}。
     * <p>
     * 照抄 Create {@code BlastingType#morphAirFlow}——高炉那档气流就是半透明的橙红，颜色每颗随机取一档，
     * 叠起来才有深浅。
     */
    private static final int FLOW_ALPHA = 0x80;

    /** 亮黄火星：不透明，负责「这是一团火」的第一眼印象 */
    private static final int EMBER_ARGB = 0xFFFFCC44;

    /** 暗灰的烟：α 0.35，垫在底下给整股火一点厚度 */
    private static final int SMOKE_ARGB = 0x59303030;

    private FlameBreath() {
    }

    /**
     * 每 tick 的推进。挂在 {@link PlayerTickEvent.Post} 上，两边都会被叫到，
     * 所以第一行的 {@code instanceof ServerPlayer} 既做了类型转换、也当了「只在服务端跑」的门。
     * <p>
     * 没有心跳、没有到期时刻、没有冷却——「在潜行」这件事服务端本来就看得见，每 tick 现查现算即可。
     */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isShiftKeyDown() || player.isSpectator() || !player.isAlive()) {
            return;
        }
        if (!player.hasEffect(ModEffects.FLAME_BREATH)) {
            return;
        }
        breathe(player.serverLevel(), player);
    }

    /** 这一 tick 的火：从嘴边算出那束火的圆锥，结算锥里的实体，再从嘴边喷粒子 */
    private static void breathe(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);

        // 方块截断走眼睛那条射线（瞄得准），只借它的长度给圆锥封顶：撞墙即止，火不该穿墙去烧后面的东西
        Vec3 from = player.getEyePosition().add(look.scale(ORIGIN_FORWARD));
        Vec3 to = from.add(look.scale(RANGE));
        BlockHitResult hit = level.clip(new ClipContext(from, to,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 end = hit.getType() == HitResult.Type.MISS ? to : hit.getLocation();

        Vec3 mouth = mouthOf(player, look);
        double reach = from.distanceTo(end);
        applyToEntities(level, player, mouth, look, reach);
        applyToTransportedItems(level, from, look, reach);
        spawnParticles(level, mouth, look);
    }

    /**
     * 出火口：眼睛往下压到嘴的高度、再往前挪出脸外面。
     * <p>
     * 判定锥的锥尖和粒子的出生点都是这里——同一处，两边的范围才对得上。
     */
    private static Vec3 mouthOf(ServerPlayer player, Vec3 look) {
        return player.getEyePosition()
                .add(0.0, -MOUTH_DROP, 0.0)
                .add(look.scale(MOUTH_FORWARD));
    }

    /**
     * 结算锥里的实体。
     * <p>
     * 先用罩得住整个圆锥的盒子粗筛（{@code getEntities} 传了施法者，他已被排除），
     * 再逐个做锥内判定——盒子是个立方体，光靠粗筛会把角上离火束老远的实体也捞进来。
     * <p>
     * 两路都是 Create 高炉那一套：掉落物走熔炼加工（<b>托在传送带 / 置物台上的那些不在这里</b>，
     * 它们由 {@link #applyToTransportedItems} 另外处理），其它实体交给 {@code affectEntity}
     * （它自带伤害与点燃，见类注释），本类不再自己补刀。
     */
    private static void applyToEntities(ServerLevel level, ServerPlayer caster, Vec3 mouth, Vec3 look, double reach) {
        // 锥底半径 = 射程 × tan(半角)，再留一格余量给粗筛本身的粗糙
        AABB search = new AABB(mouth, mouth.add(look.scale(RANGE)))
                .inflate(RANGE * CONE_TAN + 1.0);
        for (Entity entity : level.getEntities(caster, search, EntitySelector.NO_SPECTATORS)) {
            if (!entity.isAlive() || entity.isRemoved()) {
                continue;
            }
            if (!insideCone(entity, mouth, look, reach)) {
                continue;
            }

            if (entity instanceof ItemEntity item) {
                // 每 tick 一次，与真鼓风机同节奏：加工计时按 tick 递减，攒满才出成品
                if (FanProcessing.canProcess(item, AllFanProcessingTypes.BLASTING)) {
                    FanProcessing.applyProcessing(item, AllFanProcessingTypes.BLASTING);
                }
                continue;
            }
            AllFanProcessingTypes.BLASTING.affectEntity(entity, level);
        }
    }

    /**
     * 让火束扫过的<b>传送带 / 置物台</b>上的物品也照常加工，效果与它们待在真的鼓风机气流里一模一样。
     * <p>
     * 这两样东西都用 Create 的 {@link TransportedItemStackHandlerBehaviour} 托着物品
     * （原版那边由 {@code AirCurrent#tickAffectedHandlers} 驱动），所以这里的做法就是「找到这个行为、
     * 把同一套 {@link FanProcessing#applyProcessing} 喂给它」——连加工计时、批量系数都走 Create 自己的
     * 配置（{@code fanProcessingTime}），不需要另立一套。
     * <p>
     * <b>找法照抄 Create 的 {@code findAffectedHandlers}</b>：沿轴心一格一格地查，而不是扫整个锥体。
     * 真气流本来就是一根一格的柱子，沿轴查既是它的原样，也省得每 tick 去翻几十格方块。
     * 区别只在取样密度——它一格一查，这里半格一查（斜着喷时不会漏掉底下的传送带）。
     * <p>
     * <b>还要看轴心那一格的<u>正下方</u></b>：传送带与置物台常贴着气流下沿（Create 那边对水平方向
     * 也是这么补一格），漏了这格就会出现「明火燎着带子、东西却纹丝不动」。
     */
    private static void applyToTransportedItems(ServerLevel level, Vec3 from, Vec3 look, double reach) {
        // 半格一取样时同一格会被重复命中，用一个 long 集合去重，
        // 否则一 tick 里同一个物品的加工计时会掉两格
        LongSet visited = new LongOpenHashSet();
        double length = Math.min(RANGE, reach);
        for (double traveled = 0.0; traveled <= length; traveled += BEAM_SAMPLE_STEP) {
            BlockPos center = BlockPos.containing(from.add(look.scale(traveled)));
            for (int down = 0; down <= 1; down++) {
                BlockPos pos = down == 0 ? center : center.below();
                if (!visited.add(pos.asLong())) {
                    continue;
                }
                TransportedItemStackHandlerBehaviour behaviour =
                        BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
                if (behaviour != null) {
                    behaviour.handleProcessingOnAllItems(transported ->
                            FanProcessing.applyProcessing(transported, level, AllFanProcessingTypes.BLASTING));
                }
            }
        }
    }

    /**
     * 实体是否落在那束火里。
     * <p>
     * 算法就是「点到圆锥」：先看它沿轴走了多远（{@code along}），再看它偏离轴多少（{@code perpendicular}），
     * 圆锥在那个距离上的半径是 {@code along × tan(半角)}，加上 {@link #CONE_CORE} 的锥尖余量、
     * 再加上实体自身半个宽度——所以个子大的更容易被扫到，和原版打实体时用碰撞箱一个道理。
     * <p>
     * 沿轴的上限取「射程」与「被方块截断后的长度」里的较小者，于是墙后面的实体进不来。
     */
    private static boolean insideCone(Entity entity, Vec3 mouth, Vec3 look, double reach) {
        double halfWidth = entity.getBbWidth() * 0.5;
        Vec3 delta = entity.getBoundingBox().getCenter().subtract(mouth);
        double along = delta.dot(look);
        if (along < -CONE_CORE || along > Math.min(RANGE, reach) + halfWidth) {
            return false;
        }
        double perpendicular = delta.subtract(look.scale(along)).length();
        double allowed = CONE_CORE + Math.max(along, 0.0) * CONE_TAN + halfWidth;
        return perpendicular <= allowed;
    }

    /**
     * 从嘴边喷这一 tick 的粒子。
     * <p>
     * 逐颗发（而不是用 {@code sendParticles} 的 {@code count} 一次发一把）：同一个 {@code count} 里的
     * 每颗粒子拿到的是<b>同一份数据</b>，会朝完全相同的方向飞，凑不出扩散；要一颗一个方向，
     * 就只能一颗一个包。代价明账——每 tick {@value #PARTICLES_PER_TICK} 个包，只发给 32 格内的玩家，
     * 且只在按住潜行的这段时间里发。
     */
    private static void spawnParticles(ServerLevel level, Vec3 mouth, Vec3 look) {
        RandomSource random = level.random;

        for (int i = 0; i < PARTICLES_PER_TICK; i++) {
            Vec3 direction = coneDirection(look, random);
            double speed = PARTICLE_SPEED * (1.0 + (random.nextDouble() * 2.0 - 1.0) * SPEED_VARIANCE);
            level.sendParticles(FlameFlowParticleData.of(direction.scale(speed), pickColor(random)),
                    mouth.x, mouth.y, mouth.z, 1,
                    MUZZLE_JITTER, MUZZLE_JITTER, MUZZLE_JITTER, 0.0);
        }

        if (level.getGameTime() % SOUND_INTERVAL == 0) {
            level.playSound(null, mouth.x, mouth.y, mouth.z, SoundEvents.FIRE_AMBIENT,
                    SoundSource.PLAYERS, 0.3F, 0.9F + random.nextFloat() * 0.2F);
        }
    }

    /**
     * 在绕 {@code look} 的一个圆锥里随机取一个单位方向。
     * <p>
     * 先在垂直于 {@code look} 的平面上铺一组基（side / up），再在半径 {@code tan(半角)} 的圆盘上取点——
     * 用 {@code sqrt} 是为了让点在圆盘上<b>均匀</b>分布，不然会全挤在中心，看起来还是条细线。
     */
    private static Vec3 coneDirection(Vec3 look, RandomSource random) {
        Vec3 side = look.cross(new Vec3(0.0, 1.0, 0.0));
        if (side.lengthSqr() < 1.0E-6) {
            // look 正好竖直朝上/朝下时上面的叉乘退化成零向量，换一根轴
            side = look.cross(new Vec3(1.0, 0.0, 0.0));
        }
        side = side.normalize();
        Vec3 up = side.cross(look).normalize();

        double radius = Math.tan(Math.toRadians(CONE_DEGREES)) * Math.sqrt(random.nextDouble());
        double phi = random.nextDouble() * Mth.TWO_PI;
        return look.add(side.scale(Math.cos(phi) * radius))
                .add(up.scale(Math.sin(phi) * radius))
                .normalize();
    }

    /**
     * 随机挑一档颜色：多数是橙色气流，其次是亮黄火星，少量暗灰的烟。
     * <p>
     * 比例是随手定的观感参数——想让火更「实」就把火星那一段的比例调大。
     */
    private static int pickColor(RandomSource random) {
        float roll = random.nextFloat();
        if (roll < 0.2F) {
            return SMOKE_ARGB;
        }
        if (roll < 0.5F) {
            return EMBER_ARGB;
        }
        // Create 的 Color.mixColors(0xFF4400, 0xFF8855, t)，各通道线性插值
        return (FLOW_ALPHA << 24) | mixRgb(0xFF4400, 0xFF8855, random.nextFloat());
    }

    /** 两个 RGB 之间按 t 线性插值 */
    private static int mixRgb(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return r << 16 | g << 8 | b;
    }

}
