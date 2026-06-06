// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy <k666kkk666k@163.com>
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.services.MachineDamageService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class MachineDamageCommand extends SubCommand {

    @ParametersAreNonnullByDefault
    MachineDamageCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "machine-damage", false);
    }

    @Override
    protected String getDescription() {
        return "commands.machine-damage.description";
    }

    @Override
    public void onExecute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.only-players", true);
            return;
        }

        if (!player.hasPermission("slimefun.command.machine-damage") && !player.isOp()) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null) {
            Slimefun.getLocalization().sendMessage(player, "commands.machine-damage.no-target", true);
            return;
        }

        Location location = targetBlock.getLocation();
        SlimefunItem item = BlockStorage.check(targetBlock);
        if (item == null) {
            Slimefun.getLocalization().sendMessage(player, "commands.machine-damage.not-machine", true);
            return;
        }

        MachineDamageService damageService = Slimefun.getMachineDamageService();

        if (args.length == 1) {
            Slimefun.getLocalization()
                    .sendMessage(
                            player,
                            "messages.usage",
                            true,
                            msg -> msg.replace("%usage%", "/sf machine-damage <scrap|repair|info>"));
            return;
        }

        switch (args[1].toLowerCase()) {
            case "scrap" -> {
                damageService.scrapMachine(location, item);
                Slimefun.getLocalization().sendMessage(player, "commands.machine-damage.scrapped", true);
            }
            case "repair" -> {
                damageService.repairMachine(location);
                Slimefun.getLocalization().sendMessage(player, "commands.machine-damage.repaired", true);
            }
            case "info" -> {
                String info = damageService.getMachineInfo(location, item);
                Slimefun.getLocalization().sendMessage(player, "commands.machine-damage.info-header", true);
                player.sendMessage(info);
            }
            default -> {
                Slimefun.getLocalization().sendMessage(player, "commands.machine-damage.unknown-subcommand", true);
            }
        }
    }
}
