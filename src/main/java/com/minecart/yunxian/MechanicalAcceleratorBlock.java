package com.minecart.yunxian;

import com.simibubi.create.content.kinetics.base.DirectionalKineticBlock;
import com.simibubi.create.foundation.block.IBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

public class MechanicalAcceleratorBlock extends DirectionalKineticBlock
        implements IBE<MechanicalAcceleratorBlockEntity> {

    public static final BooleanProperty POWERED = BooleanProperty.create("powered");

    public MechanicalAcceleratorBlock(Properties properties) {
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

    /** 传动杆只在 FACING 的背面 */
    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(FACING).getOpposite();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(POWERED))
            return;

        // 在 5 个工作面中随机选一个发粒子，与催生范围一致
        Direction facing = state.getValue(FACING);
        Direction[] working = new Direction[5];
        int i = 0;
        for (Direction d : Direction.values()) {
            if (d != facing.getOpposite())
                working[i++] = d;
        }
        Direction face = working[random.nextInt(5)];

        double x = pos.getX() + 0.5 + face.getStepX() * 0.75 + (random.nextDouble() - 0.5) * 0.5;
        double y = pos.getY() + 0.5 + face.getStepY() * 0.75 + (random.nextDouble() - 0.5) * 0.5;
        double z = pos.getZ() + 0.5 + face.getStepZ() * 0.75 + (random.nextDouble() - 0.5) * 0.5;
        level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, y, z,
                face.getStepX() * 0.01, face.getStepY() * 0.01 + 0.02, face.getStepZ() * 0.01);
    }

    @Override
    public Class<MechanicalAcceleratorBlockEntity> getBlockEntityClass() {
        return MechanicalAcceleratorBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MechanicalAcceleratorBlockEntity> getBlockEntityType() {
        return ModBlockEntities.MECHANICAL_ACCELERATOR.get();
    }
}