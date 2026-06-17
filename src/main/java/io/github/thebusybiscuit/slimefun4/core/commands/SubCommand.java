package io.github.thebusybiscuit.slimefun4.core.commands;

import io.github.thebusybiscuit.slimefun4.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.defaults.HelpCommand;
import org.bukkit.entity.Player;

/**
 * This class represents a {@link SubCommand}, it is a {@link Command} that starts with
 * {@code /sf ...} and is followed by the name of this {@link SubCommand}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunCommand
 *
 */
// 抽象基类：所有 /sf 子命令的父类，提供公共字段、名称、隐藏标志和使用统计功能喵~
public abstract class SubCommand {

    // 持有 Slimefun 插件主实例，供子类访问插件功能喵~
    protected final Slimefun plugin;
    // 持有父命令 SlimefunCommand 实例，子命令需要反向引用父命令喵~
    protected final SlimefunCommand cmd;

    // 子命令的名称，即 /sf 后跟随的第一个参数，例如 "give" 或 "debug"喵~
    private final String name;
    // 是否在帮助列表中隐藏该子命令，true 表示不显示在 /sf help 中喵~
    private final boolean hidden;

    /*
     * 构造方法整体说明喵~
     * 功能：初始化子命令的基础数据，包括插件引用、父命令引用、命令名称和是否隐藏
     * 输入：plugin - Slimefun主实例，cmd - 父命令对象，name - 子命令名称字符串，hidden - 是否隐藏布尔值
     * 输出：无（构造方法）
     * 注意：@ParametersAreNonnullByDefault 注解保证所有参数不为null，无需额外null检查喵~
     */
    @ParametersAreNonnullByDefault
    protected SubCommand(Slimefun plugin, SlimefunCommand cmd, String name, boolean hidden) {
        // 将插件主实例赋值给成员变量，供子类调用 Slimefun API喵~
        this.plugin = plugin;
        // 将父命令对象赋值给成员变量，子类可通过 cmd 访问命令注册信息喵~
        this.cmd = cmd;

        // 保存子命令名称，用于命令路由匹配和帮助信息展示喵~
        this.name = name;
        // 保存隐藏标志，决定该子命令是否出现在 /sf help 的帮助列表喵~
        this.hidden = hidden;
    }

    /**
     * This returns the name of this {@link SubCommand}, the name is equivalent to the
     * first argument given to the actual command.
     *
     * @return The name of this {@link SubCommand}
     */
    // 返回子命令的名称字符串，例如 "give"、"debug" 等，用于命令分发匹配喵~
    @Nonnull
    public final String getName() {
        return name; // 直接返回构造时传入的命令名称喵~
    }

    /**
     * This method returns whether this {@link SubCommand} is hidden from the {@link HelpCommand}.
     *
     * @return Whether to hide this {@link SubCommand}
     */
    // 返回该子命令是否对玩家隐藏，true 则不显示在 /sf help 的帮助列表中喵~
    public final boolean isHidden() {
        return hidden; // 直接返回构造时传入的隐藏标志喵~
    }

    /*
     * recordUsage 方法说明喵~
     * 功能：记录该子命令被执行一次，通过 Map 统计各子命令的使用频次
     * 输入：commandUsage - 存储 SubCommand 到使用次数映射的 Map
     * 输出：无，但会修改 commandUsage 中该子命令对应的计数值
     * 边界条件：若该子命令不在 Map 中，merge 会以 1 初始化；若已存在则累加喵~
     */
    // 将当前子命令的使用次数在统计 Map 中加一，用于后台监控命令使用频率喵~
    protected void recordUsage(@Nonnull Map<SubCommand, Integer> commandUsage) {
        // 使用 Map.merge：若 key 不存在则设为 1，否则将旧值与新值 1 通过 Integer::sum 相加喵~
        commandUsage.merge(this, 1, Integer::sum);
    }

    // 抽象方法：子类必须实现，定义该子命令的具体执行逻辑，sender 是命令发送者，args 是命令参数数组喵~
    public abstract void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args);

    /*
     * getDescription() 无参版本说明喵~
     * 功能：生成本地化键路径字符串，格式为 "commands.<命令名>"，供语言文件查找翻译文本使用
     * 输入：无
     * 输出：形如 "commands.give" 的本地化 key 字符串喵~
     */
    // 拼接本地化配置键路径 "commands.<name>"，用于从语言文件中查询该命令的描述文本喵~
    @Nonnull
    protected String getDescription() {
        return "commands." + getName(); // 格式固定为 "commands." 前缀加上命令名称喵~
    }

    /**
     * This returns a description for this {@link SubCommand}.
     * If the given {@link CommandSender} is a {@link Player}, the description
     * will be localized with the currently selected {@link Language} of that {@link Player}.
     *
     * @param sender
     *            The {@link CommandSender} who requested the description
     *
     * @return A possibly localized description of this {@link SubCommand}
     */
    /*
     * getDescription(CommandSender) 方法说明喵~
     * 功能：根据命令发送者类型返回本地化后的命令描述字符串
     *   - 若发送者是玩家(Player)，使用该玩家的语言偏好进行本地化
     *   - 若发送者是控制台或其他非玩家，使用服务器默认语言
     * 输入：sender - 请求描述的命令发送者，不为null
     * 输出：本地化的描述字符串，不为null
     * 边界条件：sender instanceof Player 使用 Java 16+ 模式匹配写法，自动转型为 player 变量喵~
     */
    // 带发送者参数的描述方法，根据是否是玩家决定走玩家本地化还是全局默认语言喵~
    public @Nonnull String getDescription(@Nonnull CommandSender sender) {
        // 判断命令发送者是否是在线玩家，是则使用玩家个人语言设置进行本地化喵~
        if (sender instanceof Player player) {
            // 玩家发送者：按玩家已设置的语言 Language 返回本地化描述文本喵~
            return Slimefun.getLocalization().getMessage(player, getDescription());
        } else {
            // 非玩家发送者（如控制台）：使用服务器全局默认语言返回描述文本喵~
            return Slimefun.getLocalization().getMessage(getDescription());
        }
    }
}
