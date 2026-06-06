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
        double damageChanceScale = config.contains("defaults.damage_chance_scale")
                ? config.getDouble("defaults.damage_chance_scale")
                : 7.5e-6;
        double damageChanceExponent = config.contains("defaults.damage_chance_exponent")
                ? config.getDouble("defaults.damage_chance_exponent")
                : 2.5e11;
        double maxDamageChance =
                config.contains("defaults.max_damage_chance") ? config.getDouble("defaults.max_damage_chance") : 7.5e-6;
        // 确保配置值在有效范围内
        damageChanceScale = Math.max(0.0, damageChanceScale);
        damageChanceExponent = Math.max(0.0, damageChanceExponent);
        maxDamageChance = Math.max(0.0, Math.min(1.0, maxDamageChance));

        defaultConfig =
                new MachineDamageConfig(defaultEnabled, damageChanceScale, damageChanceExponent, maxDamageChance);

        // 加载具体机器配置
        if (config.contains("machines")) {
            for (String machineId : config.getKeys("machines")) {
                boolean enabled = config.contains("machines." + machineId + ".enabled")
                        ? config.getBoolean("machines." + machineId + ".enabled")
                        : defaultConfig.isEnabled();

                double scale = config.contains("machines." + machineId + ".damage_chance_scale")
                        ? config.getDouble("machines." + machineId + ".damage_chance_scale")
                        : defaultConfig.getDamageChanceScale();

                double exponent = config.contains("machines." + machineId + ".damage_chance_exponent")
                        ? config.getDouble("machines." + machineId + ".damage_chance_exponent")
                        : defaultConfig.getDamageChanceExponent();

                double maxChance = config.contains("machines." + machineId + ".max_damage_chance")
                        ? config.getDouble("machines." + machineId + ".max_damage_chance")
                        : defaultConfig.getMaxDamageChance();

                // 确保配置值在有效范围内
                scale = Math.max(0.0, scale);
                exponent = Math.max(0.0, exponent);
                maxChance = Math.max(0.0, Math.min(1.0, maxChance));

                machineConfigs.put(machineId, new MachineDamageConfig(enabled, scale, exponent, maxChance));
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

        public MachineDamageConfig(
                boolean enabled, double damageChanceScale, double damageChanceExponent, double maxDamageChance) {
            this.enabled = enabled;
            this.damageChanceScale = damageChanceScale;
            this.damageChanceExponent = damageChanceExponent;
            this.maxDamageChance = maxDamageChance;
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
    }
}
