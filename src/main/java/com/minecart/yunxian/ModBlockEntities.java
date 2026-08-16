package com.minecart.yunxian;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Yunxian.MODID);

    public static final Supplier<BlockEntityType<AcceleratorBlockEntity>> ACCELERATOR =
            BLOCK_ENTITIES.register("accelerator",
                    () -> BlockEntityType.Builder.of(AcceleratorBlockEntity::new,
                            ModBlocks.ACCELERATOR.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}