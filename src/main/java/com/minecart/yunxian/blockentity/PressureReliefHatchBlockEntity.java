package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.behaviour.PressureReliefHatchValueBoxTransform;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.List;

/**
 * 泄压舱口：被风打到就把"附着容器"里的东西吐出来。
 * <p>
 * <b>风从哪来。</b>本格自己不是风源，只能去找别人的风场——沿六个轴向逐格外走，
 * 找到一台 {@link IAirCurrentSource}，看它的风场盒子（{@code airCurrent.bounds}）
 * 是否与本格相交。用"相交"而不是"包含本格中心"，是因为 Create 的气流遇到挡风方块时
 * 只推进到<span>块内一点点</span>（{@code getFlowLimit} 返回 i + 形状深度 + 1/32），
 * 盒子只切进本格一条缝，用点判断会漏掉"正对吹过来"这一整类。
 * <p>
 * <b>放不放。</b>把风向（{@code pushing ? direction : direction.getOpposite()}）与舱口的
 * {@code FACING}（背离依附面那一侧）比一比：
 * <ul>
 *   <li>垂直于朝向——风从侧面扫过网罩，放；</li>
 *   <li>沿朝向朝外——相当于把舱口里的东西往外抽，放；</li>
 *   <li>沿朝向朝里（吹入）——气压把舱口压住，不放。</li>
 * </ul>
 * 顺带一提：泄压舱口自己的碰撞箱在垂直于朝向的方向上是"有洞的"（网罩那圈中间是空的），
 * Create 的 `findMaxDepth` 判定有洞就整格放行，所以侧风是真的穿过本格，不是吹到脸上。
 * <p>
 * <b>吐多少。</b>一批最多 {@link #VENT_AMOUNT} 个（<b>16</b>，对齐原版溜槽），再受"一组"压一头：
 * 按待吐物品里最小的那个堆叠数走（16 堆叠的东西一次最多 16 个，混装时取最严的上限）。
 * 侧面那个槽<b>只做过滤</b>，没有数量控件 —— 要调"每次吐几个"、并且能把上限抬到
 * {@link #SMART_VENT_AMOUNT}（64）的是子类
 * {@code SmartPressureReliefHatchBlockEntity}（智能泄压舱口），它把数量滑块加回来，
 * 并通过覆写 {@link #batchSize(int)} / {@link #requiresFullBatch()} 接上这里的吐货逻辑。
 * 节奏跟着风源转速走（间隔 = max(2, 100/转速) tick，与动力吸尘器吹出同一套公式）；
 * 风源不是动能方块（比如喷嘴后面那台机器缺动力、或者干脆是另一台吸尘器）时退回
 * {@link #FALLBACK_INTERVAL}。
 */
public class PressureReliefHatchBlockEntity extends SmartBlockEntity {

    /** 风场扫描间隔（tick）。风起风落最多迟这么久，一秒足够 */
    private static final int SCAN_INTERVAL = 20;

    /** 风源给不出转速时的兜底节奏（tick / 批） */
    private static final int FALLBACK_INTERVAL = 10;

    /**
     * 本类一批的上限：<b>16 个</b>，对齐原版溜槽（{@code ChuteBlockEntity.getExtractionAmount()} 也是 16）。
     * 它同时是混装时取"最小堆叠数"的初值，所以 64 堆叠的物品在这里也只会一次吐 16 个。
     */
    protected static final int VENT_AMOUNT = 16;

    /**
     * 智能舱口（带数量滑块）才够得着的天花板：<b>64 个</b>，对齐智能溜槽
     * （{@code SmartChuteBlockEntity.getExtractionAmount()} 在滑块未调时兜底 64）。
     * 基类不带滑块，碰不到这个数。
     */
    protected static final int SMART_VENT_AMOUNT = 64;

    /** 侧面 4 个槽共用同一个过滤器，只有过滤通过的物品才会被吐出来；子类要用它读数量 */
    protected FilteringBehaviour filtering;

    /** 当前"打得到本格"的风场，null = 没风；每 {@link #SCAN_INTERVAL} tick 重扫一次 */
    private AirCurrent activeCurrent;

    private int scanCooldown;
    private int ventCooldown;

    public PressureReliefHatchBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.PRESSURE_RELIEF_HATCH.get(), pos, state);
    }

    /** 子类注册自己的方块实体类型用（{@code BlockEntityType.Builder} 认的是那个两参构造器） */
    protected PressureReliefHatchBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        filtering = new FilteringBehaviour(this, new PressureReliefHatchValueBoxTransform())
                .withCallback(stack -> sendData());
        // 数量滑块只有子类（智能泄压舱口）要，本类一批就吐"一组"，滑块没有意义
        if (showsAmountSlider())
            filtering.showCount();
        filtering.setLabel(Component.translatable(filterLabelKey()));
        behaviours.add(filtering);
    }

    /** 侧面槽上是否带"数量"滑块。泄压舱口不带；智能泄压舱口覆写成 true */
    protected boolean showsAmountSlider() {
        return false;
    }

    /**
     * 是否被红石锁住（锁住就不吐货）。泄压舱口没有红石锁，恒 false；
     * 智能泄压舱口覆写成读 {@code POWERED}。
     */
    protected boolean isLocked() {
        return false;
    }

    /** 侧面过滤槽的标题翻译键 */
    protected String filterLabelKey() {
        return "create_crystal_industry.pressure_relief_hatch.filter";
    }

    @Override
    public void tick() {
        super.tick();

        // ticker 两端都挂（客户端要靠它触发 SmartBlockEntity 的 initialize），
        // 但找风场与吐货是纯服务端的事
        if (level == null || level.isClientSide)
            return;

        // 红石锁：不吐货。顺手把风场缓存清掉，免得解锁后拿着一个过期风场先用一拍
        if (isLocked()) {
            activeCurrent = null;
            return;
        }

        if (scanCooldown-- <= 0) {
            scanCooldown = SCAN_INTERVAL;
            activeCurrent = findOpeningAirCurrent();
        }
        if (activeCurrent == null)
            return;

        if (ventCooldown > 0) {
            ventCooldown--;
            return;
        }
        ventCooldown = ventInterval(activeCurrent);
        vent();
    }

    // ==================== 找风场 ====================

    /**
     * 沿六个轴向找一台"风场打到本格、而且风向能把舱口吹开"的机器（判定见 {@link #opens}）。
     * 找不到返回 null。
     * <p>
     * 逐格外走而不是直接查邻居：鼓风机可以隔着十几格打到这儿，风场是细长的一条，
     * 只有沿轴线找才找得到（风场不会拐弯）。走满一个"推送/抽吸距离"仍没找到就停——
     * 更远的风不可能够到本格。
     * <p>
     * 注意是"第一台<b>合格</b>的"而不是"第一台"：同时有两股风打到本格时（一股吹入、
     * 一股侧扫），谁先被扫到不该决定舱口开不开，只要有一股合格就该开。
     */
    private AirCurrent findOpeningAirCurrent() {
        if (level == null)
            return null;

        AABB self = new AABB(worldPosition);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int range = scanRange();

        for (Direction direction : Direction.values()) {
            cursor.set(worldPosition);
            for (int step = 1; step <= range; step++) {
                cursor.move(direction);
                if (!level.isLoaded(cursor))
                    break;
                if (!(level.getBlockEntity(cursor) instanceof IAirCurrentSource source))
                    continue;
                AirCurrent current = source.getAirCurrent();
                if (current == null || source.isSourceRemoved())
                    continue;
                if (current.bounds == null || !current.bounds.intersects(self))
                    continue;
                if (opens(current))
                    return current;
            }
        }
        return null;
    }

    /** 扫描半径：取配置里鼓风机推送/抽吸距离的上限（默认 20） */
    private static int scanRange() {
        return Math.max(AllConfigs.server().kinetics.fanPushDistance.get(),
                AllConfigs.server().kinetics.fanPullDistance.get());
    }

    /**
     * 这股风能不能把舱口吹开。风向取的是空气<span>实际流动</span>的方向：
     * {@code AirCurrent.direction} 记的是风源"出风的那一面"，抽风时气流是反过来的，
     * 所以要用 {@code pushing} 翻一下。
     */
    private boolean opens(AirCurrent current) {
        Direction actualDirection = current.pushing
                ? current.direction
                : current.direction.getOpposite();
        Direction facing = getBlockState().getValue(AmethystClusterBlock.FACING);

        // 同一个轴上：只有"往外抽"才放，"往里吹"压住不放
        if (actualDirection.getAxis() == facing.getAxis())
            return actualDirection == facing;
        // 垂直：风从侧面扫过网罩
        return true;
    }

    /** 吐出间隔：与动力吸尘器吹出同款（间隔 = max(2, 100/转速) tick，转速越快越密） */
    private static int ventInterval(AirCurrent current) {
        if (current.source instanceof KineticBlockEntity kinetic) {
            float speed = Math.abs(kinetic.getSpeed());
            if (speed > 0)
                return Math.max(2, Math.round(100f / speed));
        }
        return FALLBACK_INTERVAL;
    }

    // ==================== 吐货 ====================

    /**
     * 从依附容器里取走一批"过滤通过"的物品，在舱口所在方块的正中心丢出去。
     * 一批几个 = 侧面槽上的数量（{@link #ventAmount}），但还要再受<b>物品自己的堆叠数</b>压一头：
     * 一批就是"一组"，16 堆叠的东西不该一次吐 64 个出来。节奏由 {@link #ventInterval} 控制。
     * <p>
     * 先扫一遍容器：算出能吐的总数，以及这几样物品里最小的堆叠数（混装时取最严的那个上限，
     * 保证这批不会超过任何一样物品的"一组"）。数量不够跨槽接着取，所以散装容器也凑得齐；
     * 取出来的每一段各生成一个掉落物实体（不同槽的物品本来就叠不到一起）。
     */
    private void vent() {
        IItemHandler source = getAttachedContainer();
        if (source == null)
            return;

        int available = 0;
        int groupSize = Integer.MAX_VALUE;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty() || !canVent(stack))
                continue;
            available += stack.getCount();
            groupSize = Math.min(groupSize, stack.getMaxStackSize());
        }
        if (available <= 0)
            return;

        int budget = batchSize(groupSize);
        // 智能舱口那边 "=" 那一档：容器里凑不够这一批就一个都不吐（整批走）
        if (requiresFullBatch() && available < budget)
            return;

        for (int slot = 0; slot < source.getSlots() && budget > 0; slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (stack.isEmpty() || !canVent(stack))
                continue;

            ItemStack extracted = source.extractItem(slot, budget, false);
            if (extracted.isEmpty())
                continue;

            budget -= extracted.getCount();
            dropItem(extracted);
        }
    }

    /**
     * 这一批吐几个。本类不带数量滑块，所以是固定档：{@code min(VENT_AMOUNT, groupSize)} ——
     * 也就是"最多 16 个，且不超过待吐物品里最小的那组"。16 堆叠的东西还是 16，
     * 64 堆叠的东西也只吐 16（对齐原版溜槽那一档）。
     * 智能泄压舱口覆写它，改为"滑块上的数再被一组压一头"。
     */
    protected int batchSize(int groupSize) {
        return Math.min(VENT_AMOUNT, groupSize);
    }

    /** 是否要求"凑够一整批才吐"。只有带数量滑块的智能舱口会用到 "=" 那一档 */
    protected boolean requiresFullBatch() {
        return false;
    }

    /** 过滤器为空 = 全放行；否则只放过滤器认的物品 */
    private boolean canVent(ItemStack stack) {
        if (filtering == null)
            return true;
        if (filtering.getFilter().isEmpty())
            return true;
        return filtering.test(stack);
    }

    /** 依附方块（{@code FACING} 反方向那一格）暴露给舱口的物品容器；没有则 null */
    private IItemHandler getAttachedContainer() {
        if (level == null)
            return null;
        Direction facing = getBlockState().getValue(AmethystClusterBlock.FACING);
        BlockPos attached = worldPosition.relative(facing.getOpposite());
        // 访问方向是"依附方块朝向舱口的那个面"，与吸尘器取正前方容器同一个口径
        return level.getCapability(Capabilities.ItemHandler.BLOCK, attached, facing);
    }

    /**
     * 掉落物生成：本格中心附近、无初速度、正常拾取延迟——与智能钻头挖出来的掉落物一字不差
     * （连那个 ±0.125 格的小抖动都照抄，免得一次吐多个时叠在一起）。
     * <p>
     * 唯一的加料是 {@link #freeDropSpot}：网罩那块碰撞箱是实心的，格心（以及抖动向下时的落点）
     * 会落在它里面，物品一生成就被碰撞箱卡住、被风一推就乱抖，所以生成前先沿朝向把落点推到
     * 碰撞箱外（仍留在本格内）。
     */
    private void dropItem(ItemStack stack) {
        Vec3 dropPosition = freeDropSpot(VecHelper.offsetRandomly(
                VecHelper.getCenterOf(worldPosition),
                level.random,
                .125f
        ));
        ItemEntity itemEntity = new ItemEntity(level, dropPosition.x, dropPosition.y, dropPosition.z, stack);
        itemEntity.setDefaultPickUpDelay();
        itemEntity.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(itemEntity);
    }

    /**
     * 把落点从碰撞箱里挪出来：从候选点开始沿舱口朝向（网罩开口那一侧）逐像素外推，
     * 直到物品的身位与方块碰撞箱不再相交。
     * <p>
     * 贴地时格心本来就在网罩顶面之上，一步都不用挪；贴墙、贴天花板时才真的会挪动，
     * 而且挪的方向就是舱口开口的方向，看起来就是"从网罩里掉出来"。推满一格仍没找到空位
     * （落点要留在本格内）就老实退回候选点——宁可卡住也不把掉落物甩到别的方块里去。
     */
    private Vec3 freeDropSpot(Vec3 candidate) {
        VoxelShape collision = getBlockState().getCollisionShape(level, worldPosition);
        if (collision.isEmpty())
            return candidate;

        Vec3 origin = Vec3.atLowerCornerOf(worldPosition);
        Vec3 local = candidate.subtract(origin);
        if (!touchesCollision(collision, local))
            return candidate;

        Direction facing = getBlockState().getValue(AmethystClusterBlock.FACING);
        Vec3 step = Vec3.atLowerCornerOf(facing.getNormal()).scale(1 / 16f);
        for (int i = 1; i <= 16; i++) {
            Vec3 moved = local.add(step.scale(i));
            // 落点必须留在本格内
            if (moved.x < 0 || moved.x > 1 || moved.y < 0 || moved.y > 1 || moved.z < 0 || moved.z > 1)
                break;
            if (!touchesCollision(collision, moved))
                return moved.add(origin);
        }
        return candidate;
    }

    /** 物品（0.25 格见方、落点在底面中心）在给定落点处会不会扎进碰撞箱 */
    private static boolean touchesCollision(VoxelShape collision, Vec3 local) {
        EntityDimensions size = EntityType.ITEM.getDimensions();
        double half = size.width() / 2d;
        AABB item = new AABB(
                local.x - half, local.y, local.z - half,
                local.x + half, local.y + size.height(), local.z + half);
        for (AABB box : collision.toAabbs()) {
            if (box.intersects(item))
                return true;
        }
        return false;
    }
}
