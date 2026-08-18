package com.minecart.yunxian;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class SmartDrillFilterSlot extends ValueBoxTransform {


    public Vec3 getLocalOffset(BlockState state) {
        return new Vec3(0.5, 0.5, 0.5);
    }


    protected boolean isSideActive(BlockState state, Direction direction) {
        return true;
    }

    @Override
    public Vec3 getLocalOffset(LevelAccessor level, BlockPos pos, BlockState state) {
        return null;
    }

    @Override
    public void rotate(LevelAccessor level, BlockPos pos, BlockState state, PoseStack ms) {

    }
}