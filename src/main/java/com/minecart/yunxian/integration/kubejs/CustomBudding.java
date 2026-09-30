package com.minecart.yunxian.integration.kubejs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.block.budding.ScriptedBuddingBlock;
import com.minecart.yunxian.block.budding.YunxianClusterBlock;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.budding.BuddingFamilies.Stage;
import com.minecart.yunxian.budding.BuddingFamily.BlockEntityKind;
import com.minecart.yunxian.budding.BuddingFamily.LightRequirement;
import com.minecart.yunxian.budding.BuddingGrowthEngine;
import com.minecart.yunxian.budding.BuddingOverrides;
import com.minecart.yunxian.budding.BuddingRegistration;
import com.minecart.yunxian.budding.FluidRequirement;
import com.minecart.yunxian.budding.GrowthDefinition;
import com.minecart.yunxian.budding.GrowthEnvironment;
import com.minecart.yunxian.registry.ModCreativeTabs;
import com.minecart.yunxian.registry.ScriptedBlockDrops;
import com.minecart.yunxian.registry.ScriptedMiningLevels;

import dev.latvian.mods.kubejs.block.BlockBuilder;
import dev.latvian.mods.kubejs.block.BlockRenderType;
import dev.latvian.mods.kubejs.block.custom.BasicKubeBlock;
import dev.latvian.mods.kubejs.client.ModelGenerator;
import dev.latvian.mods.kubejs.client.VariantBlockStateGenerator;
import dev.latvian.mods.kubejs.registry.RegistryKubeEvent;
import dev.latvian.mods.kubejs.script.SourceLine;
import dev.latvian.mods.kubejs.util.ID;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 给 KubeJS 脚本用的一行注册：<b>注册一个母岩，自动带出整套五样方块</b>
 * （母岩本体 + 小芽 + 中芽 + 大芽 + 晶簇）。
 * <p>
 * 脚本里就是这么用（`CustomBudding` 由 {@link YunxianKubeJSPlugin} 绑定，无需 {@code Java.loadClass}）：
 * <pre>{@code
 * CustomBudding.create(event, 'my_crystal', 20)          // 一行：概率 1/20
 * // 或带选项（逐字段赋值、链式两种写法等价，见 Options）：
 * CustomBudding.create(event, 'my_crystal', new CustomBuddingOptions()
 *     .chance(20)
 *     .maxLight(7)
 *     .requiresWater()
 *     .growthDimensions('minecraft:overworld')  // 可多选；与下面那行取交集
 *     .growthBiomes('warm')                     // 群系 id / '#标签' / 关键字 cold·warm·hot，前面加 ! 是否定
 *     .outsideGrowthChance(0.1)                 // 出了自己的地盘只剩一成概率还在长
 *     .needfluid('minecraft:lava', 250, 1000))  // 每次生长扣 250 mB 熔岩，罐最多 1 B（见 Options#needfluid）
 * }</pre>
 * 生成 {@code <命名空间>:<id>_budding} 与 {@code _small_bud} / {@code _medium_bud} / {@code _large_bud} / {@code _cluster}；
 * 母岩的随机刻直接接到本模组的生长引擎上，芽/簇带 {@code FACING} 属性（引擎会写朝向）。
 * <p>
 * 默认贴图借用原版紫水晶那一套（零资源即可跑通），要自己的外观就改
 * {@link Options#buddingTexture} / {@link Options#stageTextures}，或者用资源包。
 * 音效、开采工具与开采等级同样默认照抄原版紫水晶（紫水晶音效、镐、不设等级），要换就改
 * {@link Options#buddingSound} / {@link Options#stageSound} / {@link Options#buddingTool} /
 * {@link Options#stageTool} / {@link Options#buddingLevel} / {@link Options#stageLevel}。
 */
public final class CustomBudding {

    private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.kubejs");

    /** 四个阶段的后缀，顺序：小芽 → 中芽 → 大芽 → 晶簇 */
    private static final String[] STAGE_KEYS = {"small_bud", "medium_bud", "large_bud", "cluster"};

    /** 默认贴图（原版紫水晶那套），包作者可用 Options 覆盖 */
    private static final String[] DEFAULT_STAGE_TEXTURES = {
            "minecraft:block/small_amethyst_bud",
            "minecraft:block/medium_amethyst_bud",
            "minecraft:block/large_amethyst_bud",
            "minecraft:block/amethyst_cluster"};
    private static final String DEFAULT_BUDDING_TEXTURE = "minecraft:block/budding_amethyst";

    /** 物品模型：平面图标（与本模组自带芽/簇的物品模型一致） */
    private static final ResourceLocation ITEM_GENERATED = ResourceLocation.withDefaultNamespace("item/generated");

    /** 通用母岩标签（与本模组自带母岩一致：智能钻头精准采集、AE2 晶体催生器都读它） */
    private static final ResourceLocation BUDDING_BLOCKS_TAG = ResourceLocation.fromNamespaceAndPath("c", "budding_blocks");

    /** 通用芽标签：三档芽进这个（方块 + 物品） */
    private static final ResourceLocation BUDS_TAG = ResourceLocation.fromNamespaceAndPath("c", "buds");

    /** 通用晶簇标签：四阶段里的终态进这个（方块 + 物品） */
    private static final ResourceLocation CLUSTERS_TAG = ResourceLocation.fromNamespaceAndPath("c", "clusters");

    /**
     * 认识的裸工具名——就是原版挖掘标签 {@code minecraft:mineable/<名字>} 的全部四个
     * （1.21.1 的 {@code BlockTags} 里只有这四个 {@code MINEABLE_WITH_*}，没有"剪刀""剑"那两个标签）。
     * <p>
     * 写别的裸名字直接报错：拼错了只会安静地登记一个没人读的标签，症状是「设了工具但挖起来还是慢」，
     * 比当场报错难查得多。要指向别的标签（如 {@code minecraft:sword_efficient}）就写完整 id，见 {@link #mineableTag}。
     */
    private static final List<String> VANILLA_TOOLS = List.of("pickaxe", "axe", "shovel", "hoe");

    /**
     * 认识的裸等级名——原版三档挖掘等级标签 {@code minecraft:needs_<名字>_tool}，与本模组自带的
     * {@code BuddingFamily.ToolTier} 一一对应。
     * <p>
     * 写别的裸名字直接报错（理由同 {@link #VANILLA_TOOLS}）。原版没有 wood/gold 档，NeoForge 的
     * {@code neoforge:needs_netherite_tool} 也不在这里——要指向那些就写完整 id，见 {@link #levelTag}。
     */
    private static final List<String> VANILLA_LEVELS = List.of("stone", "iron", "diamond");

    /** 等级写成这个（不区分大小写）= 不设等级，与写 {@code null} 等价；脚本里比 {@code null} 好读 */
    private static final String NO_LEVEL = "none";

    /**
     * 流体写成这个 = <b>显式取消</b>已有的流体需求（与 {@link #NO_LEVEL} 同一个写法，见 {@code Options#fluid}）。
     * 与"没写这一项"不是一回事：那个是 {@code null}。
     */
    private static final String NO_FLUID = "none";

    /**
     * 阶段方块继承的原版模型 id（与上面的默认贴图同名，但语义是"模型"）：
     * 借它们才拿到芽/簇该有的 cross 几何与尺寸，而不是一个整块立方体。
     */
    private static final String[] STAGE_MODELS = {
            "minecraft:block/small_amethyst_bud",
            "minecraft:block/medium_amethyst_bud",
            "minecraft:block/large_amethyst_bud",
            "minecraft:block/amethyst_cluster"};

    private CustomBudding() {
    }

    /** 最常用的一行：注册一整族，概率基数 n（每次随机刻 1/n） */
    public static Family create(RegistryKubeEvent<Block> event, String id, int chance) {
        Options options = new Options();
        options.chance = chance;
        return create(event, id, options);
    }

    /**
     * 带选项的版本；选项在调用时读取一次（之后改 Options 对象不会影响已注册的方块）。
     *
     * @param id 家族 id，可带命名空间（{@code "mypack:my_crystal"}），不带则落在 {@code kubejs} 命名空间
     */
    public static Family create(RegistryKubeEvent<Block> event, String id, Options options) {
        ResourceLocation base = parse(id);
        String namespace = base.getNamespace();
        // 开采工具与开采等级都在这儿先解析成标签：名字写错了要立刻报错，而不是等一族方块注册到一半才抛
        @Nullable ResourceLocation buddingToolTag = mineableTag(options.buddingTool);
        @Nullable ResourceLocation stageToolTag = mineableTag(options.stageTool);
        @Nullable ResourceLocation buddingLevelTag = levelTag(options.buddingLevel);
        @Nullable ResourceLocation stageLevelTag = levelTag(options.stageLevel);
        // 流体需求同理先解析：流体 id 写错、或者在方块注册时流体表还没建好，都要当场报出来。
        // 脚本跑到这里时流体已经全部注册完（注册事件按原版注册表顺序派发，流体在方块之前），
        // 所以这里查得到别的模组的流体，连脚本自己注册的都行
        @Nullable FluidRequirement fluid = fluidRequirement(options);

        // 四个阶段方块：建成模组自己的簇方块（见 StageBuilder），朝向形状、支撑判定、音效都是原版行为
        ResourceLocation[] stages = new ResourceLocation[STAGE_KEYS.length];
        for (int i = 0; i < STAGE_KEYS.length; i++) {
            ResourceLocation stageId = ResourceLocation.fromNamespaceAndPath(namespace, base.getPath() + "_" + STAGE_KEYS[i]);
            stages[i] = stageId;

            // 形状/朝向/支撑判定都由簇方块自己管（FACING、WATERLOGGED 是它自带的属性），
            // 所以这里不再额外 .property(FACING) / .noCollision()，只补音效与渲染类型：
            // cross 模型有大量透明像素，必须 cutout，否则空白处会渲染成黑色
            BlockBuilder builder = new StageBuilder(stageId, Stage.values()[i], options.stageTextures[i],
                    ResourceLocation.parse(STAGE_MODELS[i]));
            builder.sourceLine = SourceLine.UNKNOWN;
            builder.soundType(options.stageSound);
            builder.renderType(BlockRenderType.CUTOUT);
            // 名字：不指定就交给 KubeJS 按 id 自动命名（snake_case 转英文标题，写进 en_us 虚拟语言文件）；
            // 方块物品与方块共用同一个语言键（BlockItemBuilder#getTranslationKeyGroup 返回 "block"），
            // 所以这里设一次，背包/掉落物/创造栏一起变
            String stageName = stageDisplayName(options, i);
            if (stageName != null) {
                builder.displayName(Component.literal(stageName));
            }
            // 只挂方块标签：挖掘工具与开采等级读的都是方块标签，顺带挂到物品上是没用的空标签
            tagTool(builder, stageToolTag);
            tagLevel(builder, stageLevelTag);
            // 通用芽 / 晶簇标签：三档芽进 c:buds、晶簇进 c:clusters，方块与物品两侧都要挂
            // （与母岩本体自动进 c:budding_blocks 同一套做法）
            boolean cluster = Stage.values()[i] == Stage.CLUSTER;
            ResourceLocation stageCommonTag = cluster ? CLUSTERS_TAG : BUDS_TAG;
            builder.tagBlock(new ResourceLocation[]{stageCommonTag});
            forceItem(builder, stageCommonTag);
            registerBlock(event, builder);

            // 掉落规则交给运行时：精准采集掉本体，否则只有晶簇掉 Options.dropItem × dropCount（默认什么都不掉）。
            // KubeJS 的掉落 API 表达不了精准采集，覆写 generateLootTable() 又没人调用，见 ScriptedBlockDrops
            ScriptedBlockDrops.register(stageId, cluster ? itemId(options.dropItem) : null,
                    cluster ? dropCount(options) : 1);
        }

        // 母岩本体：用模组自己的方块类（它自带随机刻 → 生长引擎，且带共享展示 BE，护目镜才显示信息）
        ResourceLocation buddingId = ResourceLocation.fromNamespaceAndPath(namespace, base.getPath() + "_budding");
        LazyDefinition definition = new LazyDefinition(stages, options, fluid);
        BlockBuilder budding = new MotherBuilder(buddingId, definition);
        budding.sourceLine = SourceLine.UNKNOWN;
        budding.texture(options.buddingTexture);
        budding.soundType(options.buddingSound);
        // 母岩本体：进通用母岩标签（方块），物品标签在下面 forceItem 里补；开采工具与等级用母岩自己那份
        budding.tagBlock(new ResourceLocation[]{BUDDING_BLOCKS_TAG});
        tagTool(budding, buddingToolTag);
        tagLevel(budding, buddingLevelTag);
        if (options.displayName != null) {
            budding.displayName(Component.literal(options.displayName));
        }
        forceItem(budding, BUDDING_BLOCKS_TAG);
        registerBlock(event, budding);

        // 护目镜信息挂在方块实体上，而方块实体类型是在方块之后注册的，所以这里按 id 先声明。
        // 配了流体需求的母岩要用能存流体的那个 BE（护目镜信息由它自己补），
        // 于是两边只能登记一边——登记的是哪一边，方块实体注册时就建哪个类型
        if (fluid != null) {
            BuddingRegistration.declareFluidBuddingBlock(buddingId);
        } else {
            BuddingRegistration.declareBuddingBlock(buddingId);
        }

        // 母岩：普通破坏/普通采集什么都不掉（与原版紫水晶母岩一致），精准采集才掉本体——
        // 所以智能钻头的普通模式拿不到母岩，精准模式才拿得到（它读 c:budding_blocks 直接掉本体）
        ScriptedBlockDrops.register(buddingId, null, 1);

        // 创造栏：整族五个物品一起登记（延后到标签页构建时注入）
        registerTabEntries(options, buddingId, stages[0], stages[1], stages[2], stages[3]);

        LOGGER.info("[KubeJS] 已注册母岩家族 {}：{} / {} / {} / {} / {}（创造栏：{}）",
                base, buddingId, stages[0], stages[1], stages[2], stages[3], options.group);

        return new Family(buddingId, stages[0], stages[1], stages[2], stages[3]);
    }

    /**
     * 改一块<b>已经注册好的</b>母岩（自带的、别的脚本注册的、附属模组的都行）：
     * 参数与 {@link #create} 共用同一个 {@link Options}，但语义是<b>合并</b>——
     * 只改脚本显式写了的那几项，其余保持方块现状。
     * <pre>{@code
     * CustomBudding.modify('create_crystal_industry:ancient_debris_budding', new CustomBuddingOptions()
     *     .chance(20)                       // 极慢档（1/50）改快一点
     *     .growthDimensions('minecraft:overworld')   // 顺便解掉"只在下界满速"
     *     .needfluid('none'))               // 不再扣熔岩
     * }</pre>
     * <b>只能改"特性"</b>：概率、光照上下限、含水、生长维度/群系、地盘外概率、流体需求、掉落、开采等级。
     * 材质、破坏音效、破坏工具、翻译名与创造栏归属属于方块的「身份」，注册时就定下来、{@code modify}
     * 一概不管——脚本里写了会当场报错（要换这些就 {@code create} 一块新的）。
     * <p>
     * 与 {@link #create} 一样<b>必须写在启动脚本里</b>：它改的是注册期定下来的东西，
     * 而且 JEI 的母岩信息页只在启动时构建一次。
     * <p>
     * id 写母岩方块的 id（{@code create_crystal_industry:raw_iron_budding}）；末尾的
     * {@code _budding} 可省（不写会自动补上），裸 id 落在 {@code kubejs} 命名空间（与 {@code create} 一致）。
     * 目标方块不存在、或者不是本模组引擎驱动的母岩时，启动日志里会有一条警告——
     * 这里<b>不能</b>当场校验（脚本跑在方块注册事件里，那时别的模组方块还没入表）。
     */
    public static void modify(String id, Options options) {
        ResourceLocation blockId = buddingBlockId(parse(id));
        rejectBakedOptions(options);
        // 能当场查出来的矛盾在这儿就报（写错的脚本立刻在 KubeJS 日志里看到），
        // 只有"与方块原有值合不上"那种才留到运行时兜底（那时只能在日志里报错 + 退回出厂定义）
        validateGrowthValues(blockId, options);

        @Nullable FluidRequirement requirement = fluidRequirement(options);
        boolean clearsFluid = NO_FLUID.equals(fluidName(options));
        rejectFluidOnEnergyFamily(blockId, requirement);

        // 生长定义那一层：概率 / 光照 / 含水 / 环境 / 流体
        BuddingOverrides.declareTarget(blockId);
        BuddingOverrides.modify(blockId, new BuddingOverrides.Override(
                options.chance, options.maxLight, options.minLight, options.requiresWater,
                dimensions(options.growthDimensions), biomeEntries(options.growthBiomes),
                options.outsideGrowthChance, requirement, clearsFluid));

        // 流体罐的方块实体类型在注册期就定死了（见 BuddingRegistration），所以"这块有没有罐"
        // 必须在这里登记：加罐的进流体罐那张表；取消的回到共享护目镜那张表。
        // 原来就有罐的仍留在流体罐表里，于是两种实体类型都合法——区块重载时已有的罐不会被判非法丢掉
        if (requirement != null) {
            BuddingRegistration.declareFluidBuddingBlock(blockId);
        } else if (clearsFluid) {
            BuddingRegistration.declareBuddingBlock(blockId);
        }

        // 掉落：与 create 一致，改的是晶簇那一块（掉落规则按方块 id 存在 ScriptedBlockDrops 里，
        // 运行时可写；对自带家族同样生效——那条规则会接管战利品表，JEI 的母岩信息页读的也是它）。
        // 注意 .dropItem('none') 是"什么都不掉"（查不到这个物品，ScriptedBlockDrops 就当没有），
        // 与"没写这一项"（null，保持现状）不是一回事
        if (options.dropItem != null || options.dropCount != null) {
            if (options.dropItem == null) {
                // 只写数量是改不动的：改掉落 = 整条规则接管战利品表，而"原来的物品"在这里读不出来
                // （脚本跑在方块注册事件里，方块还没入表、战利品表自然也没得读）。
                // 与其静默把晶簇变成"什么都不掉"，不如让脚本作者把物品写出来
                throw new IllegalArgumentException("modify 里只写 .dropCount(...) 改不了掉落数量："
                        + "改掉落是整条规则接管战利品表的，得连物品一起写——"
                        + ".dropItem('要掉的物品', 数量)；想让晶簇什么都不掉就写 .dropItem('none')");
            }
            ResourceLocation clusterId = stageId(blockId, "cluster");
            ScriptedBlockDrops.register(clusterId, itemId(options.dropItem), dropCount(options));
        }
        // 开采等级：挂在方块 id 上的运行时判定（见 ScriptedMiningLevels），母岩与四个阶段各一份
        if (options.buddingLevel != null || options.stageLevel != null) {
            if (options.buddingLevel != null) {
                ScriptedMiningLevels.register(blockId, levelTag(options.buddingLevel));
            }
            if (options.stageLevel != null) {
                ResourceLocation stageLevelTag = levelTag(options.stageLevel);
                for (String key : STAGE_KEYS) {
                    ScriptedMiningLevels.register(stageId(blockId, key), stageLevelTag);
                }
            }
        }

        LOGGER.info("[KubeJS] 已修改母岩 {} 的生长参数：{}", blockId, options);
    }

    /**
     * 母岩方块 id 的归一化：末尾的 {@code _budding} 可省。
     * <p>
     * {@code create} 与 {@code BuddingFamilies} 生成的母岩 id 一律是 {@code <前缀>_budding}
     * （见 {@code BuddingFamily#buddingId()}），所以"没写后缀就当它写了"这条规则总能对上；
     * 真有一块不按这个命名来的第三方母岩，会被启动时那次目标检查抓出来。
     */
    private static ResourceLocation buddingBlockId(ResourceLocation given) {
        return given.getPath().endsWith("_budding") ? given : given.withSuffix("_budding");
    }

    /** 由母岩 id 推出同族的阶段方块 id（{@code <前缀>_small_bud} …），与 {@code create} 的命名一致 */
    private static ResourceLocation stageId(ResourceLocation buddingId, String stageKey) {
        String path = buddingId.getPath();
        String prefix = path.substring(0, path.length() - "_budding".length());
        return ResourceLocation.fromNamespaceAndPath(buddingId.getNamespace(), prefix + "_" + stageKey);
    }

    /**
     * 挡住"不属于给方块加特性"的那几项：材质、破坏音效、破坏工具、翻译名，以及创造栏归属。
     * <p>
     * {@code modify} 的定位是<b>给某一块已有母岩添加新特性</b>（概率、光照、含水、环境、流体、
     * 掉落、开采等级），不是改它的身份——外观、名字、进哪一页都是身份，注册期定下来就不动了。
     * <p>
     * 判定办法是跟一份全新的 {@link Options} 比默认值——比同名同值的写法（比如
     * {@code .buddingSound('amethyst')}）会漏过去，但那种写法本来就是"不改变现状"，无害。
     * KubeJS 的 {@code .tool(...)} 一次设两份工具，所以这里也两个一起报。
     */
    private static void rejectBakedOptions(Options options) {
        Options defaults = new Options();
        List<String> baked = new ArrayList<>();
        if (!options.buddingTexture.equals(defaults.buddingTexture)) {
            baked.add("buddingTexture");
        }
        if (!Arrays.equals(options.stageTextures, defaults.stageTextures)) {
            baked.add("stageTextures");
        }
        if (options.buddingSound != defaults.buddingSound) {
            baked.add("buddingSound");
        }
        if (options.stageSound != defaults.stageSound) {
            baked.add("stageSound");
        }
        if (!Objects.equals(options.buddingTool, defaults.buddingTool)) {
            baked.add("buddingTool");
        }
        if (!Objects.equals(options.stageTool, defaults.stageTool)) {
            baked.add("stageTool");
        }
        if (options.displayName != null) {
            baked.add("displayName");
        }
        if (options.stageDisplayNames != null) {
            baked.add("stageDisplayNames");
        }
        if (!Objects.equals(options.group, defaults.group)) {
            baked.add("group");
        }
        if (!baked.isEmpty()) {
            throw new IllegalArgumentException("modify 改不了这些项：" + String.join(" / ", baked)
                    + "——材质、破坏音效、破坏工具、翻译名与创造栏归属都在方块注册时就定下来了，"
                    + "而且它们是方块的「身份」、不是能后加的特性；要换这些请用 CustomBudding.create 注册一块新的母岩");
        }
    }

    /** {@link Options#fluid} 原样取出来，空白当没写 */
    @Nullable
    private static String fluidName(Options options) {
        return options.fluid == null || options.fluid.isBlank() ? null : options.fluid;
    }

    /**
     * 当场校验这一批生长参数：概率必须 ≥ 1，光照上下限不能自相矛盾。
     * <p>
     * 这些 {@link GrowthDefinition} 的紧凑构造器也会查，但它是在<b>随机刻里</b>被构造的——
     * 在那儿抛就是每 tick 崩一次服务端（见 {@code BuddingOverrides#apply} 的兜底）。
     * 所以能在这儿查清的一律在这儿查，报错信息里带上目标方块。
     */
    private static void validateGrowthValues(ResourceLocation blockId, Options options) {
        if (options.chance != null && options.chance < 1) {
            throw new IllegalArgumentException("modify 的概率基数必须 ≥ 1（每次随机刻 1/n 推进一级），收到 "
                    + options.chance);
        }
        // 只给了一端时，另一端可能是方块原有的值——那要等到解析时才知道，这里只查"自己就矛盾"的写法
        if (options.minLight != null && options.maxLight != null && bothPresent(options)
                && options.minLight > options.maxLight) {
            throw new IllegalArgumentException("modify 的生长格亮度下限（" + options.minLight
                    + "）不能高于上限（" + options.maxLight + "），否则永远长不出来");
        }
        if (options.outsideGrowthChance != null
                && !(options.outsideGrowthChance >= 0 && options.outsideGrowthChance <= 1)) {
            throw new IllegalArgumentException("modify 的地盘外生长概率必须是 0 到 1 之间的小数"
                    + "（0 = 完全不长，1 = 不限制），收到 " + options.outsideGrowthChance);
        }
        // 目标写在自带家族身上时，家族原本的光照要求是已知的（家族表在脚本之前就建好了）：
        // 光给下限、而它高于家族的亮度上限（如回响母岩的"必须全黑"）也当场拦掉
        RegisteredFamily family = familyOf(blockId);
        if (family != null && options.minLight != null
                && family.spec().growth().light().kind() == LightRequirement.Kind.BELOW
                && options.minLight > family.spec().growth().light().threshold() - 1) {
            throw new IllegalArgumentException("modify 的生长格亮度下限（" + options.minLight + "）与 "
                    + blockId + " 自带的光照要求冲突：这个家族要求亮度低于 "
                    + family.spec().growth().light().threshold() + " 才生长，两者不可能同时满足");
        }
    }

    /** 两个光照端点是不是都"有效"（负数 = 那一端不限制，不算有效） */
    private static boolean bothPresent(Options options) {
        return options.minLight >= 0 && options.maxLight >= 0;
    }

    /**
     * 带 AE 网格（福鲁伊克斯）或 FE（弧光石）付费的家族不能再加流体罐。
     * <p>
     * 两块方块实体只能留一个：换了通用罐，AE 网格节点 / FE 储罐就没了，而付费钩子又是"流体优先"的
     * ——结果是这两条付费被静默跳过、方块白嫖。所以这种写法直接报错，而不是让它悄悄变便宜。
     * （{@code needfluid('none')} 不受影响：那只是关掉一条本来就不存在的流体要求。）
     */
    private static void rejectFluidOnEnergyFamily(ResourceLocation blockId, @Nullable FluidRequirement requirement) {
        if (requirement == null) {
            return;
        }
        RegisteredFamily family = familyOf(blockId);
        if (family == null) {
            return;
        }
        BlockEntityKind kind = family.spec().appearance().blockEntity();
        if (kind == BlockEntityKind.AE2_GRID || kind == BlockEntityKind.FE_TANK) {
            throw new IllegalArgumentException("modify 不能给 " + blockId + " 加流体需求：它的方块实体要持"
                    + (kind == BlockEntityKind.AE2_GRID ? " ME 网格节点（AE 付费）" : " FE 储罐（电费）")
                    + "，换成流体罐之后就付不了费了。要在它身上加流体消耗，请用 CustomBudding.create 注册一块新的母岩");
        }
    }

    /**
     * 目标方块属于哪个自带家族；不是自带家族（脚本注册的、附属模组的）就返回 {@code null}。
     * <p>
     * 按<b>完整 id</b> 精确匹配（本模组的命名空间 + 家族表算出来的 {@code <家族 id>_budding}）：
     * 家族表在脚本执行之前已经建好了，所以这里查得到。不能只比路径——脚本完全可以注册一块
     * {@code kubejs:arclight_budding}，那跟自带的弧光石家族没有半点关系，
     * 按路径比会把它的流体需求也一并拒掉。
     */
    @Nullable
    private static RegisteredFamily familyOf(ResourceLocation blockId) {
        return BuddingFamilies.ALL.stream()
                .filter(family -> blockId.equals(
                        ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, family.spec().buddingId())))
                .findFirst()
                .orElse(null);
    }

    /** {@link Options#dropCount} 补上出厂默认值（没写就是 1） */
    private static int dropCount(Options options) {
        return options.dropCount != null ? options.dropCount : Options.DEFAULT_DROP_COUNT;
    }

    /**
     * 脚本给的流体需求 → {@link FluidRequirement}。
     * <p>
     * 与工具/等级一样在调用时解析：流体 id 写错要立刻报错，而不是等第一次随机刻。
     * {@code null} 与 {@code "none"} 都返回 {@code null}（前者是"没写"，后者是"取消"，
     * 覆盖表那边分得清，见 {@code BuddingOverrides.Override#clearFluid}）。
     */
    @Nullable
    private static FluidRequirement fluidRequirement(Options options) {
        String fluidOrTag = fluidName(options);
        if (fluidOrTag == null || NO_FLUID.equals(fluidOrTag)) {
            return null;
        }
        int costPerGrowth = options.fluidCostPerGrowth != null
                ? options.fluidCostPerGrowth : Options.DEFAULT_FLUID_COST;
        int tankCapacity = options.fluidCapacity != null
                ? options.fluidCapacity : Options.DEFAULT_FLUID_CAPACITY;
        return FluidRequirement.parse(fluidOrTag, costPerGrowth, tankCapacity);
    }

    /**
     * 脚本给的维度 id 列表：{@code null} = 没写这一项（沿用方块现状），
     * <b>空数组 = 清空这一边</b>（维度不限）。在这里就解析一遍，id 写错当场报错。
     */
    @Nullable
    private static List<ResourceKey<Level>> dimensions(@Nullable String[] ids) {
        if (ids == null) {
            return null;
        }
        List<ResourceKey<Level>> keys = new ArrayList<>(ids.length);
        for (String id : ids) {
            keys.add(GrowthEnvironment.dimension(id));
        }
        return List.copyOf(keys);
    }

    /** 群系条件同理：{@code null} = 没写，空数组 = 清空；字符串在这里先过一遍解析（写错当场报错） */
    @Nullable
    private static List<String> biomeEntries(@Nullable String[] entries) {
        if (entries == null) {
            return null;
        }
        // 解析一次只为校验：真正生效的那次在 BuddingOverrides 合并时做（那时要拼上原有的维度）
        GrowthEnvironment.of(Options.DEFAULT_OUTSIDE_GROWTH_CHANCE, List.of(), entries);
        return List.of(entries);
    }

    /**
     * 明确建一次物品构建器：KubeJS 的方块物品是「按需创建」的，脚本路径（{@code event.create}）
     * 会替它建好，而我们是手工构造 builder，显式调用一次 {@code item(...)} 最稳妥
     * （物品建不出来时，方块存在但 {@code /give} 与创造栏都找不到，很难排查）。
     *
     * @param itemTags 要挂到物品上的通用标签（母岩是 {@code c:budding_blocks}，芽 / 晶簇是
     *                 {@code c:buds} / {@code c:clusters}）；对应的方块标签在调用处用 {@code tagBlock(...)} 加
     *                 <p>
     *                 注意 KubeJS 的 {@code tag(...)} 会<b>同时</b>挂方块与物品标签，
     *                 而挖掘标签只该挂在方块上，所以调用方用的是 {@code tagBlock(...)}。
     */
    private static void forceItem(BlockBuilder builder, ResourceLocation... itemTags) {
        builder.item(item -> {
            for (ResourceLocation tag : itemTags) {
                item.defaultTags.add(tag);
            }
        });
    }

    /**
     * 脚本给的 {@link Options#buddingTool} / {@link Options#stageTool} → 挖掘标签；{@code null} = 不登记（徒手最快）。
     * <p>
     * 裸名字（{@code "hoe"}）走原版的 {@code minecraft:mineable/<名字>}；含 {@code :} 的当完整标签 id
     * 原样使用，所以也能指向其它模组的挖掘标签。前缀 {@code "mineable/"} 可省也可写——README 与文档里
     * 这个标签是写作 {@code #minecraft:mineable/hoe} 的，照抄下来不该报错。
     *
     * @throws IllegalArgumentException 裸名字不在 {@link #VANILLA_TOOLS} 里，或写的不是合法 id
     */
    @Nullable
    private static ResourceLocation mineableTag(@Nullable String tool) {
        if (tool == null) {
            return null;
        }
        String name = tool.trim();
        if (name.indexOf(':') >= 0) {
            ResourceLocation parsed = ResourceLocation.tryParse(name);
            if (parsed == null) {
                throw new IllegalArgumentException("不是合法的方块标签 id：" + tool);
            }
            return parsed;
        }

        String key = name.toLowerCase(Locale.ROOT);
        if (key.startsWith("mineable/")) {
            key = key.substring("mineable/".length());
        }
        if (!VANILLA_TOOLS.contains(key)) {
            throw new IllegalArgumentException("未知的开采工具：" + tool + "（可用："
                    + String.join(" / ", VANILLA_TOOLS) + "，或写完整的方块标签 id，如 \"mymod:mineable/wrench\"）");
        }
        return ResourceLocation.fromNamespaceAndPath("minecraft", "mineable/" + key);
    }

    /** 挂挖掘标签；{@code null} 就什么都不挂。只挂方块标签——挖掘工具读的一律是方块标签 */
    private static void tagTool(BlockBuilder builder, @Nullable ResourceLocation toolTag) {
        if (toolTag != null) {
            builder.tagBlock(new ResourceLocation[]{toolTag});
        }
    }

    /**
     * 脚本给的 {@link Options#buddingLevel} / {@link Options#stageLevel} → 开采等级标签；
     * {@code null}（或写 {@code "none"}）= 不设等级。
     * <p>
     * 裸名字（{@code "stone"}）走原版的 {@code minecraft:needs_<名字>_tool}；含 {@code :} 的当完整标签 id
     * 原样使用，所以也能指向别的标签（如 NeoForge 的 {@code neoforge:needs_netherite_tool}）。前缀
     * {@code "needs_"}、后缀 {@code "_tool"} 可省也可写——照抄标签名下来不该报错。
     * <p>
     * <b>解析出来只是标签，真正让等级生效的是 {@link #tagLevel} 顺手设的 {@code requiresCorrectToolForDrops}</b>。
     *
     * @throws IllegalArgumentException 裸名字不在 {@link #VANILLA_LEVELS} 里（也不是 {@code "none"}），
     *                                  或写的不是合法 id
     */
    @Nullable
    private static ResourceLocation levelTag(@Nullable String level) {
        if (level == null) {
            return null;
        }
        String name = level.trim();
        if (name.isEmpty() || name.equalsIgnoreCase(NO_LEVEL)) {
            return null;
        }
        if (name.indexOf(':') >= 0) {
            ResourceLocation parsed = ResourceLocation.tryParse(name);
            if (parsed == null) {
                throw new IllegalArgumentException("不是合法的方块标签 id：" + level);
            }
            return parsed;
        }

        String key = name.toLowerCase(Locale.ROOT);
        if (key.startsWith("needs_")) {
            key = key.substring("needs_".length());
        }
        if (key.endsWith("_tool")) {
            key = key.substring(0, key.length() - "_tool".length());
        }
        if (!VANILLA_LEVELS.contains(key)) {
            throw new IllegalArgumentException("未知的开采等级：" + level + "（可用："
                    + String.join(" / ", VANILLA_LEVELS) + " / " + NO_LEVEL
                    + "，或写完整的方块标签 id，如 \"neoforge:needs_netherite_tool\"）");
        }
        return ResourceLocation.withDefaultNamespace("needs_" + key + "_tool");
    }

    /**
     * 挂开采等级标签，并顺手把方块变成「必须用对工具才掉落」。两步缺一不可：
     * {@code needs_*_tool} 只描述等级，掉落判定读的是方块自己的 {@code requiresCorrectToolForDrops}
     * （见 {@code Player#hasCorrectToolForDrops}）——不设它的话等级标签就是个没人读的空标签，
     * 症状正是「设了等级但挖起来什么都没变」。
     * <p>
     * 副作用是工具种类也成了硬要求：母岩设成斧头 + {@code "stone"} 之后，石镐挖下来同样什么都不掉
     * （这正是原版「石头必须用镐」的规则，说清楚了就不算意外）。
     */
    private static void tagLevel(BlockBuilder builder, @Nullable ResourceLocation levelTag) {
        if (levelTag == null) {
            return;
        }
        builder.requiresTool(true);
        builder.tagBlock(new ResourceLocation[]{levelTag});
    }

    /**
     * 把方块登记进创造模式标签页。KubeJS 注册的方块**默认不进任何标签页**（玩家会以为没注册成功），
     * 所以这里默认让它们进 KubeJS 那一页；{@link Options#group} 可以换成别的（如 {@code "building_blocks"}），
     * 设成 null 就完全不进标签页（只能用 {@code /give} 取）。
     * <p>
     * 注意不能用 {@code ItemBuilder#group()}——KubeJS 2101 已移除它（会直接报错），改为在标签页构建时
     * 由 NeoForge 的 {@code BuildCreativeModeTabContentsEvent} 注入（见 {@code ModCreativeTabs}）。
     */
    private static void registerTabEntries(Options options, ResourceLocation... itemIds) {
        if (options.group == null) {
            return;
        }
        for (ResourceLocation itemId : itemIds) {
            ModCreativeTabs.addScriptedItem(options.group, itemId);
        }
    }

    /**
     * {@code "mypack:my_crystal"} → 原样；{@code "my_crystal"} → {@code kubejs:my_crystal}
     * （与 KubeJS 自己的 {@code event.create} 约定一致）。
     * <p>
     * 注意不能用 {@code ResourceLocation.tryParse} 的返回值来判断"有没有命名空间"：
     * 它在 1.21 里会给裸 id 补上 {@code minecraft:} 而不是返回 null。
     */
    private static ResourceLocation parse(String id) {
        if (!id.contains(":")) {
            return ResourceLocation.fromNamespaceAndPath("kubejs", id);
        }
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            throw new IllegalArgumentException("不是合法的方块 id：" + id);
        }
        return parsed;
    }

    /**
     * 母岩专用的 builder：建出<b>本模组的脚本母岩方块</b>，而不是 KubeJS 的通用方块。
     * 只有这样它才带共享展示方块实体——护目镜的生长信息挂在实体上
     * （{@code BuddingGrowthBlockEntity} 实现 Create 的 {@code IHaveGoggleInformation}）。
     * 随机刻也因此由方块自己处理（见 {@code ScriptedBuddingBlock}），不必再挂 KubeJS 回调。
     */
    private static final class MotherBuilder extends BasicKubeBlock.Builder {

        private final Supplier<GrowthDefinition> definition;

        MotherBuilder(ResourceLocation id, Supplier<GrowthDefinition> definition) {
            super(id);
            this.definition = definition;
        }

        @Override
        public Block createObject() {
            // randomTicks() 不能省：KubeJS 只在脚本挂了 randomTick 回调时才补这个标记
            // （见 BlockBuilder#createProperties），而本类的随机刻是自己实现的、没有回调，
            // 所以默认属性里 isRandomlyTicking 是 false——脚本母岩在原版里永远等不到随机刻，
            // 自然生长为 0，只能被催生器硬催（见 YunxianAdvancements#acceleratedRandomTick）。
            Block.Properties properties = createProperties();
            properties.randomTicks();
            return new ScriptedBuddingBlock(definition, properties);
        }
    }

    /** 把物品 id 字符串转成 ResourceLocation；null 或非法一律当没有 */
    @Nullable
    private static ResourceLocation itemId(@Nullable String id) {
        return id == null ? null : ResourceLocation.tryParse(id);
    }

    /**
     * 取第 {@code index} 个阶段（顺序：小 → 中 → 大 → 簇）的显示名。
     * <p>
     * 数组没配、比四个短、或那一项是 {@code null}/空白，都返回 {@code null}——
     * 表示这一项交给 KubeJS 按 id 自动命名，所以可以只给其中几个起名。
     */
    @Nullable
    private static String stageDisplayName(Options options, int index) {
        String[] names = options.stageDisplayNames;
        if (names == null || index >= names.length) {
            return null;
        }

        String name = names[index];
        return name == null || name.isBlank() ? null : name;
    }

    /**
     * 注册方块：{@code add} 只把它放进注册表，物品是在 KubeJS 的 {@code afterPosted} 里
     * 遍历 {@code created} 时才创建的（{@code createAdditionalObjects}）——
     * 脚本的 {@code event.create(...)} 会同时放这两处，我们手工走 {@code add} 必须自己补上，
     * 否则会出现"方块在、物品没有"（{@code /give} 报未知物品、创造栏里也没有）。
     */
    private static void registerBlock(RegistryKubeEvent<Block> event, BlockBuilder builder) {
        event.add(Registries.BLOCK, builder);
        event.created.add(builder);
    }

    /**
     * 芽/簇专用的 builder：<b>建成模组自己的簇方块</b>（{@link YunxianClusterBlock}，
     * 即 {@code AmethystClusterBlock} 的子类），而不是 KubeJS 的通用方块。
     * 这样朝向形状、支撑方块消失就掉落、挖掘音效这些原版行为全都来自它本身。
     * <p>
     * 资源侧还得自己接管两点（KubeJS 默认给的是「一条无旋转变体 + 立方体模型」）：
     * <ul>
     *   <li>模型继承原版对应的芽/簇模型（几何、尺寸、贴图键 {@code cross} 全对上）；
     *       默认的模型生成会读取 {@link #parentModel} / {@link #textures}，所以不用覆写；
     *       模型路径由 {@code blockModel} 内部按 {@code ID.BLOCK_MODEL} 拼，与下面 blockstate
     *       引用的 {@code ID.BLOCK} 是同一套约定；</li>
     *   <li>blockstate 写成六个朝向各一条、带旋转的变体——与模组自带芽/簇的数据生成用同一套公式。</li>
     * </ul>
     */
    private static final class StageBuilder extends BasicKubeBlock.Builder {

        private final Stage stage;
        private final ResourceLocation model;
        /** 阶段贴图；构造期 KubeJS 会提前调一次 getOrCreateItemBuilder，那时它还是 null */
        private String texture;

        StageBuilder(ResourceLocation id, Stage stage, String texture, ResourceLocation vanillaModel) {
            super(id);
            this.stage = stage;
            this.texture = texture;
            this.parentModel = vanillaModel;
            this.textures = Map.of("cross", texture);
            this.model = id.withPath(ID.BLOCK);
        }

        @Override
        public Block createObject() {
            // 脚本母岩不冒电火花（那是弧光石家族的专属外观，见 BuddingFamily.Appearance）
            return new YunxianClusterBlock(stage.height, stage.aabbOffset, createProperties(), stage.key, false);
        }

        /**
         * 物品模型：{@code item/generated} + 该阶段贴图，与本模组自带芽/簇的物品模型完全一致
         * （物品栏与掉落物都是平面图标，而不是把 cross 模型渲染成 3D）。
         * <p>
         * 必须覆写这里，而不是去设 {@code ItemBuilder.parentModel}/{@code textures}：
         * KubeJS 给**方块物品**生成模型走的是 {@code BlockBuilder.generateItemModel}，
         * 里面硬编码了 {@code parent(方块模型)}，压根不读 ItemBuilder 的那两个字段。
         */
        @Override
        protected void generateItemModel(ModelGenerator generator) {
            if (texture == null) {
                super.generateItemModel(generator);
                return;
            }
            generator.parent(ITEM_GENERATED);
            generator.texture("layer0", texture);
        }

        @Override
        protected void generateBlockState(VariantBlockStateGenerator generator) {
            for (Direction facing : Direction.values()) {
                generator.variant("facing=" + facing.getSerializedName(), variant -> variant.model(model)
                        .x(facing == Direction.DOWN ? 180 : facing.getAxis().isHorizontal() ? 90 : 0)
                        // 模型默认朝北：北 0、东 90、南 180、西 270
                        .y(facing.getAxis().isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360));
            }
        }
    }

    /**
     * 可选项。两种写法等价、也可以混用：
     * <pre>{@code
     * // 一、逐字段赋值
     * const opts = new CustomBuddingOptions()
     * opts.chance = 20
     * opts.requiresWater = true
     * CustomBudding.create(event, 'my_crystal', opts)
     *
     * // 二、链式（整条链作为 create 的第三个实参）
     * CustomBudding.create(event, 'my_crystal', new CustomBuddingOptions()
     *     .chance(20)
     *     .requiresWater()
     *     .maxLight(0))
     * }</pre>
     * 链式方法都返回 {@code this}，名字与字段同名——脚本里 {@code opts.chance(20)} 与
     * {@code opts.chance = 20} 各走各的，互不影响。
     * <p>
     * <b>链必须写在 {@code create} / {@code modify} 的实参里</b>：选项只在调用时读一次，
     * 而 {@code create} 返回的是注册结果 {@link Family}，不是 builder——
     * {@code CustomBudding.create(event, id).chance(20)} 那种写法不会生效（方块那时已经建好了）。
     * <p>
     * <b>生长参数那几项没写就是 {@code null}（"未指定"）</b>：{@code create} 给它们补上文档里写的
     * 默认值（概率 1/5、不限光照……），{@code modify} 则保留方块现状——所以
     * {@code .modify(id, opts.chance(20))} 只改概率，方块原本的光照/含水/环境一概不动。
     * （材质、音效、工具那几项是注册期的参数，没写就保持各自的默认值，{@code modify} 一概不管。）
     * 想显式清掉某一项也有写法：{@code maxLight(-1)} = 不限制、{@code minLight(-1)} = 不限制、
     * {@code requiresWater(false)} = 不再需要水、{@code growthDimensions()}（不给参数）= 维度不限、
     * {@code needfluid('none')} = 取消流体需求。
     */
    public static final class Options {

        // ===== create 的默认值（modify 不用它们，未指定的项以方块现状为准） =====

        /** 没写 {@link #chance} 时的概率基数 */
        public static final int DEFAULT_CHANCE = 5;
        /** 没写 {@link #maxLight} / {@link #minLight} 时的值：负数 = 那一端不限制 */
        public static final int DEFAULT_LIGHT = -1;
        /** 没写 {@link #outsideGrowthChance} 时的地盘外概率 */
        public static final double DEFAULT_OUTSIDE_GROWTH_CHANCE = 0.5;
        /** 没写 {@link #fluidCostPerGrowth} 时的每次消耗（mB） */
        public static final int DEFAULT_FLUID_COST = 250;
        /** 没写 {@link #fluidCapacity} 时的罐容量（mB） */
        public static final int DEFAULT_FLUID_CAPACITY = 1000;
        /** 没写 {@link #dropCount} 时的掉落数量 */
        public static final int DEFAULT_DROP_COUNT = 1;
        /** 没写 {@link #group} 时进的标签页 */
        public static final String DEFAULT_GROUP = "kubejs";

        /** 概率基数 n：每次随机刻有 1/n 的概率推进一级；null = 未指定（create 用 {@link #DEFAULT_CHANCE}） */
        public @Nullable Integer chance = null;
        /** 生长位允许的最大亮度（0–15）；<b>负数 = 那一端不限制</b>（显式写负数还能用来"清掉"已有的限制） */
        public @Nullable Integer maxLight = null;
        /** 生长位要求的最低亮度（0–15）；负数 = 不限制。与 {@link #maxLight} 一起构成闭区间 */
        public @Nullable Integer minLight = null;
        /** 目标格必须含水（可燃冰式）；null = 未指定 */
        public @Nullable Boolean requiresWater = null;
        /**
         * 生长要消耗哪种流体：流体 id（{@code "minecraft:lava"}）或流体标签
         * （{@code "#minecraft:lava"}）；null = 未指定（不消耗流体，或 modify 时保留现状）。
         * <p>
         * 写字符串 {@code "none"} = <b>显式取消</b>流体需求（改一块原本要烧流体的母岩时用得上，
         * 与 {@code .buddingLevel('none')} 同一个写法）。
         * <p>
         * 这三个字段是<b>成套</b>读的：只有 {@link #fluid} 非 null 时才会去看下面两个数字
         * （逐字段赋值时想改消耗或容量，必须把 {@code fluid} 也写上）。
         */
        public @Nullable String fluid = null;
        /** {@link #fluid} 每次成功生长消耗多少 mB；null = 未指定（create 用 {@link #DEFAULT_FLUID_COST}） */
        public @Nullable Integer fluidCostPerGrowth = null;
        /** {@link #fluid} 的罐容量 mB；null = 未指定（create 用 {@link #DEFAULT_FLUID_CAPACITY}） */
        public @Nullable Integer fluidCapacity = null;
        /**
         * 生长维度 id 列表（如 {@code "minecraft:overworld"}），<b>可以多选</b>：
         * 只在这些维度里正常生长，出了地盘每次判定通过后再掷一次、只有
         * {@link #outsideGrowthChance} 的概率继续生长。null / 不写 = 维度不限。
         * <p>
         * {@code modify} 时给一个<b>空数组</b>（{@code .growthDimensions()}）表示"清掉维度限制"。
         */
        public @Nullable String[] growthDimensions = null;
        /**
         * 生长群系条件，<b>可以多选</b>：具体群系 id（{@code "minecraft:lush_caves"}）、
         * 群系标签（{@code "#minecraft:is_nether"}）、内置气候关键字
         * （{@code "cold"} 寒冷 / {@code "warm"} 温暖 / {@code "hot"} 炎热；下界全域算炎热、末地全域算寒冷），
         * 或者它们前面加 {@code !} 表示<b>否定</b>（{@code "!cold"} = 只要不是寒冷群系就行）。
         * 与 {@link #growthDimensions} 都写时<b>取交集</b>——维度、群系都满足才算在自己的地盘上。
         * null / 不写 = 群系不限；{@code modify} 时空数组表示"清掉群系限制"。
         */
        public @Nullable String[] growthBiomes = null;
        /**
         * 自己的地盘<b>之外</b>的生长概率：0–1 的小数
         * （create 默认 0.5 = 一半；0 = 出了地盘就再也长不动）。只有写了 {@link #growthDimensions} 或
         * {@link #growthBiomes} 才有意义。null = 未指定。
         */
        public @Nullable Double outsideGrowthChance = null;
        /** 母岩的显示名；null = 交给 KubeJS 按 id 自动命名 */
        public @Nullable String displayName = null;
        /**
         * 四个阶段的显示名，顺序：小芽 → 中芽 → 大芽 → 晶簇。
         * <p>
         * 不配、比四个短、或某一项是 {@code null}/空白，那一项就交给 KubeJS 按 id 自动命名
         * （{@code example_crystal_small_bud} → "Example Crystal Small Bud"，写进 en_us 虚拟语言文件），
         * 所以可以只给其中几个起名。
         */
        public @Nullable String[] stageDisplayNames = null;
        /**
         * 创造模式标签页：默认 {@code "kubejs"}（KubeJS 自己那一页）。
         * 可以换成原版页（{@code "building_blocks"} / {@code "natural_blocks"} / {@code "functional_blocks"} …，用方块 id 里那套下划线写法），
         * 设成 null 则不进标签页（只能用 {@code /give} 取）。
         * <p>
         * <b>只有 {@code create} 管这一项</b>：进了哪一页属于方块的「身份」，而 {@code modify} 只管
         * 给它加特性——脚本在 {@code modify} 里写了这一项会当场报错（判定靠"与这个默认值不同"，
         * 所以写成本页本身的 {@code .group('kubejs')} 反而是空操作，本来就在那一页）。
         */
        public @Nullable String group = DEFAULT_GROUP;
        /** 母岩贴图 */
        public String buddingTexture = DEFAULT_BUDDING_TEXTURE;
        /**
         * 母岩的破坏音效（破坏、踩踏、放置等一整套）；默认 {@link SoundType#AMETHYST}，即原版紫水晶母岩同款。
         * <p>
         * 脚本里写音效名即可（{@code 'stone'} / {@code 'crop'} / {@code 'glass'} / {@code 'wood'} / {@code 'empty'} …），
         * 名字取自原版 {@code SoundType} 的字段名；写不认识的名字 KubeJS 会当场报错并列出全部可用名字。
         */
        public SoundType buddingSound = SoundType.AMETHYST;
        /**
         * 芽与晶簇的破坏音效；默认 {@link SoundType#AMETHYST}（原版紫水晶芽/簇同款）。
         * <p>
         * 四个阶段共用一个；写法与 {@link #buddingSound} 相同。
         */
        public SoundType stageSound = SoundType.AMETHYST;
        /**
         * 母岩的开采工具，默认 {@code "pickaxe"}（镐，与原版紫水晶一致）。
         * <p>
         * 裸名字走原版那四个挖掘标签 {@code minecraft:mineable/<名字>}：{@code pickaxe} / {@code axe} /
         * {@code shovel} / {@code hoe}；写别的裸名字会直接报错。
         * 要指向别的标签就写完整 id（含 {@code :}，如 {@code "mymod:mineable/wrench"}）。
         * 设成 {@code null} = 一个标签都不挂，徒手就是最快（任何工具都没有加成）。
         * <p>
         * 只管"用什么挖最快"，"要什么等级才掉"看 {@link #buddingLevel}。四个芽/簇另有一份，
         * 见 {@link #stageTool}；一次设两边用 {@link #tool}。
         */
        public @Nullable String buddingTool = "pickaxe";
        /** 芽与晶簇的开采工具，四个阶段共用一个；写法与 {@link #buddingTool} 完全相同 */
        public @Nullable String stageTool = "pickaxe";
        /**
         * 母岩的开采等级，默认 {@code null} = 不设等级（与原版紫水晶一样，任何工具、甚至徒手都能拿到掉落）。
         * <p>
         * 裸名字走原版三档挖掘等级标签 {@code minecraft:needs_<名字>_tool}：{@code "stone"} / {@code "iron"} /
         * {@code "diamond"}；{@code "none"} 或 {@code null} = 不设等级。<b>写别的裸名字会直接报错</b>。
         * 要指向别的标签就写完整 id（含 {@code :}，如 {@code "neoforge:needs_netherite_tool"}）。
         * <p>
         * 设了等级会连带给方块加上 {@code requiresCorrectToolForDrops}（只挂标签没人读，见 {@code tagLevel}），
         * 于是设完之后：<b>等级不够的工具挖下来什么都不掉</b>（含精准采集——掉落判定整段被跳过），
         * 而且<b>工具种类也必须对</b>：母岩的工具是斧头（{@link #buddingTool}）+ 等级 {@code "stone"} 时，
         * 石斧掉、石镐不掉。想要"徒手也能捡"就别设等级。
         * <p>
         * 芽与晶簇另有一份，见 {@link #stageLevel}。
         */
        public @Nullable String buddingLevel = null;
        /** 芽与晶簇的开采等级，四个阶段共用一个；写法与 {@link #buddingLevel} 完全相同 */
        public @Nullable String stageLevel = null;
        /**
         * 晶簇被普通破坏时掉落的物品 id（精准采集始终掉晶簇本体）；null = 什么都不掉。
         * 芽无论怎么破坏都只有精准采集才掉本体（与本模组自带的芽一致）。
         * <p>
         * 数量受时运加成：每一级额外给 0..等级 个，与自带晶簇的掉落表一致。
         */
        public @Nullable String dropItem = null;
        /** {@link #dropItem} 的掉落数量（小于 1 按 1 处理）；时运的加成会加在它上面。null = 未指定 */
        public @Nullable Integer dropCount = null;
        /** 四个阶段的贴图，顺序：小芽 → 中芽 → 大芽 → 晶簇 */
        public String[] stageTextures = DEFAULT_STAGE_TEXTURES.clone();

        // ==================== 链式设置（与上面的字段等价，可混用） ====================

        public Options chance(int chance) {
            this.chance = chance;
            return this;
        }

        public Options maxLight(int maxLight) {
            this.maxLight = maxLight;
            return this;
        }

        public Options minLight(int minLight) {
            this.minLight = minLight;
            return this;
        }

        /** 等价于 {@code requiresWater = true} */
        public Options requiresWater() {
            return requiresWater(true);
        }

        /** 显式给值：{@code requiresWater(false)} 可以改回不需要水 */
        public Options requiresWater(boolean value) {
            this.requiresWater = value;
            return this;
        }

        /**
         * 生长要消耗流体：母岩自带一个小罐，<b>每次成功生长扣 {@code costPerGrowth} mB</b>，
         * 罐里不够一次消耗时就<b>不再生长</b>（不是减速、也不是半价推进，是这一轮直接不长），
         * 与远古残骸母岩的熔岩罐同款。罐最多存 {@code capacity} mB。
         * <pre>{@code
         * .needfluid('minecraft:lava', 250, 1000)   // 认熔岩：每次生长扣 250 mB，最多存 1 B
         * .needfluid('#minecraft:lava', 500, 4000)  // 认标签：标签覆盖的全部流体都算
         * }</pre>
         * 流体怎么写：{@code 'minecraft:lava'} 这样的 id（按流体类型判定，静止与流动变体都算，
         * 所以桶灌的、管道抽的都认）或 {@code '#minecraft:lava'} 这样的标签；流体名写错会当场报错。
         * <p>
         * {@code capacity} 必须 ≥ {@code costPerGrowth}，否则罐永远装不满一次生长，
         * 这种配置会直接报错而不是造出一个不长的方块。
         * <p>
         * 流体怎么进罐：管道/泵（方块实现了 NeoForge 流体能力）、手持容器右键（桶灌满、舀空；
         * 一桶 = 1000 mB，半桶这种零头只能用管道补），比较器读液位，戴护目镜能看到当前量。
         * <p>
         * 不传第三个数时是 {@code .needfluid(流体)}：消耗与容量用默认的 250 / 1000。
         * 传 {@code null} = 不要流体需求（与不写这项等价）。
         */
        public Options needfluid(@Nullable String fluidOrTag, int costPerGrowth, int capacity) {
            this.fluid = fluidOrTag;
            this.fluidCostPerGrowth = costPerGrowth;
            this.fluidCapacity = capacity;
            return this;
        }

        /**
         * 同上，消耗与容量取默认值（250 mB / 1000 mB）。
         * <p>
         * 注意这两个数是<b>成套</b>写进去的：想只改消耗或容量，就把流体一起写上
         * （{@code .needfluid('minecraft:lava', 500, 1000)}）——{@code modify} 时只写
         * {@code .fluidCostPerGrowth} 之类的字段是没人读的。
         */
        public Options needfluid(@Nullable String fluidOrTag) {
            return needfluid(fluidOrTag, DEFAULT_FLUID_COST, DEFAULT_FLUID_CAPACITY);
        }

        /**
         * 生长维度 id，<b>可多选</b>（{@code .growthDimensions('minecraft:overworld', 'minecraft:nether')}）；
         * 一个都不传 = 维度不限。地盘外的生长概率用 {@link #outsideGrowthChance(double)} 调。
         */
        public Options growthDimensions(@Nullable String... dimensions) {
            this.growthDimensions = dimensions;
            return this;
        }

        /**
         * 生长群系条件，<b>可多选</b>：具体群系 id、群系标签、气候关键字（{@code cold} / {@code warm} / {@code hot}），
         * 或它们前面加 {@code !} 表示否定
         * （{@code .growthBiomes('minecraft:lush_caves', '!#minecraft:is_taiga', '!cold', '!hot')}）；
         * 一个都不传 = 群系不限。与 {@link #growthDimensions(String...)} 都写时取交集。
         */
        public Options growthBiomes(@Nullable String... biomes) {
            this.growthBiomes = biomes;
            return this;
        }

        /**
         * 自己的地盘<b>之外</b>的生长概率：0–1 的小数
         * （默认 0.5 = 一半；0 = 出了地盘就再也长不动）。
         */
        public Options outsideGrowthChance(double chance) {
            this.outsideGrowthChance = chance;
            return this;
        }

        public Options displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        /** 四个阶段的显示名，顺序：小芽 → 中芽 → 大芽 → 晶簇；传 {@code null} 的那项保持自动命名 */
        public Options stageDisplayNames(@Nullable String... stageDisplayNames) {
            this.stageDisplayNames = stageDisplayNames;
            return this;
        }

        /** 传 {@code null} = 不进任何创造栏（与字段直接赋 null 一致） */
        public Options group(@Nullable String group) {
            this.group = group;
            return this;
        }

        /** 晶簇普通破坏的掉落物；等价于只设 {@code dropItem}，数量沿用默认 1 */
        public Options dropItem(@Nullable String itemId) {
            this.dropItem = itemId;
            return this;
        }

        /** 掉落物 + 数量一次设完 */
        public Options dropItem(@Nullable String itemId, int count) {
            this.dropItem = itemId;
            this.dropCount = count;
            return this;
        }

        public Options dropCount(int dropCount) {
            this.dropCount = dropCount;
            return this;
        }

        public Options buddingTexture(String buddingTexture) {
            this.buddingTexture = buddingTexture;
            return this;
        }

        /** 母岩的破坏音效；写音效名（{@code 'stone'} / {@code 'crop'} …，见 {@link #buddingSound}） */
        public Options buddingSound(SoundType buddingSound) {
            this.buddingSound = buddingSound;
            return this;
        }

        /** 芽与晶簇的破坏音效；写音效名（{@code 'stone'} / {@code 'crop'} …，见 {@link #stageSound}） */
        public Options stageSound(SoundType stageSound) {
            this.stageSound = stageSound;
            return this;
        }

        /** 五个方块共用同一个开采工具：等价于 {@link #buddingTool} 与 {@link #stageTool} 都设成它 */
        public Options tool(@Nullable String tool) {
            this.buddingTool = tool;
            this.stageTool = tool;
            return this;
        }

        /** 母岩的开采工具；裸工具名或完整标签 id，{@code null} = 不挂标签（见 {@link #buddingTool}） */
        public Options buddingTool(@Nullable String buddingTool) {
            this.buddingTool = buddingTool;
            return this;
        }

        /** 芽与晶簇的开采工具；写法与 {@link #buddingTool} 相同 */
        public Options stageTool(@Nullable String stageTool) {
            this.stageTool = stageTool;
            return this;
        }

        /** 母岩的开采等级；{@code "stone"} / {@code "iron"} / {@code "diamond"}，{@code "none"} = 不设（见 {@link #buddingLevel}） */
        public Options buddingLevel(@Nullable String buddingLevel) {
            this.buddingLevel = buddingLevel;
            return this;
        }

        /** 芽与晶簇的开采等级；写法与 {@link #buddingLevel} 相同 */
        public Options stageLevel(@Nullable String stageLevel) {
            this.stageLevel = stageLevel;
            return this;
        }

        /** 四个阶段贴图，顺序：小芽 → 中芽 → 大芽 → 晶簇 */
        public Options stageTextures(String... stageTextures) {
            this.stageTextures = stageTextures;
            return this;
        }
    }

    /** 注册结果：五样方块的 id，方便脚本接着写配方、标签、战利品表 */
    public record Family(ResourceLocation budding, ResourceLocation smallBud, ResourceLocation mediumBud,
                         ResourceLocation largeBud, ResourceLocation cluster) {
    }

    /**
     * 生长定义要等首次随机刻才构造：KubeJS 启动脚本执行时，其它模组（含本模组）的方块还没注册完
     * （模组方块在注册事件里才入表），所以只能到时候按 id 解析一次并缓存。
     */
    private static final class LazyDefinition implements Supplier<GrowthDefinition> {
        private final ResourceLocation[] stages;
        private final int chance;
        private final int maxLight;
        private final int minLight;
        private final boolean requiresWater;
        private final @Nullable String[] growthDimensions;
        private final @Nullable String[] growthBiomes;
        private final double outsideGrowthChance;
        private final @Nullable FluidRequirement fluid;

        private GrowthDefinition cached;

        LazyDefinition(ResourceLocation[] stages, Options options, @Nullable FluidRequirement fluid) {
            this.stages = stages;
            // 选项只在注册时读一次，之后脚本再改 Options 不影响这个家族。
            // 选项里"没写"的项是 null（为的是让 modify 能区分"没写"与"写成默认值"），
            // 所以这里统一补上 create 的出厂默认值
            this.chance = options.chance != null ? options.chance : Options.DEFAULT_CHANCE;
            this.maxLight = options.maxLight != null ? options.maxLight : Options.DEFAULT_LIGHT;
            this.minLight = options.minLight != null ? options.minLight : Options.DEFAULT_LIGHT;
            this.requiresWater = Boolean.TRUE.equals(options.requiresWater);
            this.growthDimensions = options.growthDimensions;
            this.growthBiomes = options.growthBiomes;
            this.outsideGrowthChance = options.outsideGrowthChance != null
                    ? options.outsideGrowthChance : Options.DEFAULT_OUTSIDE_GROWTH_CHANCE;
            // 流体需求在 create 里就解析好了（那时报错更准），这里只是带着走
            this.fluid = fluid;
        }

        @Override
        public GrowthDefinition get() {
            GrowthDefinition definition = cached;
            if (definition == null) {
                definition = GrowthDefinition.of(stages[0].toString(), stages[1].toString(),
                        stages[2].toString(), stages[3].toString(), chance, maxLight, minLight,
                        requiresWater);
                // 维度与群系 id 都在这里才解析（构造定义时方块已注册完，与其余字段同样对待）；
                // 两者都写时取交集，所以两个列表分别点它们的链式方法
                if (growthDimensions != null && growthDimensions.length > 0) {
                    definition = definition.growthDimensions(growthDimensions);
                }
                if (growthBiomes != null && growthBiomes.length > 0) {
                    definition = definition.growthBiomes(growthBiomes);
                }
                if ((growthDimensions != null && growthDimensions.length > 0)
                        || (growthBiomes != null && growthBiomes.length > 0)) {
                    definition = definition.outsideGrowthChance(outsideGrowthChance);
                }
                if (fluid != null) {
                    definition = definition.fluidRequirement(fluid);
                }
                cached = definition;
            }
            return definition;
        }
    }
}
