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

            Player p = e.getPlayer();
            Block b = e.getClickedBlock().get();
            Location loc = b.getLocation();

            boolean damaged = ConnectorAgingManager.isConnectorDamaged(loc);
            float durability = ConnectorAgingManager.getDurability(loc);

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
        return RANGE;
    }
}
