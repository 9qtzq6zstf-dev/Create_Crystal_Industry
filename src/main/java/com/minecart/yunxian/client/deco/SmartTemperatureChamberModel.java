package com.minecart.yunxian.client.deco;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.block.SmartTemperatureChamberBlock;
import com.minecart.yunxian.registry.ModBlocks;
import com.simibubi.create.CreateClient;
import com.simibubi.create.foundation.block.connected.AllCTTypes;
import com.simibubi.create.foundation.block.connected.CTSpriteShiftEntry;
import com.simibubi.create.foundation.block.connected.CTSpriteShifter;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.CTContext;
import com.simibubi.create.foundation.block.connected.ConnectedTextureBehaviour.ContextRequirement;
import com.simibubi.create.foundation.block.connected.HorizontalCTBehaviour;
import com.simibubi.create.foundation.model.BakedModelWrapperWithData;
import com.simibubi.create.foundation.model.BakedQuadHelper;

import net.createmod.catnip.render.SpriteShiftEntry;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

/**
 * 智能温控室的连接材质：把方块的相邻情况画成"拼在一起就是一台大机器"。
 * <p>
 * 两块区域各用各的版式，因为美术给的两张图版式本来就不同：
 * <ul>
 *   <li><b>侧面</b>（模型里的 {@code 2} 号槽，炉身四周）：{@code smart_temperature_chamber_connected.png}
 *       是 <b>48x16 = 横排三格</b>——[左端封口][中间][右端封口]，而基础那张 16x16 是"两边都封口"。
 *       所以四个情况是：孤立用基础图，其余三种按左右邻居取某一列。</li>
 *   <li><b>顶/底面</b>（{@code 3} 号槽，黄铜底座）：{@code brass_block_connected.png} 是
 *       <b>64x64 = 4x4 格阵</b>，正好是 Create {@code AllCTTypes.RECTANGLE} 的版式，
 *       直接借它现成的 {@code getTextureIndex} 与 UV 换算。</li>
 * </ul>
 *
 * <h2>为什么不能直接用 {@code CTModel}</h2>
 * Create 的 {@link CTSpriteShiftEntry} 的 UV 换算是按 <b>{@code sheetSize x sheetSize} 方格阵</b>写的
 * （{@code getTargetV} 里除以 {@code sheetSize}），拿它算 3x1 会把 V 压成三分之一。
 * 而且它<b>永远拿连接版 sprite 顶掉基础图</b>，表达不了"孤立时回落到基础贴图"这第四种情况。
 * 所以这里承 {@link BakedModelWrapperWithData} 自己写：邻居情况照旧借 Create 的
 * {@link ConnectedTextureBehaviour#buildContext} 算（左右的定义、以及各个面朝哪边拧，
 * 照抄它的约定才和美术对得上），UV 自己乘。
 * <p>
 * 反过来"孤立时保留基础贴图"能成立，靠的是 {@link #gatherModelData} <b>不下发</b>那一面的索引——
 * 索引缺省是 -1，{@code getQuads} 见到 -1 就原样放过那个面。这与 {@code CTModel} 里
 * {@code index == -1 → continue} 是同一个口子。
 *
 * <h2>纯客户端</h2>
 * 整个类只在客户端加载（入口见 {@code ModRenderers}，而它自己由 {@code FMLEnvironment.dist.isClient()}
 * 把门）。方块状态里<b>没有</b>任何连接位，邻居关系是渲染时现查的，不落盘、不影响存档。
 */
public class SmartTemperatureChamberModel extends BakedModelWrapperWithData {

    /** 侧面连接图是横排三格、只有一行 */
    private static final int SIDE_COLUMNS = 3;
    private static final int SIDE_ROWS = 1;
    /** 黄铜图是 4x4 格阵，与 RECTANGLE 同规格 */
    private static final int BRASS_COLUMNS = AllCTTypes.RECTANGLE.getSheetSize();
    private static final int BRASS_ROWS = AllCTTypes.RECTANGLE.getSheetSize();

    /**
     * 侧面的一对 sprite。类型参数写 {@code HORIZONTAL} 只是为了让 {@code CTSpriteShifter} 收下它、
     * 顺带拿到可靠的重载刷新（{@code StitchedSprite} 自己会重新贴图）——<b>它的 UV 换算我们不调用</b>，
     * 用的是 {@link #rewrite} 里自己那套（见类注释：3x1 不是方格阵，算不了）。
     */
    private static final CTSpriteShiftEntry SIDE = CTSpriteShifter.getCT(AllCTTypes.HORIZONTAL,
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                    "block/smart_temperature_chamber/smart_temperature_chamber"),
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                    "block/smart_temperature_chamber/smart_temperature_chamber_connected"));

    /** 顶/底面的一对 sprite：基础图就是 Create 的黄铜块，连接图是美术给的那张 4x4 */
    private static final CTSpriteShiftEntry BRASS = CTSpriteShifter.getCT(AllCTTypes.RECTANGLE,
            ResourceLocation.fromNamespaceAndPath("create", "block/brass_block"),
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "block/common/brass_block_connected"));

    private static final ContextRequirement HORIZONTAL = ContextRequirement.builder()
            .horizontal()
            .build();
    private static final ContextRequirement AXIS_ALIGNED = ContextRequirement.builder()
            .axisAligned()
            .build();

    /**
     * 六个面各自的格号，每面 5 bit（0..15 是格号，0 表示"没有"——存进去时 +1，读出来 -1 = 不处理）。
     * 塞进一个 int 是为了让 {@link ModelData} 能按值比较，不用为每次渲染新建数组。
     */
    private static final ModelProperty<Integer> TILE_INDICES = new ModelProperty<>();

    public static void register() {
        CreateClient.MODEL_SWAPPER.getCustomBlockModels()
                .register(ModBlocks.SMART_TEMPERATURE_CHAMBER.getId(), SmartTemperatureChamberModel::new);
    }

    private SmartTemperatureChamberModel(BakedModel originalModel) {
        super(originalModel);
    }

    @Override
    protected ModelData.Builder gatherModelData(ModelData.Builder builder, BlockAndTintGetter world, BlockPos pos,
                                                BlockState state, ModelData blockEntityData) {
        // 一次全组扫描，四个方向共用；行为对象只活这一次调用，成员表就挂在它身上。
        // （模型是单例、被所有坐标共用，所以不能把这种"这一次的位置相关"状态存成字段）
        ConnectedTextureBehaviour context = new GroupAwareBehaviour(
                Set.copyOf(SmartTemperatureChamberBlock.groupAt(world, pos)));

        int packed = 0;
        for (Direction face : Direction.values()) {
            int tile;
            if (face.getAxis()
                    .isHorizontal()) {
                // 侧面：看这一面左右两边的邻居。孤立（两边都没有）时留 -1 = 不处理，保留基础贴图
                CTContext ct = context.buildContext(world, pos, state, face, HORIZONTAL);
                tile = !ct.left && !ct.right ? -1
                        : ct.left && ct.right ? 1
                        : ct.right ? 0 : 2;
            } else {
                // 顶/底面：4x4 那套有十六格，孤立也有对应的一格，所以恒有值
                CTContext ct = context.buildContext(world, pos, state, face, AXIS_ALIGNED);
                tile = AllCTTypes.RECTANGLE.getTextureIndex(ct);
            }
            packed |= (tile + 1) << (5 * face.get3DDataValue());
        }
        return builder.with(TILE_INDICES, packed);
    }

    /**
     * 只认<b>同一组</b>的邻居：共享容量的那几台之间才连成一片，拿它当"这几台确实算一组"的视觉凭证。
     * <p>
     * 判据与方块实体<b>共用</b> {@link SmartTemperatureChamberBlock#groupAt}——两边必须得出同样的结果，
     * 否则会出现"连着却不共享"或"共享却断开"。
     * <p>
     * 成员表在构造时按当前位置算好（一次扫描），所以下面那些邻居询问不再各扫一遍。
     * 其余判定（是不是同一种方块、有没有被挡住）仍旧交给父类，那是 Create 那套的既有行为。
     */
    private static class GroupAwareBehaviour extends HorizontalCTBehaviour {

        private final Set<BlockPos> members;

        private GroupAwareBehaviour(Set<BlockPos> members) {
            super(SIDE);
            this.members = members;
        }

        @Override
        public boolean connectsTo(BlockState state, BlockState other, BlockAndTintGetter reader, BlockPos pos,
                                  BlockPos otherPos, Direction face) {
            return members.contains(otherPos) && super.connectsTo(state, other, reader, pos, otherPos, face);
        }
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand, ModelData extraData,
                                    RenderType renderType) {
        List<BakedQuad> quads = super.getQuads(state, side, rand, extraData, renderType);
        if (!extraData.has(TILE_INDICES) || quads.isEmpty()) {
            return quads;
        }
        int packed = extraData.get(TILE_INDICES);
        TextureAtlasSprite sideBase = SIDE.getOriginal();
        TextureAtlasSprite sideTarget = SIDE.getTarget();
        TextureAtlasSprite brassBase = BRASS.getOriginal();
        TextureAtlasSprite brassTarget = BRASS.getTarget();
        if (sideBase == null || sideTarget == null || brassBase == null || brassTarget == null) {
            return quads;
        }

        List<BakedQuad> result = null;
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            int tile = ((packed >> (5 * quad.getDirection()
                    .get3DDataValue())) & 0x1F) - 1;
            if (tile < 0) {
                continue;
            }

            // 同一个面上混着好几种贴图（炉身 2、顶盖 4、黄铜 3……），只改自己认得的那两种
            TextureAtlasSprite source = quad.getSprite();
            boolean isSide = source == sideBase;
            boolean isBrass = !isSide && source == brassBase;
            if (!isSide && !isBrass) {
                continue;
            }

            int columns = isSide ? SIDE_COLUMNS : BRASS_COLUMNS;
            int rows = isSide ? SIDE_ROWS : BRASS_ROWS;
            TextureAtlasSprite target = isSide ? sideTarget : brassTarget;
            int column = tile % columns;
            int row = tile / columns;

            BakedQuad rewritten = BakedQuadHelper.clone(quad);
            int[] vertices = rewritten.getVertices();
            for (int vertex = 0; vertex < 4; vertex++) {
                // 基础 sprite 里的 0..1 局部坐标，加上"第几格"之后再缩回整张图。
                // U 除列数、V 除行数——**两者不一定相等**：侧面那张是 3 列 1 行，
                // 当初图省事两边都除列数，V 就被压进 sprite 顶部 1/3（正好是透明像素，
                // solid 渲染下层直接黑掉一整条）
                float localU = SpriteShiftEntry.getUnInterpolatedU(source, BakedQuadHelper.getU(vertices, vertex));
                float localV = SpriteShiftEntry.getUnInterpolatedV(source, BakedQuadHelper.getV(vertices, vertex));
                BakedQuadHelper.setU(vertices, vertex, target.getU((column + localU) / columns));
                BakedQuadHelper.setV(vertices, vertex, target.getV((row + localV) / rows));
            }
            if (result == null) {
                result = new ArrayList<>(quads);
            }
            result.set(i, rewritten);
        }
        return result == null ? quads : result;
    }
}
