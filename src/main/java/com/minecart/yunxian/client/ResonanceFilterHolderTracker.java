package com.minecart.yunxian.client;

import com.minecart.yunxian.item.ResonanceFilterItem;
import com.minecart.yunxian.registry.ModDataComponents;
import com.simibubi.create.foundation.blockEntity.behaviour.filtering.FilteringBehaviour;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 客户端记录「哪些元件的过滤槽里插着本网络的共振过滤器」，供描边用。
 * <p>
 * 为什么需要它：共振台自己会通过 {@code LogisticallyLinkedBehaviour} 登记进网络表，但
 * 漏斗/溜槽/工作盆是 Create 的方块，没法给它们挂行为。要凭空找出"谁插着我们的过滤器"
 * 只能遍历已加载的方块实体，太贵。
 * <p>
 * 所以反过来做：让 {@code FilteringBehaviour} 在它每 tick 的 {@code tick()} 里自报家门
 * （注入点见 {@code BlockEntityBehaviourMixin}，只挂在客户端）。代价是每个过滤行为每 tick
 * 一次 {@code instanceof}，判定失败立刻返回。
 * <p>
 * <b>只在客户端</b>：这是纯展示用的，不落盘、不同步、不参与判定 —— 和
 * {@code ResonanceNetworkOutlineRenderer} 同一性质。注意这一条是<b>要在代码里强制</b>的
 * （见 {@link #track}）：注入 {@code BlockEntityBehaviour.tick()} 的那个 mixin 属于
 * mixins.json 的 {@code "client"} 段，而 client 段对<b>集成服务端</b>的类同样生效，
 * 集成服务端线程也在 tick 方块实体 —— 不挡的话就是两条线程同时动这张表，直接崩。
 * <p>
 * 每 tick 整体清空重建，不做弱引用/过期那套：能活着被 tick 到的方块实体本来就是加载中的，
 * 而"这 tick 没被 tick 到"本身就等价于"不该描边"。
 */
public final class ResonanceFilterHolderTracker {

    // 并发容器：正常情况下只会被客户端线程碰（track 里挡了非客户端关卡），
    // 但它是静态表、又跨客户端/集成服务端两侧的调用点，用并发容器是廉价的保险
    private static final Map<UUID, Set<FilteringBehaviour>> HOLDERS = new ConcurrentHashMap<>();

    private ResonanceFilterHolderTracker() {
    }

    public static void register() {
        // 客户端 tick 的最前面清空（Pre 在关卡 tick 之前），随后的关卡 tick 会把数据重新填进来，
        // 同 tick 后面的 Post（描边那一刻）读到就是本 tick 的实时结果
        NeoForge.EVENT_BUS.addListener(ResonanceFilterHolderTracker::onClientTickPre);
    }

    private static void onClientTickPre(ClientTickEvent.Pre event) {
        HOLDERS.clear();
    }

    /** 由 {@code BlockEntityBehaviourMixin} 在 {@code BlockEntityBehaviour.tick()} 的 HEAD 调用 */
    public static void track(FilteringBehaviour behaviour) {
        // 只认客户端关卡。这一条是必须的，不是优化：那个 mixin 放在 mixins.json 的 "client" 段，
        // 而 client 段对【集成服务端】的类同样生效 —— 集成服务端线程也在 tick 方块实体，
        // 于是会出现客户端线程 clear()、服务端线程 computeIfAbsent 同时动同一张表，
        // 直接 ConcurrentModificationException 崩客户端（2026-10-01 实测崩过）。
        // 服务端那边的方块实体本来也不需要描边。
        Level world = behaviour.getWorld();
        if (world == null || !world.isClientSide)
            return;

        ItemStack filter = behaviour.getFilter();
        if (!(filter.getItem() instanceof ResonanceFilterItem))
            return;

        UUID network = filter.get(ModDataComponents.RESONANCE_NETWORK.get());
        if (network == null)
            return;   // 没接入网络的过滤器不该被描出来

        HOLDERS.computeIfAbsent(network, key -> ConcurrentHashMap.newKeySet()).add(behaviour);
    }

    /**
     * 返回的是<b>快照</b>而不是内部那张活集合：描边那边是 for-each 遍历，
     * 拿着活集合遍历时只要同 tick 有方块实体被 tick 到就会边遍历边改，同样是崩。
     */
    public static Collection<FilteringBehaviour> getForNetwork(UUID network) {
        Set<FilteringBehaviour> holders = HOLDERS.get(network);
        return holders == null ? Set.of() : Set.copyOf(holders);
    }
}
