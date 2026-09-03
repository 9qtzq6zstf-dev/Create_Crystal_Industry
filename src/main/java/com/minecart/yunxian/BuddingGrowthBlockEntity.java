package com.minecart.yunxian;

import java.util.List;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 母岩共用的“当前生长速度”展示 BE。
 * 不 tick、不存数据；仅当玩家佩戴护目镜看向母岩时，
 * GoggleOverlayRenderer 调用 addToGoggleTooltip，纯客户端读取邻格状态。
 */
public class BuddingGrowthBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    public BuddingGrowthBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.BUDDING_GROWTH.get(), pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level != null)
            BuddingGrowthHelper.appendGrowthTooltip(level, worldPosition, tooltip);
        return true;
    }
}