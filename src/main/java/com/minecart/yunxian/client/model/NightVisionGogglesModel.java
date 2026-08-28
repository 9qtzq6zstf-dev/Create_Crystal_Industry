package com.minecart.yunxian.client.model;

import com.minecart.yunxian.client.NightVisionToggle;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

public class NightVisionGogglesModel extends BakedModelWrapper<BakedModel> {
    private final BakedModel goggles3d;
    private final BakedModel goggles3dOn;

    public NightVisionGogglesModel(BakedModel itemModel, BakedModel goggles3d, BakedModel goggles3dOn) {
        super(itemModel);
        this.goggles3d = goggles3d;
        this.goggles3dOn = goggles3dOn;
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext displayContext, PoseStack poseStack, boolean leftHanded) {
        if (displayContext == ItemDisplayContext.HEAD) {
            BakedModel active = NightVisionToggle.isEnabled() ? goggles3dOn : goggles3d;
            return active.applyTransform(displayContext, poseStack, leftHanded);
        }
        return super.applyTransform(displayContext, poseStack, leftHanded);
    }
}