package com.minecart.yunxian.datagen;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.block.budding.FluidTankBudding;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.budding.BuddingFamilies.Stage;
import com.minecart.yunxian.budding.BuddingFamily;

import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockModelBuilder;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;

/**
 * 生成全部母岩家族的 blockstate、方块模型与物品模型。
 * <p>
 * 资源路径规则（NeoForge 的 {@code ModelProvider.getBuilder}）：路径里带 "/" 时按原样使用，
 * 否则自动补 {@code block/} 或 {@code item/} 前缀。模型要落在
 * {@code models/block/<家族>/<名字>.json}，所以方块模型必须传 {@code block/<家族>/<名字>}，
 * 物品模型传裸名字即可。
 * <p>
 * 模型形态与贴图全部来自 {@link BuddingFamily}：柱状家族（玫瑰石英、石英）用
 * {@code cube_column} + 侧面/顶面贴图，其余用 {@code cube_all}；芽与晶簇一律用
 * {@code cross} + cutout。
 */
public class ModBlockStateProvider extends BlockStateProvider {

    public ModBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Yunxian.MODID, existingFileHelper);
    }

    @Override
    protected void registerStatesAndModels() {
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (!family.isRegistered()) {
                continue; // AE2 缺席时福鲁伊克斯家族不存在
            }
            buddingBlock(family);
            for (Stage stage : Stage.values()) {
                stageBlock(family, stage);
            }
        }
    }

    /**
     * 母岩本体：方块模型与物品模型。
     * <p>
     * 烧流体的家族（{@link BuddingFamily.Growth#fluid()} 非空，对应 {@code FueledBuddingBlock}）
     * 多一套"燃料不足"外观，挂在 {@code FUELED=false} 上：贴图按
     * {@code <名字>_side_unpowered} / {@code <名字>_top_unpowered}（柱体）或 {@code <名字>_unpowered}
     * （六面体）取名，与"有燃料"那套（带动画）并列。
     * <p>
     * 贴图不做存在性检查——声明了流体需求却没画这套贴图的家族会让 {@code runData} 当场失败，
     * 比默默渲染成缺失模型好。
     */
    private void buddingBlock(RegisteredFamily family) {
        BuddingFamily spec = family.spec();
        String id = spec.id();
        String name = spec.buddingId();
        String modelPath = "block/" + id + "/" + name;
        boolean column = spec.buddingModel() == BuddingFamily.BuddingModel.CUBE_COLUMN;

        ModelFile model = column
                ? models().cubeColumn(modelPath,
                        modLoc("block/" + id + "/" + name + "_side"),
                        modLoc("block/" + id + "/" + name + "_top"))
                : models().cubeAll(modelPath, modLoc("block/" + id + "/" + name));

        Block block = family.budding().get();
        if (spec.growth().fluid() == null) {
            // simpleBlockWithItem 会顺带生成 parent 指向方块模型的物品模型
            simpleBlockWithItem(block, model);
            return;
        }

        String unpoweredPath = modelPath + "_unpowered";
        ModelFile unpowered = column
                ? models().cubeColumn(unpoweredPath,
                        modLoc("block/" + id + "/" + name + "_side_unpowered"),
                        modLoc("block/" + id + "/" + name + "_top_unpowered"))
                : models().cubeAll(unpoweredPath, modLoc("block/" + id + "/" + name + "_unpowered"));

        // 必须显式写两条带属性的变体：simpleBlock 那套写出来的空键 "" 会匹配所有状态，
        // 把 fueled=false 一并吞掉，而且 VariantBlockStateBuilder 只在漏状态时才拦，不会报错
        getVariantBuilder(block)
                .partialState().with(FluidTankBudding.FUELED, true).modelForState().modelFile(model).addModel()
                .partialState().with(FluidTankBudding.FUELED, false).modelForState().modelFile(unpowered).addModel();
        // 物品模型仍旧指"有燃料"那套（与 simpleBlockWithItem 内部做的是同一件事）
        simpleBlockItem(block, model);
    }

    /** 芽与晶簇：六向变体（不含 waterlogged）+ cross 模型 + 物品模型 */
    private void stageBlock(RegisteredFamily family, Stage stage) {
        String id = family.spec().id();
        String name = family.spec().stageId(stage.key);
        String texture = "block/" + id + "/" + name;

        BlockModelBuilder model = models()
                .cross("block/" + id + "/" + name, modLoc(texture))
                .renderType("cutout");

        Block block = family.stage(stage).get();
        getVariantBuilder(block).forAllStatesExcept(state -> {
            Direction facing = state.getValue(AmethystClusterBlock.FACING);
            return ConfiguredModel.builder()
                    .modelFile(model)
                    .rotationX(facing == Direction.DOWN ? 180 : facing.getAxis().isHorizontal() ? 90 : 0)
                    // 模型默认朝北：北 0、东 90、南 180、西 270
                    .rotationY(facing.getAxis().isVertical() ? 0 : ((int) facing.toYRot() + 180) % 360)
                    .build();
        }, AmethystClusterBlock.WATERLOGGED);

        // 芽/簇的物品模型用方块贴图，而不是同名物品贴图
        itemModels().withExistingParent(name, mcLoc("item/generated"))
                .texture("layer0", modLoc(texture));
    }
}
