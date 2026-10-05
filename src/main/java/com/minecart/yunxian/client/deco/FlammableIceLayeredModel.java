package com.minecart.yunxian.client.deco;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTModel;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.HorizontalCTBehaviour;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;

/**
 * 层叠可燃冰块的连接材质模型——与可燃冰柱（{@link FlammableIcePillarModel}）同一套做法，
 * 照搬 Create 调色板里 {@code LAYERED} 那一档：{@link HorizontalCTBehaviour} + 两张
 * {@link CTSpriteShiftEntry}。
 * <p>
 * 与柱子只有两点不同：
 * <ul>
 *   <li><b>行为类</b>：柱子用 {@code RotatedPillarCTBehaviour}（会跟着轴向转、认四个连接位），
 *       这里用 {@link HorizontalCTBehaviour}（只认水平方向那一档）；</li>
 *   <li><b>侧面的 CT 类型</b>：{@link AllCTTypes#HORIZONTAL_KRYPPERS}，只有 2×2 格，
 *       所以 {@code layered_flammable_ice_connected.png} 是 32×32（柱身那档 RECTANGLE 是 4×4 的 64×64）。</li>
 * </ul>
 * <b>端面共用同一对贴图</b>：Create 那边 LAYERED 与 PILLAR 的 {@code CTs.CAP} 也是同一个，
 * 所以这里用的还是那两张真的 {@code flammable_ice_cap(.|_connected)}，不是占位图。
 * 两张 shift 的参数逐字相同，{@code CTSpriteShifter.getCT} 内部有缓存，拿到的是同一个实例。
 */
public class FlammableIceLayeredModel extends CTModel {

    /** 侧面：2×2 图集 */
    private static final CTSpriteShiftEntry LAYERED_SHIFT =
            ctShift("layered_flammable_ice", AllCTTypes.HORIZONTAL_KRYPPERS);

    /** 端面：8×8 图集，与柱子共用 */
    private static final CTSpriteShiftEntry CAP_SHIFT =
            ctShift("flammable_ice_cap", AllCTTypes.OMNIDIRECTIONAL);

    /** 在客户端初始化时挂到 Create 的模型替换器上 */
    public static void register() {
        CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                .register(ModBlocks.LAYERED_FLAMMABLE_ICE.getId(), FlammableIceLayeredModel::new);
    }

    /** 配对命名：{@code <名字>} 与 {@code <名字>_connected}，与资源目录下的文件名一致 */
    private static CTSpriteShiftEntry ctShift(String name, AllCTTypes type) {
        ResourceLocation base =
                ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/deco/" + name);
        return CTSpriteShifter.getCT(type, base, base.withSuffix("_connected"));
    }

    private FlammableIceLayeredModel(BakedModel originalModel) {
        super(originalModel, new HorizontalCTBehaviour(LAYERED_SHIFT, CAP_SHIFT));
    }
}
