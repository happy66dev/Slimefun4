package io.github.thebusybiscuit.slimefun4.core.config;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.File;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SlimefunMachineDamageManager {

    private final Config config;
    private final Map<String, MachineDamageConfig> machineConfigs = new HashMap<>();
    private MachineDamageConfig defaultConfig;

    public SlimefunMachineDamageManager(@Nonnull Slimefun plugin) {
        
        // 尝试保存默认配置文件
        try {
            // 保存默认配置文件
            if (!new File(plugin.getDataFolder(), "machine-damage.yml").exists()) {
                plugin.saveResource("machine-damage.yml", true);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("无法保存默认机器损坏配置文件: " + e.getMessage());
        }
        
        // 加载配置
        this.config = new Config(plugin, "machine-damage.yml");
        loadConfig();
    }

    private void loadConfig() {
        // 加载默认配置
        boolean defaultEnabled = config.contains("defaults.enabled") ? config.getBoolean("defaults.enabled") : true;
        double damageChanceScale = config.contains("defaults.damage_chance_scale") ? config.getDouble("defaults.damage_chance_scale") : 1.0e-8;
        double damageChanceExponent = config.contains("defaults.damage_chance_exponent") ? config.getDouble("defaults.damage_chance_exponent") : 1.5;
        double maxDamageChance = config.contains("defaults.max_damage_chance") ? config.getDouble("defaults.max_damage_chance") : 0.01;
        boolean stopOnDamage = config.contains("defaults.stop_on_damage") ? config.getBoolean("defaults.stop_on_damage") : true;
        
        // 确保配置值在有效范围内
        damageChanceScale = Math.max(0.0, damageChanceScale);
        damageChanceExponent = Math.max(1.0, damageChanceExponent);
        maxDamageChance = Math.max(0.0, Math.min(1.0, maxDamageChance));
        
        defaultConfig = new MachineDamageConfig(defaultEnabled, damageChanceScale, damageChanceExponent, maxDamageChance, stopOnDamage);
        
        // 加载具体机器配置
        if (config.contains("machines")) {
            for (String machineId : config.getKeys("machines")) {
                boolean enabled = config.contains("machines." + machineId + ".enabled") ? 
                    config.getBoolean("machines." + machineId + ".enabled") : 
                    defaultConfig.isEnabled();
                
                double scale = config.contains("machines." + machineId + ".damage_chance_scale") ? 
                    config.getDouble("machines." + machineId + ".damage_chance_scale") : 
                    defaultConfig.getDamageChanceScale();
                
                double exponent = config.contains("machines." + machineId + ".damage_chance_exponent") ? 
                    config.getDouble("machines." + machineId + ".damage_chance_exponent") : 
                    defaultConfig.getDamageChanceExponent();
                
                double maxChance = config.contains("machines." + machineId + ".max_damage_chance") ? 
                    config.getDouble("machines." + machineId + ".max_damage_chance") : 
                    defaultConfig.getMaxDamageChance();
                
                boolean machineStopOnDamage = config.contains("machines." + machineId + ".stop_on_damage") ? 
                    config.getBoolean("machines." + machineId + ".stop_on_damage") : 
                    defaultConfig.isStopOnDamage();
                
                // 确保配置值在有效范围内
                scale = Math.max(0.0, scale);
                exponent = Math.max(1.0, exponent);
                maxChance = Math.max(0.0, Math.min(1.0, maxChance));
                
                machineConfigs.put(machineId, new MachineDamageConfig(enabled, scale, exponent, maxChance, machineStopOnDamage));
            }
        }
    }

    public void reload() {
        config.reload();
        machineConfigs.clear();
        loadConfig();
    }

    @Nonnull
    public MachineDamageConfig getMachineConfig(@Nullable String machineId) {
        if (machineId != null && machineConfigs.containsKey(machineId)) {
            return machineConfigs.get(machineId);
        }
        return defaultConfig;
    }

    public static class MachineDamageConfig {
        private final boolean enabled;
        private final double damageChanceScale;
        private final double damageChanceExponent;
        private final double maxDamageChance;
        private final boolean stopOnDamage;

        public MachineDamageConfig(boolean enabled, double damageChanceScale, double damageChanceExponent, double maxDamageChance, boolean stopOnDamage) {
            this.enabled = enabled;
            this.damageChanceScale = damageChanceScale;
            this.damageChanceExponent = damageChanceExponent;
            this.maxDamageChance = maxDamageChance;
            this.stopOnDamage = stopOnDamage;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public double getDamageChanceScale() {
            return damageChanceScale;
        }

        public double getDamageChanceExponent() {
            return damageChanceExponent;
        }

        public double getMaxDamageChance() {
            return maxDamageChance;
        }

        public boolean isStopOnDamage() {
            return stopOnDamage;
        }
    }
}
