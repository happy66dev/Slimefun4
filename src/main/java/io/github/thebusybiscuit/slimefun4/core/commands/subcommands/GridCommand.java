package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class GridCommand extends SubCommand {

    protected GridCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "grid", false);
    }

    @Override
    protected String getDescription() {
        return "commands.grid.description";
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof Player p)) {
            Slimefun.getLocalization().sendMessage(sender, "commands.grid.player-only", true);
            return;
        }

        if (!sender.hasPermission("slimefun.command.grid")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        Block target = p.getTargetBlockExact(10);
        if (target == null) {
            Slimefun.getLocalization().sendMessage(sender, "commands.grid.no-target", true);
            return;
        }

        String info = EnergyNet.getComponentInfo(target.getLocation());
        sender.sendMessage(info);
    }
}
