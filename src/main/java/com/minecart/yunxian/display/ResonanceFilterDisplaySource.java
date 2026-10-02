package com.minecart.yunxian.display;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * 显示链接器（{@code display_link}）的一个数据源：把一个共振台<b>所在网络里所有台面上的物品</b>
 * 列出来 —— 也就是"这个网络在过滤些什么"。
 * <p>
 * 为什么不是照抄置物台的 {@code ITEM_NAMES}：那个源只读<b>指到的那一个</b>方块，
 * 而我们这边真正有意义的是整个网络。所以这里自己实现，走的还是 Create 现成的通道。
 * <p>
 * <b>只覆盖 {@code provideText}</b>：基类的 {@code provideFlapDisplayText} 默认就把每一行
 * 包成单段、{@code loadFlapDisplayLayout} 默认铺满一整条字母段，正好是纯文字列表要的样子。
 * <p>
 * 每行就是<b>物品名本身</b>，不带数量。之前继承 {@code ValueListDisplaySource} 时每行总会被
 * 加上一个数字（当时放的是"有几张台子放着它"），于是单张台子会显示成"1 铁锭" —— 过滤列表里
 * 那个数字没有意义，所以换成直接继承 {@link DisplaySource}。
 * <p>
 * <b>这个类必须是双端都能加载的</b>：它会被注册进 Create 的数据源注册表，专用服务端也要加载它。
 * 所以这里只碰公共 API —— 基类里带 {@code @OnlyIn(CLIENT)} 的只有配置界面那几个方法，没被覆盖。
 */
public class ResonanceFilterDisplaySource extends DisplaySource {

    @Override
    public List<MutableComponent> provideText(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof ResonanceTableBlockEntity table))
            return EMPTY;

        return table.getNetworkFilterText()
                .stream()
                .limit(stats.maxRows())
                .map(Component::copy)
                .toList();
    }
}
