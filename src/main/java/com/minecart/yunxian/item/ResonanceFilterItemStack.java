package com.minecart.yunxian.item;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.minecart.yunxian.registry.ModDataComponents;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 共振过滤器的判定逻辑：<b>本身不存过滤规则</b>，每次判定时现场去它所在的那个共振台网络里
 * 把每张台面上放着的物品都收集起来，逐个交给 Create 自己的 {@link FilterItemStack} 解释，
 * 最后<b>取并集</b>（任意一张台子接受就算接受）。
 * <p>
 * 为什么这样接得上：Create 的 {@code FilteringBehaviour.test(ItemStack)} 是
 * {@code filter.test(level, stack)}（拉模型），而 {@code filter} 就是本对象。
 * 所以台面上换个东西，所有接了本过滤器的漏斗/溜槽/工作盆<b>下一次判定</b>就变，
 * 不需要同步、不需要 tick、不需要监听容器变化。
 * <p>
 * 网络本身借用 Create 的 {@link LogisticallyLinkedBehaviour}：它就是一个
 * 「UUID → 一组方块实体」的全局注册表（用弱引用、靠 lazyTick 续期，区块卸载/方块被拆会自动掉出去），
 * 而且是<b>全局</b>的 —— 所以同一个网络里的台子天然可以跨维度，不需要我们自己做维度解析。
 * <p>
 * 这里是动态判定的，所以<b>绝对不能照抄 {@code ListFilterItemStack} 那种构造时快照的写法</b>
 * （它在构造器里就把 18 格读进 containedItems 了）。本类的解析发生在 {@link #resolveAll} 里、
 * 按游戏刻缓存。
 * <p>
 * 三种情况，界限划得很清楚：
 * <ol>
 *   <li><b>网络里有台子放着东西 → 取并集，空台面被忽略。</b>
 *       空台子不产出过滤器、也不影响别人 —— 否则一张空台子就能把整个网络放开，
 *       其余台子放什么都白搭。</li>
 *   <li><b>台子全都空着 → 全集</b>，和「过滤槽里没插过滤器」同一个意思
 *       （空的 {@code FilterItemStack} 的 test 恒为 true，物品和流体都是）。</li>
 *   <li><b>根本读不到 → 什么都不通过</b>（fail-closed）：没绑网络、网络里一台都没有、
 *       台子的区块没加载、被拆了。</li>
 * </ol>
 * 2 与 3 的分界是有意为之：台面空了是玩家自己动的手、看得见，跟空过滤槽一致；
 * 而"读不到"是环境造成的，那时候放开就等于「区块一卸载漏斗突然放行全部物品」。
 * <p>
 * 注意并集的一个后果，这是有意为之、但要心里有数：<b>一张台子的区块没加载时，它就不在并集里</b>
 * （{@code getAllPresent} 只给得出有效的方块实体），于是它那一份过滤规则会暂时消失。
 * 只有全部台子都不可达时才退化成"什么都不通过"。
 */
public class ResonanceFilterItemStack extends FilterItemStack {

    /** 按游戏刻缓存上一刻的解析结果，避免每个物品都重建一遍网络里所有台子的过滤器 */
    private long cachedTick = Long.MIN_VALUE;
    private Level cachedLevel;
    private List<FilterItemStack> cached = List.of();

    /**
     * 解析深度护栏。
     * <p>
     * 下面那道「台面上是共振过滤器就跳过」只挡住了直接自引用，挡不住间接环：
     * 网络 A 里某张台子上放一个<b>列表过滤器</b>，里面装着绑到网络 B 的共振过滤器；
     * B 里再放一个装着绑到 A 的 —— {@code ListFilterItemStack.test} 会逐个
     * {@code FilterItemStack.of} 它里面的东西，于是又绕回本类，无限递归成栈溢出
     * （直接崩游戏，不是卡一下）。这里按线程数深度，超了就当「读不到」处理。
     */
    private static final ThreadLocal<Integer> RESOLVE_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int MAX_RESOLVE_DEPTH = 4;

    public ResonanceFilterItemStack(ItemStack filter) {
        super(filter);
    }

    // ==================== 判定入口 ====================

    @Override
    public boolean test(Level world, ItemStack stack, boolean matchNBT) {
        if (RESOLVE_DEPTH.get() >= MAX_RESOLVE_DEPTH)
            return false;
        RESOLVE_DEPTH.set(RESOLVE_DEPTH.get() + 1);
        try {
            // 并集：网络里任意一张台子的过滤器接受它，就算接受
            for (FilterItemStack filter : resolveAll(world))
                if (filter.test(world, stack, matchNBT))
                    return true;
            return false;
        } finally {
            RESOLVE_DEPTH.set(RESOLVE_DEPTH.get() - 1);
        }
    }

    @Override
    public boolean test(Level world, FluidStack stack, boolean matchNBT) {
        if (RESOLVE_DEPTH.get() >= MAX_RESOLVE_DEPTH)
            return false;
        RESOLVE_DEPTH.set(RESOLVE_DEPTH.get() + 1);
        try {
            for (FilterItemStack filter : resolveAll(world))
                if (filter.test(world, stack, matchNBT))
                    return true;
            return false;
        } finally {
            RESOLVE_DEPTH.set(RESOLVE_DEPTH.get() - 1);
        }
    }

    /**
     * 基类的 {@code fluid()} 有个 {@code fluidExtracted} 懒加载缓存标志，动态场景下那个缓存
     * 会把「第一次问到的流体」一直留着，所以必须绕开、直接委托给每次现场解析出来的对象。
     * <p>
     * 网络里可能有多个流体过滤器，这里取<b>第一个非空</b>的。调用方是流体阈值开关那类
     * "把过滤器当流体样本来读"的场景，并集在流体上没有明确含义，取第一个是最不意外的。
     */
    @Override
    public FluidStack fluid(Level level) {
        if (RESOLVE_DEPTH.get() >= MAX_RESOLVE_DEPTH)
            return FluidStack.EMPTY;
        RESOLVE_DEPTH.set(RESOLVE_DEPTH.get() + 1);
        try {
            for (FilterItemStack filter : resolveAll(level)) {
                FluidStack fluid = filter.fluid(level);
                if (!fluid.isEmpty())
                    return fluid;
            }
            return FluidStack.EMPTY;
        } finally {
            RESOLVE_DEPTH.set(RESOLVE_DEPTH.get() - 1);
        }
    }

    // ==================== 解析 ====================

    /**
     * 把网络里所有共振台当下的过滤器收集起来。返回空表表示「读不到」，
     * 调用方一律判不通过。
     */
    private List<FilterItemStack> resolveAll(Level world) {
        if (world == null)
            return List.of();

        long tick = world.getGameTime();
        if (tick == cachedTick && world == cachedLevel)
            return cached;

        List<FilterItemStack> resolved = resolveAllUncached(world);
        cachedTick = tick;
        cachedLevel = world;
        cached = resolved;
        return resolved;
    }

    private List<FilterItemStack> resolveAllUncached(Level world) {
        // 注意：基类的 filterItemStack 字段是 private，只能走公开的 item() 取。
        UUID network = item().get(ModDataComponents.RESONANCE_NETWORK.get());
        if (network == null)
            return List.of();                   // 没绑网络

        List<FilterItemStack> filters = new ArrayList<>();
        int reachable = 0;   // 够得着的台子数
        int empty = 0;       // 其中台面为空的
        // 客户端读的是另一张表（CLIENT_LINKS），两边各自由 lazyTick 里的 keepAlive 维护。
        // 这里不碰 getBlockEntity，也就不会为了一个过滤器去加载区块。
        for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(
                network, false, world.isClientSide)) {

            // 再核一次 id：Create 的 LogisticallyLinkedBehaviour.remove() 只清服务端那张表
            // （CLIENT_LINKS 是私有的，够不到），所以客户端那张旧网络的条目要等 400 tick 才自然过期。
            // 不加这道校验的话，刚并到新网络的台子会在旧网络的过滤器里多存活 20 秒。
            if (!link.freqId.equals(network))
                continue;

            if (!(link.blockEntity instanceof ResonanceTableBlockEntity table))
                continue;

            reachable++;
            ItemStack onTable = table.getFilterSource();
            if (onTable.isEmpty()) {
                // 空台面本身不产出过滤器，但要记一笔：全靠它们的时候才退化成"全集"，
                // 见方法末尾。这里刻意不往 filters 里塞"接受一切"——那样一张空台子就能
                // 把整个网络放开，别的台子放什么都白搭。
                empty++;
                continue;
            }

            // 递归护栏：台面上又放了一个共振过滤器的话，FilterItemStack.of 会再次走到本类
            if (onTable.getItem() instanceof ResonanceFilterItem)
                continue;

            // 完整镜像：台面上放列表过滤器就得到它的白/黑名单 + NBT 开关 + 18 格内容，
            // 放属性过滤器就得到它的属性筛选，放普通物品就退化成「只匹配这个物品类型」
            // （基类 FilterItemStack 走 FilterItem.testDirect → ItemHelper.sameItem）。
            //
            // 必须传副本：FilterItemStack.of 对 FilterItem 会调 trimFilterComponents，
            // 那个方法会 remove 掉 ENCHANTMENTS 与 ATTRIBUTE_MODIFIERS —— 而 getFilterSource()
            // 返回的是台面上那个物品栈本身，直接传进去等于把台上物品的附魔和属性修饰符抹掉。
            filters.add(FilterItemStack.of(onTable.copy()));
        }

        // 一台都够不着（没加载 / 被拆）→ 什么都不通过，fail-closed，见类注释
        if (reachable == 0)
            return List.of();

        // 有实货 → 就按并集来，空台面被忽略
        if (!filters.isEmpty())
            return filters;

        // 走到这里 = 够得着，但一张都没产出过滤器。两种情况要分开：
        //   - 台面全空 → 全集（"过滤槽里什么都没插"），用空的 FilterItemStack，
        //     它的 test 恒为 true（物品和流体都是）
        //   - 全被递归护栏挡下（台面上放的都是共振过滤器）→ 什么都不通过，不能当成全集
        return empty > 0 ? List.of(FilterItemStack.empty()) : List.of();
    }
}
