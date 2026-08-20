package com.minecart.yunxian;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Yunxian.MODID);

    // Rose quartz
    public static final DeferredBlock<Block> ROSE_QUARTZ_SMALL_BUD = bud("rose_quartz_small_bud", Blocks.SMALL_AMETHYST_BUD, 1, 1, "small_bud");
    public static final DeferredBlock<Block> ROSE_QUARTZ_MEDIUM_BUD = bud("rose_quartz_medium_bud", Blocks.MEDIUM_AMETHYST_BUD, 3, 2, "medium_bud");
    public static final DeferredBlock<Block> ROSE_QUARTZ_LARGE_BUD = bud("rose_quartz_large_bud", Blocks.LARGE_AMETHYST_BUD, 5, 3, "large_bud");
    public static final DeferredBlock<Block> ROSE_QUARTZ_CLUSTER = cluster("rose_quartz_cluster");
    public static final DeferredBlock<Block> ROSE_QUARTZ_BUDDING = registerBlock("rose_quartz_budding",
            () -> new GenericBuddingBlock(5, BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST),
                    ROSE_QUARTZ_SMALL_BUD.get(), ROSE_QUARTZ_MEDIUM_BUD.get(),
                    ROSE_QUARTZ_LARGE_BUD.get(), ROSE_QUARTZ_CLUSTER.get()));

    // Raw iron
    public static final DeferredBlock<Block> RAW_IRON_SMALL_BUD = bud("raw_iron_small_bud", Blocks.SMALL_AMETHYST_BUD, 1, 1, "small_bud");
    public static final DeferredBlock<Block> RAW_IRON_MEDIUM_BUD = bud("raw_iron_medium_bud", Blocks.MEDIUM_AMETHYST_BUD, 3, 2, "medium_bud");
    public static final DeferredBlock<Block> RAW_IRON_LARGE_BUD = bud("raw_iron_large_bud", Blocks.LARGE_AMETHYST_BUD, 5, 3, "large_bud");
    public static final DeferredBlock<Block> RAW_IRON_CLUSTER = cluster("raw_iron_cluster");
    public static final DeferredBlock<Block> RAW_IRON_BUDDING = oreBudding(
            "raw_iron_budding", RAW_IRON_SMALL_BUD, RAW_IRON_MEDIUM_BUD, RAW_IRON_LARGE_BUD,
            RAW_IRON_CLUSTER,
            () -> Blocks.IRON_ORE, () -> Blocks.DEEPSLATE_IRON_ORE, () -> Blocks.RAW_IRON_BLOCK);

    // Raw gold
    public static final DeferredBlock<Block> RAW_GOLD_SMALL_BUD = bud("raw_gold_small_bud", Blocks.SMALL_AMETHYST_BUD, 1, 1, "small_bud");
    public static final DeferredBlock<Block> RAW_GOLD_MEDIUM_BUD = bud("raw_gold_medium_bud", Blocks.MEDIUM_AMETHYST_BUD, 3, 2, "medium_bud");
    public static final DeferredBlock<Block> RAW_GOLD_LARGE_BUD = bud("raw_gold_large_bud", Blocks.LARGE_AMETHYST_BUD, 5, 3, "large_bud");
    public static final DeferredBlock<Block> RAW_GOLD_CLUSTER = cluster("raw_gold_cluster");
    public static final DeferredBlock<Block> RAW_GOLD_BUDDING = oreBudding(
            "raw_gold_budding", RAW_GOLD_SMALL_BUD, RAW_GOLD_MEDIUM_BUD, RAW_GOLD_LARGE_BUD,
            RAW_GOLD_CLUSTER,
            () -> Blocks.GOLD_ORE, () -> Blocks.DEEPSLATE_GOLD_ORE, () -> Blocks.RAW_GOLD_BLOCK);

    // Raw copper
    public static final DeferredBlock<Block> RAW_COPPER_SMALL_BUD = bud("raw_copper_small_bud", Blocks.SMALL_AMETHYST_BUD, 1, 1, "small_bud");
    public static final DeferredBlock<Block> RAW_COPPER_MEDIUM_BUD = bud("raw_copper_medium_bud", Blocks.MEDIUM_AMETHYST_BUD, 3, 2, "medium_bud");
    public static final DeferredBlock<Block> RAW_COPPER_LARGE_BUD = bud("raw_copper_large_bud", Blocks.LARGE_AMETHYST_BUD, 5, 3, "large_bud");
    public static final DeferredBlock<Block> RAW_COPPER_CLUSTER = cluster("raw_copper_cluster");
    public static final DeferredBlock<Block> RAW_COPPER_BUDDING = oreBudding(
            "raw_copper_budding", RAW_COPPER_SMALL_BUD, RAW_COPPER_MEDIUM_BUD, RAW_COPPER_LARGE_BUD,
            RAW_COPPER_CLUSTER,
            () -> Blocks.COPPER_ORE, () -> Blocks.DEEPSLATE_COPPER_ORE, () -> Blocks.RAW_COPPER_BLOCK);

    // Raw zinc (Create)
    public static final DeferredBlock<Block> RAW_ZINC_SMALL_BUD = bud("raw_zinc_small_bud", Blocks.SMALL_AMETHYST_BUD, 1, 1, "small_bud");
    public static final DeferredBlock<Block> RAW_ZINC_MEDIUM_BUD = bud("raw_zinc_medium_bud", Blocks.MEDIUM_AMETHYST_BUD, 3, 2, "medium_bud");
    public static final DeferredBlock<Block> RAW_ZINC_LARGE_BUD = bud("raw_zinc_large_bud", Blocks.LARGE_AMETHYST_BUD, 5, 3, "large_bud");
    public static final DeferredBlock<Block> RAW_ZINC_CLUSTER = cluster("raw_zinc_cluster");
    public static final DeferredBlock<Block> RAW_ZINC_BUDDING = oreBudding(
            "raw_zinc_budding", RAW_ZINC_SMALL_BUD, RAW_ZINC_MEDIUM_BUD, RAW_ZINC_LARGE_BUD,
            RAW_ZINC_CLUSTER,
            () -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:zinc_ore")),
            () -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:deepslate_zinc_ore")),
            () -> BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:raw_zinc_block")));

    // Echo
    public static final DeferredBlock<Block> ECHO_SMALL_BUD = bud("echo_small_bud", Blocks.SMALL_AMETHYST_BUD, 1, 1, "small_bud");
    public static final DeferredBlock<Block> ECHO_MEDIUM_BUD = bud("echo_medium_bud", Blocks.MEDIUM_AMETHYST_BUD, 3, 2, "medium_bud");
    public static final DeferredBlock<Block> ECHO_LARGE_BUD = bud("echo_large_bud", Blocks.LARGE_AMETHYST_BUD, 5, 3, "large_bud");
    public static final DeferredBlock<Block> ECHO_CLUSTER = cluster("echo_cluster");
    public static final DeferredBlock<Block> ECHO_BUDDING = registerBlock("echo_budding",
            () -> new EchoConvertingBuddingBlock(5, BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST),
                    ECHO_SMALL_BUD.get(), ECHO_MEDIUM_BUD.get(), ECHO_LARGE_BUD.get(), ECHO_CLUSTER.get()));

    public static final DeferredBlock<Block> ACCELERATOR = registerBlock("accelerator",
            () -> new AcceleratorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    public static final DeferredBlock<Block> SMART_DRILL = registerBlock("smart_drill",
            () -> new SmartDrillBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    private ModBlocks() {
    }

    private static DeferredBlock<Block> bud(String name, Block copyFrom, int stage, int height, String stageKey) {
        return registerBlock(name, () -> new YunxianClusterBlock(
                stage, height, BlockBehaviour.Properties.ofFullCopy(copyFrom), stageKey));
    }

    private static DeferredBlock<Block> cluster(String name) {
        return registerBlock(name, () -> new YunxianClusterBlock(
                7,
                3,
                BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                        .noOcclusion()
                        .isRedstoneConductor((state, level, pos) -> false)
                        .isSuffocating((state, level, pos) -> false)
                        .isViewBlocking((state, level, pos) -> false),
                "cluster"
        ));
    }

    private static DeferredBlock<Block> oreBudding(String name,
                                                   DeferredBlock<Block> small,
                                                   DeferredBlock<Block> medium,
                                                   DeferredBlock<Block> large,
                                                   DeferredBlock<Block> cluster,
                                                   Supplier<Block> stoneOre,
                                                   Supplier<Block> deepslateOre,
                                                   Supplier<Block> rawOreBlock) {
        return registerBlock(name, () -> new OreConvertingBuddingBlock(
                5,
                BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST),
                small.get(), medium.get(), large.get(), cluster.get(),
                stoneOre, deepslateOre, rawOreBlock
        ));
    }

    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockSupplier) {
        DeferredBlock<T> block = BLOCKS.register(name, blockSupplier);
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
