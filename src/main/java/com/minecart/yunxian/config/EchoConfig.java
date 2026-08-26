package com.minecart.yunxian.config;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class EchoConfig {

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue SCAN_RADIUS;
    public static final ModConfigSpec.IntValue SCAN_INTERVAL_TICKS;
    public static final ModConfigSpec.IntValue MAX_RESULTS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        SCAN_RADIUS = builder
                .comment("回响望远镜扫描半径（以玩家为中心的球形半径，单位：方块）",
                        "Scan radius of the Echo Spyglass, in blocks.")
                .defineInRange("scanRadius", 16, 4, 128);

        SCAN_INTERVAL_TICKS = builder
                .comment("两次扫描之间的间隔（tick，20 tick = 1 秒）",
                        "Interval between two scans, in ticks.")
                .defineInRange("scanIntervalTicks", 10, 1, 200);

        MAX_RESULTS = builder
                .comment("单次扫描结果上限。防止宽过滤（如\"石头\"）产生超大网络包。",
                        "Maximum number of blocks per scan. ",
                        "Prevents huge network packets from overly broad filters (e.g. stone).")
                .defineInRange("maxResults", 4096, 64, 100000);

        SPEC = builder.build();
    }

    private EchoConfig() {
    }
}