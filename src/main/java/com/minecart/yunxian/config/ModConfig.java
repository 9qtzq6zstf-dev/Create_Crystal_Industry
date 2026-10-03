package com.minecart.yunxian.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfig {
    private ModConfig() {
    }

    /**
     * 配置界面文案的翻译键前缀（界面文字全部来自语言文件，类中不再出现提示文案）：
     *   标签：create_crystal_industry.configuration.<TOML 键名>
     *   提示：create_crystal_industry.configuration.<TOML 键名>.tooltip
     * 分类（TOML 章节）共用同一套前缀，键名就是分类路径本身（如 worldgen）：
     *   分类标题：create_crystal_industry.configuration.worldgen
     *   分类提示：create_crystal_industry.configuration.worldgen.tooltip
     *
     * 每个配置值必须显式调用 .translation(键)，原因（均有源码依据）：
     *   - NeoForge 原生配置界面：显式键与它自身的默认拼法（modId + ".configuration." + 键名）完全相同，零影响；
     *   - Configured：NeoForgeValue#getTranslationKey() 返回的就是 ValueSpec#getTranslationKey()；
     *     未显式设置时为 null，getComment() 会拼出 "null.tooltip" 查不到翻译，
     *     回退成 Component.literal(英文 comment)——即此前配置界面显示英文的原因。
     */
    private static final String LANG_PREFIX = "create_crystal_industry.configuration.";

    /**
     * 开一个配置分类：TOML 文件里落成一张表（{@code [worldgen]}），配置界面里落成一层子页面。
     * 标题与提示走语言文件；comment 写进 .toml 供直接编辑者阅读，并作翻译缺失时的兜底。
     * <p>
     * 同一个分类的值必须连着定义。值在文件里的顺序就是定义顺序，中间隔着别的分类，
     * 那一组配置就会被隔开（旧版本留下的配置文件另有自己的历史顺序，分类就位后重排一次即可）。
     */
    private static void section(ModConfigSpec.Builder builder, String path, String... comment) {
        builder.comment(comment).translation(LANG_PREFIX + path).push(path);
    }

    /** 收掉最近开的一个分类，回到上一层；与 {@link #section} 成对出现 */
    private static void endSection(ModConfigSpec.Builder builder) {
        builder.pop();
    }

    public static final class Common {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        // ===== 世界生成 =====
        static {
            section(BUILDER, "worldgen",
                    "World generation: which budding blocks spawn naturally, plus the Flammable Ice",
                    "structure and Budding Glowstone settings.");
        }

        // comment 仅保留英文：它会写入 .toml 文件供直接编辑者阅读，并作为翻译缺失时的兜底。
        private static ModConfigSpec.BooleanValue budding(String name, boolean defaultValue) {
            String path = "generate_" + name;
            return BUILDER
                    .comment("Whether the " + name + " budding block generates in the world.")
                    .translation(LANG_PREFIX + path)
                    .define(path, defaultValue);
        }

        /**
         * 各母岩家族的世界生成开关：由中央定义表派生（{@link BuddingFamilies#ALL} 里
         * {@code generateInWorld} 为 true 的家族各产出一个 generate_&lt;id&gt; 配置项）。
         */
        private static final Map<String, ModConfigSpec.BooleanValue> GENERATE_BUDDING = buildBuddingFlags();

        private static Map<String, ModConfigSpec.BooleanValue> buildBuddingFlags() {
            Map<String, ModConfigSpec.BooleanValue> flags = new LinkedHashMap<>();
            for (RegisteredFamily family : BuddingFamilies.ALL) {
                if (family.spec().generateInWorld()) {
                    flags.put(family.spec().id(), budding(family.spec().id(), true));
                }
            }
            return Map.copyOf(flags);
        }

        /** 查询某个母岩家族的世界生成开关；未配置该项的家族（如玫瑰石英、福鲁伊克斯）恒为 true */
        public static boolean enabled(String key) {
            ModConfigSpec.BooleanValue flag = GENERATE_BUDDING.get(key);
            return flag == null || flag.get();
        }

        // 可燃冰母岩结构：生成概率，以及结构周围散布的灵魂沙
        public static final ModConfigSpec.IntValue FLAMMABLE_ICE_CHANCE = BUILDER
                .comment(
                        "1-in-N chance per eligible deep-ocean chunk that a flammable ice budding structure generates.",
                        "Higher = rarer.")
                .translation(LANG_PREFIX + "flammableIceChance")
                .defineInRange("flammableIceChance", 256, 1, 10000);

        // 灵魂沙的数量、散布范围与埋深固定在 FlammableIceFeature 里，只留这个开关
        public static final ModConfigSpec.BooleanValue SOUL_SAND_GENERATE = BUILDER
                .comment(
                        "Whether soul sand (sea-surface bubble effect) generates around flammable ice structures.",
                        "Its count, scatter range and burial depth are fixed in code.")
                .translation(LANG_PREFIX + "soulSandGenerate")
                .define("soulSandGenerate", true);

        // 荧石母岩：替换自然荧石团的概率，以及附带生成晶芽的开关与数量
        public static final ModConfigSpec.DoubleValue GLOWSTONE_BUDDING_CHANCE = BUILDER
                .comment(
                        "Chance (0.0–1.0) that a naturally generated glowstone cluster gets its lowest block replaced with glowstone budding.",
                        "0.0 = never, 1.0 = every cluster.")
                .translation(LANG_PREFIX + "glowstoneBuddingChance")
                .defineInRange("glowstoneBuddingChance", 0.1, 0.0, 1.0);

        public static final ModConfigSpec.BooleanValue GLOWSTONE_GENERATE_BUDS = BUILDER
                .comment(
                        "Whether glowstone budding also spawns a few glowstone buds/clusters on its side faces.")
                .translation(LANG_PREFIX + "glowstoneGenerateBuds")
                .define("glowstoneGenerateBuds", true);

        public static final ModConfigSpec.IntValue GLOWSTONE_BUD_COUNT = BUILDER
                .comment(
                        "Number of buds/clusters spawned around each naturally generated glowstone budding block.",
                        "0 = none.")
                .translation(LANG_PREFIX + "glowstoneBudCount")
                .defineInRange("glowstoneBudCount", 5, 0, 8);

        public static final ModConfigSpec.BooleanValue GLOWSTONE_BUDS_ON_GLOWSTONE = BUILDER
                .comment(
                        "Whether glowstone buds also generate on nearby glowstone blocks around the budding block.",
                        "If false, buds only appear on the budding block's own faces.")
                .translation(LANG_PREFIX + "glowstoneBudsOnGlowstone")
                .define("glowstoneBudsOnGlowstone", true);

        static {
            endSection(BUILDER);
        }

        // ===== 母岩侵染（再生传播） =====
        static {
            section(BUILDER, "infection",
                    "Budding blocks infecting neighbouring blocks into new budding blocks.");
        }

        /**
         * 母岩会不会把紧邻的方块"传染"成新的母岩（粗铁块 → 粗铁母岩、平滑石英 → 石英母岩、
         * 远古残骸 → 远古残骸母岩……）。关掉之后，母岩不再自己变多——矿石转化
         * （石头 → 矿石）与回响母岩的幽匿蔓延不受影响。
         */
        public static final ModConfigSpec.BooleanValue BUDDING_INFECTION = BUILDER
                .comment(
                        "Whether budding blocks can infect neighbouring blocks into new budding blocks",
                        "(raw iron block -> Budding Raw Iron, smooth quartz -> Budding Quartz, ...).",
                        "Turning this off stops budding blocks from multiplying themselves. Ore conversion",
                        "(stone -> ore) and the Echo block's sculk spread are not affected.")
                .translation(LANG_PREFIX + "buddingInfection")
                .define("buddingInfection", true);

        /**
         * 允许侵染的母岩名单：写<b>母岩家族 id</b>（{@code raw_iron}、{@code quartz}，即
         * {@code worldgen.generate_<id>} 那个 id）或<b>方块 id</b>（{@code kubejs:my_crystal_budding}，
         * 脚本注册的母岩只能用这一种写法）。
         * <p>
         * 写空 = 自带侵染规则的母岩全都允许（出厂设置）；写了名单 = <b>只有</b>名单里的允许，
         * 其余母岩不再侵染。{@link #BUDDING_INFECTION} 关掉时这份名单不起作用。
         */
        public static final ModConfigSpec.ConfigValue<List<? extends String>> INFECTING_BUDDING = BUILDER
                .comment(
                        "Which budding blocks may infect neighbours into new budding blocks: family ids",
                        "(raw_iron, quartz) or block ids (kubejs:my_crystal_budding).",
                        "Empty = every budding block with an infection rule keeps it (the default);",
                        "a non-empty list = ONLY those may infect.",
                        "Does nothing while buddingInfection is false.")
                .translation(LANG_PREFIX + "infectingBudding")
                // 宽松校验：只挡非字符串。拼错的 id 不会静默——凡是不在名单里的母岩都不再侵染，
                // 效果是"名单写错 = 谁都不侵染"，服主一眼就能看出自己写错了
                .defineListAllowEmpty("infectingBudding", List.of(), () -> "", element -> element instanceof String);

        /** 母岩能不能侵染（总开关）；读配置见 {@code BuddingConversions#infectionAllowed} */
        public static boolean buddingInfectionEnabled() {
            return BUDDING_INFECTION.get();
        }

        /** 允许侵染的母岩名单（家族 id 或方块 id）；空 = 按各家族出厂设置 */
        public static List<? extends String> infectingBudding() {
            return INFECTING_BUDDING.get();
        }

        static {
            endSection(BUILDER);
        }

        // ===== 催生器 =====
        static {
            section(BUILDER, "accelerator",
                    "Ticks between two acceleration passes, shared by both accelerators.");
        }

        public static final ModConfigSpec.IntValue ACCELERATOR_INTERVAL_TICKS = BUILDER
                .comment(
                        "Ticks between two acceleration passes (1 = every tick, the default).",
                        "Both accelerators scale with it: larger = slower. The electric one pays its energy cost per pass.")
                .translation(LANG_PREFIX + "acceleratorIntervalTicks")
                .defineInRange("acceleratorIntervalTicks", 1, 1, 100);

        /**
         * 两次催生之间的 tick 数，电力与动力催生器共用（下限 1 防止除零）。
         * 护目镜的倍率也读它——客户端读的是本地配置，多人游戏里服务端改过该值时会与实际不符（仅影响显示）。
         */
        public static int acceleratorIntervalTicks() {
            return Math.max(1, ACCELERATOR_INTERVAL_TICKS.get());
        }

        static {
            endSection(BUILDER);
        }

        // ===== 回响望远镜 =====
        static {
            section(BUILDER, "spyglass",
                    "Scan radius, interval and per-scan result cap of the Echo Spyglass.");
        }

        public static final ModConfigSpec.IntValue SCAN_RADIUS = BUILDER
                .comment(
                        "Scan radius of the Echo Spyglass, in blocks.")
                .translation(LANG_PREFIX + "scanRadius")
                .defineInRange("scanRadius", 64, 4, 128);

        public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS = BUILDER
                .comment(
                        "Interval between two scans, in ticks.")
                .translation(LANG_PREFIX + "scanIntervalTicks")
                .defineInRange("scanIntervalTicks", 10, 1, 200);

        public static final ModConfigSpec.IntValue MAX_RESULTS = BUILDER
                .comment(
                        "Maximum number of blocks per scan.",
                        "Prevents huge network packets from overly broad filters (e.g. stone).")
                .translation(LANG_PREFIX + "maxResults")
                .defineInRange("maxResults", 4096, 64, 100000);

        static {
            endSection(BUILDER);
        }

        // ===== 水晶电池 =====
        // 「哪些方块能当晶体、算哪一档」不在配置里，走方块标签
        // （create_crystal_industry:battery_crystal/<档位>_capacity，见 battery/CrystalTier）：
        // 标签是数据包内容，整合包直接覆写或用 KubeJS 加就行，不必改配置。
        static {
            section(BUILDER, "crystalBattery",
                    "Size limits of the Crystal Battery multiblock.");
        }

        public static final ModConfigSpec.IntValue CRYSTAL_BATTERY_MAX_WIDTH = BUILDER
                .comment(
                        "Maximum horizontal size of a Crystal Battery multiblock (width x width).",
                        "The Fluid Tank this block is modelled after is limited to 3.")
                .translation(LANG_PREFIX + "crystalBatteryMaxWidth")
                .defineInRange("crystalBatteryMaxWidth", 3, 1, 16);

        public static final ModConfigSpec.IntValue CRYSTAL_BATTERY_MAX_HEIGHT = BUILDER
                .comment(
                        "Maximum height of a Crystal Battery multiblock, in blocks.")
                .translation(LANG_PREFIX + "crystalBatteryMaxHeight")
                .defineInRange("crystalBatteryMaxHeight", 32, 1, 256);

        public static int crystalBatteryMaxWidth() {
            return CRYSTAL_BATTERY_MAX_WIDTH.get();
        }

        public static int crystalBatteryMaxHeight() {
            return CRYSTAL_BATTERY_MAX_HEIGHT.get();
        }

        static {
            endSection(BUILDER);
        }

        // ===== 动力吸尘器 =====
        static {
            section(BUILDER, "cleaner",
                    "Mechanical Cleaner: what happens to the drops it sucks in.");
        }

        public static final ModConfigSpec.BooleanValue CLEANER_DIRECT_ABSORB = BUILDER
                .comment(
                        "Whether drops appearing inside a sucking Mechanical Cleaner's airstream go straight into its inventory instead of spawning as item entities.",
                        "Saves entities in farms; the item ends up in the same place either way.",
                        "Anything that would not be picked up right away anyway (inventory full, filtered out,",
                        "or a washing/smelting airstream) still spawns as a normal drop entity.")
                .translation(LANG_PREFIX + "cleanerDirectAbsorb")
                .define("cleanerDirectAbsorb", true);

        static {
            endSection(BUILDER);
        }

        public static final ModConfigSpec SPEC = BUILDER.build();
    }

    public static final class Client {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        // ===== 回响护目镜 =====
        static {
            section(BUILDER, "goggles",
                    "Night Vision Goggles screen overlay.");
        }

        public static final ModConfigSpec.DoubleValue GOGGLES_OVERLAY_ALPHA = BUILDER
                .comment(
                        "Overall opacity of the night vision goggles screen overlay (0.0 = invisible, 1.0 = fully opaque).")
                .translation(LANG_PREFIX + "gogglesOverlayAlpha")
                .defineInRange("gogglesOverlayAlpha", 0.75, 0.0, 1.0);

        static {
            endSection(BUILDER);
        }

        // ===== 动力吸尘器 =====
        // 与 COMMON 里的 cleaner 分类标题共用翻译键（同一个方块的两组设置）
        static {
            section(BUILDER, "cleaner",
                    "Mechanical Cleaner: whether the suction animation plays.");
        }

        public static final ModConfigSpec.BooleanValue CLEANER_SUCK_ANIMATION = BUILDER
                .comment(
                        "Whether items play the fly-into-the-cleaner animation when the Mechanical Cleaner sucks them in.",
                        "Visual only: items enter the inventory at the same instant either way.")
                .translation(LANG_PREFIX + "cleanerSuckAnimation")
                .define("cleanerSuckAnimation", true);

        static {
            endSection(BUILDER);
        }

        public static final ModConfigSpec SPEC = BUILDER.build();
    }
}
