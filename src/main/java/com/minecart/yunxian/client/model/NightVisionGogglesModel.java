package com.minecart.yunxian.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

public class NightVisionGogglesModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel goggles3d;

    public NightVisionGogglesModel(BakedModel itemModel, BakedModel goggles3d) {
        super(itemModel);
        this.goggles3d = goggles3d;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext displayContext, PoseStack poseStack, boolean leftHanded) {
        if (displayContext == ItemDisplayContext.HEAD)
            return goggles3d.applyTransform(displayContext, poseStack, leftHanded);
        return super.applyTransform(displayContext, poseStack, leftHanded);
    }
}