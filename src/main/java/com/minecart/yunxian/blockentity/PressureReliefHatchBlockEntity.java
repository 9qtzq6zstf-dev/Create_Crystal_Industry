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
 * <b>吐多少。</b>一次吐几个由侧面槽上那个"数量"决定（{@link #ventAmount}，滚轮可调，
 * 范围 1 ~ 过滤物品的最大堆叠数，一般是 64）。那个控件就是 Create 过滤行为自带的数量，
 * 与动力吸尘器侧面那个槽是同一套；它上面还有一档 "≤ / ="：默认的 "≤" 是能吐多少吐多少，
 * "=" 则要求容器里凑够这个数才整批吐（凑不够就一个都不动）。
 * 节奏跟着风源转速走（间隔 = max(2, 100/转速) tick，与动力吸尘器吹出同一套公式）；
 * 风源不是动能方块（比如喷嘴后面那台机器缺动力、或者干脆是另一台吸尘器）时退回
 * {@link #FALLBACK_INTERVAL}。
 */
public class PressureReliefHatchBlockEntity extends SmartBlockEntity {

    /** 风场扫描间隔（tick）。风起风落最多迟这么久，一秒足够 */
    private static final int SCAN_INTERVAL = 20;

    /** 风源给不出转速时的兜底节奏（tick / 批） */
    private static final int FALLBACK_INTERVAL = 10;

    /** 侧面槽"数量"上限：一次最多吐这么多（控件本身按过滤物品的堆叠数夹，这里只兜脏 NBT） */
    private static final int MAX_VENT_AMOUNT = 64;

    /** 侧面 4 个槽共用同一个过滤器，只有过滤通过的物品才会被吐出来 */
    private FilteringBehaviour filtering;

    /** 当前"打得到本格"的风场，null = 没风；每 {@link #SCAN_INTERVAL} tick 重扫一次 */
    private AirCurrent activeCurrent;

    private int scanCooldown;
    private int ventCooldown;

    public PressureReliefHatchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.PRESSURE_RELIEF_HATCH.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        // showCount() 把"数量"挂到这个槽上：侧面滚轮即可调每次吐几个（1 ~ 过滤物品的最大堆叠数，
        // 通常是 64），与动力吸尘器那个槽是完全同一套控件（Create 的过滤行为自带的数量）。
        filtering = new FilteringBehaviour(this, new PressureReliefHatchValueBoxTransform())
                .showCount()
                .withCallback(stack -> sendData());
        // Create 那边这个数量的默认值是 64（一整组）。对过滤槽来说无所谓，但对"每次吐几个"
        // 就是默认开满闸——满速风扇下 2 tick 一批、一批 64 件，那是喷泉不是泄压。
        filtering.setLabel(Component.translatable("create_crystal_industry.pressure_relief_hatch.filter"));
        behaviours.add(filtering);
    }

    @Override
    public void tick() {
        super.tick();

        // ticker 两端都挂（客户端要靠它触发 SmartBlockEntity 的 initialize），
        // 但找风场与吐货是纯服务端的事
        if (level == null || level.isClientSide)
            return;

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
     * 一次取几个由侧面那个槽的数量决定（{@link #ventAmount}），节奏由 {@link #ventInterval} 控制。
     * <p>
     * 数量不够跨槽接着取，所以"每次 64 个"在散装容器上也能凑齐。取出来的每一段各生成一个
     * 掉落物实体（不同槽的物品本来就叠不到一起）。
     */
    private void vent() {
        IItemHandler source = getAttachedContainer();
        if (source == null)
            return;

        int budget = ventAmount();
        // "=" 那一档：容器里凑不够这个数就一个都不吐（整批走）。默认的 "≤" 档则是能吐多少吐多少
        if (filtering != null && !filtering.upTo && countVentable(source) < budget)
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
     * 侧面槽上"数量"的当前值 = 每次吐几个。控件本身已经把范围夹在 1 ~ 过滤物品的最大堆叠数
     * （没有过滤物品时就是 64），这里再兜一道底，免得读到脏 NBT。
     */
    private int ventAmount() {
        if (filtering == null)
            return 1;
        return Math.max(1, Math.min(MAX_VENT_AMOUNT, filtering.getAmount()));
    }

    /** 容器里"过滤通过"的物品总数，供 "=" 档判断整批够不够 */
    private int countVentable(IItemHandler source) {
        int total = 0;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (!stack.isEmpty() && canVent(stack))
                total += stack.getCount();
        }
        return total;
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
