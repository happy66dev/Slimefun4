package io.github.thebusybiscuit.slimefun4.api.events;

import javax.annotation.Nonnull;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 这个 {@link Event} 事件在玩家辐射等级每次tick更新时触发喵~
 * 事件会决定玩家辐射等级如何变化喵~
 * 辐射等级工具类请参考 {@link io.github.thebusybiscuit.slimefun4.utils.RadiationUtils} 喵~
 *
 * @author m1919810
 *
 */
@Getter
public class AsyncPlayerRadiationLevelUpdateEvent extends PlayerEvent { // 异步玩家辐射等级更新事件喵~继承自PlayerEvent表示这是个玩家相关的事件喵

    private static final HandlerList handlers = new HandlerList(); // 事件处理器列表喵~Bukkit事件系统要求每个事件类维护自己的处理器列表喵

    /**
     * 返回本次tick之前玩家的辐射等级喵~
     *
     * @return 之前的辐射等级喵
     */
    private final int previousLevel; // 玩家在本次tick更新前的辐射等级值喵~用于计算辐射等级变化量喵

    /**
     * 返回辐射等级更新的增量喵~这个数值可能为负数喵~
     * 事件调用后效果等同于调用 {@link io.github.thebusybiscuit.slimefun4.utils.RadiationUtils#addExposure(Player, int)} 喵~
     *
     * @return 辐射等级增量喵
     */
    private int deltaLevel; // 辐射等级变化量喵~正数表示辐射增加负数表示辐射减少喵

    /**
     * 覆盖辐射等级更新的增量值喵~
     *
     * @param deltaLevel 辐射等级增量的覆盖值喵
     */
    public void setDeltaLevel(int deltaLevel) { // 设置辐射等级增量喵~允许其他插件或模块覆盖默认的辐射变化量喵
        this.deltaLevel = deltaLevel; // 将传入的增量值赋给当前实例的deltaLevel字段喵
    }

    /**
     * 返回玩家是否拥有辐射防护喵~例如穿戴了防化服或处于创造模式喵~
     * 默认情况下如果玩家拥有fullProtection则辐射等级不会变化（{@link AsyncPlayerRadiationLevelUpdateEvent#deltaLevel} = 0）喵~
     * 但即使玩家拥有完全防护通过 {@link AsyncPlayerRadiationLevelUpdateEvent#setDeltaLevel(int)} 覆盖增量仍然可以改变玩家的辐射等级喵~
     *
     * @return fullProtection标志喵~true表示玩家当前有辐射防护喵
     */
    private final boolean fullProtection; // 完全辐射防护标志喵~表示玩家是否免疫辐射伤害喵

    public AsyncPlayerRadiationLevelUpdateEvent(
            Player player, int previousLevel, int delta, boolean hasProtection) { // 构造方法喵~传入玩家对象之前的辐射等级增量值和防护状态喵
        super(player, !Bukkit.isPrimaryThread()); // 调用父类PlayerEvent构造方法喵~第二个参数表示是否异步true为异步事件喵

        this.previousLevel = previousLevel; // 记录本次tick之前的辐射等级喵
        this.deltaLevel = delta; // 记录本次辐射变化的增量值喵
        this.fullProtection = hasProtection; // 记录玩家当前的辐射防护状态喵
    }

    @Nonnull
    public static HandlerList getHandlerList() { // 静态获取处理器列表方法喵~Bukkit事件系统约定必须提供此静态方法喵
        return handlers; // 返回该事件类共享的处理器列表喵
    }

    @Override
    public @NotNull HandlerList getHandlers() { // 实例获取处理器列表方法喵~Bukkit内部调用此方法获取事件的处理器列表喵
        return getHandlerList(); // 委托给静态方法返回处理器列表喵~保持代码统一喵
    }
}
