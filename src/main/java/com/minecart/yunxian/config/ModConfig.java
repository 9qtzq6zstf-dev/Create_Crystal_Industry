package com.minecart.yunxian.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class ModConfig {
    private ModConfig() {
    }

    public static final class Common {
        private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

        // ===== 世界生成开关 =====
        private static ModConfigSpec.BooleanValue budding(String name, boolean defaultValue) {
            return BUILDER.comment("控制对应母岩是否在世界中生成","Whether the " + name + " budding ore vein generates in the Overworld.")
                    .define("generate_" + name, defaultValue);
        }

        public static final ModConfigSpec.BooleanValue GENERATE_RAW_IRON = budding("raw_iron", true);
        public static final ModConfigSpec.BooleanValue GENERATE_RAW_GOLD = budding("raw_gold", true);
        public static final ModConfigSpec.BooleanValue GENERATE_RAW_COPPER = budding("raw_copper", true);
        public static final ModConfigSpec.BooleanValue GENERATE_RAW_ZINC = budding("raw_zinc", true);
        public static final ModConfigSpec.BooleanValue GENERATE_REDSTONE = budding("redstone", true);
        public static final ModConfigSpec.BooleanValue GENERATE_ECHO = budding("echo", true);

        public static boolean enabled(String key) {
            return switch (key) {
                case "raw_iron" -> GENERATE_RAW_IRON.get();
                case "raw_gold" -> GENERATE_RAW_GOLD.get();
                case "raw_copper" -> GENERATE_RAW_COPPER.get();
                case "raw_zinc" -> GENERATE_RAW_ZINC.get();
                case "redstone" -> GENERATE_REDSTONE.get();
                case "echo" -> GENERATE_ECHO.get();
                default -> true;
            };
        }

        // ===== 回响望远镜 =====
        public static final ModConfigSpec.IntValue SCAN_RADIUS = BUILDER
                .comment("回响望远镜扫描半径（以玩家为中心的球形半径，单位：方块）",
                        "Scan radius of the Echo Spyglass, in blocks.")
                .defineInRange("scanRadius", 16, 4, 128);

        public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS = BUILDER
                .comment("两次扫描之间的间隔（tick，20 tick = 1 秒）",
                        "Interval between two scans, in ticks.")
                .defineInRange("scanIntervalTicks", 10, 1, 200);

        public static final ModConfigSpec.IntValue MAX_RESULTS = BUILDER
                .comment("单次扫描结果上限。防止宽过滤（如\"石头\"）产生超大网络包。",
                        "Maximum number of blocks per scan. ",
                        "Prevents huge network packets from overly broad filters (e.g. stone).")
                .defineInRange("maxResults", 4096, 64, 100000);

        public static final ModConfigSpec SPEC = BUILDER.build();
    }
}