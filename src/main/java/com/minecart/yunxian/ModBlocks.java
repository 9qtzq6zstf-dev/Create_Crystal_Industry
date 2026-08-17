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

public class ModBlocks {
    // 创建一个延迟注册器，用于注册所有方块
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Yunxian.MODID);
    // ===== 玫瑰石英系列 =====
    public static final DeferredBlock<Block> ROSE_QUARTZ_SMALL_BUD =
            registerBlock("rose_quartz_small_bud",
                    () -> new YunxianClusterBlock(1, 1,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.SMALL_AMETHYST_BUD),
                            "small_bud"
                    ));

    public static final DeferredBlock<Block> ROSE_QUARTZ_MEDIUM_BUD =
            registerBlock("rose_quartz_medium_bud",
                    () -> new YunxianClusterBlock(3, 2,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.MEDIUM_AMETHYST_BUD),
                            "medium_bud"
                    ));

    public static final DeferredBlock<Block> ROSE_QUARTZ_LARGE_BUD =
            registerBlock("rose_quartz_large_bud",
                    () -> new YunxianClusterBlock(5, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.LARGE_AMETHYST_BUD),
                            "large_bud"
                    ));

    public static final DeferredBlock<Block> ROSE_QUARTZ_CLUSTER =
            registerBlock("rose_quartz_cluster",
                    () -> new YunxianClusterBlock(7, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false),
                            "cluster"
                    ));

    public static final DeferredBlock<Block> ROSE_QUARTZ_BUDDING =
            registerBlock("rose_quartz_budding",
                    () -> new GenericBuddingBlock(5,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST),
                            ROSE_QUARTZ_SMALL_BUD.get(),
                            ROSE_QUARTZ_MEDIUM_BUD.get(),
                            ROSE_QUARTZ_LARGE_BUD.get(),
                            ROSE_QUARTZ_CLUSTER.get()
                    ));
    // ===== 粗铁系列 =====
    // ===== 粗铁系列 =====
    public static final DeferredBlock<Block> RAW_IRON_SMALL_BUD =
            registerBlock("raw_iron_small_bud",
                    () -> new YunxianClusterBlock(1, 1,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.SMALL_AMETHYST_BUD),
                            "small_bud"
                    ));

    public static final DeferredBlock<Block> RAW_IRON_MEDIUM_BUD =
            registerBlock("raw_iron_medium_bud",
                    () -> new YunxianClusterBlock(3, 2,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.MEDIUM_AMETHYST_BUD),
                            "medium_bud"
                    ));

    public static final DeferredBlock<Block> RAW_IRON_LARGE_BUD =
            registerBlock("raw_iron_large_bud",
                    () -> new YunxianClusterBlock(5, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.LARGE_AMETHYST_BUD),
                            "large_bud"
                    ));

    public static final DeferredBlock<Block> RAW_IRON_CLUSTER =
            registerBlock("raw_iron_cluster",
                    () -> new YunxianClusterBlock(7, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false),
                            "cluster"
                    ));
    public static final DeferredBlock<Block> RAW_IRON_BUDDING =
            registerBlock("raw_iron_budding",
                    () -> new OreConvertingBuddingBlock(5,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST),
                            RAW_IRON_SMALL_BUD.get(),
                            RAW_IRON_MEDIUM_BUD.get(),
                            RAW_IRON_LARGE_BUD.get(),
                            RAW_IRON_CLUSTER.get(),
                            Blocks.IRON_ORE,           // 石头→铁矿石
                            Blocks.DEEPSLATE_IRON_ORE  // 深板岩→深层铁矿石
                    ));
    // ===== 粗金系列 =====
    public static final DeferredBlock<Block> RAW_GOLD_SMALL_BUD =
            registerBlock("raw_gold_small_bud",
                    () -> new YunxianClusterBlock(1, 1,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.SMALL_AMETHYST_BUD),
                            "small_bud"
                    ));

    public static final DeferredBlock<Block> RAW_GOLD_MEDIUM_BUD =
            registerBlock("raw_gold_medium_bud",
                    () -> new YunxianClusterBlock(3, 2,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.MEDIUM_AMETHYST_BUD),
                            "medium_bud"
                    ));

    public static final DeferredBlock<Block> RAW_GOLD_LARGE_BUD =
            registerBlock("raw_gold_large_bud",
                    () -> new YunxianClusterBlock(5, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.LARGE_AMETHYST_BUD),
                            "large_bud"
                    ));

    public static final DeferredBlock<Block> RAW_GOLD_CLUSTER =
            registerBlock("raw_gold_cluster",
                    () -> new YunxianClusterBlock(7, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false),
                            "cluster"
                    ));

    public static final DeferredBlock<Block> RAW_GOLD_BUDDING =
            registerBlock("raw_gold_budding",
                    () -> new OreConvertingBuddingBlock(5,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST),
                            RAW_GOLD_SMALL_BUD.get(),
                            RAW_GOLD_MEDIUM_BUD.get(),
                            RAW_GOLD_LARGE_BUD.get(),
                            RAW_GOLD_CLUSTER.get(),
                            Blocks.GOLD_ORE,           // 石头→金矿石
                            Blocks.DEEPSLATE_GOLD_ORE  // 深板岩→深层金矿石
                    ));
    // ===== 粗铜系列 =====
    public static final DeferredBlock<Block> RAW_COPPER_SMALL_BUD =
            registerBlock("raw_copper_small_bud",
                    () -> new YunxianClusterBlock(1, 1,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.SMALL_AMETHYST_BUD),
                            "small_bud"
                    ));

    public static final DeferredBlock<Block> RAW_COPPER_MEDIUM_BUD =
            registerBlock("raw_copper_medium_bud",
                    () -> new YunxianClusterBlock(3, 2,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.MEDIUM_AMETHYST_BUD),
                            "medium_bud"
                    ));

    public static final DeferredBlock<Block> RAW_COPPER_LARGE_BUD =
            registerBlock("raw_copper_large_bud",
                    () -> new YunxianClusterBlock(5, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.LARGE_AMETHYST_BUD),
                            "large_bud"
                    ));

    public static final DeferredBlock<Block> RAW_COPPER_CLUSTER =
            registerBlock("raw_copper_cluster",
                    () -> new YunxianClusterBlock(7, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false),
                            "cluster"
                    ));

    public static final DeferredBlock<Block> RAW_COPPER_BUDDING =
            registerBlock("raw_copper_budding",
                    () -> new OreConvertingBuddingBlock(5,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST),
                            RAW_COPPER_SMALL_BUD.get(),
                            RAW_COPPER_MEDIUM_BUD.get(),
                            RAW_COPPER_LARGE_BUD.get(),
                            RAW_COPPER_CLUSTER.get(),
                            Blocks.COPPER_ORE,         // 石头→铜矿石
                            Blocks.DEEPSLATE_COPPER_ORE // 深板岩→深层铜矿石
                    ));
    // ===== 粗锌系列 =====
    public static final DeferredBlock<Block> RAW_ZINC_SMALL_BUD =
            registerBlock("raw_zinc_small_bud",
                    () -> new YunxianClusterBlock(1, 1,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.SMALL_AMETHYST_BUD),
                            "small_bud"
                    ));

    public static final DeferredBlock<Block> RAW_ZINC_MEDIUM_BUD =
            registerBlock("raw_zinc_medium_bud",
                    () -> new YunxianClusterBlock(3, 2,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.MEDIUM_AMETHYST_BUD),
                            "medium_bud"
                    ));

    public static final DeferredBlock<Block> RAW_ZINC_LARGE_BUD =
            registerBlock("raw_zinc_large_bud",
                    () -> new YunxianClusterBlock(5, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.LARGE_AMETHYST_BUD),
                            "large_bud"
                    ));

    public static final DeferredBlock<Block> RAW_ZINC_CLUSTER =
            registerBlock("raw_zinc_cluster",
                    () -> new YunxianClusterBlock(7, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false),
                            "cluster"
                    ));

    public static final DeferredBlock<Block> RAW_ZINC_BUDDING =
            registerBlock("raw_zinc_budding",
                    () -> new OreConvertingBuddingBlock(5,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST),
                            RAW_ZINC_SMALL_BUD.get(),
                            RAW_ZINC_MEDIUM_BUD.get(),
                            RAW_ZINC_LARGE_BUD.get(),
                            RAW_ZINC_CLUSTER.get(),
                            // 锌矿石（机械动力物品）
                            BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:zinc_ore")),
                            BuiltInRegistries.BLOCK.get(ResourceLocation.parse("create:deepslate_zinc_ore"))
                    ));
    //催生器
    // ===== 催生器 =====
    public static final DeferredBlock<Block> ACCELERATOR =
            registerBlock("accelerator",
                    () -> new AcceleratorBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false)
                    ));
    //智能钻头
    public static final DeferredBlock<Block> SMART_DRILL =
            registerBlock("smart_drill",
                    () -> new SmartDrillBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                                    .noOcclusion()
                    ));


    // 辅助方法：同时注册方块和对应的物品
    private static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockSupplier) {
        DeferredBlock<T> block = BLOCKS.register(name, blockSupplier);
        // 每个方块都需要一个 BlockItem 才能拿在手里
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
        return block;
    }

    // 这个方法会在主类中被调用
    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}