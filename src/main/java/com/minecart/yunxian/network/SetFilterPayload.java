package com.minecart.yunxian.network;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.menu.EchoSpyglassFilterMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** 客户端 → 服务端：把 JEI 拖入的物品设为虚拟过滤 */
public record SetFilterPayload(ItemStack stack) implements CustomPacketPayload {

    public static final Type<SetFilterPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "set_filter"));

    // ItemStack.STREAM_CODEC 要求 RegistryFriendlyByteBuf（数据组件需要注册表访问）
    public static final StreamCodec<RegistryFriendlyByteBuf, SetFilterPayload> STREAM_CODEC =
            StreamCodec.composite(ItemStack.STREAM_CODEC, SetFilterPayload::stack, SetFilterPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SetFilterPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && player.containerMenu instanceof EchoSpyglassFilterMenu menu) {
                menu.setVirtualFilter(payload.stack());   // 服务端校验 + 持久化
            }
        });
    }
}