package com.minecart.yunxian.client;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.registry.ModFluids;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

/**
 * 流体的客户端外观：静态/流动贴图。
 * <p>
 * 整个类都只在客户端加载（{@code Dist.CLIENT} 的订阅者），共用侧的 {@link ModFluids}
 * 不引用它——一旦反向引用，专用服务端就会在类校验时去加载客户端的
 * {@code IClientFluidTypeExtensions} 而崩掉（同一套规矩见 {@code ModRenderers}）。
 * <p>
 * 贴图放在 {@code block/arclight/current_slurry/}：电流浆没有自己的方块家族，它是弧光石
 * 这条链的产物，图标与贴图跟着弧光石走更好找（弧光石母岩自己的贴图在
 * {@code block/budding/arclight/}）。
 * <p>
 * 这里<b>不写</b> {@code getTintColor()}：默认是纯白，也就是贴图什么颜色就渲染成什么颜色。
 * 想改成「一张灰度贴图 + 代码染色」，把 {@code getTintColor()} 重写成想要的 ARGB 即可。
 */
@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class ModFluidExtensions {

    private static final ResourceLocation CURRENT_SLURRY_STILL =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/arclight/current_slurry/still");
    private static final ResourceLocation CURRENT_SLURRY_FLOW =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/arclight/current_slurry/flow");

    /**
     * 可燃冰沙的流体贴图：<b>直接复用方块那一张</b>。
     * <p>
     * 它和方块本来就是同一种东西——世界里没有流体形态，这张图只会出现在储罐的液面与桶的图标上，
     * 另画一张纯属多余。静态与流动两个槽位填同一个位置：这张图不动，淌起来也不会看出区别。
     */
    private static final ResourceLocation FLAMMABLE_ICE_SLURRY_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/flammable_ice_slurry/block");

    private ModFluidExtensions() {
    }

    @SubscribeEvent
    public static void registerClientExtensions(RegisterClientExtensionsEvent event) {
        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return CURRENT_SLURRY_STILL;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return CURRENT_SLURRY_FLOW;
            }
        }, ModFluids.CURRENT_SLURRY_TYPE.get());

        event.registerFluidType(new IClientFluidTypeExtensions() {
            @Override
            public ResourceLocation getStillTexture() {
                return FLAMMABLE_ICE_SLURRY_TEXTURE;
            }

            @Override
            public ResourceLocation getFlowingTexture() {
                return FLAMMABLE_ICE_SLURRY_TEXTURE;
            }
        }, ModFluids.FLAMMABLE_ICE_SLURRY_TYPE.get());
    }
}
