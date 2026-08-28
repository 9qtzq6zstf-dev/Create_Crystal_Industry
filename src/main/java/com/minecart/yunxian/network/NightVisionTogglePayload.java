package com.minecart.yunxian.network;

import com.minecart.yunxian.EchoAttachments;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.item.NightVisionGogglesItem;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NightVisionTogglePayload() implements CustomPacketPayload {

    public static final Type<NightVisionTogglePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "night_vision_toggle"));

    public static final StreamCodec<FriendlyByteBuf, NightVisionTogglePayload> STREAM_CODEC =
            StreamCodec.unit(new NightVisionTogglePayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(NightVisionTogglePayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() == null) return;
            // 服务端也校验穿戴，防作弊
            if (!(ctx.player().getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof NightVisionGogglesItem)) {
                return;
            }
            ctx.player().setData(EchoAttachments.NIGHT_VISION,
                    !ctx.player().getData(EchoAttachments.NIGHT_VISION));
        });
    }
}