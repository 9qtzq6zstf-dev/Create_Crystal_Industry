package com.minecart.yunxian.budding;

import com.minecart.yunxian.blockentity.budding.ArclightBuddingBlockEntity;
import com.minecart.yunxian.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightningRodBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 「失活弧光石母岩被雷劈中 → 变成弧光石母岩，且 FE 给满」的判定。
 * <p>
 * 调用点只有一个：{@code mixin/LightningBoltMixin} 注入在原版
 * {@code LightningBolt#powerLightningRod} 的开头。挑这个位置的理由是它<b>恰好是「闪电在服务端结算」
 * 的那一瞬间</b>——原版 {@code tick()} 里只有 {@code life == 2} 且非 visualOnly 的服务端分支会走到它，
 * 而它自身又是无条件调用的（里面才判是不是避雷针）。所以一次注入就把两种情况都收了：
 * <ol>
 *   <li><b>闪电直接落在失活母岩上</b>：落点就是它自己；</li>
 *   <li><b>它正上方那格是避雷针、避雷针被劈中</b>：闪电劈的是避雷针，落点是避雷针那格，
 *       失活母岩在它<b>正下方</b>。这里刻意写死 {@code below()} 而不是照抄
 *       {@code clearCopperOnLightningStrike} 的 {@code FACING.getOpposite()}：避雷针要装在母岩顶上
 *       就得贴在它的上表面，那样的避雷针朝向必定是 UP，"反方向"也就是下面这格。用 {@code below()}
 *       把话说死，免得侧面贴着的避雷针也误触发。</li>
 * </ol>
 * 落点由 mixin 通过 {@code @Invoker} 直接调原版的私有 {@code getStrikePosition()} 拿到，
 * 不在这里重算一遍那个 {@code y - 1e-6}——它是原版的实现细节，抄一份出去迟早会对不上。
 * <p>
 * 转化本身只有一句换方块，剩下的两件事都在 {@link #activate} 里：把电灌满，以及补几下随机刻让
 * 新母岩当场冒出几颗小芽（见 {@link #growWelcomeBuds}）——不补的话雷劈完的母岩光秃秃的，
 * 看着不像"活过来了"。
 */
public final class LightningActivation {

    /**
     * 转化时补上的随机刻次数（见 {@link #growWelcomeBuds}）。
     * <p>
     * 弧光石家族是正常速度档（每次随机刻 1/5 概率推进一级），所以 16 下的期望是约 3 颗芽、
     * 一颗都不出的概率约 3%。纯手感值，调大调小都安全。
     */
    private static final int GROWTH_TICKS = 16;

    private LightningActivation() {
    }

    /**
     * 闪电在服务端结算完毕，落点是 {@code strike}。
     *
     * @param level  闪电所在的维度（只有 {@link ServerLevel} 才会真的改方块）
     * @param strike 原版 {@code LightningBolt#getStrikePosition()} 的结果
     */
    public static void onStrikeResolved(Level level, BlockPos strike) {
        if (!(level instanceof ServerLevel server)) {
            return; // 闪电的方块结算本来就只在服务端发生，这里只是兜底
        }
        activate(server, strike);
        if (server.getBlockState(strike).getBlock() instanceof LightningRodBlock) {
            activate(server, strike.below());
        }
    }

    /** 该位置是失活母岩就把它换成真正的弧光石母岩，并一次性把电灌满 */
    private static void activate(ServerLevel level, BlockPos pos) {
        if (!level.getBlockState(pos).is(ModBlocks.INACTIVE_ARCLIGHT_BUDDING.get())) {
            return;
        }

        BlockState activated = BuddingFamilies.ARCLIGHT.budding().get().defaultBlockState();
        level.setBlockAndUpdate(pos, activated);

        // setBlockAndUpdate 会把方块实体一并建出来（原版 LevelChunk#setBlockState 里同步完成），
        // 所以这一句拿得到刚建好的那一个。取不到也不该发生——真发生了说明方块实体的合法方块表
        // 与这里对不上（见 ModBlockEntities.ARCLIGHT_BUDDING），那就只是不满电、也不会冒芽，
        // 不影响转化本身。
        if (level.getBlockEntity(pos) instanceof ArclightBuddingBlockEntity cell) {
            // 顺序不能反：下面那几下随机刻要先付账才肯长，空罐子会全部被放弃
            cell.setEnergyToFull();
            growWelcomeBuds(level, pos, activated);
            // 再灌满一次：这几颗芽是雷劈白送的，不该从"满电"里扣掉
            // （每长一级扣 10 000 FE，不补的话转化结果就不是满电了）
            cell.setEnergyToFull();
        }
    }

    /**
     * 给刚转化的母岩补几下随机刻，让它当场冒出一圈小芽，看着像「本来就长在那儿」。
     * <p>
     * 走的是<b>方块自己的随机刻</b>（{@code BlockState#randomTick} → {@code GenericBuddingBlock#randomTick}
     * → 生长引擎），不是自己调引擎：付费钩子、光照判定、朝向、随机数消耗顺序全都由它自己那份逻辑负责，
     * 这里只是把「时间」快进一下，家族参数怎么改都不用跟着动。
     * <p>
     * {@link #GROWTH_TICKS} 取 16 的依据：弧光石家族是正常速度档（每次随机刻 1/5 概率推进一级），
     * 所以期望长出约 3 颗、一颗都不出的概率约 3%（0.8^16）。这个数只是在"劈一下就有几颗芽"和
     * "别把一整圈都长满"之间取的手感值，想要更热闹或更克制改它一个数就行。
     */
    private static void growWelcomeBuds(ServerLevel level, BlockPos pos, BlockState activated) {
        for (int i = 0; i < GROWTH_TICKS; i++) {
            activated.randomTick(level, pos, level.random);
        }
    }
}
