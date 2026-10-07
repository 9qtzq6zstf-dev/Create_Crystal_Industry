package com.minecart.yunxian.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

/**
 * 带流体罐的方块（母岩流体罐：远古残骸母岩、写了 {@code needfluid} 的脚本母岩；以及智能温控室）
 * 共用的「手持容器右键」处理。
 * <p>
 * 各家的 {@code useItemOn} 都只是转发到这里，放在一处的理由是<b>后半段那件事每边都得做</b>：
 * {@link FluidUtil#interactWithFluidHandler} 失败之后不能就这么落回原版默认行为。
 * <p>
 * 原版在方块交互没有消费动作时会继续跑手持物品的 {@code useOn}
 * （{@code ServerPlayerGameMode#useItemOn} 里方块那一段之后紧跟着 {@code stack.useOn(useoncontext)}，
 * 而且它<b>不看</b>方块返回的是 {@code FAIL} 还是 {@code PASS}），于是桶里的液体会被倒在方块旁边：
 * <ul>
 *   <li>拿的液体和罐子要的不一样（往熔岩罐上倒水）；</li>
 *   <li>罐已经满了（一桶正好灌满 1 B 的罐，所以第二次右键必然撞上这一条）。</li>
 * </ul>
 * 这两种情况下玩家想要的是「倒不进去」，不是「倒一地」。所以这里把这一下自己吞掉
 * （返回 {@link ItemInteractionResult#SUCCESS}，等于"这个交互我处理了，别再往下走了"）。
 * <p>
 * 只吞<b>流体容器</b>：手里拿的是别的物品（方块、食物、工具……）照常落回默认行为，
 * 往方块上放东西、开界面都不受影响。潜行时原版本来就会跳过方块交互
 * （{@code ServerPlayerGameMode#useItemOn} 里的 {@code flag1}），所以真想往方块旁边倒液体，
 * 潜行着倒就行——不会把人堵死。
 * <p>
 * 原本是 {@code block.budding} 包里的包级私有类，智能温控室也要同一段逻辑，就挪到
 * {@code block} 包并放开成 {@code public}（跨包引用，各调用方补一条 import）。
 */
public final class FluidTankInteraction {

    private FluidTankInteraction() {
    }

    /**
     * 试一次「手持容器 ↔ 罐子」的交互。
     *
     * @return 已经处理完的交互结果；{@code null} = 手上不是流体容器，调用方该走自己的默认行为
     */
    @Nullable
    public static ItemInteractionResult tryUse(ItemStack stack, Level level, BlockPos pos, Player player,
                                               InteractionHand hand, BlockHitResult hitResult) {
        if (stack.isEmpty()) {
            return null;
        }
        // 能装就装、能舀就舀：FluidUtil 先试把罐里的装进手里的容器，再试把容器里的倒进罐里，
        // 并负责替换容器、把换出来的空桶/满桶塞回背包（背包满了就掉在脚下）
        if (FluidUtil.interactWithFluidHandler(player, hand, level, pos, hitResult.getDirection())) {
            return ItemInteractionResult.SUCCESS;
        }
        // 手里是流体容器，但这一下什么都没成（液体不对、或者罐满/罐空）：吞掉，理由见类注释
        return FluidUtil.getFluidHandler(stack).isPresent() ? ItemInteractionResult.SUCCESS : null;
    }
}
