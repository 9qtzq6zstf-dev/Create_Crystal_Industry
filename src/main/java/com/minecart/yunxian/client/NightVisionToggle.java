package com.minecart.yunxian.client;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.item.NightVisionGogglesItem;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class NightVisionToggle {
    private static boolean enabled = false;

    private NightVisionToggle() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // 按键：只有佩戴时才切换开关
        while (ModKeybinds.TOGGLE_NIGHT_VISION.consumeClick()) {
            boolean wearing = player.getItemBySlot(EquipmentSlot.HEAD)
                    .getItem() instanceof NightVisionGogglesItem;
            if (!wearing) {
                player.displayClientMessage(
                        Component.translatable("message." + Yunxian.MODID + ".goggles_not_worn"), true);
                continue;
            }
            enabled = !enabled;
            // 第二个参数 true = 在快捷栏上方（action bar）显示，false = 聊天栏
            player.displayClientMessage(
                    Component.translatable(enabled
                            ? "message." + Yunxian.MODID + ".night_vision.on"
                            : "message." + Yunxian.MODID + ".night_vision.off"),
                    true);
        }

        boolean wearing = player.getItemBySlot(EquipmentSlot.HEAD)
                .getItem() instanceof NightVisionGogglesItem;
        if (wearing && enabled) {
            player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 210, 0, false, false, true));
        } else {
            removeOwnNightVision(player);
        }
    }

    private static void removeOwnNightVision(Player player) {
        var effect = player.getEffect(MobEffects.NIGHT_VISION);
        if (effect != null
                && effect.getDuration() <= 210
                && effect.getAmplifier() == 0
                && !effect.isAmbient()) {
            player.removeEffect(MobEffects.NIGHT_VISION);
        }
    }
}