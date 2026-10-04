package com.minecart.yunxian.compat;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.budding.BuddingConversions;
import com.minecart.yunxian.budding.BuddingFamilies;
import com.minecart.yunxian.budding.BuddingFamilies.RegisteredFamily;
import com.minecart.yunxian.client.echo.EchoSpyglassFilterScreen;
import com.minecart.yunxian.compat.jei.BuddingConversion;
import com.minecart.yunxian.compat.jei.BuddingConversionCategory;
import com.minecart.yunxian.compat.jei.BuddingInfo;
import com.minecart.yunxian.compat.jei.BuddingInfoCategory;
import com.minecart.yunxian.compat.jei.BuddingInfoCollector;
import com.minecart.yunxian.item.EchoSpyglassItem;
import com.minecart.yunxian.network.SetFilterPayload;
import com.minecart.yunxian.recipe.BuddingConversionRecipe;
import com.minecart.yunxian.registry.ModRecipes;
import com.mojang.logging.LogUtils;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@JeiPlugin
public class YunxianJeiPlugin implements IModPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath(Yunxian.MODID, "jei_plugin");

    /**
     * 本插件实例。配置重载要从模组总线那边叫过来（见 {@code Yunxian} 的构造器），
     * 那里拿不到 JEI 造的这个对象，所以留一个静态引用；JEI 只会造一个。
     */
    @Nullable
    private static YunxianJeiPlugin instance;

    /** JEI 的运行时入口：拿到它才谈得上"不重进世界就换配方"，离开世界后置空 */
    @Nullable
    private IJeiRuntime runtime;

    /** 当前注册给 JEI 的侵染配方（配置一改，要按它们逐个收放） */
    private List<BuddingConversion> conversionRecipes = List.of();

    public YunxianJeiPlugin() {
        instance = this;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    // ==================== 两页的注册 ====================

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper guiHelper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(new BuddingInfoCategory(guiHelper),
                new BuddingConversionCategory(guiHelper));
    }

    /**
     * 每次 JEI 重载配方（进世界、数据包重载）都会重新收集一遍——生成条件正是从数据包里的世界生成 JSON
     * 现读的，所以不缓存，改了 JSON 页面立刻跟着变。
     * <p>
     * 两页各自收集、各自兜底：一边的数据坏了不该把另一边也饿死。
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        try {
            List<BuddingInfo> infos = BuddingInfoCollector.collect();
            LOGGER.info("[Yunxian] 注册 JEI 母岩信息页：{} 条", infos.size());
            registration.addRecipes(BuddingInfoCategory.TYPE, infos);
        } catch (RuntimeException e) {
            // 收集失败不该让整个 JEI 配方加载挂掉：宁可这一页空着，也别把报错丢进别人的模组列表里
            LOGGER.error("[Yunxian] 收集母岩信息失败，JEI 的母岩信息页将为空", e);
        }

        try {
            conversionRecipes = collectConversionRecipes();
            LOGGER.info("[Yunxian] 注册 JEI 侵染页：{} 条", conversionRecipes.size());
            registration.addRecipes(BuddingConversionCategory.TYPE, conversionRecipes);
        } catch (RuntimeException e) {
            conversionRecipes = List.of();
            LOGGER.error("[Yunxian] 读取侵染配方失败，JEI 的侵染页将为空", e);
        }

        // 侵染那几条露不露面由配置说了算，注册完先按当时的配置摆好。
        // 这一步在 JEI 给运行时之前是空转（见 applyConversionVisibility），所以 onRuntimeAvailable 里也来一次
        applyConversionVisibility();
    }

    // ==================== 配置改了，页面跟着变 ====================

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        this.runtime = jeiRuntime;
        applyConversionVisibility();
    }

    @Override
    public void onRuntimeUnavailable() {
        this.runtime = null;
    }

    /**
     * 配置改过之后重算一遍显示与否（模组入口在配置重载事件里调过来，见 {@code Yunxian} 的注册处）。
     * <p>
     * 之所以不重新注册：JEI 没有"换掉已注册配方"这回事，能做的只有隐藏与恢复；重新加一遍还会让
     * 值相等的那些配方在页面里出现两次。
     */
    public static void refreshRecipes() {
        // 单人游戏里集成服务端也持有同一份 COMMON 配置，重载事件可能从服务端线程过来；
        // JEI 的运行时只能在客户端线程上碰，所以统一丢回主线程执行
        Minecraft.getInstance().execute(() -> {
            YunxianJeiPlugin plugin = instance;
            if (plugin != null) {
                plugin.applyConversionVisibility();
            }
        });
    }

    /**
     * 按当前配置收起/放出「侵染」页里的配方。
     * <p>
     * 判定与运行时同源：读的是 {@code BuddingConversions.configAllows}——玩家把总开关关掉、或这块母岩
     * 不在名单里，它整页的转化就立刻收起来；与游戏里"这块母岩什么都不做"完全一致。
     * <p>
     * 还没进世界（没有 runtime）时什么都不做：那一次的注册本来就会带着最新的配置。
     */
    private void applyConversionVisibility() {
        IJeiRuntime runtime = this.runtime;
        if (runtime == null) {
            return;
        }

        List<BuddingConversion> hidden = new ArrayList<>();
        List<BuddingConversion> shown = new ArrayList<>();
        for (BuddingConversion recipe : conversionRecipes) {
            // 配置按母岩统一管：这块母岩被停掉，它整页的转化一起收起来
            // （与游戏里"一条都不做"一致，见 BuddingConversions.allowed）
            (BuddingConversions.configAllows(recipe.ownerId()) ? shown : hidden).add(recipe);
        }

        IRecipeManager manager = runtime.getRecipeManager();
        manager.hideRecipes(BuddingConversionCategory.TYPE, hidden);
        manager.unhideRecipes(BuddingConversionCategory.TYPE, shown);
    }

    /**
     * 取侵染配方并摊成 JEI 显示用的记录。
     * <p>
     * 配方是从<b>客户端</b>配方管理器读的——那里是服务端同步过来的那一批，所以页面显示的就是服务器上
     * 真正生效的转化（整合包改过配方也看得出来）。配方里是方块集合与"变成本母岩自身"，而 JEI 的槽
     * 只认物品栈，所以在这里展开：输入的标签摊成成员（JEI 在一个槽里轮播，每个成员都能被单独检索）、
     * 空产物换成母岩自己。方块没有物品的整条跳过——空 ItemStack 会让 JEI 报错。
     * <p>
     * 还没进世界（没有 level）时返回空表：进世界后 JEI 会再调一次 {@code registerRecipes}。
     */
    private static List<BuddingConversion> collectConversionRecipes() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return List.of();
        }

        List<RecipeHolder<BuddingConversionRecipe>> holders = new ArrayList<>(
                level.getRecipeManager().getAllRecipesFor(ModRecipes.BUDDING_CONVERSION.get()));
        // 按配方 id 排序：配方管理器自己的返回顺序不保证稳定，页面顺序与游戏里的规则顺序都照它定
        holders.sort(Comparator.comparing(holder -> holder.id().toString()));

        List<BuddingConversion> conversions = new ArrayList<>();
        for (RecipeHolder<BuddingConversionRecipe> holder : holders) {
            BuddingConversionRecipe recipe = holder.value();
            ItemStack budding = itemOf(recipe.budding());
            if (budding.isEmpty()) {
                continue;
            }
            String ownerId = BuddingConversions.ownerIdOf(recipe.budding());
            for (BuddingConversionRecipe.Replacement replacement : recipe.replacements()) {
                List<ItemStack> inputs = new ArrayList<>();
                for (Holder<Block> input : replacement.input()) {
                    ItemStack item = itemOf(input.value());
                    if (!item.isEmpty()) {
                        inputs.add(item);
                    }
                }
                ItemStack output = replacement.output().map(YunxianJeiPlugin::itemOf).orElse(budding);
                if (inputs.isEmpty() || output.isEmpty()) {
                    continue;
                }
                conversions.add(new BuddingConversion(budding, List.copyOf(inputs), output,
                        recipe.chance(), recipe.radius(), recipe.gated(), ownerId));
            }
        }
        return List.copyOf(conversions);
    }

    /** 方块的物品；方块没有物品时返回空栈——空 ItemStack 会让 JEI 报错，调用方据此跳过 */
    private static ItemStack itemOf(Block block) {
        Item item = block.asItem();
        return item == Items.AIR ? ItemStack.EMPTY : item.getDefaultInstance();
    }

    /**
     * 母岩与晶簇本体当催化剂，玩家在 JEI 里对着它们按 R/U 就能翻到信息页。
     * <p>
     * 只登记自带家族与原版母岩：脚本/外来母岩的物品已经由信息页里的"不可见材料"覆盖了检索，
     * 而这里要遍历的 {@code BuddingFamilies} 是纯静态数据，不必为此重跑一遍信息收集。
     * <p>
     * 注意「侵染」页<b>故意不登记催化剂</b>：催化剂是按分类生效的，登记之后的后果是对着母岩按 R
     * 会列出那一页的<b>所有</b>配方（每块母岩的每条转化）。那一页改由配方内的不可见材料精确匹配，
     * 按 R 只会翻到自己那几条。
     */
    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        List<ItemStack> catalysts = new ArrayList<>();
        for (RegisteredFamily family : BuddingFamilies.ALL) {
            if (!family.isRegistered()) {
                continue;
            }
            addCatalyst(catalysts, family.budding().get());
            addCatalyst(catalysts, family.cluster().get());
        }
        addCatalyst(catalysts, Blocks.BUDDING_AMETHYST);

        if (!catalysts.isEmpty()) {
            registration.addRecipeCatalysts(BuddingInfoCategory.TYPE, catalysts.toArray(ItemStack[]::new));
        }
    }

    /** 方块没有物品时跳过（空 ItemStack 会让 JEI 报错） */
    private static void addCatalyst(List<ItemStack> catalysts, Block block) {
        Item item = block.asItem();
        if (item != Items.AIR) {
            catalysts.add(item.getDefaultInstance());
        }
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        LOGGER.info("[Yunxian] 注册 JEI 幽灵拖拽处理器");
        registration.addGhostIngredientHandler(EchoSpyglassFilterScreen.class, new EchoGhostIngredientHandler());
    }

    /** 幽灵拖拽处理器：只对回响望远镜过滤界面生效 */
    public static class EchoGhostIngredientHandler
            implements IGhostIngredientHandler<EchoSpyglassFilterScreen> {

        @Override
        public <I> List<Target<I>> getTargetsTyped(EchoSpyglassFilterScreen gui,
                                                   ITypedIngredient<I> ingredient,
                                                   boolean doStart) {
            List<Target<I>> targets = new ArrayList<>();

            // 只处理物品拖拽（流体等一律忽略）
            if (ingredient.getType() == VanillaTypes.ITEM_STACK) {
                I typed = ingredient.getIngredient();
                if (typed instanceof ItemStack stack && EchoSpyglassItem.isGhostAllowed(stack)) {
                    Slot filterSlot = gui.getMenu().getFilterSlot();
                    // 注意：JEI 19 的 Target 区域是屏幕坐标，必须加上 guiLeft / guiTop
                    Rect2i area = new Rect2i(gui.getGuiLeft() + filterSlot.x,
                            gui.getGuiTop() + filterSlot.y, 16, 16);
                    targets.add(new EchoTarget<>(gui, area));
                }
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }

        @Override
        public boolean shouldHighlightTargets() {
            return true;   // 拖拽划过过滤槽时由 JEI 画高亮框
        }
    }

    /** 落点：鼠标在 Target 区域松开时被 JEI 回调 */
    private static class EchoTarget<I> implements IGhostIngredientHandler.Target<I> {

        private final EchoSpyglassFilterScreen gui;
        private final Rect2i area;

        EchoTarget(EchoSpyglassFilterScreen gui, Rect2i area) {
            this.gui = gui;
            this.area = area;
        }

        @Override
        public Rect2i getArea() {
            return area;
        }

        @Override
        public void accept(I ingredient) {
            if (!(ingredient instanceof ItemStack stack)) {
                return;
            }
            ItemStack copy = stack.copy();
            copy.setCount(1);

            // ① 客户端立即显示幽灵物品（JEI 拖拽不产生点击包，必须自己改槽）
            gui.getMenu().getFilterSlot().set(copy);

            // ② 同步服务端：服务端校验并写入望远镜数据组件
            PacketDistributor.sendToServer(new SetFilterPayload(copy));
        }
    }
}