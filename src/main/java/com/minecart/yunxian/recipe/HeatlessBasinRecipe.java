package com.minecart.yunxian.recipe;

import com.minecart.yunxian.block.SmartTemperatureChamberBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;

import net.minecraft.world.item.crafting.Recipe;

/**
 * 一个<b>同一份配方、但没有热量要求</b>的壳：智能温控室在供热时，工作盆拿它去挑配方与执行，
 * 于是不管哪一路判定都拦不住。
 * <p>
 * <b>为什么不是"跳过热量判定"</b>：{@code HeatCondition#testBlazeBurner} 只是 Create 自己那一关。
 * 别的模组会在 {@code BasinRecipe.apply} 上挂自己的 mixin，判的<b>根本不是热量档位</b>——
 * CMR 的雪人冷却器看的是"配方热量要求是不是它自己加的自定义条件"，
 * FluidLogistics 的烈焰冷却器看的是"这个配方是不是实现了它的 {@code CoolingRecipe} 接口"，
 * 而且它们都在 {@code @At("HEAD")} 直接取消方法。想"抢先"就得打注入顺序的仗，不可靠。
 * <p>
 * 换个思路就简单了：<b>别让它们看见那份"有要求"的配方</b>。本类继承 {@link BasinRecipe}
 * （所以 {@code recipe instanceof BasinRecipe} 依旧成立，物品与流体原料、产出、时长
 * 全部由父类照原参数填好，一个字段都不用抄），只做两件事：
 * <ul>
 *   <li>{@link #getRequiredHeat()} 恒为 {@link HeatCondition#NONE} —— Create 的判定恒真，
 *       CMR 那种"热量要求是自定义条件才管"的门连进都不进；</li>
 *   <li><b>不实现</b>{@code CoolingRecipe} —— FluidLogistics 的 {@code instanceof} 直接判否，
 *       它自己就退出了。</li>
 * </ul>
 * 要诀是那些模组的 mixin 照跑，只是它们判断完决定不管：不抢顺序、不比优先级、不要编译依赖。
 * <p>
 * 剂量由调用方把关：只有盆下面是温控室<b>且在供热</b>时才换壳
 * （见 {@link #withoutHeatIfChamberHeats}），罐空了照旧走原配方，热量要求照旧生效。
 */
public class HeatlessBasinRecipe extends BasinRecipe {

    public HeatlessBasinRecipe(ProcessingRecipeParams params) {
        super(params);
    }

    @Override
    public HeatCondition getRequiredHeat() {
        return HeatCondition.NONE;
    }

    /**
     * 该不该换壳：盆下面是温控室且在供热 → 换成没有热量要求的壳；其余情况原样还回去。
     * <p>
     * 只认 {@link BasinRecipe}：非 BasinRecipe 的配方本来就不查热量（{@code apply} 里那个
     * {@code isBasinRecipe} 分支），换壳没有意义，而且壳是 BasinRecipe，塞给它们反而会改变行为。
     * <p>
     * 壳是当场新建的小对象（父类只做字段拷贝），不做缓存——调用点是"盆里东西变了才挑一次配方"
     * 与"每次成功执行一次配方"，量很小；省一个缓存就省掉了一套失效逻辑。
     */
    public static Recipe<?> withoutHeatIfChamberHeats(BasinBlockEntity basin, Recipe<?> recipe) {
        if (!(recipe instanceof BasinRecipe basinRecipe)
                || !SmartTemperatureChamberBlock.heatsBasin(basin)) {
            return recipe;
        }
        return new HeatlessBasinRecipe(basinRecipe.getParams());
    }
}
