package com.minecart.yunxian.block;

import com.minecart.yunxian.blockentity.SmartPressureReliefHatchBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

/**
 * 智能泄压舱口：外形、依附判定、碰撞箱、风场判定与吐货逻辑全部继承
 * {@link PressureReliefHatchBlock}，多两件事：
 * <ul>
 *   <li>侧面过滤槽上带一个"数量"滑块——用来调每次吐几个
 *       （由 {@link SmartPressureReliefHatchBlockEntity} 挂上）；</li>
 *   <li>可以<b>被红石锁定</b>：六个方向任意一面收到红石信号就停止吐货，
 *       外观同时换成 {@code block_powered} 那套模型（见 blockstate 里的 powered 变体）。</li>
 * </ul>
 * 原版泄压舱口两样都没有——没有数量滑块，也没有红石锁。
 * <p>
 * 贴图是 {@code textures/block/smart_pressure_relief_hatch/} 下的占位图
 * （{@code hatch} / {@code hatch_powered}），等画好后替换即可。
 */
public class SmartPressureReliefHatchBlock extends PressureReliefHatchBlock {

    /** 红石锁：true = 收到红石信号，停止吐货 */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public SmartPressureReliefHatchBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(POWERED);
        super.createBlockStateDefinition(builder);
    }

    /**
     * 红石锁：只要六个邻居里有任意一个给出信号就锁上。
     * 与动力吸尘器那份不同——吸尘器要跳过"扇叶朝向"那一面（那是它的动力输入口），
     * 舱口没有动力输入，六面一视同仁。
     */
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos,
                                boolean isMoving) {
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != powered) {
            level.setBlock(pos, state.setValue(POWERED, powered), Block.UPDATE_CLIENTS);
        }
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = super.getStateForPlacement(context);
        return state == null ? null
                : state.setValue(POWERED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmartPressureReliefHatchBlockEntity(pos, state);
    }
}
