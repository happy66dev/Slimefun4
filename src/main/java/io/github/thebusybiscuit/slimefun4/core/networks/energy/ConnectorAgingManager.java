package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.EnergyConnector;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class ConnectorAgingManager {

    // ─── 全局配置 ────────────────────────────────────────────────
    private static final double BASE = 0.2;
    private static final double PEAK_FACTOR = 10.0;
    private static final double EXP = 4.0;
    private static final double LOSS_PERCENT = 0.01;
    private static final double ACCELERATE_COEFF = 4.0;
    private static final int OVERLOAD_SECONDS = 10;

    // ─── 存储键 ──────────────────────────────────────────────────
    private static final String DURABILITY_KEY = "connector_durability";
    private static final String DAMAGED_KEY = "connector_damaged";
    private static final String OVERLOAD_TICKS_KEY = "connector_overload_ticks";

    // ─── 连接器配置 ──────────────────────────────────────────────
    public static final class ConnectorConfig {
        public final long sweetPower;
        public final long maxPower;
        public final long peakPower;
        public final long expectedLifetime;
        public final ItemStack repairItem;
        public final double baseProb;

        ConnectorConfig(long sweet, long max, long peak, long lifetime, ItemStack repair) {
            this.sweetPower = sweet;
            this.maxPower = max;
            this.peakPower = peak;
            this.expectedLifetime = lifetime;
            this.repairItem = repair;
            this.baseProb = (100.0 / LOSS_PERCENT) / lifetime;
        }

        public long getLifetimeJoules() {
            return expectedLifetime * maxPower;
        }
    }

    private static final Map<String, ConnectorConfig> CONFIGS = new HashMap<>();

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
            if (!(sfItem instanceof EnergyConnector)) continue;

            ConnectorConfig config = CONFIGS.get(sfItem.getId());
            if (config == null) continue;

            float durability = getDurability(loc);

            if (load > config.peakPower) {
                handleOverload(loc, config, load, durability, net);
            } else {
                handleNormalAging(loc, config, load, durability, net);
            }
        }
    }

    public static float getDurability(@Nonnull Location loc) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null) return 1.0f;
        try {
            String val = data.getData(DURABILITY_KEY);
            return val != null ? Float.parseFloat(val) : 1.0f;
        } catch (Exception e) {
            return 1.0f;
        }
    }

    public static void setDurability(@Nonnull Location loc, float durability) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null) return;
        durability = Math.max(0f, Math.min(1f, durability));
        data.setData(DURABILITY_KEY, String.valueOf(durability));
        if (durability <= 0f) {
            data.setData(DAMAGED_KEY, "true");
            showDamageHologram(loc);
        } else {
            data.setData(DAMAGED_KEY, "false");
        }
    }

    public static boolean isConnectorDamaged(@Nonnull Location loc) {
        var data = StorageCacheUtils.getDataContainer(loc);
        if (data == null) return false;
        try {
            return "true".equals(data.getData(DAMAGED_KEY));
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable public static ConnectorConfig getConfig(@Nonnull Location loc) {
        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(loc);
        if (!(sfItem instanceof EnergyConnector)) return null;
        return CONFIGS.get(sfItem.getId());
    }

    public static long getRemainingJoules(@Nonnull Location loc) {
        ConnectorConfig config = getConfig(loc);
        if (config == null) return 0;
        return (long) (getDurability(loc) * config.expectedLifetime * config.maxPower);
    }

    // ─── 老化概率公式 ───────────────────────────────────────────

    private static double calcLoadFactor(long load, ConnectorConfig cfg) {
        if (load <= cfg.sweetPower) {
            if (cfg.sweetPower <= 0) return BASE;
            return BASE * load / cfg.sweetPower;
        }
        if (load <= cfg.maxPower) {
            double r = (double) (load - cfg.sweetPower) / (cfg.maxPower - cfg.sweetPower);
            return BASE + (1.0 - BASE) * Math.pow(r, EXP);
        }
        if (load <= cfg.peakPower) {
            double r = (double) (load - cfg.maxPower) / (cfg.peakPower - cfg.maxPower);
            return 1.0 + (PEAK_FACTOR - 1.0) * Math.pow(r, EXP);
        }
        return PEAK_FACTOR;
    }

    private static double calcAgeFactor(float durability) {
        return 1.0 + (1.0 - durability) * ACCELERATE_COEFF;
    }

    // ─── 正常老化判定 ───────────────────────────────────────────

    private static void handleNormalAging(
            Location loc, ConnectorConfig cfg, long load, float durability, EnergyNet net) {
        double loadFactor = calcLoadFactor(load, cfg);
        double ageFactor = calcAgeFactor(durability);
        double totalProb = cfg.baseProb * loadFactor * ageFactor;

        if (totalProb <= 0) return;

        if (ThreadLocalRandom.current().nextDouble() < totalProb) {
            float newDura = durability - (float) (LOSS_PERCENT / 100.0);
            setDurability(loc, newDura);
            if (newDura <= 0f) {
                net.markDirty(loc);
            }
        }
    }

    // ─── 过载惩罚 ───────────────────────────────────────────────

    private static void handleOverload(Location loc, ConnectorConfig cfg, long load, float durability, EnergyNet net) {
        float baseLoss = (float) (0.5 / 100.0);
        float loss = baseLoss * (float) load / cfg.peakPower;
        float newDura = Math.max(0, durability - loss);
        setDurability(loc, newDura);
        if (newDura <= 0f) {
            net.markDirty(loc);
            return;
        }

        spawnOverloadParticles(loc);

        int consecutiveTicks = getOverloadTicks(loc) + 1;
        setOverloadTicks(loc, consecutiveTicks);

        if (consecutiveTicks >= OVERLOAD_SECONDS * 20) {
            setDurability(loc, 0f);
            net.markDirty(loc);
        }
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
        if (data == null) return;
        data.setData(OVERLOAD_TICKS_KEY, String.valueOf(ticks));
    }

    private static void spawnOverloadParticles(Location loc) {
        loc.getWorld()
                .spawnParticle(
                        Particle.SMOKE, loc.getX() + 0.5, loc.getY() + 0.5, loc.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.02);
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
    }

    // ─── 损坏全息 ───────────────────────────────────────────────

    private static void showDamageHologram(Location loc) {
        try {
            Slimefun.getHologramsService().setHologramLabel(loc.clone().add(0.5, 1.5, 0.5), "§c连接器损坏");
        } catch (Exception ignored) {
        }
    }

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

        ConnectorConfig config = getConfig(loc);
        if (config == null) return false;

        float durability = getDurability(loc);
        int required = getRequiredRepairCount(durability);

        if (!hasItems(p, config.repairItem, required)) {
            p.sendMessage(ChatColors.color("&c需要 &e" + required + " &c个 " + itemName(config.repairItem) + " &c来修复"));
            return false;
        }

        removeItems(p, config.repairItem, required);
        setDurability(loc, 1f);
        removeDamageHologram(loc);
        p.sendMessage(ChatColors.color("&a连接器已修复至 100%"));
        p.playSound(loc, Sound.BLOCK_ANVIL_USE, 0.6f, 1.2f);
        return true;
    }

    // ─── 工具方法 ───────────────────────────────────────────────

    public static String getStatusColor(float durability) {
        if (durability > 0.80f) return "&a"; // 健康
        if (durability > 0.50f) return "&e"; // 正常
        if (durability > 0.30f) return "&6"; // 磨损
        if (durability > 0.15f) return "&c"; // 老化
        return "&4"; // 故障
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

    private static boolean hasItems(Player p, ItemStack item, int count) {
        int found = 0;
        for (ItemStack inv : p.getInventory().getContents()) {
            if (inv != null && inv.isSimilar(item)) {
                found += inv.getAmount();
                if (found >= count) return true;
            }
        }
        return found >= count;
    }

    private static void removeItems(Player p, ItemStack item, int count) {
        int remaining = count;
        for (int i = 0; i < p.getInventory().getSize() && remaining > 0; i++) {
            ItemStack inv = p.getInventory().getItem(i);
            if (inv != null && inv.isSimilar(item)) {
                int remove = Math.min(remaining, inv.getAmount());
                inv.setAmount(inv.getAmount() - remove);
                remaining -= remove;
                if (inv.getAmount() <= 0) {
                    p.getInventory().setItem(i, null);
                }
            }
        }
    }

    private static String itemName(ItemStack item) {
        return item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().getDisplayName()
                : item.getType().name();
    }
}
