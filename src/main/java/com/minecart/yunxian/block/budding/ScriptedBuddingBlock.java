package com.minecart.yunxian.block.budding;

import java.util.List;
import java.util.function.Supplier;

import com.minecart.yunxian.blockentity.budding.BuddingGrowthBlockEntity;
import com.minecart.yunxian.blockentity.budding.FluidTankBuddingBlockEntity;
import com.minecart.yunxian.budding.BuddingConversions;
import com.minecart.yunxian.budding.BuddingGrowthEngine;
import com.minecart.yunxian.budding.BuddingOverrides;
import com.minecart.yunxian.budding.FluidRequirement;
import com.minecart.yunxian.budding.GrowthDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
 * {@link FluidTankBuddingBlockEntity}，生长前先扣一份罐里的流体（付费钩子）。
 */
public class ScriptedBuddingBlock extends BuddingAmethystBlock implements EntityBlock, FluidTankBudding {

    private final Supplier<GrowthDefinition> definition;

    // 套上脚本覆盖后的定义缓存：按覆盖表的版本号失效（见 BuddingOverrides.revision）。
    // 两个字段都 volatile、先写定义再写版本号——理由见 GenericBuddingBlock 里同样的那一段
    @Nullable
    private volatile GrowthDefinition cachedDefinition;
    private volatile int cachedRevision = -1;

    /** 本方块的 id 字符串（{@code kubejs:foo_budding}）：配置里的侵染名单按它匹配，见 BuddingConversions */
    private final String ownerId;

    // 转化规则表缓存：与生长定义一样按对象比对（脚本 modify 追加的转化也在定义里）
    @Nullable
    private volatile List<BuddingConversions.Prepared> preparedConversions;
    @Nullable
    private volatile GrowthDefinition preparedFor;

    public ScriptedBuddingBlock(ResourceLocation id, Supplier<GrowthDefinition> definition, Properties properties) {
        super(properties);
        this.ownerId = id.toString();
        this.definition = definition;
    }

    /**
     * 当前生效的生长参数（随机刻与护目镜都读它显示概率/光照/含水）：
     * 脚本注册时给的出厂定义，套上 {@code CustomBudding.modify(...)} 的覆盖（如果有）。
     */
    public GrowthDefinition growthDefinition() {
        int revision = BuddingOverrides.revision();
        GrowthDefinition cached = cachedDefinition;
        if (cached != null && cachedRevision == revision) {
            return cached;
        }
        GrowthDefinition resolved = BuddingOverrides.apply(this, definition.get());
        cachedDefinition = resolved;
        cachedRevision = revision;
        return resolved;
    }

    /**
     * 本母岩当前生效的流体需求（容量 / 每次消耗 / 认哪种流体，含脚本用
     * {@code CustomBudding.modify} 加的覆盖）；没配流体需求时返回 {@code null}。
     * <p>
     * 给方块实体读：它按这个建罐。方块的 {@link #newBlockEntity} 也是按它选实体的，
     * 所以那边只会在非 null 时建流体罐——两边读的是同一个定义对象，不会对不上。
     * <p>
     * <b>选了哪个方块实体，就必须在这类实体的合法方块表里</b>（否则区块重载时
     * {@code BlockEntityType#isValid} 会把实体直接丢掉）：`create` 注册时按脚本写的
     * {@code needfluid} 登记一边，`modify` 加罐 / 取消时再补登记另一边（见
     * {@code CustomBudding#modify}），所以这里可以放心读"解析后的定义"。
     */
    @Nullable
    @Override
    public FluidRequirement fluidRequirement() {
        return growthDefinition().fluid();
    }


    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        GrowthDefinition growth = growthDefinition();
        // 付费钩子：配了流体的母岩得先从罐里扣一份，罐空就当这一轮没抽中（扣不动的生长等于没长）。
        // 静态方法引用不带捕获，JVM 会缓存同一个实例，所以这里不必预先造好钩子
        BuddingGrowthEngine.GrowthGate gate = null;
        if (growth.fluid() != null) {
            gate = FluidTankBuddingBlockEntity::consumeGrowthCost;
        }
        BuddingGrowthEngine.tryGrow(level, pos, random, growth, gate);
        // 随机刻副作用（脚本用 .transform(输入, 产物) 写的转化/侵染）：与自带家族同一个执行器，
        // 顺序也一样——先生长那一轮，再逐条转化规则
        BuddingConversions.run(level, pos, random, prepared(growth), gate,
                BuddingConversions.infectionAllowed(ownerId));
    }

    /** 解析（并缓存）本母岩的转化规则表；定义变了（脚本改过）就重解析 */
    private List<BuddingConversions.Prepared> prepared(GrowthDefinition growth) {
        List<BuddingConversions.Prepared> cached = preparedConversions;
        if (cached != null && preparedFor == growth) {
            return cached;
        }
        List<BuddingConversions.Prepared> prepared = BuddingConversions.prepare(ownerId, this, growth.conversions());
        preparedConversions = prepared;
        preparedFor = growth;
        return prepared;
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
     * 只有带罐的母岩才算：没罐的读出来恒为 0，没必要让比较器白跑一次（判定走缓存过的定义）。
     */
    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return fluidRequirement() != null;
    }

    /** 比较器读液位；没配流体的母岩（没有罐）返回 0。算法见 {@code FluidTankBuddingBlockEntity#comparatorSignal} */
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return FluidTankBuddingBlockEntity.comparatorSignal(level, pos);
    }

    // 配了流体需求的母岩要能存流体，所以换成流体罐 BE（护目镜信息由它自己补）；
    // 其余的仍是纯展示 BE
    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return fluidRequirement() == null
                ? new BuddingGrowthBlockEntity(pos, state)
                : new FluidTankBuddingBlockEntity(pos, state);
    }
}
