package com.minecart.yunxian.budding;

import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.minecart.yunxian.block.budding.GenericBuddingBlock;
import com.minecart.yunxian.block.budding.ScriptedBuddingBlock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 已有母岩的<b>生长定义覆盖表</b>：脚本用 {@code CustomBudding.modify(母岩 id, options)} 写这里，
 * 方块在解析自己的生长定义时读这里（见 {@code GenericBuddingBlock#growthDefinition} 与
 * {@code ScriptedBuddingBlock#growthDefinition}）。
 * <p>
 * 只覆盖「生长参数」——概率、光照上下限、含水要求、生长环境、流体需求。这些正好是
 * {@link GrowthDefinition} 里除"四个阶段方块"之外的字段，所以覆盖不碰方块本身，
 * 换定义也不会影响存档里的方块状态。外观、破坏音效、破坏工具、翻译名在方块注册时就写进
 * {@code Block.Properties} 与资源里了，改不了，脚本里写了由 {@code CustomBudding} 当场报错。
 * <p>
 * 与其它覆盖表（掉落见 {@code ScriptedBlockDrops}、创造栏见 {@code ModCreativeTabs}）一样按
 * <b>方块 id</b> 索引、随时可写：脚本跑在方块注册事件里，那时方块对象还没建出来，
 * 只有 id 是确定的。读取方每次都靠 {@link #revision()} 判断表有没有变过（随机刻是热路径，
 * 不能每 tick 重建定义），所以写表必须是原子的：
 * {@code ConcurrentHashMap} + {@code volatile} 的版本号，照 {@code ScriptedBlockDrops} 的模板。
 * <p>
 * <b>客户端也要能加载本类</b>：签名里不出现 KubeJS / JEI 类型——客户端没有 KubeJS 时，
 * 护目镜与 JEI 仍然要读到覆盖后的定义。脚本在两端各跑一遍，所以两端看到的是同一份表；
 * 专用服务器的脚本与客户端不一致时，显示会与实际行为不符（仅显示层面）。
 */
public final class BuddingOverrides {

    private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.budding.override");

    private BuddingOverrides() {
    }

    /**
     * 一条覆盖：<b>只记脚本显式点名的字段</b>，其余保持方块现状（合并语义）。
     * <p>
     * 字段全为 {@code null} = 那一项没被点名。多次 {@code modify} 同一个 id 时按调用顺序逐字段叠加
     * （见 {@link Override#merge}）。
     */
    public record Override(
            /** 概率基数 n：每次随机刻 1/n 推进一级；null = 不改 */
            @Nullable Integer chance,
            /** 生长位亮度上限（0–15）；null = 不改。-1 = 显式改成"不限制" */
            @Nullable Integer maxLight,
            /** 生长位亮度下限（0–15）；null = 不改。-1 = 显式改成"不限制" */
            @Nullable Integer minLight,
            /** 是否要求目标格含水；null = 不改 */
            @Nullable Boolean requiresWater,
            /**
             * 生长维度名单；null = 不改（沿用方块现状）。<b>空列表 = 显式清空这一边</b>（维度不限）。
             * <p>
             * 与 {@link #biomeEntries()} 各管一边、互不影响：只写维度就只换维度，群系条件照旧
             * （反之亦然）。想两边都清掉就两个都写空（{@code .growthDimensions().growthBiomes()}）。
             */
            @Nullable List<ResourceKey<Level>> dimensions,
            /** 生长群系条件（原样保留脚本写的字符串，写法见 {@link GrowthEnvironment#of}）；null = 不改 */
            @Nullable List<String> biomeEntries,
            /** 自己地盘之外的生长概率（0–1）；null = 不改 */
            @Nullable Double outsideGrowthChance,
            /** 新的流体需求（罐参数见 {@link FluidRequirement}）；null = 不改 */
            @Nullable FluidRequirement fluid,
            /**
             * 显式取消流体需求（脚本写 {@code .needfluid('none')}）。
             * 与 {@code fluid == null} 不是一回事：那个是"没提这一项"，这个是"把原有的收费关掉"——
             * 远古残骸母岩那种家族自带的熔岩付费就是靠它关掉的（见 {@code GenericBuddingBlock#payGrowthEnergy}）。
             */
            boolean clearFluid,
            /** 脚本追加的转化规则（{@code CustomBuddingOptions#transform}）；空表 = 没写 */
            List<BuddingFamily.BlockConversion> extraConversions) {

        /** 这一项是不是空覆盖（一个字段都没点名）——空覆盖不必进表 */
        public boolean isEmpty() {
            return chance == null && maxLight == null && minLight == null && requiresWater == null
                    && dimensions == null && biomeEntries == null && outsideGrowthChance == null
                    && fluid == null && !clearFluid && extraConversions.isEmpty();
        }

        /**
         * 逐字段叠加：{@code later} 点过名的字段赢，其余保留 {@code this} 的（= 早先那次 modify 的设置）。
         * <p>
         * 流体那一项按"后一次说了算"合并：后一次写了新需求就用新的，写了 {@code 'none'} 就是取消，
         * 没提才留着先前那次设的——免得出现"既要某个流体、又要取消"的自相矛盾。
         */
        Override merge(Override later) {
            FluidRequirement mergedFluid = fluid;
            boolean mergedClear = clearFluid;
            if (later.fluid != null) {
                mergedFluid = later.fluid;
                mergedClear = false;
            } else if (later.clearFluid) {
                mergedFluid = null;
                mergedClear = true;
            }
            return new Override(
                    pick(later.chance, chance),
                    pick(later.maxLight, maxLight),
                    pick(later.minLight, minLight),
                    pick(later.requiresWater, requiresWater),
                    pick(later.dimensions, dimensions),
                    pick(later.biomeEntries, biomeEntries),
                    pick(later.outsideGrowthChance, outsideGrowthChance),
                    mergedFluid,
                    mergedClear,
                    concat(extraConversions, later.extraConversions));
        }

        /**
         * 把这条覆盖套到方块原本的定义上：只重建点过名的字段（含流体需求），
         * 四个阶段方块一律沿用 {@code base} 的——那是不该被生长参数改动碰到的东西。
         * <p>
         * 合并后才可能暴露的矛盾（脚本只给了光照一端、而它与方块原有的另一端对不上）<b>只让光照那一对
         * 退回原有设置</b>，同一次 {@code modify} 里的其它项照常生效——写错一个亮度不该让概率、流体
         * 一起失效。环境那一块单独兜一层异常，理由同上。
         */
        public GrowthDefinition apply(GrowthDefinition base) {
            int newChance = chance != null ? chance : base.chance();
            OptionalInt newMax = maxLight != null ? GrowthDefinition.lightBound(maxLight) : base.maxLight();
            OptionalInt newMin = minLight != null ? GrowthDefinition.lightBound(minLight) : base.minLight();
            if (newMin.isPresent() && newMax.isPresent() && newMin.getAsInt() > newMax.getAsInt()) {
                LOGGER.error("[Budding] 光照覆盖（下限 {} / 上限 {}）与方块原有的定义矛盾，"
                                + "光照这一对已退回原有设置；同一次 modify 的其它项照常生效",
                        newMin.getAsInt(), newMax.getAsInt());
                newMax = base.maxLight();
                newMin = base.minLight();
            }
            FluidRequirement newFluid = clearFluid ? null : (fluid != null ? fluid : base.fluid());

            GrowthEnvironment newEnvironment;
            try {
                newEnvironment = environment(base.growthEnvironment());
            } catch (RuntimeException e) {
                LOGGER.error("[Budding] 生长环境的覆盖与方块原有的定义合不上，这一块已退回原有设置：{}",
                        e.getMessage());
                newEnvironment = base.growthEnvironment();
            }

            return new GrowthDefinition(base.smallBud(), base.mediumBud(), base.largeBud(), base.cluster(),
                    newChance, newMax, newMin,
                    requiresWater != null ? requiresWater : base.requiresWater(),
                    newEnvironment, newFluid, base.conversions())
                    // 脚本写的转发规则是**追加**在方块原有规则之后的：modify 的定位是"给它加特性"，
                    // 不该把家族表里的矿石转化顶掉（要去掉自带的再生，用配置里的感染开关/名单）
                    .withConversions(extraConversions);
        }

        /**
         * 生长环境的合并规则：<b>维度、群系、地盘外概率各管各的</b>，
         * 只写哪一项就只换哪一项，其余沿用方块原有的——与 {@code modify} 的"没写到的项保持现状"一致。
         * <ul>
         *   <li>写了维度 → 换成脚本给的名单；没写 → 沿用原有的。<b>空数组 = 显式清空维度</b>；</li>
         *   <li>群系同理（{@code null} = 沿用，空数组 = 清空）；</li>
         *   <li>地盘外概率：写了就换，没写沿用原有的；<b>原来的环境本来就不限地方</b>（
         *       {@link GrowthEnvironment#ANY}）时，它本来就没有意义，保持"不限制"；</li>
         *   <li>三项都没写 → 原样返回。</li>
         * </ul>
         * 群系条件要在<b>这里</b>才重建（脚本给的是字符串，写法见 {@link GrowthEnvironment#of}）；
         * 那一步可能因为 id 写错而抛，所以调用方 {@link BuddingOverrides#apply} 会兜住。
         */
        private GrowthEnvironment environment(GrowthEnvironment base) {
            if (dimensions == null && biomeEntries == null) {
                return outsideGrowthChance == null
                        ? base
                        : new GrowthEnvironment(base.dimensions(), base.biomeConditions(), outsideGrowthChance);
            }
            List<ResourceKey<Level>> newDimensions = dimensions != null ? dimensions : base.dimensions();
            double outside = outsideGrowthChance != null
                    ? outsideGrowthChance
                    : base.outsideGrowthChance();
            if (biomeEntries == null) {
                return new GrowthEnvironment(newDimensions, base.biomeConditions(), outside);
            }
            return GrowthEnvironment.of(outside, newDimensions, biomeEntries.toArray(String[]::new));
        }

        /** 转化规则是累加的：两次 modify 各写一条，两条都要在 */
        private static List<BuddingFamily.BlockConversion> concat(List<BuddingFamily.BlockConversion> first,
                                                                  List<BuddingFamily.BlockConversion> second) {
            if (second.isEmpty()) {
                return first;
            }
            List<BuddingFamily.BlockConversion> merged = new java.util.ArrayList<>(first);
            merged.addAll(second);
            return List.copyOf(merged);
        }

        private static <T> T pick(@Nullable T later, T earlier) {
            return later != null ? later : earlier;
        }
    }

    /** 方块 id → 覆盖。只在启动期写（脚本），运行期只读 */
    private static final Map<ResourceLocation, Override> OVERRIDES = new ConcurrentHashMap<>();

    /** 表改过几次——方块的生长定义按它缓存（见 {@link #revision()}） */
    private static volatile int revision;

    /**
     * 被 {@code modify} 点过名的母岩 id：<b>含只改掉落、开采等级的那些</b>
     * （那几项进的是各自的表，但目标写错了同样只会静默不生效）。
     * 注册完之后由 {@link #verifyTargets()} 逐个核一遍。
     */
    private static final Set<ResourceLocation> TARGETS = ConcurrentHashMap.newKeySet();

    /** 记下一个被脚本改过的母岩 id（{@code CustomBudding.modify} 开头调用） */
    public static void declareTarget(ResourceLocation blockId) {
        TARGETS.add(blockId);
    }


    /**
     * 登记（或叠加）一条覆盖。
     * <p>
     * 同一个 id 多次调用按调用顺序叠加：后一次只改它自己点过名的字段。
     * 空覆盖直接忽略，但还是会把版本号推一格——免得脚本写了个空 {@code modify} 之后再改别的东西，
     * 方块那边因为版本号没变而用着旧定义。
     */
    public static void modify(ResourceLocation blockId, Override override) {
        if (!override.isEmpty()) {
            OVERRIDES.merge(blockId, override, (existing, later) -> existing.merge(later));
        }
        revision++;
    }

    /** 表当前的版本号：方块用它当生长定义的缓存键（热路径上只多读一个 volatile） */
    public static int revision() {
        return revision;
    }

    /** 这块方块有没有被脚本覆盖过（JEI 与护目镜用它决定要不要按"被改过的"来显示） */
    public static boolean hasOverride(Block block) {
        return OVERRIDES.containsKey(BuiltInRegistries.BLOCK.getKey(block));
    }

    /**
     * 把覆盖套到方块原本的定义上；没覆盖过就原样返回 {@code base}。
     * <p>
     * 合并出来的定义不合法时（脚本只给了一端光照、又与方块原本的另一端矛盾）记一条错、
     * 退回出厂定义：这里是随机刻链路的一部分，<b>绝不能抛</b>——抛一次就是每 tick 崩一次服务端。
     * 现象因此是"脚本那条没生效 + 日志里一条错"，而不是崩服。
     */
    public static GrowthDefinition apply(Block block, GrowthDefinition base) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        Override override = OVERRIDES.get(id);
        if (override == null) {
            return base;
        }
        try {
            return override.apply(base);
        } catch (RuntimeException e) {
            LOGGER.error("[Budding] 母岩 {} 的生长覆盖与它原有的定义合不上，这一块已退回出厂定义：{}",
                    id, e.getMessage());
            return base;
        }
    }

    /**
     * 校验一遍所有覆盖的目标：方块注册完之后调一次（见 {@code Yunxian} 的构造器）。
     * <p>
     * 脚本写 {@code modify} 时方块还没注册，查不到目标，所以拼错的 id 只能在注册完之后才抓得出来。
     * 只记警告、不改行为：写错 id 的后果是"脚本那条无声无息地没生效"，比什么都不说难查得多。
     */
    public static void verifyTargets() {
        TARGETS.forEach(id -> {
            Block block = BuiltInRegistries.BLOCK.get(id);
            if (!(block instanceof GenericBuddingBlock) && !(block instanceof ScriptedBuddingBlock)) {
                LOGGER.warn("[Budding] modify 的目标 {} 不是本模组驱动的母岩方块（方块不存在、"
                        + "或者它的生长不由本模组的引擎负责），脚本对它的改动都不会生效", id);
            }
        });
    }
}
