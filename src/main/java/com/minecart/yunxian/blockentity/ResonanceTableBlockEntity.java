package com.minecart.yunxian.blockentity;

import com.minecart.yunxian.block.ResonanceTableBlock;
import com.minecart.yunxian.item.ResonanceFilterItem;
import com.minecart.yunxian.mixin.DepotBehaviourAccessor;
import com.minecart.yunxian.registry.ModBlockEntities;
import com.minecart.yunxian.registry.ModDataComponents;
import com.minecart.yunxian.resonance.ResonanceParadox;
import com.simibubi.create.content.logistics.depot.DepotBehaviour;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.simibubi.create.content.logistics.item.filter.attribute.ItemAttribute;
import com.simibubi.create.content.logistics.packagerLink.LogisticallyLinkedBehaviour;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.utility.CreateLang;
import net.createmod.catnip.data.Pair;
import net.createmod.catnip.math.VecHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 共振台的方块实体。
 * <p>
 * <b>组合</b>一个 Create 的 {@link DepotBehaviour}，而不是去子类化 {@code DepotBlockEntity}：
 * 官方自己就是这么复用的（{@code StationBlockEntity} 和 {@code EjectorBlockEntity} 都组合它），
 * 而且 {@code DepotBlockEntity.depotBehaviour} 字段是<b>包私有</b>的，跨包的子类根本拿不到它。
 * <p>
 * 组合之后，「物品放上台面 / 从台面取走」的整条链路全部免费：右键交互走
 * {@code SharedDepotBlockMethods}，机械臂与传送带走 {@code addSubBehaviours} 里注册的
 * {@code DirectBeltInputBehaviour}，外部容器走 {@code depotBehaviour.itemHandler}（能力注册见
 * {@code ModCapabilities}），台面上物品的位移动画由 {@code DepotRenderer.renderItemsOf} 负责。
 * <p>
 * <b>网络那头直接用 Create 的 {@link LogisticallyLinkedBehaviour}</b>（也就是库存链接/打包机
 * 用的那一套）：它本身就是「UUID → 一组方块实体」的全局注册表，还带好了一套生命周期
 * （区块卸载/方块被拆会自动从表里掉出去、用弱引用、靠 lazyTick 续期）。关键是把
 * {@code global} 传 <b>false</b>：这样它完全不去碰 {@code Create.LOGISTICS}（见它的
 * {@code initialize}/{@code unload}/{@code destroy} 里那些 {@code if (... && global)} 分支），
 * 只剩我们要的「按 id 找同伙」这一件事，也不会有库存链接那套权限/红石语义。
 * <p>
 * 每个共振台<b>刚放下时</b>会在构造器里拿到一个随机的新 id，也就是"每放一个台子就是一个新网络"；
 * 要并进别的网络，用手持的共振过滤器右键它（见 {@code ResonanceFilterItem#useOn}）。
 */
public class ResonanceTableBlockEntity extends SmartBlockEntity {

    public DepotBehaviour depotBehaviour;
    public LogisticallyLinkedBehaviour linkBehaviour;
    public ScrollValueBehaviour maxStackSize;

    /** 上一次看到的充能状态。只在它翻转的那一刻动手，见 {@link #tick()} */
    private boolean powered;
    /** 充能瞬间冻结下来的那一份台面物品；没充能时是空的 */
    private ItemStack lockedFilter = ItemStack.EMPTY;

    public ResonanceTableBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESONANCE_TABLE.get(), pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        behaviours.add(depotBehaviour = new DepotBehaviour(this));

        // 四个侧面的滑块，控制这张台子最多堆多少个物品。整条链路都是 Create 现成的：
        // 值框由 ScrollValueRenderer 每 tick 遍历"玩家正看着的方块实体"自动画出来，
        // 交互由 ValueSettingsInputHandler 的 RightClickBlock 监听器接，都不用我们自己挂钩子；
        // 我们只要给出行为 + 一个 Sided 的槽位变换（数值本身由 ScrollValueBehaviour 自己存 NBT）。
        // 写法和加权弹射器的 maxStackSize 逐项一致（含 0 = "*" 即不限）。
        maxStackSize = new ScrollValueBehaviour(
                CreateLang.translateDirect("create_crystal_industry.resonance_table.stack_size"),
                this, new ResonanceTableSlot())
                .between(0, 64)
                .withFormatter(i -> i == 0 ? "*" : String.valueOf(i));
        behaviours.add(maxStackSize);

        // maxStackSize 字段是包私有的，跨包只能走访问器，见 DepotBehaviourAccessor。
        //
        // 两点必须和加权弹射器对齐，否则台子根本攒不住东西：
        // 1. 把 0（即滑块上的 "*"）折成 64 再交出去。DepotBehaviour 自己内部就把 0 当 64 算
        //    （getRemainingSpace 里那句 `maxStackSize.get() == 0 ? 64 : ...`），但
        //    DepotItemHandler.getSlotLimit 会把这个值【原样】当成槽位上限返回 —— 而
        //    ScrollValueBehaviour 的初值就是 0，于是刚放下的台子槽位上限是 0，
        //    漏斗走 ItemHandlerHelper.insertItemStacked 时一件都插不进来。
        // 2. enableMerging()：不开的话 canMergeItems() 恒为 false，insertItem / isOccupied
        //    只要台子上已经有东西就一律拒绝，台子永远只装得下第一次那一下 ——
        //    滑块也就完全不起作用了。弹射器是靠这个开关才能攒到滑块设定值的。
        ((DepotBehaviourAccessor) (Object) depotBehaviour).setMaxStackSize(
                () -> maxStackSize.getValue() == 0 ? 64 : maxStackSize.getValue());
        depotBehaviour.enableMerging();

        depotBehaviour.addSubBehaviours(behaviours);
        // global=false：不注册进 Create 的全局物流管理器，纯粹当作"按 id 找同伙"的注册表用
        behaviours.add(linkBehaviour = new LogisticallyLinkedBehaviour(this, false));
    }

    /**
     * 四个侧面的值框位置。
     * <p>
     * 只放开水平四个方向：台子顶上就是放物品的地方，把值框也画到顶面会挡着。
     */
    private static class ResonanceTableSlot extends ValueBoxTransform.Sided {

        @Override
        protected Vec3 getSouthLocation() {
            // 面中央、贴着侧面外沿。y 就是 6 —— 和 Create 加权弹射器的槽位同一个高度
            return VecHelper.voxelSpace(8, 6, 15.5);
        }

        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            return direction.getAxis().isHorizontal();
        }
    }

    /**
     * 红石充能时冻结当前过滤。
     * <p>
     * 只认「翻转的那一刻」：充能瞬间把台面物品<b>抄一份</b>存起来，之后不管台面上怎么变，
     * 过滤器读到的都是这一份；取消充能就丢掉快照、恢复实时。用抄本而不是记住"读哪个物品"，
     * 是因为台面上的东西可能被拿走甚至换掉，抄本才是真正意义上的"冻结"。
     * <p>
     * 用 tick 里比较缓存的标志位、而不是去挂钩子：方块那边换状态的路子有好几条
     * （玩家放、邻居更新、区块加载时读档），逐条挂容易漏；比较标志位这一条对所有路都成立。
     * 只在服务端跑 —— 客户端的 {@code lockedFilter} 由 NBT 同步过来，见 {@link #write}。
     */
    @Override
    public void tick() {
        super.tick();
        if (level == null || level.isClientSide)
            return;

        detonateSelfReference();

        boolean nowPowered = getBlockState().getValue(ResonanceTableBlock.POWERED);
        if (nowPowered == powered)
            return;

        powered = nowPowered;
        lockedFilter = nowPowered ? depotBehaviour.getHeldItemStack().copy() : ItemStack.EMPTY;
        setChanged();
        sendData();
    }

    /**
     * 彩蛋：台面上摆着一个<b>接回自己所在网络</b>的共振过滤器时，当场炸掉它。
     * <p>
     * 这就是那个无限自指 —— 台面上的过滤器去读本网络，本网络又读到它自己。
     * 判定那边会把它安静地判成"读不到"，这里给那份沉默一个说法：
     * 一场只有击退的爆炸（爆心在台子上方一格，见 {@link ResonanceParadox}）、
     * 一个隐藏成就，外加把这份过滤器从世上抹掉。
     * <p>
     * 每 tick 查一次，条件本身很便宜（一次 instanceof + 一次组件读）。炸完东西就没了，
     * 条件不再成立，所以不会连着炸 —— 除非有人源源不断地往台面上送，那种情况每次都该炸。
     */
    private void detonateSelfReference() {
        if (!ResonanceParadox.matches(getFilterSource(), getNetwork()))
            return;

        // 先抹掉再炸。留着的话下一 tick 读到的是同一份自指，会反复炸。
        // 充能冻结下来的那一份也一起清：那才是 getFilterSource() 在充能时真正读的东西。
        depotBehaviour.removeHeldItem();
        lockedFilter = ItemStack.EMPTY;
        setChanged();
        sendData();

        if (level instanceof ServerLevel serverLevel)
            ResonanceParadox.detonate(serverLevel, getBlockPos());
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Powered", powered);
        tag.put("LockedFilter", lockedFilter.saveOptional(registries));
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        powered = tag.getBoolean("Powered");
        lockedFilter = ItemStack.parseOptional(registries, tag.getCompound("LockedFilter"));
    }

    /**
     * 共振过滤器要读的那一样东西：台面当前放着的物品；台子被红石充能时改读冻结下来的那一份。
     * <p>
     * 只取主物品（{@code heldItem}），不含 {@code processingOutputBuffer} 那 8 格加工缓冲 ——
     * 那是置物台给「压印机在上方加工」用的，和过滤语义无关。
     * <p>
     * 充能时快照若为空（台子空着就被充能），返回空 —— 而空台面在过滤器那边的语义是
     * <b>不加限制</b>，所以那样等于把这一格冻结成"什么都放行"。
     */
    public ItemStack getFilterSource() {
        if (getBlockState().getValue(ResonanceTableBlock.POWERED))
            return lockedFilter;
        return depotBehaviour.getHeldItemStack();
    }

    /** 本台所在网络的 id。新放下的台子各有一个随机 id，也就是默认自成一个网络。 */
    public UUID getNetwork() {
        return linkBehaviour.freqId;
    }

    /**
     * 本网络在过滤些什么 —— 给显示链接器用。
     * <p>
     * 台面上放着什么就展开成什么：
     * <ul>
     *   <li>列表过滤器 → 它 18 格里装的物品（里面还套着过滤器就继续展开）；</li>
     *   <li>属性过滤器 → 它选中的属性（取反的用 Create 自带的那条 {@code .inverted} 文案）；</li>
     *   <li>普通物品 → 就是它自己。</li>
     * </ul>
     * 展开不出东西时（空列表过滤器、没配过的属性过滤器）就退回显示它本身，免得这一项从列表里凭空消失。
     * <p>
     * <b>结果按文案排序去重</b> —— 顺序必须是确定的，否则客户端和服务端各算各的、显示板上的行会来回跳。
     * <p>
     * 台面上放着的是<b>另一个网络的共振过滤器</b>时，跟着走进那个网络，列出来的就是那个网络在过滤的东西
     * —— 和判定那边一致（见 {@code ResonanceFilterItemStack}）。台面空着、台上的共振过滤器接了环或没接网络的，
     * 都不算进列表。
     */
    public List<Component> getNetworkFilterText() {
        Level level = getLevel();
        if (level == null)
            return List.of();

        Map<String, Component> texts = new TreeMap<>();
        collectNetworkText(getNetwork(), level.isClientSide, texts, new HashSet<>(), 0);
        return List.copyOf(texts.values());
    }

    /**
     * 把一个网络里所有台面上的东西展开进 {@code texts}；台面上是别的网络的共振过滤器时递归走进去。
     * <p>
     * {@code visited} 记的是<b>这条链上</b>走过的网络（离开时摘掉），用来挡互相指向的环：
     * A 的网络里某张台子放着绑到 B 的共振过滤器、B 里又有一张放着绑回 A 的，不放环护栏就会无限递归。
     * 判定那边（{@code ResonanceFilterItemStack.RESOLVING_NETWORKS}）是同一套思路，
     * 只不过那边按线程记账、这边按这一次调用记账。
     * <p>
     * {@code depth} 顺手给链条收个上界；网络数不会真的无限多，它只是个兜底。
     */
    private static void collectNetworkText(UUID network, boolean clientSide, Map<String, Component> texts,
                                           Set<UUID> visited, int depth) {
        if (depth > MAX_EXPAND_DEPTH || !visited.add(network))
            return;
        try {
            // 双端各读各的那张注册表，和滤波器那边一样
            for (LogisticallyLinkedBehaviour link : LogisticallyLinkedBehaviour.getAllPresent(
                    network, false, clientSide)) {

                if (!link.freqId.equals(network))
                    continue;
                if (!(link.blockEntity instanceof ResonanceTableBlockEntity other))
                    continue;

                ItemStack onTable = other.getFilterSource();
                if (onTable.isEmpty())
                    continue;

                // 台面上是共振过滤器：绑了别的网络就跟着走进去；绑的是这条链上的网络（环）
                // 或者根本没绑网络的，都展开不出东西，跳过。
                if (onTable.getItem() instanceof ResonanceFilterItem) {
                    UUID next = onTable.get(ModDataComponents.RESONANCE_NETWORK.get());
                    if (next != null)
                        collectNetworkText(next, clientSide, texts, visited, depth + 1);
                    continue;
                }

                collectFilterText(onTable, texts, 0);
            }
        } finally {
            visited.remove(network);
        }
    }

    /** 展开深度上限：列表过滤器可以套列表过滤器，而不设限就是个自引用陷阱 */
    private static final int MAX_EXPAND_DEPTH = 4;

    /**
     * 黑名单条目的叉号，以及它在去重表里的键前缀。
     * <p>
     * <b>用的是大写字母 X，不是「✗」。</b> 显示链接器的主要去向是显示板（翻牌显示器），
     * 而它的字符集是 lang 里写死的 {@code create.flap_display.cycles.alphabet} ——
     * 只有大写 A-Z 和空格（{@code FlapDisplaySection} 会先把文字 toUpperCase 再按这个集合出字），
     * 集合外的字符一律显示成空白。用「✗」的话在板上是一片空，叉号反而看不见。
     * <p>
     * 顺带：去重键也带上了前缀，所以白名单里的"铁锭"和黑名单里的"X 铁锭"是两条、不会互相顶掉。
     */
    private static final String DENY_MARKER = "X ";
    private static final String DENY_KEY_PREFIX = "deny:";

    private static void collectFilterText(ItemStack filterStack, Map<String, Component> out, int depth) {
        if (depth > MAX_EXPAND_DEPTH) {
            putItemName(out, filterStack);
            return;
        }

        // 必须传副本：FilterItemStack.of 对 FilterItem 会调 trimFilterComponents，
        // 那个方法会 remove 掉附魔与属性修饰符 —— 直接传等于把台面上那份物品改掉。
        FilterItemStack filter = FilterItemStack.of(filterStack.copy());

        if (filter instanceof FilterItemStack.ListFilterItemStack list) {
            if (list.containedItems.isEmpty()) {
                putItemName(out, filterStack);   // 空列表过滤器：展开不出东西，显示它本身
                return;
            }

            if (!list.isBlacklist) {
                for (FilterItemStack contained : list.containedItems)
                    collectFilterText(contained.item(), out, depth + 1);
                return;
            }

            // 黑名单模式：里面的东西是"排除"的意思，逐条加个叉号再并进来 ——
            // 不然它和白名单在板上长得一模一样。
            // 先在临时表里展开：递归是直接往传进去的那张表里写的，事后没法逐条加前缀。
            Map<String, Component> denied = new TreeMap<>();
            for (FilterItemStack contained : list.containedItems)
                collectFilterText(contained.item(), denied, depth + 1);
            for (Map.Entry<String, Component> entry : denied.entrySet())
                out.putIfAbsent(DENY_KEY_PREFIX + entry.getKey(),
                        Component.literal(DENY_MARKER).append(entry.getValue()));
            return;
        }

        if (filter instanceof FilterItemStack.AttributeFilterItemStack attribute) {
            if (attribute.attributeTests.isEmpty()) {
                putItemName(out, filterStack);
                return;
            }
            // 这里不能用 ItemAttribute#format —— 它标了 @OnlyIn(CLIENT)，而 provideText 是
            // 服务端跑的（DisplayLinkBlockEntity#tickSource 里 `if (!level.isClientSide)`），
            // 专用服务端调到它会直接崩。所以照它的写法自己拼，用的都是双端都有的 getter。
            for (Pair<ItemAttribute, Boolean> test : attribute.attributeTests) {
                ItemAttribute attr = test.getFirst();
                boolean inverted = test.getSecond();
                // 去重键必须带上参数：属性是可以带参数的（比如"某个标签"），
                // 只看 translationKey 的话两个不同的标签会被当成同一条丢掉。
                out.putIfAbsent(
                        "attribute:" + attr.getTranslationKey() + inverted
                                + Arrays.toString(attr.getTranslationParameters()),
                        Component.translatable(
                                "create.item_attributes." + attr.getTranslationKey()
                                        + (inverted ? ".inverted" : ""),
                                attr.getTranslationParameters()));
            }
            return;
        }

        putItemName(out, filterStack);   // 普通物品（也含没配过的过滤器）
    }

    private static void putItemName(Map<String, Component> out, ItemStack stack) {
        Item item = stack.getItem();
        out.putIfAbsent("item:" + BuiltInRegistries.ITEM.getKey(item),
                new ItemStack(item).getHoverName());
    }

    /**
     * 把本台并进另一个网络。
     * <p>
     * 顺序很重要：必须<b>先</b>用旧 id 把自己从注册表里摘掉，再改 id。否则 {@code LINKS} 里那个
     * 旧 id 下的条目要等 400 tick 才自然过期，这段时间本台会<b>同时出现在两个网络里</b>，
     * 旧网络的过滤器会莫名其妙地继续读到本台的东西。
     * <p>
     * 摘掉之后再 {@code keepAlive} 一次，让它在<b>本 tick</b> 就挂到新 id 下（否则要等下一次
     * lazyTick，默认 10 tick 之后才生效）。
     */
    public void joinNetwork(UUID network) {
        if (linkBehaviour.freqId.equals(network))
            return;
        LogisticallyLinkedBehaviour.remove(linkBehaviour);
        linkBehaviour.freqId = network;
        LogisticallyLinkedBehaviour.keepAlive(linkBehaviour);
        setChanged();
        sendData();
    }
}
