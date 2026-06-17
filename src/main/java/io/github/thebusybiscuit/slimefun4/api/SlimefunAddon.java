package io.github.thebusybiscuit.slimefun4.api;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 这是一个基础接口，用于标识注册了 {@link SlimefunItem} 的 {@link Plugin}（插件）喵~
 *
 * 它还包含一些工具方法，比如 {@link SlimefunAddon#getBugTrackerURL()}，
 * 用于在出现 bug 时提供上下文信息喵~
 *
 * 如果你正在开发一个附属插件（Addon），推荐实现这个接口喵~
 *
 * @author TheBusyBiscuit
 * @author ybw0014
 *
 */
public interface SlimefunAddon {

    /**
     * 返回该 {@link SlimefunAddon} 所对应的 {@link JavaPlugin} 实例喵~
     * 也就是你自己插件的主类实例喵~
     *
     * @return 你的 {@link JavaPlugin} 实例喵~
     */
    @Nonnull
    JavaPlugin getJavaPlugin();

    /**
     * 返回该 {@link SlimefunAddon} 的 Bug 追踪器链接喵~
     * 当插件出现问题时，用户可以通过这个链接提交 bug 报告喵~
     *
     * @return 插件 Bug 追踪器的 URL，如果没有则返回 null 喵~
     */
    @Nullable String getBugTrackerURL();

    /**
     * 返回该附属插件的名称，默认取自 {@link SlimefunAddon#getJavaPlugin()} 提供的
     * {@link JavaPlugin} 的插件名称喵~
     *
     * @return 该 {@link SlimefunAddon} 的名称喵~
     */
    default @Nonnull String getName() {
        // 从底层 JavaPlugin 获取插件名称并返回喵~
        return getJavaPlugin().getName();
    }

    /**
     * 返回该附属插件的版本号，默认取自 {@link SlimefunAddon#getJavaPlugin()} 提供的
     * {@link JavaPlugin} 的版本信息喵~
     *
     * @return 该 {@link SlimefunAddon} 的版本号字符串喵~
     */
    default @Nonnull String getPluginVersion() {
        // 从 JavaPlugin 的描述文件中读取版本号并返回喵~
        return getJavaPlugin().getDescription().getVersion();
    }

    /**
     * 返回该附属插件的日志记录器（Logger），默认取自 {@link SlimefunAddon#getJavaPlugin()} 提供的
     * {@link JavaPlugin} 的 Logger 喵~
     *
     * @return 该 {@link SlimefunAddon} 的 {@link Logger} 实例喵~
     */
    default @Nonnull Logger getLogger() {
        // 从底层 JavaPlugin 获取 Logger 并返回，用于打印日志信息喵~
        return getJavaPlugin().getLogger();
    }

    /**
     * 检查给定的字符串是否是该 {@link SlimefunAddon} 的某个依赖项的名称喵~
     * 具体来说，会检查该字符串是否出现在 {@link PluginDescriptionFile#getDepend()}（强依赖）
     * 或 {@link PluginDescriptionFile#getSoftDepend()}（软依赖）中喵~
     *
     * 整体思路：先排除插件依赖自身的特殊情况，再从 plugin.yml 描述文件中查找强依赖和软依赖列表喵~
     * 输入：dependency - 要检查的依赖名称字符串喵~
     * 输出：boolean，true 表示该插件依赖此名称，false 表示不依赖喵~
     * 边界条件：dependency 为 null 时会抛出 IllegalArgumentException 喵~
     *
     * @param dependency
     *            要检查的依赖项名称喵~
     *
     * @return 该 {@link SlimefunAddon} 是否依赖给定名称的 {@link Plugin} 喵~
     */
    default boolean hasDependency(@Nonnull String dependency) {
        // 喵~防御：dependency 为 null 时抛出异常，避免后续字符串比较发生空指针崩溃喵~
        Validate.notNull(dependency, "The dependency cannot be null");

        // 一个插件不可能依赖自身，但此处特殊处理：名称相同时也视为"包含"关系，直接返回 true 喵~
        if (getJavaPlugin().getName().equalsIgnoreCase(dependency)) {
            return true;
        }

        // 获取该插件的描述文件，里面包含强依赖（depend）和软依赖（softdepend）列表喵~
        PluginDescriptionFile description = getJavaPlugin().getDescription();
        // 检查强依赖列表或软依赖列表中是否包含指定的依赖名称，任意一个包含就返回 true 喵~
        return description.getDepend().contains(dependency)
                || description.getSoftDepend().contains(dependency);
    }

    /**
     * 获取该附属插件的 Wiki URL 格式模板喵~
     * 其中使用 {0} 作为占位符，表示具体的词条名称喵~
     * 默认返回 null，表示该插件没有配置 Wiki 喵~
     *
     * @return Wiki 的 URL 格式字符串，使用 {0} 作为词条名称的占位符；如果没有则返回 null 喵~
     */
    default @Nullable String getWikiURL() {
        // 默认没有 Wiki URL，返回 null，子类可以覆盖此方法提供实际链接喵~
        return null;
    }
}
