package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.implementation.items.tools.ExplosiveTool;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.lang.Validate;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 本事件在玩家使用{@link ExplosiveTool}（爆炸工具）破坏方块时触发喵~
 *
 * @author GallowsDove
 *
 * @see ExplosiveTool
 *
 */
public class ExplosiveToolBreakBlocksEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList(); // 事件处理器列表，用于Bukkit事件系统的静态注册喵~

    private final ItemStack itemInHand; // 玩家手中持有的工具物品堆喵~
    private final ExplosiveTool explosiveTool; // 触发本次事件的爆炸工具实例喵~
    private final Block mainBlock; // 被破坏的主要方块，即玩家直接指向的那个方块喵~
    private final List<Block> additionalBlocks; // 被爆炸波及额外破坏的方块列表喵~
    private boolean cancelled; // 事件是否已被取消的标志位喵~

    /**
     * 构造一个ExplosiveToolBreakBlocksEvent事件实例喵~
     * 各参数均不允许为null，构造时会进行防御性校验喵~
     */
    @ParametersAreNonnullByDefault
    public ExplosiveToolBreakBlocksEvent(
            Player player, Block block, List<Block> blocks, ItemStack item, ExplosiveTool explosiveTool) {
        super(player); // 调用父类PlayerEvent构造器，传入触发本次事件的玩家喵~

        Validate.notNull(block, "The center block cannot be null!"); // 喵~防御：主方块参数不能为null，否则后续逻辑无法确定破坏中心点喵
        Validate.notNull(blocks, "Blocks cannot be null"); // 喵~防御：额外方块列表不能为null，即便没有额外方块也应传入空列表喵
        Validate.notNull(item, "Item cannot be null"); // 喵~防御：手中物品不能为null，必须存在玩家正在使用的工具喵
        Validate.notNull(explosiveTool, "ExplosiveTool cannot be null"); // 喵~防御：爆炸工具实例不能为null，必须绑定具体工具类型喵

        this.mainBlock = block; // 记录被破坏的主要方块喵~
        this.additionalBlocks = blocks; // 记录被爆炸波及的额外方块列表喵~
        this.itemInHand = item; // 记录玩家手中持有的工具物品喵~
        this.explosiveTool = explosiveTool; // 记录触发本次事件的爆炸工具实例喵~
    }

    /**
     * 获取被破坏的主要{@link Block}（方块）喵~
     * 该方块触发了本{@link Event}（事件），且不包含在{@link #getAdditionalBlocks()}的返回列表中喵~
     *
     * @return 被破坏的主要{@link Block}喵~
     */
    @Nonnull
    public Block getPrimaryBlock() {
        return this.mainBlock; // 返回主要被破坏的方块喵~
    }

    /**
     * 获取本次事件中被额外破坏的{@link Block}（方块）列表喵~
     * 这些方块是被爆炸波及而非玩家直接指向的喵~
     *
     * @return 被破坏的额外方块{@link List}喵~
     */
    @Nonnull
    public List<Block> getAdditionalBlocks() {
        return this.additionalBlocks; // 返回被爆炸波及的额外方块列表喵~
    }

    /**
     * 获取触发本次事件的{@link ExplosiveTool}（爆炸工具）实例喵~
     *
     * @return 触发事件的{@link ExplosiveTool}喵~
     */
    @Nonnull
    public ExplosiveTool getExplosiveTool() {
        return this.explosiveTool; // 返回触发事件的爆炸工具实例喵~
    }

    /**
     * 获取本次事件中玩家手中持有的{@link ItemStack}（物品堆）喵~
     *
     * @return {@link Player}手中持有的{@link ItemStack}喵~
     */
    @Nonnull
    public ItemStack getItemInHand() {
        return this.itemInHand; // 返回玩家手中持有的工具物品堆喵~
    }

    @Override
    public boolean isCancelled() {
        return cancelled; // 返回事件是否已被取消的标志位喵~
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel; // 更新事件取消状态标志位喵~
    }

    /**
     * 获取本事件类型的静态{@link HandlerList}处理器列表喵~
     * 这是Bukkit事件系统的标准要求，用于注册和管理事件监听器喵~
     *
     * @return 静态{@link HandlerList}处理器列表喵~
     */
    @Nonnull
    public static HandlerList getHandlerList() {
        return handlers; // 返回静态处理器列表实例喵~
    }

    @Nonnull
    @Override
    public HandlerList getHandlers() {
        return getHandlerList(); // 委托给静态方法，返回同一个处理器列表喵~
    }
}
