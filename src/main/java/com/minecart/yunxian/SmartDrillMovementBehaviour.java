package com.minecart.yunxian;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.minecart.yunxian.client.SmartDrillActorVisual;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import org.jetbrains.annotations.Nullable;

public class SmartDrillMovementBehaviour extends DrillMovementBehaviour {

    private static final Logger LOGGER =
            LoggerFactory.getLogger("create_crystal_industry.smart_drill_movement");

    /** canBreak 没有 context 参数，用字段在调用期间中转（服务端单线程） */
    private MovementContext activeContext;

    public SmartDrillMovementBehaviour() {
        LOGGER.error("[SmartDrillMovement] registered");   // ← 环节1的探针
    }

    @Override
    public void visitNewPosition(MovementContext context, BlockPos pos) {
        activeContext = context;
        try {
            super.visitNewPosition(context, pos);
        } finally {
            activeContext = null;
        }
    }

    @Override
    public void tickBreaker(MovementContext context) {
        activeContext = context;
        try {
            super.tickBreaker(context);
        } finally {
            activeContext = null;
        }
    }

    @Override
    public boolean canBreak(Level world, BlockPos breakingPos, BlockState state) {
        if (!super.canBreak(world, breakingPos, state))
            return false;
        if (activeContext == null)
            return true;

        FilterItemStack filter = activeContext.blockEntityData == null
                ? null
                : activeContext.getFilterFromBE();

        boolean allowed = true;
        if (filter != null && !filter.item().isEmpty())
            allowed = filter.test(world, BlockHelper.getRequiredItem(state));

        // ← 环节2+3的探针：filterItem= 显示实际拿到的过滤物品
        LOGGER.error("[SmartDrillMovement] canBreak at {}: filterItem={}, allowed={}",
                breakingPos, filter == null ? "null" : filter.item(), allowed);
        return allowed;
    }

    @Override
    protected void destroyBlock(MovementContext context, BlockPos breakingPos) {
        if (getMode(context) == SmartDrillBlockEntity.DrillMode.PRECISE) {
            ItemStack silkTouchTool = new ItemStack(Items.NETHERITE_PICKAXE);
            Registry<Enchantment> enchantments =
                    context.world.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
            silkTouchTool.enchant(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);

            BlockHelper.destroyBlockAs(context.world, breakingPos, null, silkTouchTool, 1f,
                    stack -> this.collectOrDropItem(context, stack));
        } else {
            super.destroyBlock(context, breakingPos);
        }
    }

    private static SmartDrillBlockEntity.DrillMode getMode(MovementContext context) {
        if (context.blockEntityData == null)
            return SmartDrillBlockEntity.DrillMode.NORMAL;
        int ordinal = context.blockEntityData.getInt("Mode");
        SmartDrillBlockEntity.DrillMode[] modes = SmartDrillBlockEntity.DrillMode.values();
        return ordinal >= 0 && ordinal < modes.length
                ? modes[ordinal]
                : SmartDrillBlockEntity.DrillMode.NORMAL;
    }

    @Nullable
    @Override
    public ActorVisual createVisual(VisualizationContext visualizationContext,
                                    VirtualRenderWorld simulationWorld,
                                    MovementContext movementContext) {
        return new SmartDrillActorVisual(visualizationContext, simulationWorld, movementContext);
    }

    private static final float NORMAL_SPEED_MULTIPLIER = 2.0f;
    private static final float PRECISE_SPEED_MULTIPLIER = 1.0f;

    @Override
    protected float getBlockBreakingSpeed(MovementContext context) {
        float base = super.getBlockBreakingSpeed(context);
        float multiplier = getMode(context) == SmartDrillBlockEntity.DrillMode.PRECISE
                ? PRECISE_SPEED_MULTIPLIER
                : NORMAL_SPEED_MULTIPLIER;
        return base * multiplier;
    }
}