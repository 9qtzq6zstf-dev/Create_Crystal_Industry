package com.minecart.yunxian;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.List;

public class MechanicalCleanerBlockEntity extends KineticBlockEntity implements MenuProvider {

    /** 容器容量：27 格（与木桶/潜影盒一致） */
    public static final int INVENTORY_SIZE = 27;

    /** 吸取距离最小值（格） */
    public static final int SUCK_RANGE_MIN = 1;

    /** 吸取距离最大值（格） */
    public static final int SUCK_RANGE_MAX = 8;

    /** 吸取距离（格）：前方 1~8 格，默认 1 */
    private int suckRange = SUCK_RANGE_MIN;

    /** 吸尘器内部库存 */
    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE);

    /** 侧面的过滤 / 方向配置槽 */
    private MechanicalCleanerFilterBehaviour filtering;

    public MechanicalCleanerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_CLEANER.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    // ==================== 行为注册（仿智能钻头） ====================

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        filtering = new MechanicalCleanerFilterBehaviour(
                this,
                new MechanicalCleanerValueBoxTransform()
        ).withDirectionCallback(direction -> {
            // 方向变化：持久化与客户端同步已由行为内部处理
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

    /** 当前扇叶旋转方向 */
    public MechanicalCleanerFilterBehaviour.RotationDirection getRotationDirection() {
        if (filtering == null)
            return MechanicalCleanerFilterBehaviour.RotationDirection.NORMAL;
        return filtering.getDirection();
    }

    /** 渲染用转速：方向反转时取负，仅影响视觉旋转方向（不改变动能网络） */
    public float getRenderSpeed() {
        float base = getTrueSpeed();
        return getRotationDirection() == MechanicalCleanerFilterBehaviour.RotationDirection.REVERSED
                ? -base
                : base;
    }

    // ==================== 吸取距离 ====================

    public int getSuckRange() {
        return suckRange;
    }

    public void setSuckRange(int range) {
        suckRange = Math.max(SUCK_RANGE_MIN, Math.min(SUCK_RANGE_MAX, range));
        setChanged();
        sendData();
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
    public void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        // 客户端同步包不携带完整库存，避免每次同步传输大块数据
        if (!clientPacket) {
            tag.put("Inventory", inventory.serializeNBT(registries));
            tag.putInt("SuckRange", suckRange);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (clientPacket) {
            return;
        }
        if (tag.contains("Inventory")) {
            inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        }
        suckRange = Math.max(SUCK_RANGE_MIN, Math.min(SUCK_RANGE_MAX, tag.getInt("SuckRange")));
    }

    // ==================== 红石锁 ====================

    private boolean isRedstoneLocked() {
        return getBlockState().getValue(MechanicalCleanerBlock.POWERED);
    }

    /** 红石锁定时转速汇报为 0：扇叶冻结（仅影响扇叶视觉与动能，不影响吸取） */
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

    // ==================== 吸取逻辑 ====================

    /**
     * 计算实际可吸取的最大距离（格数）。
     * 沿朝向逐格检查，遇到第一个"完整方块"（isSuffocating：完整碰撞盒 + 不透光）即截断；
     * 若吸尘器紧贴完整方块（前方第一格就被挡），返回 0（一格都吸不到）。
     * 非完整方块（半砖、栅栏、玻璃、树叶、液体等）不阻挡吸取。
     */
    private int getEffectiveSuckRange() {
        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);

        // 逐格扫描 1 ~ suckRange：任一格是完整方块，则其后全部不可达
        for (int i = 1; i <= suckRange; i++) {
            BlockPos checkPos = worldPosition.relative(facing, i);
            BlockState state = level.getBlockState(checkPos);
            if (state.isSuffocating(level, checkPos)) {
                return i - 1;   // 返回最后一个"畅通"格数
            }
        }
        return suckRange;
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide)
            return;

        // 吸取不依赖应力输入：无应力也能吸。
        // 但红石锁是独立开关，锁定时仍然停止吸取。
        if (isRedstoneLocked())
            return;

        // 计算实际可吸距离：沿途被完整方块阻挡则截断
        int effectiveRange = getEffectiveSuckRange();
        if (effectiveRange <= 0)
            return;   // 前方紧贴完整方块，一格都吸不到

        // 吸取范围：朝向方向，从前方第一格延伸 effectiveRange 格
        Direction facing = getBlockState().getValue(MechanicalCleanerBlock.FACING);
        BlockPos first = worldPosition.relative(facing);
        BlockPos last = first.relative(facing, effectiveRange - 1);

        // 用两角构造 AABB：对任意朝向都正确（x/y/z 轴延伸均可）
        Vec3 minCorner = new Vec3(
                Math.min(first.getX(), last.getX()),
                Math.min(first.getY(), last.getY()),
                Math.min(first.getZ(), last.getZ()));
        Vec3 maxCorner = new Vec3(
                Math.max(first.getX(), last.getX()) + 1,
                Math.max(first.getY(), last.getY()) + 1,
                Math.max(first.getZ(), last.getZ()) + 1);
        AABB targetArea = new AABB(minCorner, maxCorner);

        // 每 tick 吸取该范围内所有掉落物
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, targetArea);
        for (ItemEntity itemEntity : items) {
            suckItem(itemEntity);
        }
    }

    /**
     * 尝试把一个掉落物吸入容器。
     * 依次尝试放入每个槽位；放不下的部分留在掉落物中（不吞物品）。
     */
    private void suckItem(ItemEntity itemEntity) {
        // 过滤拦截：不匹配的物品不吸入
        if (!canSuck(itemEntity.getItem()))
            return;

        // remaining：当前还没放进容器的部分
        ItemStack remaining = itemEntity.getItem().copy();

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            remaining = inventory.insertItem(slot, remaining, false);
            if (remaining.isEmpty())
                break;
        }

        // 实际吸入的数量 = 掉落物原数量 - 剩余数量
        int inserted = itemEntity.getItem().getCount() - remaining.getCount();
        if (inserted <= 0)
            return;

        // 从掉落物中扣除已吸入数量；吸空则移除该实体
        itemEntity.getItem().shrink(inserted);
        if (itemEntity.getItem().isEmpty()) {
            itemEntity.discard();
        }

        setChanged();
    }
}