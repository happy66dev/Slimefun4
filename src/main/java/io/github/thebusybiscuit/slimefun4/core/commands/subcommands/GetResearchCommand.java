package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import javax.annotation.ParametersAreNonnullByDefault;

class GetResearchCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    GetResearchCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "getresearch", false);
    }

    @Override
    @ParametersAreNonnullByDefault
    public void onExecute(CommandSender sender, String[] args) {
        if (sender instanceof Player) {
            if (sender.hasPermission("slimefun.command.cheat")) {
                SlimefunItem item = SlimefunItem.getById(args[1]);
                if (item != null) {
                    if (item.hasResearch()) {
                        sender.sendMessage(item.getResearch().getKey().getNamespace() + "." + item.getResearch().getKey().getKey());
                    } else {
                        sender.sendMessage("这个物品没有研究"+args[1]);
                    }
                } else {
                    sender.sendMessage("你输入的物品id错误"+args[1]);
                }
            } else {
                Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            }
        } else {
            Slimefun.getLocalization().sendMessage(sender, "messages.only-players", true);
        }
    }
}
