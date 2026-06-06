// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy <k666kkk666k@163.com>
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
package io.github.thebusybiscuit.slimefun4.core.services;

import city.norain.slimefun4.utils.LocalizationUtils;
import city.norain.slimefun4.utils.WorldNameMapper;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunMachineDamageManager;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.ConnectorAgingManager;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;

public class MachineDamageService {

    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();
    private static final String WORK_TICKS_KEY = "machine_damage_work_ticks";
    private static final String DAMAGED_KEY = "machine_damage_damaged";
    private static final String DAMAGE_CHANCE_KEY = "machine_damage_chance";
    private static final String REPAIR_ITEMS_KEY = "machine_damage_repair_items";
    private static final String SUBMITTED_ITEMS_KEY = "machine_damage_submitted_items";
    private static final String REPAIR_ITEM_COUNT_KEY = "machine_damage_repair_item_count";

    private static final Set<String> AGING_BLACKLIST = Set.of("CARGO_MANAGER");

    private final Map<UUID, Set<Location>> damagedBlockIndex = new ConcurrentHashMap<>();

    /**
     * 获取友好的世界名称
     */
    private String getFriendlyWorldName(String worldName) {
        return WorldNameMapper.getFriendlyName(worldName);
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

        if (AGING_BLACKLIST.contains(item.getId())) {
            return;
        }

        var data = StorageCacheUtils.getDataContainer(location);
        if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
            return;
        }

        // 检查机器损坏机制是否启用
        if (damageManager == null) {
            return;
        }
        var config = damageManager.getMachineConfig(item.getId());
        if (!config.isEnabled()) {
            return;
        }

        // 检查机器是否已经损坏
        if (isMachineDamaged(data)) {
            return;
        }

        // 增加工作刻数
        long workTicks = getWorkTicks(data) + 1;
        data.setData(WORK_TICKS_KEY, String.valueOf(workTicks));

        // 计算当前损坏概率
        double damageChance = calculateDamageRate(workTicks, config);
        data.setData(DAMAGE_CHANCE_KEY, String.valueOf(damageChance));

        // 检测是否损坏
        if (!Double.isNaN(damageChance)
                && damageChance > 0.0
                && ThreadLocalRandom.current().nextDouble() < damageChance) {
            if (data instanceof SlimefunBlockData blockData) {
                damageMachine(blockData, location, item);
            }
        }
    }

    private void damageMachine(
            @Nonnull SlimefunBlockData data, @Nonnull Location location, @Nonnull SlimefunItem item) {
        if (item instanceof EnergyNetComponent
                && ((EnergyNetComponent) item).getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
            ConnectorAgingManager.setDurability(location, 0f);
            EnergyNet net = EnergyNet.getNetworkFromLocation(location);
            if (net != null) {
                net.markDirty(location);
            }
            return;
        }
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
                if (location.getWorld() != null) {
                    location.getWorld().playSound(location, Sound.BLOCK_ANVIL_DESTROY, 1.0f, 1.0f);
                }

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
                String repairItemsJson = GSON.toJson(repairItemsList);
                data.setData(REPAIR_ITEMS_KEY, repairItemsJson);
                data.setData(REPAIR_ITEM_COUNT_KEY, String.valueOf(repairItemsList.size()));

                // 初始化已提交物品（空JSON对象）
                data.setData(SUBMITTED_ITEMS_KEY, "{}");

                // 在机器上面显示悬浮字
                Location hologramLocation = getDamageHologramLocation(location);
                Slimefun.getHologramsService().setHologramLabel(hologramLocation, "§c机器损坏");

                // 通知在线的机器主人
                String ownerUUIDStr = data.getData("machine_owner_uuid");
                if (ownerUUIDStr != null) {
                    try {
                        java.util.UUID ownerUUID = java.util.UUID.fromString(ownerUUIDStr);
                        markDamaged(location, ownerUUID);
                        /*
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
                        */
                    } catch (Exception e) {
                        Slimefun.logger().info("Error notifying machine owner: " + e.getMessage());
                    }
                }

                // 机器损坏后自动停止工作
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

            java.util.List<java.util.Map<String, String>> repairItemsList = GSON.fromJson(
                    repairItemsJson,
                    new com.google.gson.reflect.TypeToken<
                            java.util.List<java.util.Map<String, String>>>() {}.getType());

            java.util.Map<String, RepairRequirement> requirements = aggregateRepairRequirements(repairItemsList);
            if (requirements.isEmpty()) {
                return null;
            }

            RepairRequirement firstRequirement =
                    requirements.values().iterator().next();
            ItemStack repairItem = firstRequirement.item;
            String itemId = firstRequirement.id;
            String itemType = firstRequirement.type;

            if (repairItem != null) {
                try {
                    org.bukkit.inventory.meta.ItemMeta meta = repairItem.getItemMeta();
                    String itemName = meta != null
                                    && meta.getDisplayName() != null
                                    && !meta.getDisplayName().isEmpty()
                            ? meta.getDisplayName()
                            : city.norain.slimefun4.utils.LocalizationUtils.getItemName(repairItem.getType());
                    Slimefun.logger().info("Retrieved repair item: " + itemName);
                } catch (Exception e) {
                    Slimefun.logger().info("Error getting repair item name: " + e.getMessage());
                }
            }
            return repairItem;
        } catch (Exception e) {
            Slimefun.logger().log(java.util.logging.Level.WARNING, "Error getting repair item", e);
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

            java.util.List<java.util.Map<String, String>> repairItemsList = GSON.fromJson(
                    repairItemsJson,
                    new com.google.gson.reflect.TypeToken<
                            java.util.List<java.util.Map<String, String>>>() {}.getType());

            java.util.Map<String, RepairRequirement> requirements = aggregateRepairRequirements(repairItemsList);
            java.util.Map<String, Integer> submittedItemsMap = readSubmittedCounts(submittedItemsJson);

            for (RepairRequirement requirement : requirements.values()) {
                java.util.Map<String, Object> itemInfo = new java.util.HashMap<>();
                itemInfo.put("id", requirement.id);
                itemInfo.put("type", requirement.type);
                itemInfo.put("item", requirement.item);
                itemInfo.put("required", requirement.requiredCount);
                itemInfo.put("submitted", getSubmittedCount(submittedItemsMap, requirement));
                result.add(itemInfo);
            }
        } catch (Exception e) {
            Slimefun.logger().log(java.util.logging.Level.WARNING, "Error getting repair items", e);
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
            data.removeData(REPAIR_ITEMS_KEY);
            data.removeData(REPAIR_ITEM_COUNT_KEY);
            data.removeData(SUBMITTED_ITEMS_KEY);

            // 输出信息
            if (data.getLocation() != null) {
                markRepaired(data.getLocation());
                Slimefun.logger().info("Machine repaired: " + data.getSfId() + " at " + data.getLocation());
            }

            // 移除全息图
            if (data.getLocation() != null) {
                Location hologramLocation = getDamageHologramLocation(data.getLocation());
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

            java.util.List<java.util.Map<String, String>> repairItemsList = GSON.fromJson(
                    repairItemsJson,
                    new com.google.gson.reflect.TypeToken<
                            java.util.List<java.util.Map<String, String>>>() {}.getType());

            java.util.Map<String, RepairRequirement> requirements = aggregateRepairRequirements(repairItemsList);
            java.util.Map<String, Integer> submittedItemsMap = readSubmittedCounts(submittedItemsJson);

            SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
            String itemId =
                    slimefunItem != null ? slimefunItem.getId() : item.getType().name();
            String itemType = slimefunItem != null ? "slimefun" : "vanilla";
            String repairKey = repairKey(itemType, itemId);

            RepairRequirement requirement = requirements.get(repairKey);
            if (requirement != null) {
                int currentSubmitted = getSubmittedCount(submittedItemsMap, requirement);
                if (currentSubmitted >= requirement.requiredCount) {
                    return false;
                }

                submittedItemsMap.put(repairKey, currentSubmitted + 1);
                submittedItemsMap.remove(itemId);

                String newSubmittedJson = GSON.toJson(submittedItemsMap);
                data.setData(SUBMITTED_ITEMS_KEY, newSubmittedJson);

                Slimefun.logger()
                        .info("Submitted repair item: " + repairKey + ", count: " + (currentSubmitted + 1) + "/"
                                + requirement.requiredCount);

                return true;
            }
        } catch (Exception e) {
            Slimefun.logger().log(java.util.logging.Level.WARNING, "Error submitting repair item", e);
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

            java.util.List<java.util.Map<String, String>> repairItemsList = GSON.fromJson(
                    repairItemsJson,
                    new com.google.gson.reflect.TypeToken<
                            java.util.List<java.util.Map<String, String>>>() {}.getType());

            java.util.Map<String, RepairRequirement> requirements = aggregateRepairRequirements(repairItemsList);
            java.util.Map<String, Integer> submittedItemsMap = readSubmittedCounts(submittedItemsJson);

            if (requirements.isEmpty()) {
                return false;
            }

            for (RepairRequirement requirement : requirements.values()) {
                int submitted = getSubmittedCount(submittedItemsMap, requirement);
                if (submitted < requirement.requiredCount) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            Slimefun.logger().log(java.util.logging.Level.WARNING, "Error checking if can repair", e);
            return false;
        }
    }

    public void repairMachine(@Nonnull Location location) {
        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(location);
        if (sfItem instanceof EnergyNetComponent
                && ((EnergyNetComponent) sfItem).getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
            ConnectorAgingManager.setDurability(location, 1f);
            ConnectorAgingManager.clearRepairData(location);
            ConnectorAgingManager.removeDamageHologram(location);
            EnergyNet net = EnergyNet.getNetworkFromLocation(location);
            if (net != null) {
                net.markDirty(location);
            }
            return;
        }
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

    @Nonnull
    private static Location getDamageHologramLocation(@Nonnull Location location) {
        return location.clone().add(Slimefun.getHologramsService().getDefaultOffset());
    }

    @Nonnull
    private static Map<String, RepairRequirement> aggregateRepairRequirements(
            java.util.List<java.util.Map<String, String>> repairItemsList) {
        Map<String, RepairRequirement> requirements = new LinkedHashMap<>();
        if (repairItemsList == null) {
            return requirements;
        }

        for (java.util.Map<String, String> itemData : repairItemsList) {
            if (itemData == null) {
                continue;
            }

            String itemId = itemData.get("id");
            String itemType = itemData.get("type");
            if (itemId == null || itemType == null) {
                continue;
            }

            ItemStack repairItem = createRepairItem(itemType, itemId);
            String key = repairKey(itemType, itemId);
            RepairRequirement requirement = requirements.get(key);
            if (requirement == null) {
                requirements.put(key, new RepairRequirement(itemType, itemId, repairItem, 1));
            } else {
                requirement.requiredCount++;
            }
        }

        return requirements;
    }

    private static ItemStack createRepairItem(@Nonnull String itemType, @Nonnull String itemId) {
        if ("slimefun".equals(itemType)) {
            SlimefunItem slimefunItem = SlimefunItem.getById(itemId);
            return slimefunItem != null ? slimefunItem.getItem() : null;
        } else if ("vanilla".equals(itemType)) {
            try {
                org.bukkit.Material material = org.bukkit.Material.valueOf(itemId);
                return new ItemStack(material);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        return null;
    }

    private static Map<String, Integer> readSubmittedCounts(String submittedItemsJson) {
        Map<String, Integer> submittedItemsMap = new java.util.HashMap<>();
        if (submittedItemsJson == null || submittedItemsJson.isEmpty()) {
            return submittedItemsMap;
        }

        try {
            java.util.Map<String, Number> tempMap = GSON.fromJson(
                    submittedItemsJson,
                    new com.google.gson.reflect.TypeToken<java.util.Map<String, Number>>() {}.getType());
            if (tempMap != null) {
                for (java.util.Map.Entry<String, Number> entry : tempMap.entrySet()) {
                    submittedItemsMap.put(entry.getKey(), entry.getValue().intValue());
                }
            }
        } catch (Exception ignored) {
            // Older data used [] for an empty submission list. Treat malformed data as empty.
        }

        return submittedItemsMap;
    }

    private static int getSubmittedCount(
            @Nonnull Map<String, Integer> submittedItemsMap, @Nonnull RepairRequirement requirement) {
        return Math.max(
                submittedItemsMap.getOrDefault(repairKey(requirement.type, requirement.id), 0),
                submittedItemsMap.getOrDefault(requirement.id, 0));
    }

    private static String repairKey(@Nonnull String type, @Nonnull String id) {
        return type + ":" + id;
    }

    /**
     * 计算机器在连续工作指定粘液刻数后的损坏概率（每个粘液刻独立判定）。
     * 公式设计目标：平均工作寿命约为 500,000 粘液刻。
     *
     * @param continuousTicks 当前连续工作的粘液刻数（从1开始）
     * @return 损坏概率（介于 0 到 1 之间的 double 值）
     */
    public static double calculateDamageRate(long continuousTicks) {
        return calculateDamageRate(
                continuousTicks, new SlimefunMachineDamageManager.MachineDamageConfig(true, 7.5e-6, 2.5e11, 7.5e-6));
    }

    public static double calculateDamageRate(
            long continuousTicks, @Nonnull SlimefunMachineDamageManager.MachineDamageConfig config) {
        if (continuousTicks <= 0 || !config.isEnabled()) {
            return 0.0;
        }

        double ticks = (double) continuousTicks;
        double ticksSquared = ticks * ticks;
        double denominator = config.getDamageChanceExponent() + ticksSquared;
        double rate = denominator <= 0.0
                ? config.getDamageChanceScale()
                : config.getDamageChanceScale() * ticksSquared / denominator;
        return Math.max(0.0, Math.min(config.getMaxDamageChance(), rate));
    }

    private static final class RepairRequirement {
        private final String type;
        private final String id;
        private final ItemStack item;
        private int requiredCount;

        private RepairRequirement(String type, String id, ItemStack item, int requiredCount) {
            this.type = type;
            this.id = id;
            this.item = item;
            this.requiredCount = requiredCount;
        }
    }

    public void markDamaged(@Nonnull Location location, @Nonnull UUID ownerUuid) {
        damagedBlockIndex
                .computeIfAbsent(ownerUuid, k -> ConcurrentHashMap.newKeySet())
                .add(location);
    }

    public void markRepaired(@Nonnull Location location) {
        for (Set<Location> locations : damagedBlockIndex.values()) {
            locations.remove(location);
        }
        damagedBlockIndex.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    @Nonnull
    public Set<Location> getDamagedLocationsByOwner(@Nonnull UUID ownerUuid) {
        return damagedBlockIndex.getOrDefault(ownerUuid, java.util.Collections.emptySet());
    }

    public String getMachineInfo(@Nonnull Location location, @Nonnull SlimefunItem item) {
        if (item instanceof EnergyNetComponent
                && ((EnergyNetComponent) item).getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
            boolean isDamaged = ConnectorAgingManager.isConnectorDamaged(location);
            float durability = ConnectorAgingManager.getDurability(location);
            String statusColor = ConnectorAgingManager.getStatusColor(durability);
            String statusText = ConnectorAgingManager.getStatusText(durability);

            StringBuilder info = new StringBuilder();
            info.append("§a机器名称: §f").append(item.getItemName()).append("\n");
            info.append("§a类型: §f连接器（独立老化系统）\n");
            info.append("§a耐久度: §f")
                    .append(statusColor)
                    .append(String.format("%.2f%%", durability * 100))
                    .append("\n");
            info.append("§a状态: §f").append(statusColor).append(statusText).append("\n");
            info.append("§a剩余寿命: §f")
                    .append(ConnectorAgingManager.formatJoules(ConnectorAgingManager.getRemainingJoules(location)))
                    .append("J\n");

            if (isDamaged) {
                info.append("§c修复材料: §f")
                        .append(ConnectorAgingManager.getRepairItemsDisplay(location))
                        .append("\n");
            }

            return info.toString();
        }

        var data = StorageCacheUtils.getDataContainer(location);
        if (data == null) {
            return "§c无法获取机器数据！";
        }

        long workTicks = getWorkTicks(data);
        double damageChance = getDamageChance(data);
        boolean isDamaged = isMachineDamaged(data);

        var config = damageManager.getMachineConfig(item.getId());
        boolean enabled = config.isEnabled();

        StringBuilder info = new StringBuilder();
        info.append("§a机器名称: §f").append(item.getItemName()).append("\n");
        info.append("§a工作刻数: §f").append(workTicks).append("\n");
        info.append("§a当前报废几率: §f")
                .append(String.format("%.10f%%", damageChance * 100))
                .append("\n");
        info.append("§a状态: §f").append(isDamaged ? "已损坏" : "正常运行").append("\n");
        info.append("§a损坏机制: §f").append(enabled ? "启用" : "禁用").append("\n");

        // 添加修复物品信息
        if (isDamaged) {
            java.util.List<java.util.Map<String, Object>> repairItemsList = getRepairItems(data);
            if (!repairItemsList.isEmpty()) {
                info.append("§c需要以下修复物品:\n");
                for (java.util.Map<String, Object> itemInfo : repairItemsList) {
                    ItemStack repairItem = (ItemStack) itemInfo.get("item");
                    Integer submitted = (Integer) itemInfo.get("submitted");
                    Integer required = (Integer) itemInfo.get("required");

                    if (repairItem != null) {
                        org.bukkit.inventory.meta.ItemMeta meta = repairItem.getItemMeta();
                        String itemName = meta != null
                                        && meta.getDisplayName() != null
                                        && !meta.getDisplayName().isEmpty()
                                ? meta.getDisplayName()
                                : LocalizationUtils.getItemName(repairItem.getType());

                        int requiredCount = required != null && required > 0 ? required : 1;
                        int submittedCount = submitted != null ? submitted : 0;
                        if (submittedCount >= requiredCount) {
                            info.append("§a✓ ")
                                    .append(itemName)
                                    .append(" (已提交: ")
                                    .append(submittedCount)
                                    .append("/")
                                    .append(requiredCount)
                                    .append(")\n");
                        } else {
                            info.append("§c✗ ")
                                    .append(itemName)
                                    .append(" (需要: ")
                                    .append(requiredCount)
                                    .append(", 已提交: ")
                                    .append(submittedCount)
                                    .append("/")
                                    .append(requiredCount)
                                    .append(")\n");
                        }
                    }
                }
            }
        }

        return info.toString();
    }
}
