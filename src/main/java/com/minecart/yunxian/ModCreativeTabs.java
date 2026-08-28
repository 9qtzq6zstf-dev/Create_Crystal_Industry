package com.minecart.yunxian;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Yunxian.MODID);

    public static final Supplier<CreativeModeTab> YUNXIAN_TAB = CREATIVE_TABS.register("create_crystal_industry_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_crystal_industry"))
                    .icon(() -> new ItemStack(ModBlocks.ROSE_QUARTZ_CLUSTER.get()))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.ROSE_QUARTZ_BUDDING.get());
                        output.accept(ModBlocks.ROSE_QUARTZ_SMALL_BUD.get());
                        output.accept(ModBlocks.ROSE_QUARTZ_MEDIUM_BUD.get());
                        output.accept(ModBlocks.ROSE_QUARTZ_LARGE_BUD.get());
                        output.accept(ModBlocks.ROSE_QUARTZ_CLUSTER.get());

                        output.accept(ModBlocks.RAW_IRON_BUDDING.get());
                        output.accept(ModBlocks.RAW_IRON_SMALL_BUD.get());
                        output.accept(ModBlocks.RAW_IRON_MEDIUM_BUD.get());
                        output.accept(ModBlocks.RAW_IRON_LARGE_BUD.get());
                        output.accept(ModBlocks.RAW_IRON_CLUSTER.get());

                        output.accept(ModBlocks.RAW_GOLD_BUDDING.get());
                        output.accept(ModBlocks.RAW_GOLD_SMALL_BUD.get());
                        output.accept(ModBlocks.RAW_GOLD_MEDIUM_BUD.get());
                        output.accept(ModBlocks.RAW_GOLD_LARGE_BUD.get());
                        output.accept(ModBlocks.RAW_GOLD_CLUSTER.get());

                        output.accept(ModBlocks.RAW_COPPER_BUDDING.get());
                        output.accept(ModBlocks.RAW_COPPER_SMALL_BUD.get());
                        output.accept(ModBlocks.RAW_COPPER_MEDIUM_BUD.get());
                        output.accept(ModBlocks.RAW_COPPER_LARGE_BUD.get());
                        output.accept(ModBlocks.RAW_COPPER_CLUSTER.get());

                        output.accept(ModBlocks.RAW_ZINC_BUDDING.get());
                        output.accept(ModBlocks.RAW_ZINC_SMALL_BUD.get());
                        output.accept(ModBlocks.RAW_ZINC_MEDIUM_BUD.get());
                        output.accept(ModBlocks.RAW_ZINC_LARGE_BUD.get());
                        output.accept(ModBlocks.RAW_ZINC_CLUSTER.get());

                        output.accept(ModBlocks.ECHO_BUDDING.get());
                        output.accept(ModBlocks.ECHO_SMALL_BUD.get());
                        output.accept(ModBlocks.ECHO_MEDIUM_BUD.get());
                        output.accept(ModBlocks.ECHO_LARGE_BUD.get());
                        output.accept(ModBlocks.ECHO_CLUSTER.get());

                        output.accept(ModBlocks.QUARTZ_BUDDING.get());
                        output.accept(ModBlocks.QUARTZ_SMALL_BUD.get());
                        output.accept(ModBlocks.QUARTZ_MEDIUM_BUD.get());
                        output.accept(ModBlocks.QUARTZ_LARGE_BUD.get());
                        output.accept(ModBlocks.QUARTZ_CLUSTER.get());

                        output.accept(ModBlocks.REDSTONE_BUDDING.get());
                        output.accept(ModBlocks.REDSTONE_SMALL_BUD.get());
                        output.accept(ModBlocks.REDSTONE_MEDIUM_BUD.get());
                        output.accept(ModBlocks.REDSTONE_LARGE_BUD.get());
                        output.accept(ModBlocks.REDSTONE_CLUSTER.get());

                        output.accept(ModBlocks.GLOWSTONE_BUDDING.get());
                        output.accept(ModBlocks.GLOWSTONE_SMALL_BUD.get());
                        output.accept(ModBlocks.GLOWSTONE_MEDIUM_BUD.get());
                        output.accept(ModBlocks.GLOWSTONE_LARGE_BUD.get());
                        output.accept(ModBlocks.GLOWSTONE_CLUSTER.get());

                        output.accept(ModBlocks.FLAMMABLE_ICE_BUDDING.get());
                        output.accept(ModBlocks.FLAMMABLE_ICE_SMALL_BUD.get());
                        output.accept(ModBlocks.FLAMMABLE_ICE_MEDIUM_BUD.get());
                        output.accept(ModBlocks.FLAMMABLE_ICE_LARGE_BUD.get());
                        output.accept(ModBlocks.FLAMMABLE_ICE_CLUSTER.get());
                        output.accept(ModBlocks.FLAMMABLE_ICE_BLOCK.get());
                        output.accept(ModItems.FLAMMABLE_ICE.get());

                        output.accept(ModBlocks.ACCELERATOR.get());
                        output.accept(ModBlocks.SMART_DRILL.get());
                        output.accept(ModItems.ECHO_SPYGLASS.get());
                        output.accept(ModItems.NIGHT_VISION_GOGGLES.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}