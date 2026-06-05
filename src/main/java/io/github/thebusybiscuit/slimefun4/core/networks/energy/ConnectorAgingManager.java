package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import city.norain.slimefun4.utils.LocalizationUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ConnectorAgingManager {

    // ─── 全局配置 ────────────────────────────────────────────────
    private static final double LOSS_PERCENT = 0.01;
    private static final int TICK_DELAY;

    static {
        int delay = 10;
        try {
            delay = Slimefun.getCfg().getInt("URID.custom-ticker-delay");
        } catch (Exception ignored) {
        }
        TICK_DELAY = delay;
    }

    // ─── 老化因子分段 (durability -> ageFactor) ──────────────────
    private static final double[] AGE_BREAKS = {0.0, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0};
    private static final double[] AGE_VALUES = {5.0, 4.4, 3.6, 2.9, 2.3, 1.8, 1.45, 1.25, 1.1, 1.0, 1.0};

    // ─── 存储键 ──────────────────────────────────────────────────
    private static final String DURABILITY_KEY = "connector_durability";
    private static final String DAMAGED_KEY = "connector_damaged";
    private static final String OVERLOAD_TICKS_KEY = "connector_overload_ticks";
    private static final String REPAIR_ITEMS_KEY = "connector_repair_items";
    private static final String REPAIR_SUBMITTED_KEY = "connector_repair_submitted";

    private static final Gson GSON = new Gson();
    private static final Map<Location, Integer> OVERLOAD_DAMAGE_COOLDOWN = new ConcurrentHashMap<>();
    private static final int OVERLOAD_DAMAGE_INTERVAL = 1;

    // ─── 连接器配置 ──────────────────────────────────────────────
    public static final class ConnectorConfig {
        public final long sweetPower;
        public final long maxPower;
        public final long peakPower;
        public final long expectedLifetime;
        public final ItemStack repairItem;
        public final double baseProb;
        public final long expectedLifetimeJoules;

        ConnectorConfig(long sweet, long max, long peak, long lifetime, ItemStack repair) {
            this.sweetPower = sweet;
            this.maxPower = max;
            this.peakPower = peak;
            this.expectedLifetime = lifetime;
            this.repairItem = repair;
            this.baseProb = (100.0 / LOSS_PERCENT) / lifetime;
            this.expectedLifetimeJoules = computeExpectedLifetimeJoules(sweet, lifetime);
        }
    }

    static long computeExpectedLifetimeJoules(long sweetPower, long lifetime) {
        double sum = 0;
        int segments = 1000;
        double step = 1.0 / segments;
        for (int i = 0; i < segments; i++) {
            double d = i * step;
            double af = calcAgeFactor((float) d);
            sum += step / af;
        }
        double avgAgeFactor = 1.0 / sum;
        double expectedTicks = lifetime / avgAgeFactor;
        return (long) (sweetPower * expectedTicks);
    }

    private static final Map<String, ConnectorConfig> CONFIGS = new ConcurrentHashMap<>();

    public static void registerConfig(
            @Nonnull String itemId,
            long sweetPower,
            long maxPower,
            long peakPower,
            long expectedLifetime,
            @Nonnull ItemStack repairItem) {
        CONFIGS.put(itemId, new ConnectorConfig(sweetPower, maxPower, peakPower, expectedLifetime, repairItem));
    }

    private ConnectorAgingManager() {}

    // ─── 公开API ─────────────────────────────────────────────────

    public static void processAging(@Nonnull EnergyNet net) {
        Map<Location, Long> loads = net.getConnectorLoad();
        if (loads.isEmpty()) return;

        for (Map.Entry<Location, Long> entry : loads.entrySet()) {
            Location loc = entry.getKey();
            long load = entry.getValue();

            if (isConnectorDamaged(loc)) continue;
            if (load <= 0) continue;

            SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(loc);
            if (sfItem == null) continue;
            ConnectorConfig config = CONFIGS.get(sfItem.getId());
            if (config == null) continue;

            float durability = getDurability(loc);

            if (durability < (float) (LOSS_PERCENT / 100.0)) {
                setDurability(loc, 0f);
                net.markDirty(loc);
                continue;
            }

            if (load > config.peakPower) {
                handleOverload(loc, config, load, durability, net);
            } else {
                handleNormalAging(loc, config, load, durability, net);
            }
        }
    }

    public static float getDurability(@Nonnull Location loc) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null || data.isPendingRemove() || !data.isDataLoaded()) return 1.0f;
        try {
            String val = data.getData(DURABILITY_KEY);
            return val != null ? Float.parseFloat(val) : 1.0f;
        } catch (Exception e) {
            return 1.0f;
        }
    }

    public static void setDurability(@Nonnull Location loc, float durability) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null || data.isPendingRemove()) return;
        if (!data.isDataLoaded()) {
            StorageCacheUtils.requestLoad(data);
            return;
        }
        durability = Math.max(0f, Math.min(1f, durability));
        float oldDura = getDurability(loc);
        data.setData(DURABILITY_KEY, String.valueOf(durability));
        if (durability <= 0f) {
            data.setData(DAMAGED_KEY, "true");
            if (oldDura > 0f) {
                if (loc.getWorld() != null) {
                    loc.getWorld().playSound(loc, Sound.BLOCK_ANVIL_DESTROY, 1.0f, 1.0f);
                }
                generateRepairItems(loc);
            }
        } else {
            data.setData(DAMAGED_KEY, "false");
        }
    }

    public static boolean isConnectorDamaged(@Nonnull Location loc) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null || data.isPendingRemove() || !data.isDataLoaded()) return false;
        try {
            return "true".equals(data.getData(DAMAGED_KEY));
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable public static ConnectorConfig getConfig(@Nonnull Location loc) {
        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(loc);
        if (sfItem == null) {
            return null;
        }
        return CONFIGS.get(sfItem.getId());
    }

    public static long getRemainingJoules(@Nonnull Location loc) {
        ConnectorConfig config = getConfig(loc);
        if (config == null) return 0;
        return (long) (getDurability(loc) * config.expectedLifetimeJoules);
    }

    public static long getExpectedLifetimeJoules(@Nonnull String itemId) {
        ConnectorConfig config = CONFIGS.get(itemId);
        if (config == null) return 0;
        return config.expectedLifetimeJoules;
    }

    // ─── 老化概率公式 ───────────────────────────────────────────

    private static double calcLoadFactor(long load, ConnectorConfig cfg) {
        if (load <= cfg.sweetPower) {
            if (cfg.sweetPower <= 0) return 1.0;
            double x = (double) load / cfg.sweetPower;
            return x / (0.9 + 0.1 * x);
        }
        if (load <= cfg.maxPower) {
            long range = cfg.maxPower - cfg.sweetPower;
            if (range <= 0) return 1.0;
            double x = ((double) (load - cfg.sweetPower) / range) * 3.0 + 1.0;
            return x / (1.2 - 0.2 * x);
        }
        long range = cfg.peakPower - cfg.maxPower;
        if (range <= 0) return 1.0;
        double x = ((double) (load - cfg.maxPower) / range) * 4.0 + 4.0;
        return x / (0.79 - 0.0975 * x);
    }

    private static double calcAgeFactor(float durability) {
        double d = Math.max(0.0, Math.min(1.0, durability));

        if (d >= 1.0) return 1.0;

        for (int i = AGE_BREAKS.length - 2; i >= 0; i--) {
            if (d >= AGE_BREAKS[i]) {
                double t = (d - AGE_BREAKS[i]) / (AGE_BREAKS[i + 1] - AGE_BREAKS[i]);
                return AGE_VALUES[i] + (AGE_VALUES[i + 1] - AGE_VALUES[i]) * t;
            }
        }
        return AGE_VALUES[0];
    }

    // ─── 正常老化判定 ───────────────────────────────────────────

    private static void handleNormalAging(
            Location loc, ConnectorConfig cfg, long load, float durability, EnergyNet net) {
        if (getOverloadTicks(loc) > 0) {
            setOverloadTicks(loc, 0);
        }

        double loadFactor = calcLoadFactor(load, cfg);
        double ageFactor = calcAgeFactor(durability);
        double totalProb = cfg.baseProb * loadFactor * ageFactor;

        if (totalProb <= 0) return;

        if (ThreadLocalRandom.current().nextDouble() < totalProb) {
            float newDura = durability - (float) (LOSS_PERCENT / 100.0);
            if (newDura <= 0f) {
                net.markDirty(loc);
            }
            setDurability(loc, newDura);
        }
    }

    // ─── 过载惩罚 ───────────────────────────────────────────────

    private static void handleOverload(Location loc, ConnectorConfig cfg, long load, float durability, EnergyNet net) {
        float baseLoss = (float) (0.5 / 100.0);
        float loss = cfg.peakPower > 0 ? baseLoss * (float) load / cfg.peakPower : baseLoss;
        float newDura = Math.max(0, durability - loss);
        if (newDura <= 0f) {
            net.markDirty(loc);
        }
        setDurability(loc, newDura);
        if (newDura <= 0f) {
            return;
        }

        spawnOverloadParticles(loc);
        tickOverloadDamage(loc, cfg, load, net);

        int consecutiveTicks = getOverloadTicks(loc) + 1;
        setOverloadTicks(loc, consecutiveTicks);

        int maxTicks = (int) (newDura * 60.0f * 20 / TICK_DELAY);
        if (consecutiveTicks >= maxTicks) {
            setDurability(loc, 0f);
            net.markDirty(loc);
        }
    }

    private static void tickOverloadDamage(Location loc, ConnectorConfig cfg, long load, EnergyNet net) {
        int ticks = OVERLOAD_DAMAGE_COOLDOWN.merge(loc, 1, Integer::sum);
        if (ticks < OVERLOAD_DAMAGE_INTERVAL) return;
        OVERLOAD_DAMAGE_COOLDOWN.put(loc, 0);
        Slimefun.runSync(() -> applyOverloadDamage(loc, cfg, load, net));
    }

    private static void applyOverloadDamage(Location loc, ConnectorConfig cfg, long load, EnergyNet net) {
        EnergyNetComponent component = net.getConnectors().get(loc);
        if (component == null || loc.getWorld() == null) return;

        int range = component.getRange();
        if (range <= 0) return;

        double baseDamage = roundToHalf((((double) load / cfg.peakPower) - 1) * 10);
        if (baseDamage <= 0.0) return;

        Collection<Entity> nearbyEntities =
                loc.getWorld().getNearbyEntities(loc, range, range, range, entity -> entity instanceof Player);
        for (Entity entity : nearbyEntities) {
            Player player = (Player) entity;
            if (!player.isValid() || player.isDead()) continue;
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) continue;

            double distance = player.getLocation().distance(loc);
            if (distance >= range) continue;

            double damage = baseDamage * (range - distance) / range * 3.0 / 4.0;
            if (damage <= 0.0) continue;

            PlayerProfile.find(player).ifPresent(profile -> {
                if (profile.hasFullProtectionAgainst(ProtectionType.ENERGY_OVERLOAD)) return;
                player.setHealth(Math.max(0.0, player.getHealth() - damage));
                player.getWorld()
                        .spawnParticle(
                                Particle.ELECTRIC_SPARK, player.getLocation().add(0, 1, 0), 5, 0.4, 0.4, 0.4, 0.05);
            });
        }
    }

    private static double roundToHalf(double value) {
        double floor = Math.floor(value);
        double frac = value - floor;
        if (frac <= 0.2) return floor;
        if (frac <= 0.7) return floor + 0.5;
        return floor + 1.0;
    }

    private static int getOverloadTicks(Location loc) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null) return 0;
        try {
            String val = data.getData(OVERLOAD_TICKS_KEY);
            return val != null ? Integer.parseInt(val) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private static void setOverloadTicks(Location loc, int ticks) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null || data.isPendingRemove()) return;
        if (!data.isDataLoaded()) {
            StorageCacheUtils.requestLoad(data);
            return;
        }
        data.setData(OVERLOAD_TICKS_KEY, String.valueOf(ticks));
    }

    private static void spawnOverloadParticles(Location loc) {
        Slimefun.runSync(() -> {
            loc.getWorld()
                    .spawnParticle(
                            Particle.SMOKE,
                            loc.getX() + 0.5,
                            loc.getY() + 0.5,
                            loc.getZ() + 0.5,
                            3,
                            0.3,
                            0.3,
                            0.3,
                            0.02);
            loc.getWorld()
                    .spawnParticle(
                            Particle.ELECTRIC_SPARK,
                            loc.getX() + 0.5,
                            loc.getY() + 0.5,
                            loc.getZ() + 0.5,
                            5,
                            0.4,
                            0.4,
                            0.4,
                            0.05);
            loc.getWorld().playSound(loc, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.0f);
        });
    }

    // ─── 全息清理 ───────────────────────────────────────────────

    public static void removeDamageHologram(Location loc) {
        try {
            Slimefun.getHologramsService().removeHologram(loc.clone().add(0.5, 1.5, 0.5));
        } catch (Exception ignored) {
        }
    }

    // ─── 修复 ───────────────────────────────────────────────────

    public static int getRequiredRepairCount(float durability) {
        if (durability <= 0f) return 4;
        if (durability <= 0.33f) return 3;
        if (durability <= 0.66f) return 2;
        return 1;
    }

    public static boolean tryRepair(@Nonnull Player p, @Nonnull Location loc) {
        if (!isConnectorDamaged(loc) && getDurability(loc) >= 1f) {
            return false;
        }

        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null) return false;

        String itemsJson = data.getData(REPAIR_ITEMS_KEY);
        if (itemsJson == null || itemsJson.isEmpty()) {
            generateRepairItems(loc);
            itemsJson = data.getData(REPAIR_ITEMS_KEY);
            if (itemsJson == null || itemsJson.isEmpty()) {
                // p.sendMessage(ChatColors.color("&c无法生成修复材料列表（无合成配方）"));
                return false;
            }
        }

        List<Map<String, String>> repairItems;
        try {
            repairItems = GSON.fromJson(itemsJson, new TypeToken<List<Map<String, String>>>() {}.getType());
        } catch (Exception e) {
            return false;
        }

        if (repairItems == null || repairItems.isEmpty()) return false;

        int required = getRequiredRepairCount(getDurability(loc));
        if (repairItems.size() < required) {
            for (int i = repairItems.size(); i < required; i++) {
                Map<String, String> item = pickRandomRepairItem(loc);
                if (item != null) {
                    repairItems.add(item);
                }
            }
            data.setData(REPAIR_ITEMS_KEY, GSON.toJson(repairItems));
        }

        int submitted = 0;
        try {
            submitted = Integer.parseInt(data.getData(REPAIR_SUBMITTED_KEY));
        } catch (Exception ignored) {
        }

        if (submitted >= repairItems.size()) {
            setDurability(loc, 1f);
            data.removeData(REPAIR_ITEMS_KEY);
            data.removeData(REPAIR_SUBMITTED_KEY);
            data.removeData(OVERLOAD_TICKS_KEY);
            removeDamageHologram(loc);
            triggerGridRecheck(loc);
            // p.sendMessage(ChatColors.color("&a连接器已修复至 100%"));
            p.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
            return true;
        }

        Map<String, String> targetItem = repairItems.get(submitted);
        if (targetItem == null) return false;

        ItemStack held = p.getInventory().getItemInMainHand();
        if (held.getType() == Material.AIR) {
            // p.sendMessage(ChatColors.color("&c手持修复材料右键点击连接器来提交"));
            // p.sendMessage(ChatColors.color("&7需要: " + getRepairItemDisplay(targetItem)));
            return false;
        }

        if (!matchesRepairItem(held, targetItem)) {
            // p.sendMessage(ChatColors.color("&c材料不匹配，需要: " + getRepairItemDisplay(targetItem)));
            return false;
        }

        held.setAmount(held.getAmount() - 1);
        if (held.getAmount() <= 0) {
            p.getInventory().setItemInMainHand(null);
        }

        submitted++;
        data.setData(REPAIR_SUBMITTED_KEY, String.valueOf(submitted));

        if (submitted >= repairItems.size()) {
            setDurability(loc, 1f);
            data.removeData(REPAIR_ITEMS_KEY);
            data.removeData(REPAIR_SUBMITTED_KEY);
            data.removeData(OVERLOAD_TICKS_KEY);
            removeDamageHologram(loc);
            triggerGridRecheck(loc);
            // p.sendMessage(ChatColors.color("&a连接器已修复至 100%"));
            p.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
        } else {
            // p.sendMessage(ChatColors.color("&a提交成功! (&e" + submitted + "/" + repairItems.size() + "&a)"));
            // p.sendMessage(ChatColors.color("&7下一个需要: " + getRepairItemDisplay(repairItems.get(submitted))));
        }

        return true;
    }

    public static String getRepairItemsDisplay(@Nonnull Location loc) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null || data.isPendingRemove()) return "";
        if (!data.isDataLoaded()) {
            StorageCacheUtils.requestLoad(data);
            return "";
        }

        String itemsJson = data.getData(REPAIR_ITEMS_KEY);
        if (itemsJson == null || itemsJson.isEmpty()) {
            int count = getRequiredRepairCount(getDurability(loc));
            return count + "x 材料(右键查看)";
        }

        try {
            List<Map<String, String>> items =
                    GSON.fromJson(itemsJson, new TypeToken<List<Map<String, String>>>() {}.getType());
            if (items == null) return "";

            int submitted = 0;
            try {
                submitted = Integer.parseInt(data.getData(REPAIR_SUBMITTED_KEY));
            } catch (Exception ignored) {
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < items.size(); i++) {
                if (i > 0) sb.append(", ");
                String display = getRepairItemDisplay(items.get(i));
                if (i < submitted) {
                    sb.append("§7~~").append(display).append("~~");
                } else if (i == submitted) {
                    sb.append("§e> ").append(display).append(" §f<");
                } else {
                    sb.append("§7").append(display);
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ─── 修复辅助方法 ───────────────────────────────────────────

    private static String getRepairItemDisplay(Map<String, String> item) {
        if (item == null) return "未知";
        String type = item.get("type");
        String id = item.get("id");
        if (type == null || id == null) return "未知";
        if ("slimefun".equals(type)) {
            SlimefunItem sfItem = SlimefunItem.getById(id);
            return sfItem != null ? sfItem.getItemName() : id;
        }
        if ("vanilla".equals(type)) {
            try {
                Material mat = Material.valueOf(id);
                return LocalizationUtils.getItemName(mat);
            } catch (IllegalArgumentException e) {
                return id;
            }
        }
        return id;
    }

    private static boolean matchesRepairItem(ItemStack held, Map<String, String> target) {
        if (held == null || target == null) return false;

        String type = target.get("type");
        String id = target.get("id");
        if (type == null || id == null) return false;

        if ("slimefun".equals(type)) {
            SlimefunItem sfItem = SlimefunItem.getByItem(held);
            return sfItem != null && sfItem.getId().equals(id);
        }

        if ("vanilla".equals(type)) {
            return held.getType().name().equals(id);
        }

        return false;
    }

    private static void generateRepairItems(@Nonnull Location loc) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null) return;

        int count = getRequiredRepairCount(getDurability(loc));

        List<Map<String, String>> itemsList = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, String> item = pickRandomRepairItem(loc);
            if (item != null) {
                itemsList.add(item);
            }
        }
        if (itemsList.isEmpty()) return;

        data.setData(REPAIR_ITEMS_KEY, GSON.toJson(itemsList));
        data.setData(REPAIR_SUBMITTED_KEY, "0");
    }

    @Nullable private static Map<String, String> pickRandomRepairItem(@Nonnull Location loc) {
        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(loc);
        if (sfItem == null) return null;

        ItemStack[] recipe = sfItem.getRecipe();
        if (recipe == null) return null;

        List<ItemStack> weightedItems = new ArrayList<>();
        for (ItemStack item : recipe) {
            if (item != null && item.getType() != Material.AIR) {
                weightedItems.add(item);
            }
        }
        if (weightedItems.isEmpty()) return null;

        ItemStack picked = weightedItems.get(ThreadLocalRandom.current().nextInt(weightedItems.size()));
        Map<String, String> map = new HashMap<>();
        SlimefunItem sf = SlimefunItem.getByItem(picked);
        if (sf != null) {
            map.put("type", "slimefun");
            map.put("id", sf.getId());
        } else {
            map.put("type", "vanilla");
            map.put("id", picked.getType().name());
        }
        return map;
    }

    // ─── 工具方法 ───────────────────────────────────────────────

    public static String getStatusColor(float durability) {
        if (durability > 0.80f) return "&a";
        if (durability > 0.50f) return "&e";
        if (durability > 0.30f) return "&6";
        if (durability > 0.15f) return "&c";
        return "&4";
    }

    public static String getStatusText(float durability) {
        if (durability > 0.80f) return "健康";
        if (durability > 0.50f) return "正常";
        if (durability > 0.30f) return "磨损";
        if (durability > 0.15f) return "老化";
        if (durability > 0f) return "预计故障";
        return "已损坏";
    }

    public static String formatJoules(long joules) {
        if (joules >= 1_000_000_000) {
            return String.format("%.2fB", joules / 1_000_000_000.0);
        }
        if (joules >= 1_000_000) {
            return String.format("%.2fM", joules / 1_000_000.0);
        }
        if (joules >= 1_000) {
            return String.format("%.1fK", joules / 1_000.0);
        }
        return String.valueOf(joules);
    }

    public static void clearRepairData(@Nonnull Location loc) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null) return;
        data.removeData(REPAIR_ITEMS_KEY);
        data.removeData(REPAIR_SUBMITTED_KEY);
    }

    private static void triggerGridRecheck(@Nonnull Location loc) {
        EnergyNet net = EnergyNet.getNetworkFromLocation(loc);
        if (net != null) {
            net.markDirty(loc);
        } else {
            EnergyNet.onMachinePlaced(loc);
        }
    }
}
