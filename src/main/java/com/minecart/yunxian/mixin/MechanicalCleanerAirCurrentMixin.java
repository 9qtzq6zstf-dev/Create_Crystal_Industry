package com.minecart.yunxian.mixin;

import com.minecart.yunxian.MechanicalCleanerBlockEntity;
import com.simibubi.create.content.kinetics.fan.AirCurrent;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 吸尘器含水且"吹气"时，气流默认按洗涤（SPLASHING）处理。
 * 用 RETURN 注入：只有当该位置原本没有其它催化剂（返回 null）时才补洗涤，
 * 这样气流经过熔岩/营火等催化剂时仍会变成对应的熔炼/烟熏气流，
 * 不会被含水吸尘器的洗涤强制覆盖。
 */
@Mixin(AirCurrent.class)
public abstract class MechanicalCleanerAirCurrentMixin {

    @Inject(method = "getTypeAt", at = @At("RETURN"), cancellable = true)
    private void yunxian$waterloggedCleanerWashes(float offset, CallbackInfoReturnable<FanProcessingType> cir) {
        AirCurrent self = (AirCurrent) (Object) this;
        if (self.source instanceof MechanicalCleanerBlockEntity cleaner
                && self.pushing
                && cleaner.getBlockState().getValue(BlockStateProperties.WATERLOGGED)) {
            // 原本没有催化剂（null）才补洗涤；已有催化剂（熔岩/营火等）则保留原类型
            if (cir.getReturnValue() == null) {
                cir.setReturnValue(AllFanProcessingTypes.SPLASHING);
            }
        }
    }
}