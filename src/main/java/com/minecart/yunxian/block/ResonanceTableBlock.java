package com.minecart.yunxian.block;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.AllShapes;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.logistics.depot.SharedDepotBlockMethods;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.block.ProperWaterloggedBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * 共振台：一个「能放一样东西、并且会被共振过滤器读到」的台子。
 * <p>
 * 形态与交互完全照抄 Create 的置物台（{@code DepotBlock} + {@code SharedDepotBlockMethods}）：
 * 形状是 13px 的机壳，上面能放一样物品，机械臂/传送带/漏斗都能进出。这么做的原因是
 * 共振台要复用的正是 Create 的 {@code DepotBehaviour}（见 {@code ResonanceTableBlockEntity}），
 * 方块形状与交互保持一致才不会看起来怪。
 * <p>
 * 普通右键 = 放/取台面上的物品（父类那套）；<b>潜行右键 = 用共振过滤器绑定</b>，
 * 走的是物品侧的 {@code ResonanceFilterItem#useOn}（原版潜行时会跳过方块的 useItemOn，
 * 正好把两个操作分开）。
 */
public class ResonanceTableBlock extends Block implements IBE<ResonanceTableBlockEntity>, IWrenchable, ProperWaterloggedBlock {

    /**
     * 红石充能状态。被充能时换成带充能标记的材质，并且台子会<b>冻结当前过滤</b>——
     * 冻结的是「过滤器读到的那一份规则」，不是不让玩家动台面上的东西，见
     * {@code ResonanceTableBlockEntity#getFilterSource}。
     */
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ResonanceTableBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(WATERLOGGED, false).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(WATERLOGGED, POWERED));
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return fluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        updateWater(level, state, pos);
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 放下时就要按当下的红石信号定状态，否则要等第一次邻居更新才会亮
        return withWater(super.getStateForPlacement(context), context)
                .setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    /**
     * 红石充能状态只由邻居更新驱动。
     * <p>
     * 只在<b>值真的变了</b>时才 {@code setBlock}：否则每次邻居更新都设一遍方块，会再把更新
     * 广播回去，和红石元件一起绕成死循环。
     */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);

        boolean powered = level.hasNeighborSignal(pos);
        if (powered != state.getValue(POWERED))
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_ALL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return AllShapes.CASING_13PX.get(Direction.UP);
    }

    @Override
    public Class<ResonanceTableBlockEntity> getBlockEntityClass() {
        return ResonanceTableBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends ResonanceTableBlockEntity> getBlockEntityType() {
        return ModBlockEntities.RESONANCE_TABLE.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        return SharedDepotBlockMethods.onUse(stack, state, level, pos, player, hand, hitResult);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        IBE.onRemove(state, level, pos, newState);
    }

    @Override
    public void updateEntityAfterFallOn(BlockGetter level, Entity entity) {
        super.updateEntityAfterFallOn(level, entity);
        SharedDepotBlockMethods.onLanded(level, entity);
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return SharedDepotBlockMethods.getComparatorInputOverride(state, level, pos);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType pathComputationType) {
        return false;
    }
}
