package com.minecart.yunxian.client.sundae;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.network.SoulBreathPayload;
import com.minecart.yunxian.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 「灵魂火喷流」的客户端一半：把原版根本不外发的输入（空手对空气右键）翻译成心跳包，
 * 顺带取消这次右键本身。真正的效果在服务端，见 {@code SoulBreath}。
 * <p>
 * <b>为什么钩 {@code InteractionKeyMappingTriggered} 而不是 {@code PlayerInteractEvent}</b>：
 * 后者里 {@code RightClickEmpty} 只在瞄空气时才响（对着墙喷不出来），{@code RightClickBlock}
 * 又只管对着方块那一路。{@code InteractionKeyMappingTriggered} 是「右键键被触发」这件事本身，
 * 在 {@code Minecraft#startUseItem} 里每只手各发一次、无论准心指着什么，正好对上「瞄哪里都喷」。
 * <p>
 * <b>按住是白送的</b>：这个事件在按住期间会被反复触发——{@code startUseItem} 每约 4 tick 被
 * {@code Minecraft#handleKeybinds} 再调一次（{@code rightClickDelay} 数到 0 就来一次），
 * 于是心跳自然发成一条，无需自己记按键状态。松手则什么都不发，服务端那边
 * {@code SoulBreath.BEAM_TICKS} 之后火束自己灭。
 * <p>
 * <b>取消是必须的</b>：原版只在<i>手里有东西</i>时才因潜行跳过方块交互，两手空空时潜行右键照样会把
 * {@code useItemOn}/{@code useWithoutItem} 跑一遍——不取消的话，对着拉杆/按钮/地上的圣代方块一喷，
 * 那些东西会跟着一起响应。取消掉这次输入，客户端就不会发对应的 {@code ServerboundUseItem*} 包，
 * 服务端自然也不会去处理。代价是「可燃气体」那一分钟里点不了这些东西，见 {@code SoulBreath} 的类注释。
 */
@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class SoulBreathClient {

    private SoulBreathClient() {
    }

    @SubscribeEvent
    public static void onInteractionKey(InputEvent.InteractionKeyMappingTriggered event) {
        // 主手判断必须放在最前：主手那次只要没被取消，startUseItem 就会接着把副手那次也发出来，
        // 不挡住的话副手（同样满足条件时）会重复触发
        if (!event.isUseItem() || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Player player = Minecraft.getInstance().player;
        if (player == null || !wants(player)) {
            return;
        }
        // 取消这次右键（于是不发原版的 use/useItemOn 包），改发喷火心跳
        event.setCanceled(true);
        event.setSwingHand(true);
        PacketDistributor.sendToServer(new SoulBreathPayload());
    }

    /** 喷火的资格，与服务端 {@code SoulBreath#isWielding} 保持一致（服务端那份才是权威） */
    private static boolean wants(Player player) {
        return player.isSecondaryUseActive()
                && player.getMainHandItem().isEmpty()
                && player.hasEffect(ModEffects.FLAMMABLE_GAS)
                && !player.isSpectator();
    }
}
