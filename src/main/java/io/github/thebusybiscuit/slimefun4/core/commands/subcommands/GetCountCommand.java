package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import javax.annotation.Nonnull;
import org.bukkit.command.CommandSender;

/**
 * 输出当前 Slimefun Research 注册表内容，便于排查研究被其他插件修改后的实际状态。
 *
 * @author happy
 */
public class GetCountCommand extends SubCommand {

    protected GetCountCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "getcount", true);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.getcount.description";
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        // 喵~防御：复用调试权限，避免普通玩家查看内部 Research 注册表信息喵
        if (!sender.hasPermission("slimefun.command.debug")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        // 直接读取当前注册表，确认 SlimefunCustomGuide 等插件修改后的实际研究数量喵
        List<Research> researches = Slimefun.getRegistry().getResearches();
        // 向命令发送者输出当前注册表中的研究数量喵
        sender.sendMessage(ChatColors.color("&a当前 Slimefun Research 数量: &e" + researches.size()));

        // 逐条输出研究 key，便于定位具体研究是否被移除或替换喵
        if (researches.isEmpty()) {
            // 喵~防御：研究列表为空时给出明确提示，避免把空输出误认为命令未执行喵
            sender.sendMessage(ChatColors.color("&c当前 Research 注册表为空喵~"));
            return;
        }

        // 输出研究列表标题喵
        sender.sendMessage(ChatColors.color("&a当前 Research Keys:"));
        // 遍历注册表并输出每个研究的完整 NamespacedKey 喵
        for (Research research : researches) {
            // 喵~防御：注册表正常情况下不会包含 null，但调试命令不能因异常条目崩溃喵
            if (research != null) {
                sender.sendMessage(ChatColors.color("&7- &f" + research.getKey()));
            }
        }
    }
}
