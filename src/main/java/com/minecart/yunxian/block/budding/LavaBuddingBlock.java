package com.minecart.yunxian.block.budding;

import com.minecart.yunxian.blockentity.budding.LavaBuddingBlockEntity;
import com.minecart.yunxian.budding.BuddingFamily;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * 远古残骸母岩的方块：在 {@link GenericBuddingBlock} 之上补两件与「容器」有关的事
 * ——手持流体容器的右键交互，以及比较器的液位信号。
 * <p>
 * 与 {@code EchoConvertingBuddingBlock} 同一个理由走子类：这两件事都要按方块状态写行为，
 * 而家族表（{@code BuddingFamily}）只描述数据，装不下方法实现。
 * <p>
 * <b>潜行时不会交互</b>：原版 {@code ServerPlayerGameMode#useItemOn} 在潜行且手上有非空物品时
 * 整段跳过方块的 {@code useItemOn}。这里刻意<b>不</b>像 {@code CrystalBatteryInteractions}
 * 那样绕开它（那个方块要让人潜行着换晶体，才不得不挂 {@code UseItemOnBlockEvent}）——
 * 拿着熔岩桶潜行本来就不该往容器里灌，潜行时要的动作是"对着母岩放方块"
 * （想往母岩旁边倒岩浆，也是潜行着倒）。
 */
public class LavaBuddingBlock extends GenericBuddingBlock {

    public LavaBuddingBlock(BuddingFamily family, Properties properties,
                            Block smallBud, Block mediumBud, Block largeBud, Block cluster) {
        super(family, properties, smallBud, mediumBud, largeBud, cluster);
    }

    /**
     * 手持流体容器右键：能装就装、能舀就舀，罐体只认熔岩（{@link LavaBuddingBlockEntity} 里写的）。
     * 具体过程与「倒不进去时为什么不能落回原版」都写在 {@link FluidTankInteraction} 里，
     * 与脚本母岩共用同一段。
     * <p>
     * 一桶 = 1000 mB = 罐的满容量，所以<b>桶只在罐空（倒进去）或罐满（舀出来）时管用</b>；
     * 750 / 500 / 250 这些零头得用管道补——桶装不下也装不满半格，这是容器本身的限制。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemInteractionResult tank = FluidTankInteraction.tryUse(stack, level, pos, player, hand, hitResult);
        if (tank != null) {
            return tank;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    /**
     * 比较器只认「自称有模拟输出」的方块：{@code ComparatorBlock#getInputSignal} 先问
     * {@code BlockState#hasAnalogOutputSignal}，为 false 时连 {@link #getAnalogOutputSignal}
     * 都不会调（原版容器方块如熔炉同样要重写这两个）。漏了这一个方法就是"比较器毫无反应"。
     */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * 比较器读液位：空罐 0，其余按比例给 1–15（和原版炼药锅一个写法，空与非空要能区分开）。
     * <p>
     * 液位不是方块状态，所以这里现查方块实体——比较器每 tick 会读一次，读的是服务端的真实值，
     * 不像护目镜那样依赖同步包。
     */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof LavaBuddingBlockEntity tank)) {
            return 0;
        }
        int amount = tank.lavaAmount();
        if (amount <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(15.0F * amount / LavaBuddingBlockEntity.CAPACITY));
    }
}
