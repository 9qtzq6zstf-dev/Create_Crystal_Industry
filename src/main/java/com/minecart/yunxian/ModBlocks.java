package com.minecart.yunxian;

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

    // 注册母岩方块
    public static final DeferredBlock<Block> YUNXIAN_BUDDING =
            registerBlock("yunxian_budding",
                    () -> new YunxianBuddingBlock(5,  // 生长概率 1/5
                            BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST)
                    ));

    public static final DeferredBlock<Block> YUNXIAN_SMALL_BUD =
            registerBlock("yunxian_small_bud",
                    () -> new YunxianClusterBlock(1, 1,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.SMALL_AMETHYST_BUD),
                            "small_bud"
                    ));

    public static final DeferredBlock<Block> YUNXIAN_MEDIUM_BUD =
            registerBlock("yunxian_medium_bud",
                    () -> new YunxianClusterBlock(3, 2,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.MEDIUM_AMETHYST_BUD),
                            "medium_bud"
                    ));

    public static final DeferredBlock<Block> YUNXIAN_LARGE_BUD =
            registerBlock("yunxian_large_bud",
                    () -> new YunxianClusterBlock(5, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.LARGE_AMETHYST_BUD),
                            "large_bud"
                    ));

    public static final DeferredBlock<Block> YUNXIAN_CLUSTER =
            registerBlock("yunxian_cluster",
                    () -> new YunxianClusterBlock(7, 3,
                            BlockBehaviour.Properties.ofFullCopy(Blocks.AMETHYST_CLUSTER)
                                    .noOcclusion()
                                    .isRedstoneConductor((state, level, pos) -> false)
                                    .isSuffocating((state, level, pos) -> false)
                                    .isViewBlocking((state, level, pos) -> false),
                            "cluster"
                    ));
    public static final DeferredBlock<Block> ACCELERATOR =
            registerBlock("accelerator",
                    () -> new AcceleratorBlock(
                            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK) // 可以使用你喜欢的材质
                                    .noOcclusion() // 如果不需要全封闭，可启用
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