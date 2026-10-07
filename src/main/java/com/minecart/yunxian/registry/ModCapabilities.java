package com.minecart.yunxian.registry;

import com.minecart.yunxian.integration.curios.CuriosIntegration;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.capability.wrappers.FluidBucketWrapper;

public final class ModCapabilities {
    private ModCapabilities() {
    }

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ModCapabilities::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ACCELERATOR.get(),
                (blockEntity, side) -> blockEntity.getEnergyCapability(side)
        );
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.MECHANICAL_CLEANER.get(),
                (be, context) -> be.getInventory()
        );
        // 共振台：台面那格物品对外可插可取（机械臂/漏斗/带子）。直接借 DepotBehaviour 自带的
        // DepotItemHandler —— 置物台也是这么挂的，所以 arm 那边只要用基类 ArmInteractionPoint
        // 就能直接工作，不用额外写逻辑。
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                ModBlockEntities.RESONANCE_TABLE.get(),
                (be, context) -> be.depotBehaviour.itemHandler
        );
        // 水晶电池：整座多方块结构共用一个 FE 接口，从任意一格接出去看到的都是同一池电
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.CRYSTAL_BATTERY.get(),
                (blockEntity, side) -> blockEntity.getEnergyCapability(side)
        );
        // 母岩的流体罐（远古残骸的熔岩罐、脚本用 needfluid 声明的那些）：管道/泵与手持容器
        // 都靠这个能力进出（六个面都通）。罐的容量与认哪种流体由方块自己的定义给出
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.FLUID_TANK_BUDDING.get(),
                (blockEntity, side) -> blockEntity
        );
        // 智能温控室：可燃冰沙的罐，六个面都通。管道/泵与手持容器都靠这个能力进出——
        // 少了这一行，方块照样能右键灌、玩家不会觉得不对，但管道一滴也推不进来（静默失效）
        event.registerBlockEntity(
                Capabilities.FluidHandler.BLOCK,
                ModBlockEntities.SMART_TEMPERATURE_CHAMBER.get(),
                (blockEntity, side) -> blockEntity
        );
        // 弧光石母岩：1 M FE 的能量容器，只吃不吐（见 ArclightBuddingBlockEntity 的类注释）
        event.registerBlockEntity(
                Capabilities.EnergyStorage.BLOCK,
                ModBlockEntities.ARCLIGHT_BUDDING.get(),
                (blockEntity, side) -> blockEntity.getEnergyCapability(side)
        );

        // 可燃冰沙桶：手动补上流体能力。
        // NeoForge 只给「恰好是 BucketItem 类」的物品自动挂（CapabilityHooks 里是精确相等），
        // 而我们的桶必须是子类——要覆盖 emptyContents 去放方块而不是放流体。
        // 少了这一行，桶照样能放方块、玩家不会觉得不对，但 Create 的注液器灌不进、管道也抽不出，
        // 而且全程没有任何报错：典型的静默失效。
        // （FluidBucketWrapper 内部用的是 instanceof，所以子类它认。）
        event.registerItem(
                Capabilities.FluidHandler.ITEM,
                (stack, context) -> new FluidBucketWrapper(stack),
                ModItems.FLAMMABLE_ICE_SLURRY_BUCKET.get());

        // ★ 软依赖门控：只有 Curios 已加载才触碰 Curios 类
        if (ModList.get().isLoaded("curios")) {
            CuriosIntegration.registerCapabilities();
        }
    }
}