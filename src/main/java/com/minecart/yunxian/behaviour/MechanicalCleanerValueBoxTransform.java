package com.minecart.yunxian.behaviour;

import com.minecart.yunxian.block.MechanicalCleanerBlock;
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

/**
 * 侧面 4 个过滤槽的值框位置。基状态（facing=up）下的槽位与法线写死在 {@link #SLOT_POSITIONS} /
 * {@link #SLOT_NORMALS} 里，其余五个朝向按模型那套"先 x 后 y"的旋转推出来。
 * <p>
 * 泄压舱口的模型朝向与旋转表跟吸尘器完全一致，所以它直接继承本类、只换两样东西：
 * 读的是哪个 {@code FACING} 属性（{@link #facingOf}）与槽位摆在哪（{@link #slotPositions}）。
 */
public class MechanicalCleanerValueBoxTransform extends ValueBoxTransform.Sided {

    // 基准模型(facing=up)时，4 个侧面上的槽位位置（体素坐标）
    private static final Vec3[] SLOT_POSITIONS = {
            VecHelper.voxelSpace(8, 6, 15.5),   // 南面
            VecHelper.voxelSpace(8, 6, 0.5),    // 北面
            VecHelper.voxelSpace(15.5, 6, 8),   // 东面
            VecHelper.voxelSpace(0.5, 6, 8),    // 西面
    };
    // 对应 4 个侧面的法线
    private static final Vec3[] SLOT_NORMALS = {
            new Vec3(0, 0, 1),    // 南
            new Vec3(0, 0, -1),   // 北
            new Vec3(1, 0, 0),    // 东
            new Vec3(-1, 0, 0),   // 西
    };

    /** 槽位在基状态（facing=up）下的位置，下标与 {@link #SLOT_NORMALS} 一一对应 */
    protected Vec3[] slotPositions() {
        return SLOT_POSITIONS;
    }

    /** 本方块把朝向存在哪个属性上。默认是吸尘器那个，泄压舱口覆写它 */
    protected Direction facingOf(BlockState state) {
        return state.getValue(MechanicalCleanerBlock.FACING);
    }

    @Override
    protected Vec3 getSouthLocation() {
        return VecHelper.voxelSpace(8, 8, 15.5);
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        Direction facing = facingOf(state);
        float[] rot = rotationFor(facing);
        Direction side = getSide();
        Vec3[] slots = slotPositions();

        for (int i = 0; i < 4; i++) {
            Vec3 n = rotateNormal(SLOT_NORMALS[i], rot);
            if (Direction.getNearest(n.x, n.y, n.z) == side)
                return rotatePoint(slots[i], rot);
        }
        return null; // 非侧面的方向（前后）不渲染
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {
        Direction side = getSide();
        Direction facing = facingOf(state);

        // 只有方块横放（水平朝向）时的上下两面，数字才按方块朝向修正方向；
        // 四面竖墙保持基类原样（当前正常显示），不做任何改动。
        if ((side == Direction.UP || side == Direction.DOWN) && facing.getAxis().isHorizontal()) {
            // 与 getLocalOffset 同款模型旋转（先 x 后 y），
            // 并在最外层补一个 Y 轴 180°：让文字头指向方块正面而非尾部
            float[] rot = rotationFor(facing);
            TransformStack.of(ms)
                    .rotateYDegrees(180)
                    .rotateYDegrees(-rot[1])
                    .rotateXDegrees(rot[0]);
        } else {
            super.rotate(level, pos, state, ms);
        }
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction side) {
        // 只激活垂直于朝向的 4 个侧面
        return side.getAxis() != facingOf(state).getAxis();
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