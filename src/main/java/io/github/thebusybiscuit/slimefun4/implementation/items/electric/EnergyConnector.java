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
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class EnergyConnector extends SimpleSlimefunItem<BlockUseHandler> implements EnergyNetComponent, NotRotatable {

    private final int range;

    @ParametersAreNonnullByDefault
    public EnergyConnector(
            ItemGroup itemGroup,
            int tier,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            ItemStack recipeOutput) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);
        this.range = tier;
    }

    @Override
    public @Nonnull BlockUseHandler getItemHandler() {
        return e -> {
            if (!e.getClickedBlock().isPresent()) {
                return;
            }

            Slimefun.logger()
                    .log(
                            Level.INFO,
                            "[连接器维修Debug] 事件手={0}, useBlock={1}, useItem={2}, 事件取消={3}",
                            new Object[] {
                                e.getInteractEvent().getHand(),
                                e.useBlock(),
                                e.useItem(),
                                e.getInteractEvent().isCancelled()
                            });

            if (e.getInteractEvent().getHand() != EquipmentSlot.HAND) {
                Slimefun.logger().info("[连接器维修Debug] 忽略副手连接器交互");
                return;
            }

            Player p = e.getPlayer();
            Block b = e.getClickedBlock().get();
            Location loc = b.getLocation();

            boolean damaged = ConnectorAgingManager.isConnectorDamaged(loc);
            float durability = ConnectorAgingManager.getDurability(loc);

            Slimefun.logger()
                    .log(
                            Level.INFO,
                            "[连接器维修Debug] 玩家={0}, 目标位置={1}, 连接器={2}, damaged={3}, durability={4}, 手持Material={5}, 手持数量={6}",
                            new Object[] {
                                p.getName(),
                                loc,
                                getId(),
                                damaged,
                                durability,
                                p.getInventory().getItemInMainHand().getType(),
                                p.getInventory().getItemInMainHand().getAmount()
                            });

            if (damaged) {
                p.sendMessage(ChatColors.color("&c连接器已损坏！"));
                p.sendMessage(ChatColors.color("&7修复: " + ConnectorAgingManager.getRepairItemsDisplay(loc)));
                ConnectorAgingManager.tryRepair(p, loc);
                return;
            }

            if (durability > 0.99f) {
                return;
            }

            p.sendMessage(ChatColors.color("&7修复: " + ConnectorAgingManager.getRepairItemsDisplay(loc)));
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
        return range;
    }
}
