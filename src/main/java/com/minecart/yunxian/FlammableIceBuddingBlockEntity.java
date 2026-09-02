package com.minecart.yunxian;

import java.util.List;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

/**
 * 可燃冰母岩的纯展示 BE。
 * 不 tick、不存数据；仅当玩家佩戴护目镜看向母岩时，
 * GoggleOverlayRenderer 调用 addToGoggleTooltip，纯客户端读取周围方块状态。
 */
public class FlammableIceBuddingBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    public FlammableIceBuddingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLAMMABLE_ICE_BUDDING.get(), pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 判定"旁边是否有水"：6 个相邻格的流体是否为水。
        // 与修复后的生长逻辑一致——waterlogged 的芽/簇其流体状态也是水，
        // 因此"旁边只有已长出的含水芽、没有空旷水源"时也会正确显示"可生长"。
        boolean nearWater = false;
        if (level != null) {
            for (Direction dir : Direction.values()) {
                FluidState fluid = level.getFluidState(worldPosition.relative(dir));
                if (fluid.getType() == Fluids.WATER) {
                    nearWater = true;
                    break;
                }
            }
        }

        CreateLang.builder()
                .add(Component.translatable(nearWater
                                ? "create_crystal_industry.flammable_ice_budding.growable"
                                : "create_crystal_industry.flammable_ice_budding.only_in_water")
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);

        return true;
    }
}