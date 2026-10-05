package com.minecart.yunxian.datagen;

import java.util.concurrent.CompletableFuture;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.battery.CrystalTier;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModTags;

import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.IntrinsicHolderTagsProvider.IntrinsicTagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredBlock;
import org.jetbrains.annotations.Nullable;

/**
 * 生成方块标签：通用母岩标签 c:budding_blocks、通用芽 / 晶簇标签 c:buds / c:clusters、
 * 挖掘工具标签、挖掘等级标签，以及水晶电池的晶体档位标签（内容由 {@link CrystalTier} 派生）。
 * 新增母岩家族时这里不需要改动，全部由 {@link BuddingFamilies#ALL} 派生。
 */
public class ModBlockTagsProvider extends BlockTagsProvider {

    public ModBlockTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
                                @Nullable ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Yunxian.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // c:budding_blocks —— 智能钻头精准采集与 AE2 晶体催生器都认它
        IntrinsicTagAppender<Block> budding = tag(ModTags.BUDDING_BLOCKS);
        // c:buds / c:clusters —— NeoForge 通用标签把「母岩 / 芽 / 晶簇」分三类，
        // 三档芽进前者、晶簇进后者（物品侧见 ModItemTagsProvider）
        IntrinsicTagAppender<Block> buds = tag(ModTags.BUDS);
        IntrinsicTagAppender<Block> clusters = tag(ModTags.CLUSTERS);
        // 挖掘工具：全部家族方块 + 可燃冰装饰方块 + 四种机器 + 水晶电池 + 共振台
        // 注意共振台：它的属性抄的是安山岩，带 requiresCorrectToolForDrops。这类方块
        // 如果没进 mineable/pickaxe，任何工具都不算"正确工具"，挖掉直接什么都不掉
        // （和水晶电池那条注释是同一个坑），所以必须登记。
        IntrinsicTagAppender<Block> pickaxe = tag(BlockTags.MINEABLE_WITH_PICKAXE);
        pickaxe.add(ModBlocks.INACTIVE_ARCLIGHT_BUDDING.get(), ModBlocks.FLAMMABLE_ICE_BLOCK.get(),
                ModBlocks.FLAMMABLE_ICE_BRICKS.get(), ModBlocks.FLAMMABLE_ICE_SLAB.get(),
                ModBlocks.FLAMMABLE_ICE_STAIRS.get(), ModBlocks.FLAMMABLE_ICE_WALL.get(),
                ModBlocks.FLAMMABLE_ICE_BRICK_SLAB.get(),
                ModBlocks.FLAMMABLE_ICE_BRICK_STAIRS.get(), ModBlocks.FLAMMABLE_ICE_BRICK_WALL.get(),
                ModBlocks.FLAMMABLE_ICE_PILLAR.get(),
                ModBlocks.CUT_FLAMMABLE_ICE.get(), ModBlocks.CUT_FLAMMABLE_ICE_SLAB.get(),
                ModBlocks.CUT_FLAMMABLE_ICE_STAIRS.get(), ModBlocks.CUT_FLAMMABLE_ICE_WALL.get(),
                ModBlocks.LAYERED_FLAMMABLE_ICE.get(),
                ModBlocks.SMALL_FLAMMABLE_ICE_BRICKS.get(), ModBlocks.SMALL_FLAMMABLE_ICE_BRICK_SLAB.get(),
                ModBlocks.SMALL_FLAMMABLE_ICE_BRICK_STAIRS.get(), ModBlocks.SMALL_FLAMMABLE_ICE_BRICK_WALL.get(),
                ModBlocks.ACCELERATOR.get(),
                ModBlocks.SMART_DRILL.get(), ModBlocks.MECHANICAL_ACCELERATOR.get(),
                ModBlocks.MECHANICAL_CLEANER.get(), ModBlocks.CRYSTAL_BATTERY.get(),
                ModBlocks.RESONANCE_TABLE.get());
        // minecraft:walls —— 原版把每一种墙都登记进来。墙的形状与连接不依赖这个标签，
        // 它是给数据包/别的模组认「这是一堵墙」用的约定标签；物品侧由
        // ModItemTagsProvider 用 copy 复制过去（标签不能跨方块/物品互相引用）。
        // 生成的文件会与原版同名标签**合并**（replace 默认为 false），不是覆盖
        tag(BlockTags.WALLS).add(ModBlocks.FLAMMABLE_ICE_WALL.get(), ModBlocks.FLAMMABLE_ICE_BRICK_WALL.get(),
                ModBlocks.CUT_FLAMMABLE_ICE_WALL.get(), ModBlocks.SMALL_FLAMMABLE_ICE_BRICK_WALL.get());
        // 挖掘等级：只有指定了等级的家族才登记（荧石与可燃冰不设等级）
        IntrinsicTagAppender<Block> needsStone = tag(BlockTags.NEEDS_STONE_TOOL);
        // 水晶电池底子取的是铜块属性（requiresCorrectToolForDrops），
        // 等级必须跟着一起登记，否则任何工具都算不上"正确"，挖掉一格都不掉东西
        needsStone.add(ModBlocks.CRYSTAL_BATTERY.get());
        IntrinsicTagAppender<Block> needsIron = tag(BlockTags.NEEDS_IRON_TOOL);
        // 失活母岩抄的是弧光石母岩的属性（于是也带 requiresCorrectToolForDrops），
        // 而弧光石母岩属于 IRON 档，所以这一格的等级得跟着它一起登记——否则同样是什么都不掉
        needsIron.add(ModBlocks.INACTIVE_ARCLIGHT_BUDDING.get());
        IntrinsicTagAppender<Block> needsDiamond = tag(BlockTags.NEEDS_DIAMOND_TOOL);

        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (!family.isRegistered()) {
                continue;
            }
            // AE2 缺席时这些方块根本不存在，标签里必须写成可选，否则加载时报缺失
            boolean optional = family.spec().ae2Gated();

            IntrinsicTagAppender<Block> tier = switch (family.spec().toolTier()) {
                case STONE -> needsStone;
                case IRON -> needsIron;
                case DIAMOND -> needsDiamond;
                case NONE -> null;
            };

            // c:budding_blocks 只登记母岩本体：智能钻头用它判断"精准模式直接掉方块自身"，
            // AE2 晶体催生器用它决定哪些方块可以被加速——芽与晶簇不属于这个语义。
            add(budding, family.budding(), optional);
            // 芽与晶簇各有自己的通用标签
            add(buds, family.smallBud(), optional);
            add(buds, family.mediumBud(), optional);
            add(buds, family.largeBud(), optional);
            add(clusters, family.cluster(), optional);

            for (DeferredBlock<Block> block : family.blocks()) {
                add(pickaxe, block, optional);
                if (tier != null) {
                    add(tier, block, optional);
                }
            }
        }

        addBatteryCrystalTags(provider);
    }

    /**
     * 水晶电池的晶体档位标签：内容全部来自 {@link CrystalTier#defaultBlocks()}。
     * <p>
     * 物品侧不在这里生成，而是由 {@link ModItemTagsProvider} 用 {@code ItemTagsProvider#copy}
     * 把这些标签原样复制过去——标签不能跨方块/物品互相引用，只能这样避免两份列表各写一遍。
     * {@code copy} 要求被复制的方块标签由本次生成产出，所以这些标签必须是数据生成的，
     * 不能留在 resources 下手写（同路径会与生成结果冲突，构建直接失败）。
     */
    private void addBatteryCrystalTags(HolderLookup.Provider provider) {
        HolderGetter<Block> blocks = provider.lookupOrThrow(Registries.BLOCK);

        for (CrystalTier tier : CrystalTier.values()) {
            IntrinsicTagAppender<Block> tierTag = tag(tier.tag());
            for (ResourceLocation id : tier.defaultBlocks()) {
                // 用 add(Block) 而不是 addOptional(id)：默认成员全是硬依赖（原版/机械动力/本模组），
                // 写错就该当场抛异常，而不是生成一条 required:false 把错误静默吞掉。
                tierTag.add(blocks.get(ResourceKey.create(Registries.BLOCK, id))
                        .orElseThrow(() -> new IllegalStateException(
                                "晶体容量档 " + tier + " 的默认方块不存在：" + id))
                        .value());
            }
        }

        // 汇总标签：引用四个档位标签，而不是把方块再列一遍，所以永远不会和档位表脱节
        IntrinsicTagAppender<Block> crystal = tag(ModTags.BATTERY_CRYSTAL);
        for (CrystalTier tier : CrystalTier.values()) {
            crystal.addTag(tier.tag());
        }
    }

    private static void add(IntrinsicTagAppender<Block> tag, DeferredBlock<Block> block, boolean optional) {
        if (optional) {
            tag.addOptional(block.getId());
        } else {
            tag.add(block.get());
        }
    }
}
