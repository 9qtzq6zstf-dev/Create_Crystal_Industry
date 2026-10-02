package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModItems;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.foundation.ponder.CreateSceneBuilder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.element.ElementLink;
import net.createmod.ponder.api.element.EntityElement;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.createmod.ponder.api.scene.Selection;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * 共振台与共振过滤器的分镜。
 * <p>
 * <b>蓝图一 {@code resonance_table/resonance_table.nbt}（5×5×5）</b>
 * <ul>
 *   <li>(1,1,2) 主共振台</li>
 *   <li>(3,1,2) 第二张共振台（开场隐藏，演「并入同一网络」时才出场）</li>
 * </ul>
 * <b>蓝图二 {@code resonance_table/resonance_filter.nbt}（7×5×7）</b>
 * <ul>
 *   <li>(2,1,3) 主共振台，(2,1,5) 第二张台（开场隐藏）</li>
 *   <li>(5,1,3) 箱子，(5,2,3) 箱子上面的黄铜漏斗（facing=up：口朝上，落在上面的物品被收进下面的箱子）</li>
 * </ul>
 * <b>蓝图三 {@code resonance_table/resonance_display.nbt}（7×5×7）</b>
 * <ul>
 *   <li>(4,1,1) 共振台（数据来源），(3,1,1) 贴在西面的显示链接器</li>
 *   <li>(1..3,1,3) 翻牌显示器（3 宽 1 高，朝北；最东那块 (3,1,3) 是控制器）</li>
 *   <li>(4,1,3) 小齿轮正交咬住显示器，(5,2,3) 大齿轮斜对角咬住小齿轮（大-小只能斜咬），动力输入</li>
 *   <li>(4,1,5) 第二张共振台（开场隐藏）</li>
 * </ul>
 * 蓝图二、三的 y=0 都是雪块/白色混凝土棋盘格地板（既有的那 5 张蓝图也都是这么铺的，
 * Ponder 不会自己生成基座）。
 * <p>
 * <b>演出说明</b>
 * <ol>
 *   <li>台面上的物品用 {@code modifyBlockEntity(... setCenteredHeldItem)} 放上去，
 *       漏斗过滤槽里的过滤器用 Create 自己的 {@code setFilterData}（写 {@code Filter} 键）——
 *       都不写进蓝图：蓝图里塞方块实体 NBT 也行（智能钻头那张就是这么干的），但脚本更好改。</li>
 *   <li>物品的飞入与「被弹开」全是脚本演绎，轨迹是近似值。可调参数集中在
 *       {@link #dropItem} 的坐标偏移与随后的 idle 上。</li>
 *   <li>过滤判定本身在思索世界里不真跑（漏斗不 tick），所以「过得去 / 被挡下」由脚本给出，
 *       文案则严格按 {@code ResonanceFilterItemStack} 的真实语义写。</li>
 * </ol>
 */
public class ResonanceScenes {

    // ==================== 场景一：共振台 ====================

    public static void resonanceTable(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("resonance_table", "Using the Resonance Table");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos table = util.grid().at(1, 1, 2);
        BlockPos second = util.grid().at(3, 1, 2);
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);

        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(10);

        // 1) 台子是什么：只能放一样东西，右键放上去、再右键取回来
        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("The Resonance Table holds a single stack of items. Right-Click to place items on top, and Right-Click again to take them back.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(table));
        scene.idle(20);
        scene.overlay().showControls(util.vector().topOf(table), Pointing.DOWN, 45)
                .rightClick()
                .withItem(ironIngot);
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(ironIngot)));
        scene.idle(85);

        // 2) 台面上放什么，接了本网络的过滤器就放行什么
        scene.overlay().showText(105)
                .attachKeyFrame()
                .text("The item on the table is read by every Resonance Filter in its network, and decides what that network will pass.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(table));
        scene.idle(115);

        // 3) 组网手势：手持共振台潜行右键已有的台子，那个物品就记住了这张网络
        scene.overlay().showText(120)
                .attachKeyFrame()
                .text("Sneak-Using a Resonance Table on an existing one copies its network onto the held item. Every table placed from it will join the same network.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(table));
        scene.idle(20);
        scene.overlay().showControls(util.vector().centerOf(table), Pointing.DOWN, 55)
                .whileSneaking()
                .rightClick()
                .withItem(new ItemStack(ModBlocks.RESONANCE_TABLE.get()));
        scene.idle(70);
        scene.world().showSection(util.select().position(second), Direction.DOWN);
        scene.idle(10);
        scene.effects().indicateSuccess(table);
        scene.effects().indicateSuccess(second);
        scene.overlay().showBigLine(PonderPalette.GREEN, util.vector().topOf(table), util.vector().topOf(second), 70);
        scene.idle(30);

        // 4) 侧面的滑块：这张台子最多堆多少个（0 = "*" = 不限）
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("The sliders on its sides control how many items the table may hold. At '*' the table will accept a full stack.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(table));
        scene.overlay().showCenteredScrollInput(table, Direction.WEST, 85);
        scene.idle(110);
    }

    // ==================== 场景二：共振过滤器 ====================

    public static void resonanceFilter(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("resonance_filter", "Using the Resonance Filter");
        scene.configureBasePlate(0, 0, 7);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos table = util.grid().at(2, 1, 3);
        BlockPos secondTable = util.grid().at(2, 1, 5);
        BlockPos chest = util.grid().at(5, 1, 3);
        BlockPos funnel = util.grid().at(5, 2, 3);

        ItemStack filterStack = new ItemStack(ModItems.RESONANCE_FILTER.get());
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack goldIngot = new ItemStack(Items.GOLD_INGOT);

        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(chest).add(util.select().position(funnel)), Direction.DOWN);
        scene.idle(10);

        // 1) 过滤器自己不存规则
        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("Resonance Filters hold no rules of their own. Every test reads the tables of the network they are linked to.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(funnel));
        scene.idle(120);

        // 2) 台面上的铁锭就是这个网络放行的东西
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("An Iron Ingot sits on the table, so this network will only pass Iron Ingots.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(table));
        scene.idle(20);
        scene.overlay().showControls(util.vector().topOf(table), Pointing.DOWN, 45)
                .rightClick()
                .withItem(ironIngot);
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(ironIngot)));
        scene.idle(90);

        // 3) 接入网络 + 插进过滤槽
        scene.overlay().showText(120)
                .attachKeyFrame()
                .text("Sneak-Using the filter on a table links it to that table's network. It can then be placed in the filter slot of a Funnel, Chute or Basin.")
                .placeNearTarget()
                .pointAt(filterSlot(util, funnel));
        scene.idle(20);
        scene.overlay().showFilterSlotInput(filterSlot(util, funnel), Direction.SOUTH, 60);
        scene.overlay().showControls(filterSlot(util, funnel), Pointing.DOWN, 60)
                .rightClick()
                .withItem(filterStack);
        scene.idle(30);
        scene.world().setFilterData(util.select().position(funnel), FunnelBlockEntity.class, filterStack);
        scene.idle(75);

        // 4) 放行的过得去，不放行的被挡下
        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("The network will only pass what its tables allow. Iron Ingots pass, and Gold Ingots do not.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(funnel));
        scene.idle(10);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), ironIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, false);
        scene.effects().indicateRedstone(funnel);
        scene.idle(70);

        // 5) 并集：同一网络里的第二张台子放金锭，金锭也就过得去了
        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("A network combines the tables linked to it. Placing a Gold Ingot on a second table will allow Gold Ingots as well.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(secondTable));
        scene.idle(15);
        scene.world().showSection(util.select().position(secondTable), Direction.DOWN);
        scene.idle(10);
        scene.world().modifyBlockEntity(secondTable, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(new ItemStack(Items.GOLD_INGOT))));
        scene.effects().indicateSuccess(secondTable);
        scene.idle(20);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(80);

        // 6) 红石充能 = 冻结充能那一刻的台面物品
        scene.overlay().showText(120)
                .attachKeyFrame()
                .text("Supplying Redstone Power to a table freezes its filter. The network will keep filtering by a copy of the item placed at that moment.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(table));
        scene.world().toggleRedstonePower(util.select().position(table));
        scene.effects().indicateRedstone(table);
        scene.idle(15);
        // 冻结之后把台面上的铁锭换成金锭 —— 网络仍然放行铁锭（读的是冻结时的抄本）
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(new ItemStack(Items.GOLD_INGOT))));
        scene.idle(25);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), ironIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(80);

        // 7) 收尾：全空 = 不限制；读不到 = 什么都不通过
        scene.overlay().showText(120)
                .attachKeyFrame()
                .text("If every table of the network is empty, the filter will restrict nothing. A filter not linked to any table will pass nothing at all.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(funnel));
        scene.idle(130);
    }

    // ==================== 场景三：显示链接器 + 翻牌显示器 ====================

    public static void resonanceDisplay(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("resonance_display", "Displaying a Resonance Network");
        scene.configureBasePlate(0, 0, 7);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos table = util.grid().at(4, 1, 1);
        BlockPos link = util.grid().at(3, 1, 1);
        BlockPos board = util.grid().at(3, 1, 3);
        BlockPos cog = util.grid().at(4, 1, 3);
        BlockPos largeCog = util.grid().at(5, 2, 3);
        BlockPos secondTable = util.grid().at(4, 1, 5);
        Selection boardSection = util.select().fromTo(3, 1, 3, 1, 1, 3);

        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack goldIngot = new ItemStack(Items.GOLD_INGOT);

        // 1) 数据来源：共振台 + 贴在它西面的显示链接器
        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(link), Direction.EAST);
        scene.idle(10);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("A Display Link attached to a Resonance Table reads the filter of its entire network.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(link));
        scene.idle(30);
        scene.overlay().showControls(util.vector().topOf(table), Pointing.DOWN, 45)
                .rightClick()
                .withItem(ironIngot);
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(ironIngot)));
        scene.idle(80);

        // 2) 翻牌显示器出场：3 宽 1 高的板子，侧面小齿轮咬住它（它就是当齿轮参与传动的）
        scene.world().showSection(boardSection, Direction.DOWN);
        scene.world().showSection(util.select().position(cog).add(util.select().position(largeCog)), Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(util.select().position(largeCog), 16);
        scene.world().setKineticSpeed(util.select().position(cog), -32);
        scene.world().setKineticSpeed(util.select().position(board), 32);
        scene.world().setKineticSpeed(util.select().position(2, 1, 3), -32);
        scene.world().setKineticSpeed(util.select().position(1, 1, 3), 32);
        scene.idle(10);

        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("The link sends that list to the Flap Display it points at, one item per line. Flap Displays need Rotational Force to run.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(board));
        scene.idle(120);

        // 3) 在链接器的界面里选数据源，列表随即出现在显示器上
        scene.overlay().showControls(util.vector().centerOf(link), Pointing.DOWN, 50)
                .rightClick();
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Open the link's Interface to select the Resonance Network Filter source.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(link));
        scene.idle(60);
        scene.effects().indicateSuccess(link);
        scene.world().flashDisplayLink(link);
        scene.world().setDisplayBoardText(board, 0, ironIngot.getHoverName());
        scene.idle(70);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("The display now lists Iron Ingots, the only item this network filters.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(board));
        scene.idle(110);

        // 4) 列表覆盖整个网络：第二张台子放金锭，显示器多出一行
        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("The list covers every table of the network. Items placed on other tables are listed as well.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(secondTable));
        scene.idle(15);
        scene.world().showSection(util.select().position(secondTable), Direction.DOWN);
        scene.idle(10);
        scene.world().modifyBlockEntity(secondTable, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(goldIngot)));
        scene.world().flashDisplayLink(link);
        scene.world().setDisplayBoardText(board, 1, goldIngot.getHoverName());
        scene.idle(100);

        // 5) 收尾：同一个数据源也能喂给别的显示器
        scene.overlay().showText(110)
                .attachKeyFrame()
                .text("The same source can feed any other Display Link target, such as Display Boards or Nixie Tubes.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(board));
        scene.idle(120);
    }

    /**
     * 竖着放的漏斗的过滤槽位置（叠加层高亮用）。
     * <p>
     * 坐标抄自 Create 的 {@code FunnelFilterSlotPositioning}：竖直漏斗走
     * {@code voxelSpace(8, 2, 15.5)}，即贴在南面、离方块底面 2/16 处。
     * {@code blockSurface} 给的是南面中心（高度 8/16），再往下挪 6/16 就是它。
     */
    private static Vec3 filterSlot(SceneBuildingUtil util, BlockPos funnel) {
        return util.vector().blockSurface(funnel, Direction.SOUTH).add(0, -0.375, 0);
    }

    /**
     * 让一样物品从上方落到漏斗上：{@code accepted} 为真表示被收进箱子（到漏斗那一刻消失），
     * 为假表示被挡下（带一点横向速度弹开、落到地板上）。
     * <p>
     * 轨迹是近似值：落体靠 PonderLevel 自己那套重力，命中时刻用 idle 对齐；
     * 想调观感就改这里的 y 偏移与 idle。
     */
    private static void dropItem(CreateSceneBuilder scene, Vec3 above, ItemStack stack, boolean accepted) {
        if (accepted) {
            ElementLink<EntityElement> item = scene.world().createItemEntity(above, new Vec3(0, -0.05, 0), stack);
            scene.idle(8);
            scene.world().modifyEntity(item, Entity::discard);
        } else {
            scene.world().createItemEntity(above, new Vec3(0.09, 0.02, 0), stack);
            scene.idle(12);
        }
    }
}
