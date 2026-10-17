package com.minecart.yunxian.behaviour;

import com.minecart.yunxian.block.SmartDrillBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import dev.engine_room.flywheel.lib.transform.TransformStack;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmartDrillValueBoxTransform extends ValueBoxTransform.Sided {

    // 基准模型(facing=up)时，4 个侧面上的槽位位置（体素坐标）
    private static final Vec3[] SLOT_POSITIONS = {
            VecHelper.voxelSpace(8, 5, 15.5),   // 南面
            VecHelper.voxelSpace(8, 5, 0.5),    // 北面
            VecHelper.voxelSpace(15.5, 5, 8),   // 东面
            VecHelper.voxelSpace(0.5, 5, 8),    // 西面
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
        // getLocalOffset 已完全自定义，此方法不会被调用；仅需满足 Sided 的抽象要求
        return VecHelper.voxelSpace(8, 4.5, 15.5);
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(SmartDrillBlock.FACING);
        float[] rot = rotationFor(facing);
        Direction side = getSide();

        // 找到当前世界面 side 对应模型的哪个侧面
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
        return side.getAxis() != state.getValue(SmartDrillBlock.FACING).getAxis();
    }

    /**
     * 槽里物品的朝向。四面竖墙交给基类（{@code Sided.rotate} 让物品朝外贴在该面上）就够；
     * 但方块横放（水平朝向）时，顶面和底面也会被激活，基类只会把物品平铺在面上、
     * 不理会方块自己的 Y 朝向，于是物品方向跟钻头方向脱钩。
     * <p>
     * 这里补上和动力吸尘器 {@code MechanicalCleanerValueBoxTransform#rotate} 完全一致的修正：
     * 按模型那套「先 x 后 y」转回来，再在最外层加一个 Y 轴 180°，
     * 让物品的正面（而不是背面）对着钻头的正面。
     */
    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Direction side = getSide();
        Direction facing = state.getValue(SmartDrillBlock.FACING);

        if ((side == Direction.UP || side == Direction.DOWN) && facing.getAxis().isHorizontal()) {
            float[] rot = rotationFor(facing);
            TransformStack.of(ms)
                    .rotateYDegrees(180)
                    .rotateYDegrees(-rot[1])
                    .rotateXDegrees(rot[0]);
        } else {
            super.rotate(level, pos, state, ms);
        }
    }

    private static Vec3 rotatePoint(Vec3 p, float[] rot) {
        Vec3 c = p.subtract(0.5, 0.5, 0.5);
        c = c.xRot(rot[0] * Mth.DEG_TO_RAD).yRot(-rot[1] * Mth.DEG_TO_RAD);   // ← 加负号
        return c.add(0.5, 0.5, 0.5);
    }

    private static Vec3 rotateNormal(Vec3 n, float[] rot) {
        return n.xRot(rot[0] * Mth.DEG_TO_RAD).yRot(-rot[1] * Mth.DEG_TO_RAD); // ← 加负号
    }

    /** 与 smart_drill.json variants 的旋转一致：先 x 后 y */
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