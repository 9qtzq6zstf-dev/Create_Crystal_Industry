package com.minecart.yunxian;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBehaviour.ValueSettings;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsBoard;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueSettingsFormatter;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class SmartDrillFilterBehaviour extends FilteringBehaviour {

    private SmartDrillBlockEntity.DrillMode mode = SmartDrillBlockEntity.DrillMode.NORMAL;
    private Consumer<SmartDrillBlockEntity.DrillMode> modeCallback = m -> {};

    public SmartDrillFilterBehaviour(SmartBlockEntity be, ValueBoxTransform slot) {
        super(be, slot);
    }

    // 让链式调用返回子类类型，便于连续 with
    @Override
    public SmartDrillFilterBehaviour withCallback(Consumer<ItemStack> filterCallback) {
        super.withCallback(filterCallback);
        return this;
    }

    public SmartDrillFilterBehaviour withModeCallback(Consumer<SmartDrillBlockEntity.DrillMode> callback) {
        this.modeCallback = callback;
        return this;
    }

    public SmartDrillBlockEntity.DrillMode getMode() {
        return mode;
    }

    // ===== 长按打开模式选择板 =====

    @Override
    public boolean acceptsValueSettings() {
        return true; // 让 value settings 系统在长按时打开面板
    }

    @Override
    public ValueSettingsBoard createBoard(Player player, BlockHitResult hitResult) {
        SmartDrillBlockEntity.DrillMode[] modes = SmartDrillBlockEntity.DrillMode.values();
        return new ValueSettingsBoard(
                CreateLang.translateDirect("create_crystal_industry.smart_drill.mode"),
                modes.length - 1,
                1,
                List.of(Component.empty()),   // 撑出行数用，iconMode 下不显示
                new ValueSettingsFormatter.ScrollOptionSettingsFormatter(modes)
        );
    }

    @Override
    public ValueSettings getValueSettings() {
        return new ValueSettings(0, mode.ordinal());
    }

    @Override
    public void setValueSettings(Player player, ValueSettings settings, boolean ctrlDown) {
        SmartDrillBlockEntity.DrillMode[] modes = SmartDrillBlockEntity.DrillMode.values();
        SmartDrillBlockEntity.DrillMode newMode = modes[settings.value() % modes.length];
        if (newMode != mode) {
            mode = newMode;
            modeCallback.accept(mode);
            blockEntity.setChanged();
            blockEntity.sendData();
            playFeedbackSound(this);
        }
    }

    // ===== 数据持久化（过滤物品 + 模式） =====

    @Override
    public void write(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        nbt.putInt("Mode", mode.ordinal());
        super.write(nbt, registries, clientPacket);
    }

    @Override
    public void read(CompoundTag nbt, HolderLookup.Provider registries, boolean clientPacket) {
        if (nbt.contains("Mode")) {
            SmartDrillBlockEntity.DrillMode[] modes = SmartDrillBlockEntity.DrillMode.values();
            mode = modes[nbt.getInt("Mode") % modes.length];
        }
        super.read(nbt, registries, clientPacket);
    }

    // ===== 悬停提示里显示当前模式 =====

    @Override
    public MutableComponent getTip() {
        return super.getTip().copy()
                .append(" · ")
                .append(Component.translatable(mode.getTranslationKey()));
    }
}