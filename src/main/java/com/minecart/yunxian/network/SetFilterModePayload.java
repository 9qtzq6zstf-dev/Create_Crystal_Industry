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
 * 客户端 → 服务端：改共振过滤器的名单模式与匹配模式（界面下方那四个按钮）。
 * <p>
 * 两个开关一起发，和 Create 的 {@code FilterScreenPacket} 只发一个选项的做法不同：
 * 那边每次只改一位，这里干脆把当前两位一起报上去 —— 服务端存的就是这两个字段，
 * 不必按位复原，也就不存在「先发的包后到」把状态改错的可能。
 * <p>
 * 物品上的组件由菜单在关闭时统一写回（见 {@code ResonanceFilterMenu#saveData}），
 * 这里只改服务端那份菜单的字段。
 */
public record SetFilterModePayload(boolean blacklist, boolean respectData) implements CustomPacketPayload {

    public static final Type<SetFilterModePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "set_filter_mode"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterModePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.BOOL, SetFilterModePayload::blacklist,
                    ByteBufCodecs.BOOL, SetFilterModePayload::respectData,
                    SetFilterModePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetFilterModePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof ResonanceFilterMenu menu) {
                menu.applyFilterMode(payload.blacklist(), payload.respectData());
            }
        });
    }
}
