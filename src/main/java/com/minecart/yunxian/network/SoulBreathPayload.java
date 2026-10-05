package com.minecart.yunxian.network;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.effect.SoulBreath;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：灵魂火喷流的心跳。
 * <p>
 * <b>为什么要专门发个包</b>：原版空手对空气右键一个包都不发（{@code MultiPlayerGameMode} 里空手 + MISS
 * 什么都不做），服务端无从知道玩家在按住右键。客户端那边能拿到
 * {@code InputEvent.InteractionKeyMappingTriggered}（见 {@code SoulBreathClient}），于是由它代发：
 * 按住期间每约 4 tick 重发一次，服务端每收到一次就把火束的寿命往后推一格。
 * <p>
 * <b>不带载荷</b>：这里只是一个「还在按」的信号，不带任何参数——喷不喷得出、喷多远全由服务端说了算
 * （见 {@link SoulBreath#refresh} 的校验），所以没有可伪造的东西。松手不需要专门通知：
 * 心跳断了，火束自己在 {@code SoulBreath.BEAM_TICKS} 之后灭。
 */
public record SoulBreathPayload() implements CustomPacketPayload {

    public static final Type<SoulBreathPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "soul_breath"));

    public static final StreamCodec<FriendlyByteBuf, SoulBreathPayload> STREAM_CODEC =
            StreamCodec.unit(new SoulBreathPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SoulBreathPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                SoulBreath.refresh(player);
            }
        });
    }
}
