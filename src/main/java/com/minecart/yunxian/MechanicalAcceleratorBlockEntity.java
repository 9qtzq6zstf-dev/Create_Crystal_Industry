package com.minecart.yunxian;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public class MechanicalAcceleratorBlockEntity extends KineticBlockEntity {

    /** 满速基准：达到该转速时催生效果达到上限 */
    public static final float FULL_SPEED = 256f;

    /** 电力催生器参考强度：每 tick 对 6 个面各触发一次 randomTick */
    public static final float ELECTRIC_RATE = 6f;

    /** 本机效果上限 = 电力催生器的 1/10（单位：randomTick / tick） */
    public static final float MAX_EFFECT_RATE = ELECTRIC_RATE / 1f; // = 0.6

    /** 工作面数量：全部 6 个面 */
    private static final int WORKING_FACES = 6;

    public MechanicalAcceleratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MECHANICAL_ACCELERATOR.get(), pos, state);
    }

    @Override
    public void tick() {
        super.tick();

        if (level == null || level.isClientSide)
            return;

        float speed = getSpeed();
        boolean powered = speed != 0;

        BlockState state = getBlockState();
        if (state.getValue(MechanicalAcceleratorBlock.POWERED) != powered) {
            level.setBlock(worldPosition, state.setValue(MechanicalAcceleratorBlock.POWERED, powered), 3);
        }

        if (!powered || !(level instanceof ServerLevel serverLevel))
            return;

        // 满速时每面的触发概率（上限 1/10 电力催生器 ÷ 6 个工作面）
        float maxPerFaceProb = MAX_EFFECT_RATE / WORKING_FACES; // = 0.1

        // 随转速线性增长，超过满速也封顶，绝不越过 1/10
        float perFaceProb = maxPerFaceProb * (Math.abs(speed) / FULL_SPEED);
        perFaceProb = Math.min(perFaceProb, maxPerFaceProb);

        // 六个面全部施加催生效果（含传动杆所在面）
        for (Direction dir : Direction.values()) {
            if (level.random.nextFloat() < perFaceProb) {
                BlockPos target = worldPosition.relative(dir);
                level.getBlockState(target).randomTick(serverLevel, target, serverLevel.random);
            }
        }
    }
}