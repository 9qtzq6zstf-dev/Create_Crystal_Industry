package com.minecart.yunxian;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class MechanicalCleanerValueBoxTransform extends ValueBoxTransform.Sided {

    // 基准模型(facing=up)时，4 个侧面上的槽位位置（体素坐标）
    // 基准模型(facing=up)时，4 个侧面上的槽位位置（体素坐标）
    private static final Vec3[] SLOT_POSITIONS = {
            VecHelper.voxelSpace(8, 6, 15.5),   // 南面（y 8→6）
            VecHelper.voxelSpace(8, 6, 0.5),    // 北面（y 8→6）
            VecHelper.voxelSpace(15.5, 6, 8),   // 东面（y 8→6）
            VecHelper.voxelSpace(0.5, 6, 8),    // 西面（y 8→6）
    };
    // 对应 4 个侧面的法线
    private static final Vec3[] SLOT_NORMALS = {
            new Vec3(0, 0, 1),    // 南
            new Vec3(0, 0, -1),   // 北
            new Vec3(1, 0, 0),    // 东
            new Vec3(-1, 0, 0),   // 西
    };

    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(8, 8, 15.5);
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(MechanicalCleanerBlock.FACING);
        float[] rot = rotationFor(facing);
        Direction side = getSide();

        for (int i = 0; i < 4; i++) {
            Vec3 n = rotateNormal(SLOT_NORMALS[i], rot);
            if (Direction.getNearest(n.x, n.y, n.z) == side)
                return rotatePoint(SLOT_POSITIONS[i], rot);
        }
        return null; // 非侧面的方向（前后）不渲染
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction side) {
        // 只激活垂直于朝向的 4 个侧面
        return side.getAxis() != state.getValue(MechanicalCleanerBlock.FACING).getAxis();
    }

    private static Vec3 rotatePoint(Vec3 p, float[] rot) {
        Vec3 c = p.subtract(0.5, 0.5, 0.5);
        c = c.xRot(rot[0] * Mth.DEG_TO_RAD).yRot(-rot[1] * Mth.DEG_TO_RAD);
        return c.add(0.5, 0.5, 0.5);
    }

    private static Vec3 rotateNormal(Vec3 n, float[] rot) {
        return n.xRot(rot[0] * Mth.DEG_TO_RAD).yRot(-rot[1] * Mth.DEG_TO_RAD);
    }

    /** 与 mechanical_cleaner.json variants 的旋转一致：先 x 后 y */
    private static float[] rotationFor(Direction facing) {
        return switch (facing) {
            case DOWN -> new float[] {180, 0};
            case NORTH -> new float[] {90, 0};
            case SOUTH -> new float[] {90, 180};
            case EAST -> new float[] {90, 90};
            case WEST -> new float[] {90, 270};
            default -> new float[] {0, 0}; // UP
        };
    }
}