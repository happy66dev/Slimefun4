package io.github.thebusybiscuit.slimefun4.implementation.items.electric;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.rotations.NotRotatable;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.ConnectorAgingManager;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.ConnectorAgingManager.ConnectorConfig;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet;
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
            ConnectorConfig config = ConnectorAgingManager.getConfig(loc);

            if (damaged) {
                p.sendMessage(ChatColors.color("&c连接器已损坏！"));
                p.sendMessage(ChatColors.color("&7需要: " + ConnectorAgingManager.getRepairItemsDisplay(loc)));
                ConnectorAgingManager.tryRepair(p, loc);
                return;
            }

            if (durability < 1f) {
                ConnectorAgingManager.tryRepair(p, loc);
                return;
            }

            sendStatus(p, loc, durability, config);
        };
    }

    private void sendStatus(@Nonnull Player p, @Nonnull Location loc, float durability, ConnectorConfig config) {
        String netStatus;
        if (EnergyNet.getNetworkFromLocation(loc) != null) {
            netStatus = "&2\u2714";
        } else {
            netStatus = "&4\u2718";
        }

        p.sendMessage(ChatColors.color("&7连接状态: " + netStatus));
        p.sendMessage(ChatColors.color("&7范围: &c" + RANGE + " 格"));

        if (config != null) {
            String statusColor = ConnectorAgingManager.getStatusColor(durability);
            String statusText = ConnectorAgingManager.getStatusText(durability);
            p.sendMessage(ChatColors.color(
                    "&7耐久: " + statusColor + String.format("%.1f", durability * 100) + "% &7(" + statusText + ")"));
            long remaining = ConnectorAgingManager.getRemainingJoules(loc);
            p.sendMessage(ChatColors.color("&7剩余吞吐: &f" + ConnectorAgingManager.formatJoules(remaining) + " &7J"));
            p.sendMessage(ChatColors.color("&7修复: " + ConnectorAgingManager.getRepairItemsDisplay(loc)));
        }
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
