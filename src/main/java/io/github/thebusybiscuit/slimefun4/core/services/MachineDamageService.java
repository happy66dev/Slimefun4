package io.github.thebusybiscuit.slimefun4.core.services;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunMachineDamageManager;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;

public class MachineDamageService {

    private static final String WORK_TICKS_KEY = "machine_damage_work_ticks";
    private static final String DAMAGED_KEY = "machine_damage_damaged";
    private static final String DAMAGE_CHANCE_KEY = "machine_damage_chance";
    private static final String REPAIR_ITEM_KEY = "machine_damage_repair_item";
    private static final String REPAIR_ITEM_TYPE_KEY = "machine_damage_repair_item_type";
    
    /**
     * 获取友好的世界名称
     */
    private String getFriendlyWorldName(String worldName) {
        switch (worldName.toLowerCase()) {
            case "worlds":
                return "主世界";
            case "worlds_nether":
                return "地狱";
            case "worlds_the_end":
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
    
    private final SlimefunMachineDamageManager damageManager;

    public MachineDamageService(@Nonnull Slimefun plugin) {
        this.damageManager = Slimefun.getMachineDamageManager();
    }

    public void start() {
        // 服务启动逻辑
    }

    public void stop() {
        // 服务停止逻辑
    }

    public void processMachineWork(@Nonnull Location location, @Nonnull SlimefunItem item) {
        if (!(item instanceof EnergyNetComponent)) {
            return;
        }

        var data = StorageCacheUtils.getDataContainer(location);
        if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
            return;
        }

        // 检查机器损坏机制是否启用
        var config = damageManager.getMachineConfig(item.getId());
        if (!config.isEnabled()) {
            return;
        }

        // 检查机器是否已经损坏或已停止
        if (isMachineDamaged(data) || "false".equals(data.getData("machine_damage_enabled"))) {
            return;
        }

        // 增加工作刻数
        long workTicks = getWorkTicks(data) + 1;
        data.setData(WORK_TICKS_KEY, String.valueOf(workTicks));

        // 计算当前损坏概率
        double damageChance = calculateDamageRate(workTicks);
        data.setData(DAMAGE_CHANCE_KEY, String.valueOf(damageChance));

        // 检测是否损坏
        if (ThreadLocalRandom.current().nextDouble() < damageChance) {
            if (data instanceof SlimefunBlockData blockData) {
                damageMachine(blockData, location, item);
            }
        }
    }

    private void damageMachine(@Nonnull SlimefunBlockData data, @Nonnull Location location, @Nonnull SlimefunItem item) {
        // 获取合成表并选择修复物品
        ItemStack[] recipe = item.getRecipe();
        if (recipe != null) {
            // 过滤非空物品
            java.util.List<ItemStack> validItems = new java.util.ArrayList<>();
            for (ItemStack stack : recipe) {
                if (stack != null && stack.getType() != org.bukkit.Material.AIR) {
                    validItems.add(stack);
                }
            }
            
            if (!validItems.isEmpty()) {
                // 标记机器为损坏
                data.setData(DAMAGED_KEY, "true");
                
                // 随机选择一个物品
                ItemStack repairItem = validItems.get(ThreadLocalRandom.current().nextInt(validItems.size()));
                
                // 输出修复物品的详细信息
                Slimefun.logger().info("Selected repair item: type=" + repairItem.getType() + ", amount=" + repairItem.getAmount() + ", hasItemMeta=" + (repairItem.getItemMeta() != null));
                if (repairItem.getItemMeta() != null) {
                    Slimefun.logger().info("Repair item display name: " + repairItem.getItemMeta().getDisplayName());
                }
                
                // 检查是否是Slimefun物品
                SlimefunItem slimefunItem = SlimefunItem.getByItem(repairItem);
                if (slimefunItem != null) {
                    // 存储Slimefun物品ID
                    data.setData(REPAIR_ITEM_KEY, slimefunItem.getId());
                    data.setData(REPAIR_ITEM_TYPE_KEY, "slimefun");
                    Slimefun.logger().info("Stored Slimefun repair item: " + slimefunItem.getId());
                } else {
                    // 存储原版物品材质名称
                    data.setData(REPAIR_ITEM_KEY, repairItem.getType().name());
                    data.setData(REPAIR_ITEM_TYPE_KEY, "vanilla");
                    Slimefun.logger().info("Stored vanilla repair item: " + repairItem.getType().name());
                }
                
                // 输出信息
                String itemName;
                if (repairItem.getItemMeta() != null && repairItem.getItemMeta().getDisplayName() != null && !repairItem.getItemMeta().getDisplayName().isEmpty()) {
                    itemName = repairItem.getItemMeta().getDisplayName();
                } else {
                    // 使用物品类型的本地化名称
                    itemName = city.norain.slimefun4.utils.LocalizationUtils.getItemName(repairItem.getType());
                }
                Slimefun.logger().info("Machine damaged: " + item.getId() + " at " + location + ", repair item: " + itemName);
                
                // 在机器上面显示悬浮字
                Location hologramLocation = location.clone().add(0.5, 1.5, 0.5);
                Slimefun.getHologramsService().setHologramLabel(
                    hologramLocation,
                    "§c机器损坏"
                );
                
                // 通知在线的机器主人
                String ownerUUIDStr = data.getData("machine_owner_uuid");
                if (ownerUUIDStr != null) {
                    try {
                        java.util.UUID ownerUUID = java.util.UUID.fromString(ownerUUIDStr);
                        org.bukkit.OfflinePlayer offlinePlayer = org.bukkit.Bukkit.getOfflinePlayer(ownerUUID);
                        if (offlinePlayer != null && offlinePlayer.isOnline()) {
                            org.bukkit.entity.Player player = offlinePlayer.getPlayer();
                            if (player != null) {
                                player.sendMessage("§c你的机器损坏了！");
                                player.sendMessage("§c机器名称: §f" + item.getItemName());
                                player.sendMessage("§c位置: §f" + getFriendlyWorldName(location.getWorld().getName()) + " (" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")");
                                player.sendMessage("§c需要的修复物品: §f" + repairItem.getAmount() + "x " + itemName);
                            }
                        }
                    } catch (Exception e) {
                        Slimefun.logger().info("Error notifying machine owner: " + e.getMessage());
                    }
                }
                
                // 如果配置为损坏后停止工作，则停止机器
                var config = damageManager.getMachineConfig(item.getId());
                if (config.isStopOnDamage()) {
                    // 设置机器为停止状态
                    data.setData("machine_damage_enabled", "false");
                }
            } else {
                // 输出信息
                Slimefun.logger().info("Machine damage skipped: " + item.getId() + " at " + location + " (no valid items in recipe)");
            }
        } else {
            // 输出信息
            Slimefun.logger().info("Machine damage skipped: " + item.getId() + " at " + location + " (no recipe)");
        }
    }

    public boolean isMachineDamaged(@Nonnull ASlimefunDataContainer data) {
        if (!data.isDataLoaded()) {
            return false;
        }
        try {
            String value = data.getData(DAMAGED_KEY);
            return "true".equals(value);
        } catch (IllegalStateException e) {
            // 数据在检查后被卸载，返回false
            return false;
        }
    }

    public long getWorkTicks(@Nonnull ASlimefunDataContainer data) {
        if (!data.isDataLoaded()) {
            return 0L;
        }
        try {
            String value = data.getData(WORK_TICKS_KEY);
            return value != null ? Long.parseLong(value) : 0L;
        } catch (NumberFormatException e) {
            return 0L;
        } catch (IllegalStateException e) {
            // 数据在检查后被卸载，返回默认值
            return 0L;
        }
    }

    public double getDamageChance(@Nonnull ASlimefunDataContainer data) {
        if (!data.isDataLoaded()) {
            return 0.0;
        }
        try {
            String value = data.getData(DAMAGE_CHANCE_KEY);
            return value != null ? Double.parseDouble(value) : 0.0;
        } catch (NumberFormatException e) {
            return 0.0;
        } catch (IllegalStateException e) {
            // 数据在检查后被卸载，返回默认值
            return 0.0;
        }
    }

    public ItemStack getRepairItem(@Nonnull ASlimefunDataContainer data) {
        try {
            if (!data.isDataLoaded()) {
                return null;
            }
            
            String itemId = data.getData(REPAIR_ITEM_KEY);
            String itemType = data.getData(REPAIR_ITEM_TYPE_KEY);
            
            if (itemId == null || itemType == null) {
                return null;
            }
            
            ItemStack repairItem = null;
            if ("slimefun".equals(itemType)) {
                // 获取Slimefun物品
                SlimefunItem slimefunItem = SlimefunItem.getById(itemId);
                if (slimefunItem != null) {
                    repairItem = slimefunItem.getItem();
                    Slimefun.logger().info("Retrieved Slimefun repair item: " + itemId);
                } else {
                    Slimefun.logger().info("Slimefun item not found: " + itemId);
                }
            } else if ("vanilla".equals(itemType)) {
                // 获取原版物品
                try {
                    org.bukkit.Material material = org.bukkit.Material.valueOf(itemId);
                    repairItem = new ItemStack(material);
                    Slimefun.logger().info("Retrieved vanilla repair item: " + itemId);
                } catch (IllegalArgumentException e) {
                    Slimefun.logger().info("Invalid vanilla material: " + itemId);
                }
            }
            
            // 输出信息
            if (repairItem != null) {
                try {
                    String itemName = repairItem.getItemMeta() != null && repairItem.getItemMeta().getDisplayName() != null && !repairItem.getItemMeta().getDisplayName().isEmpty() ? repairItem.getItemMeta().getDisplayName() : city.norain.slimefun4.utils.LocalizationUtils.getItemName(repairItem.getType());
                    Slimefun.logger().info("Retrieved repair item: " + itemName);
                } catch (Exception e) {
                    Slimefun.logger().info("Error getting repair item name: " + e.getMessage());
                }
            }
            return repairItem;
        } catch (Exception e) {
            // 任何异常都返回null
            Slimefun.logger().info("Error getting repair item: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void repairMachine(@Nonnull SlimefunBlockData data) {
        if (!data.isDataLoaded()) {
            return;
        }
        try {
            // 重置机器状态
            data.setData(DAMAGED_KEY, "false");
            data.setData(WORK_TICKS_KEY, "0");
            data.setData(DAMAGE_CHANCE_KEY, "0.0");
            data.setData("machine_damage_enabled", "true");
            data.setData("machine_damage_charge_counter", "0.0");
            data.removeData(REPAIR_ITEM_KEY);
            data.removeData(REPAIR_ITEM_TYPE_KEY);
            
            // 输出信息
            if (data.getLocation() != null) {
                Slimefun.logger().info("Machine repaired: " + data.getSfId() + " at " + data.getLocation());
            }
            
            // 移除全息图
            if (data.getLocation() != null) {
                Location hologramLocation = data.getLocation().clone().add(0, 1.5, 0);
                Slimefun.getHologramsService().removeHologram(hologramLocation);
            }
        } catch (IllegalStateException e) {
            // 数据在检查后被卸载，忽略操作
            Slimefun.logger().info("Machine repair skipped: data unloaded for " + (data.getLocation() != null ? data.getLocation() : "unknown location"));
        }
    }

    public void repairMachine(@Nonnull Location location) {
        var data = StorageCacheUtils.getDataContainer(location);
        if (data instanceof SlimefunBlockData blockData) {
            repairMachine(blockData);
        } else {
            Slimefun.logger().info("Machine repair failed: no block data found at " + location);
        }
    }

    public void scrapMachine(@Nonnull Location location, @Nonnull SlimefunItem item) {
        var data = StorageCacheUtils.getDataContainer(location);
        if (data instanceof SlimefunBlockData blockData) {
            damageMachine(blockData, location, item);
        }
    }

    /** 
      * 计算机器在连续工作指定粘液刻数后的损坏概率（每个粘液刻独立判定）。 
      * 公式设计目标：平均工作寿命约为 500,000 粘液刻。 
      * 
      * @param continuousTicks 当前连续工作的粘液刻数（从1开始） 
      * @return 损坏概率（介于 0 到 1 之间的 double 值） 
      */ 
     public static double calculateDamageRate(long continuousTicks) { 
         // 常量定义 
         final double A = 7.5e-6;          // 最大损坏率渐近值 
         final double B = 2.5e11;          // 半饱和参数，控制曲线形状 
         
         // 将 ticks 转换为 double 进行计算，避免整数溢出 
         double ticks = (double) continuousTicks; 
         
         // 应用公式：rate = A * (ticks^2) / (B + ticks^2) 
         double ticksSquared = ticks * ticks; 
         double rate = A * ticksSquared / (B + ticksSquared); 
         
         return rate; 
     } 

    public String getMachineInfo(@Nonnull Location location, @Nonnull SlimefunItem item) {
        var data = StorageCacheUtils.getDataContainer(location);
        if (data == null) {
            return "§c无法获取机器数据！";
        }

        long workTicks = getWorkTicks(data);
        double damageChance = getDamageChance(data);
        boolean isDamaged = isMachineDamaged(data);
        boolean isEnabled = true;
        try {
            if (data.isDataLoaded()) {
                isEnabled = !"false".equals(data.getData("machine_damage_enabled"));
            }
        } catch (IllegalStateException e) {
            // 数据在检查后被卸载，使用默认值
            isEnabled = true;
        }

        var config = damageManager.getMachineConfig(item.getId());
        boolean enabled = config.isEnabled();

        StringBuilder info = new StringBuilder();
        info.append("§a机器名称: §f").append(item.getItemName()).append("\n");
        info.append("§a工作刻数: §f").append(workTicks).append("\n");
        info.append("§a当前报废几率: §f").append(String.format("%.10f%%", damageChance * 100)).append("\n");
        info.append("§a状态: §f").append(isDamaged ? "已损坏" : (isEnabled ? "正常运行" : "已停止")).append("\n");
        info.append("§a损坏机制: §f").append(enabled ? "启用" : "禁用").append("\n");

        // 添加修复物品信息
        if (isDamaged) {
            ItemStack repairItem = getRepairItem(data);
            if (repairItem != null) {
                String itemName = repairItem.getItemMeta() != null && repairItem.getItemMeta().getDisplayName() != null && !repairItem.getItemMeta().getDisplayName().isEmpty() ? repairItem.getItemMeta().getDisplayName() : city.norain.slimefun4.utils.LocalizationUtils.getItemName(repairItem.getType());
                info.append("§a需要的修复物品: §f").append(repairItem.getAmount()).append("x " ).append(itemName).append("\n");
            }
        }

        return info.toString();
    }
}
