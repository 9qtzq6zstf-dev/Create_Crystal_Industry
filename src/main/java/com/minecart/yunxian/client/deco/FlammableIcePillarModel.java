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

    /**
     * 在首次烘焙之前挂到 Create 的模型替换器上（调用点见 {@code ModRenderers#registerConnectedModelSwaps}）。
     * 这里只登记工厂、不解析贴图——Create 那边 {@code CreateRegistrate#registerCTBehviour} 也是
     * {@code model -> new CTModel(model, behaviour.get())}，行为对象留到真正建模型时才取。
     */
    public static void register() {
        CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                .register(ModBlocks.FLAMMABLE_ICE_PILLAR.getId(), FlammableIcePillarModel::create);
    }

    /**
     * 烘焙期才建两张 sprite（柱身 {@link AllCTTypes#RECTANGLE} 4×4、端面
     * {@link AllCTTypes#OMNIDIRECTIONAL} 8×8）。
     * <p>
     * 不放在 {@code static final} 字段上，是因为要等图集拼好再取：早于图集去取，名字一样也只会拿到
     * 占位 sprite，而 CT 只在「面当前用的贴图 == shift 的 original」时才重映射
     * （见 {@code CTModel#getQuads}），一错就是整块退回平贴图、静默失效。
     */
    private static FlammableIcePillarModel create(BakedModel originalModel) {
        return new FlammableIcePillarModel(originalModel, new RotatedPillarCTBehaviour(
                ctShift("pillar", AllCTTypes.RECTANGLE),
                ctShift("cap", AllCTTypes.OMNIDIRECTIONAL)));
    }

    /** 配对命名：{@code <名字>} 与 {@code <名字>_connected}，与资源目录下的文件名一致 */
    private static CTSpriteShiftEntry ctShift(String name, AllCTTypes type) {
        String dir = "block/deco/flammable_ice_";
        ResourceLocation base = ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, dir + name);
        return CTSpriteShifter.getCT(type, base, base.withSuffix("_connected"));
    }

    private FlammableIcePillarModel(BakedModel originalModel, RotatedPillarCTBehaviour behaviour) {
        super(originalModel, behaviour);
    }
}
