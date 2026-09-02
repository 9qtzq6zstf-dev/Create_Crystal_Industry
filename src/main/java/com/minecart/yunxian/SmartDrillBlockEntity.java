package com.minecart.yunxian;

import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.BlockHelper;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SmartDrillBlockEntity extends DrillBlockEntity {
    private SmartDrillFilterBehaviour filtering;

    /**
     * 服务端推送的"锁定"状态。
     * 客户端世界状态可能因"无更新的方块变化"而滞后，停转必须信任服务器。
     */
    private boolean syncedBlocked;

    public SmartDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_DRILL.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        filtering = new SmartDrillFilterBehaviour(
                this,
                new SmartDrillValueBoxTransform()
        ).withCallback(stack -> destroyNextTick())
                .withModeCallback(mode -> destroyNextTick());
        filtering.setLabel(Component.translatable("create_crystal_industry.smart_drill.filter"));
        behaviours.add(filtering);
    }

    @Override
    protected BlockPos getBreakingPos() {
        return getBlockPos().relative(getBlockState().getValue(SmartDrillBlock.FACING));
    }

    @Override
    public boolean canBreak(BlockState stateToBreak, float blockHardness) {
        if (!super.canBreak(stateToBreak, blockHardness)) {
            return false;
        }
        return filtering == null
                || filtering.getFilter().isEmpty()
                || filtering.test(BlockHelper.getRequiredItem(stateToBreak));
    }

    @Override
    public void onBlockBroken(BlockState stateToBreak) {
        if (filtering == null || filtering.getMode() == DrillMode.NORMAL) {
            super.onBlockBroken(stateToBreak);
            return;
        }
        if (level == null) {
            return;
        }

        BlockPos target = breakingPos != null ? breakingPos : getBreakingPos();

        // 母岩不遵循原版掉落表（原版精准采集也挖不掉）：精准模式下先真正破坏方块，再手动掉落母岩
        if (stateToBreak.getBlock() instanceof BuddingAmethystBlock) {
            level.destroyBlock(target, false);                            // 真正移除方块（不产生掉落）
            dropItem(target, new ItemStack(stateToBreak.getBlock()));     // 补发母岩掉落物
            return;
        }

        ItemStack silkTouchTool = new ItemStack(Items.NETHERITE_PICKAXE);
        Registry<Enchantment> enchantmentRegistry =
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        silkTouchTool.enchant(enchantmentRegistry.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);

        BlockHelper.destroyBlockAs(level, target, null, silkTouchTool, 1f, stack -> {
            if (stack.isEmpty()) {
                return;
            }
            dropItem(target, stack);
        });
    }

    /** 在目标位置生成一个无初速、正常拾取延迟的掉落物 */
    private void dropItem(BlockPos pos, ItemStack stack) {
        Vec3 dropPosition = VecHelper.offsetRandomly(
                VecHelper.getCenterOf(pos),
                level.random,
                .125f
        );
        ItemEntity itemEntity = new ItemEntity(
                level,
                dropPosition.x,
                dropPosition.y,
                dropPosition.z,
                stack
        );
        itemEntity.setDefaultPickUpDelay();
        itemEntity.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(itemEntity);
    }

    public enum DrillMode implements INamedIconOptions {
        NORMAL(AllIcons.I_TOOL_DEPLOY, "create_crystal_industry.smart_drill.mode.normal"),
        PRECISE(AllIcons.I_RESPECT_NBT, "create_crystal_industry.smart_drill.mode.precise");

        private final AllIcons icon;
        private final String translationKey;

        DrillMode(AllIcons icon, String translationKey) {
            this.icon = icon;
            this.translationKey = translationKey;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }

    /** 普通模式挖掘速度倍率 */
    private static final float NORMAL_SPEED_MULTIPLIER = 2.0f;
    /** 精准模式挖掘速度倍率 */
    private static final float PRECISE_SPEED_MULTIPLIER = 1.0f;

    @Override
    protected float getBreakSpeed() {
        float base = super.getBreakSpeed();
        if (filtering == null || filtering.getMode() == SmartDrillBlockEntity.DrillMode.NORMAL)
            return base * NORMAL_SPEED_MULTIPLIER;
        return base * PRECISE_SPEED_MULTIPLIER;
    }

    /**
     * 判断钻头当前目标是否处于"无法挖掘"的停转状态。
     * 空气/液体视为待机目标，不停转；其余按 canBreak 判断（含过滤拦截、基岩等）。
     */
    public boolean isBreakingBlocked() {
        if (level == null || filtering == null)
            return false;

        BlockPos targetPos = getBreakingPos();
        BlockState target = level.getBlockState(targetPos);
        if (target.isAir() || target.liquid())
            return false;

        return !canBreak(target, target.getDestroySpeed(level, targetPos));
    }

    private boolean isRedstoneLocked() {
        return getBlockState().getValue(SmartDrillBlock.POWERED);
    }

    /**
     * 参考 ClutchBlockEntity 的脱开机制：
     * 无法挖掘时把转速汇报为 0，客户端冻结旋转动画，服务端跳过挖掘逻辑。
     * 客户端额外信任服务端推送的 syncedBlocked（防止无更新方块变化导致视觉不停止）。
     */
    @Override
    public float getSpeed() {
        if (isRedstoneLocked() || isBreakingBlocked() || syncedBlocked)
            return 0;
        return super.getSpeed();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide)
            return;

        boolean blocked = isBreakingBlocked() || isRedstoneLocked();

        // 清掉被拦截时的残留破坏裂纹
        if (blocked && destroyProgress != 0) {
            destroyProgress = 0;
            level.destroyBlockProgress(breakerId, breakingPos, -1);
        }

        // 状态变化时推送给客户端，让视觉立刻跟上（即使方块变化没通知客户端）
        if (blocked != syncedBlocked) {
            syncedBlocked = blocked;
            setChanged();
            sendData();
        }
    }

    @Override
    public void write(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        compound.putBoolean("SyncedBlocked", syncedBlocked);
        super.write(compound, registries, clientPacket);
    }

    @Override
    protected void read(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(compound, registries, clientPacket);
        syncedBlocked = compound.getBoolean("SyncedBlocked");
    }

    /**
     * 真实网络转速：不受过滤/红石停转影响，供传动杆渲染使用。
     */
    public float getTrueSpeed() {
        return super.getSpeed();
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        // 保留父类（DrillBlockEntity → KineticBlockEntity）的机械动力同款信息
        super.addToGoggleTooltip(tooltip, isPlayerSneaking);

        // 当前采集模式：普通采集 / 精准采集（整行灰色，与动力吸尘器方向提示一致）
        // 前缀独立成 key，避免污染 DrillMode 在滚动选择器里的显示文本
        DrillMode mode = filtering != null ? filtering.getMode() : DrillMode.NORMAL;

        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.smart_drill.mode_label")
                        .withStyle(ChatFormatting.GRAY))
                .add(Component.translatable(mode.getTranslationKey())
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);

        return true;
    }
}