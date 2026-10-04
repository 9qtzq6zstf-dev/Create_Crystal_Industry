package com.minecart.yunxian.compat.jei;

import java.util.List;

import net.minecraft.world.item.ItemStack;

/**
 * 「侵染」页上的一条配方——<b>只为 JEI 显示用</b>：从游戏的
 * {@code BuddingConversionRecipe} 映射过来，把方块摊成物品栈（JEI 的槽只认 ItemStack）。
 * <p>
 * 与 {@link BuddingInfo} 同样是纯 Minecraft 记录,<b>不引用任何 JEI 类型</b>——JEI 是本模组的
 * 可选依赖(compileOnly),数据类一旦沾上它,没装 JEI 的玩家就会被类加载拖下水。
 *
 * @param budding     箭头上方那个槽里的母岩物品;它同时是"对着母岩按 R/U"的检索材料
 * @param inputs      A 端:输入方块的物品（配方里是方块集合/标签，映射时展开成全部成员，
 *                    JEI 会在一个槽里轮播，每个成员都能被单独检索）
 * @param output      B 端;"变成本母岩自身"那一类在映射时展开成本母岩的物品
 * @param chance      概率基数 n。
 *                    <b>注意这是规则级的</b>:引擎每条规则每个随机刻掷一次 n 面骰,命中后在半径内
 *                    随机取一格、按声明顺序取第一条匹配的替换——所以同一规则下的石头/深板岩两条
 *                    配方共用同一次掷骰,不是各自 1/n。tooltip 的措辞要照这个口径写。
 * @param radius      取格范围:半径 1 是 3×3×3 去掉中心,2 更大
 * @param energyGated 命中后还要过一道付费钩子(远古残骸、AE2 福鲁伊克斯的传播)
 * @param ownerId     母岩的配置标识（家族 id 或方块 id），与运行时判定"这块母岩允不允许侵染"用的是同一个
 */
public record BuddingConversion(ItemStack budding,
                                List<ItemStack> inputs,
                                ItemStack output,
                                int chance,
                                int radius,
                                boolean energyGated,
                                String ownerId) {
}
