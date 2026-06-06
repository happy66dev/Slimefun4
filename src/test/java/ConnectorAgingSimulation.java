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
import java.util.Random;

/**
 * 连接器老化概率仿真 — 纯逻辑模拟，不依赖 Spigot/Slimefun
 *
 * 复现 ConnectorAgingManager.handleNormalAging / handleOverload 的完整算法。
 * 每个连接器独立模拟 100,000 ticks，记录耐久度随时间的变化。
 *
 * 用法: javac ConnectorAgingSimulation.java && java ConnectorAgingSimulation
 */
public class ConnectorAgingSimulation {

    private static final double LOSS_PERCENT = 0.01;
    // LOSS_PERCENT / 100 = 0.0001 — 每次老化事件扣减的耐久量
    private static final double DURA_PER_HIT = LOSS_PERCENT / 100.0; // 0.0001

    // 每个 tick 实际对应 TICK_DELAY 个 Minecraft tick
    private static final int TICK_DELAY = 10;

    private static final double[] AGE_BREAKS = {0.0, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 0.95, 1.0};
    private static final double[] AGE_VALUES = {5.0, 4.4, 3.6, 2.9, 2.3, 1.8, 1.45, 1.25, 1.1, 1.0, 1.0};

    static class Config {
        final String name;
        final long sweetPower;
        final long maxPower;
        final long peakPower;
        final long expectedLifetime; // ticks at sweetPower
        final double baseProb; // 10000 / expectedLifetime

        Config(String name, long sweet, long max, long peak, long lifetime) {
            this.name = name;
            this.sweetPower = sweet;
            this.maxPower = max;
            this.peakPower = peak;
            this.expectedLifetime = lifetime;
            this.baseProb = (100.0 / LOSS_PERCENT) / lifetime;
        }
    }

    /* ─────── 算法复现 ─────── */

    static double calcLoadFactor(long load, Config cfg) {
        if (load <= cfg.sweetPower) {
            if (cfg.sweetPower <= 0) return 1.0;
            double x = (double) load / cfg.sweetPower;
            return x / (0.9 + 0.1 * x);
        }
        if (load <= cfg.maxPower) {
            double x = ((double) (load - cfg.sweetPower) / (cfg.maxPower - cfg.sweetPower)) * 3.0 + 1.0;
            return x / (1.2 - 0.2 * x);
        }
        if (load <= cfg.peakPower) {
            double x = ((double) (load - cfg.maxPower) / (cfg.peakPower - cfg.maxPower)) * 4.0 + 4.0;
            return x / (0.79 - 0.0975 * x);
        }
        return 1.0;
    }

    static double calcAgeFactor(double durability) {
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

    static class Result {
        double finalDurability;
        int hitCount;
        int overloadCount;
        int deathTick;
        double[] durabilityHistory;

        Result(int ticks) {
            this.durabilityHistory = new double[ticks + 1];
        }
    }

    /**
     * 模拟单个连接器老化过程。返回最终耐久度和统计信息。
     *
     * @param rng  随机源（可设置种子复现）
     * @param cfg  连接器配置
     * @param load 每 tick 负载 (J/t)
     * @param totalTicks 模拟总 tick 数
     */
    static Result simulate(Random rng, Config cfg, long load, int totalTicks) {
        Result r = new Result(totalTicks);
        double durability = 1.0;
        r.durabilityHistory[0] = durability;
        int consecutiveOverload = 0;

        for (int tick = 1; tick <= totalTicks; tick++) {
            if (durability <= 0.0) {
                r.deathTick = tick;
                break;
            }

            if (load <= 0) {
                r.durabilityHistory[tick] = durability;
                continue;
            }

            // 耐久低于单次扣减值 → 强制归零
            if (durability < DURA_PER_HIT) {
                durability = 0.0;
                r.deathTick = tick;
                r.durabilityHistory[tick] = durability;
                break;
            }

            if (load > cfg.peakPower) {
                // ─── 过载惩罚 (deterministic!) ───
                r.overloadCount++;
                double baseLoss = 0.005; // 0.5 / 100
                double loss = baseLoss * load / cfg.peakPower;
                durability = Math.max(0.0, durability - loss);
                if (durability <= 0.0) {
                    r.deathTick = tick;
                    r.durabilityHistory[tick] = durability;
                    break;
                }
                consecutiveOverload++;
                int maxTicks = (int) (durability * 60.0 * 20 / TICK_DELAY); // durability * 120
                if (consecutiveOverload >= maxTicks) {
                    durability = 0.0;
                    r.deathTick = tick;
                    r.durabilityHistory[tick] = durability;
                    break;
                }
            } else {
                // ─── 正常老化 ───
                consecutiveOverload = 0; // 有一次正常→重置连续过载计数
                double loadFactor = calcLoadFactor(load, cfg);
                double ageFactor = calcAgeFactor(durability);
                double totalProb = cfg.baseProb * loadFactor * ageFactor;

                if (totalProb > 0 && rng.nextDouble() < totalProb) {
                    r.hitCount++;
                    durability -= DURA_PER_HIT;
                    if (durability <= 0.0) {
                        r.deathTick = tick;
                        r.durabilityHistory[tick] = durability;
                        break;
                    }
                }
            }

            r.durabilityHistory[tick] = durability;
        }

        r.finalDurability = durability;
        if (r.deathTick == 0) r.deathTick = -1; // 未死亡
        return r;
    }

    /* ─────── 输出 ─────── */

    static String bar(double pct) {
        int n = (int) (pct * 50);
        StringBuilder sb = new StringBuilder(52);
        sb.append("[");
        for (int i = 0; i < 50; i++) {
            sb.append(i < n ? "█" : " ");
        }
        sb.append("]");
        return sb.toString();
    }

    static void printHeader(String title) {
        System.out.println();
        System.out.println("══════════════════════════════════════════════════════════════════");
        System.out.println("  " + title);
        System.out.println("══════════════════════════════════════════════════════════════════");
    }

    static void printConnectorSimulation(Config cfg, long seed, int totalTicks) {
        printHeader(cfg.name + "  sweet=" + cfg.sweetPower + " max=" + cfg.maxPower + " peak=" + cfg.peakPower
                + " lifetime=" + formatTicks(cfg.expectedLifetime));

        String[][] loads = {
            {"0.5× 甜点", String.valueOf(cfg.sweetPower / 2)},
            {"1× 甜点", String.valueOf(cfg.sweetPower)},
            {"2× 甜点", String.valueOf(cfg.sweetPower * 2)},
            {"甜点→最大中点", String.valueOf((cfg.sweetPower + cfg.maxPower) / 2)},
            {"1× 最大", String.valueOf(cfg.maxPower)},
            {"0.8× 峰值", String.valueOf((long) (cfg.peakPower * 0.8))},
            {"1.2× 峰值(过载)", String.valueOf((long) (cfg.peakPower * 1.2))},
        };

        System.out.printf("  %-18s %10s %10s %10s %10s %10s%n", "负载档位", "最终耐久%", "老化命中", "过载次数", "死亡Tick", "耐久度柱状图");
        System.out.println("  " + "-".repeat(78));

        for (String[] entry : loads) {
            String label = entry[0];
            long load = Long.parseLong(entry[1]);
            Random rng = new Random(seed); // 每档用同一种子，可比较
            Result r = simulate(rng, cfg, load, totalTicks);

            String death = r.deathTick == -1 ? "存活" : String.valueOf(r.deathTick);
            System.out.printf(
                    "  %-18s %9.1f%% %10d %10d %10s %s%n",
                    label, r.finalDurability * 100.0, r.hitCount, r.overloadCount, death, bar(r.finalDurability));
        }
    }

    static void printAggregateHistogram(Config cfg, long seed, int totalTicks, int sampleCount) {
        printHeader(cfg.name + " — 耐久度分布直方图 (" + sampleCount + "样本 × 随机负载)");

        int bins = 10;
        int[] histogram = new int[bins];
        double[] avgDeathTick = new double[bins];
        Random rng;

        // 对每个负载档位随机采样
        for (int s = 0; s < sampleCount; s++) {
            rng = new Random(seed + s);
            // 随机负载：在 [0.1×sweet, 1.5×peak] 之间均匀分布
            long load = cfg.sweetPower / 10 + rng.nextLong(cfg.peakPower * 3 / 2);
            Result r = simulate(rng, cfg, load, totalTicks);
            int bin = (int) (r.finalDurability * bins);
            if (bin >= bins) bin = bins - 1;
            histogram[bin]++;
            avgDeathTick[bin] += (r.deathTick == -1 ? totalTicks + 1 : r.deathTick);
        }

        System.out.printf("  %-10s %10s %10s%n", "耐久度区间", "样本数", "平均死亡Tick");
        System.out.println("  " + "-".repeat(36));
        for (int i = 0; i < bins; i++) {
            if (histogram[i] == 0) continue;
            double avg = 0;
            avgDeathTick[i] /= histogram[i];
            avg = avgDeathTick[i];
            String range = String.format("%.0f-%.0f%%", i * 100.0 / bins, (i + 1) * 100.0 / bins);
            String avgDeath = avg > totalTicks ? "存活" : String.format("%.0f", avg);
            System.out.printf("  %-10s %10d %10s%n", range, histogram[i], avgDeath);
        }
    }

    static void printLoadFactors(Config cfg) {
        printHeader("负载因子曲线 — " + cfg.name);
        System.out.printf("  %10s %12s %12s%n", "负载(J)", "sweet以上", "max以上");
        System.out.println("  " + "-".repeat(38));
        for (double pct = 0.1; pct <= 2.5; pct += 0.1) {
            long load = (long) (cfg.sweetPower * pct);
            double lf = calcLoadFactor(load, cfg);
            String mark = "";
            if (Math.abs(load - cfg.sweetPower) < 1) mark = " ←甜点";
            if (Math.abs(load - cfg.maxPower) < 1) mark = " ←最大";
            if (Math.abs(load - cfg.peakPower) < 1) mark = " ←峰值";
            System.out.printf("  %,10d %12.2f%n", load, lf);
        }
    }

    static void printAgeFactorCurve() {
        printHeader("老化加速因子曲线 (ageFactor — 全局)");
        System.out.printf("  %10s %10s%n", "耐久度", "ageFactor");
        System.out.println("  " + "-".repeat(24));
        for (double d = 0.0; d <= 1.0001; d += 0.05) {
            System.out.printf("  %9.0f%% %10.2f%n", d * 100.0, calcAgeFactor(d));
        }
    }

    static String formatTicks(long ticks) {
        if (ticks >= 1_000_000) return String.format("%.1fM ticks", ticks / 1_000_000.0);
        if (ticks >= 1_000) return String.format("%.1fK ticks", ticks / 1_000.0);
        return ticks + " ticks";
    }

    public static void main(String[] args) {
        int TICKS = 100_000;
        long SEED = 42;

        Config[] configs = {
            new Config("BASIC(简易)", 12, 40, 75, 72000),
            new Config("ENERGY(能源)", 24, 72, 160, 576000),
            new Config("POWERFUL(大功率)", 36, 100, 200, 144000),
            new Config("GILDED(镶金)", 100, 300, 500, 3456000),
            new Config("REINFORCED(强化)", 300, 750, 1200, 6912000),
            new Config("CARBONADO(碳金)", 512, 2000, 8000, 27648000),
            new Config("LONG_RANGE(长途)", 512, 2000, 8000, 27648000),
        };

        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║       Slimefun 连接器老化仿真 — 100K ticks 耐久度分布            ║");
        System.out.println("║   算法复现自 ConnectorAgingManager.handleNormalAging/Overload    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
        System.out.printf(
                "基础参数: DURA_PER_HIT=%.4f  TICK_DELAY=%d  baseProb=10000/lifetime%n%n", DURA_PER_HIT, TICK_DELAY);

        printAgeFactorCurve();

        for (Config cfg : configs) {
            printLoadFactors(cfg);
            printConnectorSimulation(cfg, SEED, TICKS);
            printAggregateHistogram(cfg, SEED, TICKS, 5000);
        }

        // 对比：所有连接器在甜点功率下的预期寿命 vs 仿真
        printHeader("预期寿命 vs 仿真寿命对比 (甜点功率 × 100K ticks)");
        System.out.printf("  %-20s %12s %12s %12s%n", "连接器", "baseProb", "预期寿命", "仿真耐久%");
        System.out.println("  " + "-".repeat(62));
        for (Config cfg : configs) {
            Random rng = new Random(SEED);
            Result r = simulate(rng, cfg, cfg.sweetPower, TICKS);
            double expectedTicksToDie =
                    (1.0 / DURA_PER_HIT) / (cfg.baseProb * calcLoadFactor(cfg.sweetPower, cfg) * 1.0);
            System.out.printf(
                    "  %-20s %12.6f %12.0f %11.1f%%%n",
                    cfg.name, cfg.baseProb, expectedTicksToDie, r.finalDurability * 100.0);
        }

        System.out.println("\n仿真完成。");
    }
}
