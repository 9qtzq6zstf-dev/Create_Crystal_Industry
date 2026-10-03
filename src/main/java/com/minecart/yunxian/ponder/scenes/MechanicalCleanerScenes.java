package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.behaviour.MechanicalCleanerFilterBehaviour;
import com.minecart.yunxian.blockentity.MechanicalCleanerBlockEntity;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 动力吸尘器的三条分镜：气流与收集、前方容器直取直送、配置项。三条共用同一张蓝图
 * {@code mechanical_cleaner/mechanical_cleaner.nbt}（5×5×5）：
 * <ul>
 *   <li>(2,1,3)          动力吸尘器（朝北，正面 = 北）</li>
 *   <li>(2,1,1)/(2,1,2)  双联箱子（位于正前方；开场隐藏，容器那一条才出场）</li>
 *   <li>(2,1,4)          小齿轮（轴 Z，接吸尘器背面）</li>
 *   <li>(4,1,4)          小齿轮（轴 Z）</li>
 *   <li>(3,2,4)          大齿轮（轴 Z，动力输入）</li>
 * </ul>
 * 气流、吸取、容器直取直送、模式翻转全是脚本演绎：物品飞行轨迹为近似值，可调参数集中在
 * 每段 createItemEntity 的坐标/速度与随后的 idle 上。
 */
public class MechanicalCleanerScenes {

    /*
     * ============ 1) 同一套气流，而且能收集 ============
     */
    public static void cleanerAirflow(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cleaner_airflow", "Airstream and Collection");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos cleaner = util.grid().at(2, 1, 3);
        BlockPos smallCogA = util.grid().at(2, 1, 4);
        BlockPos smallCogB = util.grid().at(4, 1, 4);
        BlockPos largeCog = util.grid().at(3, 2, 4);

        // 除箱子外的机械部件一起淡入
        scene.world().showSection(util.select().layersFrom(1)
                .substract(util.select().fromTo(util.grid().at(2, 1, 1), util.grid().at(2, 1, 2))), Direction.DOWN);
        scene.idle(15);
        powerUp(scene, util, cleaner, smallCogA, smallCogB, largeCog);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("The Mechanical Cleaner drives the same airstream as an Encased Fan.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(cleaner));
        scene.idle(20);

        // 气流粒子（纯视觉，可换粒子类型/数量）
        scene.effects().emitParticles(util.vector().centerOf(2, 1, 1),
                scene.effects().particleEmitterWithinBlockSpace(ParticleTypes.CLOUD, new Vec3(0, 0.01, 0.12)),
                2, 45);
        scene.idle(85);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Items caught in its stream, or simply lying in front of it, are sucked in and stored in its built-in inventory.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(20);

        // 两件物品一前一后画抛物线飞入机器前脸
        ElementLink<EntityElement> suckA = scene.world().createItemEntity(
                new Vec3(2.5, 1.2, 1.1), new Vec3(0, 0.22, 0.19),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(4);
        ElementLink<EntityElement> suckB = scene.world().createItemEntity(
                new Vec3(2.5, 1.2, 1.1), new Vec3(0, 0.22, 0.19),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(8);
        scene.world().modifyEntity(suckA, Entity::discard);
        scene.effects().indicateSuccess(cleaner);
        scene.idle(4);
        scene.world().modifyEntity(suckB, Entity::discard);
        scene.idle(85);
    }

    /*
     * ============ 2) 前方容器直取直送 ============
     */
    public static void cleanerContainer(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cleaner_container", "Containers in Front");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos cleaner = util.grid().at(2, 1, 3);
        BlockPos smallCogA = util.grid().at(2, 1, 4);
        BlockPos smallCogB = util.grid().at(4, 1, 4);
        BlockPos largeCog = util.grid().at(3, 2, 4);
        Selection chests = util.select().fromTo(util.grid().at(2, 1, 1), util.grid().at(2, 1, 2));

        scene.world().showSection(util.select().layersFrom(1).substract(chests), Direction.DOWN);
        scene.idle(15);
        powerUp(scene, util, cleaner, smallCogA, smallCogB, largeCog);

        scene.world().showSection(chests, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("A container placed directly in front is handled specially: the Cleaner moves items in and out of it directly, without dropping anything into the world.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(110);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("While collecting, it reaches into that container and pulls matching items into its own inventory.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(15);
        pullFromContainer(scene);

        // 翻转气流方向：从机器送回容器（NORMAL 才是吹气，默认的 REVERSED 是吸气）
        scene.world().modifyBlockEntityNBT(util.select().position(cleaner), MechanicalCleanerBlockEntity.class,
                nbt -> nbt.putInt("Direction", MechanicalCleanerFilterBehaviour.RotationDirection.NORMAL.ordinal()));
        scene.idle(35);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("While blowing, stored items are fed back into the container in front.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(15);
        pushToContainer(scene);
        scene.idle(60);
    }

    /*
     * ============ 3) 配置项在哪 ============
     * 演出：滑块与侧槽各高亮一次，再翻转一次气流方向让物品流向跟着翻 —— 说明配置是即时生效的。
     */
    public static void cleanerConfig(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("cleaner_config", "Configuring the Cleaner");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos cleaner = util.grid().at(2, 1, 3);
        BlockPos smallCogA = util.grid().at(2, 1, 4);
        BlockPos smallCogB = util.grid().at(4, 1, 4);
        BlockPos largeCog = util.grid().at(3, 2, 4);
        Selection chests = util.select().fromTo(util.grid().at(2, 1, 1), util.grid().at(2, 1, 2));

        scene.world().showSection(util.select().layersFrom(1).substract(chests), Direction.DOWN);
        scene.idle(15);
        powerUp(scene, util, cleaner, smallCogA, smallCogB, largeCog);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("Blowing or sucking, filters and transfer amounts are all configured through its interface and the side slot.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(cleaner));
        scene.overlay().showCenteredScrollInput(cleaner, Direction.WEST, 80);
        scene.idle(105);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("The side slot reverses the airstream, and the Cleaner starts moving items the other way.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(2, 1, 2));
        scene.idle(15);

        // 吸气：物品被吸走
        pullFromContainer(scene);
        scene.idle(20);

        // 吹气：物品被送回
        scene.world().modifyBlockEntityNBT(util.select().position(cleaner), MechanicalCleanerBlockEntity.class,
                nbt -> nbt.putInt("Direction", MechanicalCleanerFilterBehaviour.RotationDirection.NORMAL.ordinal()));
        scene.effects().indicateRedstone(cleaner);
        scene.idle(10);
        pushToContainer(scene);
        scene.idle(50);
    }

    // ---- 工具方法 ----

    private static void powerUp(CreateSceneBuilder scene, SceneBuildingUtil util, BlockPos cleaner,
                                BlockPos smallCogA, BlockPos smallCogB, BlockPos largeCog) {
        scene.world().setKineticSpeed(util.select().position(largeCog), 16f);
        scene.world().setKineticSpeed(util.select().position(smallCogA).add(util.select().position(smallCogB)), -32f);
        scene.world().setKineticSpeed(util.select().position(cleaner), -32f);
        scene.effects().indicateSuccess(cleaner);
        scene.effects().rotationDirectionIndicator(largeCog);
        scene.idle(15);
    }

    /** 从正前方的箱子里抽两件物品飞进吸尘器 */
    private static void pullFromContainer(CreateSceneBuilder scene) {
        ElementLink<EntityElement> pullA = scene.world().createItemEntity(
                new Vec3(2.5, 1.5, 2.98), new Vec3(0, 0.02, 0.055),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(4);
        ElementLink<EntityElement> pullB = scene.world().createItemEntity(
                new Vec3(2.5, 1.5, 2.98), new Vec3(0, 0.02, 0.055),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(6);
        scene.world().modifyEntity(pullA, Entity::discard);
        scene.idle(4);
        scene.world().modifyEntity(pullB, Entity::discard);
        scene.idle(40);
    }

    /** 库存里的物品被送回前方的箱子 */
    private static void pushToContainer(CreateSceneBuilder scene) {
        ElementLink<EntityElement> pushA = scene.world().createItemEntity(
                new Vec3(2.5, 1.5, 3.15), new Vec3(0, 0.02, -0.055),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(5);
        ElementLink<EntityElement> pushB = scene.world().createItemEntity(
                new Vec3(2.5, 1.5, 3.15), new Vec3(0, 0.02, -0.055),
                new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem()));
        scene.idle(8);
        scene.world().modifyEntity(pushA, Entity::discard);
        scene.idle(5);
        scene.world().modifyEntity(pushB, Entity::discard);
        scene.idle(40);
    }
}
