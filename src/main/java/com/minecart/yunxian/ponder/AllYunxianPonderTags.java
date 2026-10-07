package com.minecart.yunxian.ponder;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModItems;

import net.createmod.ponder.api.registration.MultiTagBuilder;
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
                .description("Blocks which slowly grow crystals, and the Accelerators which speed them up")
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
                .description("Powered machines which harvest and process crystals")
                .register();

        PonderTagRegistrationHelper<Block> blocks = helper.withKeyFunction(BuiltInRegistries.BLOCK::getKey);

        // 母岩名单由中央定义表派生（AE2 缺席时福鲁伊克斯那条不会注册，自然跳过）
        MultiTagBuilder.Tag<Block> buddingTag = blocks.addToTag(BUDDING).add(Blocks.BUDDING_AMETHYST);
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (family.isRegistered()) {
                buddingTag.add(family.budding().get());
            }
        }
        buddingTag.add(ModBlocks.ACCELERATOR.get())
                .add(ModBlocks.MECHANICAL_ACCELERATOR.get());

        blocks.addToTag(ACCELERATORS)
                .add(ModBlocks.ACCELERATOR.get())
                .add(ModBlocks.MECHANICAL_ACCELERATOR.get());

        blocks.addToTag(MACHINES)
                .add(ModBlocks.SMART_DRILL.get())
                .add(ModBlocks.MECHANICAL_CLEANER.get())
                .add(ModBlocks.RESONANCE_TABLE.get())
                .add(ModBlocks.SMART_TEMPERATURE_CHAMBER.get());

        // 共振过滤器是物品，走物品视角的那份 helper 进同一个标签
        helper.withKeyFunction(BuiltInRegistries.ITEM::getKey)
                .addToTag(MACHINES)
                .add(ModItems.RESONANCE_FILTER.get());
    }

}