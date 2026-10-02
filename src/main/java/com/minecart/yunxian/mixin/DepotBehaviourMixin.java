package com.minecart.yunxian.mixin;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 关掉共振台上的「配方加工」。
 * <p>
 * 共振台组合了置物台的 {@code DepotBehaviour}，于是连它的加工链路一起继承了：台面上有物品时，
 * 它会往<b>上方两格</b>找一个 {@code BeltProcessingBehaviour}（黄铜机械手 / 注液器那类），
 * 把它当成"传送带上的物品"交给对方加工 —— 机械手就能在共振台上压印、注液、按配方加工。
 * <p>
 * 这不是共振台该有的功能：台面上的东西是<b>过滤规则</b>，不是待加工的料。所以这里把那一次
 * 查表重定向掉：目标是我们自己的方块实体时返回 null，{@code tick()} 紧接着就是
 * {@code if (processingBehaviour == null) return;}，加工这一段自然整段跳过。
 * <p>
 * 用 {@code @Redirect} 而不是给 {@code tick()} 挂个 HEAD 注入：那是个很长的 void 方法，
 * 取消它会把置物台的插入、堆叠、动画一并干掉；而我们只想掐掉末尾那一段。
 * {@code tick()} 里 {@code BlockEntityBehaviour.get} 全方法只有这一处调用（就是接加工的那句），
 * 所以这个重定向不会误伤别的。
 * <p>
 * 置物台自己不受影响：重定向里判了方块实体类型，不是共振台就原样走回原方法。
 * 放 mixins.json 的 {@code "mixins"} 段 —— 方块实体双端都跑。
 */
@Mixin(DepotBehaviour.class)
public class DepotBehaviourMixin {

    @Redirect(
            method = "tick()V",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;"
                            + "get(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;"
                            + "Lcom/simibubi/create/foundation/blockEntity/behaviour/BehaviourType;)"
                            + "Lcom/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour;"
            )
    )
    private BlockEntityBehaviour yunxian$noRecipeProcessing(BlockGetter reader, BlockPos pos,
                                                            BehaviourType<?> type) {
        if (((DepotBehaviour) (Object) this).blockEntity instanceof ResonanceTableBlockEntity)
            return null;
        return BlockEntityBehaviour.get(reader, pos, type);
    }
}
