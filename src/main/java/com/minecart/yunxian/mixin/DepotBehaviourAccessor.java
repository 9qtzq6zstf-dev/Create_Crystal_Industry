package com.minecart.yunxian.mixin;

import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.Supplier;

/**
 * 让外部包能设置 {@link DepotBehaviour} 的 {@code maxStackSize}。
 * <p>
 * 那个字段是<b>包私有</b>的（`Supplier&lt;Integer&gt; maxStackSize;`，没有修饰符），
 * Create 自己的加权弹射器能写它是因为它就在 {@code content.logistics.depot} 包里；
 * 我们跨包，子类化也没用（包私有成员对别的包的子类一样不可见），所以只能开一个访问器。
 * <p>
 * 它控制的是「这张台子最多堆多少个物品」：{@code DepotBehaviour} 在合并与插入时都会读它
 * （见 {@code DepotBehaviour} 里那句 {@code Math.min(maxStackSize.get() == 0 ? 64 : ..., ...)}，
 * 也就是 0 等于"不限、按物品自身堆叠上限"）。
 * <p>
 * 放 mixins.json 的 {@code "mixins"} 段 —— 方块实体是双端都跑的。
 */
@Mixin(DepotBehaviour.class)
public interface DepotBehaviourAccessor {

    @Accessor("maxStackSize")
    void setMaxStackSize(Supplier<Integer> maxStackSize);
}
