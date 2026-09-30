package com.minecart.yunxian.budding;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * 生长要消耗的流体：母岩方块自己带一个小罐（容量 {@link #capacity()} mB），
 * 每次成功生长扣 {@link #costPerGrowth()} mB，罐里不够就放弃这次生长。
 * <p>
 * 参数从哪来有两种：自带家族写在家族表里（远古残骸母岩的熔岩罐就是 {@code BuddingFamilies} 里的
 * 一个常量），脚本与附属模组由这条定义给出——KubeJS 里就是 {@code CustomBuddingOptions#needfluid}。
 * 两者跑的是同一条路：方块实体是 {@code FluidTankBuddingBlockEntity}，付费钩子在
 * {@code GenericBuddingBlock#payGrowthCost}，脚本还能用 {@code CustomBudding.modify} 后改。
 * <p>
 * 认哪种流体有两种写法：
 * <ul>
 *   <li><b>流体 id</b>（{@code minecraft:lava}）——按<b>流体类型</b>（{@code FluidType}）判定，
 *       所以同一种流体的静止与流动变体都算（{@code lava} 与 {@code flowing_lava} 共用一个
 *       {@code FluidType}，见 {@code CommonHooks#getVanillaFluidType}），玩家用桶灌还是用管道抽都不会被挡；</li>
 *   <li><b>流体标签</b>（{@code #minecraft:lava}）——标签覆盖的全部流体都算，
 *       要一次收下好几个同族流体（比如自己模组的三种糖浆）时才需要。</li>
 * </ul>
 * <p>
 * 本类（{@link #parse}）在构造时就把 id 解析成注册表对象，所以调用时<b>流体注册表（{@code minecraft:fluid}）
 * 必须已经注册完</b>——它排在方块之前，KubeJS 的方块注册脚本正好满足（脚本跑到
 * {@code CustomBudding.create} 时全部流体都在表里了）。
 * <p>
 * 注意<b>只碰流体注册表本身</b>：流体类型（{@code neoforge:fluid_type}）与方块自己都排在它后面，
 * 那时读会抛 {@code Trying to access unbound value}（详见 {@link #resolve}）。
 */
public record FluidRequirement(@Nullable Fluid fluid, @Nullable TagKey<Fluid> tag,
                               int costPerGrowth, int capacity) {

    /**
     * 按脚本给的字符串解析：{@code "#minecraft:lava"} 是流体标签，其余当流体 id
     * （{@code "minecraft:lava"} / 不带命名空间的 {@code "lava"} 都行，后者落在 {@code minecraft} 下——
     * 与 {@code ResourceLocation} 的约定一致）。
     *
     * @param costPerGrowth 每次成功生长消耗多少 mB（必须 ≥ 1）
     * @param capacity      母岩的罐能装多少 mB（必须 ≥ {@code costPerGrowth}）
     * @throws IllegalArgumentException 流体不存在、id 不合法，或两个数字不成立
     */
    public static FluidRequirement parse(String fluidOrTag, int costPerGrowth, int capacity) {
        String name = fluidOrTag.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("流体需求写了个空字符串（写流体 id 或 \"#流体标签\"）");
        }
        if (name.startsWith("#")) {
            return ofTag(tag(name.substring(1)), costPerGrowth, capacity);
        }
        return ofFluid(resolve(name), costPerGrowth, capacity);
    }

    /** 认这一种流体（按流体类型判定，静止/流动变体都算） */
    public static FluidRequirement ofFluid(Fluid fluid, int costPerGrowth, int capacity) {
        return new FluidRequirement(fluid, null, costPerGrowth, capacity);
    }

    /** 认这个标签下的全部流体 */
    public static FluidRequirement ofTag(TagKey<Fluid> tag, int costPerGrowth, int capacity) {
        return new FluidRequirement(null, tag, costPerGrowth, capacity);
    }

    /** 罐里的这份流体算不算数（空栈一律不算） */
    public boolean matches(FluidStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (tag != null) {
            return stack.is(tag);
        }
        return stack.getFluidType() == fluid.getFluidType();
    }

    /**
     * 流体的显示名（护目镜浮窗与 JEI 页面用）：流体走 {@code FluidType} 的翻译名
     * （{@code minecraft:lava} → 「熔岩」），标签没有名字可翻，直接写 {@code #minecraft:lava}。
     * <p>
     * 只读翻译，不读注册表，所以客户端也调得动。
     */
    public Component displayName() {
        return tag != null ? Component.literal("#" + tag.location()) : fluid.getFluidType().getDescription();
    }

    /**
     * 两个数字都要成立，否则这个母岩永远长不出来（罐装不下一次消耗）或者一次都扣不动
     * ——这种配置属于写错了，在这里当场报出来，而不是让玩家对着一个不生长的方块找原因。
     */
    public FluidRequirement {
        if ((fluid == null) == (tag == null)) {
            throw new IllegalArgumentException("流体需求必须二选一：要么是流体，要么是流体标签");
        }
        if (costPerGrowth < 1) {
            throw new IllegalArgumentException("每次生长消耗的流体量必须 ≥ 1 mB，收到 " + costPerGrowth);
        }
        if (capacity < costPerGrowth) {
            throw new IllegalArgumentException("母岩的流体容量（" + capacity + " mB）装不下一次生长的消耗（"
                    + costPerGrowth + " mB），这样永远长不出来");
        }
    }

    private static Fluid resolve(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("不是合法的流体 id：" + id);
        }
        Fluid fluid = BuiltInRegistries.FLUID.get(location);
        if (fluid == null || fluid == Fluids.EMPTY) {
            throw new IllegalArgumentException("未知的流体：" + id
                    + "（要认一整个标签就加 # 前缀，如 \"#minecraft:lava\"）");
        }
        // 这里只查流体注册表本身（minecraft:fluid）——它排在方块之前注册，脚本注册方块时已经齐了。
        //
        // 千万别顺手在这儿读 Fluid#getFluidType()：流体类型是另一个注册表（neoforge:fluid_type），
        // NeoForge 自己的条目反而排在方块**之后**注册（注册事件先把原版注册表走一遍，
        // 其余按命名空间排序，neoforge:* 落在最后），此时 NeoForgeMod.LAVA_TYPE 还没绑定，
        // 读它会抛 "Trying to access unbound value"。本类的两个读流体类型的地方
        // （matches / displayName）都只在世界加载之后跑，那时一切都绑好了。
        return fluid;
    }

    private static TagKey<Fluid> tag(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        if (location == null) {
            throw new IllegalArgumentException("不是合法的流体标签 id：#" + id);
        }
        return TagKey.create(Registries.FLUID, location);
    }
}
