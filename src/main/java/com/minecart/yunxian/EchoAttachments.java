package com.minecart.yunxian;

import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class EchoAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Yunxian.MODID);

    /** 服务端记录的"该玩家当前是否第一人称"；默认 true */
    public static final Supplier<AttachmentType<Boolean>> FIRST_PERSON =
            ATTACHMENT_TYPES.register("camera_first_person",
                    () -> AttachmentType.builder(() -> Boolean.TRUE)
                            .serialize(Codec.BOOL)
                            .build());

    /** ★ 服务端权威的"该玩家夜视模式开关"；StreamCodec 同步给所有客户端 */
    public static final Supplier<AttachmentType<Boolean>> NIGHT_VISION =
            ATTACHMENT_TYPES.register("night_vision",
                    () -> AttachmentType.builder(() -> Boolean.FALSE)
                            .serialize(Codec.BOOL)
                            .sync(ByteBufCodecs.BOOL)
                            .build());

    private EchoAttachments() {
    }
}