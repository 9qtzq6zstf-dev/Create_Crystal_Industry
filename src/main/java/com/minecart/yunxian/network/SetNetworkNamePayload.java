package com.minecart.yunxian.network;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.menu.ResonanceFilterMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * 客户端 → 服务端：改共振过滤器所在网络的名字（在界面的输入框里敲字时发，和 Create
 * 的包裹过滤器每敲一下发一次 {@code FilterScreenPacket} 是同一个路子）。
 * <p>
 * 只带名字，<b>不带网络 id</b>：网络由服务端从玩家当前打开的那个菜单反查
 * （手里的过滤器接入的是哪张网络），客户端说了不算。这和 {@code SetFilterPayload}
 * 的写法一致。
 */
public record SetNetworkNamePayload(String name) implements CustomPacketPayload {

    /** 名字上限：界面上的输入框与这里共用。服务端也按它截断，别信客户端。 */
    public static final int MAX_LENGTH = 25;

    public static final Type<SetNetworkNamePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "set_network_name"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetNetworkNamePayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(MAX_LENGTH),
                    SetNetworkNamePayload::name, SetNetworkNamePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetNetworkNamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof ResonanceFilterMenu menu) {
                menu.setNetworkName(payload.name());   // 菜单那边校验网络并落盘
            }
        });
    }
}
