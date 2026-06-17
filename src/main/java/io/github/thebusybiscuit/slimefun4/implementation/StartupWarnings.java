package io.github.thebusybiscuit.slimefun4.implementation;

import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * This class stores some startup warnings we occasionally need to print.
 * If you setup your server the recommended way, you are never going to see
 * any of these messages.
 *
 * @author TheBusyBiscuit
 *
 */
// 这是一个包级私有的最终类，专门存放服务器启动时可能需要打印的各类警告信息喵~
// 如果服务器配置正确，这些警告信息正常情况下不会出现喵~
final class StartupWarnings {

    // 警告框的上下边框线，由星号组成，用于在控制台日志中突出显示警告区域喵~
    private static final String BORDER = "****************************************************";
    // 每行警告信息的前缀，以"* "开头，保持警告框内容的视觉对齐喵~
    private static final String PREFIX = "* ";

    // 私有构造方法，禁止外部实例化此工具类，所有方法均为静态调用喵~
    private StartupWarnings() {}

    /**
     * 当检测到服务器安装了已废弃的 CS-CoreLib 插件时，向日志输出严重警告喵~
     * 自 2021/01/30 起 Slimefun 不再依赖 CS-CoreLib，安装它会导致运行异常喵~
     *
     * @param logger 用于输出日志的 Logger 实例，不能为 null 喵~
     */
    @ParametersAreNonnullByDefault
    static void discourageCSCoreLib(Logger logger) {
        // 输出警告框上边框，视觉上将警告内容与其他日志隔开喵~
        logger.log(Level.SEVERE, BORDER);
        // 告知服务器管理员检测到了 CS-CoreLib 插件喵~
        logger.log(Level.SEVERE, PREFIX + "你好像安装了 CS-CoreLib。");
        // 输出一个空行，增加可读性喵~
        logger.log(Level.SEVERE, PREFIX);
        // 说明 CS-CoreLib 的废弃时间节点，帮助管理员了解背景信息喵~
        logger.log(Level.SEVERE, PREFIX + "自 2021/01/30 起就不再强制依赖 CS-CoreLib 了");
        // 明确告知管理员需要卸载 CS-CoreLib 才能让 Slimefun 正常运行喵~
        logger.log(Level.SEVERE, PREFIX + "你需要卸载 CS-CoreLib 才能让 Slimefun 正常运行。");
        // 输出警告框下边框，结束本段警告内容喵~
        logger.log(Level.SEVERE, BORDER);
    }

    /**
     * 当检测到服务器运行的 Minecraft 版本不被当前 Slimefun 支持时，输出严重错误警告喵~
     * 会列出当前检测到的版本和 Slimefun 所有受支持的版本供管理员参考喵~
     *
     * @param logger          用于输出日志的 Logger 实例，不能为 null 喵~
     * @param detectedVer     当前服务器检测到的 Minecraft 版本字符串，不能为 null 喵~
     * @param slimefunVersion 当前安装的 Slimefun 版本字符串，用于告知管理员该版本的支持范围喵~
     */
    @ParametersAreNonnullByDefault
    static void invalidMinecraftVersion(Logger logger, String detectedVer, String slimefunVersion) {
        // 输出警告框上边框，突出显示严重错误信息喵~
        logger.log(Level.SEVERE, BORDER);
        // 告知管理员 Slimefun 加载失败喵~
        logger.log(Level.SEVERE, PREFIX + "Slimefun 加载失败!");
        // 说明失败原因：当前 Minecraft 版本不受支持喵~
        logger.log(Level.SEVERE, PREFIX + "你正在使用不支持的 Minecraft 版本!");
        // 输出空行，增加可读性喵~
        logger.log(Level.SEVERE, PREFIX);
        // 输出当前检测到的 Minecraft 版本号，{0} 会被 detectedVer 参数替换喵~
        logger.log(Level.SEVERE, PREFIX + "你正在使用 Minecraft {0}", detectedVer);
        // 说明当前 Slimefun 版本支持的 Minecraft 版本范围，{0} 会被 slimefunVersion 替换喵~
        logger.log(Level.SEVERE, PREFIX + "但 Slimefun {0} 只支持以下版本:", slimefunVersion);
        // 从 Slimefun 静态方法获取所有受支持的版本列表，用" / "拼接后展示喵~
        logger.log(Level.SEVERE, PREFIX + "Minecraft {0}", String.join(" / ", Slimefun.getSupportedVersions()));
        // 输出警告框下边框，结束本段错误信息喵~
        logger.log(Level.SEVERE, BORDER);
    }

    /**
     * 当检测到服务器使用了不受支持的服务端软件（如 CraftBukkit）时，输出严重错误警告喵~
     * Slimefun 仅支持 Paper 及其分支，使用 CraftBukkit 会导致加载失败喵~
     *
     * @param logger 用于输出日志的 Logger 实例，不能为 null 喵~
     */
    @ParametersAreNonnullByDefault
    static void invalidServerSoftware(Logger logger) {
        // 输出警告框上边框，突出显示严重错误信息喵~
        logger.log(Level.SEVERE, BORDER);
        // 告知管理员 Slimefun 加载失败喵~
        logger.log(Level.SEVERE, PREFIX + "Slimefun 加载失败!");
        // 说明失败原因：CraftBukkit 服务端已不再被支持喵~
        logger.log(Level.SEVERE, PREFIX + "我们不再支持 CraftBukkit 服务端了!");
        // 输出空行，增加可读性喵~
        logger.log(Level.SEVERE, PREFIX);
        // 告知管理员应使用 Paper 或其分支作为服务端喵~
        logger.log(Level.SEVERE, PREFIX + "你需要使用 Paper 或其分支的服务端");
        // 推荐使用 Paper 作为首选服务端喵~
        logger.log(Level.SEVERE, PREFIX + "(我们推荐 Paper)");
        // 输出警告框下边框，结束本段错误信息喵~
        logger.log(Level.SEVERE, BORDER);
    }

    /**
     * 当检测到服务器运行的 Java 版本低于推荐版本时，输出警告信息喵~
     * 高版本 Minecraft 要求更高的 Java 版本，Slimefun 未来也将提高 Java 最低版本要求喵~
     *
     * 整体思路：
     *   1. 先通过 NumberUtils.getJavaVersion() 获取当前 JVM 的 Java 版本号喵~
     *   2. 然后用 Level.WARNING 级别输出一系列提示信息，告知管理员升级 Java 的必要性喵~
     *   输入：logger（日志实例）、recommendedJavaVersion（推荐的 Java 版本号）喵~
     *   输出：无返回值，仅向日志写入警告内容喵~
     *
     * @param logger                 用于输出日志的 Logger 实例，不能为 null 喵~
     * @param recommendedJavaVersion 推荐使用的 Java 版本号，如 17 或 21 喵~
     */
    @ParametersAreNonnullByDefault
    static void oldJavaVersion(Logger logger, int recommendedJavaVersion) {
        // 调用工具类获取当前 JVM 的 Java 主版本号，例如 Java 11 返回 11 喵~
        int javaVersion = NumberUtils.getJavaVersion();

        // 输出警告框上边框，突出显示 Java 版本警告信息喵~
        logger.log(Level.WARNING, BORDER);
        // 告知管理员当前使用的 Java 版本已过时，{0} 会被 javaVersion 替换为具体版本号喵~
        logger.log(Level.WARNING, PREFIX + "正在使用的 Java 版本 (Java {0}) 已过时.", javaVersion);
        // 输出空行，增加可读性喵~
        logger.log(Level.WARNING, PREFIX);
        // 说明高版本 Minecraft 对新 Java 版本有强制要求，{0} 被 recommendedJavaVersion 替换喵~
        logger.log(Level.WARNING, PREFIX + "由于高版本 Minecraft 对 Java {0} 的强制依赖,", recommendedJavaVersion);
        // 建议管理员尽快升级到推荐的 Java 版本，{0} 被 recommendedJavaVersion 替换喵~
        logger.log(Level.WARNING, PREFIX + "我们推荐您尽快升级到 Java {0}.", recommendedJavaVersion);
        // 说明 Slimefun 本身也计划在未来利用新版 Java 的特性喵~
        logger.log(Level.WARNING, PREFIX + "同时，为尽快使用到新版本 Java 带来的特性,");
        // 告知管理员 Slimefun 未来将依赖更高版本的 Java，{0} 被 recommendedJavaVersion 替换喵~
        logger.log(Level.WARNING, PREFIX + "Slimefun 也会在不久的将来依赖于 Java {0}.", recommendedJavaVersion);
        // 最终提醒管理员尽快升级，避免未来版本的 Slimefun 无法正常运行喵~
        logger.log(Level.WARNING, PREFIX + "为了不影响您以后的正常使用，请尽快更新!");
        // 输出警告框下边框，结束本段警告信息喵~
        logger.log(Level.WARNING, BORDER);
    }
}
