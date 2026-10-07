package com.minecart.yunxian.registry;

import com.minecart.yunxian.*;
import com.minecart.yunxian.block.AcceleratorBlock;
import com.minecart.yunxian.block.CrystalBatteryBlock;
import com.minecart.yunxian.block.FlammableIceSlurryBlock;
import com.minecart.yunxian.block.FlammableSundaeBlock;
import com.minecart.yunxian.block.MechanicalAcceleratorBlock;
import com.minecart.yunxian.block.MechanicalCleanerBlock;
import com.minecart.yunxian.block.ResonanceTableBlock;
import com.minecart.yunxian.block.SmartDrillBlock;
import com.minecart.yunxian.item.CrystalBatteryItem;
import com.minecart.yunxian.item.FlammableSundaeItem;
import com.minecart.yunxian.item.ResonanceTableItem;
import com.simibubi.create.content.decoration.palettes.ConnectedPillarBlock;
import com.simibubi.create.content.decoration.palettes.LayeredBlock;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.fml.ModList;

import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * 非母岩方块的注册表：机器、工具与可燃冰装饰方块。
 * <p>
 * 全部母岩（含各级芽与晶簇）由 {@code budding/BuddingFamilies} 的中央定义表注册，
 * 本类只提供注册表本身与 {@link #registerBlock} 工具方法。
 * <p>
 * <b>本类不得引用 BuddingFamilies</b>：BuddingFamilies 依赖本类，反向引用会让静态
 * 初始化成环（BuddingFamilies → ModBlocks → BuddingFamilies），启动即崩。
 */
public final class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Yunxian.MODID);

    // 可燃冰装饰方块（冰音效 + 蓝冰摩擦）
    public static final DeferredBlock<Block> FLAMMABLE_ICE_BLOCK = registerBlock("flammable_ice_block",
            () -> new Block(flammableIceDecoProperties()));

    // 可燃冰装饰套件：砖、台阶、楼梯，砖再出一套台阶与楼梯。
    // 贴图与模型统一收在 block/deco/ 下（见 textures/block/deco 与 models/block/deco），
    // 只有可燃冰台阶的侧面另用 flammable_ice_slab_side——那张图是 8 像素一格的冰面
    // 波形重复两次，正好配原版 block/slab 模型侧面取下半张的 UV。

    /** 可燃冰砖：可燃冰块 2×2 合成，是砖系台阶与楼梯的材料 */
    public static final DeferredBlock<Block> FLAMMABLE_ICE_BRICKS = registerBlock("flammable_ice_bricks",
            () -> new Block(flammableIceDecoProperties()));

    public static final DeferredBlock<SlabBlock> FLAMMABLE_ICE_SLAB = registerBlock("flammable_ice_slab",
            () -> new SlabBlock(flammableIceDecoProperties()));

    /**
     * 可燃冰楼梯。基状态传可燃冰块自身（原版 {@code Blocks.legacyStair} 就是这个写法），
     * {@code StairBlock} 只拿它去取爆炸抗性。
     * <p>
     * 敢在 supplier 里读同一注册表里另一个句柄，是因为 {@code DeferredRegister} 的条目表是
     * LinkedHashMap、{@code RegisterEvent#register} 又同步调用 supplier，先声明的一定先注册完；
     * <b>把可燃冰块挪到本行之后就会当场 NPE</b>。
     */
    public static final DeferredBlock<StairBlock> FLAMMABLE_ICE_STAIRS = registerBlock("flammable_ice_stairs",
            () -> new StairBlock(FLAMMABLE_ICE_BLOCK.get().defaultBlockState(), flammableIceDecoProperties()));

    /** 可燃冰墙：属性与台阶楼梯同一套，另加 {@code forceSolidOn}（原版每一种墙都带这一步） */
    public static final DeferredBlock<WallBlock> FLAMMABLE_ICE_WALL = registerBlock("flammable_ice_wall",
            () -> new WallBlock(flammableIceDecoProperties().forceSolidOn()));

    public static final DeferredBlock<SlabBlock> FLAMMABLE_ICE_BRICK_SLAB = registerBlock("flammable_ice_brick_slab",
            () -> new SlabBlock(flammableIceDecoProperties()));

    /** 可燃冰砖楼梯，对基状态的要求同 {@link #FLAMMABLE_ICE_STAIRS} */
    public static final DeferredBlock<StairBlock> FLAMMABLE_ICE_BRICK_STAIRS = registerBlock("flammable_ice_brick_stairs",
            () -> new StairBlock(FLAMMABLE_ICE_BRICKS.get().defaultBlockState(), flammableIceDecoProperties()));

    public static final DeferredBlock<WallBlock> FLAMMABLE_ICE_BRICK_WALL = registerBlock("flammable_ice_brick_wall",
            () -> new WallBlock(flammableIceDecoProperties().forceSolidOn()));

    /**
     * 可燃冰柱：仿 Create 的 {@code create:granite_pillar} 那一档装饰柱。
     * <p>
     * 用的不是原版 {@code RotatedPillarBlock}，而是 Create 的 {@link ConnectedPillarBlock}——
     * 它在 {@code axis} 之外还带 n/s/e/w 四个连接状态，相邻的柱子拼成一片时切成
     * {@code _connected} 贴图。那套连接材质在客户端（{@code client/deco/FlammableIcePillarModel}），
     * 方块这半只负责维护状态。
     * <p>
     * 属性沿用其它装饰方块那一份（蓝冰底子 + 冰音效 + 蓝冰摩擦），不带
     * {@code requiresCorrectToolForDrops}；{@code mineable/pickaxe} 标签见
     * {@code ModBlockTagsProvider}。
     */
    public static final DeferredBlock<ConnectedPillarBlock> FLAMMABLE_ICE_PILLAR = registerBlock("flammable_ice_pillar",
            () -> new ConnectedPillarBlock(flammableIceDecoProperties()));

    // 再往下的三套照抄 Create 的石材调色板：切制（CUT）、层叠（LAYERED）、小砖块（SMALL_BRICKS）。
    // 命名也跟 Create 一致（cut_deepslate / layered_deepslate / small_deepslate_bricks 那种形式），
    // 只有台阶/楼梯/墙沿用本模组既有的 <名字>_brick_<部件> 写法。
    // 贴图目前是占位——从 Create 的深板岩那三套复制来的（见 textures/block/deco 下的同名文件）。

    /** 切制可燃冰块：整块，切石得来，是切制台阶/楼梯/墙的材料 */
    public static final DeferredBlock<Block> CUT_FLAMMABLE_ICE = registerBlock("cut_flammable_ice",
            () -> new Block(flammableIceDecoProperties()));

    public static final DeferredBlock<SlabBlock> CUT_FLAMMABLE_ICE_SLAB = registerBlock("cut_flammable_ice_slab",
            () -> new SlabBlock(flammableIceDecoProperties()));

    /** 基状态要求同 {@link #FLAMMABLE_ICE_STAIRS}：被引用的方块必须在本行之前声明 */
    public static final DeferredBlock<StairBlock> CUT_FLAMMABLE_ICE_STAIRS = registerBlock("cut_flammable_ice_stairs",
            () -> new StairBlock(CUT_FLAMMABLE_ICE.get().defaultBlockState(), flammableIceDecoProperties()));

    public static final DeferredBlock<WallBlock> CUT_FLAMMABLE_ICE_WALL = registerBlock("cut_flammable_ice_wall",
            () -> new WallBlock(flammableIceDecoProperties().forceSolidOn()));

    /**
     * 层叠可燃冰块：Create 的 {@link LayeredBlock}——一个有轴向的整块，拼在一起时侧面切成
     * {@code _connected} 贴图（客户端那半见 {@code client/deco/FlammableIceLayeredModel}）。
     * <p>
     * 它没有台阶/楼梯/墙：Create 那边 LAYERED 这一档就不出这三样。
     */
    public static final DeferredBlock<LayeredBlock> LAYERED_FLAMMABLE_ICE = registerBlock("layered_flammable_ice",
            () -> new LayeredBlock(flammableIceDecoProperties()));

    /** 可燃冰小砖块：比 {@link #FLAMMABLE_ICE_BRICKS} 更细的砖纹 */
    public static final DeferredBlock<Block> SMALL_FLAMMABLE_ICE_BRICKS = registerBlock("small_flammable_ice_bricks",
            () -> new Block(flammableIceDecoProperties()));

    public static final DeferredBlock<SlabBlock> SMALL_FLAMMABLE_ICE_BRICK_SLAB = registerBlock("small_flammable_ice_brick_slab",
            () -> new SlabBlock(flammableIceDecoProperties()));

    /** 基状态要求同 {@link #FLAMMABLE_ICE_STAIRS} */
    public static final DeferredBlock<StairBlock> SMALL_FLAMMABLE_ICE_BRICK_STAIRS = registerBlock("small_flammable_ice_brick_stairs",
            () -> new StairBlock(SMALL_FLAMMABLE_ICE_BRICKS.get().defaultBlockState(), flammableIceDecoProperties()));

    public static final DeferredBlock<WallBlock> SMALL_FLAMMABLE_ICE_BRICK_WALL = registerBlock("small_flammable_ice_brick_wall",
            () -> new WallBlock(flammableIceDecoProperties().forceSolidOn()));

    /**
     * 可燃冰装饰套件的<b>唯一清单</b>，顺序就是创造栏里的陈列顺序。
     * <p>
     * 读它的是 {@link ModCreativeTabs} 的两处：{@code addDecoToCreateTabs} 把整套装饰方块送进
     * 机械动力的「建筑方块」页，{@code YUNXIAN_TAB} 再把同一批方块在自己的页里陈列一份，
     * 所以这里不挂在任何家族上。<b>以后再加变体只改这一处</b>：在上面按同样写法注册方块，
     * 再到这份清单里补一行（两处陈列会自动跟着变）。
     * <p>
     * 排列规矩：<b>先按材质分组，每组内部是「整块 + 台阶 + 楼梯 + 墙」</b>。组的先后照
     * Create 调色板里那一套（{@code PaletteBlockPattern.VANILLA_RANGE}：切制 → 砖 → 小砖 → 层叠 → 柱），
     * 我们多出来的基准块组（可燃冰块本人那一组）排在最前——它是其余几组的取材之处。
     * <p>
     * 只收装饰方块：母岩家族的方块由家族表自己陈列（{@code BuddingFamilies}），可燃冰与
     * 可燃冰圣代那两件物品留在本模组自己的标签页（见该家族的 {@code Appearance#tabExtras}）。
     */
    public static final List<DeferredBlock<? extends Block>> FLAMMABLE_ICE_DECO = List.of(
            // 基准块
            FLAMMABLE_ICE_BLOCK, FLAMMABLE_ICE_SLAB, FLAMMABLE_ICE_STAIRS, FLAMMABLE_ICE_WALL,
            // 切制
            CUT_FLAMMABLE_ICE, CUT_FLAMMABLE_ICE_SLAB, CUT_FLAMMABLE_ICE_STAIRS, CUT_FLAMMABLE_ICE_WALL,
            // 砖
            FLAMMABLE_ICE_BRICKS, FLAMMABLE_ICE_BRICK_SLAB, FLAMMABLE_ICE_BRICK_STAIRS, FLAMMABLE_ICE_BRICK_WALL,
            // 小砖块
            SMALL_FLAMMABLE_ICE_BRICKS, SMALL_FLAMMABLE_ICE_BRICK_SLAB,
            SMALL_FLAMMABLE_ICE_BRICK_STAIRS, SMALL_FLAMMABLE_ICE_BRICK_WALL,
            // 层叠与柱：Create 那边这两档也不出台阶/楼梯/墙，各自只有整块一件
            LAYERED_FLAMMABLE_ICE, FLAMMABLE_ICE_PILLAR);

    /**
     * 可燃冰圣代：玻璃杯碗盛着的可燃冰，顶上点缀熔岩，既能喝也能当摆件。
     * <p>
     * 属性抄玻璃（音效、硬度、无遮挡、不导电），因为它的外形就是一只玻璃杯碗；
     * 形状由方块自己收窄、点着的亮度走 {@code lightLevel}（和原版蜡烛同一个写法），
     * 其余行为见 {@link FlammableSundaeBlock}：右键收回、冻住周围的水、像蜡烛一样能点着。
     * <p>
     * 物品走 {@link FlammableSundaeItem}：潜行才肯放、能喝、喝完给空瓶与抗火。
     */
    public static final DeferredBlock<Block> FLAMMABLE_SUNDAE = registerBlock("flammable_sundae",
            () -> new FlammableSundaeBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .lightLevel(state -> state.getValue(FlammableSundaeBlock.LIT)
                            ? FlammableSundaeBlock.LIT_LIGHT : 0)),
            FlammableSundaeItem::new);

    /**
     * 失活弧光石母岩：弧光石母岩的「未激活」形态。是个纯装饰 & 待激活的方块，自己没有方块实体——
     * 不吃电、不长芽、不冒火花，只有一点：被雷劈中就变成真正的弧光石母岩，并且 FE 直接给满。
     * <p>
     * 两条触发路径都写在 {@code budding/LightningActivation} 里，由 {@code mixin/LightningBoltMixin}
     * 在闪电结算的那一刻调起：闪电<b>直接落在它身上</b>，或者它<b>正上方那格是避雷针、避雷针被劈中</b>。
     * 原版只在 {@code LightningBolt#powerLightningRod} 里给避雷针开了口子，普通方块没有任何钩子，
     * 所以这里必须走 mixin。
     * <p>
     * 属性逐字照抄弧光石母岩用的那一套（{@code ofFullCopy(BUDDING_AMETHYST)}）：既然它是后者的
     * 未激活形态，硬度、音效、爆炸抗性就该完全一致。<b>两个后果要记住</b>：
     * <ul>
     *   <li>这套属性带 {@code requiresCorrectToolForDrops}，所以它必须进
     *       {@code mineable/pickaxe} 与 {@code needs_iron_tool} 两个标签
     *       （见 {@code ModBlockTagsProvider}），否则挖掉一格都不掉；</li>
     *   <li>连带抄来了 {@code randomTicks} 标志位。本方块是纯 {@code Block}，{@code randomTick}
     *       是空的，所以只是让所在区段多被随机刻扫一遍、不产生任何行为。留着是为了和弧光石母岩
     *       永远同进同退，不值得为这点开销把属性表抄成第二份。</li>
     * </ul>
     * 获取方式未定：没有合成配方也没有世界生成，当前是创造限定。
     */
    public static final DeferredBlock<Block> INACTIVE_ARCLIGHT_BUDDING = registerBlock("inactive_arclight_budding",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.BUDDING_AMETHYST)));

    /**
     * 电流浆的流体方块：存在的唯一理由是「桶倒下去得有东西可放」，不是给玩家挖的。
     * 它与岩浆块的交互不在本类，而在 {@code ModFluids#registerInteractions()} 里用
     * NeoForge 的流体交互 API 注册（照着原版玄武岩那条写的）。
     * 属性逐条照抄原版水（可替换、无碰撞、不可破坏、无掉落表、不导电），
     * 只是地图颜色取电青色；真正的颜色与外观由流体类型（{@code ModFluidExtensions}）决定。
     * <p>
     * 刻意不走 {@link #registerBlock}：那个helper 会顺手注册一个 BlockItem，
     * 而流体方块不该有物品形态——桶才是它的物品。
     */
    public static final DeferredBlock<LiquidBlock> CURRENT_SLURRY_BLOCK = BLOCKS.register("current_slurry",
            () -> new LiquidBlock(ModFluids.CURRENT_SLURRY.get(), BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .replaceable()
                    .noCollission()
                    .strength(100.0F)
                    // 世界里发光靠的是**方块**属性而不是流体类型：原版岩浆就是这么写的
                    // （FluidType 那个 lightLevel 只管桶那一侧），两边都给 15
                    .lightLevel(state -> 15)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY)));

    /**
     * 可燃冰沙方块：属性逐条照抄原版细雪，行为也整个继承它（踩陷、冻伤、皮革靴能走），
     * 只有「空桶舀起来」那一处改成还我们自己的桶（见 {@link FlammableIceSlurryBlock}）。
     * <p>
     * <b>和细雪一样不给物品形态</b>——用 {@code BLOCKS.register} 而不是 {@code registerBlock}，
     * 所以它进不了背包、也不上创造栏；想摆出来只能用桶倒。这和细雪块的处理是一致的。
     * <p>
     * 掉落表也不写：原版细雪就没有掉落表，挖掉什么都不掉（要拿只能舀）。
     */
    public static final DeferredBlock<FlammableIceSlurryBlock> FLAMMABLE_ICE_SLURRY =
            BLOCKS.register("flammable_ice_slurry", () -> new FlammableIceSlurryBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.SNOW)
                            .strength(0.25F)
                            .sound(SoundType.POWDER_SNOW)
                            .dynamicShape()
                            // 原版细雪这里传的是 Blocks::never，但那个方法是 private 的，抄不过来
                            .isRedstoneConductor((state, level, pos) -> false)));

    //催生器
    public static final DeferredBlock<Block> ACCELERATOR = registerBlock("accelerator",
            () -> new AcceleratorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)));

    //智能钻头
    public static final DeferredBlock<Block> SMART_DRILL = registerBlock("smart_drill",
            () -> new SmartDrillBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    // 动力催生器
    public static final DeferredBlock<Block> MECHANICAL_ACCELERATOR = registerBlock("mechanical_accelerator",
            () -> new MechanicalAcceleratorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    // 动力吸尘器
    public static final DeferredBlock<Block> MECHANICAL_CLEANER = registerBlock("mechanical_cleaner",
            () -> new MechanicalCleanerBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion()));

    // 水晶电池：属性逐条对齐机械动力流体储罐（铜块底 + 无遮挡 + 始终导电 + 掉落自己算，不走掉落表）
    // 物品用 CrystalBatteryItem：在 2x2 / 3x3 结构上再放一层时一次补齐整层
    public static final DeferredBlock<Block> CRYSTAL_BATTERY = registerBlock("crystal_battery",
            () -> new CrystalBatteryBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.COPPER_BLOCK)
                    .noOcclusion()
                    .isRedstoneConductor((state, level, pos) -> true)
                    .noLootTable()),
            CrystalBatteryItem::new);

    /**
     * 共振台：上面放一样东西，接了「共振过滤器」的漏斗/溜槽/工作盆就按那样东西过滤。
     * 属性对齐置物台（石质、需要镐），形状与交互也照抄置物台 —— 见 {@code ResonanceTableBlock}。
     */
    public static final DeferredBlock<Block> RESONANCE_TABLE = registerBlock("resonance_table",
            () -> new ResonanceTableBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.ANDESITE)),
            ResonanceTableItem::new);

    /** AE2 是否加载：可选联动（福鲁伊克斯母岩）的开关 */
    public static final boolean AE2_LOADED =
            ModList.get() != null && ModList.get().isLoaded("ae2");

    private ModBlocks() {
    }

    /**
     * 整套可燃冰装饰方块共用的属性：逐条照可燃冰块那一份（蓝冰底子 + 冰音效 + 蓝冰摩擦）。
     * 台阶与楼梯直接沿用，形状由方块自己收窄，属性表不必为此做任何让步。
     * <p>
     * 这套属性<b>不带</b> {@code requiresCorrectToolForDrops}（蓝冰本身就没有），所以徒手也能挖掉，
     * 掉落不受工具影响；{@code mineable/pickaxe} 标签只决定挖掘速度。
     */
    private static BlockBehaviour.Properties flammableIceDecoProperties() {
        return BlockBehaviour.Properties.ofFullCopy(Blocks.BLUE_ICE)
                .sound(SoundType.GLASS)
                .friction(0.989F);
    }

    /** 注册方块并顺带注册对应的 BlockItem */
    public static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockSupplier) {
        return registerBlock(name, blockSupplier, BlockItem::new);
    }

    /**
     * 注册方块并顺带注册对应的 BlockItem，方块的物品需要自定义行为时用这个重载
     * （例如水晶电池那个"一次放一层"的 {@link CrystalBatteryItem}）。
     */
    public static <T extends Block> DeferredBlock<T> registerBlock(String name, Supplier<T> blockSupplier,
                                                                  BiFunction<T, Item.Properties, ? extends Item> itemFactory) {
        DeferredBlock<T> block = BLOCKS.register(name, blockSupplier);
        ModItems.ITEMS.register(name, () -> itemFactory.apply(block.get(), new Item.Properties()));
        return block;
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
    }
}
