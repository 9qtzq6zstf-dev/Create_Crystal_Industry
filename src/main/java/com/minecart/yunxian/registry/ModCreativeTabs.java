package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.simibubi.create.AllCreativeModeTabs;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Yunxian.MODID);

    public static final Supplier<CreativeModeTab> YUNXIAN_TAB = CREATIVE_TABS.register("create_crystal_industry_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.create_crystal_industry"))
                    .icon(() -> new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get()))
                    .displayItems((parameters, output) -> {
                        // 母岩家族：顺序与物品栏一致，AE2 联动的家族排在工具之后
                        for (RegisteredFamily family : BuddingFamilies.ALL) {
                            if (family.isRegistered() && !family.spec().ae2Gated()) {
                                // 弧光石那条链（母岩 / 三档芽 / 晶簇，外加上面 tabExtras 里的弧光石
                                // 与失活母岩）还是半成品，暂时不上物品栏——方块与物品仍在注册表里，
                                // /give 能拿到；做完之后把这一判去掉即可
                                if (family == BuddingFamilies.ARCLIGHT) {
                                    continue;
                                }
                                acceptFamily(output, family);
                            }
                        }

                        output.accept(ModBlocks.ACCELERATOR.get());
                        output.accept(ModBlocks.MECHANICAL_ACCELERATOR.get());
                        output.accept(ModBlocks.SMART_DRILL.get());
                        output.accept(ModBlocks.MECHANICAL_CLEANER.get());
                        // 水晶电池还是半成品，暂时不上物品栏（方块与物品仍在注册表里，/give 能拿到）；
                        // 做完之后把这一行加回来即可
                        output.accept(ModBlocks.RESONANCE_TABLE.get());
                        output.accept(ModItems.RESONANCE_FILTER.get());
                        output.accept(ModItems.ECHO_SPYGLASS.get());
                        output.accept(ModItems.NIGHT_VISION_GOGGLES.get());
                        // 电流浆桶：弧光石那条链的产物容器，同样是半成品，跟那条链一起先不上物品栏
                        // （流体、方块与桶都在注册表里，/give 能拿到；做完之后把这一行加回来即可）

                        for (RegisteredFamily family : BuddingFamilies.ALL) {
                            if (family.isRegistered() && family.spec().ae2Gated()) {
                                acceptFamily(output, family);
                            }
                        }
                    })
                    .build());

    /** 一个家族固定 5 个方块（母岩 + 三档芽 + 晶簇），外加家族自定义的追加物品 */
    private static void acceptFamily(CreativeModeTab.Output output, RegisteredFamily family) {
        for (var block : family.blocks()) {
            output.accept(block.get());
        }
        for (var extra : family.spec().appearance().tabExtras()) {
            output.accept(extra.get());
        }
    }

    /**
     * 把可燃冰装饰套件也放进机械动力的「建筑方块」标签页（{@code create:palettes}）。
     * <p>
     * 这套方块从命名到规格都是照 Create 的石材调色板做的（切制 / 层叠 / 小砖块 / 柱），
     * 摆在那一页跟 Create 自己的同类方块挨着更好找。<b>全套只放这里，本模组自己的标签页里不放</b>
     * （那里留的是母岩家族与可燃冰、可燃冰圣代那两件物品）——建材集中在一页找起来才不散。
     * <p>
     * 清单读 {@link ModBlocks#FLAMMABLE_ICE_DECO}。{@code accept} 对重复条目会当场抛
     * {@code IllegalArgumentException}（崩在开创造背包那一步），所以先问一遍标签页里有没有；
     * {@code getParentEntries()} 会实时反映<em>同一个事件里</em>刚被别处（如脚本）加进去的条目，
     * 因此这道去重对跨来源的重复也有效。
     * <p>
     * Create 是本模组的硬依赖，直接引用它的标签页句柄比写死字符串 id 编译期就能对上。
     * {@code AllCreativeModeTabs} 虽带 {@code net.minecraft.client.*} 的导入，但 Create 在自己的
     * 构造器里无条件调用它的 {@code register}，专用服务端本来就会加载这个类；真正客户端专属的那段
     * 用 {@code CatnipServices.PLATFORM.getEnv().isClient()} 兜住了，所以在这里引用它不多担任何风险。
     */
    public static void addDecoToCreateTabs(BuildCreativeModeTabContentsEvent event) {
        if (!AllCreativeModeTabs.PALETTES_CREATIVE_TAB.getKey().equals(event.getTabKey())) {
            return;
        }
        for (DeferredBlock<? extends Block> block : ModBlocks.FLAMMABLE_ICE_DECO) {
            Item item = block.get().asItem();
            if (!alreadyInTab(event, item)) {
                event.accept(item);
            }
        }
    }

    /**
     * 脚本（KubeJS）注册的方块要进的标签页：键 = 标签页 id，值 = 物品 id。
     * <p>
     * 存 id 而不是 {@link Item}：脚本执行时方块还没注册，只能等标签页构建时再解析。
     */
    private static final Map<String, List<ResourceLocation>> SCRIPT_ENTRIES = new LinkedHashMap<>();

    /** 供 KubeJS 助手调用：把某个物品放进指定的创造栏标签页（延后到标签页构建时生效） */
    public static void addScriptedItem(String tabId, ResourceLocation itemId) {
        SCRIPT_ENTRIES.computeIfAbsent(tabId, key -> new ArrayList<>()).add(itemId);
    }

    /**
     * 把脚本注册的方块塞进对应的创造栏标签页——KubeJS 注册的方块**默认不进任何标签页**，
     * 玩家会以为"没注册成功"。匹配很宽松：标签页的完整 id、路径、命名空间三者之一等于配置值即可，
     * 所以写 {@code "kubejs"} 既能命中 {@code kubejs} 也能命中 {@code kubejs:xxx}。
     * <p>
     * 事件由 NeoForge 在构建标签页时触发（KubeJS 自己的 {@code modifyCreativeTab} 也是架在它上面的）。
     */
    public static void addScriptedEntries(BuildCreativeModeTabContentsEvent event) {
        if (SCRIPT_ENTRIES.isEmpty()) {
            return;
        }

        ResourceLocation tab = event.getTabKey().location();
        for (Map.Entry<String, List<ResourceLocation>> entry : SCRIPT_ENTRIES.entrySet()) {
            String group = entry.getKey();
            if (!group.equals(tab.toString()) && !group.equals(tab.getPath()) && !group.equals(tab.getNamespace())) {
                continue;
            }
            for (ResourceLocation itemId : entry.getValue()) {
                Item item = BuiltInRegistries.ITEM.get(itemId);
                if (item != null && item != Items.AIR && !alreadyInTab(event, item)) {
                    event.accept(item);
                }
            }
        }
    }

    /**
     * 标签页里已经有这个物品就不要再加：KubeJS 自己也会把脚本注册的方块放进它那一页，
     * 重复 {@code accept} 会让原版直接抛
     * {@code IllegalArgumentException: Itemstack … already exists in the tab's list}，
     * 而标签页是在**打开创造模式背包时**重建的——于是崩在开背包这一步。
     */
    private static boolean alreadyInTab(BuildCreativeModeTabContentsEvent event, Item item) {
        for (ItemStack stack : event.getParentEntries()) {
            if (stack.getItem() == item) {
                return true;
            }
        }
        return false;
    }

    private ModCreativeTabs() {
    }

    public static void register(IEventBus modEventBus) {
        CREATIVE_TABS.register(modEventBus);
    }
}