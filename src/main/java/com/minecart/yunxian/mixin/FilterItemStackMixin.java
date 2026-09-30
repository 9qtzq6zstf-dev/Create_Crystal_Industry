package com.minecart.yunxian.mixin;

import com.minecart.yunxian.item.ResonanceFilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让共振过滤器无论何时都能拿到自己的判定对象。
 * <p>
 * <b>为什么非要注入</b>：Create 的入口条件是
 * <pre>
 * if (!filter.isComponentsPatchEmpty() &amp;&amp; filter.getItem() instanceof FilterItem item)
 *     return item.makeStackWrapper(filter);
 * return new FilterItemStack(filter);
 * </pre>
 * 而 {@code isComponentsPatchEmpty()} 看的是<b>组件补丁</b>、不是原型的默认组件
 * （{@code ItemStack.isComponentsPatchEmpty} → {@code PatchedDataComponentMap.isPatchEmpty}
 * → {@code patch.isEmpty()}）。也就是说 {@code new ItemStack(共振过滤器)} 这种干净栈
 * ——{@code /give} 给的、创造标签页里的、JEI 作弊拿到的——补丁是空的，会掉回基类
 * {@code FilterItemStack}，判定静默退化成「只匹配另一个共振过滤器」，什么都不收，
 * <b>而且没有任何报错</b>。
 * <p>
 * 靠「配方产物带上 components」只能盖住配方这一条路，盖不住 {@code /give} 和 JEI，
 * 所以在这里直接接管，把这一整类静默失效从根上去掉。
 * <p>
 * 放在 mixins.json 的 {@code "mixins"} 段（不是 {@code "client"}）：判定是双端都要用的。
 */
@Mixin(FilterItemStack.class)
public abstract class FilterItemStackMixin {

    @Inject(
            method = "of(Lnet/minecraft/world/item/ItemStack;)Lcom/simibubi/create/content/logistics/filter/FilterItemStack;",
            at = @At("HEAD"),
            cancellable = true)
    private static void yunxian$resonanceFilter(ItemStack filter, CallbackInfoReturnable<FilterItemStack> cir) {
        if (filter.getItem() instanceof ResonanceFilterItem item) {
            cir.setReturnValue(item.makeStackWrapper(filter));
        }
    }
}
