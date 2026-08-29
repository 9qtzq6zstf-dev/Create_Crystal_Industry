package com.minecart.yunxian;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, Yunxian.MODID);

    public static final Supplier<BlockEntityType<AcceleratorBlockEntity>> ACCELERATOR =
            BLOCK_ENTITIES.register("accelerator", () -> BlockEntityType.Builder.of(
                    AcceleratorBlockEntity::new,
                    ModBlocks.ACCELERATOR.get()
            ).build(null));

    public static final Supplier<BlockEntityType<SmartDrillBlockEntity>> SMART_DRILL =
            BLOCK_ENTITIES.register("smart_drill", () -> BlockEntityType.Builder.of(
                    SmartDrillBlockEntity::new,
                    ModBlocks.SMART_DRILL.get()
            ).build(null));

    // 动力催生器
    public static final Supplier<BlockEntityType<MechanicalAcceleratorBlockEntity>> MECHANICAL_ACCELERATOR =
            BLOCK_ENTITIES.register("mechanical_accelerator", () -> BlockEntityType.Builder.of(
                    MechanicalAcceleratorBlockEntity::new,
                    ModBlocks.MECHANICAL_ACCELERATOR.get()
            ).build(null));

    private ModBlockEntities() {
    }

    public static void register(IEventBus modEventBus) {
        BLOCK_ENTITIES.register(modEventBus);
    }
}