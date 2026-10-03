package com.minecart.yunxian.ponder.scenes;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.minecart.yunxian.registry.ModBlocks;
import com.minecart.yunxian.registry.ModItems;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.logistics.funnel.FunnelBlockEntity;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.item.filter.attribute.attributes.InTagAttribute;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * 共振台一族的六条分镜：台面、组网与共享过滤、过滤规则、列表与属性、红石冻结、显示。
 * <p>
 * <b>蓝图一 {@code resonance_table/resonance_table.nbt}（5×5×5）</b>：台子 (1,1,2)，第二张台子 (3,1,2)。
 * <br><b>蓝图二 {@code resonance_table/resonance_filter.nbt}（7×5×7）</b>：台子 (2,1,3) 与 (2,1,5)，
 * 箱子 (5,1,3)，箱子上面的黄铜漏斗 (5,2,3)。
 * <br><b>蓝图三 {@code resonance_table/resonance_display.nbt}（7×5×7）</b>：台子 (4,1,1)、
 * 贴在西面的显示链接器 (3,1,1)、翻牌显示器 (1..3,1,3)（控制器在最东的 (3,1,3)）、
 * 动力小齿轮 (4,1,3) 与大齿轮 (5,2,3)、第二张台子 (4,1,5)。
 * <p>
 * 演出说明：
 * <ol>
 *   <li>台面物品用 {@code modifyBlockEntity(... setCenteredHeldItem)} 设置，漏斗过滤槽用 Create 官方的
 *       {@code setFilterData}，显示器用 {@code setDisplayBoardText} —— 都不写进蓝图。</li>
 *   <li>物品的飞入与被弹开全是脚本演绎，轨迹为近似值，可调参数集中在 {@link #dropItem}。</li>
 *   <li>过滤判定本身在思索世界里不真跑（漏斗不 tick），「过得去 / 被挡下」由脚本给出，
 *       文案则严格按 {@code ResonanceFilterItemStack} 的真实语义写。</li>
 *   <li>四条分镜共用「过滤器」那张蓝图，靠演示重点与物品不同来区分。</li>
 *   <li><b>蓝图里两张共振台写的是同一个网络 id（{@code Freq}）</b>，否则「同一网络共享过滤」
 *       与并集这些演示根本演不出来——共振台的网络 id 本来是放下时随机生成的，
 *       不写进蓝图的话每张台子各成一个网络。</li>
 *   <li>「全空 / 半空 / 全不空」三种网络状态各配一句短文案，与各自的演示同步；这张分镜因此
 *       有四条文字，比平常多一条 —— 文字条数是可读性的提醒，不是硬上限。</li>
 * </ol>
 */
public class ResonanceScenes {

    // ==================== 1) 台面 ====================

    public static void resonanceTable(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("resonance_table", "The Resonance Table");
        scene.configureBasePlate(0, 0, 5);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos table = util.grid().at(1, 1, 2);
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack ironStack = new ItemStack(Items.IRON_INGOT, 16);

        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("A Resonance Table can only ever hold one kind of item.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(table));
        scene.idle(15);
        scene.overlay().showControls(util.vector().topOf(table), Pointing.DOWN, 40)
                .rightClick()
                .withItem(ironIngot);
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(ironIngot)));
        scene.idle(100);

        scene.overlay().showText(85)
                .attachKeyFrame()
                .text("Right-Click to place an item on a Resonance Table, and Right-Click again to take it back.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(table));
        scene.idle(20);
        scene.overlay().showControls(util.vector().topOf(table), Pointing.DOWN, 40)
                .rightClick();
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.removeHeldItem());
        scene.idle(85);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("The sliders on its sides set how many items a Resonance Table may hold, just like the Weighted Ejector.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(table));
        scene.overlay().showCenteredScrollInput(table, Direction.WEST, 70);
        scene.idle(20);
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(ironStack)));
        scene.effects().indicateSuccess(table);
        scene.idle(80);
    }

    // ==================== 2) 组网 ====================

    public static void resonanceNetwork(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("resonance_network", "Configuring the Filter with Several Tables");
        scene.configureBasePlate(0, 0, 7);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos table = util.grid().at(2, 1, 3);
        BlockPos second = util.grid().at(2, 1, 5);
        BlockPos chest = util.grid().at(5, 1, 3);
        BlockPos funnel = util.grid().at(5, 2, 3);

        ItemStack filterStack = new ItemStack(ModItems.RESONANCE_FILTER.get());
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack goldIngot = new ItemStack(Items.GOLD_INGOT);

        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(chest).add(util.select().position(funnel)), Direction.DOWN);
        scene.idle(10);

        // 过滤器装进漏斗；第一张台子放铁锭
        scene.world().setFilterData(util.select().position(funnel), FunnelBlockEntity.class, filterStack);
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(ironIngot)));
        scene.idle(10);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Sneak-Using a Resonance Table copies its network onto the held item.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(table));
        scene.idle(20);
        scene.overlay().showControls(util.vector().centerOf(table), Pointing.DOWN, 50)
                .whileSneaking()
                .rightClick()
                .withItem(new ItemStack(ModBlocks.RESONANCE_TABLE.get()));
        scene.idle(60);
        scene.effects().indicateSuccess(table);
        scene.idle(10);

        // 第二张台子带着同一个网络落位，台面放金锭
        scene.world().showSection(util.select().position(second), Direction.DOWN);
        scene.idle(10);
        scene.world().modifyBlockEntity(second, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(goldIngot)));
        scene.effects().indicateSuccess(second);
        scene.overlay().showBigLine(PonderPalette.GREEN, util.vector().topOf(table), util.vector().topOf(second), 70);
        scene.idle(20);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Every table placed from that item joins the same network, and they filter together.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(second));
        scene.idle(15);

        // 同一个过滤器同时按两张共振台的台面判定
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), ironIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(60);

        // 半空：拿走第二张台子上的金锭，金锭随之被挡下，铁锭照过
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("A table holding nothing is simply left out of the filter.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(second));
        scene.idle(15);
        scene.world().modifyBlockEntity(second, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.removeHeldItem());
        scene.idle(20);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, false);
        scene.effects().indicateRedstone(funnel);
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), ironIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(60);

        // 全空：两张台子都空着，过滤器不再限制任何物品
        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("Only when every table of the network is empty does it pass everything.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(table));
        scene.idle(15);
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.removeHeldItem());
        scene.idle(20);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(70);
    }

    // ==================== 3) 过滤规则来自网络 ====================

    public static void resonanceFilter(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("resonance_filter", "Automating the Filter");
        scene.configureBasePlate(0, 0, 7);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos table = util.grid().at(2, 1, 3);
        BlockPos chest = util.grid().at(5, 1, 3);
        BlockPos funnel = util.grid().at(5, 2, 3);

        ItemStack filterStack = new ItemStack(ModItems.RESONANCE_FILTER.get());
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack goldIngot = new ItemStack(Items.GOLD_INGOT);

        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(chest).add(util.select().position(funnel)), Direction.DOWN);
        scene.idle(10);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("A Resonance Filter holds no rules of its own: it reads the Resonance Tables of the network it is linked to.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(funnel));
        scene.idle(15);
        scene.overlay().showFilterSlotInput(filterSlot(util, funnel), Direction.SOUTH, 50);
        scene.world().setFilterData(util.select().position(funnel), FunnelBlockEntity.class, filterStack);
        scene.idle(95);

        // 台面放铁锭 —— 铁锭过得去，金锭被挡下
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(ironIngot)));
        scene.idle(10);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Only what the network allows will pass.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(funnel));
        scene.idle(10);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), ironIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, false);
        scene.effects().indicateRedstone(funnel);
        scene.idle(60);

        // 台面换成金锭 —— 放行的立刻跟着变
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(goldIngot)));
        scene.idle(10);

        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Change the item on the Resonance Table, and the filter follows at once.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(table));
        scene.idle(10);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), ironIngot, false);
        scene.effects().indicateRedstone(funnel);
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(70);
    }

    // ==================== 4) 台面上放过滤器：列表与属性 ====================

    public static void resonanceFilterTypes(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("resonance_filter_types", "List and Attribute Filters");
        scene.configureBasePlate(0, 0, 7);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos table = util.grid().at(2, 1, 3);
        BlockPos chest = util.grid().at(5, 1, 3);
        BlockPos funnel = util.grid().at(5, 2, 3);

        ItemStack filterStack = new ItemStack(ModItems.RESONANCE_FILTER.get());
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack goldIngot = new ItemStack(Items.GOLD_INGOT);
        ItemStack stick = new ItemStack(Items.STICK);

        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(chest).add(util.select().position(funnel)), Direction.DOWN);
        scene.idle(10);
        scene.world().setFilterData(util.select().position(funnel), FunnelBlockEntity.class, filterStack);
        scene.idle(10);

        // 台面放列表过滤器：装着的两样东西都过得去
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(listFilter())));
        scene.idle(10);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("A Resonance Table also accepts a List Filter, and passes every item that filter holds.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(table));
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), ironIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(60);

        // 换成属性过滤器：按属性匹配，木棍被挡下
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(ingotAttributeFilter())));
        scene.idle(15);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("An Attribute Filter on a table is read the same way, and passes whatever its attributes match.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(table));
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), stick, false);
        scene.effects().indicateRedstone(funnel);
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), ironIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(70);
    }

    // ==================== 5) 红石充能冻结过滤 ====================

    public static void resonanceFilterRedstone(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("resonance_filter_redstone", "Freezing the Filter");
        scene.configureBasePlate(0, 0, 7);
        scene.world().showSection(util.select().layer(0), Direction.UP);
        scene.idle(5);

        BlockPos table = util.grid().at(2, 1, 3);
        BlockPos power = util.grid().at(1, 1, 3);
        BlockPos chest = util.grid().at(5, 1, 3);
        BlockPos funnel = util.grid().at(5, 2, 3);

        ItemStack filterStack = new ItemStack(ModItems.RESONANCE_FILTER.get());
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack goldIngot = new ItemStack(Items.GOLD_INGOT);

        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(chest).add(util.select().position(funnel)), Direction.DOWN);
        scene.idle(10);

        scene.world().setFilterData(util.select().position(funnel), FunnelBlockEntity.class, filterStack);
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(ironIngot)));
        scene.idle(10);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, false);
        scene.effects().indicateRedstone(funnel);
        scene.idle(25);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("Supplying Redstone Power to a Resonance Table freezes its filter.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(table));
        // 红石块是给观众看的「电源」，充能时才出现
        scene.world().showSection(util.select().position(power), Direction.DOWN);
        scene.world().toggleRedstonePower(util.select().position(table));
        scene.effects().indicateRedstone(table);
        scene.idle(60);

        // 冻结之后把台面换成金锭 —— 网络仍然只放行铁锭
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(goldIngot)));
        scene.idle(50);

        scene.overlay().showText(100)
                .attachKeyFrame()
                .text("The network keeps filtering by a copy of the item that sat there when it was powered.")
                .placeNearTarget()
                .pointAt(util.vector().topOf(table));
        scene.idle(10);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), ironIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, false);
        scene.effects().indicateRedstone(funnel);
        scene.idle(80);

        // 撤掉信号，规则立刻回到台面上
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Remove the signal and the filter follows the Resonance Table again.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(table));
        scene.world().hideSection(util.select().position(power), Direction.DOWN);
        scene.world().toggleRedstonePower(util.select().position(table));
        scene.idle(15);
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), goldIngot, true);
        scene.effects().indicateSuccess(funnel);
        scene.idle(15);

        // 台面这会儿是金锭，铁锭反过来过不去了
        dropItem(scene, util.vector().centerOf(funnel).add(0, 1.7, 0), ironIngot, false);
        scene.effects().indicateRedstone(funnel);
        scene.idle(85);
    }

    // ==================== 6) 显示链接器 + 翻牌显示器 ====================

    public static void resonanceDisplay(SceneBuilder builder, SceneBuildingUtil util) {
        CreateSceneBuilder scene = new CreateSceneBuilder(builder);
        scene.title("resonance_display", "Reading a Network on a Display");
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

        scene.world().showSection(util.select().position(table), Direction.DOWN);
        scene.idle(4);
        scene.world().showSection(util.select().position(link), Direction.EAST);
        scene.idle(10);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("A Display Link attached to a Resonance Table reads the filter of its entire network.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(link));
        scene.idle(30);
        scene.overlay().showControls(util.vector().topOf(table), Pointing.DOWN, 40)
                .rightClick()
                .withItem(ironIngot);
        scene.world().modifyBlockEntity(table, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(ironIngot)));
        scene.idle(75);

        // 显示器出场并接上转动
        scene.world().showSection(boardSection, Direction.DOWN);
        scene.world().showSection(util.select().position(cog).add(util.select().position(largeCog)), Direction.DOWN);
        scene.idle(10);
        scene.world().setKineticSpeed(util.select().position(largeCog), 16f);
        scene.world().setKineticSpeed(util.select().position(cog), -32f);
        scene.world().setKineticSpeed(util.select().position(board), 32f);
        scene.world().setKineticSpeed(util.select().position(2, 1, 3), -32f);
        scene.world().setKineticSpeed(util.select().position(1, 1, 3), 32f);
        scene.idle(10);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("The link sends that list to the Flap Display it points at, one item per line.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(board));
        scene.idle(20);
        scene.world().flashDisplayLink(link);
        scene.world().setDisplayBoardText(board, 0, ironIngot.getHoverName());
        scene.idle(95);

        // 第二张台子进网 —— 列表多一行
        scene.world().showSection(util.select().position(secondTable), Direction.DOWN);
        scene.idle(10);
        scene.world().modifyBlockEntity(secondTable, ResonanceTableBlockEntity.class,
                be -> be.depotBehaviour.setCenteredHeldItem(new TransportedItemStack(goldIngot)));
        scene.world().flashDisplayLink(link);
        scene.world().setDisplayBoardText(board, 1, goldIngot.getHoverName());
        scene.idle(15);

        scene.overlay().showText(95)
                .attachKeyFrame()
                .text("The list covers every Resonance Table of the network, so a second Resonance Table adds a second line.")
                .placeNearTarget()
                .pointAt(util.vector().centerOf(board));
        scene.idle(105);
    }

    // ---- 工具方法 ----

    /** 一个装着铁锭与金锭的 Create 列表过滤器（演示用） */
    private static ItemStack listFilter() {
        ItemStack filter = new ItemStack(AllItems.FILTER.get());
        filter.set(AllDataComponents.FILTER_ITEMS, ItemContainerContents.fromItems(
                List.of(new ItemStack(Items.IRON_INGOT), new ItemStack(Items.GOLD_INGOT))));
        return filter;
    }

    /**
     * 一个「属于 {@code #c:ingots} 标签」的属性过滤器（演示用）。
     * <p>
     * 属性过滤器的内容平时由它自己的界面写进去，这里直接构造那一条属性。
     * 白名单模式留默认值，即任选一条属性命中就通过。
     */
    private static ItemStack ingotAttributeFilter() {
        ItemStack filter = new ItemStack(AllItems.ATTRIBUTE_FILTER.get());
        ItemAttribute.ItemAttributeEntry entry = new ItemAttribute.ItemAttributeEntry(
                new InTagAttribute(ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "ingots"))), false);
        filter.set(AllDataComponents.ATTRIBUTE_FILTER_MATCHED_ATTRIBUTES, List.of(entry));
        return filter;
    }

    /**
     * 竖着放的漏斗的过滤槽位置（叠加层高亮用）。
     * <p>
     * 坐标抄自 Create 的 {@code FunnelFilterSlotPositioning}：竖直漏斗走
     * {@code voxelSpace(8, 2, 15.5)}，即贴在南面、离方块底面 2/16 处。
     */
    private static Vec3 filterSlot(SceneBuildingUtil util, BlockPos funnel) {
        return util.vector().blockSurface(funnel, Direction.SOUTH).add(0, -0.375, 0);
    }

    /**
     * 让一样物品从上方落到漏斗上：{@code accepted} 为真表示被收进箱子（到漏斗那一刻消失），
     * 为假表示被挡下（带一点横向速度弹开、落到地板上）。
     * <p>
     * 轨迹是近似值：落体靠 PonderLevel 自己那套重力，命中时刻用 idle 对齐。
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
