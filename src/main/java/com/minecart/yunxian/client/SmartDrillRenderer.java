package com.minecart.yunxian.client;

import com.minecart.yunxian.SmartDrillBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;

import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;

import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class SmartDrillRenderer extends KineticBlockEntityRenderer<SmartDrillBlockEntity> {

    /**
     * Smart Drill 的旋转头部模型
     *
     * 对应：
     * assets/yunxian/models/block/smart_drill/head.json
     */
    public static final PartialModel HEAD =
            PartialModel.of(ResourceLocation.parse(
                    "create_crystal_industry:block/smart_drill/head"
            ));

    public SmartDrillRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(
            SmartDrillBlockEntity be,
            BlockState state
    ) {
        return CachedBuffers.partialFacing(HEAD, state);
    }
}