package com.minecart.yunxian.menu;

import com.minecart.yunxian.blockentity.ResonanceTableBlockEntity;
import com.minecart.yunxian.network.SetNetworkNamePayload;
import com.minecart.yunxian.registry.ModDataComponents;
import com.minecart.yunxian.registry.ModMenus;
import com.minecart.yunxian.resonance.ResonanceNetworkNames;
import com.simibubi.create.content.logistics.filter.AbstractFilterMenu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * 共振过滤器右键打开的界面。
 * <p>
 * 版式整套是 Create「包裹过滤器」那一套（{@code PackageFilterMenu}）：面板同样是 218×79、
 * 玩家槽位同样在 (40,101)、同样没有幽灵槽 —— 因为这里没有「往里放东西」这回事，
 * 界面只做两件事：固定显示一台共振台，以及给这张网络起个名字。
 * <p>
 * 接的仍是 {@link AbstractFilterMenu}（幽灵槽的处理、玩家物品栏槽位、「手持物品被换走就关界面」
 * 都由那条继承链做好），只是槽位一个都不加。服务端实例由 {@code FilterItem#use} 里的
 * {@code openMenu} 创建，客户端实例由 {@link ModMenus#RESONANCE_FILTER_MENU} 的工厂
 * 从数据流里读出手持物品重建。
 * <p>
 * <b>名字存在 {@link ResonanceNetworkNames} 里（按网络 id，落在世界存档）</b>，
 * 不是存在物品上：同一张网络上的每个过滤器看到的必须是同一个名字。客户端没有那份存档，
 * 所以开界面时由 {@code ResonanceFilterItem#use} 把当前名字一起塞进数据流，
 * 之后每敲一下字由 {@link SetNetworkNamePayload} 送回服务端落盘。
 */
public class ResonanceFilterMenu extends AbstractFilterMenu {

    /**
     * 玩家槽位第一格，跟着 {@code player_inventory.png} 在窗口里的落点走。三个数叠出来：
     * <ol>
     *   <li>窗口左移 11（{@code ResonanceFilterScreen#WINDOW_X_OFFSET}）；</li>
     *   <li>贴图按 {@code getLeftOfCentered} 摆，再让出 (218-176)/2 = 21 —— 合起来横坐标 32；</li>
     *   <li>贴图内部第一格恒定在 (8,18)：{@code MenuBase#addPlayerSlots} 传进去的 (x,y) 就是第一格。</li>
     * </ol>
     * 纵向 = 面板 79 + 间隙 4 + 18。后面三行 / 快捷栏的间距由 {@code addPlayerSlots} 按 18 / 58 排。
     * <b>改动界面的窗口尺寸或偏移时，这两个数必须跟着对一遍。</b>
     */
    public static final int PLAYER_SLOT_X = 40;
    public static final int PLAYER_ROW0_Y = 101;

    /**
     * 手里这件过滤器接入的网络 id，没接入就是 {@code null}（那时输入框整个禁用）。
     * <p>
     * <b>不能带字段初始值</b>：这两个字段是在构造器链里被赋值的（{@link #initAndReadInventory}
     * 由 {@code MenuBase} 的构造器调、{@link #createOnClient} 更是先于子类字段初始化跑），
     * 写 {@code = ""} 之类的初始值会在 {@code super()} 返回后把刚读到的东西清掉。
     */
    @Nullable
    private UUID network;
    @Nullable
    private String networkName;

    /** 客户端：手持物品与当前名字都从数据流里读，顺序必须与 {@code ResonanceFilterItem#use} 的写法人一致 */
    public ResonanceFilterMenu(int id, Inventory playerInventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenus.RESONANCE_FILTER_MENU.get(), id, playerInventory, extraData);
    }

    /** 服务端：手持物品直接给 */
    public ResonanceFilterMenu(int id, Inventory playerInventory, ItemStack filter) {
        super(ModMenus.RESONANCE_FILTER_MENU.get(), id, playerInventory, filter);
    }

    public static ResonanceFilterMenu create(int id, Inventory playerInventory, ItemStack filter) {
        return new ResonanceFilterMenu(id, playerInventory, filter);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    protected ItemStack createOnClient(RegistryFriendlyByteBuf extraData) {
        ItemStack filter = super.createOnClient(extraData);
        networkName = extraData.readUtf(SetNetworkNamePayload.MAX_LENGTH);
        return filter;
    }

    @Override
    protected void initAndReadInventory(ItemStack filterItem) {
        network = filterItem.get(ModDataComponents.RESONANCE_NETWORK.get());

        // 名字只在服务端查得到（客户端没有世界存档），客户端那份是 createOnClient 刚读进来的，
        // 所以这里绝不能无条件盖掉 —— isClientSide 就是那条分界线。
        if (player.level().isClientSide || network == null)
            return;
        MinecraftServer server = player.level().getServer();
        if (server != null)
            networkName = ResonanceNetworkNames.get(server).getName(network);
    }

    @Override
    protected int getPlayerInventoryXOffset() {
        return PLAYER_SLOT_X;
    }

    @Override
    protected int getPlayerInventoryYOffset() {
        return PLAYER_ROW0_Y;
    }

    /** 没有可放东西的槽：面板上那个格子是固定渲染的共振台，不是槽位 */
    @Override
    protected void addFilterSlots() {
    }

    @Override
    protected ItemStackHandler createGhostInventory() {
        return new ItemStackHandler();
    }

    /**
     * shift 快速转移整个不做。<b>必须覆盖</b>：基类那套会往编号 36 的槽里塞东西
     * （{@code GhostItemMenu#quickMoveStack} 里的 {@code getSlot(i + 36)}），而这里
     * 一个过滤槽都没加，直接 shift 点物品栏就是越界崩 —— Create 的包裹过滤器
     * （同样没有过滤槽）也是这么挡的。
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    /**
     * 界面不往过滤器物品上写任何东西：名字属于网络（存 {@link ResonanceNetworkNames}），
     * 规则属于台面，物品自己身上只该有「接入的是哪张网络」这一个组件。
     * 留这个空实现挡掉基类那套 {@code create:filter_items} 的回写。
     */
    @Override
    protected void saveData(ItemStack contentHolder) {
    }

    // ==================== 给界面用的 ====================

    /** 当前网络的名字，没名字就是空串 */
    public String networkName() {
        return networkName == null ? "" : networkName;
    }

    /** 这件过滤器接入网络了没有 —— 没接入就没有名字可起，界面据此禁用输入框 */
    public boolean isLinked() {
        return network != null;
    }

    /**
     * 改名。界面每敲一下先在本端调一次（client 那份菜单的字段跟着变），
     * 同时把名字发到服务端由 {@link SetNetworkNamePayload} 落到这里真正落盘。
     * <p>
     * <b>网络 id 只从本端手里这件物品上取</b>（客户端发来的只有名字），空名字等于取消命名。
     */
    public void setNetworkName(String name) {
        String trimmed = name.length() > SetNetworkNamePayload.MAX_LENGTH
                ? name.substring(0, SetNetworkNamePayload.MAX_LENGTH)
                : name;
        networkName = trimmed;

        if (network == null || player.level().isClientSide)
            return;
        MinecraftServer server = player.level().getServer();
        if (server == null)
            return;
        ResonanceNetworkNames.get(server).setName(network, trimmed);

        // 台子上方的名字牌读的是各自缓存的那一份，改完名字得把在加载中的台子推一遍
        ResonanceTableBlockEntity.refreshNetworkNames(network, player.level());
    }
}
