package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import net.minecraft.core.UUIDUtil;
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

    private ModDataComponents() {
    }

    public static void register(IEventBus modEventBus) {
        DATA_COMPONENTS.register(modEventBus);
    }
}
