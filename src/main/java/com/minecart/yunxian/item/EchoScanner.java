package com.minecart.yunxian.item;

import com.minecart.yunxian.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;

public final class EchoScanner {

    private EchoScanner() {
    }

    public static List<BlockPos> findOres(ServerLevel level, BlockPos center, int radius) {
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int radiusSq = radius * radius;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) {
                        continue;   // 球形范围
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    // 只扫描已加载区块，避免服务器因为扫描而疯狂加载/生成区块
                    if (level.isLoaded(cursor) && level.getBlockState(cursor).is(ModTags.ECHO_REVEALS)) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        return found;
    }
}