package com.minecart.yunxian.client;

import com.minecart.yunxian.MechanicalCleanerBlockEntity;
import com.simibubi.create.content.kinetics.base.KineticBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.engine_room.flywheel.api.visualization.VisualizationManager;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.createmod.catnip.animation.AnimationTickHolder;
import net.createmod.catnip.render.CachedBuffers;
import net.createmod.catnip.render.SuperByteBuffer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.Direction.Axis;
import net.minecraft.core.Direction.AxisDirection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;

public class MechanicalCleanerRenderer extends KineticBlockEntityRenderer<MechanicalCleanerBlockEntity> {

    /** 旋转的扇叶（含内部轴） */
    public static final PartialModel PROPELLER =
            PartialModel.of(ResourceLocation.parse(
                    "create_crystal_industry:block/mechanical_cleaner/propeller"));

    /** 外部传动杆 */
    public static final PartialModel SHAFT =
            PartialModel.of(ResourceLocation.parse(
                    "create_crystal_industry:block/mechanical_cleaner/shaft"));

    public MechanicalCleanerRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected SuperByteBuffer getRotatedModel(MechanicalCleanerBlockEntity be, BlockState state) {
        // 和智能钻头 getRotatedModel 返回 HEAD 对应：这里返回扇叶
        return CachedBuffers.partialFacing(PROPELLER, state);
    }

    @Override
    protected void renderSafe(MechanicalCleanerBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        BlockState state = getRenderedBlockState(be);
        RenderType type = getRenderType(be, state);
        VertexConsumer vc = buffer.getBuffer(type);

        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            // Flywheel 已画扇叶，vanilla 只补传动杆（和智能钻头补 SHAFT 完全一致）
            renderShaft(be, state, ms, vc, light);
        } else {
            // 无 Flywheel：vanilla 画扇叶 + 传动杆
            renderPropeller(be, state, ms, vc, light);
            renderShaft(be, state, ms, vc, light);
        }
    }

    /** 扇叶：按有效转速旋转（红石锁定时冻结） */
    private void renderPropeller(MechanicalCleanerBlockEntity be, BlockState state, PoseStack ms,
                                 VertexConsumer vc, int light) {
        SuperByteBuffer propeller = CachedBuffers.partialFacing(PROPELLER, state);
        standardKineticRotationTransform(propeller, be, light).renderInto(ms, vc);
    }

    /** 传动杆：始终按真实转速旋转（红石锁定时仍转，与智能钻头一致） */
    private void renderShaft(MechanicalCleanerBlockEntity be, BlockState state, PoseStack ms,
                             VertexConsumer vc, int light) {
        SuperByteBuffer shaft;
        try {
            shaft = CachedBuffers.partialFacing(SHAFT, state);
        } catch (Exception e) {
            return;
        }
        Axis axis = getRotationAxisOf(be);
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, be.getBlockPos(), axis);
        float angle = ((time * be.getTrueSpeed() * 3f / 10 + offset) % 360) / 180 * (float) Math.PI;  // ← 修正：be.getTrueSpeed()
        shaft.light(light);
        shaft.rotateCentered(angle, Direction.get(AxisDirection.POSITIVE, axis));
        shaft.renderInto(ms, vc);
    }
}