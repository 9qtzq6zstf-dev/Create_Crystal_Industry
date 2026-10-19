package com.minecart.yunxian.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;

/**
 * 「模型数据变了、方块状态没变」时，怎么让客户端真的重画那一块。
 * <p>
 * 常用在连接材质上：几台机器的分组锚点存在方块实体里，换锚点时方块状态一个字都没改。
 */
public final class ClientModelRefresh {

    private ClientModelRefresh() {
    }

    /**
     * 把 {@code pos} 所在的区块段连同四周标脏，逼客户端重建网格。
     * <p>
     * 为什么非做不可：{@code BlockEntity#requestModelDataUpdate} 只是把这一格挂进"下次取模型数据时
     * 要重算"的名单，它自己不会重画任何东西；而方块状态没变时，原版的 {@code setBlock} 又会把同状态的
     * 更新当成没事发生（{@code LevelChunk#setBlockState} 里 {@code blockstate == state} 直接返回 null），
     * 于是网格一直用着旧的数据，直到玩家碰一下旁边某个方块才重画。
     * <p>
     * 只标本段其实就够让这一格重建，但连接材质会读邻居的锚点，同组的可能横跨两个区块段，
     * 所以连四周一起标。
     */
    public static void markSectionDirty(Level level, BlockPos pos) {
        if (level instanceof ClientLevel clientLevel) {
            SectionPos section = SectionPos.of(pos);
            clientLevel.setSectionDirtyWithNeighbors(section.x(), section.y(), section.z());
        }
    }
}
