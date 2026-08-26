package com.minecart.yunxian.item;

import com.minecart.yunxian.ModTags;
import com.minecart.yunxian.config.EchoConfig;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

public final class EchoScanner {

    private EchoScanner() {
    }

    public static List<BlockPos> findOres(ServerLevel level, BlockPos center, int radius, ItemStack filterStack) {

        // 过滤模式：
        // 0 = 无过滤物品，走 echo_reveals 标签
        // 1 = Create 过滤器（普通 Filter / Create 6 数据驱动的属性过滤器）
        // 2 = 普通物品（方块物品），按方块精确匹配
        int mode;
        FilterItemStack createFilter = null;

        if (filterStack == null || filterStack.isEmpty()) {
            mode = 0;
        } else {
            FilterItemStack parsed = FilterItemStack.of(filterStack);
            if (!parsed.isEmpty()) {
                mode = 1;
                createFilter = parsed;
            } else {
                mode = 2;
            }
        }

        // 从配置读取结果上限（只读一次，不在循环里重复调 get()）
        int maxResults = EchoConfig.MAX_RESULTS.get();

        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int radiusSq = radius * radius;

        outer:
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (found.size() >= maxResults) {
                        break outer;
                    }
                    if (dx * dx + dy * dy + dz * dz > radiusSq) {
                        continue;
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(cursor);

                    boolean match = switch (mode) {
                        case 0 -> state.is(ModTags.ECHO_REVEALS);
                        // Create 的 FilterItemStack 只有物品/流体两个重载，方块必须转成物品再测
                        case 1 -> createFilter.test(level, new ItemStack(state.getBlock().asItem()));
                        default -> state.getBlock().asItem() == filterStack.getItem();
                    };
                    if (match) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        return found;
    }
}