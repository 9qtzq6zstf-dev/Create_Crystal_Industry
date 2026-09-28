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
import net.neoforged.neoforge.fluids.FluidUtil;

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
 * 拿着熔岩桶潜行本来就不该往容器里灌，潜行时要的动作是"对着母岩放方块"。
 */
public class LavaBuddingBlock extends GenericBuddingBlock {

    public LavaBuddingBlock(BuddingFamily family, Properties properties,
                            Block smallBud, Block mediumBud, Block largeBud, Block cluster) {
        super(family, properties, smallBud, mediumBud, largeBud, cluster);
    }

    /**
     * 手持流体容器右键：先试着把罐里的熔岩装进手里的容器（空桶舀出来），
     * 装不进去再试着把容器里的倒进罐里（熔岩桶灌进去）。
     * <p>
     * {@code FluidUtil} 这个入口同时管了容器的替换与"多出来的容器塞回背包"，
     * 而且只认罐体自己声明能收的流体（{@link LavaBuddingBlockEntity} 只认熔岩），
     * 所以手上拿别的东西时它自然返回 false，控制权落回方块的默认行为。
     * <p>
     * 一桶 = 1000 mB = 罐的满容量，所以<b>桶只在罐空（倒进去）或罐满（舀出来）时管用</b>；
     * 750 / 500 / 250 这些零头得用管道补——桶装不下也装不满半格，这是容器本身的限制。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection())) {
            return ItemInteractionResult.SUCCESS;
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
