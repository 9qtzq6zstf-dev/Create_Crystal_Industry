package com.minecart.yunxian.client;

import com.minecart.yunxian.Yunxian;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;

/** 在物品栏/热键栏里用 16x16 平面贴图代替被隐藏的 3D 模型 */
public final class EchoSpyglassGuiDecorator implements IItemDecorator {

    private static final ResourceLocation FLAT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                    "textures/item/echo_spyglass.png");

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack,
                          int xOffset, int yOffset) {
        // 必须用 9 参重载并显式给 16x16:6 参重载会把贴图当 256x256,
        // 导致只采样到左上角一小块。
        graphics.blit(FLAT_TEXTURE, xOffset, yOffset, 0, 0, 16, 16, 16, 16);
        return true;
    }
}