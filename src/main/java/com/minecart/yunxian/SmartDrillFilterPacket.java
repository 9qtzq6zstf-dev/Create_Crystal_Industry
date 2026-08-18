package com.minecart.yunxian;

import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SmartDrillFilterPacket(
        BlockPos pos,
        ItemStack filter
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SmartDrillFilterPacket> TYPE =
            new CustomPacketPayload.Type<>(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            "create_crystal_industry",
                            "smart_drill_filter"
                    )
            );

    public static final StreamCodec<RegistryFriendlyByteBuf, SmartDrillFilterPacket> STREAM_CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,
                    SmartDrillFilterPacket::pos,

                    ItemStack.OPTIONAL_STREAM_CODEC,
                    SmartDrillFilterPacket::filter,

                    SmartDrillFilterPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(
            SmartDrillFilterPacket packet,
            IPayloadContext context
    ) {
        context.enqueueWork(() -> {

            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            // 获取玩家当前所在世界的方块实体
            BlockEntity blockEntity =
                    player.level().getBlockEntity(packet.pos());

            if (!(blockEntity instanceof SmartDrillBlockEntity drill)) {
                return;
            }

            // 防止玩家从远处修改智能钻头
            if (player.distanceToSqr(
                    packet.pos().getX() + 0.5,
                    packet.pos().getY() + 0.5,
                    packet.pos().getZ() + 0.5
            ) > 64.0) {
                return;
            }

            // 获取 FilteringBehaviour
            FilteringBehaviour filtering = drill.getFiltering();

            if (filtering == null) {
                return;
            }

            // 空 ItemStack = 清除过滤器
            ItemStack stack = packet.filter();

            // 设置过滤器
            filtering.setFilter(stack);

            // 标记方块实体已改变
            drill.setChanged();

            // 同步给客户端
            drill.sendData();
        });
    }
}