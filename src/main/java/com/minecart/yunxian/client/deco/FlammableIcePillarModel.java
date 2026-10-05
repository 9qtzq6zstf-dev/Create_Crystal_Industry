package com.minecart.yunxian.client.deco;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.RotatedPillarCTBehaviour;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * 可燃冰柱的连接材质模型——照搬 Create 自己给 {@code create:granite_pillar} 那一档柱子的做法
 * （见 {@code PaletteBlockPattern.PILLAR}）：{@link RotatedPillarCTBehaviour} + 两张
 * {@link CTSpriteShiftEntry}。
 * <p>
 * <b>两张图的规格不是随便定的</b>：CT 的贴图重映射要按「图集里切出多少格」来定位，
 * 格数由 {@link AllCTTypes} 的类型决定，所以 {@code _connected} 那张图的像素尺寸必须配得上——
 * <ul>
 *   <li>柱身 {@link AllCTTypes#RECTANGLE}：4×4 格，所以 {@code flammable_ice_pillar_connected.png} 是 64×64；</li>
 *   <li>端面 {@link AllCTTypes#OMNIDIRECTIONAL}：8×8 格，所以 {@code flammable_ice_cap_connected.png} 是 128×128。</li>
 * </ul>
 * 换成别的 CT 类型（比如 CAP 用 {@code HORIZONTAL}）就得跟着重画贴图，不是改一行代码的事。
 * <p>
 * <b>shift 的 original 必须与模型文件里写的贴图路径逐字相同</b>：CT 只在「某个面当前用的贴图
 * == shift 的 original」时才重映射（见 {@code CTModel#getQuads}），差一个字就静默失效、退回一张平贴图。
 * 这里的两个名字与 {@code models/block/deco/flammable_ice_pillar(.|_horizontal).json} 里的
 * {@code side} / {@code end} 一一对应。
 */
public class FlammableIcePillarModel extends CTModel {

    /** 柱身：4×4 图集 */
    private static final CTSpriteShiftEntry PILLAR_SHIFT =
            ctShift("pillar", AllCTTypes.RECTANGLE);

    /** 两端的盖子：8×8 图集 */
    private static final CTSpriteShiftEntry CAP_SHIFT =
            ctShift("cap", AllCTTypes.OMNIDIRECTIONAL);

    /** 在客户端初始化时挂到 Create 的模型替换器上，让本方块用这个模型而不是普通烘培模型 */
    public static void register() {
        CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                .register(ModBlocks.FLAMMABLE_ICE_PILLAR.getId(), FlammableIcePillarModel::new);
    }

    /** 配对命名：{@code <名字>} 与 {@code <名字>_connected}，与资源目录下的文件名一致 */
    private static CTSpriteShiftEntry ctShift(String name, AllCTTypes type) {
        String dir = "block/deco/flammable_ice_";
        ResourceLocation base = ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, dir + name);
        return CTSpriteShifter.getCT(type, base, base.withSuffix("_connected"));
    }

    private FlammableIcePillarModel(BakedModel originalModel) {
        super(originalModel, new RotatedPillarCTBehaviour(PILLAR_SHIFT, CAP_SHIFT));
    }
}
