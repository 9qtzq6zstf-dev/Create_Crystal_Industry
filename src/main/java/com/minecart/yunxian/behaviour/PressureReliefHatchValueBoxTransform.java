package com.minecart.yunxian.behaviour;

import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 泄压舱口的过滤槽值框：位置与旋转规则全部继承吸尘器那份
 * （{@link MechanicalCleanerValueBoxTransform}，两者模型朝向与旋转表一致），
 * 只换两样东西——朝向读的是晶簇那个 {@link AmethystClusterBlock#FACING}，
 * 槽位摆在网罩四面墙的外表面上。
 */
public class PressureReliefHatchValueBoxTransform extends MechanicalCleanerValueBoxTransform {

    /**
     * 基状态（facing=up）下网罩四面墙外表面上的槽位，顺序必须与父类的
     * {@code SLOT_NORMALS}（南、北、东、西）一一对应，否则槽会串面。
     * <p>
     * 网罩那圈柱子占 x/z 2~14、y 2~7，所以四面墙的外表面就是 x=2 / x=14 / z=2 / z=14；
     * 高度取网罩腰线 y=4.5 再往下 0.5 像素（视觉上更贴着网罩下沿那一排横梁）。
     */
    private static final Vec3[] HATCH_SLOTS = {
            VecHelper.voxelSpace(8, 4, 14),   // 南面（z=14 那道墙的外表面）
            VecHelper.voxelSpace(8, 4, 2),    // 北面（z=2）
            VecHelper.voxelSpace(14, 4, 8),   // 东面（x=14）
            VecHelper.voxelSpace(2, 4, 8),    // 西面（x=2）
    };

    @Override
    protected Vec3[] slotPositions() {
        return HATCH_SLOTS;
    }

    @Override
    protected Direction facingOf(BlockState state) {
        return state.getValue(AmethystClusterBlock.FACING);
    }
}
