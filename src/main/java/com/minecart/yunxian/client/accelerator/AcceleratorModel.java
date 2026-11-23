package com.minecart.yunxian.client.accelerator;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.SimpleCTBehaviour;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * 电力催生器的外壳（{@code electric_casing}）连接材质。
 * <p>
 * 图集布局与机械动力的机壳一致：{@link AllCTTypes#OMNIDIRECTIONAL}，8x8 格
 * （{@code textures/block/accelerator/electric_casing_connected.png}，128x128）。
 * <p>
 * 只影响外壳那一层：{@code CTModel#getQuads} 只在「四边形的贴图 == shift 的 original」时重映射 UV，
 * 而叠在上面的十字覆盖层（{@code accelerator}/{@code accelerator_powered}）用的是另一张贴图，
 * 因此不会被重映射，也不需要改它的模型。
 * <p>
 * shift 放在 static final：{@link net.createmod.catnip.render.StitchedSprite} 要在图集 stitch 之前
 * 进全局表，stitch 完才能拿到真正的 sprite；本类由 {@link #register()} 在客户端初始化时加载，
 * 时机与水晶电池一致。
 */
public class AcceleratorModel extends CTModel {

    private static final CTSpriteShiftEntry CASING_SHIFT = CTSpriteShifter.getCT(
            AllCTTypes.OMNIDIRECTIONAL,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/accelerator/electric_casing"),
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/accelerator/electric_casing_connected"));

    /** 在客户端初始化时挂到 Create 的模型替换器上，让本方块用这个模型而不是普通烘培模型 */
    public static void register() {
        CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                .register(ModBlocks.ACCELERATOR.getId(), AcceleratorModel::new);
    }

    private AcceleratorModel(BakedModel originalModel) {
        super(originalModel, new SimpleCTBehaviour(CASING_SHIFT));
    }
}
