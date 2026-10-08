package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.block.SmartTemperatureChamberBlock;
import com.minecart.yunxian.registry.ModItems;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.content.kinetics.mixer.MechanicalMixerBlockEntity;
import com.simibubi.create.content.kinetics.steamEngine.PoweredShaftBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 智能温控室的三条分镜，各用各的蓝图（机器的构图差别太大，塞不进同一张 5x5x5）：
 * <ul>
 *   <li>{@code temperature_chamber/heating.nbt} —— 温控室 / 工作盆 / 搅拌机叠在 (2,·,2)，
 *       搅拌机侧面咬一个小齿轮、齿轮顶上压一台马达；其中搅拌机离工作盆两格（Create 的硬性规矩，
 *       见蓝图那边的注释），所以这张是 5x6x5</li>
 *   <li>{@code temperature_chamber/sharing.nbt} —— 2x2 四台温控室（(1..2,1,1..2)），四台拼满外接矩形
 *       ——料只倒进 (1,1,1) 那一台，另外三台空着，这是"整组看总量"的正确起手式</li>
 *   <li>{@code temperature_chamber/boiler.nbt} —— 一台完整的锅炉（2x2 罐体 + 蒸汽引擎 + 水泵与管道 + 水源），
 *       下面压一组 2x2 温控室</li>
 * </ul>
 * 三条都靠"翻档位 + 给动力"演：温控室的档位就是它<b>在不在烧</b>的投影，思索世界里方块实体不跑服务端
 * 逻辑，所以 {@link #setBurning} 直接改方块状态，与 Create 自己演烈焰人燃烧室是同一个写法。
 * <p>
 * <b>动力要一格一格给</b>：{@code scene.world().setKineticSpeed(...)} 只是把选中那几格方块实体 NBT 里的
 * {@code Speed} 改掉，<b>不会</b>沿动力网络传播（思索世界里方块实体虽然有 ticker，但没人替我们重算网络）。
 * 所以马达与搅拌机要一起选中，不能只提马达。
 * <p>
 * 共享那一条特意只把料倒进<b>其中一台</b>，再让四台一起亮——这是"共享容量"最直观的演示；
 * 边框连成一片是连接材质自己会做的事，不用脚本管。
 */
public class TemperatureChamberScenes {


    /*
     * ============ 1) 摆在工作盆下面，烧料就有热 ============
     * 演出：温控室就位 → 工作盆扣上来 → 搅拌机与那套传动（侧面小齿轮 + 顶上马达）压上来
     * → 倒进可燃冰沙、整台亮起 → 搅拌机搅两轮 → 料烧完熄灭、机器停下。
     */
    public static void chamberHeating(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("temperature_chamber_heating", "Powering a Basin");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos chamber = util.grid().at(2, 1, 2);
        BlockPos basin = util.grid().at(2, 2, 2);
        // 搅拌机离工作盆两格，中间那格必须是空气；它只能靠侧面那只小齿轮咬住（见蓝图那边的注释），
        // 齿轮顶上再压一台马达（facing=down 就是轴朝下）
        BlockPos mixer = util.grid().at(2, 4, 2);
        BlockPos cog = util.grid().at(3, 4, 2);
        BlockPos motor = util.grid().at(3, 5, 2);
        // 传动这一摞（搅拌机 + 小齿轮 + 马达；中间那格空气无所谓）：刹车/静止都一起做
        Selection drive = util.select().fromTo(mixer, motor);

        scene.world().showSection(util.select().position(chamber), Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(util.select().position(basin), Direction.DOWN);
        scene.idle(15);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("The Smart Temperature Chamber sets the Basin to the temperature its recipe asks for, heating or cooling as needed.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(chamber));
        scene.idle(80);

        scene.world().showSection(drive, Direction.DOWN);
        // 先把转速钉死在 0：蓝图里 Speed 本来就是 0，但马达读档时有可能按自己的 ScrollValue
        // 生成转速，而这条分镜要的是"先停着、点着之后才转"
        scene.world().setKineticSpeed(drive, 0f);
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

        // 转起来。小齿轮与搅拌机是"咬合"，转向必须相反（Create 那边给的倍率就是 -1）；
        // 马达与齿轮之间是轴向连接，与齿轮同向，而朝下的马达实际转速是 -ScrollValue，
        // 所以蓝图里 ScrollValue 写的 32 正好对上这里的 -32。
        scene.world().setKineticSpeed(util.select().position(mixer), 32f);
        scene.world().setKineticSpeed(util.select().fromTo(cog, motor), -32f);
        startMixing(scene, mixer);
        scene.idle(45);
        startMixing(scene, mixer);
        scene.idle(45);
        scene.world().setKineticSpeed(drive, 0f);
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
     * ============ 2) 拼在一起共享容量 ============
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
                .text("Chambers placed together share one capacity, and it grows with the size of the group.")
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

        // 指这四台的**交角**：边框消失这件事正是发生在它们彼此相接的那两条缝上
        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("The frames between them fall away, so a block of Chambers reads as a single machine.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(front).add(0.5, 0.5, 0.5));
        scene.idle(95);
    }

    /*
     * ============ 3) 当蒸汽锅炉的热源 ============
     * 演出：整套锅炉就位（罐体 / 引擎与动力轴 / 水泵与管道 / 水源）→ 通上水、泵转起来
     * → 点亮温控室：锅炉这才有热，仪表盘抬起来、引擎跟着转 → 烧完熄灭，引擎停下。
     * <p>
     * 为什么非要把一整台锅炉摆齐：Create 的锅炉要<b>罐体 ≥4 格 + 贴着引擎 + 管道持续通水</b>
     * 三样齐全，少一样它就只是个储罐、什么都不会发生（单摆一个储罐连仪表盘都不画）。
     * 锅炉怎么搭是 Create 自己那条思索的事，这条分镜只讲一件事，所以全程只有一句文字。
     * <p>
     * 引擎的转速是脚本写上去的：思索世界不跑服务端逻辑，锅炉算不出效率
     * （{@code GeneratingKineticBlockEntity#updateGeneratedRotation} 在客户端直接 return），
     * 所以给动力轴写的就是"真算出来会是那个数"——四格罐体把热量封顶 1 级、水够、一台引擎，
     * 效率正好是 1，即 16 x 4 = 64。
     */
    public static void chamberBoiler(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("temperature_chamber_boiler", "A Steam Boiler's Heat Source");
        scene.configureBasePlate(0, 0, 7);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        Selection chambers = util.select().fromTo(1, 1, 1, 2, 1, 2);
        Selection boilerTanks = util.select().fromTo(1, 2, 1, 2, 2, 2);
        Selection engineTrain = util.select().fromTo(3, 2, 1, 5, 2, 1);
        Selection supply = util.select()
                .fromTo(4, 1, 5, 3, 2, 5)                     // 水里的立管、上面那截、带动齿轮的马达
                .add(util.select().fromTo(4, 2, 2, 4, 2, 4))  // 三截管道与水泵
                .add(util.select().position(3, 2, 4))         // 咬住水泵的小齿轮
                .add(util.select().position(3, 2, 2))         // 拐进罐体的最后一段
                .add(util.select().fromTo(4, 0, 5, 5, 0, 6)); // 水池
        Selection boilerControl = util.select().position(1, 2, 1);
        Selection shaft = util.select().position(5, 2, 1);
        Vec3 chamberGroupCentre = util.vector().centerOf(util.grid().at(1, 1, 1)).add(0.5, 0, 0.5);

        scene.world().showSection(chambers, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(boilerTanks, Direction.DOWN);
        scene.idle(10);
        scene.world().showSection(engineTrain, Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(supply, Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(75)
                .attachKeyFrame()
                .text("The Smart Temperature Chamber can also serve as a Steam Boiler's heat source.")
                .placeNearTarget()
                .pointAt(chamberGroupCentre);
        scene.idle(85);

        // 水先通上：泵与它的动力转起来（泵与齿轮是咬合，转向相反；齿轮与马达同轴，同向）。
        // 这一步也要告诉锅炉"水正在进来"：Supply 就是进水口的流速
        scene.world().setKineticSpeed(util.select().position(4, 2, 4), 64f);
        scene.world().setKineticSpeed(util.select().fromTo(3, 2, 4, 3, 2, 5), -64f);
        scene.world().propagatePipeChange(util.grid().at(4, 2, 4));
        setBoilerSupply(scene, boilerControl, 20f);
        scene.idle(25);

        // 点着温控室：锅炉这才有热 —— 仪表盘抬起来、引擎跟着转
        setBurning(scene, chambers, true);
        setBoilerHeat(scene, boilerControl, 2);
        setEnginePower(scene, shaft, 1f);
        scene.world().setKineticSpeed(shaft, 64f);
        scene.effects().emitParticles(
                chamberGroupCentre,
                scene.effects().simpleParticleEmitter(ParticleTypes.LARGE_SMOKE, new Vec3(0, 0.05, 0)),
                2, 60);
        scene.idle(80);

        // 烧完就停：引擎与仪表盘一起落回去
        setBurning(scene, chambers, false);
        setBoilerHeat(scene, boilerControl, 0);
        setEnginePower(scene, shaft, 0f);
        scene.world().setKineticSpeed(shaft, 0f);
        scene.idle(70);
    }

    /**
     * 改写锅炉控制器 NBT 里 {@code Boiler} 子标签的某个数。
     * <p>
     * 思索里没有服务端替我们算这些：真游戏里 {@code ActiveHeat} / {@code Supply} 由服务端
     * 数出来再同步给客户端，这里就按"那一刻真算出来是多少"写进去，客户端的仪表盘才会跟着动
     * （{@code BoilerData#read} 里那句 {@code gauge.chase}）。
     * <p>
     * 两个方法分开写是因为类型不同：{@code ActiveHeat} 是 int，{@code Supply} 是 float，
     * 写错类型读端会静静拿到 0。
     */
    private static void setBoilerHeat(CreateSceneBuilder scene, Selection boiler, int activeHeat) {
        scene.world().modifyBlockEntityNBT(boiler, FluidTankBlockEntity.class,
                nbt -> nbt.getCompound("Boiler").putInt("ActiveHeat", activeHeat));
    }

    /** 锅炉的进水流量（mB/tick），见 {@link #setBoilerHeat} */
    private static void setBoilerSupply(CreateSceneBuilder scene, Selection boiler, float supply) {
        scene.world().modifyBlockEntityNBT(boiler, FluidTankBlockEntity.class,
                nbt -> nbt.getCompound("Boiler").putFloat("Supply", supply));
    }

    /**
     * 动力轴上记的效率（{@code EnginePower}）：它只是给人看的读数，真正让画面转起来的是
     * 脚本写到 {@code Speed} 上的转速。跟着锅炉一起 0 / 1 地改，是为了 NBT 与前因后果对得上。
     */
    private static void setEnginePower(CreateSceneBuilder scene, Selection shaft, float power) {
        scene.world().modifyBlockEntityNBT(shaft, PoweredShaftBlockEntity.class,
                nbt -> nbt.putFloat("EnginePower", power));
    }

    /**
     * 让搅拌机搅一轮：它演上下搅拌靠的是方块实体上的 {@code Running}/{@code Ticks}
     * （{@code startProcessingBasin} 就是把这两位摆成"刚开始搅"），只给转速的话它只会干转。
     * <p>
     * 一轮 40 tick 自己就结束（{@code tick()} 里 {@code runningTicks >= 40} 会把 {@code running} 放回 false），
     * 所以想让画面一直在搅就得隔一轮喊一次——中途喊是白喊，方法自己会跳过
     * （{@code if (running && runningTicks <= 20) return;}）。
     */
    private static void startMixing(CreateSceneBuilder scene, BlockPos mixer) {
        scene.world().modifyBlockEntity(mixer, MechanicalMixerBlockEntity.class,
                MechanicalMixerBlockEntity::startProcessingBasin);
    }

    /**
     * 翻档位：温控室的 {@code blaze} 就是"在不在烧"的投影，烧着是 {@code SEETHING}、没料是 {@code NONE}。
     * <p>
     * 直接改方块状态、不去塞方块实体：思索世界是纯客户端，方块实体的服务端 tick 不跑
     * （{@code SmartTemperatureChamberBlock#getTicker} 在客户端直接返回 null），
     * 罐里的量不会自己把状态顶上去（Create 自己演烈焰人燃烧室也是直接改这一位）。
     * 亮度与外观都挂在这一位上，所以翻它就够了。
     */
    private static void setBurning(CreateSceneBuilder scene, BlockPos pos, boolean burning) {
        scene.world().modifyBlock(pos,
                state -> state.setValue(SmartTemperatureChamberBlock.HEAT_LEVEL,
                        burning ? HeatLevel.SEETHING : HeatLevel.NONE),
                false);
    }

    /** 翻一整组温控室的档位（锅炉那条分镜下面摆的是 2x2 一组，四台一起点） */
    private static void setBurning(CreateSceneBuilder scene, Selection chambers, boolean burning) {
        scene.world().modifyBlocks(chambers,
                state -> state.setValue(SmartTemperatureChamberBlock.HEAT_LEVEL,
                        burning ? HeatLevel.SEETHING : HeatLevel.NONE),
                false);
    }
}
