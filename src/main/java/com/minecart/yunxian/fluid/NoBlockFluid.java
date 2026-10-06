package com.minecart.yunxian.fluid;

import java.util.function.Supplier;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/**
 * 「在世界里不流动」的流体：储罐、管道、注液器都认它，但它自己永远不会淌开、也从来不是一格液体。
 * <p>
 * 照抄 Create 的 {@code VirtualFluid}（它的茶就是这么做的）——两处 override 合起来达成这件事：
 * <ul>
 *   <li>{@link #getAmount} 返回 0：没有量，也就谈不上流动与铺开；</li>
 *   <li>{@link #isSource} 按构造时那个标志返回。</li>
 * </ul>
 * 于是 {@code BaseFlowingFluid.Properties} 里的 {@code .block(...)} 可以整个不设
 * （那个字段本来就是可空的），世界上也就不存在这种流体的 {@code FluidState}。
 * <p>
 * <b>与 Create 那版唯一的区别是桶</b>：它的 {@code getBucket()} 返回 {@code Items.AIR}（茶不能装桶），
 * 我们则老老实实走 {@code Properties#bucket(...)}——因为 {@code FluidType#getBucket} 是从那儿取的，
 * 而 Create 判断「这个桶能不能装这个流体」正是问 {@code getBucket}（见 {@code FluidBucketWrapper}）。
 * 桶拿不到，管道与注液器就灌不进去。
 * <p>
 * <b>{@link #createLegacyBlock} 是第二处区别，而且它不是「世界形态」那条线索上的</b>：
 * 这个方法回答的是「这个流体<b>该变成哪个方块</b>」，不是「它自己在世界里长什么样」——
 * 它没有世界形态这件事已经由上面两处 override 保证了。Create 正是把它当成前者用的：
 * {@code FluidHelper.hasBlockState(fluid)} 的实现就是
 * {@code fluid.defaultFluidState().createLegacyBlock() != 空气}，而这个布尔值决定了
 * <b>管道允不允许把这种流体倒进世界里</b>：
 * <ul>
 *   <li>{@code OpenEndedPipe#provideFluidToSpace} 见到 false 就<b>直接 return true</b>
 *       ——管道以为自己倒成功了，其实什么都没放；</li>
 *   <li>{@code OpenEndedFluidHandler#fill} 见到 false 就<b>每次调用都清空内部缓冲</b>
 *       （正常流体是攒满 1000 mB 才倒一次），于是流体被凭空销毁；</li>
 *   <li>{@code HosePulleyFluidHandler#fill} 见到 false 干脆<b>拒收</b>。</li>
 * </ul>
 * 所以这里返回构造时给的 {@code worldBlock}（可燃冰沙给的是可燃冰沙方块）：布尔值翻成 true，
 * Create 走回它原本那条路——攒满 1000 mB，再 {@code setBlock(..., createLegacyBlock(), ...)}。
 * 这也<b>不算撒谎</b>：这种流体被倒出来时确实会变成那个方块，只是那条路由桶自己走
 * （见 {@code FlammableIceSlurryBucketItem}），不经 {@code LiquidBlock}。
 */
public class NoBlockFluid extends BaseFlowingFluid {

    /**
     * @param worldBlock 这种流体「该变成的方块」。传 {@code Supplier} 而不是方块实例，因为
     *                   {@code minecraft:block} 的注册晚于 {@code minecraft:fluid}
     *                   （见 {@code ModFluids} 的类注释）；真正取值的时机是运行时，那时早已注册完毕。
     */
    public static NoBlockFluid createSource(Properties properties, Supplier<BlockState> worldBlock) {
        return new NoBlockFluid(properties, true, worldBlock);
    }

    public static NoBlockFluid createFlowing(Properties properties, Supplier<BlockState> worldBlock) {
        return new NoBlockFluid(properties, false, worldBlock);
    }

    private final boolean source;
    private final Supplier<BlockState> worldBlock;

    protected NoBlockFluid(Properties properties, boolean source, Supplier<BlockState> worldBlock) {
        super(properties);
        this.source = source;
        this.worldBlock = worldBlock;
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
        return worldBlock.get();
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
