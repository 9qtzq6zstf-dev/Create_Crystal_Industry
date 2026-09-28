package com.minecart.yunxian.registry;

import com.minecart.yunxian.item.FlammableIceItem;
import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.item.NightVisionGogglesItem;
import com.minecart.yunxian.item.EchoSpyglassItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Yunxian.MODID);

    public static final DeferredItem<FlammableIceItem> FLAMMABLE_ICE =
            ITEMS.register("flammable_ice", () -> new FlammableIceItem(new Item.Properties()));

    /**
     * 弧光石：弧光石母岩的晶簇掉落物，也是冲压出电流浆的原料。
     * 它<b>只有物品</b>，没有对应的装饰方块（与可燃冰不同）。
     */
    public static final DeferredItem<Item> ARCLIGHT =
            ITEMS.register("arclight", () -> new Item(new Item.Properties()));

    /**
     * 电流浆桶：电流浆的容器物品（灌装、倒出、进储罐）。
     * <p>
     * 刻意用原版的 {@code BucketItem} 本体、<b>不要</b>子类：NeoForge 只给
     * 「{@code item.getClass() == BucketItem.class}」的物品挂流体能力
     * （{@code CapabilityHooks} 里就是 == 判断），换成子类的话 Create 的注液器就灌不进它、
     * 手持它也倒不出东西——这是静默失效，没有报错。
     */
    public static final DeferredItem<BucketItem> CURRENT_SLURRY_BUCKET =
            ITEMS.register("current_slurry_bucket", () -> new BucketItem(ModFluids.CURRENT_SLURRY.get(),
                    new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    public static final DeferredItem<EchoSpyglassItem> ECHO_SPYGLASS =
            ITEMS.register("echo_spyglass",
                    () -> new EchoSpyglassItem(new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.RARE)
                            .component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)));
    public static final DeferredItem<NightVisionGogglesItem> NIGHT_VISION_GOGGLES =
            ITEMS.register("night_vision_goggles",
                    () -> new NightVisionGogglesItem(new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.RARE)));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}