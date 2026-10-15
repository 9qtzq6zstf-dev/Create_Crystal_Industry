package com.minecart.yunxian.resonance;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 共振台网络的<b>名字</b>：网络 id（UUID）→ 玩家在共振过滤器界面里填的那行字。
 * <p>
 * <b>为什么名字不能像过滤规则那样存在物品或方块上</b>：规则是「这个过滤器怎么判」，
 * 存在物品自己身上天经地义；而名字是「这张网络叫什么」，同一张网络上的每个过滤器、
 * 每张台子看到的必须是同一个名字。物品或方块各存一份的话，谁后写的算数，
 * 换个物品看同一个网络就变成另一个名字了。
 * <p>
 * 所以这里跟 Create 存物流网络状态的做法一样（{@code LogisticsNetworkSavedData}）：
 * 按网络 id 记在世界存档里。<b>存在主世界的数据存储上</b>，因为网络是跨维度的，
 * 名字也必须只有一份。名字为空 = 没名字，顺手把条目删掉，别让表一直涨。
 * <p>
 * 只服务端读写：客户端那份名字是开界面时随数据流带过去的（见
 * {@code ResonanceFilterItem#use} 与 {@code ResonanceFilterMenu#createOnClient}）。
 */
public class ResonanceNetworkNames extends SavedData {

    /** 存档里的文件名，落在主世界的数据存储中 */
    private static final String DATA_NAME = "crystal_resonance_networks";

    private final Map<UUID, String> names = new HashMap<>();

    public static SavedData.Factory<ResonanceNetworkNames> factory() {
        return new SavedData.Factory<>(ResonanceNetworkNames::new, ResonanceNetworkNames::load);
    }

    public static ResonanceNetworkNames get(MinecraftServer server) {
        return server.overworld()
                .getDataStorage()
                .computeIfAbsent(factory(), DATA_NAME);
    }

    /** 没起过名字的网络返回空串 —— 调用方一律拿它当"没有名字"，不必再判 null */
    public String getName(UUID network) {
        String name = names.get(network);
        return name == null ? "" : name;
    }

    public void setName(UUID network, String name) {
        if (name.isBlank()) {
            if (names.remove(network) != null)
                setDirty();
            return;
        }
        if (!name.equals(names.put(network, name)))
            setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag nbt, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        names.forEach((id, name) -> {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", id);
            entry.putString("Name", name);
            list.add(entry);
        });
        nbt.put("Networks", list);
        return nbt;
    }

    private static ResonanceNetworkNames load(CompoundTag nbt, HolderLookup.Provider registries) {
        ResonanceNetworkNames data = new ResonanceNetworkNames();
        ListTag list = nbt.getList("Networks", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.hasUUID("Id"))
                continue;
            data.names.put(entry.getUUID("Id"), entry.getString("Name"));
        }
        return data;
    }
}
