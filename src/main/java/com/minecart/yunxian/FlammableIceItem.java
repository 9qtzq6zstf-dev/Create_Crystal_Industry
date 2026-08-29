package com.minecart.yunxian;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;

import javax.annotation.Nullable;

public class FlammableIceItem extends Item {
    public FlammableIceItem(Properties properties) {
        super(properties);
    }

    // NeoForge 燃料钩子：返回烧炼时间（tick）。200 = 1 秒
    @Override
    public int getBurnTime(ItemStack stack, @Nullable RecipeType<?> recipeType) {
        return 400;
    }
}