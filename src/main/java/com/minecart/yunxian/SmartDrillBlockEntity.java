package com.minecart.yunxian;

import com.simibubi.create.content.kinetics.drill.DrillBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.utility.BlockHelper;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class SmartDrillBlockEntity extends DrillBlockEntity {
    private SmartDrillFilterBehaviour filtering;

    public SmartDrillBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SMART_DRILL.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);

        filtering = new SmartDrillFilterBehaviour(
                this,
                new SmartDrillValueBoxTransform()
        ).withCallback(stack -> destroyNextTick())
                .withModeCallback(mode -> destroyNextTick());
        filtering.setLabel(Component.translatable("create_crystal_industry.smart_drill.filter"));
        behaviours.add(filtering);
    }

    @Override
    protected BlockPos getBreakingPos() {
        return getBlockPos().relative(getBlockState().getValue(SmartDrillBlock.FACING));
    }

    @Override
    public boolean canBreak(BlockState stateToBreak, float blockHardness) {
        if (!super.canBreak(stateToBreak, blockHardness)) {
            return false;
        }
        return filtering == null
                || filtering.getFilter().isEmpty()
                || filtering.test(BlockHelper.getRequiredItem(stateToBreak));
    }

    @Override
    public void onBlockBroken(BlockState stateToBreak) {
        if (filtering == null || filtering.getMode() == DrillMode.NORMAL) {
            super.onBlockBroken(stateToBreak);
            return;
        }
        if (level == null) {
            return;
        }

        BlockPos target = breakingPos != null ? breakingPos : getBreakingPos();
        ItemStack silkTouchTool = new ItemStack(Items.NETHERITE_PICKAXE);
        Registry<Enchantment> enchantmentRegistry =
                level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);
        silkTouchTool.enchant(enchantmentRegistry.getHolderOrThrow(Enchantments.SILK_TOUCH), 1);

        Vec3 dropPosition = VecHelper.offsetRandomly(
                VecHelper.getCenterOf(target),
                level.random,
                .125f
        );

        BlockHelper.destroyBlockAs(level, target, null, silkTouchTool, 1f, stack -> {
            if (stack.isEmpty()) {
                return;
            }
            ItemEntity itemEntity = new ItemEntity(
                    level,
                    dropPosition.x,
                    dropPosition.y,
                    dropPosition.z,
                    stack
            );
            itemEntity.setDefaultPickUpDelay();
            itemEntity.setDeltaMovement(Vec3.ZERO);
            level.addFreshEntity(itemEntity);
        });
    }

    public enum DrillMode implements INamedIconOptions {
        NORMAL(AllIcons.I_TOOL_DEPLOY, "create_crystal_industry.smart_drill.mode.normal"),
        PRECISE(AllIcons.I_RESPECT_NBT, "create_crystal_industry.smart_drill.mode.precise");

        private final AllIcons icon;
        private final String translationKey;

        DrillMode(AllIcons icon, String translationKey) {
            this.icon = icon;
            this.translationKey = translationKey;
        }

        @Override
        public AllIcons getIcon() {
            return icon;
        }

        @Override
        public String getTranslationKey() {
            return translationKey;
        }
    }
}