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

            Player p = e.getPlayer();
            Block b = e.getClickedBlock().get();
            Location loc = b.getLocation();

            boolean damaged = ConnectorAgingManager.isConnectorDamaged(loc);
            float durability = ConnectorAgingManager.getDurability(loc);
            ConnectorConfig config = ConnectorAgingManager.getConfig(loc);

            if (damaged) {
                int required = ConnectorAgingManager.getRequiredRepairCount(0f);
                p.sendMessage(ChatColors.color("&c连接器已损坏！"));
                if (config != null) {
                    p.sendMessage(
                            ChatColors.color("&7需要 &e" + required + " &7个 " + itemName(config.repairItem) + " &7来修复"));
                }
                tryRepair(p, loc, config);
                return;
            }

            if (durability < 1f) {
                tryRepair(p, loc, config);
                return;
            }

            sendStatus(p, loc, durability, config);
        };
    }

    private void tryRepair(@Nonnull Player p, @Nonnull Location loc, ConnectorConfig config) {
        if (config == null) return;
        if (p.getInventory().getItemInMainHand().isSimilar(config.repairItem)
                || p.getInventory().getItemInOffHand().isSimilar(config.repairItem)) {
            ConnectorAgingManager.tryRepair(p, loc);
        }
    }

    private void sendStatus(@Nonnull Player p, @Nonnull Location loc, float durability, ConnectorConfig config) {
        String statusColor = ConnectorAgingManager.getStatusColor(durability);
        String statusText = ConnectorAgingManager.getStatusText(durability);
        String netStatus;
        if (EnergyNet.getNetworkFromLocation(loc) != null) {
            netStatus = "&2\u2714";
        } else {
            netStatus = "&4\u2718";
        }

        p.sendMessage(ChatColors.color("&7连接状态: " + netStatus));
        p.sendMessage(ChatColors.color(
                "&7耐久: " + statusColor + String.format("%.1f", durability * 100) + "% &7(" + statusText + ")"));

        if (config != null) {
            long remaining = ConnectorAgingManager.getRemainingJoules(loc);
            p.sendMessage(ChatColors.color("&7剩余吞吐: &f" + ConnectorAgingManager.formatJoules(remaining) + " &7J"));
            int needed = ConnectorAgingManager.getRequiredRepairCount(durability);
            p.sendMessage(ChatColors.color("&7修复: &e" + needed + " &7x " + itemName(config.repairItem)));
        }
    }

    private static String itemName(ItemStack item) {
        return item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().name();
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
