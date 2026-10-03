package com.minecart.yunxian.ponder;

import java.util.ArrayList;
import java.util.List;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.ponder.scenes.CrystalScenes;
import com.minecart.yunxian.ponder.scenes.MechanicalCleanerScenes;
import com.minecart.yunxian.ponder.scenes.ResonanceScenes;
import com.minecart.yunxian.ponder.scenes.SmartDrillScenes;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModItems;

import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * 思索分镜的注册表：<b>一个功能一条分镜</b>，同一台机器的几条按「入门 → 进阶」排。
 * <p>
 * 多条分镜共用同一张蓝图是刻意为之：蓝图只决定场上摆了什么，讲什么由脚本决定。
 */
public class AllYunxianPonderScenes {

    public static void register(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        // 把所有“方块 → ResourceLocation”的注册统一换成 Block 视角
        PonderSceneRegistrationHelper<Block> blocks = helper.withKeyFunction(BuiltInRegistries.BLOCK::getKey);

        // 1) 母岩：怎么长、催生器怎么帮它
        List<Block> buddingBlocks = new ArrayList<>();
        buddingBlocks.add(Blocks.BUDDING_AMETHYST);
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (family.isRegistered()) {
                buddingBlocks.add(family.budding().get());
            }
        }
        blocks.forComponents(buddingBlocks)
                .addStoryBoard("budding/accelerated_growth", CrystalScenes::buddingGrowth, AllYunxianPonderTags.BUDDING);

        // 2) 电力催生器：供能 / 随机刻还加速什么
        blocks.forComponents(ModBlocks.ACCELERATOR.get())
                .addStoryBoard("accelerator/electric", CrystalScenes::electricAccelerator,
                        AllYunxianPonderTags.ACCELERATORS)
                .addStoryBoard("accelerator/electric", CrystalScenes::randomTickTargets,
                        AllYunxianPonderTags.ACCELERATORS);

        // 3) 动力催生器：供能（转速决定快慢）
        blocks.forComponents(ModBlocks.MECHANICAL_ACCELERATOR.get())
                .addStoryBoard("accelerator/mechanical", CrystalScenes::mechanicalAccelerator,
                        AllYunxianPonderTags.ACCELERATORS);

        // 4) 智能钻头：速度 / 切模式 / 精准采集母岩
        blocks.forComponents(ModBlocks.SMART_DRILL.get())
                .addStoryBoard("smart_drill/smart_drill", SmartDrillScenes::smartDrillSpeed,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("smart_drill/smart_drill", SmartDrillScenes::smartDrillModes,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("smart_drill/smart_drill", SmartDrillScenes::smartDrillSilkTouch,
                        AllYunxianPonderTags.MACHINES);

        // 5) 动力吸尘器：气流与收集 / 前方容器 / 配置
        blocks.forComponents(ModBlocks.MECHANICAL_CLEANER.get())
                .addStoryBoard("mechanical_cleaner/mechanical_cleaner", MechanicalCleanerScenes::cleanerAirflow,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("mechanical_cleaner/mechanical_cleaner", MechanicalCleanerScenes::cleanerContainer,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("mechanical_cleaner/mechanical_cleaner", MechanicalCleanerScenes::cleanerConfig,
                        AllYunxianPonderTags.MACHINES);

        // 6) 共振台一族：台面 / 组网与共享过滤 / 规则来源 / 列表与属性 / 红石冻结 / 显示
        blocks.forComponents(ModBlocks.RESONANCE_TABLE.get())
                .addStoryBoard("resonance_table/resonance_table", ResonanceScenes::resonanceTable,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("resonance_table/resonance_filter", ResonanceScenes::resonanceNetwork,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("resonance_table/resonance_filter", ResonanceScenes::resonanceFilter,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("resonance_table/resonance_filter", ResonanceScenes::resonanceFilterTypes,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("resonance_table/resonance_filter", ResonanceScenes::resonanceFilterRedstone,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("resonance_table/resonance_display", ResonanceScenes::resonanceDisplay,
                        AllYunxianPonderTags.MACHINES);

        // 7) 共振过滤器：与共振台挂同一整套分镜，鼠标停在物品上也能直接看全部
        helper.withKeyFunction(BuiltInRegistries.ITEM::getKey)
                .forComponents(ModItems.RESONANCE_FILTER.get())
                .addStoryBoard("resonance_table/resonance_table", ResonanceScenes::resonanceTable,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("resonance_table/resonance_filter", ResonanceScenes::resonanceNetwork,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("resonance_table/resonance_filter", ResonanceScenes::resonanceFilter,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("resonance_table/resonance_filter", ResonanceScenes::resonanceFilterTypes,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("resonance_table/resonance_filter", ResonanceScenes::resonanceFilterRedstone,
                        AllYunxianPonderTags.MACHINES)
                .addStoryBoard("resonance_table/resonance_display", ResonanceScenes::resonanceDisplay,
                        AllYunxianPonderTags.MACHINES);
    }

}
