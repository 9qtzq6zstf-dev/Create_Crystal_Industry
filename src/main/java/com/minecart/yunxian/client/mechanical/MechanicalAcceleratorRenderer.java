package com.minecart.yunxian.client.mechanical;

import com.minecart.yunxian.block.MechanicalAcceleratorBlock;
import com.minecart.yunxian.blockentity.MechanicalAcceleratorBlockEntity;
import com.simibubi.create.AllPartialModels;
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

public class MechanicalAcceleratorRenderer extends KineticBlockEntityRenderer<MechanicalAcceleratorBlockEntity> {

    /** 传动杆 partial：随应力旋转，直接用机动力的半截传动杆模型 */
    public static final PartialModel SHAFT = AllPartialModels.SHAFT_HALF;

    public MechanicalAcceleratorRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    /**
     * 半截传动杆模型朝 +Z 伸出，而本方块只在 FACING 的背面接应力，
     * 所以定向要用 {@code FACING.getOpposite()}，否则杆会跑到工作面上。
     */
    private static SuperByteBuffer shaft(BlockState state) {
        return CachedBuffers.partialFacing(SHAFT, state,
                state.getValue(MechanicalAcceleratorBlock.FACING).getOpposite());
    }

    @Override
    protected SuperByteBuffer getRotatedModel(MechanicalAcceleratorBlockEntity be, BlockState state) {
        return shaft(state);
    }

    @Override
    protected void renderSafe(MechanicalAcceleratorBlockEntity be, float partialTicks, PoseStack ms,
                              MultiBufferSource buffer, int light, int overlay) {
        // 与智能钻头同款逻辑：Flywheel 活跃时它已画了 SHAFT，vanilla 不再重复
        if (VisualizationManager.supportsVisualization(be.getLevel())) {
            return;
        }

        BlockState state = getRenderedBlockState(be);
        RenderType type = getRenderType(be, state);
        VertexConsumer vc = buffer.getBuffer(type);

        SuperByteBuffer shaft;
        try {
            shaft = shaft(state);
        } catch (Exception e) {
            return;
        }

        Axis axis = getRotationAxisOf(be);
        float time = AnimationTickHolder.getRenderTime(be.getLevel());
        float offset = getRotationOffsetForPosition(be, be.getBlockPos(), axis);
        float angle = ((time * be.getSpeed() * 3f / 10 + offset) % 360) / 180 * (float) Math.PI;

        shaft.light(light);
        shaft.rotateCentered(angle, Direction.get(AxisDirection.POSITIVE, axis));
        shaft.renderInto(ms, vc);
    }
}