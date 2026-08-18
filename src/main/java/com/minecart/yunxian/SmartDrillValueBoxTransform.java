package com.minecart.yunxian;

import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmartDrillValueBoxTransform extends ValueBoxTransform.Sided {

    @Override
    protected Vec3 getSouthLocation() {
        // 单槽位：正面顶部，位置已被验证不被头部模型遮挡
        return VecHelper.voxelSpace(8, 4.5, 15.5);
    }

    @Override
    protected boolean isSideActive(BlockState state, Direction side) {
        // 必须 == ：物品渲染在正面框内（!= 会渲染到方块的角上）
        return side.getAxis() != state.getValue(SmartDrillBlock.FACING).getAxis();
    }
}