package com.minecart.yunxian.fluid;

import com.minecart.yunxian.registry.ModBlocks;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidInteractionRegistry;

/**
 * 本模组的流体—方块交互。目前只有一条：<b>熔岩 → 深板岩</b>。
 * <p>
 * 写法是<b>逐字照抄原版玄武岩那一条</b>（它就在 {@link FluidInteractionRegistry} 的静态块里），
 * 只换了两个方块：
 * <table>
 *   <tr><th>原版玄武岩</th><th>本模组</th></tr>
 *   <tr><td>熔岩<b>下方</b>是灵魂土</td><td>熔岩下方仍是<b>灵魂土</b>（没换）</td></tr>
 *   <tr><td>熔岩<b>旁边</b>是蓝冰</td><td>熔岩旁边是<b>可燃冰块</b></td></tr>
 *   <tr><td>熔岩变成玄武岩</td><td>熔岩变成<b>深板岩</b></td></tr>
 * </table>
 * 于是「下方灵魂土 + 旁边可燃冰块」的熔岩会变成深板岩，并带上原版的「滋啦」声与模组可拦的
 * {@code fireFluidPlaceBlockEvent} 钩子——这些都由注册表代劳，本类不需要任何自己的逻辑。
 * <p>
 * <b>几点与原版一致的脾气，照抄就得接受</b>：
 * <ul>
 *   <li>注册表只遍历<b>除向下之外</b>的方向，所以"旁边"指侧面或上方；"下方"特指
 *       {@code pos.below()}（灵魂土必须垫在熔岩正下方）。</li>
 *   <li>交互按<b>注册顺序</b>取第一个命中的，原版「熔岩 + 水 → 黑曜石 / 圆石」排在本条之前：
 *       熔岩旁边只要有水，先命中的就是它，本条不会触发（原版玄武岩也是同样的脾气）。</li>
 *   <li>不要求水参与——原版玄武岩那条也不要求（源码里那条的注释与判断都只提灵魂土与蓝冰）。</li>
 * </ul>
 * <b>必须在注册表绑定之后注册</b>，见 {@code Yunxian#commonSetup}。
 */
public final class FluidInteractions {

    private FluidInteractions() {
    }

    public static void register() {
        // 在这里取一次方块实例：判定跑在邻居更新里，没必要每次都查句柄
        Block flammableIce = ModBlocks.FLAMMABLE_ICE_BLOCK.get();
        FluidInteractionRegistry.addInteraction(NeoForgeMod.LAVA_TYPE.value(),
                new FluidInteractionRegistry.InteractionInformation(
                        (level, currentPos, relativePos, currentState) -> level.getBlockState(currentPos.below())
                                .is(Blocks.SOUL_SOIL)
                                && level.getBlockState(relativePos).is(flammableIce),
                        Blocks.DEEPSLATE.defaultBlockState()));
    }
}
