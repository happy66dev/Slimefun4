package io.github.thebusybiscuit.slimefun4.api.items.virtual;

import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

/**
 * 虚拟/代理物品的处理器接口，用于那些行为无法仅靠 ItemStack 的可见 Material 完整表达的物品喵~
 * 例如：粘液科技自定义物品可能外观相同但功能不同，需要通过此接口区分和处理喵~
 *
 * <p>实现类应保证热路径方法（高频调用方法）尽量廉价，只检查物品创建时预计算好的紧凑元数据喵~
 *
 * <p>通过 {@link io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem#addItemHandler(ItemHandler...)}
 * 将此处理器注册到对应的 SlimefunItem 上喵~
 */
public interface VirtualItemHandler extends ItemHandler {

    // 重写父接口方法，返回当前接口类型作为唯一标识符，用于处理器注册和查找喵~
    @Override
    @Nonnull
    default Class<? extends ItemHandler> getIdentifier() {
        // 返回 VirtualItemHandler 类本身作为标识符，确保同类型处理器不会重复注册喵~
        return VirtualItemHandler.class;
    }

    /**
     * 两个物品堆叠进行比较时所处的上下文场景枚举喵~
     * 不同场景下的比较逻辑可能不同，例如合并堆叠和配方输入的判定标准有所区别喵~
     */
    enum MatchContext {
        GENERIC, // 通用比较场景，无特殊业务含义喵~
        STACK_MERGE, // 物品堆叠合并时的比较场景喵~
        RECIPE_INPUT, // 配方输入槽位匹配时的比较场景喵~
        AUTO_CRAFTER_PREDICATE // 自动合成器使用谓词判断时的比较场景喵~
    }

    /**
     * 物品堆叠被插入背包/容器时所处的上下文场景枚举喵~
     * 不同容器类型对物品的接受规则可能不同喵~
     */
    enum InventoryContext {
        MENU_FIT, // 检查物品是否能放入菜单(BlockMenu)槽位喵~
        MENU_INSERT, // 将物品实际插入菜单槽位时喵~
        CARGO_INSERT, // 货运网络向容器插入物品时喵~
        VANILLA_FIT, // 原版背包/容器的容纳检查喵~
        MACHINE_OUTPUT, // 机器输出槽位放置物品时喵~
        OUTPUT_CHEST // 输出箱子接收物品时喵~
    }

    /**
     * 物品堆叠被消耗时所处的上下文场景枚举喵~
     * 菜单消耗和虚拟合成的消耗处理逻辑可能不同喵~
     */
    enum ConsumeContext {
        MENU_CONSUME, // 在菜单中消耗物品时的场景喵~
        VIRTUAL_CRAFTING // 虚拟合成过程中消耗物品时的场景喵~
    }

    /**
     * 合成剩余物（如水桶合成后留下空桶）被解析时所处的上下文枚举喵~
     */
    enum RemainderContext {
        AUTO_CRAFTER, // 自动合成器处理合成剩余物时的场景喵~
        VIRTUAL_CRAFTING // 虚拟合成处理合成剩余物时的场景喵~
    }

    /**
     * 三态比较结果枚举，用于表示处理器对某次比较的处理状态喵~
     * 三态设计允许处理器声明"我不管这个"，让其他处理器或默认逻辑接手喵~
     */
    enum ComparisonResult {
        NOT_HANDLED, // 此处理器不处理该比较，交由其他逻辑判断喵~
        MATCH, // 两个物品匹配喵~
        NO_MATCH // 两个物品不匹配喵~
    }

    /**
     * 三态准入结果枚举，用于表示处理器对物品进入某槽位的许可状态喵~
     * 三态设计同样允许处理器声明"我不干涉此次插入"喵~
     */
    enum AdmissionResult {
        NOT_HANDLED, // 此处理器不干涉该插入操作，交由其他逻辑决定喵~
        ALLOW, // 允许物品进入该槽位喵~
        DENY // 拒绝物品进入该槽位喵~
    }

    /**
     * 封装"替换物品堆叠"类操作的返回值 record，包含是否被处理以及替换后的物品喵~
     * 使用 record 类型自动生成构造器、getter 和 equals/hashCode 方法喵~
     *
     * @param handled 该处理器是否处理了本次请求，false 表示交由其他逻辑处理喵~
     * @param item    替换后的物品堆叠，为 null 时表示清空该槽位喵~
     */
    record ItemResult(boolean handled, @Nullable ItemStack item) {

        // 预先创建"未处理"的单例实例，避免每次调用 notHandled() 都重新分配对象，节省内存喵~
        private static final ItemResult NOT_HANDLED = new ItemResult(false, null);

        // 返回预创建的"未处理"单例，表示此处理器不处理本次请求喵~
        public static @Nonnull ItemResult notHandled() {
            return NOT_HANDLED;
        }

        // 创建并返回一个"已处理"结果，携带替换后的物品（可为 null 表示清空槽位）喵~
        public static @Nonnull ItemResult handled(@Nullable ItemStack item) {
            return new ItemResult(true, item);
        }
    }

    /**
     * 快速判断给定物品堆叠是否属于本虚拟物品系统管辖的物品喵~
     * 此方法会被高频调用（热路径），实现时必须保持极低的计算开销喵~
     *
     * 整体思路：只做最简单的标记检查（如 NBT tag 或名称前缀），不做复杂逻辑喵~
     * 输入：待检查的物品堆叠（可为 null）喵~
     * 输出：true 表示属于本系统，false 表示不属于喵~
     * 边界条件：item 为 null 时应返回 false，实现类需自行处理空值喵~
     *
     * @param item 待检查的物品堆叠
     * @return 是否应由本处理器接管该物品的相关操作
     */
    default boolean isVirtualItem(@Nullable ItemStack item) {
        // 喵~防御：默认实现直接返回 false，表示基础接口不认领任何物品，子类按需重写喵~
        return false;
    }

    /**
     * 在指定上下文中比较两个物品堆叠是否相同喵~
     * 例如：合并堆叠时需要判断两个堆叠是否属于同一种虚拟物品喵~
     *
     * 整体思路：根据 context 场景选择合适的比较策略，返回三态结果喵~
     * 输入：左侧堆叠 left、右侧堆叠 right（均可为 null）、比较上下文 context喵~
     * 输出：ComparisonResult 三态结果喵~
     * 边界条件：left 或 right 为 null 时，实现类应返回 NO_MATCH 或 NOT_HANDLED喵~
     *
     * @param left    左侧物品堆叠
     * @param right   右侧物品堆叠
     * @param context 比较发生时的业务场景
     * @return 比较结果
     */
    default @Nonnull ComparisonResult matches(
            @Nullable ItemStack left, @Nullable ItemStack right, @Nonnull MatchContext context) {
        // 默认实现不处理任何比较，交由框架或其他处理器处理喵~
        return ComparisonResult.NOT_HANDLED;
    }

    /**
     * 将物品堆叠与配方谓词进行匹配，判断该物品是否满足配方输入条件喵~
     * 用于自动合成器等需要按条件匹配物品而非精确匹配的场景喵~
     *
     * 整体思路：将 item 传入 predicate 进行测试，同时考虑 context 决定是否拦截喵~
     * 输入：待测物品 item（非 null）、配方谓词 predicate、上下文 context喵~
     * 输出：ComparisonResult 三态结果喵~
     *
     * @param item      待测物品堆叠
     * @param predicate 配方定义的匹配谓词
     * @param context   比较发生时的业务场景
     * @return 比较结果
     */
    default @Nonnull ComparisonResult matchesPredicate(
            @Nonnull ItemStack item, @Nonnull Predicate<ItemStack> predicate, @Nonnull MatchContext context) {
        // 默认实现不处理谓词匹配，交由框架或其他处理器处理喵~
        return ComparisonResult.NOT_HANDLED;
    }

    /**
     * 解析给定物品堆叠在特定插入上下文中的有效最大堆叠数量喵~
     * 虚拟物品可能有自定义的最大堆叠数（如某些物品最多叠 1 个），通过此方法覆盖默认值喵~
     *
     * 整体思路：检查 item 是否有自定义堆叠上限，有则返回自定义值，无则返回 defaultMaxStackSize喵~
     * 输入：待检查物品 item、插入上下文 context、原始最大堆叠数 defaultMaxStackSize喵~
     * 输出：实际生效的最大堆叠数量喵~
     *
     * @param item                待检查物品堆叠
     * @param context             插入发生时的业务场景
     * @param defaultMaxStackSize 原始/默认的最大堆叠数量
     * @return 该虚拟物品在此上下文中实际允许的最大堆叠数量
     */
    default int getMaxStackSize(@Nonnull ItemStack item, @Nonnull InventoryContext context, int defaultMaxStackSize) {
        // 默认实现直接返回原始最大堆叠数，表示不修改堆叠上限喵~
        return defaultMaxStackSize;
    }

    /**
     * 决定一个虚拟物品堆叠是否允许进入某个空槽位喵~
     * 例如：某些特殊物品只能放入特定机器，此方法可在货运或菜单插入时拦截不合法操作喵~
     *
     * 整体思路：检查 item 在 context 场景下是否满足准入条件，返回三态结果喵~
     * 输入：待插入物品 item（非 null）、插入上下文 context喵~
     * 输出：AdmissionResult 三态结果喵~
     *
     * @param item    待插入的物品堆叠
     * @param context 插入发生时的业务场景
     * @return 准入结果
     */
    default @Nonnull AdmissionResult allows(@Nonnull ItemStack item, @Nonnull InventoryContext context) {
        // 默认实现不干涉任何插入，交由框架或其他处理器决定喵~
        return AdmissionResult.NOT_HANDLED;
    }

    /**
     * 从物品堆叠中消耗指定数量，并返回消耗后的替换物品（如空桶替换满桶）喵~
     * 此方法处理物品被"用掉"的场景，例如配方合成消耗材料喵~
     *
     * 整体思路：
     *   1. 检查 item 是否属于本虚拟物品系统喵~
     *   2. 从 item 中减去 amount 数量喵~
     *   3. 若 replaceConsumables 为 true，将消耗后的物品替换为对应的容器物品（如空桶）喵~
     *   4. 根据 context 决定具体的消耗规则喵~
     * 输入：原始物品堆叠 item（非 null）、消耗数量 amount、是否替换消耗品 replaceConsumables、上下文 context喵~
     * 输出：ItemResult，包含消耗后的替换物品（可为 null 表示全部消耗）喵~
     * 边界条件：amount 小于等于 0 时，实现类应按最安全的方式处理喵~
     *
     * @param item               原始物品堆叠
     * @param amount             需要消耗的数量
     * @param replaceConsumables 消耗后是否应将消耗品替换为对应空容器
     * @param context            消耗发生时的业务场景
     * @return 消耗操作的替换结果
     */
    default @Nonnull ItemResult consume(
            @Nonnull ItemStack item, int amount, boolean replaceConsumables, @Nonnull ConsumeContext context) {
        // 默认实现不处理任何消耗操作，返回未处理结果让框架走默认逻辑喵~
        return ItemResult.notHandled();
    }

    /**
     * 解析物品被合成消耗后留下的剩余物（如合成时水桶留下空桶）喵~
     * 此方法在合成完成后被调用，用于确定消耗物品后应归还给玩家的剩余物喵~
     *
     * 整体思路：
     *   1. 检查 item 是否属于本虚拟物品系统喵~
     *   2. 根据 item 的类型和 context 场景，确定合成后应留下的剩余物喵~
     *   3. 返回封装了剩余物的 ItemResult喵~
     * 输入：已被消耗的物品堆叠 item（非 null）、合成上下文 context喵~
     * 输出：ItemResult，包含应归还玩家的剩余物（可为 null 表示无剩余）喵~
     *
     * @param item    已被消耗的物品堆叠
     * @param context 合成发生时的业务场景
     * @return 剩余物解析结果
     */
    default @Nonnull ItemResult getRemainder(@Nonnull ItemStack item, @Nonnull RemainderContext context) {
        // 默认实现不处理剩余物解析，返回未处理结果让框架走默认逻辑喵~
        return ItemResult.notHandled();
    }
}
