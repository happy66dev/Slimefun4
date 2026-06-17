package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.Cooler;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.lang.Validate;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

/**
 * This {@link Event} is called whenever a {@link Player} is
 * fed through a {@link Cooler}.
 * 当玩家通过 Cooler（冷却背包）获得饮食补充时触发此事件喵~
 *
 * @author TheBusyBiscuit
 * @see Cooler
 */
public class CoolerFeedPlayerEvent extends PlayerEvent implements Cancellable {

    // Bukkit事件系统要求的静态处理器列表，用于管理所有监听此事件的监听器喵~
    private static final HandlerList handlers = new HandlerList();

    // 触发此事件的Cooler背包对象，记录是哪个冷却背包给玩家补充了食物喵~
    private final Cooler cooler;
    // 触发此事件的Cooler背包的ItemStack形式，可用于获取物品显示信息喵~
    private final ItemStack coolerItem;

    // 玩家实际消耗的物品（药水），可以被外部监听器修改以替换效果喵~
    private ItemStack consumedItem;
    // 标记此事件是否已被取消，取消后Cooler不会给玩家施加药水效果喵~
    private boolean cancelled;

    /*
     * 构造方法：创建一个CoolerFeedPlayerEvent事件实例喵~
     * 整体思路：当Cooler背包检测到玩家需要补充状态效果时，创建此事件并广播给所有监听器喵~
     * 输入：player=触发事件的玩家, cooler=使用的Cooler对象, coolerItem=Cooler的物品形式, consumedItem=被消耗的药水物品
     * 输出：构造完成的事件对象，可以被监听器取消或修改consumedItem喵~
     * 边界条件：@ParametersAreNonnullByDefault注解要求所有参数均不为null喵~
     */
    @ParametersAreNonnullByDefault
    public CoolerFeedPlayerEvent(Player player, Cooler cooler, ItemStack coolerItem, ItemStack consumedItem) {
        // 调用父类PlayerEvent构造方法，将玩家信息注册到事件中喵~
        super(player);

        // 保存触发事件的Cooler背包对象引用喵~
        this.cooler = cooler;
        // 保存Cooler背包对应的ItemStack物品引用喵~
        this.coolerItem = coolerItem;
        // 保存玩家即将消耗的药水物品引用，监听器可以将其替换为其他药水喵~
        this.consumedItem = consumedItem;
    }

    /**
     * This returns the {@link Cooler} that was used.
     * 返回触发此事件的Cooler背包对象喵~
     *
     * @return The {@link Cooler} that was used
     */
    @Nonnull
    public Cooler getCooler() {
        // 返回触发本次喂食事件的Cooler背包逻辑对象喵~
        return cooler;
    }

    /**
     * This returns the {@link Cooler} that was used (as an {@link ItemStack})
     * 以ItemStack形式返回触发此事件的Cooler背包物品喵~
     *
     * @return The {@link Cooler} that was used
     */
    @Nonnull
    public ItemStack getCoolerItem() {
        // 返回Cooler背包的ItemStack实体，可用于获取物品名称、lore等显示信息喵~
        return coolerItem;
    }

    /**
     * This returns the {@link ItemStack} that was consumed.
     * The returned {@link ItemStack} is immutable.
     * 返回被消耗的药水物品的副本，副本不可变以防外部意外修改原始数据喵~
     *
     * @return The {@link ItemStack} that was consumed
     */
    @Nonnull
    public ItemStack getConsumedItem() {
        // 返回consumedItem的克隆副本，防止外部代码直接修改内部状态喵~
        return consumedItem.clone();
    }

    /**
     * This sets the {@link ItemStack} that should be "consumed".
     * The {@link ItemStack} must be a potion.
     * The {@link Player} will receive the {@link PotionEffect PotionEffects} of the
     * provided potion upon consumption.
     * 替换本次事件中玩家将要消耗的药水物品，新物品必须是药水类型喵~
     *
     * @param item The new {@link ItemStack}
     */
    public void setConsumedItem(@Nonnull ItemStack item) {
        // 喵~防御：item为null时立即抛出异常，防止后续操作空指针崩溃喵~
        Validate.notNull(item, "The consumed Item cannot be null!");
        // 喵~防御：物品的ItemMeta必须是PotionMeta类型，确保消耗的一定是药水而非其他物品喵~
        Validate.isTrue(item.getItemMeta() instanceof PotionMeta, "The item must be a potion!");

        // 通过验证后，将消耗物品替换为监听器指定的新药水喵~
        this.consumedItem = item;
    }

    // 实现Cancellable接口：返回当前事件是否已被取消喵~
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    // 实现Cancellable接口：设置事件取消状态，取消后Cooler不会给玩家施加药水效果喵~
    @Override
    public void setCancelled(boolean cancel) {
        // 将传入的取消标志写入字段，供后续isCancelled()读取喵~
        this.cancelled = cancel;
    }

    // Bukkit事件系统要求的静态方法，返回全局唯一的HandlerList供事件总线注册和调度使用喵~
    @Nonnull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    // 实现Event抽象方法：返回此事件实例对应的HandlerList，Bukkit用它来找到所有监听器喵~
    @Nonnull
    @Override
    public HandlerList getHandlers() {
        return getHandlerList();
    }
}
