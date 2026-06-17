package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.implementation.items.androids.AndroidInstance;
import io.github.thebusybiscuit.slimefun4.implementation.items.androids.MinerAndroid;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * This {@link Event} is fired before a {@link MinerAndroid} mines a {@link Block}.
 * If this {@link Event} is cancelled, the {@link Block} will not be mined.
 *
 * @author poma123
 */
public class AndroidMineEvent extends Event implements Cancellable {

    // Bukkit事件系统的静态处理器列表，注册所有监听此事件的监听器喵~
    private static final HandlerList handlers = new HandlerList();

    private final Block block; // 被挖掘的目标方块喵~
    private final AndroidInstance android; // 触发此次挖掘事件的采矿机器人实例喵~
    private boolean cancelled; // 事件取消标志，设为true则阻止方块被挖掘喵~

    /**
     * @param block
     *            The mined {@link Block}
     * @param android
     *            The {@link AndroidInstance} that triggered this {@link Event}
     */
    @ParametersAreNonnullByDefault
    public AndroidMineEvent(Block block, AndroidInstance android) {
        this.block = block; // 保存被挖掘的目标方块引用喵~
        this.android = android; // 保存触发事件的采矿机器人实例引用喵~
    }

    /**
     * This method returns the mined {@link Block}
     *
     * @return the mined {@link Block}
     */
    @Nonnull
    public Block getBlock() {
        return block; // 返回被挖掘的目标方块喵~
    }

    /**
     * This method returns the {@link AndroidInstance} who
     * triggered this {@link Event}
     *
     * @return the involved {@link AndroidInstance}
     */
    @Nonnull
    public AndroidInstance getAndroid() {
        return android; // 返回触发事件的采矿机器人实例喵~
    }

    @Override
    public boolean isCancelled() {
        // 喵~防御：返回取消标志，若事件已被取消则采矿操作将被阻止喵~
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        // 设置事件取消状态，cancel为true时阻止采矿机器人挖掘方块喵~
        cancelled = cancel;
    }

    @Nonnull
    public static HandlerList getHandlerList() {
        return handlers; // 返回静态处理器列表供Bukkit事件系统使用喵~
    }

    @Nonnull
    @Override
    public HandlerList getHandlers() {
        return getHandlerList(); // 委托给静态方法getHandlerList()返回处理器列表喵~
    }
}
