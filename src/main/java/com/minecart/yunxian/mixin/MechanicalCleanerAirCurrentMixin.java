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
 * 吸尘器含水时，其气流变为机械动力的"洗涤"气流（SPLASHING）：
 * 拦截 AirCurrent.getTypeAt，只要风源是含水（WATERLOGGED）的吸尘器，
 * 气流内任意位置的物品都按洗涤配方处理。
 */
@Mixin(AirCurrent.class)
public abstract class MechanicalCleanerAirCurrentMixin {

    @Inject(method = "getTypeAt", at = @At("HEAD"), cancellable = true)
    private void yunxian$waterloggedCleanerWashes(float offset, CallbackInfoReturnable<FanProcessingType> cir) {
        AirCurrent self = (AirCurrent) (Object) this;
        if (self.source instanceof MechanicalCleanerBlockEntity cleaner
                && cleaner.getBlockState().getValue(BlockStateProperties.WATERLOGGED)) {
            cir.setReturnValue(AllFanProcessingTypes.SPLASHING);
        }
    }
}