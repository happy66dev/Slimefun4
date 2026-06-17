package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.guide.CheatSheetSlimefunGuide;
import io.github.thebusybiscuit.slimefun4.implementation.guide.SurvivalSlimefunGuide;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.lang.Validate;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * This {@link Event} is called whenever a {@link Player} clicks to unlock a {@link Research}.
 * This is called before {@link Research#canUnlock(Player)}.
 * The {@link Event} is not called for {@link CheatSheetSlimefunGuide}.
 *
 * @author uiytt
 *
 * @see SurvivalSlimefunGuide
 *
 */
// 玩家解锁研究前触发的事件类，实现了可取消接口以便监听器阻止解锁喵~
public class PlayerPreResearchEvent extends Event implements Cancellable {

    // Bukkit事件系统要求的静态处理器列表，用于注册所有监听此事件的监听器喵~
    private static final HandlerList handlers = new HandlerList();

    // 触发本次研究解锁操作的玩家对象喵~
    private final Player player;
    // 玩家尝试解锁的研究对象喵~
    private final Research research;
    // 与本次研究关联的Slimefun物品喵~
    private final SlimefunItem slimefunItem;
    // 记录事件是否被取消，true表示解锁流程将被中止喵~
    private boolean cancelled;

    /**
     * 构造方法：创建一个玩家预解锁研究事件实例喵~
     * 输入：触发事件的玩家p、目标研究research、关联的Slimefun物品slimefunItem
     * 边界条件：三个参数均不允许为null，否则抛出IllegalArgumentException喵~
     */
    @ParametersAreNonnullByDefault
    public PlayerPreResearchEvent(Player p, Research research, SlimefunItem slimefunItem) {
        // 喵~防御：确保传入的玩家对象不为null，避免后续操作产生空指针异常喵
        Validate.notNull(p, "The Player cannot be null");
        // 喵~防御：确保传入的研究对象不为null，避免解锁逻辑出现空指针崩溃喵
        Validate.notNull(research, "Research cannot be null");
        // 喵~防御：确保传入的SlimefunItem不为null，避免物品相关操作空指针崩溃喵
        Validate.notNull(slimefunItem, "SlimefunItem cannot be null");

        // 将传入的玩家赋值给事件字段，供后续监听器通过getPlayer()读取喵~
        this.player = p;
        // 将传入的研究对象赋值给事件字段，供后续监听器通过getResearch()读取喵~
        this.research = research;
        // 将传入的Slimefun物品赋值给事件字段，供后续监听器通过getSlimefunItem()读取喵~
        this.slimefunItem = slimefunItem;
    }

    // 获取触发本次研究解锁事件的玩家对象喵~
    @Nonnull
    public Player getPlayer() {
        return player;
    }

    // 获取玩家尝试解锁的研究对象喵~
    @Nonnull
    public Research getResearch() {
        return research;
    }

    // 获取与本次研究关联的Slimefun物品喵~
    @Nonnull
    public SlimefunItem getSlimefunItem() {
        return slimefunItem;
    }

    // Bukkit事件体系要求的静态方法，返回全局事件处理器列表供事件总线使用喵~
    @Nonnull
    public static HandlerList getHandlerList() {
        return handlers;
    }

    // 实现Event抽象方法，返回当前事件的处理器列表，Bukkit通过此方法分发事件喵~
    @Nonnull
    @Override
    public HandlerList getHandlers() {
        return getHandlerList();
    }

    // 实现Cancellable接口：返回事件是否已被取消，true表示解锁流程将被阻止喵~
    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    // 实现Cancellable接口：设置取消状态，监听器传入true可阻止后续解锁逻辑执行喵~
    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
