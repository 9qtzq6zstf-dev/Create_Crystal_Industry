package com.minecart.yunxian;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmartDrillValueBoxTransform extends ValueBoxTransform.Sided {

    public enum Slot {
        FILTER,
        MODE
    }

    private final Slot slot;

    public SmartDrillValueBoxTransform(Slot slot) {
        this.slot = slot;
    }

    @Override
    protected Vec3 getSouthLocation() {
        return switch (slot) {
            case FILTER -> VecHelper.voxelSpace(8, 11.5, 15.5);
            case MODE -> VecHelper.voxelSpace(8, 4.5, 15.5);
        };
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction side) {
        return side.getAxis() != state.getValue(SmartDrillBlock.FACING).getAxis();
    }

}