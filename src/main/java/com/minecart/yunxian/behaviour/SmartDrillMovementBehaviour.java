package com.minecart.yunxian.behaviour;

import com.minecart.yunxian.blockentity.SmartDrillBlockEntity;
import com.minecart.yunxian.registry.ScriptedBlockDrops;
import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.kinetics.drill.DrillMovementBehaviour;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import com.minecart.yunxian.client.mechanical.SmartDrillActorVisual;
import com.simibubi.create.content.contraptions.render.ActorVisual;
import com.simibubi.create.foundation.virtualWorld.VirtualRenderWorld;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import org.jetbrains.annotations.Nullable;

public class SmartDrillMovementBehaviour extends DrillMovementBehaviour {

    /** canBreak 没有 context 参数，用字段在调用期间中转（服务端单线程） */
    private MovementContext activeContext;

    /** c:budding_blocks 方块标签（data/c/tags/block/...，如存在） */
    private static final TagKey<Block> BUDDING_BLOCKS_BLOCK_TAG =
            TagKey.create(Registries.BLOCK, ResourceLocation.parse("c:budding_blocks"));
    /** c:budding_blocks 物品标签（data/c/tags/item/...，当前实际生效的那份） */
    private static final TagKey<Item> BUDDING_BLOCKS_ITEM_TAG =
            TagKey.create(Registries.ITEM, ResourceLocation.parse("c:budding_blocks"));

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

        return filter == null
                || filter.item().isEmpty()
                || filter.test(world, BlockHelper.getRequiredItem(state));
    }

    /**
     * 命中 c:budding_blocks（物品标签或方块标签任一）→ 精准模式直接掉落方块自身。
     * 注意：钻头破坏的是方块状态，若只存在物品标签，必须通过方块对应的物品去匹配。
     */
    private static boolean isBuddingBlock(BlockState state) {
        if (state.is(BUDDING_BLOCKS_BLOCK_TAG)) {
            return true;
        }
        Item item = state.getBlock().asItem();
        return item != Items.AIR && new ItemStack(item).is(BUDDING_BLOCKS_ITEM_TAG);
    }

    @Override
    protected void destroyBlock(MovementContext context, BlockPos breakingPos) {
        BlockState state = context.world.getBlockState(breakingPos);
        boolean precise = getMode(context) == SmartDrillBlockEntity.DrillMode.PRECISE;

        // 脚本（KubeJS）注册的方块没有战利品表，掉落规则由 ScriptedBlockDrops 决定，
        // 不能交给 Create 的挖掘辅助：它只把 BlockDropsEvent 里补发的产物 popResource 到世界里，
        // 动态结构一个都收不到（掉落物会落在方块那儿的地上）。静态钻头对这类方块另有分支，
        // 见 SmartDrillBlockEntity#onBlockBroken——两处都按同一套规则自己算掉落再交出去。
        // 脚本注册的母岩也走这里：它在 c:budding_blocks 里，但 ScriptedBlockDrops 的规则与母岩一致
        // （精准采集掉本体、普通破坏按登记的方式掉，母岩登记的是"什么都不掉"），不必再单独判一次。
        if (ScriptedBlockDrops.isScripted(state.getBlock())) {
            context.world.destroyBlock(breakingPos, false);
            for (ItemStack stack : ScriptedBlockDrops.dropsFor(state,
                    precise ? silkTouchTool(context.world) : ItemStack.EMPTY)) {
                if (!stack.isEmpty())
                    collectOrDropItem(context, stack);
            }
            return;
        }

        if (!precise) {
            super.destroyBlock(context, breakingPos);
            return;
        }

        // 命中 c:budding_blocks 的方块不遵循原版掉落表：精准模式下直接掉落方块自身。
        // 这里必须用「不产生任何掉落」的破坏方式——BlockHelper.destroyBlock 会先照原版掉落表
        // 发一份（母岩那张表掉的是低一档的方块，且不吃精准采集），再补方块本体就成了两份：
        // 动态结构上表现为精准模式额外掉出普通模式的产物。静态的智能钻头同样走
        // destroyBlock(pos, false)（见 SmartDrillBlockEntity#onBlockBroken），两处保持一致。
        if (isBuddingBlock(state)) {
            context.world.destroyBlock(breakingPos, false);
            collectOrDropItem(context, new ItemStack(state.getBlock()));
            return;
        }

        BlockHelper.destroyBlockAs(context.world, breakingPos, null, silkTouchTool(context.world), 1f,
                stack -> this.collectOrDropItem(context, stack));
    }

    /** 钻头本身没有工具：精准模式用一把附了精准采集的下界合金镐来模拟（掉落表与掉落规则都只读附魔） */
    private static ItemStack silkTouchTool(Level world) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        Registry<Enchantment> enchantments =
                world.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        tool.enchant(enchantments.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        return tool;
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