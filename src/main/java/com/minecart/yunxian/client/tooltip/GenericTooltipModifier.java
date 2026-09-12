package com.minecart.yunxian.client.tooltip;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.simibubi.create.foundation.item.ItemDescription;
import com.simibubi.create.foundation.item.TooltipModifier;

import net.createmod.catnip.lang.FontHelper;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * 通用物品提示修饰器（机械动力风格）。
 *
 * <p>排版复用 Create 的 ItemDescription.Builder：默认悬停只显示深灰色页眉
 * （“按住 [Shift] 查看简介”），按住 Shift 展开简介；换行与 `_高亮_` 语法
 * 由 TooltipHelper 处理。</p>
 *
 * <p>注册进 TooltipModifier.REGISTRY 后，由 Create 的
 * ClientEvents.addToItemTooltip 在 ItemTooltipEvent 上统一派发，
 * 本类不需要自己监听事件。</p>
 *
 * <p>两个注册入口：</p>
 * <pre>
 * // 静态简介：每个 key 一行，按传入顺序显示
 * GenericTooltipModifier.register(ModItems.FLAMMABLE_ICE.get(),
 *         "item.create_crystal_industry.flammable_ice.tooltip.summary");
 *
 * // 动态简介：文本在每次悬浮时求值，可含实时数据（如当前按键名）
 * GenericTooltipModifier.register(ModItems.NIGHT_VISION_GOGGLES.get(),
 *         () -> I18n.get("item.create_crystal_industry.night_vision_goggles.tooltip.summary",
 *                 ModKeybinds.TOGGLE_NIGHT_VISION.getTranslatedKeyMessage().getString()));
 * </pre>
 */
public class GenericTooltipModifier implements TooltipModifier {

    /**
     * 简介调色板。把这一行对齐到你之前能编译通过的那份修饰器里用的常量
     * （若当时按兜底改成了 GRAY_AND_WHITE，这里也改成它；现在只需改这一处）。
     */
    public static final FontHelper.Palette PALETTE = FontHelper.Palette.STANDARD_CREATE;

    private final FontHelper.Palette palette;
    private final List<Supplier<String>> summaryLines;

    public GenericTooltipModifier(FontHelper.Palette palette, List<Supplier<String>> summaryLines) {
        this.palette = palette;
        this.summaryLines = List.copyOf(summaryLines);
    }

    /** 注册静态简介：每个翻译 key 对应一行 */
    public static void register(Item item, String... translationKeys) {
        List<Supplier<String>> lines = new ArrayList<>(translationKeys.length);
        for (String key : translationKeys) {
            lines.add(() -> I18n.get(key));
        }
        TooltipModifier.REGISTRY.register(item, new GenericTooltipModifier(PALETTE, lines));
    }

    /** 注册单行动态简介：每次悬浮时求值，可读取实时状态 */
    public static void register(Item item, Supplier<String> dynamicLine) {
        TooltipModifier.REGISTRY.register(item, new GenericTooltipModifier(PALETTE, List.of(dynamicLine)));
    }

    @Override
    public void modify(ItemTooltipEvent context) {
        // 每次重建而不做语言级缓存：简介可能含实时数据（如当前按键名），
        // 缓存会像 Create 的 ItemDescription.Modifier 那样只在语言切换时失效，
        // 导致改键后文案不刷新。构建成本与原生提示自身的每帧排版同阶，可忽略。
        ItemDescription.Builder builder = new ItemDescription.Builder(palette);
        for (Supplier<String> line : summaryLines) {
            builder.addSummary(line.get());
        }
        // 与 Create 的 ItemDescription.Modifier 一致：插在物品名之后
        context.getToolTip().addAll(1, builder.build().getCurrentLines());
    }
}