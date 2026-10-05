package com.minecart.yunxian.registry;

import com.minecart.yunxian.*;
import com.minecart.yunxian.block.AcceleratorBlock;
import com.minecart.yunxian.block.CrystalBatteryBlock;
import com.minecart.yunxian.block.FlammableSundaeBlock;
import com.minecart.yunxian.block.MechanicalAcceleratorBlock;
import com.minecart.yunxian.block.MechanicalCleanerBlock;
import com.minecart.yunxian.block.ResonanceTableBlock;
import com.minecart.yunxian.block.SmartDrillBlock;
import com.minecart.yunxian.item.CrystalBatteryItem;
import com.minecart.yunxian.item.FlammableSundaeItem;
import com.minecart.yunxian.item.ResonanceTableItem;
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
