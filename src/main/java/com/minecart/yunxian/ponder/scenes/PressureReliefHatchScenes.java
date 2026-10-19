package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.block.SmartPressureReliefHatchBlock;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * 泄压舱口的三条分镜。蓝图都在 {@code pressure_relief_hatch/} 下，5×5×5、机器都摆在 y=1：
 * <ul>
 *   <li>{@code relief_hatch}：(3,1,2) 舱口（网罩朝北），(3,1,3) 箱子（舱口背后、也就是它的容器），
 *       (1,1,2) 风扇（自西向东横着扫过网罩），(0,1,2) 小齿轮、(0,2,2) 大齿轮带它转</li>
 *   <li>{@code relief_hatch_nozzle}：风扇挪到 (1,1,1) 朝东，(2,1,1) 是分散网，
 *       舱口仍在 (3,1,2)、箱子 (3,1,3) —— 落在网盖住的范围里，却偏在风源轴线以北</li>
 *   <li>{@code smart_relief_hatch}：与 {@code relief_hatch} 同布局，只把舱口换成智能版</li>
 * </ul>
 * 三条分镜都挂在两种舱口的思索索引下。风场、吐货、开门状态全都不在分镜里真跑——
 * 思索的世界是客户端侧的假世界，舱口方块实体的 {@code tick()} 在客户端直接返回，
 * 所以"吐出来"的动画全部用 {@code createItemEntity} 脚本演绎，风向只靠粒子示意。
 * <p>
 * <b>文本节奏</b>：每条 {@code showText(n)} 在 n tick 后消失，之后要再空 {@link #TEXT_GAP} tick
 * 才能出下一条，所以每条文本后面都用"若干动画 + 补白"凑够 {@code n + TEXT_GAP}。改动画时长时记得一起改补白。
 */
public class PressureReliefHatchScenes {

    /** 上一条文本消失后，到下一条出现之间的空档 */
    private static final int TEXT_GAP = 10;

    /*
     * ============ 1) 侧风吐货 ============
     */
    public static void reliefHatchVenting(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("relief_hatch_venting", "Venting Through the Hatch");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos hatch = util.grid().at(3, 1, 2);
        BlockPos chest = util.grid().at(3, 1, 3);
        Vec3 ventAt = util.vector().centerOf(hatch).add(0, 0.15, 0);

        BlockPos fan = util.grid().at(1, 1, 2);
        BlockPos cog = util.grid().at(0, 1, 2);
        BlockPos bigCog = util.grid().at(0, 2, 2);

        Selection fanRig = util.select().position(fan)
                .add(util.select().position(cog))
                .add(util.select().position(bigCog));

        // 先只露出舱口和它背后的箱子，风源留到后面再登场
        scene.world().showSection(util.select().layersFrom(1).substract(fanRig), Direction.DOWN);
        scene.idle(15);

        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("In an airstream, hatches release items from the container")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(chest));
        scene.idle(85 + TEXT_GAP);

        // 侧向风源：自西向东横着扫过网罩，气流与网罩朝向垂直
        scene.world().showSection(fanRig, Direction.DOWN);
        scene.idle(10);
        powerUp(scene, util.select().position(bigCog), util.select().position(cog),
                util.select().position(fan), hatch);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("Air flowing past the grille opens the hatch")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(hatch));
        scene.idle(20);
        ventItems(scene, ventAt, new Vec3(0.05, 0.24, 0), roseQuartz(), 3, 8);
        scene.effects().indicateSuccess(hatch);
        // 20 + 24 是 44，补到 95 + 10
        scene.idle(61);

        // 提速：同样的风场转得更快，吐得更密（只说"一批最多 16 个"，节奏靠动画演）
        scene.world().setKineticSpeed(util.select().position(bigCog), 64f);
        scene.world().setKineticSpeed(util.select().position(cog), -128f);
        scene.world().setKineticSpeed(util.select().position(fan), -128f);
        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("One batch holds up to 16 items")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(hatch));
        scene.idle(8);
        ventItems(scene, ventAt, new Vec3(0.05, 0.24, 0), roseQuartz(), 8, 3);
        // 8 + 24 是 32，补到 85 + 10
        scene.idle(63);

        // 过滤：侧面配置槽里设定的过滤只放匹配的物品出去
        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("Filters are set in its side slot")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(hatch));
        scene.idle(10);
        scene.overlay().showFilterSlotInput(util.vector().blockSurface(hatch, Direction.EAST), 85);
        ventItems(scene, ventAt, new Vec3(0.05, 0.24, 0), roseQuartz(), 2, 8);
        // 10 + 16 是 26，补到 95 + 10
        scene.idle(79);
    }

    /*
     * ============ 2) 分散网盖住的那片范围 ============
     */
    public static void reliefHatchNozzle(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("relief_hatch_nozzle", "Covered by a Nozzle");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos fan = util.grid().at(1, 1, 1);
        BlockPos cog = util.grid().at(0, 1, 1);
        BlockPos bigCog = util.grid().at(0, 2, 1);
        BlockPos nozzle = util.grid().at(2, 1, 1);

        // 舱口在分散网范围里，但偏在风扇轴线（z=1）以北一格
        BlockPos hatch = util.grid().at(3, 1, 2);
        BlockPos chest = util.grid().at(3, 1, 3);
        Vec3 ventAt = util.vector().centerOf(hatch).add(0, 0.15, 0);

        Selection fanRig = util.select().position(fan)
                .add(util.select().position(cog))
                .add(util.select().position(bigCog));
        Selection hatchRig = util.select().position(hatch).add(util.select().position(chest));

        scene.world().showSection(fanRig, Direction.DOWN);
        scene.idle(10);
        powerUp(scene, util.select().position(bigCog), util.select().position(cog),
                util.select().position(fan), nozzle);
        scene.world().showSection(util.select().position(nozzle), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("A Nozzle spreads the airstream into a wide field")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(nozzle));
        scene.idle(8);
        // 8 已用，补到 95 + 10
        scene.idle(97);

        scene.world().showSection(hatchRig, Direction.SOUTH);
        scene.idle(12);

        scene.overlay().showText(105)
                .attachKeyFrame()
                .text("Hatches in that field ignore the Fan's axis")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(hatch));
        scene.idle(8);
        ventItems(scene, ventAt, new Vec3(0, 0.24, 0.05), roseQuartz(), 4, 8);
        scene.effects().indicateSuccess(hatch);
        // 8 + 32 是 40，补到 105 + 10
        scene.idle(75);

        // 纯动画收尾：一直在吐
        ventItems(scene, ventAt, new Vec3(0, 0.24, 0.05), roseQuartz(), 3, 10);
        scene.idle(40);
    }

    /*
     * ============ 3) 智能版：数量滑块与红石锁 ============
     */
    public static void smartReliefHatch(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("smart_relief_hatch", "The Smart Pressure Relief Hatch");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos hatch = util.grid().at(3, 1, 2);
        BlockPos chest = util.grid().at(3, 1, 3);
        BlockPos redstone = util.grid().at(4, 1, 2);
        Vec3 ventAt = util.vector().centerOf(hatch).add(0, 0.15, 0);

        BlockPos fan = util.grid().at(1, 1, 2);
        BlockPos cog = util.grid().at(0, 1, 2);
        BlockPos bigCog = util.grid().at(0, 2, 2);

        Selection fanRig = util.select().position(fan)
                .add(util.select().position(cog))
                .add(util.select().position(bigCog));

        scene.world().showSection(util.select().layersFrom(1).substract(fanRig), Direction.DOWN);
        scene.idle(15);
        scene.world().showSection(fanRig, Direction.DOWN);
        scene.idle(10);
        powerUp(scene, util.select().position(bigCog), util.select().position(cog),
                util.select().position(fan), hatch);

        // 一组就是 64 个：先演一次整批释放
        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("It can release a full stack at once")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(chest));
        scene.idle(95 + TEXT_GAP);
        ventItems(scene, ventAt, new Vec3(0.05, 0.24, 0), roseQuartz(), 12, 2);
        scene.idle(30);

        // 数量滑块
        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("The batch size is set in the side slot")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(hatch));
        scene.overlay().showCenteredScrollInput(hatch, Direction.EAST, 85);
        scene.idle(95 + TEXT_GAP);

        // 「=」模式：凑满一批才吐
        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("'=' mode waits for a full batch")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(hatch));
        scene.overlay().showCenteredScrollInput(hatch, Direction.EAST, 85);
        scene.idle(95 + TEXT_GAP);

        // 红石锁：旁边摆一块红石块就停。
        // 思索的假世界在 setBlock 后不会跑邻居更新，所以方块状态里的 powered 得手动翻过去。
        scene.world().setBlock(redstone, Blocks.REDSTONE_BLOCK.defaultBlockState(), true);
        scene.world().modifyBlock(hatch,
                state -> state.setValue(SmartPressureReliefHatchBlock.POWERED, true), false);
        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("Redstone signals pause the release")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(redstone));
        scene.idle(95 + TEXT_GAP);
    }

    // ---- 工具方法 ----

    private static ItemStack roseQuartz() {
        return new ItemStack(BuddingFamilies.ROSE_QUARTZ.cluster().get().asItem());
    }

    /** 给一套风源上速度：大齿轮转得慢、小齿轮与风源反向同速（与动力吸尘器那条分镜同一套数） */
    private static void powerUp(CreateSceneBuilder scene, Selection bigCog, Selection cog, Selection fan,
                                BlockPos machine) {
        scene.world().setKineticSpeed(bigCog, 16f);
        scene.world().setKineticSpeed(cog, -32f);
        scene.world().setKineticSpeed(fan, -32f);
        scene.effects().indicateSuccess(machine);
        scene.idle(15);
    }

    /** 从网罩里吐出几件物品：斜着往上飞出去，再落到台面上（真实掉落由方块实体负责，这里只是演） */
    private static void ventItems(CreateSceneBuilder scene, Vec3 origin, Vec3 motion, ItemStack stack,
                                  int count, int gap) {
        for (int i = 0; i < count; i++) {
            scene.world().createItemEntity(origin, motion, stack.copy());
            scene.idle(gap);
        }
    }
}
