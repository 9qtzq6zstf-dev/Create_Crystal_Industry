package com.minecart.yunxian.integration.kubejs;

import java.util.List;
import java.util.Map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.minecart.yunxian.Yunxian;

import dev.latvian.mods.kubejs.core.RecipeManagerKJS;
import dev.latvian.mods.kubejs.plugin.ClassFilter;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.plugin.builtin.event.ServerEvents;
import dev.latvian.mods.kubejs.recipe.RecipesKubeEvent;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * KubeJS 插件：把 {@link CustomBudding} 绑成脚本全局对象，放开脚本对本模组类的访问，
 * 并把脚本写的 {@code .transform(...)} 送进配方表。
 * <p>
 * KubeJS 不读模组 jar 里的脚本，但会读 jar 根部的 {@code kubejs.plugins.txt}
 * （一行一个类名），据此加载本类——所以本类<b>只在装了 KubeJS 时才会被加载</b>，
 * 没装 KubeJS 的玩家完全不受影响（与 AE2 联动的隔离做法一致）。
 */
public class YunxianKubeJSPlugin implements KubeJSPlugin {

    private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.kubejs");

    @Override
    public void registerBindings(BindingRegistry bindings) {
        // 脚本里直接写 CustomBudding.create(...) / new CustomBuddingOptions()
        bindings.add("CustomBudding", CustomBudding.class);
        bindings.add("CustomBuddingOptions", CustomBudding.Options.class);
    }

    @Override
    public void registerClasses(ClassFilter filter) {
        // 进阶用法：脚本可以直接 Java.loadClass 本模组的生长引擎与定义自己拼
        filter.allow("com.minecart.yunxian.**");
    }

    /**
     * 每次脚本（重）加载之后，把 KubeJS 的配方流水线那道门重新打开。
     * <p>
     * KubeJS 只在"有脚本监听 {@code ServerEvents.recipes}"时才处理配方表（{@code RecipeManagerMixin}
     * 里那道 {@code if (ServerEvents.RECIPES.hasListeners())}），而 {@code .transform(...)} 登记的规则
     * 正是在流水线里的 {@link #beforeRecipeLoading} 注入的——整合包只写 {@code .transform}、不写配方脚本时，
     * 那条钩子一次都不会被调到。
     * <p>
     * 登记点必须在<b>这里</b>：{@code ScriptManager.reload()} 的顺序是
     * {@code clearCaches}（我们的登记表这时清空）→ {@code unload()}（{@code ScriptType#unload} 会把
     * <b>所有事件监听器清空</b>）→ 加载脚本 → {@code afterScriptsLoaded}。所以登记在插件初始化
     * （{@code init}/{@code registerEvents}）的那一份撑不到配方加载就被清掉了，门照样是关的。
     * <p>
     * 监听器本身什么都不做，只是把门打开（{@code cx = null} 是插件期的正常用法：脚本期才有
     * "只能在脚本加载时登记"的限制）。代价是 KubeJS 会走一遍配方解析——没写配方脚本时那一步是透明的
     * （已知类型照常解析、未知类型原样保留，这也是我们不必写 {@code RecipeSchema} 的原因）。
     */
    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        // 配方属于服务端脚本这一档，只在这里登记一次，免得每种脚本类型都塞一个进去
        if (manager.scriptType == ScriptType.SERVER) {
            ServerEvents.RECIPES.listen(null, ScriptType.SERVER, null, event -> null);
        }
    }

    /**
     * 把脚本用 {@code .transform(...)} 登记的侵染规则塞进配方表。
     * <p>
     * 时机是这里的原因：脚本跑在方块注册期，那时配方系统还没开始加载，所以 {@code CustomBudding}
     * 只能先把请求记在缓冲里；这个钩子在 KubeJS 解析配方表之前被调用，正好补上。
     * 直接往 {@code recipes} 里放原始 JSON 就行——本模组的配方类型有自己的序列化器，
     * KubeJS 对上没有注册 schema 的类型也是原样保留，不必再写一套 RecipeSchema。
     * <p>
     * <b>每一轮配方加载都要重新交一遍</b>（客户端一轮、集成服务端/服务器再来一轮），所以
     * {@code CustomBudding} 那份登记表是取出不清空的；配方 id 按登记序号定，重交的是同一批 id。
     */
    @Override
    public void beforeRecipeLoading(RecipesKubeEvent event, RecipeManagerKJS manager,
                                    Map<ResourceLocation, JsonElement> recipes) {
        List<CustomBudding.TransformRequest> requests = CustomBudding.transformRequests();
        for (int i = 0; i < requests.size(); i++) {
            CustomBudding.TransformRequest request = requests.get(i);
            if (!resolvable(request)) {
                continue;
            }
            recipes.put(recipeId(request, i), toRecipeJson(request));
        }
    }

    /**
     * 脚本写的输入与产物在不在（这时方块注册表已经加载完，查得准）。
     * 不在就跳过这一条并记一条警告——否则它会变成一条"解码失败的配方"，玩家在日志里看到的是
     * 一句没头没尾的 {@code Parsing error}，而不是"你 .transform 里的方块 id 写错了"。
     */
    private static boolean resolvable(CustomBudding.TransformRequest request) {
        boolean inputOk = request.input().startsWith("#")
                ? tagExists(request.input().substring(1))
                : blockExists(request.input());
        if (!inputOk || !blockExists(request.output())) {
            LOGGER.warn("[KubeJS] .transform 的{}不存在，这条规则已跳过：{} → {}",
                    inputOk ? "产物" : "输入", request.input(), request.output());
            return false;
        }
        return true;
    }

    private static boolean blockExists(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        Block block = location == null ? null : BuiltInRegistries.BLOCK.get(location);
        return block != null && block != Blocks.AIR;
    }

    private static boolean tagExists(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location != null
                && BuiltInRegistries.BLOCK.getTag(TagKey.create(Registries.BLOCK, location)).isPresent();
    }

    /**
     * 启动脚本（重新）加载之前清掉上一次登记的规则——脚本重跑会按当次内容重新登记，
     * 这样删掉一条 {@code .transform} 才会真的消失。
     * <p>
     * 清空的时机只能绑在<b>启动脚本</b>上：{@code clearCaches()} 对每种脚本类型都会调一次，
     * 绑在它上面的话，服务端脚本一加载就会把启动期登记好的规则清掉（而 {@code .transform}
     * 只写在启动脚本里），于是规则永远到不了配方表。
     */
    @Override
    public void beforeScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.STARTUP) {
            CustomBudding.clearTransformRequests();
        }
    }

    /** 注入配方的 id：按母岩 + 该次登记的序号定，同一个脚本重载后 id 不变 */
    private static ResourceLocation recipeId(CustomBudding.TransformRequest request, int index) {
        return ResourceLocation.fromNamespaceAndPath(Yunxian.MODID,
                "kubejs/" + request.budding().getNamespace() + "_" + request.budding().getPath()
                        + "/transform_" + index);
    }

    /** 一份 {@code budding_conversion} 配方 JSON：概率与半径照脚本写的，不参与付费 */
    private static JsonElement toRecipeJson(CustomBudding.TransformRequest request) {
        JsonObject recipe = new JsonObject();
        recipe.addProperty("type", Yunxian.MODID + ":budding_conversion");
        recipe.addProperty("budding", request.budding().toString());
        recipe.addProperty("chance", request.chance());
        recipe.addProperty("radius", request.radius());

        JsonObject replacement = new JsonObject();
        // 输入的三种写法（单个 id / id 列表 / '#标签'）里，脚本只写得出前一种与标签，
        // 而配方那边用同一个字段收，所以原样放进 JSON 即可
        replacement.addProperty("input", request.input());
        replacement.addProperty("output", request.output());
        JsonArray replacements = new JsonArray();
        replacements.add(replacement);
        recipe.add("replacements", replacements);
        return recipe;
    }
}

