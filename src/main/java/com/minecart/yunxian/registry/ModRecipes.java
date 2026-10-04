package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.recipe.BuddingConversionRecipe;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * 本模组的配方类型与序列化器。
 * <p>
 * 目前只有一种：{@code budding_conversion}——母岩的「侵染」（把相邻方块 A 变成 B）。
 * 它既是数据包配方（JSON 可写、可被整合包覆盖），也是运行时规则（引擎按母岩方块取用），
 * 见 {@link BuddingConversionRecipe}。
 * <p>
 * <b>注册方式</b>：必须走 {@code DeferredRegister}——{@code RecipeType.register(String)} /
 * {@code RecipeSerializer.register(String, ...)} 内部用的是 {@code withDefaultNamespace}，
 * 会把类型注册到 {@code minecraft:} 命名空间下，等于替原版占了名字。
 */
public final class ModRecipes {

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES =
            DeferredRegister.create(Registries.RECIPE_TYPE, Yunxian.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Yunxian.MODID);

    /** 母岩侵染：随机刻里把相邻方块 A 变成 B */
    public static final DeferredHolder<RecipeType<?>, RecipeType<BuddingConversionRecipe>> BUDDING_CONVERSION =
            RECIPE_TYPES.register("budding_conversion", () -> RecipeType.simple(
                    ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "budding_conversion")));

    public static final DeferredHolder<RecipeSerializer<?>, BuddingConversionRecipe.Serializer>
            BUDDING_CONVERSION_SERIALIZER =
            RECIPE_SERIALIZERS.register("budding_conversion", BuddingConversionRecipe.Serializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus modEventBus) {
        RECIPE_TYPES.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
    }
}
