package com.minecart.yunxian.recipe;

import java.util.List;
import java.util.Optional;

import com.minecart.yunxian.registry.ModRecipes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * 「侵染」：一块母岩在随机刻里把相邻方块 A 变成 B。这是本模组唯一的转化形态——石头→矿石与
 * 矿物块→母岩自身都只是它的两条配方，不再分两类写死的规则。
 * <p>
 * <b>一条配方 = 一条规则</b>，不是一对 A→B：母岩每个随机刻按 {@code chance} 掷一次骰，
 * 命中后在半径 {@code radius} 内随机取一格、再按 {@code replacements} 的顺序取第一条匹配的写入。
 * 所以「石头→铁矿石」与「深板岩→深层铁矿石」共用同一次掷骰——拆成两条配方会让矿石产出翻倍，
 * 也打乱随机数消耗顺序（那是对外契约，见 {@code BuddingRecipes}）。
 * <p>
 * 它同时是数据包配方（JSON 可写、可被整合包覆盖）与运行时规则（引擎按母岩方块取用）。
 * <b>不参与合成语义</b>：{@code matches} 恒 false、{@code assemble} 给空栈、{@code isSpecial} 为 true，
 * 只是走配方系统这条数据通道，好让脚本与数据包都能加。
 *
 * @param budding      哪块母岩会做这件事（只认本模组驱动的两种母岩方块）
 * @param chance       概率基数 n：每随机刻 1/n；必须 ≥ 1
 * @param radius       取格半径：{@code 2r+1} 的立方体去掉中心
 * @param gated        命中后还要过一道付费钩子（远古残骸的熔岩、福鲁伊克斯的 AE）
 * @param replacements 替换规则，按顺序取第一条匹配的
 */
public record BuddingConversionRecipe(Block budding,
                                      int chance,
                                      int radius,
                                      boolean gated,
                                      List<Replacement> replacements) implements Recipe<RecipeInput> {

    /**
     * 一条替换：把 {@code input} 里的方块换成 {@code output}。
     * <p>
     * 输入用 {@link HolderSet}：JSON 里写单个方块 id、id 列表或 {@code "#标签"} 都行，
     * 一步涵盖原来的"具体方块 / 方块标签"两种写法。
     * <p>
     * {@code output} 为空表示<b>变成本母岩自身</b>（再生传播/侵染），由引擎在运行时展开成母岩的
     * 默认方块状态——这样配方不必反过来引用它自己所属的母岩。
     */
    public record Replacement(HolderSet<Block> input, Optional<Block> output) {

        public static final MapCodec<Replacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                // homogeneousList:单个 id / id 列表 / "#标签" 三种写法都吃
                RegistryCodecs.homogeneousList(Registries.BLOCK).fieldOf("input").forGetter(Replacement::input),
                BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("output").forGetter(Replacement::output)
        ).apply(instance, Replacement::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Replacement> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.holderSet(Registries.BLOCK), Replacement::input,
                        ByteBufCodecs.optional(ByteBufCodecs.registry(Registries.BLOCK)), Replacement::output,
                        Replacement::new);
    }

    /**
     * 取格半径的上限：再大就是把整片地形当骰子，既吃性能也没手感。
     * 数据包 JSON 与 KubeJS 的 {@code .transform(...)} 共用这一个上限，不会一边收一边不收。
     */
    public static final int MAX_RADIUS = 8;

    public static final MapCodec<BuddingConversionRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            // 注意：方块字段要用注册表的 byNameCodec，不能用 Block.CODEC（那是带类型的 dispatch 编解码器）
            BuiltInRegistries.BLOCK.byNameCodec().fieldOf("budding").forGetter(BuddingConversionRecipe::budding),
            // chance 与 radius 必填：它们是这条配方的语义核心，没有自然的默认值。
            // 用带默认值的可选写法会同时踩两个坑——JSON 里看不到概率（读的人得去翻代码），
            // 以及漏写时静默变成默认值而不是报错。gated 例外：布尔开关不写即 false，不含糊。
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf("chance").forGetter(BuddingConversionRecipe::chance),
            Codec.intRange(0, MAX_RADIUS).fieldOf("radius").forGetter(BuddingConversionRecipe::radius),
            Codec.BOOL.optionalFieldOf("gated", false).forGetter(BuddingConversionRecipe::gated),
            Replacement.CODEC.codec().listOf().fieldOf("replacements")
                    .forGetter(BuddingConversionRecipe::replacements)
    ).apply(instance, BuddingConversionRecipe::new));

    /**
     * 配方要同步给客户端（{@code isSpecial} 也不例外），这个编解码器写错就是连接被踢、
     * JEI 一片空白——字段顺序与 {@link #CODEC} 一一对应，两端必须一致。
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, BuddingConversionRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(Registries.BLOCK), BuddingConversionRecipe::budding,
                    ByteBufCodecs.VAR_INT, BuddingConversionRecipe::chance,
                    ByteBufCodecs.VAR_INT, BuddingConversionRecipe::radius,
                    ByteBufCodecs.BOOL, BuddingConversionRecipe::gated,
                    Replacement.STREAM_CODEC.apply(ByteBufCodecs.list()), BuddingConversionRecipe::replacements,
                    BuddingConversionRecipe::new);

    /** 概率基数必须 ≥ 1，否则 {@code RandomSource#nextInt} 会当场抛异常 */
    public BuddingConversionRecipe {
        if (chance < 1) {
            throw new IllegalArgumentException("侵染的概率基数必须 ≥ 1，收到 " + chance);
        }
        replacements = List.copyOf(replacements);
    }

    // ==================== 合成语义：一概不参与 ====================

    @Override
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(RecipeInput input, net.minecraft.core.HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @Override
    public ItemStack getResultItem(net.minecraft.core.HolderLookup.Provider registries) {
        return ItemStack.EMPTY;
    }

    /** 不进配方书、也不参与配方匹配：它的触发器是母岩的随机刻 */
    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.BUDDING_CONVERSION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipes.BUDDING_CONVERSION.get();
    }

    /** 配方的序列化器：只做编解码，逻辑全在引擎里 */
    public static class Serializer implements RecipeSerializer<BuddingConversionRecipe> {

        @Override
        public MapCodec<BuddingConversionRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, BuddingConversionRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
