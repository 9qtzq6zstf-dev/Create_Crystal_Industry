package com.minecart.yunxian;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {
    // 创建创造模式标签页的延迟注册器
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Yunxian.MODID);

    // 注册一个名为 "yunxian" 的标签页
    public static final Supplier<CreativeModeTab> YUNXIAN_TAB =
            CREATIVE_TABS.register("yunxian_tab",
                    () -> CreativeModeTab.builder()
                            // 设置标签页标题（翻译键：itemGroup.yunxian）
                            .title(Component.translatable("itemGroup.yunxian"))
                            // 设置图标（使用完整水晶簇）
                            .icon(() -> new ItemStack(ModBlocks.ROSE_QUARTZ_CLUSTER.get()))
                            // 添加物品到标签页
                            .displayItems((parameters, output) -> {
                                // 按照你希望的顺序添加
                                output.accept(ModBlocks.ROSE_QUARTZ_BUDDING.get());
                                output.accept(ModBlocks.ROSE_QUARTZ_SMALL_BUD.get());
                                output.accept(ModBlocks.ROSE_QUARTZ_MEDIUM_BUD.get());
                                output.accept(ModBlocks.ROSE_QUARTZ_LARGE_BUD.get());
                                output.accept(ModBlocks.ROSE_QUARTZ_CLUSTER.get());
                                output.accept(ModBlocks.ACCELERATOR.get());
                                // 如果你还有其他物品，比如独立物品，也可以添加
                                // 但这里所有方块都已自动注册了 BlockItem
                            })
                            .build()
            );

    // 供主类调用的注册方法
    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}