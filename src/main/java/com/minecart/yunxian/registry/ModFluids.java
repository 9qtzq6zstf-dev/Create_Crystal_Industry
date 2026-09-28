package com.minecart.yunxian.registry;

import com.minecart.yunxian.Yunxian;
import com.minecart.yunxian.fluid.CurrentSlurryFluid;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * 本模组的流体，目前只有一种：<b>电流浆</b>（弧光石经工作盆冲压得到）。
 * <p>
 * 按原版/NeoForge 的标准写法注册了<b>两份</b>：{@code current_slurry} 是源（静止）流体，
 * {@code flowing_current_slurry} 是流动变体，前者连同 {@link ModBlocks#CURRENT_SLURRY_BLOCK}
 * 与 {@link ModItems#CURRENT_SLURRY_BUCKET} 构成「桶能倒出来」所需的最小集合。
 * 储罐、管道、工作盆与配方里引用的都是<b>源</b>那一份
 * （Create 自己的巧克力/蜂蜜/种子油也是这个规矩）。
 * <p>
 * <b>名字来自默认规则</b>：NeoForge 的 {@code FluidType#getDescriptionId()} 是
 * {@code fluid_type.<命名空间>.<注册名>}，所以语言文件里的键是
 * {@code fluid_type.create_crystal_industry.current_slurry}，不需要在这里写死。
 * <p>
 * <b>注册顺序</b>：{@code minecraft:fluid} 早于 {@code minecraft:block}/{@code minecraft:item}
 * （见 {@code BuiltInRegistries} 的字段顺序），所以 {@code LiquidBlock} 与 {@code BucketItem}
 * 里可以安全地 {@code CURRENT_SLURRY.get()}；但 {@code neoforge:fluid_type} 反而<b>晚于</b>
 * {@code minecraft:fluid}，因此 {@link #CURRENT_SLURRY_PROPERTIES} 里持有的是类型的
 * <b>句柄</b>而不是取出来的实例——这一条踩过坑，别改。
 * <p>
 * 客户端的贴图与颜色不在这里注册：那条链只在客户端加载，见 {@code client/ModFluidExtensions}。
 */
public final class ModFluids {

    public static final DeferredRegister<FluidType> FLUID_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Yunxian.MODID);

    public static final DeferredRegister<Fluid> FLUIDS =
            DeferredRegister.create(Registries.FLUID, Yunxian.MODID);

    /** 密度与黏度都取得比水高（它读起来该是「浆」，不是水）；亮度 15，与原版岩浆同档 */
    public static final DeferredHolder<FluidType, FluidType> CURRENT_SLURRY_TYPE =
            FLUID_TYPES.register("current_slurry", () -> new FluidType(FluidType.Properties.create()
                    .density(2500)
                    .viscosity(4000)
                    .lightLevel(15)));

    /*
     * 两个 lambda 里必须写全限定名 ModFluids.CURRENT_SLURRY_PROPERTIES：静态初始化器按**简单名**
     * 引用后面才声明的字段是编译错误（illegal forward reference），加限定名就合法。真正读值的时机
     * 是注册事件（类初始化已完成），所以不会是 null。
     */

    /** 源（静止）流体：配方、储罐、管道、桶引用的都是它；液面火星见 {@code CurrentSlurryFluid} */
    public static final DeferredHolder<Fluid, CurrentSlurryFluid.Source> CURRENT_SLURRY =
            FLUIDS.register("current_slurry",
                    () -> new CurrentSlurryFluid.Source(ModFluids.CURRENT_SLURRY_PROPERTIES));

    /** 流动变体：只在世界里淌着的那份存在，玩家一般只会在桶/管道的贴图上见到它 */
    public static final DeferredHolder<Fluid, CurrentSlurryFluid.Flowing> FLOWING_CURRENT_SLURRY =
            FLUIDS.register("flowing_current_slurry",
                    () -> new CurrentSlurryFluid.Flowing(ModFluids.CURRENT_SLURRY_PROPERTIES));

    /**
     * 流体参数。写法参照原版岩浆：比水稠，铺得近、掉得快、淌得慢
     * （水是 4 / 1 / 5，岩浆是 2 / 2 / 30，这里取中间偏稠的一档）。
     * <p>
     * 声明位置必须在两个流体句柄之后——本对象要读它们的<b>值</b>；而
     * {@code .block(...)} / {@code .bucket(...)} 收的是 Supplier，方块与物品注册晚于流体也没关系。
     */
    private static final BaseFlowingFluid.Properties CURRENT_SLURRY_PROPERTIES =
            new BaseFlowingFluid.Properties(CURRENT_SLURRY_TYPE, CURRENT_SLURRY, FLOWING_CURRENT_SLURRY)
                    .block(ModBlocks.CURRENT_SLURRY_BLOCK)
                    .bucket(ModItems.CURRENT_SLURRY_BUCKET)
                    .slopeFindDistance(2)
                    .levelDecreasePerBlock(2)
                    .tickRate(20);

    private ModFluids() {
    }

    public static void register(IEventBus modEventBus) {
        FLUID_TYPES.register(modEventBus);
        FLUIDS.register(modEventBus);
    }
}
