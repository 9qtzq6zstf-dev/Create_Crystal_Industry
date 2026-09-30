package com.minecart.yunxian.block.budding;

import java.util.function.Supplier;

import com.minecart.yunxian.blockentity.budding.BuddingGrowthBlockEntity;
import com.minecart.yunxian.blockentity.budding.ScriptedFluidBuddingBlockEntity;
import com.minecart.yunxian.budding.BuddingGrowthEngine;
import com.minecart.yunxian.budding.FluidRequirement;
import com.minecart.yunxian.budding.GrowthDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BuddingAmethystBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * 脚本（KubeJS）注册的母岩方块：生长参数由脚本给的 {@link GrowthDefinition} 决定
 * （不像自带家族那样来自 {@code BuddingFamilies} 那张表）。
 * <p>
 * 之所以要有这个类，而不是直接用 KubeJS 的通用方块：护目镜信息挂在**方块实体**上
 * （{@link BuddingGrowthBlockEntity} 实现 Create 的 {@code IHaveGoggleInformation}），
 * KubeJS 的方块没有实体，戴上护目镜看它就是一片空白。
 * <p>
 * 定义用 {@link Supplier} 惰性取：脚本执行时四个阶段方块还没注册完，只有等真正要用的时候
 * （首次随机刻、或护目镜读参数）才解析。
 * <p>
 * 写了流体需求（{@code CustomBuddingOptions#needfluid}）的母岩多两件事：方块实体换成能存流体的
 * {@link ScriptedFluidBuddingBlockEntity}，生长前先扣一份罐里的流体（付费钩子）。
 */
public class ScriptedBuddingBlock extends BuddingAmethystBlock implements EntityBlock {

    private final Supplier<GrowthDefinition> definition;

    public ScriptedBuddingBlock(Supplier<GrowthDefinition> definition, Properties properties) {
        super(properties);
        this.definition = definition;
    }

    /** 当前生效的生长参数（护目镜会读它显示概率/光照/含水） */
    public GrowthDefinition growthDefinition() {
        return definition.get();
    }

    /**
     * 本母岩要消耗的流体（容量 / 每次消耗 / 认哪种流体）；没配流体需求时返回 {@code null}。
     * <p>
     * 给方块实体读：它按这个建罐。方块的 {@link #newBlockEntity} 也是按它选实体的，
     * 所以那边只会在非 null 时建流体罐——两边读的是同一个定义对象，不会对不上。
     */
    @Nullable
    public FluidRequirement fluidRequirement() {
        return definition.get().fluid();
    }

    /**
     * 从方块状态取流体需求，取不到就抛——{@link ScriptedFluidBuddingBlockEntity} 的构造器用。
     * <p>
     * 抛出不是"防玩家"，而是防我们自己把没配流体的母岩接上流体罐实体：
     * 走到这里必然是本模组的接线错了，报出来比建一个永远装不进东西的空罐好查。
     */
    public static FluidRequirement fluidRequirementOf(BlockState state) {
        if (state.getBlock() instanceof ScriptedBuddingBlock scripted) {
            FluidRequirement requirement = scripted.fluidRequirement();
            if (requirement != null) {
                return requirement;
            }
        }
        throw new IllegalStateException("母岩 " + BuiltInRegistries.BLOCK.getKey(state.getBlock())
                + " 没有流体需求，不该建流体罐方块实体");
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        GrowthDefinition growth = definition.get();
        // 付费钩子：配了流体的母岩得先从罐里扣一份，罐空就当这一轮没抽中（扣不动的生长等于没长）。
        // 静态方法引用不带捕获，JVM 会缓存同一个实例，所以这里不必预先造好钩子
        BuddingGrowthEngine.GrowthGate gate = null;
        if (growth.fluid() != null) {
            gate = ScriptedFluidBuddingBlockEntity::consumeGrowthCost;
        }
        BuddingGrowthEngine.tryGrow(level, pos, random, growth, gate);
    }

    /**
     * 手持流体容器右键：能装就装、能舀就舀，只认罐体自己声明能收的流体
     * （由脚本的 {@link FluidRequirement} 决定）。具体过程与「倒不进去时为什么不能落回原版」
     * 都写在 {@link FluidTankInteraction} 里，与远古残骸母岩共用同一段。
     * <p>
     * 没配流体的母岩压根没有罐，连能力查询都不做——右键照旧（拿桶往旁边倒液体仍然照原版来）。
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (fluidRequirement() != null) {
            ItemInteractionResult tank = FluidTankInteraction.tryUse(stack, level, pos, player, hand, hitResult);
            if (tank != null) {
                return tank;
            }
        }
        return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
    }

    /**
     * 比较器只认「自称有模拟输出」的方块：{@code ComparatorBlock#getInputSignal} 先问
     * {@code BlockState#hasAnalogOutputSignal}，为 false 时连 {@link #getAnalogOutputSignal}
     * 都不会调（原版容器方块如熔炉同样要重写这两个）。漏了这一个方法就是"比较器毫无反应"。
     * <p>
     * 没配流体的母岩没有罐，读出来恒为 0，等于没有输出——多这一个虚调用不值得为它分流两个方块类。
     */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** 比较器读液位；没配流体的母岩（没有罐）返回 0。算法见 {@code ScriptedFluidBuddingBlockEntity#comparatorSignal} */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return ScriptedFluidBuddingBlockEntity.comparatorSignal(level, pos);
    }

    // 配了流体需求的母岩要能存流体，所以换成流体罐 BE（护目镜信息由它自己补）；
    // 其余的仍是纯展示 BE
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return fluidRequirement() == null
                ? new BuddingGrowthBlockEntity(pos, state)
                : new ScriptedFluidBuddingBlockEntity(pos, state);
    }
}
