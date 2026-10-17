package com.minecart.yunxian.integration.fluidlogistics;

import java.lang.reflect.Method;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * 「机械动力：流体包裹」（fluidlogistics）的可选兼容：判断一个包裹是不是"碰水不拆"的那种。
 * <p>
 * 那个模组的半流体包裹用的是铜质包装，掉进水里<b>不会</b>像 Create 原版包裹那样自己散开
 * （它自己的 {@code PackageEntityMixin} 就是在 {@code onInsideBlock} 里把水的拆包拦掉的，
 * 判据是 {@code PackageResources.survivesWater}）。
 * <p>
 * 含水吸尘器把包裹吸进来是为了当场拆包，判据却是"按 9 格读物品内容物"——铜质流体包裹里装的是流体，
 * 按物品读出来是空的，于是会被当成空包裹直接销毁，<b>里面的流体整包蒸发</b>。所以这类包裹照常吸得进来，
 * 只是含水也不拆（见 {@code MechanicalCleanerBlockEntity#unpackWaterloggedPackages}）。
 * <p>
 * <b>为什么用反射</b>：那个模组只是运行期的可选项，本模组没有也不该为它加编译期依赖。
 * 需要调的只有两个静态方法，反射足够，且真没装时一次 {@code ModList} 判断就短路了。
 */
public final class FluidPackageCompat {

    private static final String MOD_ID = "fluidlogistics";
    private static final String API_CLASS = "com.yision.fluidlogistics.api.packager.PackageResources";

    /** 已经确认查不到（没装模组 / 版本对不上）。与 {@link #survivesWater} 一起构成可用性判断 */
    private static boolean unavailable;

    private static Method survivesWater;
    private static Method isBootstrapped;

    private FluidPackageCompat() {
    }

    /**
     * 这个包裹碰水会不会保持不拆（= 铜质流体包裹）。
     * <p>
     * 没装对应模组、或 API 还没 bootstrap 时一律返回 {@code false}，也就是"按普通包裹处理"——
     * 兼容层缺席不该改变原有行为。
     */
    public static boolean survivesWater(ItemStack box) {
        if (box.isEmpty() || !resolve())
            return false;
        try {
            // 与那个模组自己的 mixin 一致：registry 没 bootstrap 之前它也不认这个判据
            if (!(Boolean) isBootstrapped.invoke(null))
                return false;
            return (Boolean) survivesWater.invoke(null, box);
        } catch (ReflectiveOperationException | RuntimeException e) {
            // 兼容层出任何问题都退回原行为，不能连累吸尘器本身
            unavailable = true;
            survivesWater = null;
            isBootstrapped = null;
            return false;
        }
    }

    private static boolean resolve() {
        if (survivesWater != null)
            return true;
        if (unavailable)
            return false;
        // 加载早期的 ModList 可能还没建好，这时候不能就此判"没装"，留到下次再问
        if (ModList.get() == null || !ModList.get().isLoaded(MOD_ID))
            return false;
        unavailable = true;
        try {
            Class<?> api = Class.forName(API_CLASS);
            isBootstrapped = api.getMethod("isBootstrapped");
            survivesWater = api.getMethod("survivesWater", ItemStack.class);
            unavailable = false;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
