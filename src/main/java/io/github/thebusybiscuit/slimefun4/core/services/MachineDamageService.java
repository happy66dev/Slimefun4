package io.github.thebusybiscuit.slimefun4.core.services;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunMachineDamageManager;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

public class MachineDamageService {

    private static final String WORK_TICKS_KEY = "machine_damage_work_ticks";
    private static final String DAMAGED_KEY = "machine_damage_damaged";
    private static final String DAMAGE_CHANCE_KEY = "machine_damage_chance";
    private static final String REPAIR_ITEMS_KEY = "machine_damage_repair_items";
    private static final String SUBMITTED_ITEMS_KEY = "machine_damage_submitted_items";
    private static final String REPAIR_ITEM_COUNT_KEY = "machine_damage_repair_item_count";

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

    private void damageMachine(
            @Nonnull SlimefunBlockData data, @Nonnull Location location, @Nonnull SlimefunItem item) {
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

                // 计算需要的修复物品数量：非空区域的格子数量的30%，存在小数时进一位
                int nonEmptySlots = validItems.size();
                int repairItemCount = (int) Math.ceil(nonEmptySlots * 0.3);

                // 随机抽取修复物品
                java.util.List<java.util.Map<String, String>> repairItemsList = new java.util.ArrayList<>();
                for (int i = 0; i < repairItemCount; i++) {
                    // 随机选择一个物品
                    ItemStack repairItem =
                            validItems.get(ThreadLocalRandom.current().nextInt(validItems.size()));

                    // 检查是否是Slimefun物品
                    SlimefunItem slimefunItem = SlimefunItem.getByItem(repairItem);
                    java.util.Map<String, String> itemData = new java.util.HashMap<>();

                    if (slimefunItem != null) {
                        itemData.put("id", slimefunItem.getId());
                        itemData.put("type", "slimefun");
                    } else {
                        itemData.put("id", repairItem.getType().name());
                        itemData.put("type", "vanilla");
                    }
                    repairItemsList.add(itemData);
                }

                // 存储修复物品列表（JSON格式）
                String repairItemsJson = new com.google.gson.Gson().toJson(repairItemsList);
                data.setData(REPAIR_ITEMS_KEY, repairItemsJson);
                data.setData(REPAIR_ITEM_COUNT_KEY, String.valueOf(repairItemsList.size()));

                // 初始化已提交物品（空JSON数组）
                data.setData(SUBMITTED_ITEMS_KEY, "[]");

                // 输出修复物品的详细信息
                Slimefun.logger().info("Selected repair items count: " + repairItemsList.size());
                for (java.util.Map<String, String> itemData : repairItemsList) {
                    Slimefun.logger().info("Repair item: id=" + itemData.get("id") + ", type=" + itemData.get("type"));
                }

                // 输出信息
                Slimefun.logger()
                        .info("Machine damaged: " + item.getId() + " at " + location + ", need "
                                + repairItemsList.size() + " items to repair");

                // 在机器上面显示悬浮字
                Location hologramLocation = location.clone().add(0.5, 1.5, 0.5);
                Slimefun.getHologramsService().setHologramLabel(hologramLocation, "§c机器损坏");

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
                                player.sendMessage("§c位置: §f"
                                        + getFriendlyWorldName(
                                                location.getWorld().getName()) + " (" + location.getBlockX() + ", "
                                        + location.getBlockY() + ", " + location.getBlockZ() + ")");
                                player.sendMessage("§c需要 " + repairItemsList.size() + " 种修复物品（随机抽取）");
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
                Slimefun.logger()
                        .info("Machine damage skipped: " + item.getId() + " at " + location
                                + " (no valid items in recipe)");
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

            String repairItemsJson = data.getData(REPAIR_ITEMS_KEY);
            if (repairItemsJson == null || repairItemsJson.isEmpty()) {
                return null;
            }

            java.util.List<java.util.Map<String, String>> repairItemsList = new com.google.gson.Gson()
                    .fromJson(
                            repairItemsJson,
                            new com.google.gson.reflect.TypeToken<
                                    java.util.List<java.util.Map<String, String>>>() {}.getType());

            if (repairItemsList == null || repairItemsList.isEmpty()) {
                return null;
            }

            java.util.Map<String, String> firstItemData = repairItemsList.get(0);
            String itemId = firstItemData.get("id");
            String itemType = firstItemData.get("type");

            ItemStack repairItem = null;
            if ("slimefun".equals(itemType)) {
                SlimefunItem slimefunItem = SlimefunItem.getById(itemId);
                if (slimefunItem != null) {
                    repairItem = slimefunItem.getItem();
                    Slimefun.logger().info("Retrieved Slimefun repair item: " + itemId);
                } else {
                    Slimefun.logger().info("Slimefun item not found: " + itemId);
                }
            } else if ("vanilla".equals(itemType)) {
                try {
                    org.bukkit.Material material = org.bukkit.Material.valueOf(itemId);
                    repairItem = new ItemStack(material);
                    Slimefun.logger().info("Retrieved vanilla repair item: " + itemId);
                } catch (IllegalArgumentException e) {
                    Slimefun.logger().info("Invalid vanilla material: " + itemId);
                }
            }

            if (repairItem != null) {
                try {
                    String itemName = repairItem.getItemMeta() != null
                                    && repairItem.getItemMeta().getDisplayName() != null
                                    && !repairItem
                                            .getItemMeta()
                                            .getDisplayName()
                                            .isEmpty()
                            ? repairItem.getItemMeta().getDisplayName()
                            : city.norain.slimefun4.utils.LocalizationUtils.getItemName(repairItem.getType());
                    Slimefun.logger().info("Retrieved repair item: " + itemName);
                } catch (Exception e) {
                    Slimefun.logger().info("Error getting repair item name: " + e.getMessage());
                }
            }
            return repairItem;
        } catch (Exception e) {
            Slimefun.logger().info("Error getting repair item: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public java.util.List<java.util.Map<String, Object>> getRepairItems(@Nonnull ASlimefunDataContainer data) {
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();

        try {
            if (!data.isDataLoaded()) {
                return result;
            }

            String repairItemsJson = data.getData(REPAIR_ITEMS_KEY);
            String submittedItemsJson = data.getData(SUBMITTED_ITEMS_KEY);

            if (repairItemsJson == null || repairItemsJson.isEmpty()) {
                return result;
            }

            java.util.List<java.util.Map<String, String>> repairItemsList = new com.google.gson.Gson()
                    .fromJson(
                            repairItemsJson,
                            new com.google.gson.reflect.TypeToken<
                                    java.util.List<java.util.Map<String, String>>>() {}.getType());

            java.util.Map<String, Integer> submittedItemsMap = new java.util.HashMap<>();
            if (submittedItemsJson != null && !submittedItemsJson.isEmpty()) {
                java.util.Map<String, Number> tempMap = new com.google.gson.Gson()
                        .fromJson(
                                submittedItemsJson,
                                new com.google.gson.reflect.TypeToken<java.util.Map<String, Number>>() {}.getType());
                for (java.util.Map.Entry<String, Number> entry : tempMap.entrySet()) {
                    submittedItemsMap.put(entry.getKey(), entry.getValue().intValue());
                }
            }

            if (repairItemsList != null) {
                for (java.util.Map<String, String> itemData : repairItemsList) {
                    java.util.Map<String, Object> itemInfo = new java.util.HashMap<>();
                    String itemId = itemData.get("id");
                    String itemType = itemData.get("type");

                    ItemStack repairItem = null;
                    if ("slimefun".equals(itemType)) {
                        SlimefunItem slimefunItem = SlimefunItem.getById(itemId);
                        if (slimefunItem != null) {
                            repairItem = slimefunItem.getItem();
                        }
                    } else if ("vanilla".equals(itemType)) {
                        try {
                            org.bukkit.Material material = org.bukkit.Material.valueOf(itemId);
                            repairItem = new ItemStack(material);
                        } catch (IllegalArgumentException e) {
                            // ignore
                        }
                    }

                    itemInfo.put("id", itemId);
                    itemInfo.put("type", itemType);
                    itemInfo.put("item", repairItem);
                    itemInfo.put("submitted", submittedItemsMap.getOrDefault(itemId, 0));
                    result.add(itemInfo);
                }
            }
        } catch (Exception e) {
            Slimefun.logger().info("Error getting repair items: " + e.getMessage());
            e.printStackTrace();
        }

        return result;
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
            data.removeData(REPAIR_ITEMS_KEY);
            data.removeData(REPAIR_ITEM_COUNT_KEY);
            data.removeData(SUBMITTED_ITEMS_KEY);

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
            Slimefun.logger()
                    .info("Machine repair skipped: data unloaded for "
                            + (data.getLocation() != null ? data.getLocation() : "unknown location"));
        }
    }

    public boolean trySubmitRepairItem(@Nonnull SlimefunBlockData data, @Nonnull ItemStack item) {
        if (!data.isDataLoaded() || !isMachineDamaged(data)) {
            return false;
        }

        try {
            String repairItemsJson = data.getData(REPAIR_ITEMS_KEY);
            String submittedItemsJson = data.getData(SUBMITTED_ITEMS_KEY);

            if (repairItemsJson == null || repairItemsJson.isEmpty()) {
                return false;
            }

            java.util.List<java.util.Map<String, String>> repairItemsList = new com.google.gson.Gson()
                    .fromJson(
                            repairItemsJson,
                            new com.google.gson.reflect.TypeToken<
                                    java.util.List<java.util.Map<String, String>>>() {}.getType());

            java.util.Map<String, Integer> submittedItemsMap = new java.util.HashMap<>();
            if (submittedItemsJson != null && !submittedItemsJson.isEmpty()) {
                java.util.Map<String, Number> tempMap = new com.google.gson.Gson()
                        .fromJson(
                                submittedItemsJson,
                                new com.google.gson.reflect.TypeToken<java.util.Map<String, Number>>() {}.getType());
                for (java.util.Map.Entry<String, Number> entry : tempMap.entrySet()) {
                    submittedItemsMap.put(entry.getKey(), entry.getValue().intValue());
                }
            }

            SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
            String itemId =
                    slimefunItem != null ? slimefunItem.getId() : item.getType().name();
            String itemType = slimefunItem != null ? "slimefun" : "vanilla";

            for (java.util.Map<String, String> repairItemData : repairItemsList) {
                String repairId = repairItemData.get("id");
                String repairType = repairItemData.get("type");

                if (repairId.equals(itemId) && repairType.equals(itemType)) {
                    int currentSubmitted = submittedItemsMap.getOrDefault(itemId, 0);
                    currentSubmitted++;
                    submittedItemsMap.put(itemId, currentSubmitted);

                    String newSubmittedJson = new com.google.gson.Gson().toJson(submittedItemsMap);
                    data.setData(SUBMITTED_ITEMS_KEY, newSubmittedJson);

                    Slimefun.logger().info("Submitted repair item: " + itemId + ", count: " + currentSubmitted);

                    return true;
                }
            }
        } catch (Exception e) {
            Slimefun.logger().info("Error submitting repair item: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    public boolean canRepair(@Nonnull ASlimefunDataContainer data) {
        if (!data.isDataLoaded() || !isMachineDamaged(data)) {
            return false;
        }

        try {
            String repairItemsJson = data.getData(REPAIR_ITEMS_KEY);
            String submittedItemsJson = data.getData(SUBMITTED_ITEMS_KEY);

            if (repairItemsJson == null || repairItemsJson.isEmpty()) {
                return false;
            }

            java.util.List<java.util.Map<String, String>> repairItemsList = new com.google.gson.Gson()
                    .fromJson(
                            repairItemsJson,
                            new com.google.gson.reflect.TypeToken<
                                    java.util.List<java.util.Map<String, String>>>() {}.getType());

            java.util.Map<String, Integer> submittedItemsMap = new java.util.HashMap<>();
            if (submittedItemsJson != null && !submittedItemsJson.isEmpty()) {
                java.util.Map<String, Number> tempMap = new com.google.gson.Gson()
                        .fromJson(
                                submittedItemsJson,
                                new com.google.gson.reflect.TypeToken<java.util.Map<String, Number>>() {}.getType());
                for (java.util.Map.Entry<String, Number> entry : tempMap.entrySet()) {
                    submittedItemsMap.put(entry.getKey(), entry.getValue().intValue());
                }
            }

            if (repairItemsList == null) {
                return false;
            }

            for (java.util.Map<String, String> repairItemData : repairItemsList) {
                String repairId = repairItemData.get("id");
                int submitted = submittedItemsMap.getOrDefault(repairId, 0);
                if (submitted == 0) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            Slimefun.logger().info("Error checking if can repair: " + e.getMessage());
            e.printStackTrace();
            return false;
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
        final double A = 7.5e-6; // 最大损坏率渐近值
        final double B = 2.5e11; // 半饱和参数，控制曲线形状

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
        info.append("§a当前报废几率: §f")
                .append(String.format("%.10f%%", damageChance * 100))
                .append("\n");
        info.append("§a状态: §f")
                .append(isDamaged ? "已损坏" : (isEnabled ? "正常运行" : "已停止"))
                .append("\n");
        info.append("§a损坏机制: §f").append(enabled ? "启用" : "禁用").append("\n");

        // 添加修复物品信息
        if (isDamaged) {
            java.util.List<java.util.Map<String, Object>> repairItemsList = getRepairItems(data);
            if (!repairItemsList.isEmpty()) {
                info.append("§c需要以下修复物品:\n");
                for (java.util.Map<String, Object> itemInfo : repairItemsList) {
                    ItemStack repairItem = (ItemStack) itemInfo.get("item");
                    Integer submitted = (Integer) itemInfo.get("submitted");

                    if (repairItem != null) {
                        String itemName = repairItem.getItemMeta() != null
                                        && repairItem.getItemMeta().getDisplayName() != null
                                        && !repairItem
                                                .getItemMeta()
                                                .getDisplayName()
                                                .isEmpty()
                                ? repairItem.getItemMeta().getDisplayName()
                                : city.norain.slimefun4.utils.LocalizationUtils.getItemName(repairItem.getType());

                        if (submitted != null && submitted > 0) {
                            info.append("§a✓ ")
                                    .append(itemName)
                                    .append(" (已提交: ")
                                    .append(submitted)
                                    .append("/1)\n");
                        } else {
                            info.append("§c✗ ").append(itemName).append(" (需要: 1)\n");
                        }
                    }
                }
            }
        }

        return info.toString();
    }
}
