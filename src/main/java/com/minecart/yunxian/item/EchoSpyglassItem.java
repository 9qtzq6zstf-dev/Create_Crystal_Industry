package com.minecart.yunxian.item;

import com.minecart.yunxian.config.EchoConfig;
import com.minecart.yunxian.network.EchoRevealPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

public class EchoSpyglassItem extends Item {

    private static final int USE_DURATION_TICKS = 1200;   // 60 秒

    public EchoSpyglassItem(Properties properties) {
        super(properties);
    }

    // 只要原版的持镜动作，不继承 SpyglassItem，因此没有缩放
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPYGLASS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        player.playSound(SoundEvents.SPYGLASS_USE, 1.0F, 1.0F);

        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            scanAndSend(level, serverPlayer);   // 立即先扫一次，反馈更快
        }
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(entity instanceof ServerPlayer serverPlayer)) {
            return;
        }

        int usedTicks = USE_DURATION_TICKS - remainingUseDuration;
        int interval = EchoConfig.SCAN_INTERVAL_TICKS.get();
        if (usedTicks > 0 && usedTicks % interval == 0) {
            scanAndSend(level, serverPlayer);
        }
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, EchoRevealPayload.clear(level));
        }
        entity.playSound(SoundEvents.SPYGLASS_STOP_USING, 1.0F, 1.0F);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, EchoRevealPayload.clear(level));
        }
        return stack;
    }

    private void scanAndSend(Level level, ServerPlayer player) {
        if (level instanceof ServerLevel serverLevel) {
            PacketDistributor.sendToPlayer(player,
                    new EchoRevealPayload(serverLevel.dimension(),
                            EchoScanner.findOres(serverLevel, player.blockPosition(), EchoConfig.SCAN_RADIUS.get())));
        }
    }
}