package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.block.SmartPressureReliefHatchBlock;
import com.minecart.yunxian.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 智能泄压舱口：与 {@link PressureReliefHatchBlockEntity} 完全一样，只多一件事——
 * 侧面过滤槽上带一个"数量"滑块，可以调每次吐几个。
 * <p>
 * 风场判定、过滤、掉落物、含水解包那些全在父类里，这里只接三根线：
 * <ul>
 *   <li>{@link #showsAmountSlider()}：把数量控件挂上（父类默认不挂）；</li>
 *   <li>{@link #batchSize(int)}：一批几个 = 滑块的值，再被"一组"（物品的堆叠数）压一头，
 *       免得 16 堆叠的东西一次吐 64 个；</li>
 *   <li>{@link #requiresFullBatch()}：接上滑块那档 "≤ / ="——"≤"（默认）能吐多少吐多少，
 *       "=" 则要求容器里凑够这一批才整批吐。</li>
 * </ul>
 * 滑块本身是 Create 过滤行为自带的数量，与动力吸尘器侧面那个槽是同一套控件。
 */
public class SmartPressureReliefHatchBlockEntity extends PressureReliefHatchBlockEntity {

    public SmartPressureReliefHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_PRESSURE_RELIEF_HATCH.get(), pos, state);
    }

    @Override
    protected boolean showsAmountSlider() {
        return true;
    }

    @Override
    protected String filterLabelKey() {
        return "create_crystal_industry.smart_pressure_relief_hatch.filter";
    }

    /**
     * 一批 = 滑块上的数量，但还要再受<span>物品自己的堆叠数</span>压一头：
     * 一批就是"一组"，16 堆叠的东西不该一次吐 64 个。控件本身已经把值夹在
     * 1 ~ 过滤物品的最大堆叠数（没设过滤物品时就是 64），这里再兜一道底，免得读到脏 NBT。
     */
    @Override
    protected int batchSize(int groupSize) {
        if (filtering == null)
            return 1;
        int amount = Math.max(1, Math.min(MAX_VENT_AMOUNT, filtering.getAmount()));
        return Math.min(amount, groupSize);
    }

    /** 滑块上那档 "=" 要求凑够一整批才吐；默认的 "≤" 档则是能吐多少吐多少 */
    @Override
    protected boolean requiresFullBatch() {
        return filtering != null && !filtering.upTo;
    }

    /** 红石锁：收到信号就不吐货（外观同时换成 {@code block_powered} 那套模型） */
    @Override
    protected boolean isLocked() {
        return getBlockState().getValue(SmartPressureReliefHatchBlock.POWERED);
    }
}
