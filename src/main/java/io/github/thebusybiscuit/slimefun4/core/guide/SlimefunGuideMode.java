package io.github.thebusybiscuit.slimefun4.core.guide;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import javax.annotation.Nonnull;

/**
 * 这个枚举定义了 SlimefunGuide（Slimefun指南书）支持的所有显示模式喵~
 * 每个枚举常量对应一种具体的 SlimefunGuideImplementation（指南书实现逻辑）喵~
 *
 * This enum holds the different designs a {@link SlimefunGuide} can have.
 * Each constant corresponds to a {@link SlimefunGuideImplementation}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunGuide
 * @see SlimefunGuideImplementation
 *
 */
public enum SlimefunGuideMode {

    /**
     * 普通生存模式下的标准指南书样式，所有玩家均可正常使用喵~
     * This design is the standard layout used in survival mode.
     */
    SURVIVAL_MODE("普通模式"),

    /**
     * 仅管理员可用的作弊模式指南书样式，允许直接刷出任意 SlimefunItem 物品喵~
     * This is an admin-only design which creates a {@link SlimefunGuide} that allows
     * you to spawn in any {@link SlimefunItem}
     */
    CHEAT_MODE("作弊模式");

    // 当前指南书模式对应的本地化显示名称，用于在UI界面中展示给玩家喵~
    private final String displayName;

    /**
     * 枚举构造方法，接收显示名称字符串并保存到成员变量喵~
     *
     * @param displayName 该指南书模式的显示名称，不能为null喵~
     */
    SlimefunGuideMode(@Nonnull String displayName) {
        // 将传入的显示名称保存到字段，供 getDisplayName() 方法返回喵~
        this.displayName = displayName;
    }

    /**
     * 获取指南书样式的本地化显示名称，用于在界面或日志中标识当前模式喵~
     *
     * @return 指南书样式的显示名称，不会为null喵~
     */
    public @Nonnull String getDisplayName() {
        // 直接返回构造时保存的显示名称字符串喵~
        return displayName;
    }
}
