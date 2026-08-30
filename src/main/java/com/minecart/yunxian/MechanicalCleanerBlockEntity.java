package com.minecart.yunxian;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

public class MechanicalCleanerBlockEntity extends KineticBlockEntity {

    public MechanicalCleanerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_CLEANER.get(), pos, state);
    }

    private boolean isRedstoneLocked() {
        return getBlockState().getValue(MechanicalCleanerBlock.POWERED);
    }

    /**
     * 参考智能钻头的脱开机制：
     * 红石锁定时向客户端汇报 0 转速，扇叶冻结。
     * POWERED 是方块状态，setBlock(flag=2) 会同步到客户端，客户端 getSpeed() 同样生效。
     */
    @Override
    public float getSpeed() {
        if (isRedstoneLocked())
            return 0;
        return super.getSpeed();
    }

    /** 真实网络转速：不受红石锁影响，供传动杆渲染使用（与智能钻头 getTrueSpeed 一致） */
    public float getTrueSpeed() {
        return super.getSpeed();
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide)
            return;

        if (getSpeed() == 0)
            return;

        // TODO: 吸尘/清理功能（等确认）
    }
}