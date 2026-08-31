package com.minecart.yunxian.mixin;

import com.minecart.yunxian.MechanicalCleanerBlock;
import com.minecart.yunxian.MechanicalCleanerBlockEntity;
import com.simibubi.create.content.logistics.chute.ChuteBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让溜槽把吸尘器当作"等效鼓风机"：
 * - 吸尘器在溜槽正上方、朝 DOWN → 影响 calculatePull（拉）
 * - 吸尘器在溜槽正下方、朝 UP   → 影响 calculatePush（推）
 * 与 EncasedFanBlockEntity 对 ChuteBlockEntity 的驱动方式完全对称。
 */
@Mixin(ChuteBlockEntity.class)
public abstract class ChuteBlockEntityMixin {

    @Inject(method = "calculatePull", at = @At("HEAD"), cancellable = true)
    private void yunxian$cleanerPull(CallbackInfoReturnable<Float> cir) {
        ChuteBlockEntity self = (ChuteBlockEntity) (Object) this;
        if (self.getLevel() == null)
            return;

        BlockPos above = self.getBlockPos().above();
        BlockState stateAbove = self.getLevel().getBlockState(above);
        if (stateAbove.getBlock() instanceof MechanicalCleanerBlock
                && stateAbove.getValue(MechanicalCleanerBlock.FACING) == Direction.DOWN) {
            BlockEntity be = self.getLevel().getBlockEntity(above);
            if (be instanceof MechanicalCleanerBlockEntity cleaner) {
                cir.setReturnValue(equivalentSpeed(cleaner, Direction.DOWN));
            }
        }
    }

    @Inject(method = "calculatePush", at = @At("HEAD"), cancellable = true)
    private void yunxian$cleanerPush(CallbackInfoReturnable<Float> cir) {
        ChuteBlockEntity self = (ChuteBlockEntity) (Object) this;
        if (self.getLevel() == null)
            return;

        BlockPos below = self.getBlockPos().below();
        BlockState stateBelow = self.getLevel().getBlockState(below);
        if (stateBelow.getBlock() instanceof MechanicalCleanerBlock
                && stateBelow.getValue(MechanicalCleanerBlock.FACING) == Direction.UP) {
            BlockEntity be = self.getLevel().getBlockEntity(below);
            if (be instanceof MechanicalCleanerBlockEntity cleaner) {
                cir.setReturnValue(equivalentSpeed(cleaner, Direction.UP));
            }
        }
    }

    /**
     * 吸尘器对溜槽的"等效鼓风机转速"：
     * - 风向朝向溜槽（吹入）→ 正转速（物品向上）；
     * - 风向背向溜槽（吸离）→ 负转速（物品向下）；
     * - 无动力 / 红石锁（getAirFlowDirection 返回 null）→ 0，相当于没风。
     */
    private static float equivalentSpeed(MechanicalCleanerBlockEntity cleaner, Direction toChute) {
        Direction airflow = cleaner.getAirFlowDirection();
        if (airflow == null)
            return 0f;
        float speed = Math.abs(cleaner.getSpeed());
        return airflow == toChute ? speed : -speed;
    }
}