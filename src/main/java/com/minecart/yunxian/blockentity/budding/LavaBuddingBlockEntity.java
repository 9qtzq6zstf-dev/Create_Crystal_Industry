package com.minecart.yunxian.blockentity.budding;

import java.util.List;

import com.minecart.yunxian.registry.ModBlockEntities;
import com.minecart.yunxian.util.BuddingGrowthHelper;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.utility.CreateLang;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * 远古残骸母岩的熔岩罐：容量 1 B（{@link #CAPACITY}），每次成功生长扣 {@link #COST_PER_GROWTH}。
 * <p>
 * 与其它三个母岩方块实体不同，这个 BE <b>存数据</b>（罐里的熔岩随方块落盘）并实现
 * {@link IFluidHandler}——方块的能力注册见 {@code ModCapabilities}，Create 的流体管道、
 * 泵，或任何认 NeoForge 流体能力的机器都能直接灌进来、抽出去。
 * <p>
 * 罐里只认 {@link FluidTags#LAVA}：既防别的液体占位，也防有人拿它当熔岩管道用
 * （抽得出、装不进别的东西）。装熔岩桶那一下交互在方块侧，见 {@code LavaBuddingBlock}。
 * <p>
 * <b>客户端同步</b>：罐里的量是护目镜浮窗要读的，而方块实体数据只有在方块更新时才会发给客户端
 * ——{@code ServerLevel#sendBlockUpdated} → {@code ChunkHolder#blockChanged} →
 * {@code BlockEntity#getUpdatePacket}（见 NeoForge 源码 ChunkHolder.broadcastBlockEntity），
 * 所以每次罐体变动都必须自己顶一次 {@code sendBlockUpdated}，光 {@code setChanged()} 是不够的。
 * 那一次方块更新的 flags 用什么值都不影响这条路，取 {@link Block#UPDATE_CLIENTS}。
 */
public class LavaBuddingBlockEntity extends BlockEntity implements IHaveGoggleInformation, IFluidHandler {

    /** 罐容量：1 B（正好一桶熔岩） */
    public static final int CAPACITY = 1000;

    /** 每次成功生长（以及付了费的再生传播）消耗的熔岩 */
    public static final int COST_PER_GROWTH = 250;

    /**
     * 罐体。写在匿名子类里是为了让<b>所有</b>写入路径（管道、桶、生长扣费、读档）
     * 都走同一个 {@code onContentsChanged}——扣费是随机刻里的热路径，漏一次同步
     * 就会让护目镜显示旧数值，这种 bug 很难从现象反查。
     */
    private final FluidTank tank = new FluidTank(CAPACITY, stack -> stack.is(FluidTags.LAVA)) {
        @Override
        protected void onContentsChanged() {
            setChanged();
            // 客户端读到同步包时也会走这里（readFromNBT → setFluid），别把包再发回去
            if (level != null && !level.isClientSide) {
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    };

    public LavaBuddingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ANCIENT_DEBRIS_BUDDING.get(), pos, state);
    }

    /** 罐里现有多少熔岩（护目镜与比较器都读它） */
    public int lavaAmount() {
        return tank.getFluidAmount();
    }

    /**
     * 尝试为一次生长（或付费的直接转化）扣掉一份熔岩。
     *
     * @return 罐里不足 {@link #COST_PER_GROWTH} 时返回 false，表示放弃这次生长
     */
    public boolean tryConsumeGrowthCost() {
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (tank.getFluidAmount() < COST_PER_GROWTH || !tank.getFluid().is(FluidTags.LAVA)) {
            return false;
        }
        // EXECUTE 会经 onContentsChanged 落盘并同步给客户端
        tank.drain(COST_PER_GROWTH, FluidAction.EXECUTE);
        return true;
    }

    // ==================== 流体能力 ====================

    @Override
    public int getTanks() {
        return tank.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tankIndex) {
        return tank.getFluidInTank(tankIndex);
    }

    @Override
    public int getTankCapacity(int tankIndex) {
        return tank.getTankCapacity(tankIndex);
    }

    @Override
    public boolean isFluidValid(int tankIndex, FluidStack stack) {
        return tank.isFluidValid(tankIndex, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        return tank.fill(resource, action);
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        return tank.drain(resource, action);
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        return tank.drain(maxDrain, action);
    }

    // ==================== 存档与同步 ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tank.writeToNBT(registries, tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tank.readFromNBT(registries, tag);
    }

    /** 方块实体包带上整罐熔岩：护目镜浮窗在客户端读它（罐很小，整份发不心疼） */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ==================== 护目镜 ====================

    /**
     * 熔岩量与「每次生长要花多少」都直接给数字：这是方块自己的燃料表，
     * 玩家得按它决定什么时候再补一桶——与「方块固有参数只说定性话」的规矩不冲突，
     * 因为液位是状态，不是参数。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level != null) {
            BuddingGrowthHelper.appendGrowthTooltip(level, worldPosition, tooltip);
            // 本家族是下界特产：这行「只在某某维度满速」必须由专用 BE 自己加，
            // 共用的 BuddingGrowthBlockEntity 才会代劳（见该方法注释里的提醒）
            BuddingGrowthHelper.appendGrowthEnvironment(getBlockState(), tooltip);
        }
        int amount = lavaAmount();
        boolean enough = amount >= COST_PER_GROWTH;
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.ancient_debris_budding.lava",
                                amount, CAPACITY)
                        .withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.ancient_debris_budding.cost",
                                COST_PER_GROWTH)
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);
        // 不够一次生长时把"所以现在不会长"明说出来，别让玩家从 100/1000 这个数字自己推
        if (!enough) {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.ancient_debris_budding.too_little")
                            .withStyle(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }
        return true;
    }
}
