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
 *
 * 符号约定（Create 溜槽源码）：
 * - pull > 0 = pull_up（吸气向上），pull < 0 = push_down（吹气向下）
 * - push > 0 = push_up（吹气向上），push < 0 = pull_down（吸气向下）
 * 所以"吸尘器在上方"和"在下方"两处，吹向溜槽时符号是相反的，必须分开处理。
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
                cir.setReturnValue(equivalentPull(cleaner));
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
                cir.setReturnValue(equivalentPush(cleaner));
            }
        }
    }

    /**
     * 吸尘器在溜槽上方（朝 DOWN）时的等效 pull：
     * - 吹向溜槽（airflow=DOWN）→ pull < 0 → 溜槽"吹气向下"
     * - 吸离溜槽（airflow=UP）  → pull > 0 → 溜槽"吸气向上"
     */
    private static float equivalentPull(MechanicalCleanerBlockEntity cleaner) {
        Direction airflow = cleaner.getAirFlowDirection();
        if (airflow == null)
            return 0f;
        float speed = Math.abs(cleaner.getSpeed());
        return airflow == Direction.DOWN ? -speed : speed;
    }

    /**
     * 吸尘器在溜槽下方（朝 UP）时的等效 push：
     * - 吹向溜槽（airflow=UP）  → push > 0 → 溜槽"吹气向上"
     * - 吸离溜槽（airflow=DOWN）→ push < 0 → 溜槽"吸气向下"
     */
    private static float equivalentPush(MechanicalCleanerBlockEntity cleaner) {
        Direction airflow = cleaner.getAirFlowDirection();
        if (airflow == null)
            return 0f;
        float speed = Math.abs(cleaner.getSpeed());
        return airflow == Direction.UP ? speed : -speed;
    }
}