package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.MachineDamageService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class MachineDamageNotificationListener implements Listener {

    /**
     * 获取友好的世界名称
     */
    private String getFriendlyWorldName(String worldName) {
        switch (worldName.toLowerCase()) {
            case "world":
                return "主世界";
            case "world_nether":
                return "地狱";
            case "world_the_end":
                return "末地";
            case "world_galactifun_earth_orbit":
                return "地球轨道";
            case "world_galactifun_enceladus":
                return "土卫二";
            case "world_galactifun_europa":
                return "木卫二";
            case "world_galactifun_io":
                return "木卫一";
            case "world_galactifun_mars":
                return "火星";
            case "world_galactifun_the_moon":
                return "月球";
            case "world_galactifun_titan":
                return "土卫六";
            case "world_galactifun_venus":
                return "金星";
            default:
                return worldName;
        }
    }

    public MachineDamageNotificationListener(Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        var player = e.getPlayer();
        var playerUUID = player.getUniqueId().toString();
        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        var damageService = Slimefun.getMachineDamageService();

        // 检查所有已加载的区块数据
        for (var chunkData : controller.getAllLoadedChunkData()) {
            for (var blockData : chunkData.getAllBlockData()) {
                checkMachineDamage(blockData, player, playerUUID, damageService);
            }
        }

        // 检查所有通用方块数据
        // 由于 loadedUniversalData 是私有字段，我们需要通过其他方式获取通用方块数据
        // 暂时注释掉这部分，因为需要修改 BlockDataController 来提供访问方法
        // for (var universalData : controller.loadedUniversalData.values()) {
        //     if (universalData instanceof com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunUniversalBlockData ubd) {
        //         checkMachineDamage(ubd, player, playerUUID, damageService);
        //     }
        // }
    }

    private void checkMachineDamage(ASlimefunDataContainer data, org.bukkit.entity.Player player, String playerUUID, MachineDamageService damageService) {
        if (!data.isDataLoaded()) {
            return;
        }

        // 检查是否是该玩家的机器
        String ownerUUID = data.getData("machine_owner_uuid");
        if (ownerUUID == null || !ownerUUID.equals(playerUUID)) {
            return;
        }

        // 检查机器是否损坏
        if (damageService.isMachineDamaged(data)) {
            // 获取机器信息
            SlimefunItem item = null;
            org.bukkit.Location location = null;

            if (data instanceof SlimefunBlockData blockData) {
                item = SlimefunItem.getById(blockData.getSfId());
                location = blockData.getLocation();
            } else if (data instanceof com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunUniversalBlockData ubd) {
                item = SlimefunItem.getById(ubd.getSfId());
                if (ubd.getLastPresent() != null) {
                    location = ubd.getLastPresent().toLocation();
                }
            }

            if (item != null && location != null) {
                // 获取修复物品
                var repairItem = damageService.getRepairItem(data);
                if (repairItem != null) {
                    String itemName = repairItem.getItemMeta() != null && repairItem.getItemMeta().getDisplayName() != null && !repairItem.getItemMeta().getDisplayName().isEmpty() ? repairItem.getItemMeta().getDisplayName() : city.norain.slimefun4.utils.LocalizationUtils.getItemName(repairItem.getType());
                    player.sendMessage("§c你的机器损坏了！");
                    player.sendMessage("§c机器名称: §f" + item.getItemName());
                    player.sendMessage("§c位置: §f" + getFriendlyWorldName(location.getWorld().getName()) + " (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")");
                    player.sendMessage("§c需要的修复物品: §f" + repairItem.getAmount() + "x " + itemName);
                }
            }
        }
    }
}