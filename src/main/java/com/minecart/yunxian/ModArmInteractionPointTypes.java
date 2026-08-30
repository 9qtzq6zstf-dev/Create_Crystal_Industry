package com.minecart.yunxian;

import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPoint;
import com.simibubi.create.content.kinetics.mechanicalArm.ArmInteractionPointType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 机械手交互点类型注册。
 * 让吸尘器成为机械手的可交互容器（DEPOSIT / TAKE 模式均可）。
 *
 * 关键：不能在模组构造器里直接 Registry.register——
 * ARM_INTERACTION_POINT_TYPE 注册表在 Create 构造完成时已冻结。
 * 必须挂在 RegisterEvent 上，在注册阶段（冻结前）完成注册。
 */
public final class ModArmInteractionPointTypes {

    private ModArmInteractionPointTypes() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.key())) {
                event.register(
                        CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.key(),
                        ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "mechanical_cleaner"),
                        () -> new MechanicalCleanerArmPointType());
            }
        });
    }

    private static class MechanicalCleanerArmPointType extends ArmInteractionPointType {

        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return state.is(ModBlocks.MECHANICAL_CLEANER.get());
        }

        @Override
        public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            // 基类交互点：插/取全部走 Capabilities.ItemHandler.BLOCK（Direction.UP），无需额外逻辑
            return new ArmInteractionPoint(this, level, pos, state);
        }
    }
}