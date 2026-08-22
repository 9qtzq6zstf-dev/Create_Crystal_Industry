package com.minecart.yunxian.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public final class ModRenderTypes extends RenderType {

    // 四边形版本：每条棱 = 1 个朝向相机的矩形，线宽在所有平台上都生效
    public static final RenderType ECHO_ORE_OVERLAY_QUADS = create(
            "echo_ore_overlay_quads",
            DefaultVertexFormat.POSITION_COLOR,
            VertexFormat.Mode.QUADS,
            4096,
            false,
            false,
            CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setOutputState(RenderStateShard.ITEM_ENTITY_TARGET)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.NO_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
    );

    private ModRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode,
                           int bufferSize, boolean affectsCrumbling, boolean sortOnUpload,
                           Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }
}