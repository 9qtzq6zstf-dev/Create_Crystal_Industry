package com.minecart.yunxian.block;

import com.minecart.yunxian.blockentity.SmartPressureReliefHatchBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * 智能泄压舱口：外形、依附判定、碰撞箱、风场判定与吐货逻辑全部继承
 * {@link PressureReliefHatchBlock}，只多一个"数量"滑块——用来调每次吐几个。
 * 那个滑块由 {@link SmartPressureReliefHatchBlockEntity} 挂上；原版泄压舱口没有它，
 * 一批固定就是"一组"（物品自己的堆叠数）。
 * <p>
 * 贴图目前是从机械动力的 {@code create:block/hatch} 与 {@code create:block/net} 复制过来的
 * 占位图（在 {@code textures/block/smart_pressure_relief_hatch/}），等画好后替换即可。
 */
public class SmartPressureReliefHatchBlock extends PressureReliefHatchBlock {

    public SmartPressureReliefHatchBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmartPressureReliefHatchBlockEntity(pos, state);
    }
}
