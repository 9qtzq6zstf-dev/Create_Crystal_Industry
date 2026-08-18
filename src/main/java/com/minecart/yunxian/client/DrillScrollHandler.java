package com.minecart.yunxian.client;

import com.minecart.yunxian.SilkTouchModePacket;
import com.minecart.yunxian.SmartDrillBlock;
import com.simibubi.create.AllItems;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = "create_crystal_industry", value = Dist.CLIENT)
public class DrillScrollHandler {

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        ItemStack mainHand = player.getMainHandItem();
        // 修正：使用 ItemStack.is() 方法检查是否为扳手
        if (!mainHand.is(AllItems.WRENCH)) return;

        if (!(mc.hitResult instanceof BlockHitResult hitResult)) return;
        BlockPos pos = hitResult.getBlockPos();
        Level level = player.level();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SmartDrillBlock)) return;

        // 取消默认滚轮行为（防止切换快捷栏物品）
        event.setCanceled(true);

        boolean currentSilk = state.getValue(SmartDrillBlock.SILK_TOUCH);
        boolean newSilk = !currentSilk;

        PacketDistributor.sendToServer(new SilkTouchModePacket(pos, newSilk));

        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.literal("钻头模式: " + (newSilk ? "精准采集" : "普通")),
                    true // 显示在动作栏
            );
        }
    }
}