package com.minecart.yunxian.compat.jei;

import com.minecart.yunxian.Yunxian;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.AbstractRecipeCategory;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * JEI 的「侵染」页：母岩在上，两端是方块 A 与方块 B，中间一支箭头，箭头下面写清概率。
 * <p>
 * 三条刻意的取舍：
 * <ul>
 *   <li><b>三个都是 JEI 的物品槽</b>（母岩在上、A/B 分列两端）。方块的物品模型本身就是立体的小方块，
 *       放进槽里观感并不差，还顺带白拿了五件事：可拖拽可收藏、悬停就报名字、
 *       <b>标签输入能原生轮播</b>（回响母岩的输入是 {@code #echo_convertible}，十几项还嵌着
 *       {@code #minecraft:dirt}，画成一个方块画不出来，展开成十几条配方又会把列表刷屏）、
 *       R/U 检索，以及母岩槽上的额外说明（见下）。</li>
 *   <li><b>概率口径挂在母岩槽上</b>：用 {@code addRichTooltipCallback} 往那个槽自己的 tooltip 里追加，
 *       与方块名同一个框——另开一个 {@code getTooltip} 会让玩家悬停时看到两个 tooltip 框。</li>
 *   <li><b>底板交给 JEI</b>：与信息页同理，本页只画那支箭头与一行字，资源包换掉底板时页面也跟着变。</li>
 * </ul>
 * <p>
 * 概率是"规则级"的：引擎每条规则每个随机刻只掷一次 n 面骰，命中后在半径内随机取一格、
 * 按声明顺序取第一条匹配的替换——所以石头与深板岩那两条配方共用同一次掷骰。页面上按规则写，
 * 不按逐对概率写（{@link BuddingConversion} 有完整说明）。
 * <p>
 * 版面坐标有个硬约束：JEI 先调 {@link #draw}、<b>再把可见槽画在最上层</b>，所以自绘永远盖不住槽，
 * 箭头与那行字必须自己躲开三个格子（下面这几个常量就是按这个排的）。
 */
public class BuddingConversionCategory extends AbstractRecipeCategory<BuddingConversion> {

    public static final RecipeType<BuddingConversion> TYPE =
            RecipeType.create(Yunxian.MODID, "budding_conversion", BuddingConversion.class);

    // ==================== 版面 ====================

    private static final int WIDTH = 127;
    private static final int HEIGHT = 52;

    /**
     * 三个物品格（16×16 物品格的左上角）。格子本身是光板的，边上那圈边框来自
     * {@code setStandardSlotBackground()}（18×18，向外各扩 1 像素）——所以与箭头之间要留出这点余量。
     * <p>
     * 母岩居中在箭头上方（中心 x=63），A/B 与箭头同高一排（中心 y=31）。
     */
    private static final int BUDDING_SLOT_X = 55;
    private static final int BUDDING_SLOT_Y = 6;
    private static final int A_SLOT_X = 6;
    private static final int B_SLOT_X = 105;
    private static final int SLOT_Y = 23;

    /**
     * 两端之间的横箭头：Create 的 {@code AllGuiTextures.JEI_LONG_ARROW}——{@code jei/widgets.png}
     * 的 u=19、v=0，71×10，原样贴。
     */
    private static final ResourceLocation CREATE_JEI_WIDGETS =
            ResourceLocation.fromNamespaceAndPath("create", "textures/gui/jei/widgets.png");
    private static final int ARROW_X = 28;
    private static final int ARROW_Y = 26;
    private static final int ARROW_U = 19;
    private static final int ARROW_V = 0;
    private static final int ARROW_W = 71;
    private static final int ARROW_H = 10;

    /** 「n 随机刻」那行字：箭头下沿（36）再空 3 像素，居中于箭头（它下面没有别的元素，不担心压格） */
    private static final int TEXT_Y = 39;

    /** 文案配色：底板是 JEI 那块浅灰九宫格，所以用深色、不加文字阴影（同信息页） */
    private static final int TEXT_COLOR = 0xFF3F3F3F;

    public BuddingConversionCategory(IGuiHelper guiHelper) {
        super(TYPE,
                Component.translatable(BuddingInfoText.LANG + "conversion.title"),
                guiHelper.createDrawableItemStack(new ItemStack(Blocks.BUDDING_AMETHYST)),
                WIDTH, HEIGHT);
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, BuddingConversion recipe, IFocusGroup focuses) {
        // 母岩那一槽是 CATALYST：JEI 对这个角色的说明正是"配方需要它、但它不被消耗"，
        // 与"由这块母岩去转化邻格"完全对得上。它在 U（用途）那一侧的检索由这个角色覆盖。
        builder.addSlot(RecipeIngredientRole.CATALYST, BUDDING_SLOT_X, BUDDING_SLOT_Y)
                .addItemStack(recipe.budding())
                .setStandardSlotBackground()
                .addRichTooltipCallback((slotView, tooltip) -> {
                    // 「每随机刻掷一次 1/n、命中后在半径内随机取一格」——把规则级的掷骰讲清楚，
                    // 免得玩家把页面上那个 n 读成"每一格方块各有 1/n 概率"
                    tooltip.add(Component.translatable(BuddingInfoText.LANG + "conversion.chance",
                            recipe.chance(), recipe.radius()).withStyle(ChatFormatting.GRAY));
                    if (recipe.energyGated()) {
                        tooltip.add(Component.translatable(BuddingInfoText.LANG + "conversion.gated")
                                .withStyle(ChatFormatting.GRAY));
                    }
                });

        // 两端都是可见栏位：能拖、能收藏，按下 R/U 也能翻回这一页
        builder.addSlot(RecipeIngredientRole.INPUT, A_SLOT_X, SLOT_Y)
                .addItemStacks(recipe.inputs())
                .setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.OUTPUT, B_SLOT_X, SLOT_Y)
                .addItemStack(recipe.output())
                .setStandardSlotBackground();

        // "对着母岩按 R"这一侧只认 OUTPUT，CATALYST 在那一侧不算数，所以两种角色都补挂一遍，
        // 不必赌 JEI 的角色判定细节（信息页的不可见材料是同一个理由）。
        builder.addInvisibleIngredients(RecipeIngredientRole.INPUT).addItemStack(recipe.budding());
        builder.addInvisibleIngredients(RecipeIngredientRole.OUTPUT).addItemStack(recipe.budding());
    }

    @Override
    public void draw(BuddingConversion recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics,
                     double mouseX, double mouseY) {
        guiGraphics.blit(CREATE_JEI_WIDGETS, ARROW_X, ARROW_Y, ARROW_U, ARROW_V, ARROW_W, ARROW_H);
        drawChance(guiGraphics, recipe.chance());
    }

    /** 箭头下面那行「n 随机刻」，居中于箭头 */
    private static void drawChance(GuiGraphics guiGraphics, int chance) {
        Font font = Minecraft.getInstance().font;
        Component text = Component.translatable(BuddingInfoText.LANG + "conversion.ticks", chance);
        guiGraphics.drawString(font, text, ARROW_X + (ARROW_W - font.width(text)) / 2, TEXT_Y,
                TEXT_COLOR, false);
    }
}
