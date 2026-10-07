package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.block.SmartTemperatureChamberBlock;
import com.minecart.yunxian.registry.ModItems;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 智能温控室的三条分镜，各用各的蓝图（机器的构图差别太大，塞不进同一张 5x5x5）：
 * <ul>
 *   <li>{@code temperature_chamber/heating.nbt} —— 温控室 / 工作盆 / 搅拌机 / 马达，自上而下叠在 (2,·,2)</li>
 *   <li>{@code temperature_chamber/sharing.nbt} —— 2x2 四台温控室（(1..2,1,1..2)），四台拼满外接矩形</li>
 *   <li>{@code temperature_chamber/boiler.nbt} —— 温控室上面顶一只储罐</li>
 * </ul>
 * 三条都靠"翻档位 + 给动力"演：温控室的档位就是它<b>在不在烧</b>的投影，思索世界里方块实体不跑服务端
 * 逻辑，所以 {@link #setBurning} 直接改方块状态，与 Create 自己演烈焰人燃烧室是同一个写法。
 * <p>
 * 共享那一条特意只把料倒进<b>其中一台</b>，再让四台一起亮——这是"共享容量"最直观的演示；
 * 边框连成一片是连接材质自己会做的事，不用脚本管。
 */
public class TemperatureChamberScenes {


    /*
     * ============ 1) 摆在工作盆下面，烧料就有热 ============
     * 演出：温控室就位 → 工作盆与搅拌机叠上来 → 倒进可燃冰沙、整台亮起 → 搅拌机转起来。
     */
    public static void chamberHeating(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("temperature_chamber_heating", "Powering a Basin");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos chamber = util.grid().at(2, 1, 2);
        BlockPos basin = util.grid().at(2, 2, 2);
        BlockPos mixer = util.grid().at(2, 3, 2);
        BlockPos motor = util.grid().at(2, 4, 2);

        scene.world().showSection(util.select().position(chamber), Direction.DOWN);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("The Smart Temperature Chamber heats the Basin placed directly above it.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(chamber));
        scene.idle(80);

        scene.world().showSection(util.select().fromTo(basin, motor), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("It runs on Flammable Ice Slurry, poured in by bucket or fed by pipe.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(chamber));
        scene.idle(95);

        // 倒料：手势 + 桶，然后整台亮起来
        scene.overlay().showControls(util.vector().topOf(chamber), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(ModItems.FLAMMABLE_ICE_SLURRY_BUCKET.get()));
        scene.idle(10);
        setBurning(scene, chamber, true);
        scene.idle(5);
        scene.effects().emitParticles(
                util.vector().centerOf(chamber),
                scene.effects().simpleParticleEmitter(ParticleTypes.LARGE_SMOKE, new Vec3(0, 0.05, 0)),
                6, 1);
        scene.idle(15);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("While it burns, the Basin ignores what heat a recipe asks for and simply runs it.")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(basin, Direction.WEST));
        scene.idle(35);

        // 上面那台搅拌机转起来，盆里的配方就有动静了
        scene.world().setKineticSpeed(util.select().position(motor), 32f);
        scene.idle(110);
        scene.world().setKineticSpeed(util.select().position(motor), 0f);
        scene.idle(20);

        // 烧完就停：档位退回没燃料那一档
        setBurning(scene, chamber, false);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Once the Slurry runs out, the Chamber cools down and the Basin stops.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(chamber));
        scene.idle(90);
    }

    /*
     * ============ 2) 拼在一起的共享一只大罐 ============
     * 演出：四台就位 → 只往其中一台倒料 → 四台同一刻一起亮 → 边框已连成一片。
     */
    public static void chamberSharing(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("temperature_chamber_sharing", "Shared Capacity");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos front = util.grid().at(1, 1, 1);
        scene.world().showSection(util.select().fromTo(front, util.grid().at(2, 1, 2)), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("Chambers placed together share one tank. A full rectangle holds a thousand mB per Chamber.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(front));
        scene.idle(95);

        // 只往一台里倒
        scene.overlay().showControls(util.vector().topOf(front), Pointing.DOWN, 40)
                .rightClick()
                .withItem(new ItemStack(ModItems.FLAMMABLE_ICE_SLURRY_BUCKET.get()));
        scene.idle(10);
        setBurning(scene, front, true);
        scene.idle(25);

        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("Fuel poured into any one of them feeds the whole group, and they light together.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(util.grid().at(1, 1, 2)));
        scene.idle(15);
        // 整组一起亮
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                setBurning(scene, util.grid().at(x, 1, z), true);
            }
        }
        scene.idle(95);

        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("The frames between them fall away, so a block of Chambers reads as a single machine.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(util.grid().at(2, 2, 2)).add(0, -0.5, 0));
        scene.idle(95);
    }

    /*
     * ============ 3) 当蒸汽锅炉的热源 ============
     * 演出：储罐顶上来 → 点亮 → 罐里翻腾起来，蒸汽往上冒。
     */
    public static void chamberBoiler(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("temperature_chamber_boiler", "Heating a Steam Boiler");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos chamber = util.grid().at(2, 1, 2);
        BlockPos tank = util.grid().at(2, 2, 2);

        scene.world().showSection(util.select().position(chamber), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(75)
                .attachKeyFrame()
                .text("A Steam Boiler placed on top counts the Smart Temperature Chamber as its heat source.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(chamber));
        scene.idle(85);

        scene.world().showSection(util.select().position(tank), Direction.DOWN);
        scene.idle(20);
        scene.overlay().showText(80)
                .attachKeyFrame()
                .text("Water poured into the tank turns to steam while the Chamber burns.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(tank));
        scene.idle(90);

        setBurning(scene, chamber, true);
        scene.idle(10);
        scene.effects().emitParticles(
                util.vector().topOf(tank),
                scene.effects().simpleParticleEmitter(ParticleTypes.CLOUD, new Vec3(0, 0.08, 0)),
                12, 1);
        scene.idle(20);
        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("It supplies the same top heat a Blaze Burner would, and stops the moment the fuel does.")
                .placeNearTarget()
                .pointAt(util.vector().blockSurface(tank, Direction.WEST));
        scene.idle(100);

        setBurning(scene, chamber, false);
        scene.idle(80);
    }

    /**
     * 翻档位：温控室的 {@code blaze} 就是"在不在烧"的投影，烧着是 {@code SEETHING}、没料是 {@code NONE}。
     * <p>
     * 直接改方块状态、不去塞方块实体：思索世界是纯客户端，方块实体的服务端 tick 不跑，
     * 罐里的量不会自己把状态顶上去（Create 自己演烈焰人燃烧室也是直接改这一位）。
     * 亮度与外观都挂在这一位上，所以翻它就够了。
     */
    private static void setBurning(CreateSceneBuilder scene, BlockPos pos, boolean burning) {
        scene.world().modifyBlock(pos,
                state -> state.setValue(SmartTemperatureChamberBlock.HEAT_LEVEL,
                        burning ? HeatLevel.SEETHING : HeatLevel.NONE),
                false);
    }
}
