package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;

/**
 * 此命令向服务器控制台输出当前所有已注册 Slimefun 物品的显示名称喵~
 */
class GetAllItemCommand extends SubCommand {

    /**
     * 创建用于控制台导出物品显示名称的子命令喵~
     *
     * @param plugin Slimefun 插件实例喵~
     * @param cmd Slimefun 主命令实例喵~
     */
    protected GetAllItemCommand(Slimefun plugin, SlimefunCommand cmd) {
        // 注册隐藏子命令，避免普通帮助菜单显示大量调试功能喵~
        super(plugin, cmd, "getallitem", true);
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        // 喵~防御：仅允许控制台输出完整列表，避免玩家聊天栏被大量物品名称刷屏喵~
        if (!(sender instanceof ConsoleCommandSender)) {
            // 向非控制台执行者说明此命令的使用限制喵~
            sender.sendMessage(ChatColors.color("&c此命令只能由服务器控制台执行喵~"));
            return;
        }

        // 复制注册表列表，避免排序时修改全局注册顺序喵~
        List<SlimefunItem> registeredItems = Slimefun.getRegistry().getAllSlimefunItems().stream()
                // 按去色后的显示名称排序，使控制台结果稳定且便于检索喵~
                .sorted(Comparator.comparing(
                        item -> ChatColor.stripColor(item.getItemName()), String.CASE_INSENSITIVE_ORDER))
                // 将排序流收集为可重复遍历的列表喵~
                .toList();

        // 输出本次导出的物品总数喵~
        sender.sendMessage("[Slimefun] 已注册物品显示名称，共 " + registeredItems.size() + " 个：");

        // 逐行输出每个物品的显示名称喵~
        for (SlimefunItem registeredItem : registeredItems) {
            // 喵~防御：显示名称为空时回退物品 ID，保证单个异常物品不会中断导出喵~
            String displayName = registeredItem.getItemName();
            // 输出物品显示名称并去除颜色代码，保证控制台日志清晰可搜索喵~
            sender.sendMessage(ChatColor.stripColor(
                    displayName == null || displayName.isEmpty() ? registeredItem.getId() : displayName));
        }

        // 输出结束标记，便于在控制台日志中定位完整列表范围喵~
        sender.sendMessage("[Slimefun] 物品显示名称输出完成喵~");
    }
}
