package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * GuideCommand — 处理 /sf guide 子命令喵~
 *
 * 整体思路：
 *   玩家执行 /sf guide 后，此命令会依次检查发送者身份与权限喵~
 *   若发送者是拥有对应权限的玩家，则将 Slimefun 指南书发放到其背包喵~
 *   若无权限则发送拒绝提示，若非玩家(如控制台)则发送"仅玩家可用"提示喵~
 *
 * 输入：CommandSender sender（命令发送者），String[] args（命令参数，此命令不使用）
 * 输出：无返回值，副作用为给玩家背包添加指南书或发送提示消息喵~
 * 边界条件：sender 为 null 由父类框架保证不会发生；权限节点缺失时走无权限分支喵~
 */
// 继承 SubCommand，将本类注册为名为 "guide" 的子命令，false 表示此命令不对外隐藏喵~
class GuideCommand extends SubCommand {

    /**
     * 构造函数：将 GuideCommand 注册到 Slimefun 命令系统喵~
     *
     * @param plugin 当前 Slimefun 插件实例，用于访问本地化等功能喵~
     * @param cmd    父级 SlimefunCommand 对象，用于子命令挂载喵~
     */
    @ParametersAreNonnullByDefault
    GuideCommand(Slimefun plugin, SlimefunCommand cmd) {
        // 调用父类构造器，注册子命令名称为 "guide"，false 表示该命令在帮助列表中可见喵~
        super(plugin, cmd, "guide", false);
    }

    /**
     * 命令执行入口：当玩家或其他发送者执行 /sf guide 时触发喵~
     *
     * 整体思路：
     *   1. 先判断发送者是否为玩家，控制台无法接收物品故拒绝喵~
     *   2. 再判断玩家是否拥有 slimefun.command.guide 权限节点喵~
     *   3. 权限满足时获取服务器配置的默认指南模式，并将对应指南书的克隆放入玩家背包喵~
     *
     * @param sender 命令的发送者，可能是玩家、控制台或命令方块喵~
     * @param args   命令附带的参数数组，此命令不使用任何参数喵~
     */
    @Override
    public void onExecute(CommandSender sender, String[] args) {
        // 喵~防御：使用 instanceof 模式匹配判断发送者是否为玩家，非玩家(如控制台)走 else 分支喵~
        if (sender instanceof Player player) {
            // 检查玩家是否拥有领取指南书的权限节点 slimefun.command.guide 喵~
            if (sender.hasPermission("slimefun.command.guide")) {
                // 获取当前服务器配置的默认指南显示模式(如生存模式/百科模式)喵~
                SlimefunGuideMode design = SlimefunGuide.getDefaultMode();
                // 根据指南模式获取对应的指南书 ItemStack，clone() 防止修改原始物品模板，然后放入玩家背包喵~
                player.getInventory().addItem(SlimefunGuide.getItem(design).clone());
            } else {
                // 玩家无权限时通过本地化系统向其发送无权限提示消息喵~
                Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            }
        } else {
            // 喵~防御：发送者不是玩家(如控制台)时，发送"仅玩家可用"提示消息喵~
            Slimefun.getLocalization().sendMessage(sender, "messages.only-players", true);
        }
    }
}
