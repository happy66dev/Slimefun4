package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.enchanting.AutoEnchanter;
import javax.annotation.Nonnull;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.apache.commons.lang.Validate;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * 每当{@link AutoEnchanter}正在对{@link ItemStack}进行附魔时触发的异步事件喵~
 * 这是一个可取消事件，取消后附魔操作将不会执行喵~
 *
 * @author StarWishsama
 */
public class AsyncAutoEnchanterProcessEvent extends Event implements Cancellable {

    // 事件处理器列表，用于Bukkit事件系统注册和分发喵~
    private static final HandlerList handlers = new HandlerList();

    // 当前正在进行附魔操作的物品喵~
    private final ItemStack item;
    // 用于给物品附魔的附魔书喵~
    private final ItemStack enchantedBook;
    // 自动附魔台对应的方块菜单界面喵~
    private final BlockMenu menu;

    // 事件是否被取消的标记位喵~
    private boolean cancelled;

    /**
     * 构造一个自动附魔台处理事件喵~
     *
     * @param item 正在被附魔的物品，不能为null喵~
     * @param enchantedBook 用于附魔的附魔书，不能为null喵~
     * @param menu 自动附魔台的方块菜单界面，不能为null喵~
     */
    public AsyncAutoEnchanterProcessEvent(
            @Nonnull ItemStack item, @Nonnull ItemStack enchantedBook, @Nonnull BlockMenu menu) {
        // 将事件标记为异步事件喵~
        super(true);

        // 喵~防御：确保被附魔的物品不为null，否则抛出异常阻止后续空指针崩溃喵~
        Validate.notNull(item, "The item to enchant cannot be null!");
        // 喵~防御：确保附魔书不为null，否则抛出异常阻止后续空指针崩溃喵~
        Validate.notNull(enchantedBook, "The enchanted book to enchant cannot be null!");
        // 喵~防御：确保方块菜单不为null，否则抛出异常阻止后续空指针崩溃喵~
        Validate.notNull(menu, "The menu of auto-enchanter cannot be null!");

        // 保存被附魔的物品引用喵~
        this.item = item;
        // 保存附魔书的引用喵~
        this.enchantedBook = enchantedBook;
        // 保存方块菜单的引用喵~
        this.menu = menu;
    }

    /**
     * 获取正在被附魔的{@link ItemStack}喵~
     *
     * @return 正在被附魔的物品喵~
     */
    @Nonnull
    public ItemStack getItem() {
        // 返回当前正在附魔的物品喵~
        return item;
    }

    /**
     * 获取用于附魔的附魔书{@link ItemStack}喵~
     *
     * @return 正在被使用的附魔书喵~
     */
    @Nonnull
    public ItemStack getEnchantedBook() {
        // 返回当前使用的附魔书喵~
        return enchantedBook;
    }

    /**
     * 获取{@link AutoEnchanter}对应的{@link BlockMenu}界面喵~
     *
     * @return 自动附魔台当前使用的方块菜单喵~
     */
    @Nonnull
    public BlockMenu getMenu() {
        // 返回自动附魔台的方块菜单界面喵~
        return menu;
    }

    /**
     * 获取该事件的全局处理器列表，供Bukkit事件系统调用喵~
     *
     * @return 事件处理器列表喵~
     */
    @Nonnull
    public static HandlerList getHandlerList() {
        // 返回静态处理器列表实例喵~
        return handlers;
    }

    /**
     * 获取当前事件实例关联的处理器列表喵~
     *
     * @return 事件处理器列表喵~
     */
    @Nonnull
    @Override
    public HandlerList getHandlers() {
        // 委托给静态方法获取处理器列表喵~
        return getHandlerList();
    }

    /**
     * 检查事件是否已被取消喵~
     *
     * @return 如果事件被取消返回true，否则返回false喵~
     */
    @Override
    public boolean isCancelled() {
        // 返回取消标记位的当前状态喵~
        return cancelled;
    }

    /**
     * 设置事件的取消状态喵~
     *
     * @param cancel true表示取消本次附魔事件，false表示恢复喵~
     */
    @Override
    public void setCancelled(boolean cancel) {
        // 更新取消标记位喵~
        this.cancelled = cancel;
    }
}
