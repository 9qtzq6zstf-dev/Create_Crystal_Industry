package com.minecart.yunxian.mixin;

import com.minecart.yunxian.recipe.HeatlessBasinRecipe;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;

import net.minecraft.world.item.crafting.Recipe;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 让温控室在供热时，工作盆上面那台机器（搅拌机 / 压床……）改用
 * {@link HeatlessBasinRecipe} 那份"没有热量要求"的壳来挑配方与执行。
 *
 * <h2>为什么打在机器上，而不是 {BasinRecipe} 上</h2>
 * 冷却器那类模组是在 {@code BasinRecipe.apply} 里面挂 {@code @At("HEAD")} 的取消注入，
 * 抢在它们前面既不可靠（比的是注入顺序）也不礼貌。改在<b>调用点</b>下手就没这个问题：
 * 它们那些 mixin 的宿主方法照旧执行，只是拿到的配方已经没有热量要求了，它们自己就退出了。
 *
 * <h2>为什么是这两处</h2>
 * {@code BasinRecipe} 在本仓库能看到的调用点就这两个，正好覆盖"挑配方"与"执行配方"两段：
 * <ul>
 *   <li>{@code matchBasinRecipe} 里的 {@code BasinRecipe.match} —— 挑不中就不会成为
 *       {@code currentRecipe}，所以这一处不换壳的话后面白搭；</li>
 *   <li>{@code applyBasinRecipe} 里的 {@code BasinRecipe.apply} —— 真正扣料出货那一次。</li>
 * </ul>
 * 两处的目标是互为重载的不同方法（{@code match} / {@code apply}），所以是两个独立的
 * {@code @Redirect}；{@code match} 内部自己会调私有的 {@code apply(test=true)}，
 * 壳已经带在参数里了，不用再拦一次。
 * <p>
 * 剂量的判定（盆下面是温控室且在供热）不在这里，在
 * {@link HeatlessBasinRecipe#withoutHeatIfChamberHeats}——mixin 类里的成员会被整个拷进
 * 目标类，逻辑留在外面更干净，也好单独读懂。
 */
@Mixin(BasinOperatingBlockEntity.class)
public class BasinOperatingBlockEntityMixin {

    @Redirect(
            method = "matchBasinRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/processing/basin/BasinRecipe;"
                            + "match(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;"
                            + "Lnet/minecraft/world/item/crafting/Recipe;)Z"
            )
    )
    private static boolean yunxian$matchWithoutHeat(BasinBlockEntity basin, Recipe<?> recipe) {
        return BasinRecipe.match(basin, HeatlessBasinRecipe.withoutHeatIfChamberHeats(basin, recipe));
    }

    @Redirect(
            method = "applyBasinRecipe",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/simibubi/create/content/processing/basin/BasinRecipe;"
                            + "apply(Lcom/simibubi/create/content/processing/basin/BasinBlockEntity;"
                            + "Lnet/minecraft/world/item/crafting/Recipe;)Z"
            )
    )
    private static boolean yunxian$applyWithoutHeat(BasinBlockEntity basin, Recipe<?> recipe) {
        return BasinRecipe.apply(basin, HeatlessBasinRecipe.withoutHeatIfChamberHeats(basin, recipe));
    }
}
