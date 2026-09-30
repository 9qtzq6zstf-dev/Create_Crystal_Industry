package com.minecart.yunxian.item;

import com.minecart.yunxian.block.ResonanceTableBlock;
import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.minecart.yunxian.registry.ModDataComponents;
import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 共振过滤器：放进漏斗/溜槽/工作盆的过滤槽后，<b>过滤规则不来自它自己</b>，
 * 而是实时取自它所在的那个「共振台网络」里每张台面上放着的物品，取并集。
 * <p>
 * 接法见 {@link ResonanceFilterItemStack}。本类只负责三件事：
 * <ol>
 *   <li>{@link #makeStackWrapper} —— 把判定交给自定义的 {@code FilterItemStack} 子类
 *       （Create 的 {@code FilterItemStack.of} 会把判定分发到这里，这是唯一的接入口）；</li>
 *   <li>潜行右键共振台：让过滤器接入该台所在的网络（<b>组网本身</b>走
 *       {@code ResonanceTableItem#useOn}，和本类无关）；</li>
 *   <li>把网络 id 写进物品的 DataComponent。</li>
 * </ol>
 * <b>网络 id 必须存在物品自己身上</b>：{@code FilteringBehaviour.read()} 是从 NBT 反序列化出的
 * ItemStack 重新 {@code FilterItemStack.of(...)} 构造判定对象的，方块实体那边不留任何状态，
 * 也正因为如此，蓝图复制、方块被拆掉落、物品栏搬运都会自动带着它走。
 * <p>
 * 措辞一律用 Create 物流网络那套现成的（{@code create.logistically_linked.*}），
 * 包括调频后的附魔光效和清除时的音效 —— <b>不自己造词</b>。频道的 UUID 不向玩家展示。
 */
public class ResonanceFilterItem extends FilterItem {

    public ResonanceFilterItem(Properties properties) {
        super(properties);
    }

    // ==================== 网络 ====================

    public static boolean isTuned(ItemStack stack) {
        return stack.get(ModDataComponents.RESONANCE_NETWORK.get()) != null;
    }

    /** 接入网络后带附魔光效，和 Create 仓储链接站一致 */
    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return isTuned(stack);
    }

    // ==================== FilterItem 的抽象方法 ====================

    @Override
    public FilterItemStack makeStackWrapper(ItemStack filter) {
        return new ResonanceFilterItemStack(filter);
    }

    @Override
    public DataComponentType<?> getComponentType() {
        return ModDataComponents.RESONANCE_NETWORK.get();
    }

    /**
     * 拿不到 Level，所以这里解析不出网络里的台子现在放着什么，只能返回空数组。
     * 唯一调用方是蓝图的需求覆盖层（{@code BlueprintOverlayRenderer}），
     * 表现为「蓝图里不列出共振过滤器当前镜像的物品」——可以接受。
     */
    @Override
    public ItemStack[] getFilterItems(ItemStack stack) {
        return new ItemStack[0];
    }

    /** 右键打开的那个过滤器界面。共振过滤器不用界面，内容全在同屏的共振台上。 */
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        return null;
    }

    /** 未接入网络时不加任何提示行 —— Create 的仓储链接站也是这样，频道的 UUID 不给玩家看 */
    @Override
    public List<Component> makeSummary(ItemStack filter) {
        if (!isTuned(filter))
            return Collections.emptyList();
        return List.of(CreateLang.translateDirect("logistically_linked.tooltip")
                .withStyle(ChatFormatting.GOLD));
    }

    // ==================== 交互 ====================

    /**
     * 潜行右键共振台 = 让过滤器<b>接入</b>这张台子所在的网络（只在过滤器还没接入任何网络时生效）。
     * <p>
     * <b>这里不负责组网</b>：让两张台子进同一个网络是手持「共振台」那件事
     * （见 {@code ResonanceTableItem#useOn}）。过滤器已经接入过网络时，对着别的网络里的台子
     * 点一下不会把它改过去 —— 什么也不做，因为 Create 那边没有"已经调过频"的措辞可用，
     * 与其自己造一句，不如不吭声；物品的附魔光效本身就说明它已经在网里了。
     * <p>
     * 能走到这里是因为原版在「潜行 + 手持物品」时会跳过方块的 {@code useItemOn}
     * （见 {@code Yunxian} 里 {@code CrystalBatteryInteractions} 那条注释），正好把
     * 共振台的普通右键让给「放/取台面上的物品」，接入只能用潜行——两边不打架。
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || !player.isShiftKeyDown())
            return InteractionResult.PASS;

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!(level.getBlockState(pos).getBlock() instanceof ResonanceTableBlock))
            return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof ResonanceTableBlockEntity table))
            return InteractionResult.PASS;

        if (level.isClientSide)
            return InteractionResult.SUCCESS;

        ItemStack stack = context.getItemInHand();
        if (isTuned(stack))
            return InteractionResult.SUCCESS;

        UUID joined = table.getNetwork();
        stack.set(ModDataComponents.RESONANCE_NETWORK.get(), joined);
        // 措辞同 Create：接入一个已经存在的网络
        player.displayClientMessage(CreateLang.translateDirect("logistically_linked.connected"), true);
        return InteractionResult.SUCCESS;
    }

    /**
     * 潜行右键空气 = 退出网络。措辞与音效都照抄 Create 仓储链接站的清除动作。
     * 非潜行或被父类拿走的情况一律交回 {@code super} —— 必须覆盖掉父类实现，
     * 否则 {@code FilterItem#use} 会在这里打开过滤器的容器界面。
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!player.isShiftKeyDown() || hand != InteractionHand.MAIN_HAND || !isTuned(stack))
            return InteractionResultHolder.pass(stack);

        if (level.isClientSide) {
            level.playSound(player, player.blockPosition(), SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                    SoundSource.BLOCKS, 0.75f, 1.0f);
        } else {
            player.displayClientMessage(CreateLang.translateDirect("logistically_linked.cleared"), true);
            stack.remove(ModDataComponents.RESONANCE_NETWORK.get());
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
