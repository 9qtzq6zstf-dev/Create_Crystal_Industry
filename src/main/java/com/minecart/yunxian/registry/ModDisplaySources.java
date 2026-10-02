package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.display.ResonanceFilterDisplaySource;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.registry.CreateRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.RegisterEvent;

/**
 * 让共振台能被 Create 的显示链接器（{@code display_link}）读取。
 * <p>
 * 两件事：
 * <ol>
 *   <li>把数据源注册进 Create 的 {@code DISPLAY_SOURCE} 注册表；</li>
 *   <li>把数据源<b>关联到我们那个方块</b>（{@link DisplaySource#BY_BLOCK}）——
 *       显示链接器正是按方块查这张表（见 {@code DisplaySource.getAll}）。</li>
 * </ol>
 * 注册表那一步必须挂在 {@code RegisterEvent} 上、不能在模组构造器里直接注册：Create 的
 * 那几个自有注册表在它构造完成时就冻结了（和 {@link ModArmInteractionPointTypes} 同一个坑）。
 * <p>
 * 关联那一步反过来要<b>晚</b>做 —— 得等我们的方块注册完，所以放在 commonSetup 里。
 * {@code SimpleRegistry} 没有冻结检查，晚加是安全的。
 */
public final class ModDisplaySources {

    public static final ResourceLocation RESONANCE_FILTER_LIST =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "resonance_filter_list");

    private static final ResonanceFilterDisplaySource SOURCE = new ResonanceFilterDisplaySource();

    private ModDisplaySources() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(RegisterEvent.class, event -> {
            if (event.getRegistryKey().equals(CreateRegistries.DISPLAY_SOURCE))
                event.register(CreateRegistries.DISPLAY_SOURCE, RESONANCE_FILTER_LIST, () -> SOURCE);
        });
    }

    /** 等方块注册完之后再挂 —— 见类注释 */
    public static void associateBlocks() {
        DisplaySource.BY_BLOCK.add(ModBlocks.RESONANCE_TABLE.get(), SOURCE);
    }
}
