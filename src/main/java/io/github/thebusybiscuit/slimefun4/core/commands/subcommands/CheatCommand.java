package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// 作弊指令子命令类，处理 /sf cheat 命令，允许有权限的玩家打开物品作弊菜单喵~
class CheatCommand extends SubCommand {

    /**
     * 构造函数：注册 cheat 子命令到插件和父命令喵~
     * 整体思路：调用父类 SubCommand 构造器，传入命令名称 "cheat" 和 false(表示此命令不需要OP权限)喵~
     * 输入：plugin - Slimefun 插件实例；cmd - 父命令 SlimefunCommand 实例喵~
     * 边界条件：@ParametersAreNonnullByDefault 注解确保参数均不为 null 喵~
     */
    @ParametersAreNonnullByDefault
    CheatCommand(Slimefun plugin, SlimefunCommand cmd) {
        // 调用父类构造器，注册命令名为 "cheat"，false 表示非 OP 专属命令喵~
        super(plugin, cmd, "cheat", false);
    }

    /**
     * 执行 /sf cheat 命令的核心逻辑喵~
     * 整体思路：先判断发送者是否是玩家，再验证是否有 slimefun.cheat.items 权限，
     *          有权限则打开作弊菜单，无权限则发送提示消息，非玩家也发送仅限玩家提示喵~
     * 输入：sender - 命令发送者(玩家或控制台)；args - 命令附带的参数数组喵~
     * 边界条件：控制台无法使用此命令；玩家需要有专属权限节点才能打开菜单喵~
     */
    @Override
    public void onExecute(CommandSender sender, String[] args) {
        // 喵~防御：判断发送者是否为玩家实例，控制台不支持打开GUI菜单喵~
        if (sender instanceof Player player) {
            // 检查玩家是否拥有作弊物品所需的权限节点 slimefun.cheat.items 喵~
            if (sender.hasPermission("slimefun.cheat.items")) {
                // 玩家有权限，打开 Slimefun 作弊物品菜单(可获取所有物品)喵~
                SlimefunGuide.openCheatMenu(player);
            } else {
                // 玩家无权限，通过本地化系统发送"没有权限"的提示消息给玩家喵~
                Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            }
        } else {
            // 发送者是控制台或非玩家实体，发送"仅限玩家使用"的提示消息喵~
            Slimefun.getLocalization().sendMessage(sender, "messages.only-players", true);
        }
    }
}
