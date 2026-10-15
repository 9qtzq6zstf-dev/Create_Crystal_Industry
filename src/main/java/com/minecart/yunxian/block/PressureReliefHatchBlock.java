package com.minecart.yunxian.block;

import com.minecart.yunxian.blockentity.PressureReliefHatchBlockEntity;

import net.createmod.catnip.math.VoxelShaper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;

import javax.annotation.Nullable;

/**
 * 泄压舱口：贴在方块面上的小舱门，被风打到就把依附容器里的东西吐出来（逻辑见
 * {@link PressureReliefHatchBlockEntity}）。目前没有界面，也不进本体逻辑，
 * 方块这一半只管三件事：朝向与形状、依附判定、把方块实体挂上去。
 * <p>
 * <b>朝向语义与晶簇完全一致</b>（基类 {@link AmethystClusterBlock} 就是原版晶簇）：
 * {@code FACING} 指<b>背离依附面</b>的那一侧，也就是舱口朝外开的方向；
 * 点哪个面就贴在哪个面（上下东西南北六向都能放）。含水、掉落与形状旋转一并继承自基类。
 * <p>
 * <b>依附判定</b>比晶簇松：晶簇只认"够硬的面"（{@code isFaceSturdy}），
 * 而舱口的正事是从依附方块里取东西，所以外加一条——<b>自带物品容器的方块也算数</b>。
 * 这样箱子、木桶、带库存的机器这类"不是完整方块但能装东西"的方块也能挂。
 * 能力查询两端都注册（无 dist 门控），所以客户端对同一个位置给出的答案是一致的。
 * <p>
 * <b>碰撞箱分两块</b>（{@link #UP_SHAPE}）：模型是"一块 14×14 的薄安装板 + 一圈 12×12 的网罩"
 * 两团实体，合成一个盒子会把网罩中间那圈空心一起填成实心，所以照模型拆成两个盒子：
 * 安装板（x 1~15、z 1~15、厚 2）+ 网罩（x 2~14、z 2~14、y 2~7）。
 * <p>
 * <b>模型伸到方块外的那截凸台（y −2~0）不进碰撞箱</b>：它本来就是插进依附方块里的，
 * 依附方块自己已经实心，再给它一圈碰撞只会让舱口在相邻格子里多挡 2 像素。
 * <p>
 * 想看朝向对不对，进游戏放一面墙、地板、天花板各一个即可：安装板应当贴着方块面，
 * 网罩朝外悬空；若网罩埋进方块里，就是 blockstate 的旋转表整体差了一格 180 度。
 */
public class PressureReliefHatchBlock extends AmethystClusterBlock implements EntityBlock {

    /**
     * 碰撞箱：基状态（FACING=UP）的两个盒子，逐像素对齐模型的两团实体。
     * 其余五个朝向由 {@link VoxelShaper#forDirectional} 绕方块中心转出来，
     * 与本模组机器用的是同一套（见 {@code MechanicalCleanerBlock}）。
     */
    private static final VoxelShape UP_SHAPE = Shapes.or(
            // 安装板：模型 x 1~15、z 1~15（比网罩四周各宽 1 像素的一圈法兰）、厚 2
            Block.box(1, 0, 1, 15, 2, 15),
            // 网罩：模型 x 2~14、z 2~14，从 y 2 立到 y 7
            Block.box(2, 2, 2, 14, 7, 14));

    /** 六向碰撞箱：基状态朝上，其余方向由它转出来 */
    private static final VoxelShaper SHAPE = VoxelShaper.forDirectional(UP_SHAPE, Direction.UP);

    public PressureReliefHatchBlock(BlockBehaviour.Properties properties) {
        // 基类这两个参数（高度 / 内缩）只喂它自己那六个盒子，而本类覆写了 getShape，
        // 所以这里传的是一组"外轮廓大致对得上"的兜底值：万一有别的代码直接读基类那几个
        // protected 形状字段，拿到的也是 14×14、沿朝向突出 7 像素的合理形状。
        super(7.0F, 1.0F, properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE.get(state.getValue(FACING));
    }

    /**
     * 依附判定：背后的那一格够硬（原版晶簇那条），<b>或者</b>它自带物品容器。
     * 后一条是为了箱子这类"不是完整方块、装得了东西"的方块——舱口的正事就是从它里面取东西。
     */
    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos support = pos.relative(facing.getOpposite());
        if (level.getBlockState(support).isFaceSturdy(level, support, facing))
            return true;
        return level instanceof Level realLevel && hasItemContainer(realLevel, support, facing);
    }

    /** 该位置有没有面向舱口的物品容器（能力查询两端都注册，客户端结果一致） */
    private static boolean hasItemContainer(Level level, BlockPos support, Direction facing) {
        return level.getCapability(Capabilities.ItemHandler.BLOCK, support, facing.getOpposite()) != null;
    }

    // ==================== 方块实体 ====================

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PressureReliefHatchBlockEntity(pos, state);
    }

    /**
     * 两端都挂 ticker，吐货逻辑在方块实体里自己判服务端。
     * <p>
     * <b>不能只在服务端挂</b>：{@code SmartBlockEntity} 的 {@code initialize()}（内含
     * {@code forEachBehaviour(initialize)}）是在 {@code tick()} 里按需触发的，客户端不 tick
     * 就永远不会初始化，过滤器那一侧的客户端状态与渲染都会缺一块。
     */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof PressureReliefHatchBlockEntity hatch)
                hatch.tick();
        };
    }
}
