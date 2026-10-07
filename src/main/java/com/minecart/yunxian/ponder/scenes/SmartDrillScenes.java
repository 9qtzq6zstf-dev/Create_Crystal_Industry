package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.blockentity.SmartDrillBlockEntity;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * 智能钻头的四条分镜：速度、切换模式、精准采集母岩、受红石控制。四条共用同一张蓝图
 * {@code smart_drill/smart_drill.nbt}（5×5×5）：
 * <ul>
 *   <li>(1,1,2) 智能钻头（朝北），背面 (1,1,3) 链式传动箱</li>
 *   <li>(3,1,2) 普通机械钻头（朝北），背面 (3,1,3) 链式传动箱 → (3,1,4) 小齿轮 → (2,2,4) 大齿轮</li>
 *   <li>(1,0,3)/(2,0,3)/(3,0,3) 底层取力回路，随基座层 (layer 0) 显示</li>
 *   <li>演示位（空气格）(1,1,1)/(3,1,1) 由脚本放置方块</li>
 * </ul>
 * 四条分镜讲同一台机器，靠不同的演示物（石头 / 母岩 / 红石块）与镜头错开构图。
 * 挖掘、模式切换、掉落一律脚本演绎；碎裂用 destroyBlock + 一簇 BLOCK 粒子代替裂纹动画。
 * 红石那一条额外摆两块红石块演示「锁」与「前方例外」，红石锁本身由
 * {@code toggleRedstonePower} 直接翻转方块自己的 {@code POWERED}（与 Create 的思索同款写法）。
 */
public class SmartDrillScenes {

    /*
     * ============ 1) 普通采集比普通钻头快一倍 ============
     * 演出：两台钻头同刻开挖同一块石头，智能钻头先挖穿 —— 这正是它两倍速的意思。
     */
    public static void smartDrillSpeed(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("smart_drill_speed", "Normal Harvesting");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos smart = util.grid().at(1, 1, 2);
        BlockPos vanilla = util.grid().at(3, 1, 2);
        BlockPos smartBack = util.grid().at(1, 1, 3);
        BlockPos vanillaBack = util.grid().at(3, 1, 3);
        BlockPos cog = util.grid().at(3, 1, 4);
        BlockPos largeCog = util.grid().at(2, 2, 4);
        BlockPos spotSmart = util.grid().at(1, 1, 1);
        BlockPos spotVanilla = util.grid().at(3, 1, 1);

        // 传动部分先就位，两台钻头随后各自出场
        scene.world().showSection(util.select().position(smartBack).add(util.select().position(vanillaBack)), Direction.NORTH);
        scene.idle(4);
        scene.world().showSection(util.select().position(cog), Direction.NORTH);
        scene.idle(4);
        scene.world().showSection(util.select().position(largeCog), Direction.DOWN);
        scene.idle(8);
        scene.world().showSection(util.select().fromTo(spotSmart, smart), Direction.DOWN);
        scene.idle(8);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("The Smart Drill is a Mechanical Drill with two harvesting modes. This scene covers Normal Harvesting.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(smart));
        scene.idle(20);
        scene.world().showSection(util.select().fromTo(spotVanilla, vanilla), Direction.DOWN);
        scene.idle(90);

        // 通电：整机转起来
        powerUp(scene, util, largeCog, cog, smartBack, vanillaBack, smart, vanilla);

        // 同刻开挖同一块石头
        scene.world().setBlock(spotSmart, Blocks.STONE.defaultBlockState(), false);
        scene.world().setBlock(spotVanilla, Blocks.STONE.defaultBlockState(), false);
        scene.idle(10);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("In Normal Harvesting the Smart Drill mines twice as fast as a regular Mechanical Drill.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(spotSmart));
        scene.idle(15);
        destroyWithDebris(scene, util, spotSmart, Blocks.STONE.defaultBlockState());
        scene.idle(20);
        destroyWithDebris(scene, util, spotVanilla, Blocks.STONE.defaultBlockState());
        scene.idle(80);
    }

    /*
     * ============ 2) 侧面的设置槽切换模式 ============
     * 演出：手势右键侧槽 → 槽位高亮 → 写入 Mode 让槽位图标切换 → 再挖一次，速度与普通钻头持平。
     */
    public static void smartDrillModes(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("smart_drill_modes", "Switching Modes");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos smart = util.grid().at(1, 1, 2);
        BlockPos vanilla = util.grid().at(3, 1, 2);
        BlockPos smartBack = util.grid().at(1, 1, 3);
        BlockPos vanillaBack = util.grid().at(3, 1, 3);
        BlockPos cog = util.grid().at(3, 1, 4);
        BlockPos largeCog = util.grid().at(2, 2, 4);
        BlockPos spotSmart = util.grid().at(1, 1, 1);
        BlockPos spotVanilla = util.grid().at(3, 1, 1);

        // 朝向为北时四个垂直于轴向的面都能配置，这里指朝西那一面
        Vec3 slot = util.vector().centerOf(smart).add(-0.47, 0.0, 0.19);

        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(largeCog), Direction.DOWN);
        scene.idle(10);
        powerUp(scene, util, largeCog, cog, smartBack, vanillaBack, smart, vanilla);
        scene.idle(10);

        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("The setting slot on its side switches harvesting modes.")
                .placeNearTarget()
                .pointAt(slot);
        scene.overlay().showFilterSlotInput(slot, 55);
        scene.idle(20);
        scene.overlay().showControls(slot, Pointing.DOWN, 45)
                .rightClick();
        scene.idle(45);

        // 切到精准采集：只改槽里的模式值，槽位图标与行为随之改变
        scene.world().modifyBlockEntityNBT(util.select().position(smart), SmartDrillBlockEntity.class,
                nbt -> nbt.putInt("Mode", SmartDrillBlockEntity.DrillMode.PRECISE.ordinal()));
        scene.effects().indicateSuccess(smart);
        scene.idle(10);

        scene.world().setBlock(spotSmart, Blocks.STONE.defaultBlockState(), false);
        scene.world().setBlock(spotVanilla, Blocks.STONE.defaultBlockState(), false);
        scene.idle(25);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("Silk Touch Harvesting mines at the speed of a regular drill: both drills break the block at the same moment.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(spotSmart));
        scene.idle(15);
        destroyWithDebris(scene, util, spotSmart, Blocks.STONE.defaultBlockState());
        destroyWithDebris(scene, util, spotVanilla, Blocks.STONE.defaultBlockState());
        scene.idle(100);
    }

    /*
     * ============ 3) 精准采集把母岩整块采下 ============
     * 演出：普通钻头把母岩打碎（碎屑散去、什么也不掉）↔ 智能钻头采下母岩方块本身（掉落物飞出）。
     */
    public static void smartDrillSilkTouch(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("smart_drill_silk_touch", "Harvesting Budding Blocks");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos smart = util.grid().at(1, 1, 2);
        BlockPos vanilla = util.grid().at(3, 1, 2);
        BlockPos smartBack = util.grid().at(1, 1, 3);
        BlockPos vanillaBack = util.grid().at(3, 1, 3);
        BlockPos cog = util.grid().at(3, 1, 4);
        BlockPos largeCog = util.grid().at(2, 2, 4);
        BlockPos spotSmart = util.grid().at(1, 1, 1);
        BlockPos spotVanilla = util.grid().at(3, 1, 1);

        BlockState budding = BuddingFamilies.ROSE_QUARTZ.budding().get().defaultBlockState();

        scene.world().showSection(util.select().layer(1), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(largeCog), Direction.DOWN);
        scene.idle(10);
        powerUp(scene, util, largeCog, cog, smartBack, vanillaBack, smart, vanilla);
        scene.idle(10);

        scene.world().setBlock(spotVanilla, budding, false);
        scene.idle(10);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Budding Blocks are a special case: a regular drill shatters them, and the block itself cannot be collected.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(spotVanilla));
        scene.idle(20);
        destroyWithDebris(scene, util, spotVanilla, budding);
        scene.idle(90);

        // 换成精准采集
        scene.world().modifyBlockEntityNBT(util.select().position(smart), SmartDrillBlockEntity.class,
                nbt -> nbt.putInt("Mode", SmartDrillBlockEntity.DrillMode.PRECISE.ordinal()));
        scene.effects().indicateSuccess(smart);
        scene.world().setBlock(spotSmart, budding, false);
        scene.idle(15);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("In Silk Touch Harvesting the Smart Drill collects the Budding Block itself.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(spotSmart));
        scene.idle(15);
        destroyWithDebris(scene, util, spotSmart, budding);
        ElementLink<EntityElement> drop = scene.world().createItemEntity(util.vector().centerOf(spotSmart), Vec3.ZERO,
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.budding().get().asItem()));
        scene.effects().indicateSuccess(spotSmart);
        scene.idle(100);
    }

    /*
     * ============ 4) 受红石控制，但正前方例外 ============
     * 演出：头顶摆一块红石块 → 钻头停转，前方石头纹丝不动；撤掉后立刻接着挖。
     * 再把红石块摆到它正对的那一面 —— 那是唯一「不听红石」的方向，钻头照挖不误。
     */
    public static void smartDrillRedstone(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("smart_drill_redstone", "Redstone Control");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos smart = util.grid().at(1, 1, 2);
        BlockPos vanilla = util.grid().at(3, 1, 2);
        BlockPos smartBack = util.grid().at(1, 1, 3);
        BlockPos vanillaBack = util.grid().at(3, 1, 3);
        BlockPos cog = util.grid().at(3, 1, 4);
        BlockPos largeCog = util.grid().at(2, 2, 4);
        BlockPos spotSmart = util.grid().at(1, 1, 1);     // 正前方：钻头采的那一格
        BlockPos topSide = util.grid().at(1, 2, 2);       // 头顶：拿它演示「信号锁住机器」

        // 头顶那一格要一并显示出来：ponder 只渲染「已被 showSection 覆盖」的格子，
        // 没显示过的空位里 setBlock 放下的方块根本不会画出来（红石块会像没放一样）
        scene.world().showSection(util.select().layer(1).add(util.select().position(topSide)), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(largeCog), Direction.DOWN);
        scene.idle(10);
        powerUp(scene, util, largeCog, cog, smartBack, vanillaBack, smart, vanilla);
        scene.idle(10);

        // 头顶落下一块红石块：钻头停转
        scene.world().setBlock(topSide, Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
        setLocked(scene, util, smart, true);
        scene.effects().indicateRedstone(topSide);
        scene.idle(5);

        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Redstone Power locks the Smart Drill: it stops spinning and stops harvesting, and the block in front of it is left untouched.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(smart));
        // 文字展示期间正前方摆上石头：锁定期间它一动不动
        scene.world().setBlock(spotSmart, Blocks.STONE.defaultBlockState(), false);
        scene.idle(130);

        // 撤掉红石块：接着挖
        scene.world().destroyBlock(topSide);
        setLocked(scene, util, smart, false);
        scene.effects().indicateSuccess(smart);
        scene.idle(5);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Remove the signal and the drill picks up right where it left off.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(spotSmart));
        destroyWithDebris(scene, util, spotSmart, Blocks.STONE.defaultBlockState());
        scene.idle(130);

        // 正前方换成红石块：唯一不受影响的一面
        scene.world().setBlock(spotSmart, Blocks.REDSTONE_BLOCK.defaultBlockState(), false);
        scene.effects().indicateRedstone(spotSmart);
        scene.idle(5);
        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("The face it points at is the one exception: redstone placed in front does not lock it, and the drill keeps working.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(spotSmart));
        // 让这块红石块多停一会儿再被挖掉：它只活了一瞬的话观众根本来不及看清
        scene.idle(110);
        destroyWithDebris(scene, util, spotSmart, Blocks.REDSTONE_BLOCK.defaultBlockState());
        scene.idle(60);
    }

    // ---- 工具方法 ----

    /**
     * 把智能钻头切到「被红石锁住 / 解锁」。两半都要动，只做一半钻头不会停转：
     * <ul>
     *   <li>{@code toggleRedstonePower} 换的是方块状态 {@code POWERED}——模型因此切成
     *       {@code block_powered} 那一款，这是观众看得见的外观变化；</li>
     *   <li><b>但光有它不够</b>：{@code BlockEntity#getBlockState()} 返回的是方块实体里
     *       <b>缓存</b>的那份状态，而 ponder 的 {@code SchematicLevel#setBlock} 只更新自己的方块表、
     *       从不调用 {@code BlockEntity#setBlockState}（原版是 {@code LevelChunk} 那条路在同步它）。
     *       于是 {@code SmartDrillBlockEntity#isRedstoneLocked()} 读到的还是放置时的
     *       {@code powered=false}，转头照转 —— 文本说「停止旋转」而动画不停，就是这么来的。</li>
     *   <li>这里补上服务端平时推的那份锁定标志（{@code SyncedBlocked}，见
     *       {@code SmartDrillBlockEntity} 的 read/write），{@code getSpeed()} 才会真的汇报 0：
     *       头部冻结、后方传动杆照转（它走 {@code getTrueSpeed()}），与游戏里锁定时一模一样。</li>
     * </ul>
     */
    private static void setLocked(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos smart, boolean locked) {
        scene.world().toggleRedstonePower(util.select().position(smart));
        scene.world().modifyBlockEntityNBT(util.select().position(smart), SmartDrillBlockEntity.class,
                nbt -> nbt.putBoolean("SyncedBlocked", locked));
    }

    /** 大齿轮为动力输入端 16 rpm，其余按啮合与链传动取值 */
    private static void powerUp(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos largeCog, BlockPos cog,
                                BlockPos smartBack, BlockPos vanillaBack, BlockPos smart, BlockPos vanilla) {
        scene.world().setKineticSpeed(util.select().position(largeCog), 16f);
        scene.world().setKineticSpeed(util.select()
                .position(cog)
                .add(util.select().position(vanillaBack))
                .add(util.select().position(vanilla))
                .add(util.select().position(smartBack))
                .add(util.select().position(smart))
                .add(util.select().fromTo(util.grid().at(1, 0, 3), util.grid().at(3, 0, 3))), -32f);
        scene.effects().indicateSuccess(smart);
        scene.effects().indicateSuccess(vanilla);
        scene.effects().rotationDirectionIndicator(largeCog);
        scene.idle(10);
    }

    /** 破坏方块并喷出一小簇该方块的碎屑（替代原版挖掘裂纹动画） */
    private static void destroyWithDebris(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos pos, BlockState state) {
        scene.world().destroyBlock(pos);
        scene.effects().emitParticles(
                util.vector().centerOf(pos),
                scene.effects().particleEmitterWithinBlockSpace(
                        new BlockParticleOption(ParticleTypes.BLOCK, state),
                        Vec3.ZERO),
                8, 2);
    }
}
