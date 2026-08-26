package com.minecart.yunxian;

import com.mojang.serialization.Codec;
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

    private EchoAttachments() {
    }
}