package com.minecart.yunxian;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.belt.behaviour.DirectBeltInputBehaviour;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.IAirCurrentSource;
import com.simibubi.create.content.kinetics.fan.NozzleBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.infrastructure.config.AllConfigs;
import com.simibubi.create.infrastructure.config.CKinetics;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

public class MechanicalCleanerBlockEntity extends KineticBlockEntity
        implements MenuProvider, IAirCurrentSource {

    /** 容器容量：27 格 */
    public static final int INVENTORY_SIZE = 27;

    /** 目标气流长度最小值（格） */
    public static final int SUCK_RANGE_MIN = 1;

    /** 目标气流长度最大值（格）：与鼓风机 256 转速下的最大距离一致（fanPushDistance 默认 20） */
    public static final int SUCK_RANGE_MAX = 20;

    /** 目标气流长度（格）：GUI 调节的数值，实际长度还会受转速上限约束 */
    private int suckRange = SUCK_RANGE_MIN;

    /** 吸尘器内部库存 */
    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE);

    /** 侧面的过滤 / 方向配置槽 */
    private MechanicalCleanerFilterBehaviour filtering;

    // ===== 气流（复用鼓风机 AirCurrent）=====

    /** 气流对象：推/吸力、长度、阻挡、粒子全部与鼓风机一致 */
    public AirCurrent airCurrent;

    /** 气流重建冷却（fanBlockCheckRate 配置，默认 30 tick） */
    protected int airCurrentUpdateCooldown;

    /** 实体搜索冷却（5 tick，与鼓风机一致） */
    protected int entitySearchCooldown;

    /** 需要重建气流 */
    protected boolean updateAirFlow;

    public MechanicalCleanerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_CLEANER.get(), pos, state);
        airCurrent = new AirCurrent(this);
        updateAirFlow = true;
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    // ==================== IAirCurrentSource ====================

    @Override
    public AirCurrent getAirCurrent() {
        return airCurrent;
    }

    @Override
    public Level getAirCurrentWorld() {
        return level;
    }

    @Override
    public BlockPos getAirCurrentPos() {
        return worldPosition;
    }

    /** 气流起点面：方块朝向 */
    @Override
    public Direction getAirflowOriginSide() {
        return getBlockState().getValue(MechanicalCleanerBlock.FACING);
    }

    /**
     * 气流方向由"扇叶旋转方向"决定，与应力旋转方向无关：
     * NORMAL = 吹出（FACING 方向）；REVERSED = 吸入（FACING 反方向）。
     * 转速为 0 时无风。
     */
    @Override
    public Direction getAirFlowDirection() {
        if (getSpeed() == 0)
            return null;
        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);
        return isPulling() ? facing.getOpposite() : facing;
    }

    /**
     * 气流长度 = min(GUI 目标值, 转速决定的最大距离)。
     * 与鼓风机一致：fanRotationArgmax=256 时距离因子=1，长度 = fanPushDistance/fanPullDistance（默认 20）。
     */
    @Override
    public float getMaxDistance() {
        float speed = Math.abs(getSpeed());
        CKinetics config = AllConfigs.server().kinetics;
        float distanceFactor = Math.min(speed / config.fanRotationArgmax.get(), 1);
        float maxBySpeed = isPulling()
                ? Mth.lerp(distanceFactor, 3f, config.fanPullDistance.get())
                : Mth.lerp(distanceFactor, 3, config.fanPushDistance.get());
        return Math.min(suckRange, maxBySpeed);
    }

    @Override
    public boolean isSourceRemoved() {
        return isRemoved();
    }

    // ==================== 行为注册（仿智能钻头 + 开放容器） ====================

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        // 开放容器核心：
        // 1) 传送带可直接从任意面送物进来（默认 canInsertFromSide = true）
        // 2) 工作盆 canOutputTo 靠它判定"是否朝吸尘器开口"
        // 插入动作走物品栏能力（Capabilities.ItemHandler.BLOCK）
        behaviours.add(new DirectBeltInputBehaviour(this));

        filtering = new MechanicalCleanerFilterBehaviour(
                this,
                new MechanicalCleanerValueBoxTransform()
        ).withDirectionCallback(direction -> {
            // 方向切换 = 吹/吸切换：重建气流并同步客户端
            updateAirFlow = true;
            sendData();
        });
        filtering.setLabel(Component.translatable("create_crystal_industry.mechanical_cleaner.filter"));
        behaviours.add(filtering);
    }

    // ==================== 过滤与方向 ====================

    /** 是否允许吸取该物品：过滤为空 = 全部吸取；否则仅吸过滤匹配的物品 */
    public boolean canSuck(ItemStack stack) {
        if (filtering == null)
            return true;
        if (filtering.getFilter().isEmpty())
            return true;
        return filtering.test(stack);
    }

    /** 当前扇叶旋转方向：NORMAL = 吹出，REVERSED = 吸入 */
    public MechanicalCleanerFilterBehaviour.RotationDirection getRotationDirection() {
        if (filtering == null)
            return MechanicalCleanerFilterBehaviour.RotationDirection.NORMAL;
        return filtering.getDirection();
    }

    /** 是否处于"吸入"模式（气流方向与朝向相反） */
    private boolean isPulling() {
        return getRotationDirection() == MechanicalCleanerFilterBehaviour.RotationDirection.REVERSED;
    }

    /** 渲染用转速：方向反转时取负，仅影响视觉旋转方向（不改变动能网络） */
    public float getRenderSpeed() {
        float base = getTrueSpeed();
        return isPulling() ? -base : base;
    }

    // ==================== 气流长度（GUI 配置） ====================

    public int getSuckRange() {
        return suckRange;
    }

    public void setSuckRange(int range) {
        suckRange = Math.max(SUCK_RANGE_MIN, Math.min(SUCK_RANGE_MAX, range));
        setChanged();
        sendData();
        updateAirFlow = true;
    }

    // ==================== 容器 UI ====================

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.create_crystal_industry.mechanical_cleaner");
    }

    @Override
    public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
        return new MechanicalCleanerMenu(id, playerInventory, inventory, worldPosition, suckRange);
    }

    // ==================== NBT 持久化 ====================

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        // SuckRange 两端都要读：客户端需要它计算气流长度（否则粒子只在第一格、风力错位）
        suckRange = Math.max(SUCK_RANGE_MIN, Math.min(SUCK_RANGE_MAX, compound.getInt("SuckRange")));
        if (clientPacket) {
            airCurrent.rebuild();
            return;
        }
        if (compound.contains("Inventory")) {
            inventory.deserializeNBT(registries, compound.getCompound("Inventory"));
        }
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(compound, registries, clientPacket);
        // SuckRange 很小，两端都写：客户端同步包也需要它
        compound.putInt("SuckRange", suckRange);
        if (!clientPacket) {
            compound.put("Inventory", inventory.serializeNBT(registries));
        }
    }

    // ==================== 红石锁 ====================

    private boolean isRedstoneLocked() {
        return getBlockState().getValue(MechanicalCleanerBlock.POWERED);
    }

    /** 红石锁定时转速汇报为 0：无风、不吸不吹 */
    @Override
    public float getSpeed() {
        if (isRedstoneLocked())
            return 0;
        return super.getSpeed();
    }

    /** 真实网络转速：不受红石锁影响，供传动杆渲染使用 */
    public float getTrueSpeed() {
        return super.getSpeed();
    }

    // ==================== 气流驱动（仿鼓风机） ====================

    @Override
    public void onSpeedChanged(float prevSpeed) {
        super.onSpeedChanged(prevSpeed);
        updateAirFlow = true;
    }

    /** 前方方块变化时调用：重建气流（阻挡判定会变） */
    public void blockInFrontChanged() {
        updateAirFlow = true;
    }

    @Override
    public void tick() {
        super.tick();

        boolean server = !level.isClientSide;

        // 周期性检查阻挡：每 fanBlockCheckRate(30) tick 重建一次气流
        if (server && airCurrentUpdateCooldown-- <= 0) {
            airCurrentUpdateCooldown = AllConfigs.server().kinetics.fanBlockCheckRate.get();
            updateAirFlow = true;
        }

        if (updateAirFlow) {
            updateAirFlow = false;
            airCurrent.rebuild();
            sendData();
        }

        // 无转速则无风（红石锁 / 未接入应力都归于此）
        if (getSpeed() == 0)
            return;

        // 每 5 tick 刷新气流中的实体列表
        if (entitySearchCooldown-- <= 0) {
            entitySearchCooldown = 5;
            airCurrent.findEntities();
        }

        airCurrent.tick();

        // 吸入模式：气流范围内所有掉落物直接收入容器（无需先吸到正面）
        if (server && isPulling()) {
            collectItemsInFlow();
            collectItemsFromNozzle();   // 分散网（喷嘴）覆盖范围
        }
    }

    // ==================== 收集逻辑 ====================

    /**
     * 吸入模式：扫描整个气流范围（airCurrent.bounds，已含阻挡截断），
     * 范围内所有掉落物直接收入 27 格容器。
     * 容器满时物品原地堆积（由玩家产线处理）。
     */
    private void collectItemsInFlow() {
        // 无有效气流时不收集
        if (airCurrent == null || airCurrent.maxDistance <= 0)
            return;
        AABB flowBounds = airCurrent.bounds;
        if (flowBounds == null || flowBounds.getSize() <= 0)
            return;

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, flowBounds);
        for (ItemEntity itemEntity : items) {
            suckItem(itemEntity);
        }
    }

    /**
     * 分散网（喷嘴）兼容：吸尘器正前方挂有喷嘴时，
     * 喷嘴把气流分散成以自身为中心的球形风场（半径 = 吸尘器气流长度）。
     * 吸入模式下，该球体内、且有视线的掉落物直接收入容器。
     */
    private void collectItemsFromNozzle() {
        if (airCurrent == null || getMaxDistance() <= 0)
            return;

        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);
        BlockPos nozzlePos = worldPosition.relative(facing);

        // 正前方必须是喷嘴才走分散网逻辑
        if (!(level.getBlockEntity(nozzlePos) instanceof NozzleBlockEntity))
            return;

        // 喷嘴覆盖范围：以喷嘴中心为球心，半径 = 吸尘器气流长度（与喷嘴 calcRange 一致）
        Vec3 center = VecHelper.getCenterOf(nozzlePos);
        float radius = getMaxDistance();
        AABB coverage = new AABB(center, center).inflate(radius);

        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, coverage);
        for (ItemEntity itemEntity : items) {
            // 与喷嘴自身行为一致：超出半径或无视线的不吸
            if (itemEntity.position().distanceTo(center) > radius)
                continue;
            if (!canSeeNozzle(itemEntity, nozzlePos))
                continue;
            suckItem(itemEntity);
        }
    }

    /**
     * 与 NozzleBlockEntity.canSee 相同：从物品位置到喷嘴中心做碰撞射线，
     * 命中喷嘴自身才视为可见（风能真正覆盖到）。
     */
    private boolean canSeeNozzle(ItemEntity entity, BlockPos nozzlePos) {
        ClipContext context = new ClipContext(entity.position(), VecHelper.getCenterOf(nozzlePos),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity);
        return nozzlePos.equals(level.clip(context).getBlockPos());
    }

    /**
     * 尝试把一个掉落物吸入容器。
     * 依次尝试放入每个槽位；放不下的部分留在掉落物中（不吞物品）。
     */
    private void suckItem(ItemEntity itemEntity) {
        // 过滤拦截：不匹配的物品不吸入
        if (!canSuck(itemEntity.getItem()))
            return;

        ItemStack remaining = itemEntity.getItem().copy();

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
            if (remaining.isEmpty())
                break;
        }

        int inserted = itemEntity.getItem().getCount() - remaining.getCount();
        if (inserted <= 0)
            return;

        itemEntity.getItem().shrink(inserted);
        if (itemEntity.getItem().isEmpty()) {
            itemEntity.discard();
        }

        setChanged();
    }
}