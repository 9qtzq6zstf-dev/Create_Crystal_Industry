package com.minecart.yunxian.blockentity.budding;

import java.util.List;

import com.minecart.yunxian.block.budding.ScriptedBuddingBlock;
import com.minecart.yunxian.budding.FluidRequirement;
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

/**
 * 脚本（KubeJS）母岩的流体罐：容量、每次生长的消耗、认哪种流体<b>全部由脚本定义给出</b>
 * （{@code CustomBuddingOptions#needfluid} → {@code GrowthDefinition#fluid()}），
 * 所以同一个方块实体类型能服务任意多个脚本母岩。
 * <p>
 * 与远古残骸母岩的 {@link LavaBuddingBlockEntity} 是同一套做法，区别只有两点：
 * 那边的参数是写死的常量（只认熔岩、1 B / 250 mB），这边每块母岩各读各的定义。
 * 实现 {@link IFluidHandler} 之后，Create 的流体管道、泵，或任何认 NeoForge 流体能力的机器
 * 都能直接灌进来（能力注册见 {@code ModCapabilities}），手持流体容器的右键交互在方块侧
 * （见 {@link ScriptedBuddingBlock}）。
 * <p>
 * <b>客户端同步</b>：罐里的量是护目镜浮窗要读的，而方块实体数据只有在方块更新时才会发给客户端，
 * 所以每次罐体变动都必须自己顶一次 {@code sendBlockUpdated}，光 {@code setChanged()} 是不够的
 * （理由同 {@link LavaBuddingBlockEntity} 的类注释）。
 */
public class ScriptedFluidBuddingBlockEntity extends BlockEntity implements IHaveGoggleInformation, IFluidHandler {

    /** 本母岩的流体需求：罐容量、每次生长的消耗、认哪种流体 */
    private final FluidRequirement requirement;

    /**
     * 罐体。写在匿名子类里是为了让<b>所有</b>主动写入路径（管道、桶、生长扣费）
     * 都走同一个 {@code onContentsChanged}——扣费是随机刻里的热路径，漏一次同步
     * 就会让护目镜显示旧数值，这种 bug 很难从现象反查。
     */
    private final FluidTank tank;

    public ScriptedFluidBuddingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCRIPTED_FLUID_BUDDING.get(), pos, state);
        // 方块只会给配了流体需求的母岩建这个实体（见 ScriptedBuddingBlock#newBlockEntity），
        // 所以这里拿不到需求就是本模组的 bug，直接报出来而不是建一个装不进东西的空罐
        this.requirement = ScriptedBuddingBlock.fluidRequirementOf(state);
        this.tank = new FluidTank(requirement.capacity(), requirement::matches) {
            @Override
            protected void onContentsChanged() {
                setChanged();
                // 客户端读同步包走的是 readFromNBT（直接写字段，不经这里），所以这条判断平时不会命中；
                // 留着是兜底：真在客户端被调到时也只落盘、不把包原样发回去
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        };
    }

    /** 本母岩要消耗的流体（容量 / 每次消耗 / 认哪种流体） */
    public FluidRequirement requirement() {
        return requirement;
    }

    /** 罐里现有多少流体（护目镜与比较器都读它） */
    public int fluidAmount() {
        return tank.getFluidAmount();
    }

    /**
     * 尝试为一次生长扣掉一份流体。
     *
     * @return 罐里不足 {@link FluidRequirement#costPerGrowth()} 时返回 false，表示放弃这次生长
     */
    public boolean tryConsumeGrowthCost() {
        if (level == null || level.isClientSide()) {
            return false;
        }
        if (tank.getFluidAmount() < requirement.costPerGrowth() || !requirement.matches(tank.getFluid())) {
            return false;
        }
        // EXECUTE 会经 onContentsChanged 落盘并同步给客户端
        tank.drain(requirement.costPerGrowth(), FluidAction.EXECUTE);
        return true;
    }

    /**
     * 生长的付费钩子：交给 {@code ScriptedBuddingBlock} 挂到生长引擎上
     * （{@code BuddingGrowthEngine.GrowthGate} 的形状正好是静态方法引用，不产生额外对象）。
     */
    public static boolean consumeGrowthCost(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ScriptedFluidBuddingBlockEntity tank
                && tank.tryConsumeGrowthCost();
    }

    /**
     * 比较器读液位：空罐 0，其余按比例给 1–15（和原版炼药锅一个写法，空与非空要能区分开）。
     * <p>
     * 液位不是方块状态，所以这里现查方块实体——比较器每 tick 会读一次，读的是服务端的真实值，
     * 不像护目镜那样依赖同步包。没配流体需求的脚本母岩（没有罐）返回 0。
     */
    public static int comparatorSignal(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ScriptedFluidBuddingBlockEntity tank)) {
            return 0;
        }
        int amount = tank.fluidAmount();
        if (amount <= 0) {
            return 0;
        }
        return Math.max(1, Math.round(15.0F * amount / tank.requirement.capacity()));
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

    /** 方块实体包带上整罐流体：护目镜浮窗在客户端读它（罐是脚本给的容量，一般只有几桶，整份发不心疼） */
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
     * 流体的名称与液位都直接给数字：这是方块自己的燃料表，玩家得按它决定什么时候再补一桶
     * ——与「方块固有参数只说定性话」的规矩不冲突，因为液位是状态，不是参数。
     * <p>
     * 脚本母岩的生长参数（速度 / 光照 / 含水）与生长环境也在这里一起补上：
     * 那几行本来是共享展示 BE（{@code BuddingGrowthBlockEntity}）代劳的，配了流体的母岩用的是本类，
     * 得自己记得调，否则护目镜上会少几行（与 {@code LavaBuddingBlockEntity} 同一个理由）。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level != null) {
            BuddingGrowthHelper.appendGrowthTooltip(level, worldPosition, tooltip);
            BuddingGrowthHelper.appendScriptedInfo(getBlockState(), tooltip);
            BuddingGrowthHelper.appendGrowthEnvironment(getBlockState(), tooltip);
        }
        int amount = fluidAmount();
        boolean enough = amount >= requirement.costPerGrowth();
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.scripted.fluid",
                                requirement.displayName(), amount, requirement.capacity())
                        .withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.scripted.fluid_cost",
                                requirement.costPerGrowth())
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);
        // 不够一次生长时把"所以现在不会长"明说出来，别让玩家从 100/1000 这个数字自己推
        if (!enough) {
            CreateLang.builder()
                    .add(Component.translatable("create_crystal_industry.goggles.scripted.fluid_too_little")
                            .withStyle(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }
        return true;
    }
}
