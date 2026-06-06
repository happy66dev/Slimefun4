// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy <k666kkk666k@163.com>
// Based on Slimefun4 by TheBusyBiscuit and contributors, licensed under GPL-3.0
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
package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.api.events.ExplosiveToolBreakBlocksEvent;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunBlockBreakEvent;
import io.github.thebusybiscuit.slimefun4.api.events.SlimefunBlockPlaceEvent;
import io.github.thebusybiscuit.slimefun4.api.network.Network;
import io.github.thebusybiscuit.slimefun4.core.networks.NetworkManager;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * This {@link Listener} is responsible for all updates to a {@link Network}.
 *
 * @author meiamsome
 * @author TheBusyBiscuit
 *
 * @see Network
 * @see NetworkManager
 *
 */
public class NetworkListener implements Listener {

    /**
     * Our {@link NetworkManager} instance.
     */
    private final NetworkManager manager;

    public NetworkListener(@Nonnull Slimefun plugin, @Nonnull NetworkManager manager) {
        this.manager = manager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onBlockBreak(SlimefunBlockBreakEvent e) {
        Location broken = e.getBlockBroken().getLocation();
        EnergyNet.removeHologramAt(broken);
        manager.updateAllNetworks(broken);
        EnergyNet.wakeUpConflictNets();
        EnergyNet.abortAllInitializing();
    }

    @EventHandler
    public void onBlockPlace(SlimefunBlockPlaceEvent e) {
        manager.updateAllNetworks(e.getBlockPlaced().getLocation());
        EnergyNet.onMachinePlaced(e.getBlockPlaced().getLocation());
        EnergyNet.wakeUpConflictNets();
        EnergyNet.abortAllInitializing();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplosiveToolUse(ExplosiveToolBreakBlocksEvent e) {
        // Fixes #3013 - Also update networks when using an explosive tool
        for (Block b : e.getAdditionalBlocks()) {
            manager.updateAllNetworks(b.getLocation());
        }
        EnergyNet.wakeUpConflictNets();
        EnergyNet.abortAllInitializing();
    }
}
