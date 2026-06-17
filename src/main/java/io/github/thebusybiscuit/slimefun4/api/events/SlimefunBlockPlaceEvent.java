package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * This {@link Event} is fired whenever a {@link SlimefunItem} is placed as a {@link Block} in the world.
 *
 * @author J3fftw1
 */
// 当玩家在世界中放置任意Slimefun方块时触发的自定义事件类，实现Cancellable接口使监听器可以取消放置行为喵~
public class SlimefunBlockPlaceEvent extends Event implements Cancellable {

    // 静态事件处理器列表，Bukkit事件系统要求每个自定义事件都必须有此字段来注册和管理监听器喵~
    private static final HandlerList handlers = new HandlerList();

    // 玩家放置后在世界中生成的方块对象，携带方块的坐标位置和所在世界信息喵~
    private final Block blockPlaced;
    // 对应的Slimefun物品定义，包含该Slimefun物品的ID、名称及特殊行为逻辑喵~
    private final SlimefunItem slimefunItem;
    // 玩家手持并实际用来放置的ItemStack物品堆，包含物品数量、附魔、Lore等实例数据喵~
    private final ItemStack placedItem;
    // 触发本次放置操作的玩家对象，可用于查询玩家名、UUID、权限等信息喵~
    private final Player player;

    // 事件是否被取消的标志位，默认false表示放置正常进行，监听器可调用setCancelled(true)阻止放置喵~
    private boolean cancelled = false;

    /**
     * @param player
     *        The {@link Player} who placed this {@link SlimefunItem}
     * @param placedItem
     *        The {@link ItemStack} held by the {@link Player}
     * @param blockPlaced
     *        The {@link Block} placed by the {@link Player}
     * @param slimefunItem
     *        The {@link SlimefunItem} within the {@link ItemStack}
     */
    /*
     * 构造方法整体思路：
     * Slimefun框架检测到玩家放置Slimefun方块后，调用此构造方法创建事件对象并通过Bukkit事件总线广播。
     * 输入：放置方块的玩家、手持的ItemStack、世界中生成的方块对象、对应的SlimefunItem定义。
     * 输出：初始化完毕的SlimefunBlockPlaceEvent实例，供订阅该事件的监听器消费处理。
     * 边界条件：@ParametersAreNonnullByDefault注解声明所有参数均不可为null，调用方必须保证传入非null值喵~
     */
    @ParametersAreNonnullByDefault
    public SlimefunBlockPlaceEvent(Player player, ItemStack placedItem, Block blockPlaced, SlimefunItem slimefunItem) {
        // 调用父类Event的构造方法，初始化Bukkit事件的基础数据结构喵~
        super();

        // 保存玩家对象到实例字段，后续由getPlayer()方法对外提供访问喵~
        this.player = player;
        // 保存手持物品ItemStack到实例字段，后续由getItemStack()方法对外提供访问喵~
        this.placedItem = placedItem;
        // 保存被放置的方块对象到实例字段，后续由getBlockPlaced()方法对外提供访问喵~
        this.blockPlaced = blockPlaced;
        // 保存Slimefun物品定义到实例字段，后续由getSlimefunItem()方法对外提供访问喵~
        this.slimefunItem = slimefunItem;
    }

    /**
     * This gets the placed {@link Block}
     *
     * @return The placed {@link Block}
     */
    // 返回玩家放置的方块对象，监听器可通过此方法获取方块坐标、类型及所在世界等位置信息喵~
    public @Nonnull Block getBlockPlaced() {
        return blockPlaced;
    }

    /**
     * This gets the {@link SlimefunItem} being placed
     *
     * @return The {@link SlimefunItem} being placed
     */
    // 返回正在被放置的SlimefunItem定义对象，可用于判断物品类型、获取Slimefun物品ID及特有属性喵~
    public @Nonnull SlimefunItem getSlimefunItem() {
        return slimefunItem;
    }

    /**
     * This gets the placed {@link ItemStack}.
     *
     * @return The placed {@link ItemStack}
     */
    // 返回玩家手持的ItemStack物品堆实例，包含物品的数量、附魔、自定义名称等详细数据喵~
    public @Nonnull ItemStack getItemStack() {
        return placedItem;
    }

    /**
     * This gets the {@link Player}
     *
     * @return The {@link Player}
     */
    // 返回触发本次放置操作的玩家对象，可用于获取玩家信息或进行权限检查喵~
    public @Nonnull Player getPlayer() {
        return player;
    }

    // 实现Cancellable接口：返回当前事件是否已被取消，true表示放置操作将被阻止喵~
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    // 实现Cancellable接口：允许监听器传入true来取消本次方块放置，Bukkit框架据此决定是否执行实际放置逻辑喵~
    @Override
    public void setCancelled(boolean cancelled) {
        // 将外部传入的取消状态写入标志位，供isCancelled()读取喵~
        this.cancelled = cancelled;
    }

    // 静态方法返回全局事件处理器列表，Bukkit在注册监听器和分发事件时必须调用此方法喵~
    public static @Nonnull HandlerList getHandlerList() {
        return handlers;
    }

    // 实现Event父类的抽象方法，返回当前事件实例的处理器列表，Bukkit通过此方法将事件分发给所有注册的监听器喵~
    @Override
    public @Nonnull HandlerList getHandlers() {
        return getHandlerList();
    }
}
