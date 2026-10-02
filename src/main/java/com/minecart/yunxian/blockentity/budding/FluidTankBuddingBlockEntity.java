package com.minecart.yunxian.blockentity.budding;

import java.util.List;

import com.minecart.yunxian.block.budding.FluidTankBudding;
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
import org.jetbrains.annotations.Nullable;

/**
 * 母岩的流体罐：容量、每次生长的消耗、认哪种流体<b>全部由方块自己的定义给出</b>
 * （{@code CustomBuddingOptions#needfluid} 或家族表里的 {@code Growth.fluid} →{@code GrowthDefinition#fluid()}），
 * 所以同一个方块实体类型能服务任意多块母岩——脚本注册的与自带家族（远古残骸的熔岩罐）共用它。
 * <p>
 * 参数每块母岩各读各的定义，所以既没有写死的常量、也不用为每个家族注册一个专用类型。
 * 实现 {@link IFluidHandler} 之后，Create 的流体管道、泵，或任何认 NeoForge 流体能力的机器
 * 都能直接灌进来（能力注册见 {@code ModCapabilities}），手持流体容器的右键交互在方块侧
 * （见 {@code FluidTankInteraction}，两块方块类共用）。
 * <p>
 * <b>客户端同步</b>：罐里的量是护目镜浮窗要读的，而方块实体数据只有在方块更新时才会发给客户端，
 * 所以每次罐体变动都必须自己顶一次 {@code sendBlockUpdated}，光 {@code setChanged()} 是不够的
 * （不顶这一次的话，护目镜会一直显示旧数值）。
 */
public class FluidTankBuddingBlockEntity extends BlockEntity implements IHaveGoggleInformation, IFluidHandler {

    /**
     * 本母岩的流体需求：罐容量、每次生长的消耗、认哪种流体。
     * <p>
     * 可能为 {@code null}：脚本用 {@code CustomBudding.modify} 取消了流体需求之后，世界上早先放下的
     * 方块里存的仍是本实体类型，区块重载时原版会按存档里的类型 id 重新构造它（不走
     * {@code ScriptedBuddingBlock#newBlockEntity}），那一刻已经没有流体需求了。
     * 那种情况下这个罐退化成"容量 0、什么都不收"的空罐——不崩，也不影响新放下的方块。
     */
    @Nullable
    private final FluidRequirement requirement;

    /**
     * 罐体。写在匿名子类里是为了让<b>所有</b>主动写入路径（管道、桶、生长扣费）
     * 都走同一个 {@code onContentsChanged}——扣费是随机刻里的热路径，漏一次同步
     * 就会让护目镜显示旧数值，这种 bug 很难从现象反查。
     */
    private final FluidTank tank;

    public FluidTankBuddingBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_TANK_BUDDING.get(), pos, state);
        this.requirement = FluidTankBudding.requirementOf(state);
        this.tank = createTank(requirement);
    }

    /** 需求为 {@code null} 时建一个容量 0、什么都不收的空罐（见 {@link #requirement} 的注释） */
    private FluidTank createTank(@Nullable FluidRequirement requirement) {
        return new FluidTank(requirement == null ? 0 : requirement.capacity(),
                stack -> requirement != null && requirement.matches(stack)) {
            @Override
            protected void onContentsChanged() {
                setChanged();
                // 客户端读同步包走的是 readFromNBT（直接写字段，不经这里），所以这条判断平时不会命中；
                // 留着是兜底：真在客户端被调到时也只落盘、不把包原样发回去
                if (level != null && !level.isClientSide) {
                    // 先顶方块状态再发方块实体包：前者是给"换贴图"的（客户端按 fueled 选模型），
                    // 后者才是护目镜要读的液位，两个都得发
                    syncFueledState();
                    level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
                }
            }
        };
    }

    /** 罐里现有多少流体（护目镜与比较器都读它） */
    public int fluidAmount() {
        return tank.getFluidAmount();
    }

    /**
     * 罐里够不够再长一次：量不少于一次消耗、且流体类型是罐收的那种。
     * <p>
     * 类型不对也算"不够"：脚本可能用 {@code modify} 把这块母岩要的流体换过，而罐是方块实体
     * 创建时建的——里面留着的旧流体既不会被消耗、管道也抽得出来，此时它长不了，
     * 材质与护目镜都该照实报红。
     * <p>
     * 没有流体需求（旧罐退化来的空罐）视作永远够：这一位对那块母岩没有意义，
     * 显示成"燃料不足"反而误导。
     */
    private boolean hasEnoughForOneGrowth() {
        return requirement == null
                || (tank.getFluidAmount() >= requirement.costPerGrowth() && requirement.matches(tank.getFluid()));
    }

    /**
     * 把 {@link FluidTankBudding#FUELED} 顶成与罐里实时一致——客户端就是靠它换
     * {@code _unpowered} 贴图的。
     * <p>
     * 只在值真的变了才写：{@code setBlock} 会顶一次客户端更新，白写就是白发。
     * 没登记这一位的母岩（脚本方块）直接跳过，不换材质也不报错，理由见
     * {@link FluidTankBudding#FUELED}。
     * <p>
     * 不担心把罐子写坏：同一个方块只换状态时原版会保留方块实体
     * （{@code LevelChunk#setBlockState} 只在方块本身变了才 remove），也不会重入
     * {@link #onContentsChanged()}。
     */
    private void syncFueledState() {
        BlockState state = getBlockState();
        if (!state.hasProperty(FluidTankBudding.FUELED)) {
            return;
        }
        boolean fueled = hasEnoughForOneGrowth();
        if (state.getValue(FluidTankBudding.FUELED) != fueled) {
            level.setBlock(worldPosition, state.setValue(FluidTankBudding.FUELED, fueled), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * 尝试为一次生长扣掉一份流体。
     *
     * @return 罐里不足 {@link FluidRequirement#costPerGrowth()}（或流体类型不对）时返回 false，表示放弃这次生长
     */
    public boolean tryConsumeGrowthCost() {
        if (requirement == null || level == null || level.isClientSide()) {
            return false;
        }
        if (!hasEnoughForOneGrowth()) {
            return false;
        }
        // EXECUTE 会经 onContentsChanged 落盘、同步给客户端，并顺手顶掉 fueled 状态
        tank.drain(requirement.costPerGrowth(), FluidAction.EXECUTE);
        return true;
    }

    /**
     * 生长的付费钩子：交给 {@code ScriptedBuddingBlock} 挂到生长引擎上
     * （{@code BuddingGrowthEngine.GrowthGate} 的形状正好是静态方法引用，不产生额外对象）。
     */
    public static boolean consumeGrowthCost(ServerLevel level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof FluidTankBuddingBlockEntity tank
                && tank.tryConsumeGrowthCost();
    }

    /**
     * 比较器读液位：空罐 0，其余按比例给 1–15（和原版炼药锅一个写法，空与非空要能区分开）。
     * <p>
     * 液位不是方块状态，所以这里现查方块实体——比较器每 tick 会读一次，读的是服务端的真实值，
     * 不像护目镜那样依赖同步包。没配流体需求的脚本母岩（没有罐）返回 0。
     */
    public static int comparatorSignal(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof FluidTankBuddingBlockEntity tank)
                || tank.requirement == null) {
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
     * 生长参数（速度 / 光照 / 含水）与生长环境也在这里一起补上：那几行本来是共享展示 BE
     * （{@code BuddingGrowthBlockEntity}）代劳的，带罐的母岩用的是本类，得自己记得调，
     * 否则护目镜上会少几行。
     * <p>
     * <b>流体名报"罐里那一桶"的实名</b>（{@link FluidRequirement#displayNameOf}），只有空罐或流体不对时
     * 才报需求的名字。流体的名字是<b>带栈</b>的（{@code FluidType#getDescription(FluidStack)}）：
     * 机械动力那一种药水流体就是靠它把笼统的「药水」细分成「迅捷药水」「力量药水」等具体药水，
     * 而只报 {@code FluidType} 的通用名会让所有药水看起来一模一样；药水的<b>等级</b>
     * （迅捷 I / 迅捷 II）名字里本来也没有，由那个方法照原版习惯补上后缀。
     */
    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        if (level != null) {
            BuddingGrowthHelper.appendGrowthTooltip(level, worldPosition, tooltip);
            BuddingGrowthHelper.appendScriptedInfo(getBlockState(), tooltip);
            BuddingGrowthHelper.appendGrowthEnvironment(getBlockState(), tooltip);
        }
        // 脚本取消过流体需求（旧罐退化来的空罐）：没有"花多少 / 认哪种流体"可讲，不显示流体那两行
        if (requirement == null) {
            return true;
        }
        int amount = fluidAmount();
        // 罐里的东西不对也算"不够"：脚本可能用 modify 把这块母岩的流体换过，
        // 而罐是方块实体创建时建的——里面留着的旧流体既不会被消耗、管道也抽得出来，
        // 所以这里如实报红，别让玩家对着 1000/1000 的绿字纳闷它为什么不长
        boolean rightFluid = amount <= 0 || requirement.matches(tank.getFluid());
        boolean enough = rightFluid && amount >= requirement.costPerGrowth();
        // 罐里装着对的东西就报它的实名（药水借此细分成具体药水、并补上等级后缀，见方法注释）；
        // 空罐与装错东西时仍旧报需求的名字——那正是"该灌什么 / 该换成什么"的提示
        Component fluidLabel = rightFluid && amount > 0
                ? FluidRequirement.displayNameOf(tank.getFluid())
                : requirement.displayName();
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.scripted.fluid",
                                fluidLabel, amount, requirement.capacity())
                        .withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.RED))
                .forGoggles(tooltip, 1);
        CreateLang.builder()
                .add(Component.translatable("create_crystal_industry.goggles.scripted.fluid_cost",
                                requirement.costPerGrowth())
                        .withStyle(ChatFormatting.GRAY))
                .forGoggles(tooltip, 1);
        // 现在不会长的话把原因明说出来，别让玩家从 100 / 1000 这个数字自己推
        if (!enough) {
            CreateLang.builder()
                    .add(Component.translatable(rightFluid
                                    ? "create_crystal_industry.goggles.scripted.fluid_too_little"
                                    : "create_crystal_industry.goggles.scripted.fluid_wrong")
                            .withStyle(ChatFormatting.RED))
                    .forGoggles(tooltip, 1);
        }
        return true;
    }
}
