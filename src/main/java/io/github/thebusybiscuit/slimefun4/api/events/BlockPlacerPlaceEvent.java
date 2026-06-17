package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.BlockPlacer;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.lang.Validate;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 每当{@link BlockPlacer}想放置{@link Block}时触发这个{@link Event}喵~
 *
 * @author TheBusyBiscuit
 *
 */
public class BlockPlacerPlaceEvent extends BlockEvent implements Cancellable {

    // 静态HandlerList用于Bukkit事件系统注册监听器喵~
    private static final HandlerList handlers = new HandlerList();

    // 触发本次放置事件的方块放置器方块喵~
    private final Block blockPlacer;
    // 被放置方块对应的物品堆喵~
    private ItemStack placedItem;

    // 标记事件是否被取消喵~
    private boolean cancelled = false;
    // 标记事件是否已锁定（锁定后不可再修改取消状态和物品）喵~
    private boolean locked = false;

    /**
     * 构造一个新的{@link BlockPlacerPlaceEvent}喵~
     *
     * @param blockPlacer
     *            触发放置的{@link BlockPlacer}
     * @param placedItem
     *            被放置的{@link Block}对应的{@link ItemStack}
     * @param block
     *            被放置的{@link Block}
     */
    @ParametersAreNonnullByDefault
    public BlockPlacerPlaceEvent(Block blockPlacer, ItemStack placedItem, Block block) {
        // 调用父类BlockEvent构造器，传入被放置的方块喵~
        super(block);

        // 保存被放置的物品堆引用喵~
        this.placedItem = placedItem;
        // 保存方块放置器引用喵~
        this.blockPlacer = blockPlacer;
    }

    /**
     * 获取触发本次放置事件的{@link BlockPlacer}喵~
     *
     * @return 触发放置的{@link BlockPlacer}
     */
    @Nonnull
    public Block getBlockPlacer() {
        // 返回方块放置器引用喵~
        return blockPlacer;
    }

    /**
     * 获取本次被放置的{@link ItemStack}喵~
     *
     * @return 被放置的{@link ItemStack}
     */
    @Nonnull
    public ItemStack getItemStack() {
        // 返回被放置的物品堆喵~
        return placedItem;
    }

    /**
     * 设置本次要放置的{@link ItemStack}喵~
     *
     * @param item
     *            要放置的{@link ItemStack}
     */
    public void setItemStack(@Nonnull ItemStack item) {
        // 喵~防御：校验传入的ItemStack不为null，避免后续操作空指针崩溃喵~
        Validate.notNull(item, "The ItemStack must not be null!");

        // 喵~防御：事件被锁定后不允许修改放置物品，防止BlockPlaceHandler内意外篡改喵~
        if (!locked) {
            // 事件未锁定，正常更新放置的物品喵~
            this.placedItem = item;
        } else {
            // 事件已锁定，发出警告告知调用方不能在BlockPlaceHandler中修改事件喵~
            SlimefunItem.getByItem(placedItem)
                    .warn("A BlockPlacerPlaceEvent cannot be modified from within a BlockPlaceHandler!");
        }
    }

    @Override
    public boolean isCancelled() {
        // 返回事件是否被取消的状态喵~
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        // 喵~防御：事件被锁定后不允许修改取消状态，防止BlockPlaceHandler内意外篡改喵~
        if (!locked) {
            // 事件未锁定，正常设置取消标记喵~
            cancelled = cancel;
        } else {
            // 事件已锁定，发出警告告知调用方不能在BlockPlaceHandler中修改事件状态喵~
            SlimefunItem.getByItem(placedItem)
                    .warn("A BlockPlacerPlaceEvent cannot be modified from within a BlockPlaceHandler!");
        }
    }

    /**
     * 将此{@link Event}标记为不可变，之后将不能再修改其状态喵~
     */
    public void setImmutable() {
        // 将事件锁定，防止后续任何修改喵~
        locked = true;
    }

    @Nonnull
    public static HandlerList getHandlerList() {
        // 返回静态HandlerList供Bukkit事件系统使用喵~
        return handlers;
    }

    @Nonnull
    @Override
    public HandlerList getHandlers() {
        // 委托给静态方法getHandlerList喵~
        return getHandlerList();
    }
}
