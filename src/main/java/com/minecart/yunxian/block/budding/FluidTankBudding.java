package com.minecart.yunxian.block.budding;

import com.minecart.yunxian.budding.FluidRequirement;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 「带流体罐的母岩」——告诉方块实体按什么参数建罐、每次生长扣多少。
 * <p>
 * 两条路都实现它：本模组自带家族（{@link GenericBuddingBlock}，参数来自家族表，
 * 远古残骸母岩的熔岩罐就是它）与脚本注册的母岩（{@link ScriptedBuddingBlock}，
 * 参数来自 {@code CustomBuddingOptions#needfluid}）。方块实体只认这个接口，
 * 所以"哪个类建的母岩"与"有没有罐"是两件互不相干的事。
 * <p>
 * <b>读的是"解析后的定义"</b>（含脚本 {@code CustomBudding.modify} 的覆盖），
 * 于是加罐、改参数、取消罐都能在启动期由脚本改掉。
 */
public interface FluidTankBudding {

    /**
     * 罐里够不够一次生长：{@code false} = 客户端渲染"燃料不足"那套静态贴图
     * （远古残骸母岩的 {@code ancient_debris_budding_side/top_unpowered}）。
     * <p>
     * <b>想显示这一位的母岩必须自己把它加进 {@code createBlockStateDefinition}</b>
     * （本模组里是 {@code FueledBuddingBlock}，见那个类的注释：方块状态只能在构造器里注册，
     * 而 {@code createBlockStateDefinition} 早于子类字段赋值，读不到家族表）。
     * 没登记的母岩（脚本注册的 {@code ScriptedBuddingBlock}）方块实体那边会先
     * {@code hasProperty} 判空再跳过，不换材质也不报错——脚本方块不能加这一位，
     * 因为它们的 blockstate JSON 由脚本作者自己写，凭空多一条属性会让原版加载器
     * 报"缺变体"并把它渲染成缺失模型。
     */
    BooleanProperty FUELED = BooleanProperty.create("fueled");

    /**
     * 本方块当前生效的流体需求；没配（或已被脚本取消）时返回 {@code null}。
     * <p>
     * 取需求为空的方块不该建出流体罐方块实体——但那可能发生在存档里：脚本改过之后，
     * 早先放下的方块存的仍是罐的实体类型，区块重载时会照旧构造它。所以方块实体那边
     * 拿到 {@code null} 只会退化成一个空罐，不会抛。
     */
    @Nullable
    FluidRequirement fluidRequirement();

    /** 从方块状态取流体需求（给方块实体用）；不是带罐的母岩就返回 {@code null} */
    @Nullable
    static FluidRequirement requirementOf(BlockState state) {
        return state.getBlock() instanceof FluidTankBudding host ? host.fluidRequirement() : null;
    }
}
