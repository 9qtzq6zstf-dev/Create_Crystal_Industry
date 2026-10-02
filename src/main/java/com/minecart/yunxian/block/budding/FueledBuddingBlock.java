package com.minecart.yunxian.block.budding;

import com.minecart.yunxian.budding.BuddingFamily;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * 烧流体的母岩：比普通母岩多一个 {@link FluidTankBudding#FUELED} 方块状态，
 * 罐里的流体不够一次生长时置为 {@code false}，客户端据此换成 {@code _unpowered} 那套静态贴图
 * （够的时候用带动画的那套，外观差异见 datagen 的 {@code ModBlockStateProvider}）。
 * <p>
 * 之所以要单独一个子类，而不是把这一位直接加在 {@link GenericBuddingBlock} 上：
 * <ul>
 *   <li>方块状态只能在 {@code createBlockStateDefinition} 里注册，而它由 {@code Block} 的构造器
 *       在 {@code super(...)} 里调用，早于子类的字段赋值——读不到家族表，没法"按家族决定加不加"
 *       （与 {@link EchoConvertingBuddingBlock} 的 {@code CAN_SUMMON} 同一条理由）；</li>
 *   <li>加在基类上会让其余十几个家族的 blockstate、模型与 <b>{@code defaultBlockState()} 的语义</b>
 *       全都变一遍，而 {@code defaultBlockState()} 还被"远古残骸 → 自身"的再生传播、ponder 场景、
 *       JEI 渲染共用。只给吃流体的家族加，其余家族一个字节都不动。</li>
 * </ul>
 * <p>
 * <b>边界</b>：用哪个类由家族表的 {@code Growth.fluid()} 决定，也就是<b>出厂设置</b>。
 * 脚本用 {@code CustomBudding.modify} 后来给某块母岩加上的流体需求不会让它长出这一位
 * （那样也没有对应的贴图）——指示器只服务家族表里声明的罐。
 */
public class FueledBuddingBlock extends GenericBuddingBlock {

    public FueledBuddingBlock(BuddingFamily family, Properties properties,
                              Block smallBud, Block mediumBud, Block largeBud, Block cluster) {
        super(family, properties, smallBud, mediumBud, largeBud, cluster);
        // 默认必须是"燃料不足"：方块放下时罐是空的，世界生成、脚本放置与
        // 「远古残骸 → 自身」的再生传播走的都是默认状态。
        // 注意 BooleanProperty 的隐含默认是 true（ImmutableSet.of(true, false) 的顺序），
        // 不能省这一句——省了就是"空罐却显示有燃料"，而且要等到罐体真的变化才会被纠正。
        this.registerDefaultState(this.stateDefinition.any().setValue(FUELED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FUELED);
        super.createBlockStateDefinition(builder);
    }
}
