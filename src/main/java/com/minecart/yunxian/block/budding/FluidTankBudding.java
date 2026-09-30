package com.minecart.yunxian.block.budding;

import com.minecart.yunxian.budding.FluidRequirement;

import net.minecraft.world.level.block.state.BlockState;
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
