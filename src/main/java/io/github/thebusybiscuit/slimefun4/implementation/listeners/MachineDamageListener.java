package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import city.norain.slimefun4.utils.LocalizationUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.MachineDamageService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Result;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

@ParametersAreNonnullByDefault
public class MachineDamageListener implements Listener {

    public MachineDamageListener(Slimefun plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player player) {
            updateInventoryItems(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player player) {
            updateInventoryItems(player);
        }
    }

    @EventHandler
    public void onPlayerItemHeld(PlayerItemHeldEvent e) {
        updateInventoryItems(e.getPlayer());
    }

    private void updateInventoryItems(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && !item.getType().isAir()) {
                updateItemLore(item);
            }
        }
    }

    private void updateItemLore(ItemStack item) {
        // 物品lore显示损坏机制的功能已被注释化
        /*
        SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
        if (slimefunItem instanceof EnergyNetComponent component) {

            SlimefunMachineDamageManager damageManager = Slimefun.getMachineDamageManager();
            var config = damageManager.getMachineConfig(slimefunItem.getId());
            boolean enabled = config.isEnabled();
            double scale = config.getDamageChanceScale();
            double denominator = config.getDamageChanceExponent();

            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                // 检查是否已经存在损坏相关的lore
                boolean hasDamageLore = false;
                if (meta.hasLore()) {
                    for (String line : meta.getLore()) {
                        if (line.contains("损坏几率") || line.contains("损坏机制") || line.contains("缩放倍率") || line.contains("增长指数") || line.contains("增长公式") || line.contains("损坏上限") || line.contains("缓冲参数B") || line.contains("损坏公式")) {
                            hasDamageLore = true;
                            break;
                        }
                    }
                }

                // 如果已经存在损坏相关的lore，则不做修改
                if (!hasDamageLore) {
                    List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                    // Add damage chance lore
                    lore.add(ChatColor.GRAY + "损坏机制: " + (enabled ? ChatColor.GREEN + "启用" : ChatColor.RED + "禁用"));
                    if (enabled) {
                        lore.add(ChatColor.GRAY + "损坏上限: " + ChatColor.RED + String.format("%.8f", scale));
                        lore.add(ChatColor.GRAY + "缓冲参数B: " + ChatColor.RED + String.format("%.2f", denominator));
                        lore.add(ChatColor.GRAY + "损坏公式: A × t² / (B + t²)");
                    }

                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
        }
        */
    }

    private final java.util.Set<String> interactedBlocks = new java.util.HashSet<>();

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK && e.getHand() == EquipmentSlot.HAND) {
            Player player = e.getPlayer();
            Location location = e.getClickedBlock().getLocation();
            String locationKey = location.getWorld().getName() + "_" + location.getBlockX() + "_" + location.getBlockY()
                    + "_" + location.getBlockZ();

            SlimefunItem slimefunItem =
                    com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils.getSlimefunItem(location);

            if (slimefunItem != null) {
                MachineDamageService damageService = Slimefun.getMachineDamageService();
                var data =
                        com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils.getDataContainer(location);

                if (data != null && data.isDataLoaded() && damageService.isMachineDamaged(data)) {
                    e.setUseInteractedBlock(Result.DENY);
                    e.setUseItemInHand(Result.DENY);
                    e.setCancelled(true);

                    java.util.List<java.util.Map<String, Object>> repairItems = damageService.getRepairItems(data);
                    ItemStack heldItem = player.getInventory().getItemInMainHand();

                    if (interactedBlocks.contains(locationKey)) {
                        return;
                    }

                    interactedBlocks.add(locationKey);
                    org.bukkit.Bukkit.getScheduler()
                            .runTaskLater(
                                    Slimefun.instance(),
                                    () -> {
                                        interactedBlocks.remove(locationKey);
                                    },
                                    6L);

                    if (!repairItems.isEmpty()) {
                        if (heldItem != null && heldItem.getType() != Material.AIR) {
                            if (damageService.trySubmitRepairItem(
                                    (com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData) data,
                                    heldItem)) {
                                heldItem.setAmount(heldItem.getAmount() - 1);

                                if (damageService.canRepair(data)) {
                                    damageService.repairMachine(location);
                                    BlockListener.clearDamagedMachineBreakAttempts(location);
                                    player.sendMessage(ChatColor.GREEN + "机器已成功修复！");
                                } else {
                                    player.sendMessage(ChatColor.YELLOW + "已提交修复物品，继续提交其他需要的物品...");
                                    displayRepairItems(player, damageService, data);
                                }
                            } else {
                                displayRepairItems(player, damageService, data);
                            }
                        } else {
                            displayRepairItems(player, damageService, data);
                        }
                    }
                }
            }
        }
    }

    private void displayRepairItems(
            Player player,
            MachineDamageService damageService,
            com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer data) {
        java.util.List<java.util.Map<String, Object>> repairItems = damageService.getRepairItems(data);
        if (repairItems.isEmpty()) {
            return;
        }

        player.sendMessage(ChatColor.RED + "此机器需要以下修复物品:");
        for (java.util.Map<String, Object> itemInfo : repairItems) {
            ItemStack item = (ItemStack) itemInfo.get("item");
            Integer submitted = (Integer) itemInfo.get("submitted");
            Integer required = (Integer) itemInfo.get("required");
            int requiredCount = required != null && required > 0 ? required : 1;
            int submittedCount = submitted != null ? submitted : 0;

            if (item != null) {
                String itemName;
                if (item.getItemMeta() != null
                        && item.getItemMeta().getDisplayName() != null
                        && !item.getItemMeta().getDisplayName().isEmpty()) {
                    itemName = item.getItemMeta().getDisplayName();
                } else {
                    itemName = LocalizationUtils.getItemName(item.getType());
                }

                if (submittedCount >= requiredCount) {
                    player.sendMessage(ChatColor.GREEN
                            + "✓ "
                            + itemName
                            + ChatColor.GRAY
                            + " (已提交: "
                            + submittedCount
                            + "/"
                            + requiredCount
                            + ")");
                } else {
                    player.sendMessage(ChatColor.RED
                            + "✗ "
                            + itemName
                            + ChatColor.GRAY
                            + " (需要: "
                            + requiredCount
                            + ", 已提交: "
                            + submittedCount
                            + "/"
                            + requiredCount
                            + ")");
                }
            }
        }
    }
}
