// 调试鱼子命令类，处理 /sf debug_fish 命令，给拥有权限的玩家发放调试用特殊鱼物品喵~
package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// DebugFishCommand 是 SubCommand 的子类，专门负责处理给予调试鱼的子命令喵~
class DebugFishCommand extends SubCommand {

    /**
     * 构造方法：将此子命令注册到 Slimefun 主插件和父命令中喵~
     * 参数 plugin 是 Slimefun 主插件实例，用于访问本地化等功能喵~
     * 参数 cmd 是父级命令 /sf，本子命令挂载在其下喵~
     * "debug_fish" 是子命令名称，true 表示此命令仅限玩家使用（控制台无法执行）喵~
     */
    @ParametersAreNonnullByDefault
    DebugFishCommand(Slimefun plugin, SlimefunCommand cmd) {
        // 调用父类构造，注册子命令名为 "debug_fish"，true 表示仅玩家可用喵~
        super(plugin, cmd, "debug_fish", true);
    }

    /**
     * 命令执行入口：当玩家输入 /sf debug_fish 时被调用喵~
     * 整体逻辑：
     *   1. 检查命令发送者是否是玩家实例，且拥有 slimefun.debugging 权限节点喵~
     *   2. 满足条件则将调试鱼的克隆物品加入玩家背包喵~
     *   3. 不满足条件（非玩家或无权限）则发送本地化"无权限"提示消息喵~
     * 输入：sender=命令发送者（玩家或控制台），args=命令参数数组（此命令不需要参数）喵~
     * 输出：无返回值，副作用是修改玩家背包或向发送者发送消息喵~
     */
    @Override
    public void onExecute(CommandSender sender, String[] args) {
        // 喵~防御：同时判断 sender 是否为 Player 实例并检查权限，防止控制台或无权限用户触发喵~
        if (sender instanceof Player player && sender.hasPermission("slimefun.debugging")) {
            // 将 DEBUG_FISH 物品的克隆副本加入玩家背包，clone() 确保不会修改原始物品数据喵~
            player.getInventory().addItem(SlimefunItems.DEBUG_FISH.clone());
        } else {
            // 权限不足时通过本地化系统发送"无权限"提示，true 表示附带插件前缀喵~
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
        }
    }
}
