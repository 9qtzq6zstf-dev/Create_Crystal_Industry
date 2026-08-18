package com.minecart.yunxian;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Places the shared filter and mode controls on all four faces around the drill axis.
 *
 * <p>Create renders a sided filtering behaviour on every active face. Scroll
 * behaviours do not select their side automatically while hovering, so hit
 * testing also updates the selected side from the actual face under the cursor.</p>
 */
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

    @Override
    public boolean testHit(LevelAccessor level, BlockPos pos, BlockState state, Vec3 hit) {
        Direction hitSide = getHitSide(hit);
        if (!isSideActive(state, hitSide)) {
            return false;
        }
        fromSide(hitSide);
        return super.testHit(level, pos, state, hit);
    }

    private static Direction getHitSide(Vec3 hit) {
        double x = hit.x - .5;
        double y = hit.y - .5;
        double z = hit.z - .5;
        double absX = Math.abs(x);
        double absY = Math.abs(y);
        double absZ = Math.abs(z);

        if (absX >= absY && absX >= absZ) {
            return x >= 0 ? Direction.EAST : Direction.WEST;
        }
        if (absY >= absZ) {
            return y >= 0 ? Direction.UP : Direction.DOWN;
        }
        return z >= 0 ? Direction.SOUTH : Direction.NORTH;
    }
}
