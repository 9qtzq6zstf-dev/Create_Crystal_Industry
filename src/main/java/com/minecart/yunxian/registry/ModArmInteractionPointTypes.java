package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.mechanicalArm.AllArmInteractionPointTypes;
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
                event.register(
                        CreateBuiltInRegistries.ARM_INTERACTION_POINT_TYPE.key(),
                        ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "resonance_table"),
                        () -> new ResonanceTableArmPointType());
            }
        });
    }

    /**
     * 共振台也能被机械手插取：这样就能用机械手往台面上换过滤器，
     * 让一条产线按「台面上放的是哪个过滤器」自动切换过滤规则。
     * <p>
     * 注意 Create 官方的置物台交互点（{@code AllArmInteractionPointTypes.DepotType}）是用
     * {@code AllBlocks.DEPOT.has(state)} 精确匹配方块的，我们的方块<b>不会</b>被它认出来，
     * 所以必须在这里单独注册一个。
     */
    private static class ResonanceTableArmPointType extends ArmInteractionPointType {

        @Override
        public boolean canCreatePoint(Level level, BlockPos pos, BlockState state) {
            return state.is(ModBlocks.RESONANCE_TABLE.get());
        }

        @Override
        public ArmInteractionPoint createPoint(Level level, BlockPos pos, BlockState state) {
            // 必须用 Create 的 DepotPoint 而不是基类 ArmInteractionPoint：
            // 基类的交互位置是方块中心，机械手会伸进台子里面；DepotPoint 覆写成
            // 「方块角 + (.5, 14/16, .5)」，正好是台面上那样物品所在的高度
            // （形状与置物台一致，所以同一个位置也适用）。
            return new AllArmInteractionPointTypes.DepotPoint(this, level, pos, state);
        }
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