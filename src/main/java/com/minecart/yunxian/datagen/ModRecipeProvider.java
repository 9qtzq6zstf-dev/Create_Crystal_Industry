package com.minecart.yunxian.datagen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamily.BlockConversion;
import com.minecart.yunxian.budding.BuddingFamily.Replacement;
import com.minecart.yunxian.recipe.BuddingConversionRecipe;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.common.conditions.ModLoadedCondition;

/**
 * 母岩的侵染配方（{@code budding_conversion}）：家族表里声明一条，这里吐一份 JSON。
 * <p>
 * 声明仍写在 {@code BuddingFamilies} 的家族工厂里——一个家族一处，不散成几十个手写 JSON；
 * 这份 provider 只负责把 {@link BuddingFamilies#declaredConversions()} 翻成配方。
 * 配方 id 取 {@code budding_conversion/<母岩方块>/<输入>_to_<输出>}，整合包照着同一个 id
 * 覆盖即可改掉任意一条。
 * <p>
 * 注意本 provider 与 {@code LootTableProvider} 一样会接管自己的输出目录
 * （{@code src/generated/resources/data/<modid>/recipe/} 与 {@code …/advancement/}）：
 * 以后再往那两个目录手放 JSON 会被当成过期文件删掉。
 */
public class ModRecipeProvider extends RecipeProvider {

    public ModRecipeProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries);
    }

    @Override
    protected void buildRecipes(RecipeOutput output) {
        BuddingFamilies.declaredConversions().forEach((buddingId, conversions) -> {
            Block budding = BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, buddingId));
            if (budding == Blocks.AIR) {
                throw new IllegalStateException("家族表声明的母岩方块不存在：" + buddingId
                        + "——方块 id 与 BuddingFamilies 里那条家族对不上了");
            }

            for (BlockConversion conversion : conversions) {
                List<BuddingConversionRecipe.Replacement> replacements = new ArrayList<>(conversion.replacements().size());
                for (Replacement replacement : conversion.replacements()) {
                    replacements.add(toRecipeReplacement(replacement));
                }
                BuddingConversionRecipe recipe = new BuddingConversionRecipe(budding,
                        conversion.chance(), conversion.radius(), conversion.energyGated(), replacements);
                output.withConditions(conditionsFor(recipe)).accept(recipeId(buddingId, conversion), recipe, null);
            }
        });
    }

    /**
     * 配方引用了别的模组的方块时补一条 {@code mod_loaded} 条件。
     * <p>
     * 少了它，没装那个模组的玩家每次加载配方都会看到一条 {@code Parsing error loading recipe …}：
     * 方块 id 不存在，整条配方解码失败（福鲁伊克斯那条就是——它的输入是 {@code ae2:fluix_block}）。
     * 本模组自己的方块与原版方块不需要条件，所以只给别的命名空间加。
     */
    private static ICondition[] conditionsFor(BuddingConversionRecipe recipe) {
        Set<String> mods = new LinkedHashSet<>();
        for (BuddingConversionRecipe.Replacement replacement : recipe.replacements()) {
            for (Holder<Block> holder : replacement.input()) {
                holder.unwrapKey().ifPresent(key -> noteForeignMod(mods, key.location()));
            }
            replacement.output().ifPresent(block -> noteForeignMod(mods, BuiltInRegistries.BLOCK.getKey(block)));
        }
        return mods.stream().map(ModLoadedCondition::new).toArray(ICondition[]::new);
    }

    private static void noteForeignMod(Set<String> mods, ResourceLocation id) {
        String namespace = id.getNamespace();
        if (!namespace.equals("minecraft") && !namespace.equals(Yunxian.MODID)) {
            mods.add(namespace);
        }
    }

    /**
     * 声明里的输入是"具体方块 或 方块标签"二选一，配方里统一成 {@link HolderSet}：
     * 标签写成 {@code #标签}（{@code RegistryCodecs.homogeneousList} 认得），具体方块写成它的持有者。
     * 产物为空表示"变成本母岩自身"，原样带过去。
     */
    private static BuddingConversionRecipe.Replacement toRecipeReplacement(Replacement replacement) {
        HolderSet<Block> input = replacement.inputTag() != null
                ? BuiltInRegistries.BLOCK.getOrCreateTag(replacement.inputTag())
                : HolderSet.direct(BuiltInRegistries.BLOCK.wrapAsHolder(replacement.input().get()));
        Optional<Block> output = replacement.output() == null
                ? Optional.empty()
                : Optional.of(replacement.output().get());
        return new BuddingConversionRecipe.Replacement(input, output);
    }

    /** 配方 id 用第一条替换来描述这一条规则：{@code budding_conversion/<母岩>/<输入>_to_<输出>} */
    private static ResourceLocation recipeId(String buddingId, BlockConversion conversion) {
        Replacement first = conversion.replacements().get(0);
        return ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                "budding_conversion/" + buddingId + "/" + inputKey(first) + "_to_" + outputKey(first));
    }

    private static String inputKey(Replacement replacement) {
        return replacement.inputTag() != null
                ? replacement.inputTag().location().getPath()
                : blockPath(replacement.input().get());
    }

    /** 产物为空 = 变成本母岩自身 */
    private static String outputKey(Replacement replacement) {
        return replacement.output() == null ? "self" : blockPath(replacement.output().get());
    }

    private static String blockPath(Block block) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        return id == null ? "unknown" : id.getPath();
    }
}
