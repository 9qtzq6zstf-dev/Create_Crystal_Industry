package com.minecart.yunxian.budding;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.block.budding.GenericBuddingBlock;
import com.minecart.yunxian.block.budding.ScriptedBuddingBlock;
import com.minecart.yunxian.budding.BuddingFamily.BlockConversion;
import com.minecart.yunxian.budding.BuddingFamily.Replacement;
import com.minecart.yunxian.config.ModConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 母岩的随机刻副作用——<b>「侵染 / 转化周围方块」的解析与执行</b>，自带家族与脚本母岩共用。
 * <p>
 * 规则写在 {@link GrowthDefinition#conversions()} 里：自带家族由家族表声明
 * （{@code BuddingFamilies} 的 {@code Growth.conversions}），脚本母岩由
 * {@code CustomBuddingOptions#transform} 追加，{@code CustomBudding.modify} 也能往已有的母岩上追加。
 * 两类方块各自在自己的 {@code randomTick} 里调 {@link #run}。
 * <p>
 * <b>判定顺序与随机数消耗顺序是对外契约</b>：每条规则先掷概率、再取方向，与历史实现逐位一致；
 * 改顺序会改变所有母岩的手感，也会让附属模组的行为跟着变。
 * <p>
 * 「把方块变成母岩」这一类（也就是<em>侵染/再生传播</em>）另外受配置的两个开关管：
 * {@code infection.buddingInfection} 与 {@code infection.infectingBudding}（见 {@link #infectionAllowed}）。
 * 判定看的是<b>产物</b>是不是母岩方块，所以家族表里的再生规则与脚本写的
 * {@code .transform('粗铁块', '我的母岩')} 一视同仁；「石头 → 铁矿」那种普通转化不受影响。
 */
public final class BuddingConversions {

    private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.budding");

    /**
     * 母岩「再生传播」的标准概率基数与半径：自带家族表与脚本的 {@code .transform} 都用它。
     * <p>
     * 1/25000 是刻意稀有的——它是"母岩会自己变多"的唯一途径，太快会让矿脉无限增殖。
     */
    public static final int INFECTION_CHANCE = 25_000;
    public static final int INFECTION_RADIUS = 1;

    private BuddingConversions() {
    }

    /** 一条已解析的规则 */
    public record Prepared(int chance, int radius, boolean energyGated, List<Target> targets) {
    }

    /** 一条已解析的替换：匹配即写入 {@code output}（"替换为本母岩自身"已在解析时展开） */
    public record Target(Predicate<BlockState> matches, BlockState output) {
    }

    /**
     * 解析一份规则表：把 Supplier 里的方块取出来、把标签建成谓词。
     * <p>
     * 首次随机刻（或首次读护目镜/JEI）时注册表已冻结，所以那时解析才准；
     * 解析不开的条目<b>禁用并记一条错误</b>，绝不让它拖垮整块母岩。
     *
     * @param ownerId 母岩的标识（家族 id 或方块 id），只用于日志
     * @param owner   母岩方块本身：{@code output} 为 null 的替换按"换成它自己"展开
     */
    public static List<Prepared> prepare(String ownerId, Block owner, List<BlockConversion> conversions) {
        List<Prepared> prepared = new ArrayList<>(conversions.size());

        for (BlockConversion conversion : conversions) {
            List<Target> targets = new ArrayList<>(conversion.replacements().size());
            for (Replacement replacement : conversion.replacements()) {
                Predicate<BlockState> matches = matcher(ownerId, replacement);
                // output 为 null 表示“替换为本母岩自身”
                BlockState output = replacement.output() == null
                        ? owner.defaultBlockState()
                        : resolveState(ownerId, replacement.output(), "输出方块");
                if (output == null) {
                    continue; // 解析失败：该条替换禁用
                }
                targets.add(new Target(matches, output));
            }
            prepared.add(new Prepared(conversion.chance(), conversion.radius(),
                    conversion.energyGated(), List.copyOf(targets)));
        }
        return List.copyOf(prepared);
    }

    /**
     * 跑一轮随机刻副作用：每条规则各消耗一次随机数，即使最终没有可替换的目标
     * （顺序契约见类注释）。规则表为空时一次随机数都不消耗。
     *
     * @param gate             付费钩子（{@code energyGated} 的规则要付费），null = 免费
     * @param infectionAllowed 这块母岩现在还能不能"把方块变成母岩"（配置开关，见 {@link #infectionAllowed}）
     */
    public static void run(ServerLevel level, BlockPos pos, RandomSource random, List<Prepared> prepared,
                           @Nullable BuddingGrowthEngine.GrowthGate gate, boolean infectionAllowed) {
        for (Prepared conversion : prepared) {
            // 每条规则各消耗一次随机数，即使最终没有可替换的目标
            if (random.nextInt(conversion.chance()) != 0) {
                continue;
            }
            apply(level, pos, random, conversion, gate, infectionAllowed);
        }
    }

    private static void apply(ServerLevel level, BlockPos pos, RandomSource random, Prepared conversion,
                              @Nullable BuddingGrowthEngine.GrowthGate gate, boolean infectionAllowed) {
        int radius = conversion.radius();
        BlockPos targetPos = pos.offset(
                random.nextInt(2 * radius + 1) - radius,
                random.nextInt(2 * radius + 1) - radius,
                random.nextInt(2 * radius + 1) - radius);
        if (targetPos.equals(pos)) {
            return;
        }

        BlockState targetState = level.getBlockState(targetPos);
        for (Target target : conversion.targets()) {
            if (!target.matches().test(targetState)) {
                continue;
            }
            // "把这个方块变成母岩"＝侵染/再生传播：配置关了就不做（继续看下一条替换，
            // 所以一条规则里混着普通转化与再生时，普通那条照样生效）
            if (!infectionAllowed && isBuddingBlock(target.output())) {
                continue;
            }
            if (conversion.energyGated() && gate != null && !gate.canGrow(level, pos)) {
                return;
            }
            level.setBlockAndUpdate(targetPos, target.output());
            // 矿石母岩侵蚀出矿石 / 回响母岩蔓延出幽匿 / 粗铁块被传染成新的母岩
            // ——这一条也是"母岩会自己扩张"的关键
            YunxianAdvancements.awardNear(level, pos, target.output().is(Blocks.SCULK)
                    ? YunxianAdvancements.DEEP_SCULK_SPREAD
                    : YunxianAdvancements.BUDDING_MOTHERLODE);
            return;
        }
    }

    /**
     * 配置里"这块母岩允不允许侵染周围方块"：
     * <ul>
     *   <li>{@code infection.buddingInfection} 关掉 = 谁都不能（总开关）；</li>
     *   <li>{@code infection.infectingBudding} 写空 = 按各家族出厂设置（都随家族表）；
     *       写了名单 = <b>只有</b>名单里的允许。</li>
     * </ul>
     * 名单里可以写<b>母岩家族 id</b>（{@code raw_iron}、{@code quartz}）或<b>方块 id</b>
     * （{@code kubejs:my_crystal_budding}——脚本注册的母岩得用这一种写法），
     * 正好对应 {@code ownerId} 的两种取值。
     */
    public static boolean infectionAllowed(String ownerId) {
        if (!ModConfig.Common.buddingInfectionEnabled()) {
            return false;
        }
        List<? extends String> allowed = ModConfig.Common.infectingBudding();
        return allowed.isEmpty() || allowed.contains(ownerId);
    }

    /** 产物是不是母岩方块：是 → 这条替换属于"侵染/再生"，归配置管 */
    private static boolean isBuddingBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof GenericBuddingBlock || block instanceof ScriptedBuddingBlock;
    }

    private static Predicate<BlockState> matcher(String ownerId, Replacement replacement) {
        if (replacement.input() != null) {
            Block input = resolveBlock(ownerId, replacement.input(), "输入方块");
            // 解析失败（方块不存在）时永不匹配，绝不把空气当匹配目标
            return input == null ? state -> false : state -> state.is(input);
        }
        if (replacement.inputTag() != null) {
            TagKey<Block> tag = replacement.inputTag();
            return state -> state.is(tag);
        }
        LOGGER.error("[Budding] 母岩 {} 的转化规则既没有输入方块也没有输入标签，已禁用", ownerId);
        return state -> false;
    }

    @Nullable
    private static BlockState resolveState(String ownerId, Supplier<Block> supplier, String description) {
        Block block = resolveBlock(ownerId, supplier, description);
        return block == null ? null : block.defaultBlockState();
    }

    /**
     * 解析目标方块。首次随机刻时注册表已冻结，查到的才是真实方块；
     * 解析失败时记一次错误并禁用该规则。
     */
    @Nullable
    private static Block resolveBlock(String ownerId, Supplier<Block> supplier, String description) {
        Block block = supplier.get();
        if (block == null || block == Blocks.AIR) {
            LOGGER.error("[Budding] 母岩 {} 未能解析{}——资源位置写错或该方块不存在，相关规则已禁用",
                    ownerId, description);
            return null;
        }
        return block;
    }

    /**
     * 脚本给的方块 id → 延迟解析的 Supplier：脚本执行时方块还没入表，只能等到真正要用时再查
     * （那时注册表已冻结）。查不到会由 {@link #resolveBlock} 记错误并禁用这条规则。
     */
    public static Supplier<Block> blockSupplier(ResourceLocation id) {
        return () -> BuiltInRegistries.BLOCK.get(id);
    }
}
