package com.minecart.yunxian.client.budding;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.minecart.yunxian.budding.GrowthEnvironment;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * 生长环境（维度 / 群系）的显示名——<b>只在客户端用</b>
 * （JEI 母岩信息页与护目镜浮窗共用，两处显示的名字才不会打架）。
 * <p>
 * 名字一律走语言文件，查不到就照实写 id，绝不猜：
 * <ul>
 *   <li>维度：{@code create_crystal_industry.dimension.<命名空间>.<路径>}
 *       ——原版没有维度名的语言键，只能自己定一套。本模组给原版三个维度都备了
 *       （{@code minecraft.overworld} / {@code minecraft.the_nether} / {@code minecraft.the_end}，
 *       注意下界的 id 是 {@code the_nether} 不是 {@code nether}）；
 *       附属模组与整合包要给自己维度起名，加同前缀的键即可；</li>
 *   <li>群系：原版就有的 {@code biome.<命名空间>.<路径>}（如「下界荒地」）；</li>
 *   <li>群系标签：{@code create_crystal_industry.biome_tag.<命名空间>.<路径>}
 *       （如 {@code #minecraft:is_nether} → 「下界群系」），与流体标签同一套约定；
 *       本模组把那十几个"地理/气候"类的原版标签备齐了，没备的就原样写 {@code #id}。</li>
 * </ul>
 * <p>
 * 全部走 {@link Component#translatableWithFallback} 而不是"先 {@code I18n.exists} 再决定"：
 * 后者的判断发生在<b>构建这个名字的那一刻</b>，那一刻客户端语言未必已经加载好
 * （JEI 的母岩信息页就是在客户端构建的，不一定等到进世界），一旦判成"没有翻译"，
 * 这串名字就被定格成字面量 id，之后也不会再变；而带兜底的翻译组件是<b>渲染时</b>才取名字的，
 * 语言后到、玩家中途切语言、资源包改译名，它都跟得上。
 */
public final class EnvironmentDisplay {

    /** 维度语言键前缀；后接维度的命名空间与路径，如 {@code create_crystal_industry.dimension.minecraft.the_nether} */
    private static final String DIMENSION_KEY_PREFIX = "create_crystal_industry.dimension.";

    /** 气候关键字语言键前缀；后接关键字的小写名，如 {@code create_crystal_industry.climate.cold} =「寒冷群系」 */
    private static final String CLIMATE_KEY_PREFIX = "create_crystal_industry.climate.";

    /** 群系标签语言键前缀；后接标签的命名空间与路径，如 {@code create_crystal_industry.biome_tag.minecraft.is_nether} */
    private static final String BIOME_TAG_KEY_PREFIX = "create_crystal_industry.biome_tag.";

    /** 并列名字之间的分隔符。中英文都读得顺，所以不放进语言文件（与 JEI 的名单同款） */
    private static final String SEPARATOR = " / ";

    private EnvironmentDisplay() {
    }

    /** 生长维度的并列名（如「下界 / 末地」）；没写维度时返回空组件 */
    public static Component dimensions(GrowthEnvironment environment) {
        return join(environment.dimensions(), EnvironmentDisplay::dimensionName);
    }

    /** 肯定侧的群系并列名（具体群系用原名，标签写成 {@code #minecraft:is_nether}，关键字写成「寒冷群系」） */
    public static Component biomes(GrowthEnvironment environment) {
        return conditions(environment, false);
    }

    /** 否定侧的群系并列名；没有否定条件时返回空组件 */
    public static Component excludedBiomes(GrowthEnvironment environment) {
        return conditions(environment, true);
    }

    private static Component conditions(GrowthEnvironment environment, boolean exclude) {
        List<Component> names = new ArrayList<>(environment.biomeConditions().size());
        for (GrowthEnvironment.BiomeCondition condition : environment.biomeConditions()) {
            if (condition.exclude() == exclude) {
                names.add(conditionName(condition));
            }
        }
        return join(names);
    }

    /** 一条群系条件 → 名字：具体群系走原版语言键、标签走本模组的标签语言键、气候关键字走本模组的语言键 */
    private static Component conditionName(GrowthEnvironment.BiomeCondition condition) {
        if (condition.climate() != null) {
            String key = CLIMATE_KEY_PREFIX + condition.climate().name().toLowerCase(Locale.ROOT);
            return Component.translatableWithFallback(key, condition.climate().name());
        }
        if (condition.tag() != null) {
            return biomeTagName(condition.tag());
        }
        return biomeName(condition.biome());
    }

    /** 单个维度的显示名 */
    public static Component dimensionName(ResourceKey<Level> dimension) {
        ResourceLocation location = dimension.location();
        return Component.translatableWithFallback(
                DIMENSION_KEY_PREFIX + location.getNamespace() + "." + location.getPath(),
                location.toString());
    }

    /** 单个群系的显示名：原版 {@code biome.<命名空间>.<路径>} 语言键，没有就写 id */
    public static Component biomeName(ResourceKey<Biome> biome) {
        ResourceLocation location = biome.location();
        return Component.translatableWithFallback(
                "biome." + location.getNamespace() + "." + location.getPath(),
                location.toString());
    }

    /** 群系标签的显示名：{@code create_crystal_industry.biome_tag.<命名空间>.<路径>}，没翻译就写 {@code #命名空间:路径} */
    public static Component biomeTagName(TagKey<Biome> tag) {
        ResourceLocation location = tag.location();
        return Component.translatableWithFallback(
                BIOME_TAG_KEY_PREFIX + location.getNamespace() + "." + location.getPath(),
                "#" + location);
    }

    private static <T> Component join(List<T> entries, java.util.function.Function<T, Component> name) {
        List<Component> names = new ArrayList<>(entries.size());
        for (T entry : entries) {
            names.add(name.apply(entry));
        }
        return join(names);
    }

    private static Component join(List<Component> names) {
        MutableComponent joined = Component.empty();
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) {
                joined.append(Component.literal(SEPARATOR));
            }
            joined.append(names.get(i));
        }
        return joined;
    }
}
