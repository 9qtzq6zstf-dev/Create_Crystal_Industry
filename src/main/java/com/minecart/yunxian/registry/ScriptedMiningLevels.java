package com.minecart.yunxian.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 脚本给母岩（以及它的芽/晶簇）设的<b>开采等级</b>：按方块 id 记一份"要什么档位才拿得到掉落"，
 * 玩家挖到登记过的方块时按它判定（{@code PlayerEvent.HarvestCheck}，在
 * {@code Player#hasCorrectToolForDrops} 里触发）。
 * <p>
 * 为什么要在运行时判定，而不是像 {@code CustomBudding.create} 那样挂方块标签：
 * {@code minecraft:needs_*_tool} 这类标签是注册期烘进方块状态里的，<b>已经注册好的方块改不了</b>
 * （脚本用 {@code CustomBudding.modify} 改的正是这种方块）。而 {@code HarvestCheck} 允许在
 * "能不能采"这一步直接给答案，于是等级可以后改。代价是<b>只影响原版这一步判定</b>：
 * 方块标签本身没变，别的模组若直接读标签（而不是走原版的采集判定）看不到这个改动。
 * <p>
 * 工具<b>种类</b>不归这里管（那仍然由方块注册时的挖掘标签决定，改不了）——这里只补"等级够不够"。
 */
public final class ScriptedMiningLevels {

    private static final Logger LOGGER = LoggerFactory.getLogger("create_crystal_industry.mining");

    private ScriptedMiningLevels() {
    }

    /**
     * 认得的等级，用"原版里正好需要这一档的代表方块"表示。
     * <p>
     * 1.21 的 {@code Tier} 没有数字等级，能不能采完全由工具的 {@code minecraft:tool} 组件说了算；
     * 所以这里不去猜工具是什么材质，而是<b>拿代表方块去问工具本身</b>
     * （{@code ItemStack#isCorrectToolForDrops}）——模组工具、附魔与数据包改动都照顾得到，
     * 与玩家平时挖矿看到的判定完全一致。
     */
    private enum Tier {
        /** 不设等级：谁都能拿掉落。写 {@code .buddingLevel('none')} 得到它——连方块原有的等级也一并压掉 */
        NONE(null),
        /** 石头档：代表方块用铁矿石（它在 {@code #minecraft:needs_stone_tool} 里） */
        STONE(Blocks.IRON_ORE),
        /** 铁档：代表方块用钻石矿石（在 {@code #minecraft:needs_iron_tool} 里） */
        IRON(Blocks.DIAMOND_ORE),
        /** 钻石档：代表方块用黑曜石（在 {@code #minecraft:needs_diamond_tool} 里） */
        DIAMOND(Blocks.OBSIDIAN);

        @Nullable
        private final Block representative;

        Tier(@Nullable Block representative) {
            this.representative = representative;
        }
    }

    /**
     * 认得的等级标签：就是原版三档（{@code CustomBudding} 里写裸名字 {@code stone} / {@code iron} /
     * {@code diamond} 时解析出来的也是这三个）。
     * <p>
     * 别的标签（{@code neoforge:needs_netherite_tool}、自己写的）认不了：原版没有"必须下界合金"的
     * 代表方块，1.21 的 {@code Tier} 也没有数字等级可查。那种情况记一条警告、不改判定——
     * 挂标签才是它们该走的路（那也是 {@code create} 的做法，注册时就写）。
     */
    private static final Map<ResourceLocation, Tier> KNOWN_TAGS = Map.of(
            ResourceLocation.withDefaultNamespace("needs_stone_tool"), Tier.STONE,
            ResourceLocation.withDefaultNamespace("needs_iron_tool"), Tier.IRON,
            ResourceLocation.withDefaultNamespace("needs_diamond_tool"), Tier.DIAMOND);

    /** 方块 id → 要求的档位（只在启动期写、运行期读，照 {@code ScriptedBlockDrops} 的模板） */
    private static final Map<ResourceLocation, Tier> TIERS = new ConcurrentHashMap<>();

    /**
     * 登记一个方块的开采等级。
     *
     * @param blockId  方块 id（母岩本体、或某一级芽/晶簇）
     * @param levelTag 要求的等级标签（{@code minecraft:needs_iron_tool}）；{@code null} = 不设等级
     *                 （谁都能拿到掉落，连方块原有的等级也一并压掉）
     */
    public static void register(ResourceLocation blockId, @Nullable ResourceLocation levelTag) {
        if (levelTag == null) {
            TIERS.put(blockId, Tier.NONE);
            return;
        }
        Tier tier = KNOWN_TAGS.get(levelTag);
        if (tier == null) {
            LOGGER.warn("[Mining] 运行时的开采等级认不了标签 {}（方块 {}）：只认原版三档"
                    + "（minecraft:needs_{stone,iron,diamond}_tool）。要挂别的等级标签请在 "
                    + "CustomBudding.create 里写，那是注册期的事", levelTag, blockId);
            return;
        }
        TIERS.put(blockId, tier);
    }

    /** 事件入口（在 {@code Yunxian} 的构造器里挂到游戏事件总线上） */
    public static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (TIERS.isEmpty()) {
            return;
        }
        BlockState state = event.getTargetBlock();
        Tier tier = TIERS.get(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
        if (tier == null) {
            return; // 没登记过的方块：一点不碰，原样放行
        }
        if (tier == Tier.NONE) {
            event.setCanHarvest(true);
            return;
        }
        // 事件本身不给工具（它只有方块与玩家），得自己从玩家手上拿——
        // 事件是在挖方块那一步触发的，主手拿的正是正在用的那把工具
        ItemStack tool = event.getEntity().getMainHandItem();
        event.setCanHarvest(tool.isCorrectToolForDrops(tier.representative.defaultBlockState()));
    }
}
