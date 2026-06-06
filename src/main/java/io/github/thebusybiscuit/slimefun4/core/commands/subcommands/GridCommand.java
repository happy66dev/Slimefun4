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
