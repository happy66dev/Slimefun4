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
package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.Capacitor;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.EnergyConnector;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.reactors.Reactor;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AGenerator;
import org.bukkit.block.Block;

/**
 * This enum contains the different types of components an {@link EnergyNet}
 * can have.
 *
 * @author TheBusyBiscuit
 * @author Linox
 * @see EnergyNetComponent
 * @see EnergyNet
 */
public enum EnergyNetComponentType {

    /**
     * A Generator generates Energy and feeds it into the network.
     * Also see: {@link AGenerator} or {@link Reactor}
     */
    GENERATOR,

    /**
     * A {@link Capacitor} stores energy from the network and provides it to any consumers.
     * It can be used as a buffer.
     */
    CAPACITOR,

    /**
     * A Consumer consumes energy from the network, most often linked to {@link AContainer}
     * or other types of machinery.
     */
    CONSUMER,

    /**
     * A Connector transmits energy through the network.
     * Also see: {@link EnergyConnector}
     */
    CONNECTOR,

    /**
     * A fallback value to use when a {@link Block} cannot be classified as any of the
     * other options.
     */
    NONE;
}
