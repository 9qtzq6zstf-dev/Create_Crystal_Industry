package com.minecart.yunxian.item;

import com.minecart.yunxian.block.ResonanceTableBlock;
import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.minecart.yunxian.registry.ModDataComponents;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 共振台的方块物品。<b>组网就是靠它</b>：手持共振台潜行右键一个已有的共振台，
 * 手上的这个物品就记住那张台子所在的网络；之后把它放到别处，新台子会自动进同一个网络。
 * <p>
 * 这样"组网"这件事完全不经过共振过滤器 —— 过滤器只负责"接入"某个网络（见
 * {@code ResonanceFilterItem#useOn}），两件事分开。
 * <p>
 * 「记住网络」存在物品自己的 {@code RESONANCE_NETWORK} 组件上；放置时由
 * {@link #updateCustomBlockEntityTag} 灌进新方块实体 —— 这是原版 {@code BlockItem.place}
 * 里正好的那个钩子，时机在方块实体已经建好、其它初始化还没走完的时候。
 * <p>
 * 行为与措辞都对着 Create 的 {@code LogisticallyLinkedBlockItem}（仓储链接站）来：调频用
 * {@code logistically_linked.tuned}、放置后按原本有没有网络用 {@code .connected} /
 * {@code .new_network_started}、清除用 {@code .cleared}、提示行用 {@code .tooltip}，
 * 连"调频后带附魔光效"和清除的音效都照搬 —— <b>不自己造词</b>。
 * 唯一的差别是手势反过来：Create 是普通右键调频、潜行照常放置；我们这边普通右键被
 * 共振台自己的"放/取台面物品"占着，所以调频只能放潜行。
 */
public class ResonanceTableItem extends BlockItem {

    public ResonanceTableItem(Block block, Properties properties) {
        super(block, properties);
    }

    // ==================== 网络 ====================

    @Nullable
    public static UUID networkFromStack(ItemStack stack) {
        return stack.get(ModDataComponents.RESONANCE_NETWORK.get());
    }

    public static boolean isTuned(ItemStack stack) {
        return networkFromStack(stack) != null;
    }

    /** 调频后带附魔光效，和 Create 仓储链接站一致 */
    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return isTuned(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        // 没记网络时不加任何提示行 —— Create 的仓储链接站也是这样
        if (!isTuned(stack))
            return;
        CreateLang.translate("logistically_linked.tooltip")
                .style(ChatFormatting.GOLD)
                .addTo(tooltip);
    }

    // ==================== 交互 ====================

    /**
     * 潜行右键已有的共振台 = 让手上这个物品记住该台的网络；非潜行 = 照常放置。
     * <p>
     * 潜行时原版会跳过方块的 {@code useItemOn}，所以不会走到共振台"放/取台面物品"那套，
     * 也不会顺手把方块放下去。
     * <p>
     * 这里是<b>单向</b>的：只让手上的物品记住，不会去改被点那张台子的网络。
     * 想把一张已经放下去的台子换到别的网络，得先挖掉、给物品印上目标网络再重新放。
     */
    @Override
    public @NotNull InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null)
            return InteractionResult.FAIL;
        if (!player.isShiftKeyDown())
            return placeAndReport(context);

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof ResonanceTableBlock))
            return placeAndReport(context);
        if (!(level.getBlockEntity(pos) instanceof ResonanceTableBlockEntity table))
            return InteractionResult.PASS;
        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        assignFrequency(context.getItemInHand(), player, table.getNetwork());
        return InteractionResult.SUCCESS;
    }

    /**
     * 放置并在成功后报一句 —— 措辞和时机都照抄 Create 的 {@code LogisticallyLinkedBlockItem}：
     * 原本就带着网络的，放下之后是"已成功连接到现有的网络"；原本没有的，是"成功新建了一个物流网络"。
     */
    private InteractionResult placeAndReport(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        boolean tuned = player != null && isTuned(context.getItemInHand());

        InteractionResult result = super.useOn(context);

        // 只在服务端、且真的放下了才报。客户端那一半也走这里，不挡掉会本地再报一遍
        // （Create 那边是靠 level.isClientSide 挡的）；consumesAction 比 Create 的
        // "!= FAIL" 严一点，能挡掉"点在不该放的地方、什么都没放下却报了新建网络"。
        if (player == null || level.isClientSide || !result.consumesAction())
            return result;

        // 「已连接到现有网络」直接用 Create 的键；「新建网络」那句不能用它的 ——
        // Create 的中文是"成功新建了一个物流网络"，我们这边没有物流，得说"过滤网络"。
        // （它的英文原文 "New link network started" 里没有那个词，所以两种语言里只有中文需要换。）
        player.displayClientMessage(tuned
                ? CreateLang.translateDirect("logistically_linked.connected")
                : Component.translatable("message.create_crystal_industry.resonance_table.new_network_started"),
                true);
        return result;
    }

    /**
     * 潜行右键空气 = 清除网络（Create 的仓储链接站在普通右键空气时做同样的事，
     * 连提示语和音效都一样）。非潜行右键空气不做任何事。
     */
    @Override
    public @NotNull InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND || !isTuned(stack))
            return super.use(level, player, hand);

        if (level.isClientSide) {
            level.playSound(player, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.75f, 1.0f);
        } else {
            player.displayClientMessage(CreateLang.translateDirect("logistically_linked.cleared"), true);
            stack.remove(ModDataComponents.RESONANCE_NETWORK.get());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** 把网络 id 写进物品，并报一句 —— 措辞同 Create 的 {@code assignFrequency} */
    private static void assignFrequency(ItemStack stack, Player player, UUID frequency) {
        stack.set(ModDataComponents.RESONANCE_NETWORK.get(), frequency);
        player.displayClientMessage(CreateLang.translateDirect("logistically_linked.tuned"), true);
    }

    /**
     * 放置时把物品上记着的网络灌进新台子。
     * <p>
     * 原版 {@code BlockItem.place} 会无条件调这个钩子（双端都调），且调用时方块实体已经存在、
     * 位置就是 {@code pos}，正是我们能安全碰它的最早时刻。客户端那一半必须跳过。
     * <p>
     * 物品上没记网络时不做事 —— 新台子会保留它构造时拿到的随机 id，也就是自成一张新网络。
     */
    @Override
    protected boolean updateCustomBlockEntityTag(BlockPos pos, Level level, Player player,
                                                 ItemStack stack, BlockState state) {
        boolean changed = super.updateCustomBlockEntityTag(pos, level, player, stack, state);

        UUID network = networkFromStack(stack);
        if (network == null || level.isClientSide)
            return changed;
        if (!(level.getBlockEntity(pos) instanceof ResonanceTableBlockEntity table))
            return changed;

        table.joinNetwork(network);
        return true;
    }
}
