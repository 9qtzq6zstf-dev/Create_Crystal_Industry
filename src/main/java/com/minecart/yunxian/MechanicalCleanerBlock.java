package com.minecart.yunxian;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

public class MechanicalCleanerBlock extends DirectionalKineticBlock
        implements IBE<MechanicalCleanerBlockEntity> {

    /** 红石锁：true = 收到红石信号，扇叶停转 */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public MechanicalCleanerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
        super.createBlockStateDefinition(builder);
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(FACING).getAxis();
    }

    /** 传动杆在 FACING 背面（应力输入面） */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        // 红石锁：同步信号状态，仅在变化时更新，避免递归
        boolean powered = hasLockingSignal(level, pos, state);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), 2);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        boolean powered = hasLockingSignal(context.getLevel(), context.getClickedPos(), state);
        return state.setValue(POWERED, powered);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }

        // 命中配置槽（4 个侧面）：交给基类行为系统处理
        // - 手持物品右键侧面 → 设置过滤
        // - 空手长按侧面 → 切换扇叶方向（值设置面板）
        if (isConfigSlotSide(state, hitResult.getDirection())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        // 其余位置：空手打开容器
        if (stack.isEmpty()) {
            if (level.getBlockEntity(pos) instanceof MechanicalCleanerBlockEntity blockEntity) {
                // 网络包里写入方块位置 + 当前吸取距离，客户端工厂据此构造菜单
                player.openMenu(blockEntity, buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeInt(blockEntity.getSuckRange());
                });
            }
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 配置槽只在垂直于朝向的 4 个侧面（与 ValueBoxTransform.isSideActive 一致） */
    private boolean isConfigSlotSide(BlockState state, Direction side) {
        return side.getAxis() != state.getValue(FACING).getAxis();
    }

    /**
     * 红石锁判定：忽略扇叶朝向（FACING）的红石信号，只统计其余 5 个方向。
     * 与智能钻头 hasLockingSignal 完全一致。
     */
    private boolean hasLockingSignal(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        for (Direction side : Direction.values()) {
            if (side == facing) {
                continue;
            }
            if (level.getSignal(pos.relative(side), side) > 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Class<MechanicalCleanerBlockEntity> getBlockEntityClass() {
        return MechanicalCleanerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MechanicalCleanerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.MECHANICAL_CLEANER.get();
    }
}