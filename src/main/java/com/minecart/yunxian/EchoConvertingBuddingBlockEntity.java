package com.minecart.yunxian;

import java.util.List;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * 回响母岩的纯展示 BE。
 * 不 tick、不存数据；仅当玩家佩戴护目镜看向母岩时，
 * GoggleOverlayRenderer 调用 addToGoggleTooltip，纯客户端读取方块状态与光照。
 */
public class EchoConvertingBuddingBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    public EchoConvertingBuddingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ECHO_BUDDING.get(), pos, state);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        BlockState state = getBlockState();

        // 1) can_summon = true 时显示挖掘警告；false 时无此警告行
        if (state.getValue(BlockStateProperties.CAN_SUMMON)) {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.echo_budding.warden_warning")
                            .withStyle(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }

        // 2) 生长状态：三态判定
        EchoConvertingBuddingBlock.GrowthStatus status = EchoConvertingBuddingBlock.GrowthStatus.NO_SPACE;
        if (level != null && state.getBlock() instanceof EchoConvertingBuddingBlock block) {
            status = block.getGrowthStatus(level, worldPosition);
        }

        String key = switch (status) {
            case GROWABLE -> "create_crystal_industry.echo_budding.growth.growable";
            case NEEDS_DARKNESS -> "create_crystal_industry.echo_budding.growth.requires_darkness";
            case NO_SPACE -> "create_crystal_industry.echo_budding.growth.no_space";
        };

        CreateLang.builder()
                .add(Component.translatable(key)
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);

        // 追加“当前生长速度”（与其它母岩一致）
        if (level != null)
            BuddingGrowthHelper.appendGrowthTooltip(level, worldPosition, tooltip);

        return true;
    }
}