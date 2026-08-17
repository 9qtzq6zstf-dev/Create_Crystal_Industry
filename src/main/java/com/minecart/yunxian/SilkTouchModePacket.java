package com.minecart.yunxian;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SilkTouchModePacket(BlockPos pos, boolean mode) implements CustomPacketPayload {
    public static final Type<SilkTouchModePacket> TYPE = new Type<>(ResourceLocation.parse("yunxian:silk_touch_mode"));

    public static final StreamCodec<FriendlyByteBuf, SilkTouchModePacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SilkTouchModePacket decode(FriendlyByteBuf buf) {
            return new SilkTouchModePacket(buf.readBlockPos(), buf.readBoolean());
        }

        @Override
        public void encode(FriendlyByteBuf buf, SilkTouchModePacket packet) {
            buf.writeBlockPos(packet.pos);
            buf.writeBoolean(packet.mode);
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}