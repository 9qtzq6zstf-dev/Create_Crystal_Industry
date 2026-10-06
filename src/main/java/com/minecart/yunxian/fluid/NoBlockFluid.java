package com.minecart.yunxian.fluid;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * 「在世界里不存在」的流体：储罐、管道、注液器都认它，但谁也倒不出来。
 * <p>
 * 照抄 Create 的 {@code VirtualFluid}（它的茶就是这么做的）——三处 override 合起来达成这件事：
 * <ul>
 *   <li>{@link #createLegacyBlock} 返回空气：任何尝试把它变成方块的路径都落空；</li>
 *   <li>{@link #getAmount} 返回 0：没有量，也就谈不上流动与铺开；</li>
 *   <li>{@link #isSource} 按构造时那个标志返回。</li>
 * </ul>
 * 于是 {@code BaseFlowingFluid.Properties} 里的 {@code .block(...)} 可以整个不设
 * （那个字段本来就是可空的）。
 * <p>
 * <b>与 Create 那版唯一的区别是桶</b>：它的 {@code getBucket()} 返回 {@code Items.AIR}（茶不能装桶），
 * 我们则老老实实走 {@code Properties#bucket(...)}——因为 {@code FluidType#getBucket} 是从那儿取的，
 * 而 Create 判断「这个桶能不能装这个流体」正是问 {@code getBucket}（见 {@code FluidBucketWrapper}）。
 * 桶拿不到，管道与注液器就灌不进去。
 */
public class NoBlockFluid extends BaseFlowingFluid {

    public static NoBlockFluid createSource(Properties properties) {
        return new NoBlockFluid(properties, true);
    }

    public static NoBlockFluid createFlowing(Properties properties) {
        return new NoBlockFluid(properties, false);
    }

    private final boolean source;

    protected NoBlockFluid(Properties properties, boolean source) {
        super(properties);
        this.source = source;
    }

    @Override
    public Fluid getSource() {
        return source ? this : super.getSource();
    }

    @Override
    public Fluid getFlowing() {
        return source ? super.getFlowing() : this;
    }

    @Override
    protected BlockState createLegacyBlock(FluidState state) {
        return Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean isSource(FluidState state) {
        return source;
    }

    @Override
    public int getAmount(FluidState state) {
        return 0;
    }
}
