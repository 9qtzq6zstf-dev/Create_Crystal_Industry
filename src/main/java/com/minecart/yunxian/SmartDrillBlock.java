package com.minecart.yunxian;

import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import com.simibubi.create.foundation.damageTypes.CreateDamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

public class SmartDrillBlock extends DirectionalKineticBlock implements IBE<SmartDrillBlockEntity>, SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty SILK_TOUCH = BooleanProperty.create("silk_touch");
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public SmartDrillBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(FACING, Direction.NORTH)
                .setValue(SILK_TOUCH, false)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(SILK_TOUCH, WATERLOGGED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        FluidState fluidState = context.getLevel().getFluidState(context.getClickedPos());
        return defaultBlockState()
                .setValue(FACING, context.getNearestLookingDirection().getOpposite())
                .setValue(SILK_TOUCH, false)
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state;
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : Fluids.EMPTY.defaultFluidState();
    }

    @Override
    public Class<SmartDrillBlockEntity> getBlockEntityClass() {
        return SmartDrillBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SmartDrillBlockEntity> getBlockEntityType() {
        return ModBlockEntities.SMART_DRILL.get();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmartDrillBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (lvl, pos, st, be) -> {
            if (be instanceof SmartDrillBlockEntity drill) {
                drill.tick();
            }
        };
    }

    // ========== 伤害生物 ==========
    @Override
    public void entityInside(BlockState state, Level worldIn, BlockPos pos, Entity entityIn) {
        if (entityIn instanceof ItemEntity) return;
        if (!new AABB(pos).deflate(0.1f).intersects(entityIn.getBoundingBox())) return;
        withBlockEntityDo(worldIn, pos, be -> {
            if (be.getSpeed() == 0) return;
            entityIn.hurt(CreateDamageSources.drill(worldIn), (float) getDamage(be.getSpeed()));
        });
    }

    public static double getDamage(float speed) {
        float speedAbs = Math.abs(speed);
        double sub1 = Math.min(speedAbs / 16, 2);
        double sub2 = Math.min(speedAbs / 32, 4);
        double sub3 = Math.min(speedAbs / 64, 4);
        return Mth.clamp(sub1 + sub2 + sub3, 1, 10);
    }

    // ========== 交互 ==========
    // 不加 @Override，避免签名不匹配
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        // 如果手持扳手，让滚轮逻辑接管，不打开 GUI
        if (player.getItemInHand(hand).is(AllItems.WRENCH)) {
            return InteractionResult.SUCCESS;
        }

        // 否则打开 GUI
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SmartDrillBlockEntity drill) {
                player.openMenu(drill, pos);
            }
        }
        return InteractionResult.SUCCESS;
    }

    // ========== 动力接入 ==========
    // 不加 @Override，避免版本兼容问题
    public Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    // 不加 @Override
    public boolean hasShaftTowards(LevelReader world, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }
}