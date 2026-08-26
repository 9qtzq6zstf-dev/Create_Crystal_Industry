package com.minecart.yunxian.network;

import com.minecart.yunxian.EchoAttachments;
import com.minecart.yunxian.Yunxian;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CameraModePayload(boolean firstPerson) implements CustomPacketPayload {

    public static final Type<CameraModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "camera_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CameraModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, CameraModePayload::firstPerson,
                    CameraModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CameraModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                serverPlayer.setData(EchoAttachments.FIRST_PERSON.get(), payload.firstPerson());
            }
        });
    }
}