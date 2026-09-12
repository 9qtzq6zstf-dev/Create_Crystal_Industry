package com.minecart.yunxian.ponder;

import com.minecart.yunxian.ponder.scenes.CrystalScenes;
import com.minecart.yunxian.ponder.scenes.MechanicalCleanerScenes;
import com.minecart.yunxian.ponder.scenes.SmartDrillScenes;
import com.minecart.yunxian.registry.ModBlocks;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class AllYunxianPonderScenes {

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // 把所有“方块 → ResourceLocation”的注册统一换成 Block 视角
        PonderSceneRegistrationHelper<Block> blocks = helper.withKeyFunction(BuiltInRegistries.BLOCK::getKey);

        // 1) 母岩通用分镜：原版紫水晶 + 本模组全部母岩共用同一份蓝图/脚本
        blocks.forComponents(
                        Blocks.BUDDING_AMETHYST,
                        ModBlocks.ROSE_QUARTZ_BUDDING.get(),
                        ModBlocks.RAW_IRON_BUDDING.get(),
                        ModBlocks.RAW_GOLD_BUDDING.get(),
                        ModBlocks.RAW_COPPER_BUDDING.get(),
                        ModBlocks.RAW_ZINC_BUDDING.get(),
                        ModBlocks.DIAMOND_BUDDING.get(),
                        ModBlocks.EMERALD_BUDDING.get(),
                        ModBlocks.LAPIS_BUDDING.get(),
                        ModBlocks.ECHO_BUDDING.get(),
                        ModBlocks.QUARTZ_BUDDING.get(),
                        ModBlocks.REDSTONE_BUDDING.get(),
                        ModBlocks.GLOWSTONE_BUDDING.get(),
                        ModBlocks.FLAMMABLE_ICE_BUDDING.get())
                .addStoryBoard("budding/accelerated_growth", CrystalScenes::buddingGrowth, AllYunxianPonderTags.BUDDING);

        // AE2 存在时才注册的福鲁伊克斯母岩
        if (ModBlocks.FLUIX_BUDDING != null)
            blocks.forComponents(ModBlocks.FLUIX_BUDDING.get())
                    .addStoryBoard("budding/accelerated_growth", CrystalScenes::buddingGrowth, AllYunxianPonderTags.BUDDING);

        // 2) 电力催生器
        blocks.forComponents(ModBlocks.ACCELERATOR.get())
                .addStoryBoard("accelerator/electric", CrystalScenes::electricAccelerator, AllYunxianPonderTags.ACCELERATORS);

        // 3) 动力催生器
        blocks.forComponents(ModBlocks.MECHANICAL_ACCELERATOR.get())
                .addStoryBoard("accelerator/mechanical", CrystalScenes::mechanicalAccelerator, AllYunxianPonderTags.ACCELERATORS);

        // 4) 智能钻头
        blocks.forComponents(ModBlocks.SMART_DRILL.get())
                .addStoryBoard("smart_drill/smart_drill", SmartDrillScenes::smartDrill, AllYunxianPonderTags.MACHINES);

        // 5) 动力吸尘器
        blocks.forComponents(ModBlocks.MECHANICAL_CLEANER.get())
                .addStoryBoard("mechanical_cleaner/mechanical_cleaner", MechanicalCleanerScenes::mechanicalCleaner,
                        AllYunxianPonderTags.MACHINES);
    }

}