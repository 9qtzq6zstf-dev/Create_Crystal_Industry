package com.minecart.yunxian.client;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.client.model.NightVisionGogglesModel;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

@EventBusSubscriber(modid = Yunxian.MODID, value = Dist.CLIENT)
public final class NightVisionGogglesRender {
    private NightVisionGogglesRender() {}

    @SubscribeEvent
    public static void onPlayerPre(RenderPlayerEvent.Pre event) {
        // 每个玩家渲染开始前：记录"正在被渲染的玩家"
        NightVisionGogglesModel.RENDERING_PLAYER.set(event.getEntity());
    }

    @SubscribeEvent
    public static void onPlayerPost(RenderPlayerEvent.Post event) {
        // 渲染结束后清除，防止串到下一个玩家
        NightVisionGogglesModel.RENDERING_PLAYER.remove();
    }
}