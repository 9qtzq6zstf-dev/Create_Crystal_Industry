package com.minecart.yunxian.block;

import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * 泄压舱口：贴在任意方块面上的小舱门。目前只有外观，没有功能。
 * <p>
 * <b>朝向语义与晶簇完全一致</b>（基类 {@link AmethystClusterBlock} 就是原版晶簇）：
 * {@code FACING} 指<b>背离依附面</b>的那一侧，也就是舱口朝外开的方向；
 * 点哪个面就贴在哪个面（上下东西南北六向都能放），依附的那一格必须够硬
 * （{@code isFaceSturdy}），依附方块没了自己就掉。含水、掉落与支撑面判定一并继承自基类。
 * <p>
 * 碰撞箱由基类按朝向算成六向盒子，参数见 {@link #HEIGHT} 与 {@link #AABB_OFFSET}：
 * 沿朝向突出 7 像素、横截面 14×14，与模型外轮廓对齐。
 * <p>
 * <b>模型朝向——改 blockstate 或换模型前务必先读这段。</b>
 * 模型是从 Create 的 {@code item_hatch/block_closed} 改出来的，而那条老路把它画成了
 * <b>"朝 +Z（南）突出"</b>：安装板贴在北侧、网罩往南伸出去（网罩那张 {@code create:block/net}
 * 也只留了朝南的一面，正说明它是从南边看的）。所以 <b>不加任何旋转 = FACING 朝南</b>，
 * blockstate 里那张旋转表是围着这一点推出来的，读起来会有点反直觉：
 * <ul>
 *   <li>水平四面：南 0、西 90、北 180、东 270（<b>不是</b>常见的北 0 / 东 90）；</li>
 *   <li>上下两面：up 用 x=90、down 用 x=270。</li>
 * </ul>
 * 换句话说：这份模型不是"朝上"的，别照抄智能钻头那种 up 不旋转的表。
 * <p>
 * 想看朝向对不对，进游戏放一面墙、地板、天花板各一个即可：安装板应当贴着方块面，
 * 网罩朝外悬空；若网罩埋进方块里，就是旋转表整体差了一格 180 度。
 */
public class PressureReliefHatchBlock extends AmethystClusterBlock {

    /** 碰撞箱沿朝向的厚度（像素）：与模型网罩的深度一致（模型 z 0~2 是安装板、2~7 是网罩） */
    public static final float HEIGHT = 7.0F;

    /** 碰撞箱横截面四边的内缩（像素）：模型横截面 x、y 各留 1 像素，即 14×14 */
    public static final float AABB_OFFSET = 1.0F;

    public PressureReliefHatchBlock(BlockBehaviour.Properties properties) {
        super(HEIGHT, AABB_OFFSET, properties);
    }
}
