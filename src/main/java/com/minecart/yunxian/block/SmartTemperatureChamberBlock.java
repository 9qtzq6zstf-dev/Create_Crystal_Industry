package com.minecart.yunxian.block;

import com.minecart.yunxian.blockentity.SmartTemperatureChamberBlockEntity;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock;
import com.simibubi.create.content.processing.burner.BlazeBurnerBlock.HeatLevel;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 智能温控室：烧<b>可燃冰沙</b>，把正上方的工作盆调成配方要的温度。
 * <p>
 * 定位上是<b>控温</b>而不是供热：配方要加热、要超级加热，还是别的模组加的冷却，它一律照做
 * （"供热"那句会把话说小，见 {@code SmartTemperatureChamberBlockEntity#addToGoggleTooltip}
 * 里那段同样的取舍）。
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
 * 不登记就不会顺带变成风扇的超热催化剂。
 * <p>
 * <b>锅炉热源则是另外单独挂的</b>（{@code Yunxian#commonSetup} 里把
 * {@code BoilerHeater.BLAZE_BURNER} 注册到本方块上）：走那条路拿到的是<b>按档位算热</b>——
 * 烧着 = 2 档满热，没燃料 = 不热；而进了 {@code passive_boiler_heaters} 标签只有被动的一档，
 * 那是给火、岩浆块这类免费热源用的，对一台要烧燃料的机器不合适。
 * （{@code ConductorBlockInteractionBehavior.BlazeBurner} 更窄，按方块实例注册，我们命中不了，
 * 所以放到火车上不会像烈焰人燃烧室那样当导体。）
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
public class SmartTemperatureChamberBlock extends BaseEntityBlock {

    public static final MapCodec<SmartTemperatureChamberBlock> CODEC =
            simpleCodec(SmartTemperatureChamberBlock::new);

    /**
     * Create 那一位，原样引用。序列化名是 {@code blaze}（{@code EnumProperty.create("blaze", ...)}），
     * 取值 {@code none / smouldering / fading / kindled / seething}——方块状态 JSON 的变体键
     * 就照这几个名字写，见 {@code blockstates/smart_temperature_chamber.json}。
     */
    public static final EnumProperty<HeatLevel> HEAT_LEVEL = BlazeBurnerBlock.HEAT_LEVEL;

    public SmartTemperatureChamberBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(HEAT_LEVEL, HeatLevel.NONE));
    }

    @Override
    protected MapCodec<SmartTemperatureChamberBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HEAT_LEVEL);
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
     * 一整片连通块最多几格。超过就<b>整块都不共享</b>，每台退回只管自己那一桶。
     * <p>
     * 取 256 = 一整块 <b>16x16</b>：摆满 16x16 照样共享（正好卡在线上，不再往上放），
     * 再大就退回各自为政。上限之所以存在，是因为一组的代价随台数走：
     * 每一台都要独立算出"自己属于哪个矩形"（见 {@link #groupAt}，要在整块连通域上反复切矩形），
     * 每 tick 还要把整组成员扫一遍把燃料加总（见 {@code SmartTemperatureChamberBlockEntity}），
     * 摆满 256 台时这份加总是 O(台数²)。
     */
    public static final int MAX_COMPONENT_CELLS = 256;

    /**
     * 外接矩形的面积上限：给切矩形的算法兜底，也挡住"稀疏的一大片"
     * （256 格散在一张 256x256 的网里，每一台都要在那张网上切矩形）。
     * 16x16 正好是 256，所以摆满一整块 16x16 仍在这条线以内。
     */
    private static final int MAX_BOX_AREA = 256;

    /**
     * 从 {@code origin} 出发，算出它属于哪一组。
     * <p>
     * 规则：先在<b>同一个 Y 上、四向相邻</b>的整片连通域里切出一个<b>面积最大的全满矩形</b>，
     * 那一批算一组；把切走的格子拿掉，对剩下的再切一次，直到切到 {@code origin} 所在的那一个为止。
     * 于是连成一片的温控室会被划分成若干块矩形，<b>每块各自共享容量</b>。
     * <p>
     * 之所以不要求"整片就是一个矩形"：3x3 缺一个角这种很常见的摆法，外接矩形没填满，
     * 一刀切判否会让八台一台都不共享；切出它里面那个 2x3 才是玩家期待的。
     * <p>
     * 面积并列时按<b>先宽、后高、再取左上角靠前</b>来定（例如 2x3 与 3x2 都是 6，取宽的那个），
     * 保证同一块连通域无论从哪一格问起、切法都完全一样。
     * <p>
     * 垂直方向不算（上下叠着的不共享）；判定不成立或超上限时<b>只返回 {@code origin} 自己</b>，
     * 所以调用方拿到的列表一定可以直接用：容量 = 长度 × 单台容量，燃料 = 各格之和。
     * <p>
     * <b>方块实体与客户端的连接材质共用这一个方法</b>，两边必须得出同样的结果——
     * 连接纹理只在共享容量的那几台之间才画，靠的就是"同一个判据"。所以这里只读
     * {@link BlockGetter} 与方块状态，不去碰方块实体：客户端未必有实体，而方块状态两端都有。
     */
    public static List<BlockPos> groupAt(BlockGetter level, BlockPos origin) {
        List<BlockPos> solo = List.of(origin);
        if (level == null) {
            return solo;
        }

        // 1) 取连通域。
        //    探过的格子(seen)与真正的成员(members)必须分开：seen 里还躺着**探过但不是温控室**的
        //    邻居，拿它去铺网格会踩到外接矩形外面（曾经就这么崩过：Index -1 out of bounds）
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> members = new ArrayList<>();
        int minX = origin.getX();
        int maxX = minX;
        int minZ = origin.getZ();
        int maxZ = minZ;
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            members.add(current);
            minX = Math.min(minX, current.getX());
            maxX = Math.max(maxX, current.getX());
            minZ = Math.min(minZ, current.getZ());
            maxZ = Math.max(maxZ, current.getZ());
            // 一边走一边就判上限：真摆出一个 20x20 时不能先把整片扫完再放弃
            if (members.size() > MAX_COMPONENT_CELLS) {
                return solo;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(dir);
                if (seen.add(next) && isChamber(level, next)) {
                    queue.add(next);
                }
            }
        }

        int width = maxX - minX + 1;
        int depth = maxZ - minZ + 1;
        if (width * depth > MAX_BOX_AREA) {
            return solo;
        }

        // 2) 铺成一张网格，反复切最大全满矩形，直切到 origin 所在的那一个
        boolean[][] remaining = new boolean[width][depth];
        for (BlockPos pos : members) {
            remaining[pos.getX() - minX][pos.getZ() - minZ] = true;
        }
        int originX = origin.getX() - minX;
        int originZ = origin.getZ() - minZ;

        while (true) {
            Rect best = largestRectangle(remaining, width, depth);
            if (best == null) {
                return solo; // 走不到：origin 自己那一格总在某块矩形里
            }
            if (best.contains(originX, originZ)) {
                List<BlockPos> group = new ArrayList<>(best.area());
                for (int x = best.x; x < best.x + best.width; x++) {
                    for (int z = best.z; z < best.z + best.depth; z++) {
                        group.add(new BlockPos(x + minX, origin.getY(), z + minZ));
                    }
                }
                return group;
            }
            best.clearFrom(remaining);
        }
    }

    /**
     * 网格里面积最大的全满矩形。<b>单调栈</b>按行扫（每行维护"往上连续有多高"，再求直方图里的最大矩形），
     * 一行一次 O(宽 x 深)，比穷举所有左上/右下角快得多。
     * <p>
     * 并列时的取舍写死在 {@link #better} 里，这是"同一块连通域切法唯一"的根。
     * 不返回 {@code null} 除非整张网格都是空的。
     */
    @Nullable
    private static Rect largestRectangle(boolean[][] grid, int width, int depth) {
        int[] heights = new int[depth];
        Rect best = null;
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                heights[z] = grid[x][z] ? heights[z] + 1 : 0;
            }
            // 直方图求最大矩形；末尾补一个 0 把栈里剩的弹干净
            Deque<Integer> stack = new ArrayDeque<>();
            for (int col = 0; col <= depth; col++) {
                int barHeight = col == depth ? 0 : heights[col];
                while (!stack.isEmpty() && heights[stack.peek()] >= barHeight) {
                    int popped = stack.pop();
                    int height = heights[popped];
                    int left = stack.isEmpty() ? 0 : stack.peek() + 1;
                    Rect candidate = new Rect(x - height + 1, left, height, col - left);
                    if (better(candidate, best)) {
                        best = candidate;
                    }
                }
                stack.push(col);
            }
        }
        return best;
    }

    /** 两个候选谁更该被切走：先比面积，再比宽，再比高，最后取左上角靠前的 */
    private static boolean better(Rect candidate, @Nullable Rect current) {
        if (current == null) {
            return true;
        }
        if (candidate.area() != current.area()) {
            return candidate.area() > current.area();
        }
        if (candidate.width != current.width) {
            return candidate.width > current.width;
        }
        if (candidate.depth != current.depth) {
            return candidate.depth > current.depth;
        }
        if (candidate.x != current.x) {
            return candidate.x < current.x;
        }
        return candidate.z < current.z;
    }

    /** 网格坐标下的一块矩形（{@code x}/{@code z} 是左上角，{@code width}/{@code depth} 是格数） */
    private record Rect(int x, int z, int width, int depth) {

        int area() {
            return width * depth;
        }

        boolean contains(int px, int pz) {
            return px >= x && px < x + width && pz >= z && pz < z + depth;
        }

        void clearFrom(boolean[][] grid) {
            for (int gx = x; gx < x + width; gx++) {
                for (int gz = z; gz < z + depth; gz++) {
                    grid[gx][gz] = false;
                }
            }
        }
    }

    /** 这一格是不是温控室。用 {@code instanceof} 而不是比对注册表句柄，理由同 {@link #heatsBasin} */
    public static boolean isChamber(BlockGetter level, BlockPos pos) {
        return level.getBlockState(pos)
                .getBlock() instanceof SmartTemperatureChamberBlock;
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

    /**
     * 遮挡形状给空：这个方块<b>不参与面剔除</b>，它画的永远是模型本来的样子。
     * <p>
     * 模型不是整块实心——炉身四角各挖了一条竖槽、顶盖又比炉身收进 2 格，那张 casing 贴图本身
     * 还有三成像素是镂空的（那几片像素由模型文件里的 {@code render_type} 去挖，见 {@code ModBlocks}）
     * ——而默认的 {@code getShape}（也就是遮挡形状的默认来源）是整块实心立方体。原版的剔除判据是
     * "我这一面的遮挡形状整个被邻居盖住，就把这一面<b>所有</b>四边形一起丢掉"
     * （{@code Block.shouldRenderFace} 拿两边的 {@code getFaceOcclusionShape} 做差），
     * 于是整机会被当成实心方块：邻居的面被剔掉（从这个方块的凹槽看过去就是一个个洞），
     * 而它自己那些<b>凹进去</b>的面——槽壁、顶盖上收进去的内壁、上面压着东西时的那道台阶——
     * 也会被一起剔掉，那些面是看得见的。
     * <p>
     * {@code noOcclusion()}（见 {@code ModBlocks}）只关掉"我挡不挡别人"那一半；另一半判的是
     * <b>我自己的</b>遮挡形状，那一步根本不看 {@code canOcclude}，所以这里也要给空——
     * 空形状会让 {@code shouldRenderFace} 在第二个分支就返回 true。邻居若也是温控室，
     * 两边各自照常画自己的面（也就是两片贴在一起、法线相反的面，背面的那片被背面剔除吃掉，
     * 不会有深度冲突），和 Create 的流体储罐一样不为此写 {@code skipRendering}。
     * <p>
     * 只动遮挡：碰撞形状仍是默认的整块实心（{@code getOcclusionShape} 不参与碰撞），
     * 走路该撞还是撞。透光见下面那两位——它是<b>玻璃那样的透明方块</b>，不挡光。
     */
    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    /**
     * 光按玻璃那样整片穿过去。原版 {@code minecraft:glass} 就是靠这一位做到的
     * （{@code TransparentBlock} 里只覆写了 {@code propagatesSkylightDown}、{@code getShadeBrightness}
     * 与 {@code getVisualShape}，没碰 {@code getLightBlock}）。
     * <p>
     * 默认实现够不着：{@code propagatesSkylightDown} 的默认值是"形状不是整块实心"，
     * 而这里的形状（碰撞用的那个）偏偏是整块实心，于是默认只肯给 1 级衰减——树叶、水那一档的
     * 半透明，拿它砌的墙/地板仍会一层层把光吃掉。改成 true 之后不必再动
     * {@code getLightBlock}：那个默认实现走 {@code isSolidRender}，而我们已经声明不遮挡，
     * 于是它自己就得出 0（衰减为零）。这一位正好与染色玻璃 {@code TintedGlassBlock} 相反
     * ——那位是"看不见但挡光"，这里是"看得见也不挡光"。
     */
    @Override
    protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }
}
