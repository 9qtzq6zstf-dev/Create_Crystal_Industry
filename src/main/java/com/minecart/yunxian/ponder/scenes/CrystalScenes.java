package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.block.AcceleratorBlock;
import com.minecart.yunxian.block.MechanicalAcceleratorBlock;

import com.minecart.yunxian.budding.BuddingFamilies;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.Block;

/**
 * 母岩与催生器的四条分镜：一张只讲一件事。
 * <p>
 * 演出优先：能用方块变化、粒子与高亮说清的就不写字，每条分镜只留 2-3 条文字。
 * 三张蓝图的坐标见各方法上方的注释。
 */
public class CrystalScenes {

    /*
     * ============ 1) 母岩怎么生长 ============
     * 蓝图 budding/accelerated_growth.nbt：
     *   - (2,1,2)  原版紫水晶母岩
     *   - (2,2,2)  生长格（空气）
     *   - (1,1,2)  动力催生器（代码强制朝向 EAST，背面即西侧）
     *   - (0,1,2)  传动杆（轴沿 X）
     *   - (3,1,2)  电力催生器
     *
     * 演出：催生器出场前，芽一级一级慢慢冒（级间长 idle 表现「慢」）；
     * 通电后连跳三级到晶簇，再 restoreBlocks 重来一轮表现可重复的产出。
     */
    public static void buddingGrowth(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("budding_growth", "How Budding Blocks Grow");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos budding = util.grid().at(2, 1, 2);
        BlockPos spot = util.grid().at(2, 2, 2);
        BlockPos mech = util.grid().at(1, 1, 2);
        BlockPos shaft = util.grid().at(0, 1, 2);
        BlockPos electric = util.grid().at(3, 1, 2);

        scene.world().showSection(util.select().fromTo(budding, spot), Direction.DOWN);
        scene.idle(20);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("Budding Blocks only advance their growth when a random tick lands on them. Random ticks are rare, and each one only has a small chance to advance a stage.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(budding));
        scene.idle(120);

        // 自然生长：一级芽之间留很久，表现「慢」
        scene.world().setBlock(spot, budState(Blocks.SMALL_AMETHYST_BUD), false);
        scene.idle(60);
        scene.world().setBlock(spot, budState(Blocks.MEDIUM_AMETHYST_BUD), false);
        scene.idle(70);

        // 催生器就位
        scene.world().showSection(util.select().position(mech), Direction.SOUTH);
        scene.world().showSection(util.select().position(shaft), Direction.EAST);
        scene.world().showSection(util.select().position(electric), Direction.NORTH);
        scene.world().modifyBlock(mech, s -> s.setValue(MechanicalAcceleratorBlock.FACING, Direction.EAST), false);
        scene.idle(15);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("Accelerators change that: each tick, they force a random tick onto every block they touch.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(electric));
        scene.idle(15);

        scene.world().cycleBlockProperty(electric, AcceleratorBlock.POWERED);
        scene.world().cycleBlockProperty(mech, MechanicalAcceleratorBlock.POWERED);
        scene.world().modifyBlockEntityNBT(util.select().position(shaft), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", 32f));
        scene.effects().indicateSuccess(electric);
        scene.effects().indicateSuccess(mech);
        scene.idle(10);

        // 同一颗芽瞬间走完剩下的阶段
        scene.world().setBlock(spot, budState(Blocks.LARGE_AMETHYST_BUD), false);
        scene.idle(6);
        scene.world().setBlock(spot, budState(Blocks.AMETHYST_CLUSTER), false);
        scene.effects().indicateSuccess(spot);
        scene.idle(40);

        // 可重复：清掉再长一轮
        scene.world().restoreBlocks(util.select().position(spot));
        scene.idle(5);
        growUp(scene, spot);
        scene.idle(25);

        scene.overlay().showText(120)
                .attachKeyFrame()
                .text("Some Budding Blocks add their own requirements: Echo needs darkness, Flammable Ice needs water, Fluix needs AE power, and Quartz and Glowstone only grow at full speed in the Nether.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(budding));
        scene.idle(130);
    }

    /*
     * ============ 2) 电力催生器 ============
     * 蓝图 accelerator/electric.nbt：
     *   - (2,1,2)         电力催生器（中心）
     *   - (2,1,1)/(2,2,1) 粗铁母岩 / 小型粗铁芽
     *   - (2,1,3)/(2,2,3) 玫瑰石英母岩 / 小型玫瑰石英芽
     *   - (3,1,2)/(3,2,2) 原版紫水晶母岩 / 小型紫水晶芽
     *   - (1,1,2)         小麦 age 0（基座 (1,0,2) 为耕地）
     *
     * 演出：通电后四个方向同时推进，用 showOutline 圈出被催的那几块，替代「六个面」的说法。
     */
    public static void electricAccelerator(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("accelerator_electric", "Powering the Electric Accelerator");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos acc = util.grid().at(2, 1, 2);
        BlockPos ironBudding = util.grid().at(2, 1, 1);
        BlockPos ironBud = util.grid().at(2, 2, 1);
        BlockPos roseBudding = util.grid().at(2, 1, 3);
        BlockPos roseSpot = util.grid().at(2, 2, 3);
        BlockPos amethyst = util.grid().at(3, 1, 2);
        BlockPos amethystSpot = util.grid().at(3, 2, 2);
        BlockPos wheat = util.grid().at(1, 1, 2);

        scene.world().showSection(util.select().position(acc), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Accelerators come in two types. This Electric Accelerator runs on FE; the Mechanical Accelerator runs on Rotational Force.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(acc));
        scene.idle(100);

        scene.world().showSection(util.select().fromTo(ironBudding, ironBud), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().fromTo(roseBudding, roseSpot), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().fromTo(amethyst, amethystSpot), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(wheat), Direction.DOWN);
        scene.idle(15);

        // 圈出四周会被催到的方块（不配文字，靠高亮说明「周围都吃得到」）
        scene.overlay().showOutlineWithText(util.select()
                        .position(ironBudding)
                        .add(util.select().position(roseBudding))
                        .add(util.select().position(amethyst))
                        .add(util.select().position(wheat)), 100)
                .text("Supplying it with FE starts it: every tick, one random tick is forced onto each neighbouring block.")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(20);

        scene.world().cycleBlockProperty(acc, AcceleratorBlock.POWERED);
        scene.effects().indicateSuccess(acc);
        scene.idle(10);
        advanceAll(scene, wheatCrop(wheat), familyCrop(ironBud, BuddingFamilies.RAW_IRON),
                familyCrop(roseSpot, BuddingFamilies.ROSE_QUARTZ), amethystCrop(amethystSpot));
        scene.idle(20);
    }

    /*
     * ============ 3) 动力催生器 ============
     * 蓝图 accelerator/mechanical.nbt：
     *   - (2,1,2)         动力催生器（正面朝北，背面朝南）
     *   - (2,1,1)/(2,2,1) 粗金母岩 / 生长位
     *   - (3,1,2)/(3,2,2) 原版紫水晶母岩 / 生长位
     *   - (1,1,2)         小麦 age 0（基座 (1,0,2) 为耕地）
     *   - (2,1,3)         传动杆（轴沿 Z，接催生器背面）
     *   - (2,1,4)         小齿轮（与大齿轮斜角啮合）
     *   - (3,2,4)         大齿轮（动力输入）
     *
     * 演出：低速段只长一级、提速后连跳三级 —— 转速与产出速度的关系由两段动画对比给出。
     */
    public static void mechanicalAccelerator(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("accelerator_mechanical", "Powering the Mechanical Accelerator");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos acc = util.grid().at(2, 1, 2);
        BlockPos goldBudding = util.grid().at(2, 1, 1);
        BlockPos goldSpot = util.grid().at(2, 2, 1);
        BlockPos amethyst = util.grid().at(3, 1, 2);
        BlockPos amethystSpot = util.grid().at(3, 2, 2);
        BlockPos wheat = util.grid().at(1, 1, 2);
        BlockPos shaft = util.grid().at(2, 1, 3);
        BlockPos cog = util.grid().at(2, 1, 4);
        BlockPos largeCog = util.grid().at(3, 2, 4);

        scene.world().showSection(util.select().position(acc), Direction.DOWN);
        scene.world().modifyBlock(acc, s -> s.setValue(MechanicalAcceleratorBlock.FACING, Direction.NORTH), false);
        scene.idle(10);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("This Mechanical Accelerator runs on Rotational Force, taken at its back.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(acc));
        scene.idle(20);

        // 背面动力链依次就位：传动杆 → 小齿轮 → 大齿轮
        scene.world().showSection(util.select().position(shaft), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(cog), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(largeCog), Direction.DOWN);
        scene.idle(10);

        scene.world().showSection(util.select().fromTo(goldBudding, goldSpot), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().fromTo(amethyst, amethystSpot), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(wheat), Direction.DOWN);
        scene.idle(10);

        // 低速：只长一级
        scene.world().cycleBlockProperty(acc, MechanicalAcceleratorBlock.POWERED);
        scene.world().modifyBlockEntityNBT(util.select().fromTo(shaft, cog), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", -16f));
        scene.world().modifyBlockEntityNBT(util.select().position(largeCog), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", 8f));
        scene.effects().indicateSuccess(acc);
        scene.idle(10);
        scene.world().setBlock(amethystSpot, budState(Blocks.SMALL_AMETHYST_BUD, Direction.UP), false);
        scene.world().setBlock(goldSpot, budState(BuddingFamilies.RAW_GOLD.smallBud().get(), Direction.UP), false);
        scene.idle(35);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("The slower the input spins, the fewer random ticks it applies each second. At low speed a stage still takes a while.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(amethystSpot));
        scene.idle(105);

        // 提速：剩下的阶段一瞬间走完
        scene.world().modifyBlockEntityNBT(util.select().fromTo(shaft, cog), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", -64f));
        scene.world().modifyBlockEntityNBT(util.select().position(largeCog), KineticBlockEntity.class,
                nbt -> nbt.putFloat("Speed", 32f));
        scene.effects().indicateSuccess(largeCog);
        scene.idle(5);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Raise the input speed and the same stages complete in moments.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(largeCog));
        scene.idle(15);
        advanceAll(scene, wheatCrop(wheat), familyCrop(goldSpot, BuddingFamilies.RAW_GOLD), amethystCrop(amethystSpot));
        scene.idle(90);
    }

    /*
     * ============ 4) 随机刻还加速什么 ============
     * 蓝图沿用 accelerator/electric.nbt：通电后小麦与母岩一起推进，说明加速的不止晶体。
     */
    public static void randomTickTargets(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("accelerator_random_ticks", "What Random Ticks Drive");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos acc = util.grid().at(2, 1, 2);
        BlockPos ironBudding = util.grid().at(2, 1, 1);
        BlockPos ironBud = util.grid().at(2, 2, 1);
        BlockPos roseBudding = util.grid().at(2, 1, 3);
        BlockPos roseSpot = util.grid().at(2, 2, 3);
        BlockPos amethyst = util.grid().at(3, 1, 2);
        BlockPos amethystSpot = util.grid().at(3, 2, 2);
        BlockPos wheat = util.grid().at(1, 1, 2);

        scene.world().showSection(util.select().position(acc), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().fromTo(ironBudding, ironBud), Direction.DOWN);
        scene.idle(3);
        scene.world().showSection(util.select().fromTo(roseBudding, roseSpot), Direction.DOWN);
        scene.idle(3);
        scene.world().showSection(util.select().fromTo(amethyst, amethystSpot), Direction.DOWN);
        scene.idle(3);
        scene.world().showSection(util.select().position(wheat), Direction.DOWN);
        scene.idle(12);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Random ticks drive far more than crystal growth. Crops, saplings and every other random-tick mechanic are accelerated as well.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(wheat));
        scene.idle(110);

        scene.world().cycleBlockProperty(acc, AcceleratorBlock.POWERED);
        scene.effects().indicateSuccess(acc);
        scene.idle(10);
        advanceAll(scene, wheatCrop(wheat), familyCrop(ironBud, BuddingFamilies.RAW_IRON),
                familyCrop(roseSpot, BuddingFamilies.ROSE_QUARTZ), amethystCrop(amethystSpot));

        // 圈出同时被催的几块，收尾不再写字
        scene.idle(15);
        scene.overlay().showOutlineWithText(util.select()
                        .position(wheat)
                        .add(util.select().position(ironBudding))
                        .add(util.select().position(amethyst)), 100)
                .text("All six sides are ticked at once, so one Accelerator serves everything around it.")
                .attachKeyFrame()
                .placeNearTarget();
        scene.idle(110);
    }

    // ---- 工具方法 ----

    /** 一个会生长的目标：它自己的方块状态进阶序列（不同家族、小麦各不相同） */
    private record Crop(BlockPos pos, BlockState[] stages) {}

    private static Crop wheatCrop(BlockPos pos) {
        return new Crop(pos, new BlockState[]{wheatState(2), wheatState(5), wheatState(7)});
    }

    private static Crop amethystCrop(BlockPos pos) {
        return new Crop(pos, new BlockState[]{
                budState(Blocks.MEDIUM_AMETHYST_BUD), budState(Blocks.LARGE_AMETHYST_BUD), budState(Blocks.AMETHYST_CLUSTER)});
    }

    /** 本模组母岩家族：中芽 → 大芽 → 晶簇 */
    private static Crop familyCrop(BlockPos pos, BuddingFamilies.RegisteredFamily family) {
        return new Crop(pos, new BlockState[]{
                budState(family.mediumBud().get()), budState(family.largeBud().get()), budState(family.cluster().get())});
    }

    /**
     * 所有目标<b>同一刻</b>各推进一级，走完自己的序列 —— 「六个面同时吃随机刻」的那一下。
     * 序列长短不一时按最长的走，短的走完就停。
     */
    private static void advanceAll(SceneBuilder scene, Crop... crops) {
        int steps = 0;
        for (Crop c : crops)
            steps = Math.max(steps, c.stages().length);
        for (int i = 0; i < steps; i++) {
            for (Crop c : crops)
                if (i < c.stages().length)
                    scene.world().setBlock(c.pos(), c.stages()[i], false);
            scene.idle(5);
        }
    }

    private static BlockState budState(Block block) {
        return budState(block, Direction.UP);
    }

    private static BlockState budState(Block block, Direction facing) {
        return block.defaultBlockState().setValue(AmethystClusterBlock.FACING, facing);
    }

    private static BlockState wheatState(int age) {
        return Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7, age);
    }

    /** 生长格上连走三级：小芽 → 中芽 → 大芽 → 晶簇 */
    private static void growUp(SceneBuilder scene, BlockPos spot) {
        scene.world().setBlock(spot, budState(Blocks.SMALL_AMETHYST_BUD), false);
        scene.idle(4);
        scene.world().setBlock(spot, budState(Blocks.MEDIUM_AMETHYST_BUD), false);
        scene.idle(4);
        scene.world().setBlock(spot, budState(Blocks.LARGE_AMETHYST_BUD), false);
        scene.idle(4);
        scene.world().setBlock(spot, budState(Blocks.AMETHYST_CLUSTER), false);
        scene.idle(10);
    }

}
