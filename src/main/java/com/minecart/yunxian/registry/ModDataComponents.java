package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.mojang.serialization.Codec;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.UUID;

/**
 * 本模组的物品数据组件。
 * <p>
 * 只放「必须跟着物品走、且要被 Create 的过滤器判定读到」的东西。共振过滤器所在的网络 id 就是典型：
 * 它必须存在物品自己身上，因为 {@code FilteringBehaviour.read()} 是从 NBT 里的 ItemStack
 * 重新构造判定对象的（见 {@code ResonanceFilterItemStack} 的类注释），方块实体那边不留任何状态。
 */
public final class ModDataComponents {

    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Yunxian.MODID);

    /**
     * 共振过滤器所在的那个共振台网络（一个 UUID）。
     * <p>
     * 用 id 而不是坐标：网络是「一组共振台」，跟具体在哪、有几个、跨不跨维度都无关，
     * 而且共振台被拆掉重放也能续上同一个网络（见 {@code LogisticallyLinkedBehaviour}）。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<UUID>> RESONANCE_NETWORK =
            DATA_COMPONENTS.register("resonance_network", () -> DataComponentType.<UUID>builder()
                    .persistent(UUIDUtil.CODEC)
                    .networkSynchronized(UUIDUtil.STREAM_CODEC)
                    .build());

    /**
     * 共振过滤器所在网络的名字，<b>只是一份给客户端看的副本</b>。
     * <p>
     * 名字的正主是 {@code ResonanceNetworkNames} 那张按网络 id 记在世界存档里的表，只有服务端读得到；
     * 而工具提示是客户端画的，客户端手里只有这件物品自己的组件。所以把名字抄一份挂在物品上，
     * 和网络 id 一样跟着物品走（进箱子、进蓝图、被搬来搬去都带着）。
     * 服务端在接入网络时写上、退出网络时摘掉，之后靠 {@code ResonanceFilterItem#inventoryTick}
     * 每秒对一次答案 —— <b>别拿它当正主</b>，判定与界面一律走那张表。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> RESONANCE_NETWORK_NAME =
            DATA_COMPONENTS.register("resonance_network_name", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /**
     * 共振过滤器的名单模式：{@code true} = 黑名单（把网络判定的结果取反），缺省不存在 = 白名单。
     * <p>
     * 和网络 id 一样必须存在物品自己身上：判定对象是靠 {@code FilterItemStack.of(ItemStack)} 现场重建的。
     * 但<b>规则不在这里</b> —— 规则永远取自共振台，这里只记「拿到网络结果之后怎么用」。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RESONANCE_FILTER_BLACKLIST =
            DATA_COMPONENTS.register("resonance_filter_blacklist", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    /**
     * 共振过滤器的匹配模式：{@code true} = 连数据组件一起比（附魔、自定义名称等不同即算不同物品）。
     * <p>
     * <b>只收紧、不放松</b>：缺省不存在时沿用调用方传进来的 {@code matchNBT}，行为与加这个开关之前一致；
     * 打开后无论调用方怎么说都按严格比。这样老存档里的过滤器判法不会因为这次改动而变。
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> RESONANCE_FILTER_RESPECT_DATA =
            DATA_COMPONENTS.register("resonance_filter_respect_data", () -> DataComponentType.<Boolean>builder()
                    .persistent(Codec.BOOL)
                    .networkSynchronized(ByteBufCodecs.BOOL)
                    .build());

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
