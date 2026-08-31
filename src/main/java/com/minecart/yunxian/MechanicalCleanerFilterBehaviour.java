package com.minecart.yunxian;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.INamedIconOptions;
import com.simibubi.create.foundation.gui.AllIcons;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;

import java.util.function.Consumer;

public class MechanicalCleanerFilterBehaviour extends FilteringBehaviour {

    private RotationDirection direction = RotationDirection.NORMAL;
    private Consumer<RotationDirection> directionCallback = d -> {};

    public MechanicalCleanerFilterBehaviour(SmartBlockEntity be, ValueBoxTransform slot) {
        super(be, slot);
    }

    // 让链式调用返回子类类型，便于连续 with
    @Override
    public MechanicalCleanerFilterBehaviour withCallback(Consumer<ItemStack> filterCallback) {
        super.withCallback(filterCallback);
        return this;
    }

    public MechanicalCleanerFilterBehaviour withDirectionCallback(Consumer<RotationDirection> callback) {
        this.directionCallback = callback;
        return this;
    }

    public RotationDirection getDirection() {
        return direction;
    }

    /** GUI 风向按钮调用：切换一次方向 */
    public void toggleDirection() {
        setDirection(direction == RotationDirection.NORMAL ? RotationDirection.REVERSED : RotationDirection.NORMAL);
    }

    public void setDirection(RotationDirection newDirection) {
        if (newDirection == direction)
            return;
        direction = newDirection;
        directionCallback.accept(direction);
        blockEntity.setChanged();
        blockEntity.sendData();
        playFeedbackSound(this);
    }

    // ===== 数据持久化（过滤物品 + 数量 + 模式由父类 FilteringBehaviour 负责） =====
    // 这里只需额外持久化方向

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        nbt.putInt("Direction", direction.ordinal());
        super.write(nbt, registries, clientPacket);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        if (nbt.contains("Direction")) {
            RotationDirection[] directions = RotationDirection.values();
            direction = directions[nbt.getInt("Direction") % directions.length];
        }
        super.read(nbt, registries, clientPacket);
    }

    // ===== 悬停提示里显示当前方向 =====

    @Override
    public MutableComponent getTip() {
        return super.getTip().copy()
                .append(" · ")
                .append(Component.translatable(direction.getTranslationKey()));
    }

    /** 扇叶旋转方向 */
    public enum RotationDirection implements INamedIconOptions {
        NORMAL(AllIcons.I_TOOL_DEPLOY, "create_crystal_industry.mechanical_cleaner.direction.normal"),
        REVERSED(AllIcons.I_RESPECT_NBT, "create_crystal_industry.mechanical_cleaner.direction.reversed");

        private final AllIcons icon;
        private final String translationKey;

        RotationDirection(AllIcons icon, String translationKey) {
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