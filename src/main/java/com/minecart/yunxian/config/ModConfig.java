package com.minecart.yunxian.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfig {
    private ModConfig() {
    }

    public static final class Common {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        // ===== 世界生成开关 =====
        private static ModConfigSpec.BooleanValue budding(String name, boolean defaultValue) {
            return BUILDER.comment(
                            "Whether the " + name + " budding block generates in the world.",
                            name + " 母岩方块是否在世界中自然生成。")
                    .define("generate_" + name, defaultValue);
        }

        public static final ModConfigSpec.BooleanValue GENERATE_RAW_IRON = budding("raw_iron", true);
        public static final ModConfigSpec.BooleanValue GENERATE_RAW_GOLD = budding("raw_gold", true);
        public static final ModConfigSpec.BooleanValue GENERATE_RAW_COPPER = budding("raw_copper", true);
        public static final ModConfigSpec.BooleanValue GENERATE_RAW_ZINC = budding("raw_zinc", true);
        public static final ModConfigSpec.BooleanValue GENERATE_REDSTONE = budding("redstone", true);
        public static final ModConfigSpec.BooleanValue GENERATE_ECHO = budding("echo", true);
        public static final ModConfigSpec.BooleanValue GENERATE_GLOWSTONE = budding("glowstone", true);
        public static final ModConfigSpec.BooleanValue GENERATE_QUARTZ = budding("quartz", true);

        public static final ModConfigSpec.DoubleValue GLOWSTONE_BUDDING_CHANCE = BUILDER
                .comment(
                        "Chance (0.0–1.0) that a naturally generated glowstone cluster gets its lowest block replaced with glowstone budding.",
                        "自然生成的荧石团最底部方块被替换为荧石母岩的概率（0.0–1.0）。",
                        "0.0 = never, 1.0 = every cluster.",
                        "0.0 = 永不替换，1.0 = 每个荧石团都替换。")
                .defineInRange("glowstoneBuddingChance", 0.5, 0.0, 1.0);

        // ★ 两个新字段：必须在 SPEC = BUILDER.build() 之前定义 ★
        public static final ModConfigSpec.BooleanValue GLOWSTONE_GENERATE_BUDS = BUILDER
                .comment(
                        "Whether glowstone budding also spawns a few glowstone buds/clusters on its side faces.",
                        "荧石母岩是否在其侧面生成少量荧石晶簇/晶芽。",
                        "Whether naturally generated glowstone budding is accompanied by buds.",
                        "自然生成的荧石母岩是否自带晶芽。")
                .define("glowstoneGenerateBuds", true);

        public static final ModConfigSpec.IntValue GLOWSTONE_BUD_COUNT = BUILDER
                .comment(
                        "Number of buds/clusters spawned around each naturally generated glowstone budding block.",
                        "每块自然生成的荧石母岩周围生成晶簇/晶芽的数量上限。",
                        "0 = none.",
                        "0 = 不生成。")
                .defineInRange("glowstoneBudCount", 5, 0, 8);

        public static final ModConfigSpec.BooleanValue GLOWSTONE_BUDS_ON_GLOWSTONE = BUILDER
                .comment(
                        "Whether glowstone buds also generate on nearby glowstone blocks around the budding block.",
                        "晶簇/晶芽是否也会生成在母岩附近的荧石块表面上。",
                        "If false, buds only appear on the budding block's own faces.",
                        "设为 false 时，晶簇只出现在母岩自身的面上。")
                .define("glowstoneBudsOnGlowstone", true);

        public static boolean enabled(String key) {
            return switch (key) {
                case "raw_iron" -> GENERATE_RAW_IRON.get();
                case "raw_gold" -> GENERATE_RAW_GOLD.get();
                case "raw_copper" -> GENERATE_RAW_COPPER.get();
                case "raw_zinc" -> GENERATE_RAW_ZINC.get();
                case "redstone" -> GENERATE_REDSTONE.get();
                case "echo" -> GENERATE_ECHO.get();
                case "glowstone" -> GENERATE_GLOWSTONE.get();
                case "quartz" -> GENERATE_QUARTZ.get();
                default -> true;
            };
        }

        // ===== 回响望远镜 =====
        public static final ModConfigSpec.IntValue SCAN_RADIUS = BUILDER
                .comment(
                        "回响望远镜扫描半径（以玩家为中心的球形半径，单位：方块）",
                        "Scan radius of the Echo Spyglass, in blocks.")
                .defineInRange("scanRadius", 16, 4, 128);

        public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS = BUILDER
                .comment(
                        "两次扫描之间的间隔（tick，20 tick = 1 秒）",
                        "Interval between two scans, in ticks.")
                .defineInRange("scanIntervalTicks", 10, 1, 200);

        public static final ModConfigSpec.IntValue MAX_RESULTS = BUILDER
                .comment(
                        "单次扫描结果上限。防止宽过滤（如\"石头\"）产生超大网络包。",
                        "Maximum number of blocks per scan.",
                        "Prevents huge network packets from overly broad filters (e.g. stone).")
                .defineInRange("maxResults", 4096, 64, 100000);

        public static final ModConfigSpec SPEC = BUILDER.build();
    }
}