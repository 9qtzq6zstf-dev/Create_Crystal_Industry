package com.minecart.yunxian.block;

import com.minecart.yunxian.blockentity.SmartTemperatureChamberBlockEntity;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 智能温控室：烧<b>可燃冰沙</b>给正上方的工作盆供热。
 * <p>
 * <b>它凭什么能顶替烈焰人燃烧室</b>——Create 判定工作盆热量的唯一入口是
 * {@code BasinBlockEntity.getHeatLevelOf(BlockState)}，第一句就是
 * <pre>if (state.hasProperty(BlazeBurnerBlock.HEAT_LEVEL))
 *     return state.getValue(BlazeBurnerBlock.HEAT_LEVEL);</pre>
 * 也就是说：<b>哪个方块身上挂了这一位属性，工作盆就认谁的账</b>，与方块是不是
 * {@code create:blaze_burner} 毫无关系。所以这里直接把 Create 那一份
 * {@link BlazeBurnerBlock#HEAT_LEVEL} <b>实例</b>复用过来（是同一个对象，不是自己新建一位同名属性
 * ——{@code hasProperty} 比的是实例，自己造一个同名的会当场失配），一块 mixin 都不用写。
 * <p>
 * 档位取 {@link HeatLevel#SEETHING}：{@code HeatCondition#testBlazeBurner} 里
 * {@code SUPERHEATED} 要求 SEETHING、{@code HEATED} 要求「不是 NONE 也不是 SMOULDERING」，
 * SEETHING 是同时满足两者的唯一一档，也正好对上需求里的「无视热量条件」。
 * <p>
 * <b>刻意不进的 Create 标签</b>：{@code create:passive_boiler_heaters}、
 * {@code create:fan_processing_catalysts/blasting} 与 {@code .../smoking}。
 * 那几处虽然也读 {@code HEAT_LEVEL}，但都先问「这个方块在不在对应标签里」，
 * 不登记就等于这块方块只会加热工作盆——不会顺带变成蒸汽锅炉热源或风扇的超热催化剂。
 * （{@code BoilerHeaters} 与 {@code ConductorBlockInteractionBehavior.BlazeBurner} 更窄，
 * 它们按方块实例注册，我们本来就命中不了。）
 * <p>
 * <p>
 * <b>但这一位属性只解决 Create 侧</b>：别的模组会在 {@code BasinRecipe.apply} 里挂自己的判定，
 * 判的不是热量档位（CMR 的雪人冷却器看"热量要求是不是它自己加的自定义条件"、
 * FluidLogistics 的烈焰冷却器看"配方有没有实现它的 {@code CoolingRecipe} 接口"），
 * 报什么档位都拦不住。那一路由 {@link com.minecart.yunxian.recipe.HeatlessBasinRecipe}
 * 在调用点上换成一份"没有热量要求"的壳来兜——两件事互不冲突，属性这一位照旧管着
 * 亮度与外观，也照旧让"温控室在不在供热"这件事对别的模组可见。
 * <p>
 * 方块状态是<b>推导出来的</b>：方块实体看着罐里的液体改写它，方块自己不存任何数据，
 * 所以永远不会有「状态说有燃料、罐里其实是空的」这种两面不一致。
 * 液体本身、消耗节奏都在 {@link SmartTemperatureChamberBlockEntity}。
 */
public class SmartTemperatureChamberBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final MapCodec<SmartTemperatureChamberBlock> CODEC =
            simpleCodec(SmartTemperatureChamberBlock::new);

    /**
     * Create 那一位，原样引用。序列化名是 {@code blaze}（{@code EnumProperty.create("blaze", ...)}），
     * 取值 {@code none / smouldering / fading / kindled / seething}——方块状态 JSON 的变体键
     * 就照这几个名字写，见 {@code blockstates/smart_temperature_chamber.json}。
     */
    public static final EnumProperty<HeatLevel> HEAT_LEVEL = BlazeBurnerBlock.HEAT_LEVEL;

    /**
     * 朝向：高炉模型只有一面是"炉口"（{@code front}），摆下去得让炉口对着玩家。
     * <p>
     * 用原版那一位 {@code facing}（只有四个水平方向），语义与高炉、熔炉完全一致——
     * 方块状态 JSON 里就是靠它给模型加 {@code y} 旋转的（见
     * {@code blockstates/smart_temperature_chamber.json}，4 个朝向 × 5 个档位共 20 条变体）。
     */
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public SmartTemperatureChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(HEAT_LEVEL, HeatLevel.NONE)
                .setValue(FACING, Direction.NORTH));
    }

    /**
     * 炉口对着<b>放下它的玩家</b>（取玩家水平朝向的反方向，与原版熔炉、高炉同一个写法）。
     * <p>
     * 只设 {@code FACING}：燃料档位是方块实体按罐里液体现算的，摆放那一刻一律从 {@code NONE} 起步
     * （{@code defaultBlockState()} 里的值），不在这里画蛇添足。
     * <p>
     * {@code rotate} / {@code mirror} 不用自己写：父类 {@link HorizontalDirectionalBlock} 已经把
     * 这一位接上了，结构方块旋转、{@code /place structure} 出来的朝向都是对的。
     */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected MapCodec<SmartTemperatureChamberBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEAT_LEVEL, FACING);
    }

    /**
     * 亮度（写进 {@code Properties#lightLevel} 的函数引用）。照 {@code BlazeBurnerBlock.getLight}，
     * 只是我们只会用到两端：烧着 15、不烧 0。
     * <p>
     * 亮度的更新靠状态翻转那次 {@code setBlockAndUpdate}（标志位 3 会带上光照重算），
     * 所以这里不需要（也没有）额外的通知。
     */
    public static int getLight(BlockState state) {
        return state.getValue(HEAT_LEVEL) == HeatLevel.SEETHING ? 15 : 0;
    }

    /**
     * 这台温控室有没有在给<b>这个</b>工作盆供热：正下方那块是自己，且状态不是没燃料那一档。
     * <p>
     * 换壳那一路（{@link com.minecart.yunxian.recipe.HeatlessBasinRecipe}）拿它当剂量开关——
     * 罐空了这里就返回 false，配方照旧按原样的热量要求判，于是"烧完就停工"这条不受影响。
     * <p>
     * 只看方块状态、不去查方块实体：状态本来就是方块实体按罐里液体顶出来的，
     * 读它比查一次方块实体便宜，也不会有"状态与罐对不上"的第二份真相。
     * <p>
     * 用 {@code instanceof} 而不是比对注册表句柄，是为了不把 {@code ModBlocks} 牵扯进方块类里
     * （两者互相引用，虽然只在方法体里读、不会有静态初始化成环的问题，但少一条边少一分风险）。
     */
    public static boolean heatsBasin(BasinBlockEntity basin) {
        Level level = basin.getLevel();
        if (level == null) {
            return false;
        }
        BlockState below = level.getBlockState(basin.getBlockPos().below());
        return below.getBlock() instanceof SmartTemperatureChamberBlock
                && below.getValue(HEAT_LEVEL) != HeatLevel.NONE;
    }

    /**
     * 正上方若是工作盆，催它重算一次内容物。
     * <p>
     * 上面那台机器（搅拌机 / 压床）是拿 {@code BasinBlockEntity#contentsChanged} 当"该重新挑配方了"的信号的
     * （{@code BasinOperatingBlockEntity#basinChecker} 是个延迟行为），所以只改方块状态而不催这一下，
     * 空转中的搅拌机可能要等到下一个周期才反应。Create 自己的 {@code BlazeBurnerBlock#onPlace}
     * 做的就是这一件事，我们额外在燃料状态翻转与方块被拆掉时也各催一次。
     */
    public static void notifyBasinAbove(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos.above()) instanceof BasinBlockEntity basin) {
            basin.notifyChangeOfContents();
        }
    }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level.isClientSide) {
            return;
        }
        notifyBasinAbove(level, pos);
    }

    /**
     * 拆掉时也催一次：不然正上方那台正在干活、或者正等着热量的机器得等到它自己的周期才会反应过来。
     * <p>
     * 判定 {@code !state.is(newState.getBlock())} 是为了跳过"自己换成自己"（我们改燃料状态走的就是
     * {@code setBlock}，那种情况方块没变、方块实体也还在，不该当成拆除）。
     * <p>
     * 罐里的可燃冰沙随方块一起消失——和母岩的流体罐一样，没有回收逻辑。
     */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            notifyBasinAbove(level, pos);
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    /**
     * 手持桶/瓶右键：灌进去或舀出来。只吞<b>流体容器</b>，其余物品照常落回原版
     * （「倒不进去时为什么不能落回原版」那段理由写在 {@link FluidTankInteraction}）。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        ItemInteractionResult tank = FluidTankInteraction.tryUse(stack, level, pos, player, hand, hitResult);
        if (tank != null) {
            return tank;
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    /**
     * 烧着的时候学烈焰人燃烧室哼一声营地火堆的噼啪声——纯客户端方法，
     * 专用服务端只会加载这个类、不会调用它（和 {@code AcceleratorBlock#animateTick} 同一套写法）。
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(10) != 0 || state.getValue(HEAT_LEVEL) != HeatLevel.SEETHING) {
            return;
        }
        level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
    }

    /** 比较器认这一位（原版先问它，答 false 就直接不往下读了），有罐所以恒为 true */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** 比较器读液位；算法见 {@link SmartTemperatureChamberBlockEntity#comparatorSignal} */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return SmartTemperatureChamberBlockEntity.comparatorSignal(level, pos);
    }

    /**
     * <b>永远建方块实体</b>，这一点与 {@code BlazeBurnerBlock#newBlockEntity} 相反
     * （那个在没点火时返回 null）。原因是这里的方块实体才是罐的主人：没燃料的温控室照样得
     * 接管道和桶灌进来的液体，而燃料状态只是罐的一个投影。
     */
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SmartTemperatureChamberBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                  BlockEntityType<T> type) {
        if (level.isClientSide() || type != ModBlockEntities.SMART_TEMPERATURE_CHAMBER.get()) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof SmartTemperatureChamberBlockEntity chamber) {
                chamber.serverTick();
            }
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
