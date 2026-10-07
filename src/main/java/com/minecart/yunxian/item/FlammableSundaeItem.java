package com.minecart.yunxian.item;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

import com.minecart.yunxian.registry.ModEffects;

/**
 * 可燃冰圣代的物品：一手是饮品，一手是摆件。
 * <p>
 * <b>只有潜行才放得下去</b>：不潜行时 {@link #place} 直接回 PASS，于是那次右键会退化成
 * 「对着空气用物品」→ 开喝（{@code MultiPlayerGameMode#startUseItem}／原版
 * {@code ServerPlayerGameMode} 在方块交互返回 PASS 后都会回落到 {@code use}）。
 * 换句话说，手里拿着圣代对着地上的圣代右键 = 取回（走方块那侧的
 * {@link com.minecart.yunxian.block.FlammableSundaeBlock#useItemOn}），
 * 潜行右键 = 再放一个，两个动作由原版的潜行分流天然分开。
 * <p>
 * 喝完拿到空瓶靠的是 {@code FoodProperties#usingConvertsTo}，不是自己写 {@code finishUsingItem}：
 * 原版 {@code Player#eat} 已经包办了「喝空的那一份变成瓶子 / 没喝空的往背包塞一个瓶子、
 * 塞不下就丢地上」以及创造模式不消耗，见 {@code Player#eat}。
 * <p>
 * {@code alwaysEdible} 是刻意的：它是件玩具，吃饱了也得能喝下去。喝下去拿到三样东西：
 * <ul>
 *   <li>「灼寒」（{@link ModEffects#SCORCHING_COLD}，10 秒）：满冻外观与冻伤共用同一个阈值，
 *       那笔冻伤由那边拦掉；同时又持续着火，所谓「冻着的冰在烧」。
 *       这就是顶上那撮熔岩点缀下肚的代价——<b>不再另给抗火了</b>，抗火会把这份灼烧
 *       整个免疫掉，那就等于白加点缀；</li>
 *   <li>「冰霜行者」（{@link ModEffects#FROST_WALKER}，10 秒）：脚下那片水面被踩成冰，
 *       与灼寒同长——一边冻着水、一边烧着人，十秒后一起结束；</li>
 *   <li>「火焰吐息」（{@link ModEffects#FLAME_BREATH}，60 秒）：能喷火的许可证。</li>
 * </ul>
 */
public class FlammableSundaeItem extends BlockItem {

    /**
     * 「灼寒」的持续时间。冻透要 7 秒（见 {@link com.minecart.yunxian.effect.FrozenEffect} 里的爬升速率），
     * 所以 10 秒 = 冻透后还能满冻 3 秒；效果结束后靠原版的每 tick -2 自然回暖（约 3.5 秒）。
     * <p>
     * 烧也烧这么久：{@link com.minecart.yunxian.effect.ScorchingColdEffect} 按原版速率
     * 每 20 tick 扣 1 点，这一口下去合计 10 点（5 颗心）。
     */
    private static final int SCORCHING_COLD_DURATION_TICKS = 200;

    /**
     * 「火焰吐息」的持续时间：60 秒。
     * <p>
     * 比灼寒那 10 秒长得多，是刻意的：灼寒是这一口下去的代价，能喷火才是这件玩具的正题。
     * 两个时长从此各走各的（见 {@link ModEffects#FLAME_BREATH}）——冻完的 50 秒里，
     * 玩家按住潜行键照样能喷火（见 {@link com.minecart.yunxian.effect.FlameBreath}）。
     */
    private static final int FLAME_BREATH_TICKS = 1200;

    /**
     * 「冰霜行者」的持续时间：与「灼寒」同为 10 秒。
     * <p>
     * 这一口冻的是脚下那片水——喝完这十秒里，河面能踩着冰走过去；灼寒那笔代价也是十秒，
     * 两件事一起开始、一起结束。要单独调就动这一个数（可燃冰沙瓶那边有各自的一份）。
     */
    private static final int FROST_WALKER_TICKS = 200;

    public FlammableSundaeItem(Block block, Properties properties) {
        super(block, properties
                .stacksTo(16)
                .craftRemainder(Items.GLASS_BOTTLE)
                .food(new FoodProperties.Builder()
                        .nutrition(5)
                        .saturationModifier(0.4F)
                        // 吃饱了也照样吃：这是件玩具/陷阱，不是口粮
                        .alwaysEdible()
                        .usingConvertsTo(Items.GLASS_BOTTLE)
                        .effect(() -> new MobEffectInstance(ModEffects.SCORCHING_COLD, SCORCHING_COLD_DURATION_TICKS), 1.0F)
                        .effect(() -> new MobEffectInstance(ModEffects.FLAME_BREATH, FLAME_BREATH_TICKS), 1.0F)
                        .effect(() -> new MobEffectInstance(ModEffects.FROST_WALKER, FROST_WALKER_TICKS), 1.0F)
                        .build()));
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Player player = context.getPlayer();
        // 玩家为 null 时是发射器/其它非玩家放置，照放不误
        if (player != null && !player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        return super.place(context);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.DRINK;
    }

    @Override
    public SoundEvent getDrinkingSound() {
        return SoundEvents.HONEY_DRINK;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.HONEY_DRINK;
    }
}
