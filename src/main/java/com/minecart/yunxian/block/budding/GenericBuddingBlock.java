package com.minecart.yunxian.block.budding;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.Supplier;

import com.minecart.yunxian.blockentity.budding.BuddingGrowthBlockEntity;
import com.minecart.yunxian.blockentity.budding.EchoConvertingBuddingBlockEntity;
import com.minecart.yunxian.blockentity.budding.ArclightBuddingBlockEntity;
import com.minecart.yunxian.blockentity.budding.FlammableIceBuddingBlockEntity;
import com.minecart.yunxian.blockentity.budding.FluidTankBuddingBlockEntity;
import com.minecart.yunxian.budding.BuddingFamily;
import com.minecart.yunxian.budding.BuddingFamily.EnergyRequirement;
import com.minecart.yunxian.budding.BuddingFamily.GrowthRule;
import com.minecart.yunxian.budding.BuddingFamily.LightRequirement;
import com.minecart.yunxian.budding.BuddingConversions;
import com.minecart.yunxian.budding.BuddingGrowthEngine;
import com.minecart.yunxian.budding.BuddingOverrides;
import com.minecart.yunxian.budding.FluidRequirement;
import com.minecart.yunxian.budding.GrowthDefinition;
import com.minecart.yunxian.effect.ArclightSource;
import com.minecart.yunxian.integration.ae2.AE2Budding;
import com.minecart.yunxian.registry.ModBlockEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 母岩方块：把家族定义合成一个 {@link GrowthDefinition}，
 * 生长本身交给公开的 {@link BuddingGrowthEngine}（附属模组与 KubeJS 脚本用的是同一个入口）。
 * <p>
 * 各家族的全部差异（光照门槛、含水要求、充能要求、方块转化、芽/簇方块、方块实体）
 * 都写在 {@code budding/BuddingFamilies} 那一张表里，本类不含任何家族特例；
 * 随机刻副作用（转化/传播）与生长能量留在本类，因为它们是家族特有的。
 * <p>
 * 唯一保留的子类是 {@link EchoConvertingBuddingBlock}：它的 {@code CAN_SUMMON} 状态
 * 必须在构造器里注册，而 {@code BlockBehaviour} 的构造器会先调用
 * {@code createBlockStateDefinition}（此时子类字段尚未赋值），无法从 family 读取。
 */
public class GenericBuddingBlock extends BuddingAmethystBlock implements EntityBlock, FluidTankBudding {

    protected final BuddingFamily family;
    protected final Block smallBud;
    protected final Block mediumBud;
    protected final Block largeBud;
    protected final Block cluster;

    /**
     * 生长（及付费转化）的付费钩子。
     * <p>
     * 无条件挂上：家族层面的付费（AE / FE / 熔岩罐）看 {@link EnergyRequirement} 与家族表，
     * 而流体那一份是<b>定义</b>里的东西、脚本随时能用 {@code CustomBudding.modify} 加上去——
     * 静态判断"这个家族要不要付费"会漏掉后加的那些。免费家族这里只是白跑一次调用（返回 true），
     * 而它只在概率判定通过、真要放方块之前才被调，代价可以忽略。
     */
    private final BuddingGrowthEngine.GrowthGate energyGate = this::payGrowthCost;

    /**
     * 转化规则表缓存：首次随机刻解析一次（那时配方管理器已经就绪），之后只跟着配方表走。
     * <p>
     * 配方会随数据包重载换一批（脚本用 {@code .transform} 加的也走配方），所以缓存记着当时那份
     * {@link BuddingConversions#recipeRevision()}，对不上就重建。
     */
    @Nullable
    private volatile List<BuddingConversions.Prepared> preparedConversions;
    /** 缓存对应的 {@link BuddingConversions#recipeRevision()} */
    private volatile int preparedRevision = -1;

    // 生长定义缓存：脚本改过覆盖（版本号变了）才重建。
    // 两个字段都 volatile、且**先写定义再写版本号**：护目镜与 JEI 在客户端线程读、随机刻在服务端线程读，
    // 写反了会让人读到「新版本号 + 旧定义」，而且因为版本号相等，那份旧定义再也不会被重建
    @Nullable
    private volatile GrowthDefinition cachedDefinition;
    /** 缓存对应的 {@link BuddingOverrides#revision()}；脚本改过覆盖就会变 */
    private volatile int cachedRevision = -1;

    public GenericBuddingBlock(BuddingFamily family, Properties properties, Block smallBud, Block mediumBud,
                               Block largeBud, Block cluster) {
        super(properties);
        this.family = family;
        this.smallBud = smallBud;
        this.mediumBud = mediumBud;
        this.largeBud = largeBud;
        this.cluster = cluster;
    }

    public BuddingFamily family() {
        return family;
    }

    /**
     * 四个生长阶段的方块，顺序：小芽 → 中芽 → 大芽 → 晶簇（与 {@link GrowthDefinition#stages()} 一致）。
     * <p>
     * 供外部读取本方块会往哪四个方块长——附属模组用本类建自己的母岩时，
     * JEI 的母岩信息页就是靠它渲染出整套芽与晶簇的（我们拿不到对方私有的四个字段）。
     */
    public List<Block> stages() {
        return List.of(smallBud, mediumBud, largeBud, cluster);
    }

    /**
     * 方块实体：<b>定义里有流体需求就用通用罐</b>（脚本与本模组的远古残骸共用同一个），
     * 否则按家族表给的种类选（多数是纯展示用的共享 BE）。
     * <p>
     * 罐这一项不看家族表，是因为脚本能用 {@code CustomBudding.modify} 给任何一块母岩加罐或取消罐，
     * 而定义是唯一知道当前状态的地方；两边一旦对不上，区块重载时方块实体会被
     * {@code BlockEntityType#isValid} 丢掉（脚本加罐 / 取消罐时都会补登记合法方块表，
     * 见 {@code CustomBudding#modify}）。
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (fluidRequirement() != null) {
            return new FluidTankBuddingBlockEntity(pos, state);
        }
        return switch (family.appearance().blockEntity()) {
            case SHARED_GROWTH -> new BuddingGrowthBlockEntity(pos, state);
            case ECHO_DISPLAY -> new EchoConvertingBuddingBlockEntity(pos, state);
            case ICE_DISPLAY -> new FlammableIceBuddingBlockEntity(pos, state);
            case FE_TANK -> new ArclightBuddingBlockEntity(pos, state);
            case AE2_GRID -> newFluixBlockEntity(pos, state);
        };
    }

    /** 本母岩当前生效的流体需求（含脚本覆盖）；不烧流体时 {@code null}——见 {@link FluidTankBudding} */
    @Nullable
    @Override
    public FluidRequirement fluidRequirement() {
        return growthDefinition().fluid();
    }

    /**
     * 手持流体容器右键：能装就装、能舀就舀，只认罐体自己声明能收的流体。
     * 具体过程与「倒不进去时为什么不能落回原版」都写在 {@link FluidTankInteraction} 里，与脚本母岩共用同一段。
     * <p>
     * 没罐的母岩连能力查询都不做——右键照旧（拿桶往旁边倒液体仍然照原版来）。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (fluidRequirement() != null) {
            ItemInteractionResult tank = FluidTankInteraction.tryUse(stack, level, pos, player, hand, hitResult);
            if (tank != null) {
                return tank;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    /**
     * 比较器只认「自称有模拟输出」的方块（{@code ComparatorBlock#getInputSignal} 先问这一个方法）。
     * <p>
     * 只有带罐的母岩才算：读液位要现查方块实体，没罐的母岩恒定输出 0，没必要让比较器白跑
     * （判定走的是缓存过的定义，每个比较器 tick 一次，成本可忽略）。
     */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return fluidRequirement() != null;
    }

    /** 比较器读液位；没罐的母岩返回 0。算法见 {@code FluidTankBuddingBlockEntity#comparatorSignal} */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return FluidTankBuddingBlockEntity.comparatorSignal(level, pos);
    }

    /**
     * 福鲁伊克斯母岩的 BE 由 AE2 联动模块注册（{@code ModBlockEntities.FLUIX_BUDDING}）。
     * AE2 缺席时该类型为 null，而那时连方块本身都不会注册，理论上不会走到这里。
     */
    private static BlockEntity newFluixBlockEntity(BlockPos pos, BlockState state) {
        Supplier<BlockEntityType<?>> type = ModBlockEntities.FLUIX_BUDDING;
        return type == null ? null : type.get().create(pos, state);
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return family.growth().buddingSignal() > 0;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return family.growth().buddingSignal();
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        GrowthDefinition definition = growthDefinition();
        BuddingGrowthEngine.tryGrow(level, pos, random, definition, energyGate);
        // 随机刻副作用的随机数消耗顺序与历史实现一致：先生长那一轮，再逐条转化规则
        BuddingConversions.run(level, pos, random, prepared(level), energyGate,
                BuddingConversions.allowed(this));
    }

    /** 取（并缓存）本母岩的侵染规则表：配方随数据包重载换过一批就重建 */
    private List<BuddingConversions.Prepared> prepared(ServerLevel level) {
        int revision = BuddingConversions.recipeRevision();
        List<BuddingConversions.Prepared> cached = preparedConversions;
        if (cached != null && preparedRevision == revision) {
            return cached;
        }
        List<BuddingConversions.Prepared> prepared = BuddingConversions.prepare(level, this);
        preparedConversions = prepared;
        // 先写表再写版本号（两个都是 volatile）：反了会让别的线程读到"新版本号 + 旧表"，
        // 那份旧表因为版本号相等再也不会被重建
        preparedRevision = revision;
        return prepared;
    }

    /**
     * 母岩周围的电火花（目前只有弧光石家族，见 {@code Appearance#sparkParticles()}）。
     * 走原版的 {@code animateTick}：只在客户端、只对玩家附近的方块随机调用，
     * 所以这里不需要判断距离，服务端也一次都不会执行。
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (family.appearance().sparkParticles()) {
            ArclightSource.maybeSpark(level, pos, random);
        }
    }

    // ==================== 生长定义 ====================

    /**
     * 本方块当前生效的生长定义：家族表定义的出厂值，套上脚本的覆盖（如果有）。
     * <p>
     * 按 {@link BuddingOverrides#revision()} 缓存：随机刻是热路径，不能每 tick 重建一个定义，
     * 而脚本的 {@code modify} 会在启动期改表——用版本号当缓存键，改过之后第一次用到就会重建。
     */
    public GrowthDefinition growthDefinition() {
        int revision = BuddingOverrides.revision();
        GrowthDefinition cached = cachedDefinition;
        if (cached != null && cachedRevision == revision) {
            return cached;
        }

        // 光照下限留空：家族表的 LightRequirement 只有"无要求 / 必须低于某个亮度"两种，没有下限
        // 流体需求来自家族表（远古残骸的熔岩罐就是这么声明的）：方块实体、付费钩子、护目镜与
        // 比较器都读定义里的这一项，于是脚本的 modify 能统一地加、改、取消它。
        // 转化（侵染）规则不在这里：它们已经统一成配方，引擎按配方表取用，见 BuddingConversions
        GrowthDefinition built = new GrowthDefinition(smallBud, mediumBud, largeBud, cluster,
                family.growth().speed().chance(),
                familyMaxLight(), OptionalInt.empty(), family.growth().rule() == GrowthRule.SUBMERGED,
                family.growth().growthEnvironment(), family.growth().fluid());
        GrowthDefinition resolved = BuddingOverrides.apply(this, built);
        cachedDefinition = resolved;
        cachedRevision = revision;
        return resolved;
    }

    /** 家族的光照要求换算成"允许的最大亮度"：{@code below(t)} 即亮度 ≤ t-1，不限光时为空 */
    private OptionalInt familyMaxLight() {
        LightRequirement light = family.growth().light();
        return light.kind() == LightRequirement.Kind.BELOW
                ? OptionalInt.of(light.threshold() - 1)
                : OptionalInt.empty();
    }

    /**
     * 生长位是否满足光照要求（默认无要求，回响母岩要求亮度 0）。
     * <p>
     * 客户端也会调它（回响母岩的护目镜状态），所以判定只依赖客户端也拿得到的东西：家族定义。
     */
    protected boolean canGrowAtLight(Level level, BlockPos neighborPos) {
        return BuddingGrowthEngine.lightAllows(level, neighborPos, growthDefinition());
    }

    /**
     * 生长的付费钩子：真正放置下一阶段前调用，返回 false 表示本次放弃生长（随机刻与标了
     * {@code gated()} 的付费转化都走这里）。
     * <p>
     * <b>流体优先</b>：定义里有流体需求（家族表声明或脚本 {@code modify} 加的都算）就先问罐子，
     * 不够就不长——脚本用 {@code .needfluid('none')} 取消之后这一项自然消失，家族自带的
     * 收费也就跟着关掉了（定义是唯一知道当前状态的地方，见 {@code BuddingOverrides}）。
     * 剩下的按 {@link EnergyRequirement} 走：AE2 那条路必须隔离（理由见 {@code AE2Budding} 的类注释）。
     */
    protected boolean payGrowthCost(ServerLevel level, BlockPos pos) {
        if (growthDefinition().fluid() != null) {
            return FluidTankBuddingBlockEntity.consumeGrowthCost(level, pos);
        }
        return switch (family.growth().energy()) {
            case FREE -> true;
            case AE2_GRID -> AE2Budding.tryConsumeGrowthEnergy(level, pos);
            case FE -> level.getBlockEntity(pos) instanceof ArclightBuddingBlockEntity cell
                    && cell.tryConsumeGrowthCost();
        };
    }

}
