package io.github.thebusybiscuit.slimefun4.implementation.items.electric;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Locale;
import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.event.block.BlockPlaceEvent;

final class EnergyAccessoryPlacement {

    private static final String ENERGY_METER_ID = "ENERGY_METER";
    private static final String CURRENT_LIMITER_ID = "CURRENT_LIMITER";

    private EnergyAccessoryPlacement() {}

    static boolean validate(
            @Nonnull BlockPlaceEvent e,
            @Nonnull String currentItemId,
            @Nonnull String invalidConnectorMessage,
            @Nonnull String conflictMessage) {
        Location placed = e.getBlock().getLocation();
        Location connLoc = placed.clone().subtract(0, 1, 0);
        var connData = StorageCacheUtils.getDataContainer(connLoc);
        if (connData == null || connData.isPendingRemove()) {
            reject(e, currentItemId, invalidConnectorMessage);
            return false;
        }

        String connId = connData.getSfId();
        if (connId == null || !connId.toUpperCase(Locale.ROOT).contains("CONNECTOR")) {
            reject(e, currentItemId, invalidConnectorMessage);
            return false;
        }

        var existingData = StorageCacheUtils.getDataContainer(placed);
        if (existingData != null && !existingData.isPendingRemove()) {
            String existingId = existingData.getSfId();
            if (isEnergyAccessory(existingId) && !currentItemId.equals(existingId)) {
                reject(e, currentItemId, conflictMessage);
                return false;
            }
        }

        return true;
    }

    private static boolean isEnergyAccessory(String itemId) {
        return ENERGY_METER_ID.equals(itemId) || CURRENT_LIMITER_ID.equals(itemId);
    }

    private static void reject(@Nonnull BlockPlaceEvent e, @Nonnull String currentItemId, @Nonnull String message) {
        e.setCancelled(true);
        Location placed = e.getBlock().getLocation();
        var placedData = StorageCacheUtils.getDataContainer(placed);
        if (placedData != null && currentItemId.equals(placedData.getSfId())) {
            Slimefun.getDatabaseManager().getBlockDataController().removeBlock(placed);
        }
        e.getPlayer().sendMessage(ChatColors.color(message));
    }
}
