package com.minecart.yunxian;

import com.minecart.yunxian.item.EchoSpyglassItem;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemContainerContents;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Yunxian.MODID);

    public static final DeferredItem<EchoSpyglassItem> ECHO_SPYGLASS =
            ITEMS.register("echo_spyglass",
                    () -> new EchoSpyglassItem(new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.RARE)
                            .component(DataComponents.CONTAINER, ItemContainerContents.EMPTY)));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
    }
}