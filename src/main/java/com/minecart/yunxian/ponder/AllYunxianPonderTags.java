package com.minecart.yunxian.ponder;

import com.minecart.yunxian.registry.ModBlocks;

import net.createmod.ponder.api.registration.PonderTagRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class AllYunxianPonderTags {

    public static final ResourceLocation BUDDING = loc("budding");
    public static final ResourceLocation ACCELERATORS = loc("accelerators");
    public static final ResourceLocation MACHINES = loc("machines");

    private static ResourceLocation loc(String id) {
        return ResourceLocation.fromNamespaceAndPath("create_crystal_industry", id);
    }

    public static void register(PonderTagRegistrationHelper<ResourceLocation> helper) {
        helper.registerTag(BUDDING)
                .addToIndex()
                .item(Items.AMETHYST_CLUSTER, true, false)
                .title("Crystal Budding Blocks")
                .description("Blocks that slowly grow crystals, and the Accelerators that speed them up")
                .register();

        helper.registerTag(ACCELERATORS)
                .addToIndex()
                .item(ModBlocks.ACCELERATOR.get().asItem(), true, false)
                .title("Accelerators")
                .description("Machines which force random ticks onto adjacent blocks")
                .register();

        helper.registerTag(MACHINES)
                .addToIndex()
                .item(ModBlocks.SMART_DRILL.get().asItem(), true, false)
                .title("Machines")
                .description("Powered machines that harvest and process crystals")
                .register();

        PonderTagRegistrationHelper<Block> blocks = helper.withKeyFunction(BuiltInRegistries.BLOCK::getKey);

        blocks.addToTag(BUDDING)
                .add(Blocks.BUDDING_AMETHYST)
                .add(ModBlocks.ROSE_QUARTZ_BUDDING.get())
                .add(ModBlocks.RAW_IRON_BUDDING.get())
                .add(ModBlocks.RAW_GOLD_BUDDING.get())
                .add(ModBlocks.RAW_COPPER_BUDDING.get())
                .add(ModBlocks.RAW_ZINC_BUDDING.get())
                .add(ModBlocks.DIAMOND_BUDDING.get())
                .add(ModBlocks.EMERALD_BUDDING.get())
                .add(ModBlocks.LAPIS_BUDDING.get())
                .add(ModBlocks.ECHO_BUDDING.get())
                .add(ModBlocks.QUARTZ_BUDDING.get())
                .add(ModBlocks.REDSTONE_BUDDING.get())
                .add(ModBlocks.GLOWSTONE_BUDDING.get())
                .add(ModBlocks.FLAMMABLE_ICE_BUDDING.get())
                .add(ModBlocks.ACCELERATOR.get())
                .add(ModBlocks.MECHANICAL_ACCELERATOR.get());

        blocks.addToTag(ACCELERATORS)
                .add(ModBlocks.ACCELERATOR.get())
                .add(ModBlocks.MECHANICAL_ACCELERATOR.get());

        blocks.addToTag(MACHINES)
                .add(ModBlocks.SMART_DRILL.get())
                .add(ModBlocks.MECHANICAL_CLEANER.get());

        if (ModBlocks.FLUIX_BUDDING != null)
            blocks.addToTag(BUDDING)
                    .add(ModBlocks.FLUIX_BUDDING.get());
    }

}