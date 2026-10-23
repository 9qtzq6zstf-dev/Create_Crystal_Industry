package com.minecart.yunxian.block;

import com.minecart.yunxian.blockentity.SmartTemperatureChamberBlockEntity;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
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
 * <p>
 * <b>扳手</b>（{@link IWrenchable}）：只认<b>潜行</b>右键的快捷拆除，掉落物直接进背包并播放
 * 机械动力的拆卸音效；走的是 {@code IWrenchable} 默认实现（{@code Block.getDrops} +
 * {@code destroyBlock}），同时会发 {@code BlockEvent.BreakEvent}，领地保护照常生效。
 * <p>
 * <b>不提供扳手转向</b>（覆写成 {@link InteractionResult#PASS}）：温控室没有朝向属性，
 * 默认实现转完还是同一个状态，只会白吃掉一次点击再播一遍拧动的音效。
 */
public class SmartTemperatureChamberBlock extends BaseEntityBlock implements IWrenchable {

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

    /**
     * 扳手右键不作为，只放行潜行拆除（{@code IWrenchable#onSneakWrenched}）。
     * <p>
     * 默认实现会绕点击面转朝向；温控室没有朝向属性，转完仍是同一状态。这里直接 PASS，
     * 把点击还给原版流程，免得玩家拧一下以为没反应、实际白白转了一圈。
     */
    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        return InteractionResult.PASS;
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
     * <b>分组是记下来的，不是现算的</b>：每台温控室的方块实体里存着一个"组锚点"，
     * 同一组各格记的值相同。放下时新格子只把自己<b>并进一块已经存在的矩形</b>
     * （判据："加进这一格之后那一块仍然是矩形"，见 {@link #assignGroupOnPlace}），
     * 已经连好的那几块因此谁也拆不开——新格子要么只是把某一块撑大，要么自成一格。
     * <p>
     * 兜底：<b>这一格自己</b>还没拿到锚点时（老存档第一次进来、客户端这块还没同步到、
     * 思索世界的方块实体不走 {@code onLoad}），退回 {@link #canonicalGroup} 那套纯函数切法
     * （标准朝向 + 整行/整列长矩形），拿它当临时画面。
     * <p>
     * 反过来，自己锚点齐全、只是某个<b>邻居</b>的锚点还在路上时，绝不做整片重切——只把那几格
     * 暂时当作谁都连不上。整片重切会把已经连好的几台重新切开，那是"新放一台抢走一格"的成因。
     * <p>
     * 之所以不要求"整片就是一个矩形"：3x3 缺一个角这种很常见的摆法，外接矩形没填满，
     * 一刀切判否会让八台一台都不共享；切出它里面那块矩形才是玩家期待的。
     * <p>
     * 垂直方向不算（上下叠着的不共享）；判定不成立或超上限时<b>只返回 {@code origin} 自己</b>，
     * 所以调用方拿到的列表一定可以直接用：容量 = 长度 × 单台容量，燃料 = 各格之和。
     * <p>
     * <b>方块实体与客户端的连接材质共用这一个方法</b>，两边必须得出同样的结果——
     * 连接纹理只在共享容量的那几台之间才画，靠的就是"同一个判据"。判据就是上面那个锚点，
     * 所以这里要读方块实体（{@link BlockGetter} 本来就带 {@code getBlockEntity}）。
     */
    public static List<BlockPos> groupAt(BlockGetter level, BlockPos origin) {
        List<BlockPos> solo = List.of(origin);
        if (level == null) {
            return solo;
        }
        Component component = componentAt(level, origin);
        if (component == null) {
            return solo;
        }

        BlockPos mine = anchorAt(level, origin);
        if (mine == null) {
            // 只有"这一格自己还没拿到锚点"才回退纯函数切法——此时这片连通域在客户端等于还没分组，
            // 拿纯函数切出来的形状当临时画面是对的。反过来（自己锚点齐全、只是邻居没到位）绝不回退。
            return canonicalGroup(component, origin);
        }
        List<BlockPos> group = new ArrayList<>();
        for (BlockPos member : component.members()) {
            BlockPos anchor = anchorAt(level, member);
            if (anchor == null) {
                // 这一格的锚点还没送来（同步包在路上）：它暂时谁也不连，下一帧就会自己好。
                // 绝不能在这里退回整片重切——那会把已经连好的几台重新切开，正是"新放一台抢走一格"的成因。
                continue;
            }
            if (anchor.equals(mine)) {
                group.add(member);
            }
        }
        return group.isEmpty() ? solo : group;
    }

    /**
     * 纯函数切法：标准朝向 + 整行/整列长矩形，返回 {@code origin} 所在的那一片。
     * 老存档补分组、兜底、拆掉一格后重切都用它；只看方块状态，两端算出来一致。
     */
    public static List<BlockPos> canonicalGroup(BlockGetter level, BlockPos origin) {
        Component component = componentAt(level, origin);
        return component == null ? List.of(origin) : canonicalGroup(component, origin);
    }

    private static List<BlockPos> canonicalGroup(Component component, BlockPos origin) {
        for (List<BlockPos> piece : canonicalPieces(component)) {
            if (piece.contains(origin)) {
                return piece;
            }
        }
        return List.of(origin); // 走不到：origin 自己那一格总在某块矩形里
    }

    /**
     * 把这一片切成若干矩形：先摆成"标准朝向"，在标准朝向上从坐标最小的格子起长矩形，
     * 只按整行/整列吃（长满一条边才并进来），切完按映射写回原坐标。
     * <p>
     * 世界坐标的东南西北不该影响分组——同一片方块整体转个方向、换个角摆，必须切出同样的分法，
     * 所以要先挑标准朝向（见 {@link #canonicalOrientation}）。
     */
    private static List<List<BlockPos>> canonicalPieces(Component component) {
        List<BlockPos> members = component.members();
        int minX = component.minX();
        int minZ = component.minZ();
        Orientation canonical = canonicalOrientation(members, minX, minZ,
                component.width(), component.depth());

        boolean[][] remaining = new boolean[canonical.outWidth()][canonical.outDepth()];
        int[][] fromX = new int[canonical.outWidth()][canonical.outDepth()];
        int[][] fromZ = new int[canonical.outWidth()][canonical.outDepth()];
        int y = members.get(0).getY();
        for (BlockPos pos : members) {
            int[] at = canonical.forward(pos.getX() - minX, pos.getZ() - minZ);
            remaining[at[0]][at[1]] = true;
            fromX[at[0]][at[1]] = pos.getX();
            fromZ[at[0]][at[1]] = pos.getZ();
        }

        List<List<BlockPos>> pieces = new ArrayList<>();
        Rect rect;
        while ((rect = growFromSmallest(remaining, canonical.outWidth(), canonical.outDepth())) != null) {
            List<BlockPos> piece = new ArrayList<>(rect.area());
            for (int x = rect.x; x < rect.x + rect.width; x++) {
                for (int z = rect.z; z < rect.z + rect.depth; z++) {
                    piece.add(new BlockPos(fromX[x][z], y, fromZ[x][z]));
                }
            }
            pieces.add(piece);
            rect.clearFrom(remaining);
        }
        return pieces;
    }

    /**
     * 取连通域（同一个 Y 上、四向相邻）与它的外接矩形。超过上限
     * （{@link #MAX_COMPONENT_CELLS} / {@link #MAX_BOX_AREA}）时返回 {@code null}，
     * 调用方退回"只管自己那一格"。
     */
    @Nullable
    private static Component componentAt(BlockGetter level, BlockPos origin) {
        // 探过的格子(seen)与真正的成员(members)必须分开：seen 里还躺着**探过但不是温控室**的
        // 邻居，拿它去铺网格会踩到外接矩形外面（曾经就这么崩过：Index -1 out of bounds）
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
                return null;
            }
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(dir);
                if (seen.add(next) && isChamber(level, next)) {
                    queue.add(next);
                }
            }
        }
        if ((maxX - minX + 1) * (maxZ - minZ + 1) > MAX_BOX_AREA) {
            return null;
        }
        return new Component(members, minX, maxX, minZ, maxZ);
    }

    /** 把一堆格子包成"一片"（外接矩形自己算）。拆掉一格后重切时用，调用方保证它们本来就连通 */
    private static Component componentOf(List<BlockPos> cells) {
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : cells) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        return new Component(cells, minX, maxX, minZ, maxZ);
    }

    /** 一片连通域：成员 + 外接矩形 */
    private record Component(List<BlockPos> members, int minX, int maxX, int minZ, int maxZ) {

        int width() {
            return maxX - minX + 1;
        }

        int depth() {
            return maxZ - minZ + 1;
        }
    }

    /** 这一格的组锚点；不是温控室、或它的方块实体还没到位时为 {@code null} */
    @Nullable
    private static BlockPos anchorAt(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof SmartTemperatureChamberBlockEntity chamber
                ? chamber.groupAnchor()
                : null;
    }

    /** 一组的"标准锚点"：最小 x、其次最小 z 的那一格。补分组（老存档、拆后重切）用它，两端算出来一致 */
    public static BlockPos anchorOf(List<BlockPos> group) {
        BlockPos best = group.get(0);
        for (BlockPos pos : group) {
            if (pos.getX() < best.getX() || (pos.getX() == best.getX() && pos.getZ() < best.getZ())) {
                best = pos;
            }
        }
        return best;
    }

    /**
     * 从 {@code from} 出发，把同一片连通域里锚点等于 {@code anchor} 的那些格子收出来。
     * 一组按定义就是一个矩形，所以这就是"那一组的全部格子"。
     */
    private static List<BlockPos> groupOf(BlockGetter level, BlockPos from, BlockPos anchor) {
        List<BlockPos> group = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(from);
        queue.add(from);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            group.add(current);
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(dir);
                if (seen.add(next) && anchor.equals(anchorAt(level, next))) {
                    queue.add(next);
                }
            }
        }
        return group;
    }

    /** 这一堆格子是不是正好摆满一个矩形（不多不少） */
    private static boolean isFullRectangle(List<BlockPos> cells) {
        if (cells.isEmpty()) {
            return false;
        }
        Set<BlockPos> set = new HashSet<>(cells);
        if (set.size() != cells.size()) {
            return false; // 有重复：两组叠在一起了（不该发生，保守判否）
        }
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : cells) {
            minX = Math.min(minX, pos.getX());
            maxX = Math.max(maxX, pos.getX());
            minZ = Math.min(minZ, pos.getZ());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        if ((maxX - minX + 1) * (maxZ - minZ + 1) != cells.size()) {
            return false;
        }
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!set.contains(new BlockPos(x, cells.get(0).getY(), z))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 把某一格的组锚点写进方块实体，并让客户端跟着知道（连接材质要拿它跟邻居比） */
    private static void setAnchor(Level level, BlockPos pos, BlockPos anchor) {
        if (level.getBlockEntity(pos) instanceof SmartTemperatureChamberBlockEntity chamber) {
            chamber.setGroupAnchor(anchor);
            chamber.dropGroupCache();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * 把整组的渲染各刷一次。
     * <p>
     * 连接材质拿"同组各格的组锚点"现算，所以任何一格并入或拆出，<b>同组每一台</b>的模型数据都过期了。
     * 只刷紧挨着的那几台是不够的：三台并排时，中间那台能收到两边的更新，最边上那台什么都收不到，
     * 它的边框会停在"这一组到这里就结束"的旧样子——截图里第二、三台之间的那道缝就是这么来的。
     * <p>
     * 方块状态没变（变的只是方块实体里的锚点），光靠方块实体那条同步路不会让客户端重建网格，
     * 所以这里逐格喊一次 {@code sendBlockUpdated}；客户端收到后会重新取一次模型数据、并把所在区块段
     * 标脏重画（见方块实体的 {@code loadAdditional} 与 {@code ClientModelRefresh}）。
     */
    private static void refreshRender(Level level, List<BlockPos> members) {
        for (BlockPos member : members) {
            if (isChamber(level, member)) {
                updateForRender(level, member);
            }
        }
    }

    /** 状态不变也发一次方块更新：客户端借这一下重新取模型数据 */
    private static void updateForRender(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
    }

    /**
     * 放下这一格时定它归哪一组。两条规矩，都是为了<b>已经连好的那几块谁也拆不开</b>：
     * <ol>
     *   <li><b>不许吃别人内部的格子</b>：并进某一块之前先看这一格是不是<b>别的</b>块的"关键格"
     *       （拿掉它就散成两块，见 {@link #isArticulation}）。是就整块跳过。这一条最隐蔽——
     *       "新格子并进 A 块"看着只动了 A，但它落点若正好在 B 块内部，B 当场裂成两半；
     *       两条 1x3 十字相交正是这种情形（中间那格属于竖的那条，横的那条想把它拉过去，
     *       竖的那条就断成两截）。宁可自成一格，也不拆别人的。</li>
     *   <li><b>只并"整块"，不并"半块"</b>：候选只有两种——并进四邻里的<b>某一块</b>，
     *       或把四邻里那几块<b>整体</b>并进来。整体并进来要求并完之后正好是一个矩形，
     *       也就是新形成的结构把已有的那几块完整包住了；这就是判据里"除非新形成的结构
     *       包含了已有的连接"那半句。剔掉"并两块中的一部分"那种可能，才不会把原有归属改花。</li>
     * </ol>
     * 每个候选都还要过 {@link #isFullRectangle}（并完必须正好摆满一个矩形，没有缺角）。
     * 通过的在其中取<b>最大</b>的那一个——于是"两块都能并"时优先接最长的那条；
     * 都不通过就<b>自成一格</b>，已有分组一格不动。
     */
    private static void assignGroupOnPlace(Level level, BlockPos pos) {
        List<List<BlockPos>> neighbours = new ArrayList<>(4);
        List<BlockPos> anchors = new ArrayList<>(4);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            BlockPos anchor = anchorAt(level, side);
            if (anchor == null) {
                continue;
            }
            List<BlockPos> group = groupOf(level, side, anchor);
            boolean known = false;
            for (List<BlockPos> seen : neighbours) {
                if (seen.equals(group)) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                neighbours.add(group);
                anchors.add(anchor);
            }
        }

        List<BlockPos> best = null;
        BlockPos bestAnchor = null;

        // 候选一：并进四邻里单独某一块
        for (int i = 0; i < neighbours.size(); i++) {
            List<BlockPos> combined = new ArrayList<>(neighbours.get(i));
            combined.add(pos);
            if (isFullRectangle(combined) && (best == null || combined.size() > best.size())) {
                best = combined;
                bestAnchor = anchors.get(i);
            }
        }

        // 候选二：把四邻里那几块整体并进来。只有"包住了它们"（并完是个满矩形）才成立，
        // 这正是"新形成的结构包含了已有的连接"；四邻只有一块时与候选一重合，不必重复试
        if (neighbours.size() > 1) {
            List<BlockPos> union = new ArrayList<>();
            union.add(pos);
            for (List<BlockPos> group : neighbours) {
                union.addAll(group);
            }
            if (isFullRectangle(union) && !breaksNeighbourGroup(pos, neighbours)) {
                if (best == null || union.size() > best.size()) {
                    best = union;
                    bestAnchor = anchors.get(0);
                }
            }
        }

        if (best == null) {
            setAnchor(level, pos, pos);
            return;
        }
        for (BlockPos cell : best) {
            setAnchor(level, cell, bestAnchor);
        }
    }

    /**
     * 这一格并进来会不会把四邻里某一块拆散。
     * <p>
     * 只看"这一格少掉之后那一块还连不连通"：一块里只要有一格被拿走之后就够不着同块的其余格子，
     * 那就是把它切成两半。上面两条规矩共用这一个判据。
     */
    private static boolean breaksNeighbourGroup(BlockPos pos, List<List<BlockPos>> neighbours) {
        for (List<BlockPos> group : neighbours) {
            if (group.size() > 1 && group.contains(pos) && isArticulation(group, pos)) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@code cell} 是不是 {@code group} 里的"关键格"：把它拿掉之后，这一组会断成不止一块。
     * <p>
     * 从这一组里任意一个剩下的格子洪泛一遍，看能不能走到其余所有格子。一组本来就是一个矩形，
     * 所以除了"从内部拿走一格"，其余任何一格拿掉都还是连通的（角、边都只是让矩形变小）。
     */
    private static boolean isArticulation(List<BlockPos> group, BlockPos cell) {
        List<BlockPos> rest = new ArrayList<>(group.size());
        for (BlockPos member : group) {
            if (!member.equals(cell)) {
                rest.add(member);
            }
        }
        if (rest.isEmpty()) {
            return false;
        }
        // 同一组必在同一个 Y 上（分组不看垂直方向），所以平面内的四向就够
        Set<BlockPos> reachable = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        reachable.add(rest.get(0));
        queue.add(rest.get(0));
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                BlockPos next = current.relative(dir);
                // 只走本来就在这一组里的格子（用集合查，不看洪泛到没到过）
                if (!rest.contains(next) || !reachable.add(next)) {
                    continue;
                }
                queue.add(next);
            }
        }
        return reachable.size() != rest.size();
    }

    /**
     * 拆掉一格之后善后：它原来那一组剩下的格子不再是一个矩形，所以只把<b>那一组</b>重切一次
     * （用纯函数那套），其余各组原样不动。切完给每一片重新定锚点（取该片最小 x/z 的那一格）。
     * <p>
     * 残余是从四邻找出来的：邻居里同锚点的那些格，就是这一组去掉 {@code pos} 之后剩下的
     * ——方块这时已经被拆掉了，扫连通域不会再包含它。
     * <p>
     * 返回重切过的那些格（也就是还活着的那一组），调用方拿它去刷渲染：重定锚点之后，
     * 每一片的连接材质都变了。
     */
    private static List<BlockPos> regroupAfterRemoval(Level level, BlockPos pos) {
        List<BlockPos> survivors = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        // 拆掉的那一格自己要排掉：方块实体可能还留着（取决于 onRemove 与方块实体移除的先后），
        // 它留着的话会被当成"残余"里的一格，切出来又把它算进去
        seen.add(pos);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos side = pos.relative(dir);
            BlockPos anchor = anchorAt(level, side);
            if (anchor == null || seen.contains(side)) {
                continue;
            }
            for (BlockPos cell : groupOf(level, side, anchor)) {
                if (seen.add(cell)) {
                    survivors.add(cell);
                }
            }
        }
        if (survivors.isEmpty()) {
            return List.of();
        }
        for (List<BlockPos> piece : canonicalPieces(componentOf(survivors))) {
            BlockPos anchor = anchorOf(piece);
            for (BlockPos cell : piece) {
                setAnchor(level, cell, anchor);
            }
        }
        return survivors;
    }

    /**
     * 在这一片的 8 种朝向（旋转 4 种 x 翻转 2 种）里挑"标准朝向"：
     * 每种朝向下把格子压成一行一个 bit 掩码，逐行比大小，取最小的那一种
     * （并列时取枚举里靠前的，保证同一片方块无论从哪一格问起都挑到同一个）。
     * <p>
     * 挑标准朝向是为了让分组<b>与朝向无关</b>：同一片方块整体转 90 度、或者翻个面摆，
     * 分组必须一模一样——不然同一个摆法只是换了个角，"1x3 保不保得住"就变了。
     */
    private static Orientation canonicalOrientation(List<BlockPos> members, int minX, int minZ,
                                                    int width, int depth) {
        Orientation best = null;
        long[] bestRows = null;
        for (int variant = 0; variant < Orientation.VARIANTS; variant++) {
            Orientation candidate = new Orientation(variant, width, depth);
            long[] rows = new long[candidate.outWidth()];
            for (BlockPos pos : members) {
                int[] at = candidate.forward(pos.getX() - minX, pos.getZ() - minZ);
                rows[at[0]] |= 1L << at[1];
            }
            if (best == null || compareRows(rows, bestRows) < 0) {
                best = candidate;
                bestRows = rows;
            }
        }
        return best;
    }

    /** 逐行比 bit 掩码；行数不同时短的那个算小 */
    private static int compareRows(long[] a, long[] b) {
        for (int i = 0; i < a.length && i < b.length; i++) {
            if (a[i] != b[i]) {
                return Long.compare(a[i], b[i]);
            }
        }
        return Integer.compare(a.length, b.length);
    }

    /**
     * 连通域的一种朝向：{@code forward} 把"外接矩形内的相对坐标"映射到该朝向下的坐标。
     * <p>
     * 后四种是转置（x/z 对调），所以外接矩形的宽高也跟着换，见 {@link #outWidth()}。
     */
    private record Orientation(int variant, int sourceWidth, int sourceDepth) {

        /** 旋转 4 种 x 翻转 2 种 */
        static final int VARIANTS = 8;

        int outWidth() {
            return variant < 4 ? sourceWidth : sourceDepth;
        }

        int outDepth() {
            return variant < 4 ? sourceDepth : sourceWidth;
        }

        int[] forward(int x, int z) {
            return switch (variant) {
                case 0 -> new int[] {x, z};
                case 1 -> new int[] {sourceWidth - 1 - x, z};
                case 2 -> new int[] {x, sourceDepth - 1 - z};
                case 3 -> new int[] {sourceWidth - 1 - x, sourceDepth - 1 - z};
                case 4 -> new int[] {z, x};
                case 5 -> new int[] {z, sourceWidth - 1 - x};
                case 6 -> new int[] {sourceDepth - 1 - z, x};
                default -> new int[] {sourceDepth - 1 - z, sourceWidth - 1 - x};
            };
        }
    }

    /**
     * 取网格里<b>坐标最小</b>（先 x 后 z）的那个还没被分走的格子当锚点，从它长出一个矩形：
     * 每一步只吃<b>一整行或一整列</b>（那一条必须整条都还没被分走），两个方向都吃不下去时就长完了。
     * <p>
     * 优先方向取<b>锚点那一行与那一列谁更长</b>（一样长时先走 x）。这一条是为"一条长龙"定的：
     * 摆成 1x3 之后在它侧面补上两格，锚点处的行比列长，于是先沿行吃满、得到 1x3 + 1x2，
     * 而不是"面积最大的矩形优先"那种切法给出的 2x2 + 1x1 —— 后者会把原来那条 1x3 拆开。
     * 同理，整块矩形补满一整行/一整列时仍然是同一个矩形（长出新的一整条而已）。
     * <p>
     * 因为只吃"整条"，所以每一块的两条边都贴着已经填满的一整行/列，切法对同一块连通域唯一，
     * 与查询点无关（{@link #groupAt} 保证同一组各格算出的是同一份名单）。
     * 不返回 {@code null} 除非整张网格都是空的。
     */
    @Nullable
    private static Rect growFromSmallest(boolean[][] grid, int width, int depth) {
        int anchorX = -1;
        int anchorZ = -1;
        for (int x = 0; x < width && anchorX < 0; x++) {
            for (int z = 0; z < depth; z++) {
                if (grid[x][z]) {
                    anchorX = x;
                    anchorZ = z;
                    break;
                }
            }
        }
        if (anchorX < 0) {
            return null;
        }

        int runX = 0;
        while (anchorX + runX < width && grid[anchorX + runX][anchorZ]) {
            runX++;
        }
        int runZ = 0;
        while (anchorZ + runZ < depth && grid[anchorX][anchorZ + runZ]) {
            runZ++;
        }
        boolean preferX = runX >= runZ;

        int x = anchorX;
        int z = anchorZ;
        int w = 1;
        int d = 1;
        while (true) {
            boolean grew = false;
            for (int pass = 0; pass < 2 && !grew; pass++) {
                boolean horizontal = (pass == 0) == preferX;
                if (horizontal) {
                    if (x + w < width && columnIsFree(grid, x + w, z, d)) {
                        w++;
                        grew = true;
                    }
                } else if (z + d < depth && rowIsFree(grid, x, z + d, w)) {
                    d++;
                    grew = true;
                }
            }
            if (!grew) {
                return new Rect(x, z, w, d);
            }
        }
    }

    /** 这一整列（z 起、深 d 格）是不是都还没被分走 */
    private static boolean columnIsFree(boolean[][] grid, int x, int z, int depth) {
        for (int i = 0; i < depth; i++) {
            if (!grid[x][z + i]) {
                return false;
            }
        }
        return true;
    }

    /** 这一整行（x 起、宽 w 格）是不是都还没被分走 */
    private static boolean rowIsFree(boolean[][] grid, int x, int z, int width) {
        for (int i = 0; i < width; i++) {
            if (!grid[x + i][z]) {
                return false;
            }
        }
        return true;
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
        assignGroupOnPlace(level, pos);
        refreshRender(level, groupAt(level, pos));
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
            refreshRender(level, regroupAfterRemoval(level, pos));
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
