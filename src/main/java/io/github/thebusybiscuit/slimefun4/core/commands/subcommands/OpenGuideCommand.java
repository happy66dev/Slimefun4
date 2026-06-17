package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// 此子命令类用于处理 /sf open_guide 命令，直接为玩家打开 Slimefun 指南界面喵~
class OpenGuideCommand extends SubCommand {

    /**
     * 构造函数：注册 open_guide 子命令到 Slimefun 命令体系喵~
     * 输入：plugin - Slimefun 插件主实例，cmd - 父命令对象
     * 输出：初始化好的子命令实例，false 表示此命令不在帮助菜单中显示喵~
     */
    @ParametersAreNonnullByDefault
    OpenGuideCommand(Slimefun plugin, SlimefunCommand cmd) {
        // 调用父类构造，注册命令名为 "open_guide"，第四个参数 false 表示不显示在帮助列表中喵~
        super(plugin, cmd, "open_guide", false);
    }

    /**
     * 命令执行入口：当玩家执行 /sf open_guide 时被调用喵~
     * 整体思路：
     *   1. 先判断命令发送者是否为玩家（控制台不能打开 GUI 界面）
     *   2. 再检查玩家是否拥有 slimefun.command.open_guide 权限
     *   3. 有权限则直接打开生存模式的 Slimefun 指南界面
     *   4. 无权限则发送"没有权限"的本地化提示消息
     * 输入：sender - 命令发送者（玩家或控制台），args - 命令参数数组（本命令不使用）
     * 输出：无返回值，副作用是打开指南 GUI 或发送提示消息喵~
     */
    @Override
    @ParametersAreNonnullByDefault
    public void onExecute(CommandSender sender, String[] args) {
        // 判断命令发送者是否为玩家实例，使用 Java 16+ 的模式匹配语法顺便转型为 player 喵~
        if (sender instanceof Player player) {
            // 检查玩家是否拥有打开指南的权限节点 slimefun.command.open_guide 喵~
            if (sender.hasPermission("slimefun.command.open_guide")) {
                // 玩家有权限，以生存模式（SURVIVAL_MODE）为该玩家打开 Slimefun 指南界面喵~
                SlimefunGuide.openGuide(player, SlimefunGuideMode.SURVIVAL_MODE);
            } else {
                // 喵~防御：玩家没有权限时，发送本地化的"无权限"提示消息，第三个参数 true 表示带前缀喵~
                Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            }
        } else {
            // 喵~防御：命令发送者不是玩家（如控制台），发送"仅玩家可用"的本地化提示消息喵~
            Slimefun.getLocalization().sendMessage(sender, "messages.only-players", true);
        }
    }
}
