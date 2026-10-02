package com.minecart.yunxian.budding;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentPredicate;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.alchemy.PotionContents;
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
 * 认哪种流体有三种写法：
 * <ul>
 *   <li><b>流体 id</b>（{@code minecraft:lava}）——按<b>流体类型</b>（{@code FluidType}）判定，
 *       所以同一种流体的静止与流动变体都算（{@code lava} 与 {@code flowing_lava} 共用一个
 *       {@code FluidType}，见 {@code CommonHooks#getVanillaFluidType}），玩家用桶灌还是用管道抽都不会被挡；</li>
 *   <li><b>流体标签</b>（{@code #minecraft:lava}）——标签覆盖的全部流体都算，
 *       要一次收下好几个同族流体（比如自己模组的三种糖浆）时才需要；</li>
 *   <li><b>流体 + 数据组件</b>（{@code create:potion[minecraft:potion_contents={potion:"minecraft:swiftness"}]}）
 *       ——精确到"哪一种"。机械动力的药水就是<b>一个</b>流体，迅捷/力量的区别全在组件上，
 *       只有这种写法才拦得住别人往罐里灌别的药水，显示的名字也才能从笼统的「药水」变成「迅捷药水」。</li>
 * </ul>
 * <p>
 * 本类（{@link #parse}）在构造时就把 id 解析成注册表对象，所以调用时<b>流体注册表（{@code minecraft:fluid}）
 * 必须已经注册完</b>——它排在方块之前，KubeJS 的方块注册脚本正好满足（脚本跑到
 * {@code CustomBudding.create} 时全部流体都在表里了）。带组件的那种写法要多读两个东西，
 * 但同样安全：{@code Potion.CODEC} 挂在<b>内置</b>的药水注册表上（bootstrap 期就绪），
 * {@code DataComponentPatch.CODEC} 是普通 codec，用 {@code NbtOps} 就能解。
 * <p>
 * 注意<b>只碰流体注册表本身</b>：流体类型（{@code neoforge:fluid_type}）与方块自己都排在它后面，
 * 那时读会抛 {@code Trying to access unbound value}（详见 {@link #resolve}）。
 */
public record FluidRequirement(@Nullable Fluid fluid, @Nullable TagKey<Fluid> tag,
                               @Nullable DataComponentPredicate components,
                               int costPerGrowth, int capacity) {

    /**
     * 按脚本给的字符串解析。
     * <ul>
     *   <li>{@code "#minecraft:lava"} —— 流体标签；</li>
     *   <li>{@code "minecraft:lava"} —— 流体 id（不带命名空间的 {@code "lava"} 也认，落在
     *       {@code minecraft} 下，与 {@code ResourceLocation} 的约定一致）；</li>
     *   <li>{@code "create:potion[minecraft:potion_contents={potion:\"minecraft:swiftness\"}]"}
     *       —— 流体 id + 数据组件，方括号里与<b>原版物品</b>的写法一致：一串 {@code 组件id=值}，
     *       值用 SNBT。标签不能带组件（见 {@link #componentsOf} 与构造器里的校验）。</li>
     * </ul>
     *
     * @param costPerGrowth 每次成功生长消耗多少 mB（必须 ≥ 1）
     * @param capacity      母岩的罐能装多少 mB（必须 ≥ {@code costPerGrowth}）
     * @throws IllegalArgumentException 流体不存在、id 不合法、组件写错，或两个数字不成立
     */
    public static FluidRequirement parse(String fluidOrTag, int costPerGrowth, int capacity) {
        String name = fluidOrTag.trim();
        if (name.isEmpty()) {
            throw new IllegalArgumentException("流体需求写了个空字符串（写流体 id 或 \"#流体标签\"）");
        }
        if (name.startsWith("#")) {
            if (name.indexOf('[') >= 0) {
                throw new IllegalArgumentException("流体标签不能再带数据组件：" + fluidOrTag
                        + "（标签里可能有好几种流体，一条组件说明不了它们全部；要精确到某一种就写流体 id）");
            }
            return ofTag(tag(name.substring(1)), costPerGrowth, capacity);
        }

        int open = name.indexOf('[');
        if (open < 0) {
            return ofFluid(resolve(name), costPerGrowth, capacity);
        }
        Fluid fluid = resolve(name.substring(0, open).trim());
        return ofFluid(fluid, componentsOf(fluid, name.substring(open), fluidOrTag), costPerGrowth, capacity);
    }

    /** 认这一种流体（按流体类型判定，静止/流动变体都算） */
    public static FluidRequirement ofFluid(Fluid fluid, int costPerGrowth, int capacity) {
        return new FluidRequirement(fluid, null, null, costPerGrowth, capacity);
    }

    /**
     * 认这一种流体，<b>且必须带这些数据组件</b>——机械动力的药水靠它精确到某一种药水。
     * <p>
     * 判定是<b>子集</b>：罐里那份流体只要<b>含有</b>这些组件就算数，多出来的组件不影响
     * （与 NeoForge 的 {@code DataComponentFluidIngredient.of(false, stack)} 一致，
     * 创造内部也是这么写的）。传 {@code null} 或空判定等于不要求组件。
     */
    public static FluidRequirement ofFluid(Fluid fluid, @Nullable DataComponentPredicate components,
                                           int costPerGrowth, int capacity) {
        return new FluidRequirement(fluid, null, components, costPerGrowth, capacity);
    }

    /** 认这个标签下的全部流体 */
    public static FluidRequirement ofTag(TagKey<Fluid> tag, int costPerGrowth, int capacity) {
        return new FluidRequirement(null, tag, null, costPerGrowth, capacity);
    }

    /**
     * 照着一个流体栈认：流体 + 栈上的<b>全部</b>组件。
     * <p>
     * 附属模组想给母岩指定"只要迅捷药水"时最顺手的入口——手上已经有一份样本栈的话
     * （例如从创造那边拿到的 {@code PotionFluid.of(...)}），直接丢进来即可。
     * 与 {@code DataComponentFluidIngredient.of(false, template)} 是同一个意思。
     */
    public static FluidRequirement ofStack(FluidStack template, int costPerGrowth, int capacity) {
        if (template.isEmpty()) {
            throw new IllegalArgumentException("不能用空流体栈当流体需求");
        }
        return ofFluid(template.getFluid(), DataComponentPredicate.allOf(template.getComponents()),
                costPerGrowth, capacity);
    }

    /** 罐里的这份流体算不算数（空栈一律不算） */
    public boolean matches(FluidStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (tag != null) {
            return stack.is(tag);
        }
        if (stack.getFluidType() != fluid.getFluidType()) {
            return false;
        }
        return components == null || components.test(stack);
    }

    /**
     * 流体的显示名（护目镜浮窗与 JEI 页面用）：流体走 {@code FluidType} 的翻译名
     * （{@code minecraft:lava} → 「熔岩」）；标签查一条翻译，查不到就退回字面的 {@code #minecraft:lava}。
     * <p>
     * 标签本身没有名字可翻，所以约定一条<b>带兜底</b>的翻译键
     * {@code create_crystal_industry.fluid_tag.<命名空间>.<路径>}：语言文件里写了就是人话，
     * 没写就原样显示标签名——整合包/附属模组可以给别的标签补上自己的翻译。
     * 本模组已把常见的那批都备好了（{@code lang/*.json}）：原版的 {@code #minecraft:lava} /
     * {@code #minecraft:water}，加上 NeoForge 公共标签 {@code #c:*} 的<b>全部</b>条目
     * （水、熔岩、奶、药水、几种炖菜、经验等，其中 {@code #c:chocolate} 与 {@code #c:tea}
     * 是机械动力补进来的）——脚本直接写这些标签就能显示成人话，不必自己补语言键。
     * 另有两条公共标签（{@code #c:gaseous}、{@code #c:hidden_from_recipe_viewers}）<b>故意不翻</b>：
     * 它们描述的是流体性质、不是"某种流体"，当流体名显示反而误导，就让它退回字面写法。
     * <p>
     * <b>写成流体 id 不需要这些键</b>：{@code minecraft:water}、{@code create:honey} 之类走的是
     * {@code FluidType} 自带的翻译名（NeoForge 把原版流体指向 {@code block.minecraft.water}，
     * Create 给自家的写了 {@code fluid.create.honey}），照常跟着语言走。
     * <p>
     * <b>不去把标签解析成某一种流体</b>（那样也能白捡到名字），有两个理由：一是标签本来就可以装
     * 好几种流体，没有"它的名字"可言——{@code #minecraft:lava} 里就躺着 lava 与 flowing_lava
     * 两种流体，只是共用一种 {@code FluidType} 才显得只有一种；二是解析要读标签注册表，
     * 而本类刻意<b>只读翻译、不读注册表</b>，免得在渲染路径上撞上注册表尚未绑定的情况
     * （风险见 {@link #resolve} 那条注意事项）。
     */
    public Component displayName() {
        if (tag != null) {
            return tagDisplayName(tag);
        }
        if (components == null) {
            return fluid.getFluidType().getDescription();
        }
        // 带组件的流体（药水这类）名字是"带栈"的：拿一个只有组件、没有量的样本去问，
        // 才会得到「迅捷药水」而不是笼统的「药水」。纯数据、不读注册表，客户端也调得动。
        return displayNameOf(template());
    }

    /**
     * 一个流体栈的显示名（护目镜与 JEI 共用），一般就是 {@link FluidStack#getHoverName()}，
     * 但<b>药水额外补一个等级后缀</b>。
     * <p>
     * 原版（以及照抄原版命名的创造）的药水名里<b>没有等级</b>：{@code Potion#getName} 取的是药水的
     * <b>基础名</b>（{@code strong_swiftness} 的 name 字段就是 {@code "swiftness"}），
     * 所以迅捷 I 与迅捷 II 都叫 {@code item.minecraft.potion.effect.swiftness}——原版药水物品
     * 自己也是这么起名的（{@code PotionItem:138} 就是同一个调用），等级本来只写在效果那几行里
     * （{@code potion.withAmplifier} + {@code potion.potency.n}）。
     * <p>
     * 可我们这一行是"这块母岩<b>只认哪一种</b>"——不带等级就分不出 I 和 II，指示的作用就没了。
     * 所以这里照原版那套后缀自己补上（多效果取最高的那个等级，中英文都跟着语言走）。
     */
    public static Component displayNameOf(FluidStack stack) {
        Component name = stack.getHoverName();
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return name;
        }
        int amplifier = 0;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            amplifier = Math.max(amplifier, effect.getAmplifier());
        }
        // 0 级不补：potion.potency.0 是空串，补了只会多一个尾随空格
        return amplifier <= 0
                ? name
                : Component.translatable("potion.withAmplifier", name,
                        Component.translatable("potion.potency." + amplifier));
    }

    /** 标签的显示名：{@code create_crystal_industry.fluid_tag.<命名空间>.<路径>}，没翻译就用字面的 {@code #命名空间:路径} */
    private static Component tagDisplayName(TagKey<Fluid> tag) {
        ResourceLocation location = tag.location();
        return Component.translatableWithFallback(
                "create_crystal_industry.fluid_tag." + location.getNamespace() + "." + location.getPath(),
                "#" + location);
    }

    /** 只有组件、没有实际用量的样本栈，专供 {@link #displayName()} 问"带栈的名字" */
    private FluidStack template() {
        FluidStack stack = new FluidStack(fluid, 1);
        stack.applyComponents(components.asPatch());
        return stack;
    }

    /**
     * 两个数字都要成立，否则这个母岩永远长不出来（罐装不下一次消耗）或者一次都扣不动
     * ——这种配置属于写错了，在这里当场报出来，而不是让玩家对着一个不生长的方块找原因。
     * <p>
     * 组件那一项另有两道：标签不能配组件（组件描述的是某一批具体流体，标签可能一装好几种）；
     * {@code []} 这种空写法归一化成"没写"，免得判定里挂着一个永远为真的空条件。
     */
    public FluidRequirement {
        if ((fluid == null) == (tag == null)) {
            throw new IllegalArgumentException("流体需求必须二选一：要么是流体，要么是流体标签");
        }
        if (components != null && tag != null) {
            throw new IllegalArgumentException("流体标签不能再带数据组件（标签里可能有好几种流体，"
                    + "一条组件说明不了它们全部）——要精确到某一种就写流体 id");
        }
        if (components != null && components.alwaysMatches()) {
            components = null;
        }
        if (costPerGrowth < 1) {
            throw new IllegalArgumentException("每次生长消耗的流体量必须 ≥ 1 mB，收到 " + costPerGrowth);
        }
        if (capacity < costPerGrowth) {
            throw new IllegalArgumentException("母岩的流体容量（" + capacity + " mB）装不下一次生长的消耗（"
                    + costPerGrowth + " mB），这样永远长不出来");
        }
    }

    /**
     * 把 {@code [组件]} 那一段解析成判定。
     * <p>
     * 写法与原版物品的 {@code [组件]} 一致：方括号里是一串 {@code 组件id=值}，值用 SNBT
     * （{@code create:potion[minecraft:potion_contents={potion:"minecraft:swiftness"}]}）。
     * <p>
     * <b>不能把整段直接丢给 SNBT 解析器</b>：SNBT 的不加引号的"键"只认
     * {@code [0-9A-Za-z_.+-]}，读到 {@code minecraft} 就在冒号处断了（实测报
     * {@code Expected '}' at position 26}）。原版物品那套 {@code [组件]} 同样不是 SNBT——
     * {@code ItemParser} 是把组件名当<b>资源位置</b>逐个读出来的。所以这里也自己切：
     * 按顶层逗号分成若干 {@code 组件id=值}，组件名当普通字符串塞进 {@link CompoundTag}
     * （键只在内存里，不再过词法），值再单独交给 SNBT。
     * <p>
     * 解析完不直接拿 patch 当判定，而是把它盖在一个只有 1 mB 的样本栈上，取<b>整个栈的组件</b>
     * 做判定——与 {@code DataComponentFluidIngredient.of(false, stack)} 完全同款（创造也这么写）。
     */
    private static DataComponentPredicate componentsOf(Fluid fluid, String bracketPart, String whole) {
        if (!bracketPart.startsWith("[") || !bracketPart.endsWith("]")) {
            throw new IllegalArgumentException("流体组件要写成 流体id[组件id=值]，方括号没闭合：" + whole);
        }
        String inner = bracketPart.substring(1, bracketPart.length() - 1).trim();
        if (inner.isEmpty()) {
            return DataComponentPredicate.EMPTY; // [] = 没写，构造器会归一化成 null
        }

        CompoundTag root = new CompoundTag();
        for (String entry : splitTopLevel(inner, ',')) {
            if (entry.isBlank()) {
                continue;
            }
            List<String> pair = splitTopLevel(entry, '=');
            String typeId = pair.get(0).trim();
            if (typeId.isEmpty() || pair.size() == 1) {
                throw new IllegalArgumentException("流体组件缺了值：" + entry.trim()
                        + "（该写成 组件id=值，如 minecraft:potion_contents={potion:\"minecraft:swiftness\"}）");
            }
            // 借一个占位键让 SNBT 去解析值：这样数字、字符串、列表、复合标签都认，
            // 又不必碰"值本身长什么样"（{} 只是 parseTag 的入口形状，v 只是个壳）
            String valueText = String.join("=", pair.subList(1, pair.size())).trim();
            Tag value;
            try {
                value = TagParser.parseTag("{v:" + valueText + "}").get("v");
            } catch (CommandSyntaxException e) {
                throw new IllegalArgumentException("流体组件 " + typeId + " 的值不是合法的 SNBT："
                        + valueText + "（" + e.getMessage() + "）", e);
            }
            if (value == null) {
                throw new IllegalArgumentException("流体组件 " + typeId + " 没写出值：" + entry.trim());
            }
            root.put(typeId, value);
        }
        if (root.isEmpty()) {
            return DataComponentPredicate.EMPTY;
        }

        DataComponentPatch patch = DataComponentPatch.CODEC.parse(NbtOps.INSTANCE, root)
                .getOrThrow(message -> new IllegalArgumentException("流体组件解析失败：" + whole
                        + "（" + message + "）"));

        FluidStack probe = new FluidStack(fluid, 1);
        probe.applyComponents(patch);
        return DataComponentPredicate.allOf(probe.getComponents());
    }

    /**
     * 按<b>顶层</b>分隔符切分：括号（{@code {}} / {@code []}）与引号之内的分隔符不算数，
     * 所以 {@code a={x:1,y:2},b=3} 会在第一个逗号处切开，而不会切进 {@code {}} 里面。
     */
    private static List<String> splitTopLevel(String text, char separator) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (c == '\\') {
                    i++; // 跳过被转义的字符（\" 之类）
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                depth--;
            } else if (c == separator && depth == 0) {
                parts.add(text.substring(start, i));
                start = i + 1;
            }
        }
        parts.add(text.substring(start));
        return parts;
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
