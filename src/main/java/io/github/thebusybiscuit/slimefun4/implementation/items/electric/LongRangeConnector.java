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
package io.github.thebusybiscuit.slimefun4.implementation.items.electric;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.rotations.NotRotatable;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.ConnectorAgingManager;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class LongRangeConnector extends SimpleSlimefunItem<BlockUseHandler>
        implements EnergyNetComponent, NotRotatable {

    private static final int RANGE = 128;

    @ParametersAreNonnullByDefault
    public LongRangeConnector(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            ItemStack recipeOutput) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);
    }

    @Override
    public @Nonnull BlockUseHandler getItemHandler() {
        return e -> {
            if (!e.getClickedBlock().isPresent()) {
                return;
            }

            if (e.getInteractEvent().getHand() != EquipmentSlot.HAND) {
                return;
            }

            Player p = e.getPlayer();
            Block b = e.getClickedBlock().get();
            Location loc = b.getLocation();

            boolean damaged = ConnectorAgingManager.isConnectorDamaged(loc);
            float durability = ConnectorAgingManager.getDurability(loc);

            if (damaged) {
                p.sendMessage(ChatColors.color("&c连接器已损坏，无法供能！"));
                p.sendMessage(ChatColors.color("&7请手持对应维修材料右键提交："));
                p.sendMessage(ChatColors.color("&e需要：" + ConnectorAgingManager.getRepairItemsDisplay(loc)));
                ConnectorAgingManager.tryRepair(p, loc);
                return;
            }

            if (durability > 0.99f) {
                return;
            }

            p.sendMessage(ChatColors.color("&e连接器正在老化！"));
            p.sendMessage(ChatColors.color("&7请手持维修材料右键提交："));
            p.sendMessage(ChatColors.color("&e需要：" + ConnectorAgingManager.getRepairItemsDisplay(loc)));
            ConnectorAgingManager.tryRepair(p, loc);
        };
    }

    @Override
    public final @Nonnull EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONNECTOR;
    }

    @Override
    public int getCapacity() {
        return 0;
    }

    @Override
    public int getRange() {
        return RANGE;
    }
}
