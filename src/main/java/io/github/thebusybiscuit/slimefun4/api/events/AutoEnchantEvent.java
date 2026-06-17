package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.enchanting.AutoEnchanter;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * An {@link Event} that is called whenever an {@link AutoEnchanter} is trying to enchant
 * an {@link ItemStack}.
 *
 * @author WalshyDev
 *
 * @see AutoDisenchantEvent
 */
// AutoEnchanter（自动附魔机）尝试给物品附魔时触发的自定义事件喵
// 实现了Cancellable接口，允许其他插件通过setCancelled(true)阻止本次附魔行为喵
public class AutoEnchantEvent extends Event implements Cancellable {

    // 事件处理器列表，Bukkit事件系统要求的静态注册对象喵
    // 所有该类型事件共享同一个handlers实例喵
    private static final HandlerList handlers = new HandlerList();

    // 正在被附魔的物品栈，final保证构造后不可重新赋值喵
    private final ItemStack item;
    // 执行附魔操作的方块（自动附魔机本体），可能为null当构造时未传入block喵
    private Block block;
    // 事件取消标志位，true表示该附魔事件已被某插件取消喵
    private boolean cancelled;

    // 构造函数：仅指定被附魔物品（向后兼容旧版调用方）喵
    // @param item 要被附魔的物品栈，不可为null喵
    public AutoEnchantEvent(@Nonnull ItemStack item) {
        super(true); // 调用父类Event构造，true表示此事件在异步环境下也可安全触发喵

        this.item = item; // 保存被附魔物品的引用喵
    }

    // 构造函数：同时指定被附魔物品和附魔机方块位置喵
    // @param item 要被附魔的物品栈，不可为null喵
    // @param block 附魔机所在的方块，可为null表示未知附魔机位置喵
    public AutoEnchantEvent(@Nonnull ItemStack item, @Nullable Block block) {
        super(true); // 调用父类Event构造，true表示此事件在异步环境下也可安全触发喵

        this.item = item; // 保存被附魔物品的引用喵
        this.block = block; // 保存附魔机方块引用，调用方可能传入null表示位置未知喵
    }

    /**
     * This returns the {@link ItemStack} that is being enchanted.
     *
     * @return The {@link ItemStack} that is being enchanted
     */
    @Nonnull
    public ItemStack getItem() {
        return item; // 返回正在被附魔的物品栈，返回值永远不会为null喵
    }

    /**
     * This returns the {@link Block} that is enchanting items
     *
     * @return The {@link Block} that is enchanting items
     */
    @Nullable public Block getBlock() {
        return block; // 返回执行附魔操作的方块，可能返回null喵
    }

    @Override
    public boolean isCancelled() {
        return cancelled; // 返回当前事件是否已被取消（true=已阻止附魔）喵
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel; // 设置事件取消状态，其他插件监听此事件时可调用该方法阻止附魔执行喵
    }

    // Bukkit事件系统的静态访问方法，用于获取该事件类型的处理器列表喵
    // 返回值不允许为null喵
    @Nonnull
    public static HandlerList getHandlerList() {
        return handlers; // 返回静态共享的事件处理器列表喵
    }

    // Bukkit事件系统的实例访问方法，父类Event通过此方法获取处理器列表来分发事件喵
    // 返回值不允许为null喵
    @Nonnull
    @Override
    public HandlerList getHandlers() {
        return getHandlerList(); // 委托给静态方法getHandlerList()返回处理器列表喵
    }
}
