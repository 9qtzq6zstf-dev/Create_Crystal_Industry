package com.minecart.yunxian.integration.deployerlib;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * 「Deployer」库（deployer）的非物品包裹兼容：认出那些<b>装的不是物品</b>的包裹。
 * <p>
 * 那个库给 Create 的包裹体系补了一种新包裹——{@code GenericPackageItem}，它继承
 * {@code PackageItem}，内容物按"库存类型"（{@code StockInventoryType}）存，可以是流体、能量等等，
 * 但<b>不是</b>物品。依赖它的模组（例如 repackaged 的 {@code copper_bottle} / {@code brass_battery}）
 * 就是把流体、能量装进这种包裹里运的。
 * <p>
 * 含水吸尘器拆包的判据是"按 9 格读物品内容物"（{@code PackageItem#getContents}），这类包裹读出来必然是空的，
 * 于是会命中"空包裹直接销毁"，<b>里面的流体或能量整包蒸发</b>。所以它们与「流体包裹」的铜质包裹同理：
 * 照常吸得进来，只是含水也不拆，原样躺在槽里（见 {@code MechanicalCleanerBlockEntity#unpackWaterloggedPackages}）。
 * <p>
 * <b>为什么按类判而不是按"内容物是否为空"判</b>：空包裹本来就该销毁（玩家丢进去的纸盒），
 * 只有"压根不是装物品"的包裹才需要留。那个库里没有物品型的 {@code StockInventoryType}，
 * 所以 {@code GenericPackageItem} 全都可以按这个口径处理。
 * <p>
 * <b>为什么用反射</b>：deployer 只是运行期的可选依赖，本模组不该为它加编译期依赖；
 * 需要问的只有"这个物品是不是那个类"，一次 {@code Class.forName} 就够。
 */
public final class NonItemPackageCompat {

    private static final String MOD_ID = "deployer";
    private static final String PACKAGE_CLASS = "net.liukrast.deployer.lib.logistics.packager.GenericPackageItem";

    /** 已经确认查不到（没装模组 / 版本对不上）。与 {@link #isNonItemPackage} 一起构成可用性判断 */
    private static boolean unavailable;

    private static Class<?> packageClass;

    private NonItemPackageCompat() {
    }

    /**
     * 这个包裹装的是不是"非物品"的库存（流体、能量……）。
     * <p>
     * 没装 deployer 时一律返回 {@code false}，也就是"按普通包裹处理"——兼容层缺席不该改变原有行为。
     */
    public static boolean isNonItemPackage(ItemStack box) {
        if (box.isEmpty() || !resolve())
            return false;
        try {
            return packageClass.isInstance(box.getItem());
        } catch (RuntimeException e) {
            // 兼容层出任何问题都退回原行为，不能连累吸尘器本身
            unavailable = true;
            packageClass = null;
            return false;
        }
    }

    private static boolean resolve() {
        if (packageClass != null)
            return true;
        if (unavailable)
            return false;
        // 加载早期的 ModList 可能还没建好，这时候不能就此判"没装"，留到下次再问
        if (ModList.get() == null || !ModList.get().isLoaded(MOD_ID))
            return false;
        unavailable = true;
        try {
            packageClass = Class.forName(PACKAGE_CLASS);
            unavailable = false;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
