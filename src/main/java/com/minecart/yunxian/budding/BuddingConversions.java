package com.minecart.yunxian.budding;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import com.minecart.yunxian.advancement.YunxianAdvancements;
import com.minecart.yunxian.block.budding.GenericBuddingBlock;
import com.minecart.yunxian.block.budding.ScriptedBuddingBlock;
import com.minecart.yunxian.config.ModConfig;
import com.minecart.yunxian.recipe.BuddingConversionRecipe;
import com.minecart.yunxian.registry.ModRecipes;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 母岩的随机刻副作用——<b>「侵染」的执行器</b>：每随机刻按配方掷骰，把相邻的方块 A 变成 B。
 * <p>
 * 规则是<b>配方</b>（{@link BuddingConversionRecipe}，数据包可写）：家族表声明的那些由
 * {@code ModRecipeProvider} 生成成 JSON，脚本用 {@code .transform(...)} 加的走 KubeJS 的配方通道，
 * 整合包可以直接覆盖同一个配方 id。母岩自己只管在 {@code randomTick} 里调 {@link #run}。
 * <p>
 * <b>判定顺序与随机数消耗顺序是对外契约</b>：每条规则先掷一次概率、再取三连位置，与历史实现逐位一致；
 * 改顺序会改变所有母岩的手感，也会让附属模组的行为跟着变。规则顺序因此<b>按配方 id 排序</b>固定下来
 * ——配方管理器返回的顺序不保证稳定，而同一个存档两次会话必须跑出同一套随机数消耗。
 * <p>
 * 配置统一管着<b>全部</b>转化（不分"石头→矿石"与"变成母岩"两类）：总开关关掉、或这块母岩不在名单里，
 * 它就什么都不做——随机数照旧消耗，只是不写方块（见 {@link #allowed}）。
 */
public final class BuddingConversions {

    private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.budding");

    /**
     * 母岩「再生传播」的标准概率基数与半径：家族表与脚本的 {@code .transform} 都用它。
     * <p>
     * 1/25000 是刻意稀有的——它是"母岩会自己变多"的唯一途径，太快会让矿脉无限增殖。
     */
    public static final int INFECTION_CHANCE = 25_000;
    public static final int INFECTION_RADIUS = 1;

    /** 配方换过一批（进世界、数据包重载）——按配方缓存规则表的一方靠它失效 */
    private static volatile int recipeRevision;

    private BuddingConversions() {
    }

    /** 配方（重新）加载完了：缓存要重建。挂在数据包同步事件上，见 {@code Yunxian} 的注册处 */
    public static void onRecipesReloaded() {
        recipeRevision++;
    }

    /** 配方表的版本号：方块按它判断自己缓存的规则表还新不新（热路径上只多读一个 volatile） */
    public static int recipeRevision() {
        return recipeRevision;
    }

    /** 一条已解析的规则 */
    public record Prepared(int chance, int radius, boolean energyGated, List<Target> targets) {
    }

    /** 一条已解析的替换：匹配即写入 {@code output}（"替换为本母岩自身"已在解析时展开） */
    public record Target(Predicate<BlockState> matches, BlockState output) {
    }

    /**
     * 取一块母岩的转化规则并解析成可执行的形态。
     * <p>
     * 规则来自配方管理器（{@link ModRecipes#BUDDING_CONVERSION}），只认 {@code budding} 指向这块方块的
     * 配方；<b>按配方 id 排序</b>保证顺序确定（配方管理器自己的返回顺序不保证稳定，而随机数消耗顺序
     * 是对外契约）。输入是 {@link HolderSet}（具体方块、方块列表、标签都收），产物为空表示
     * "变成本母岩自身"，这里展开成它的默认方块状态。
     * <p>
     * 空输入集合（标签没加载出来）会记一条警告：那条替换永远匹配不上，与旧实现"解析失败即禁用该条"
     * 的表现一致，只是这里是配方那边的问题。
     */
    public static List<Prepared> prepare(ServerLevel level, Block owner) {
        List<RecipeHolder<BuddingConversionRecipe>> holders = new ArrayList<>();
        for (RecipeHolder<BuddingConversionRecipe> holder
                : level.getRecipeManager().getAllRecipesFor(ModRecipes.BUDDING_CONVERSION.get())) {
            if (holder.value().budding() == owner) {
                holders.add(holder);
            }
        }
        holders.sort(Comparator.comparing(holder -> holder.id().toString()));

        List<Prepared> prepared = new ArrayList<>(holders.size());
        for (RecipeHolder<BuddingConversionRecipe> holder : holders) {
            BuddingConversionRecipe recipe = holder.value();
            List<Target> targets = new ArrayList<>(recipe.replacements().size());
            for (BuddingConversionRecipe.Replacement replacement : recipe.replacements()) {
                HolderSet<Block> input = replacement.input();
                if (input.size() == 0) {
                    LOGGER.warn("[Budding] 配方 {} 有一条替换的输入是空集合（标签没加载出来？），这一条永不匹配",
                            holder.id());
                }
                BlockState output = replacement.output()
                        .map(Block::defaultBlockState)
                        .orElseGet(owner::defaultBlockState);
                targets.add(new Target(
                        state -> input.contains(state.getBlock().builtInRegistryHolder()), output));
            }
            prepared.add(new Prepared(recipe.chance(), recipe.radius(), recipe.gated(), List.copyOf(targets)));
        }
        return List.copyOf(prepared);
    }

    /**
     * 跑一轮随机刻副作用：每条规则各消耗一次随机数，即使最终没有可替换的目标
     * （顺序契约见类注释）。规则表为空时一次随机数都不消耗。
     *
     * @param gate    付费钩子（{@code gated} 的规则要付费），null = 免费
     * @param allowed 这块母岩现在允不允许转化（配置开关，见 {@link #allowed}）
     */
    public static void run(ServerLevel level, BlockPos pos, RandomSource random, List<Prepared> prepared,
                           @Nullable BuddingGrowthEngine.GrowthGate gate, boolean allowed) {
        for (Prepared conversion : prepared) {
            // 每条规则各消耗一次随机数，即使最终没有可替换的目标
            if (random.nextInt(conversion.chance()) != 0) {
                continue;
            }
            apply(level, pos, random, conversion, gate, allowed);
        }
    }

    private static void apply(ServerLevel level, BlockPos pos, RandomSource random, Prepared conversion,
                              @Nullable BuddingGrowthEngine.GrowthGate gate, boolean allowed) {
        int radius = conversion.radius();
        BlockPos targetPos = pos.offset(
                random.nextInt(2 * radius + 1) - radius,
                random.nextInt(2 * radius + 1) - radius,
                random.nextInt(2 * radius + 1) - radius);
        if (targetPos.equals(pos)) {
            return;
        }
        // 配置停掉了这块母岩：骰子照掷、随机数照耗（顺序契约），但一个字都不写
        if (!allowed) {
            return;
        }

        BlockState targetState = level.getBlockState(targetPos);
        for (Target target : conversion.targets()) {
            if (!target.matches().test(targetState)) {
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
     *   <li>{@code infection.buddingInfection} 关掉 = 谁都不能（总开关，管<b>全部</b>转化）；</li>
     *   <li>{@code infection.infectingBudding} 写空 = 所有母岩都允许；写了名单 = <b>只有</b>名单里的允许。</li>
     * </ul>
     * 名单里可以写<b>母岩家族 id</b>（{@code raw_iron}、{@code quartz}）或<b>方块 id</b>
     * （{@code kubejs:my_crystal_budding}——脚本注册的母岩得用这一种写法），见 {@link #ownerIdOf(Block)}。
     */
    public static boolean allowed(Block budding) {
        return configAllows(ownerIdOf(budding));
    }

    /** 同上，只是按配置里认的"母岩标识"判定——JEI 页手上只有这个标识，没有方块对象 */
    public static boolean configAllows(String ownerId) {
        if (!ModConfig.Common.buddingInfectionEnabled()) {
            return false;
        }
        List<? extends String> list = ModConfig.Common.infectingBudding();
        return list.isEmpty() || list.contains(ownerId);
    }

    /**
     * 这块方块会不会跑本模组的转化引擎——只有这两种方块类会在自己的 {@code randomTick} 里调
     * {@link #run}。判断"一条 budding_conversion 配方到底能不能生效"就靠它：配方里 {@code budding}
     * 写了别的方块（黑曜石之类）照样能解码与载入，但没人会触发它。
     */
    public static boolean drivesConversions(Block block) {
        return block instanceof GenericBuddingBlock || block instanceof ScriptedBuddingBlock;
    }

    /**
     * 配置里认的"母岩标识"：家族母岩用家族 id，其余（脚本注册的、外部声明的）用方块 id。
     * <p>
     * 取值必须与两个方块类传给运行时的那个一致——{@code GenericBuddingBlock} 用的是
     * {@code family.id()}，{@code ScriptedBuddingBlock} 用的是方块 id。
     */
    public static String ownerIdOf(Block block) {
        return block instanceof GenericBuddingBlock generic
                ? generic.family().id()
                : BuiltInRegistries.BLOCK.getKey(block).toString();
    }
}
