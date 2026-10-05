package com.minecart.yunxian.client.echo;

import net.minecraft.client.resources.model.BakedModel;
import net.neoforged.neoforge.client.model.BakedModelWrapper;

/**
 * 只做一件事：把这个物品的模型标记成「平面物品」。
 *
 * <p>机械动力摆物品时靠 {@link BakedModel#isGui3d()} 分流（置物台 {@code DepotRenderer}、
 * 传送带 {@code BeltRenderer}、桌布 {@code TableClothRenderer}、滤槽 {@code ValueBoxRenderer}
 * 都是 {@code ItemDisplayContext.FIXED}）：报 true 就当成方块那样立着摆，报 false 才按平面物品
 * 那样躺下，并套用它自己那套位置与缩放。
 *
 * <p>而本物品顶层模型是 {@code neoforge:separate_transforms}，根不是 {@code builtin/generated}——
 * 原版只有走 {@code ItemModelGenerator} 生成器（根为生成标记）的物品模型才会被
 * {@code setGui3d(false)}，于是顶层 context 一直默认报 true，平面贴图在台面上就竖着立起来了。
 * 平面/手持 3D 的分发本来就由模型文件里的 {@code perspectives} 负责，这里只是补上这个分类标志。
 */
public class EchoSpyglassModel extends BakedModelWrapper<BakedModel> {

    public EchoSpyglassModel(BakedModel original) {
        super(original);
    }

    @Override
    public boolean isGui3d() {
        return false;
    }
}
