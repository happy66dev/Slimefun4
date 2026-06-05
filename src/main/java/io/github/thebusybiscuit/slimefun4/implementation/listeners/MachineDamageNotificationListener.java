package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import city.norain.slimefun4.utils.WorldNameMapper;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunUniversalBlockData;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.ConnectorAgingManager;
import io.github.thebusybiscuit.slimefun4.core.services.MachineDamageService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class MachineDamageNotificationListener implements Listener {

    /**
     * 获取友好的世界名称
     */
    private String getFriendlyWorldName(String worldName) {
        return WorldNameMapper.getFriendlyName(worldName);
    }

    public MachineDamageNotificationListener(Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        var player = e.getPlayer();
        var playerUUID = player.getUniqueId();
        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        var damageService = Slimefun.getMachineDamageService();
        String playerUUIDString = playerUUID.toString();
        Set<String> checkedData = new HashSet<>();

        for (var data : controller.getAllLoadedData()) {
            if (checkedData.add(getNotificationKey(data))) {
                checkMachineDamage(data, player, playerUUIDString, damageService);
            }
        }

        controller
                .getDamagedDataByOwnerAsync(playerUUIDString)
                .thenAccept(dataList -> Slimefun.runSync(() -> {
                    if (!player.isOnline()) {
                        return;
                    }

                    for (var data : dataList) {
                        if (checkedData.add(getNotificationKey(data))) {
                            checkMachineDamage(data, player, playerUUIDString, damageService);
                        }
                    }
                }))
                .exceptionally(throwable -> {
                    Slimefun.logger()
                            .log(
                                    java.util.logging.Level.WARNING,
                                    "Failed to query damaged machines for login notification",
                                    throwable);
                    return null;
                });
    }

    private void checkMachineDamage(
            ASlimefunDataContainer data, Player player, String playerUUID, MachineDamageService damageService) {
        if (!data.isDataLoaded()) {
            return;
        }

        String ownerUUID = data.getData("machine_owner_uuid");
        if (ownerUUID == null || !ownerUUID.equals(playerUUID)) {
            return;
        }

        if (damageService.isMachineDamaged(data)) {
            SlimefunItem item = getSlimefunItem(data);
            Location location = getLocation(data);

            if (item != null && location != null) {
                var repairItems = damageService.getRepairItems(data);
                if (!repairItems.isEmpty()) {
                    sendMachineDamageNotification(player, item, location, repairItems);
                }
                return;
            }
        }

        SlimefunItem item = getSlimefunItem(data);
        Location location = getLocation(data);
        if (item != null && location != null && isConnectorDamaged(data, location)) {
            sendConnectorDamageNotification(player, item, location);
        }
    }

    private SlimefunItem getSlimefunItem(ASlimefunDataContainer data) {
        if (data instanceof SlimefunBlockData blockData) {
            return SlimefunItem.getById(blockData.getSfId());
        } else if (data instanceof SlimefunUniversalBlockData ubd) {
            return SlimefunItem.getById(ubd.getSfId());
        }
        return null;
    }

    private Location getLocation(ASlimefunDataContainer data) {
        if (data instanceof SlimefunBlockData blockData) {
            return blockData.getLocation();
        } else if (data instanceof SlimefunUniversalBlockData ubd && ubd.getLastPresent() != null) {
            return ubd.getLastPresent().toLocation();
        }
        return null;
    }

    private boolean isConnectorDamaged(ASlimefunDataContainer data, Location location) {
        if (location == null) {
            return false;
        }

        try {
            if ("true".equals(data.getData("connector_damaged"))) {
                return true;
            }
        } catch (IllegalStateException ignored) {
            return false;
        }

        return ConnectorAgingManager.getConfig(location) != null && ConnectorAgingManager.isConnectorDamaged(location);
    }

    private String getNotificationKey(ASlimefunDataContainer data) {
        return Slimefun.getDatabaseManager().getBlockDataController().getContainerKey(data);
    }

    private void sendMachineDamageNotification(
            Player player,
            SlimefunItem item,
            Location location,
            java.util.List<java.util.Map<String, Object>> repairItems) {
        /*
        player.sendMessage("§c你的机器损坏了！");
        player.sendMessage("§c机器名称: §f" + item.getItemName());
        player.sendMessage("§c位置: §f" + formatLocation(location));
        player.sendMessage("§c需要以下修复物品:");
        for (var itemInfo : repairItems) {
            org.bukkit.inventory.ItemStack itemStack = (org.bukkit.inventory.ItemStack) itemInfo.get("item");
            Integer submitted = (Integer) itemInfo.get("submitted");
            Integer required = (Integer) itemInfo.get("required");
            int requiredCount = required != null && required > 0 ? required : 1;
            int submittedCount = submitted != null ? submitted : 0;

            if (itemStack != null) {
                org.bukkit.inventory.meta.ItemMeta meta = itemStack.getItemMeta();
                String itemName = meta != null
                                && meta.hasDisplayName()
                                && !meta.getDisplayName().isEmpty()
                        ? meta.getDisplayName()
                        : city.norain.slimefun4.utils.LocalizationUtils.getItemName(itemStack.getType());

                if (submittedCount >= requiredCount) {
                    player.sendMessage("§a✓ " + itemName + " (已提交: " + submittedCount + "/" + requiredCount + ")");
                } else {
                    player.sendMessage("§c✗ " + itemName + " (需要: " + requiredCount + ", 已提交: " + submittedCount + "/"
                            + requiredCount + ")");
                }
            }
        }
        */
    }

    private void sendConnectorDamageNotification(Player player, SlimefunItem item, Location location) {
        /*
        player.sendMessage("§c你的连接器损坏了！");
        player.sendMessage("§c连接器名称: §f" + item.getItemName());
        player.sendMessage("§c位置: §f" + formatLocation(location));

        String repairItemsDisplay = ConnectorAgingManager.getRepairItemsDisplay(location);
        if (!repairItemsDisplay.isEmpty()) {
            player.sendMessage("§c需要以下修复物品: §f" + repairItemsDisplay);
        }
        */
    }

    private String formatLocation(Location location) {
        String worldName = location.getWorld() != null ? location.getWorld().getName() : "unknown";
        return getFriendlyWorldName(worldName) + " (" + location.getBlockX() + ", " + location.getBlockY() + ", "
                + location.getBlockZ() + ")";
    }
}
