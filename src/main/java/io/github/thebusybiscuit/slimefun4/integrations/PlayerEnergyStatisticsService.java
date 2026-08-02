package io.github.thebusybiscuit.slimefun4.integrations;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * 负责维护玩家级 Slimefun 能源统计，并为 Plan 提供线程安全快照喵
 */
public final class PlayerEnergyStatisticsService {

    // 统计文件格式版本，后续变更字段时可以据此兼容升级喵
    private static final int SCHEMA_VERSION = 1;
    // 统计文件名称，和 Slimefun 的数据目录保持一致喵
    private static final String FILE_NAME = "plan-energy-statistics.yml";
    // 玩家统计节点名称，避免和文件元数据混在一起喵
    private static final String PLAYERS_PATH = "players";
    // 统计开始时间配置键，单位：毫秒时间戳喵
    private static final String STARTED_AT_PATH = "started-at";
    // 每个玩家累计发电量配置键，单位：Slimefun 能量单位喵
    private static final String PRODUCED_PATH = "produced";
    // 每个玩家累计耗电量配置键，单位：Slimefun 能量单位喵
    private static final String CONSUMED_PATH = "consumed";

    // 保存 Slimefun 主插件实例，用于记录持久化错误日志喵
    private final io.github.thebusybiscuit.slimefun4.implementation.Slimefun plugin;
    // 保存统计文件位置，避免每次读写都重新拼接路径喵
    private final File statisticsFile;
    // 保存内存中的累计统计，只有主线程更新这张表喵
    private final Map<UUID, MutableTotals> totalsByPlayer = new HashMap<>();
    // 保存对外发布的不可变快照，Plan 异步线程只读取这张表喵
    private volatile Map<UUID, PlayerEnergySnapshot> publishedSnapshots = Collections.emptyMap();
    // 使用单线程写入器，防止多个异步保存相互覆盖喵
    private final ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor(runnable -> {
        // 创建守护线程，避免统计服务异常阻止 JVM 退出喵
        Thread persistenceThread = new Thread(runnable, "Slimefun-Plan-Energy-Statistics");
        // 设置线程名称后返回给执行器使用喵
        persistenceThread.setDaemon(true);
        return persistenceThread;
    });
    // 标记当前是否有尚未落盘的累计数据喵
    private volatile boolean dirty;
    // 保存最近一次异步保存任务，关闭时等待它完成避免旧快照覆盖新快照喵
    private volatile java.util.concurrent.Future<?> pendingPersistenceTask;
    // 保存统计功能首次初始化时间，避免每次 flush 覆盖统计边界喵
    private long startedAt;
    // 保存周期刷盘任务 ID，关闭时主动取消避免访问已关闭服务喵
    private int flushTaskId = -1;
    // 标记服务是否已经关闭，防止关闭后继续提交异步写入喵
    private volatile boolean closed;

    /**
     * 创建玩家能源统计服务并准备统计文件喵
     *
     * @param plugin Slimefun 主插件实例喵
     */
    public PlayerEnergyStatisticsService(@Nonnull io.github.thebusybiscuit.slimefun4.implementation.Slimefun plugin) {
        // 保存调用方传入的插件实例喵
        this.plugin = plugin;
        // 将统计文件放入 Slimefun 数据目录，避免污染插件配置目录喵
        this.statisticsFile = new File(plugin.getDataFolder(), FILE_NAME);
    }

    /**
     * 加载已经保存的累计值，并为首次启用建立统计开始时间喵
     */
    public synchronized void load() {
        // 喵~防御：关闭后的服务不能重新加载，避免覆盖已经发布的快照喵
        if (closed) {
            return;
        }
        // 确保统计文件的父目录存在，目录创建失败时后续保存会报告明确错误喵
        File parentDirectory = statisticsFile.getParentFile();
        if (parentDirectory != null && !parentDirectory.exists() && !parentDirectory.mkdirs()) {
            // 记录目录创建失败，但继续以内存零值运行，避免影响 Slimefun 主功能喵
            plugin.getLogger().warning("无法创建 Plan 能源统计目录，将暂以内存模式运行喵");
        }
        // 文件不存在时从零开始，统计开始时间由当前功能启用时刻确定喵
        if (!statisticsFile.exists()) {
            // 记录本次功能首次启用时间，后续重启继续沿用该统计边界喵
            startedAt = System.currentTimeMillis();
            dirty = true;
            publishSnapshots();
            schedulePeriodicFlush();
            return;
        }
        // 读取 YAML 文件，读取异常时安全回退到空统计喵
        try {
            YamlConfiguration configuration = YamlConfiguration.loadConfiguration(statisticsFile);
            // 读取并校验文件格式版本，未知版本不强行解析以避免数据误读喵
            int schemaVersion = configuration.getInt("schema-version", SCHEMA_VERSION);
            if (schemaVersion != SCHEMA_VERSION) {
                plugin.getLogger().warning("Plan 能源统计文件版本不兼容，将从零开始读取喵");
                publishSnapshots();
                return;
            }
            // 恢复文件中记录的统计开始时间，缺失或非法时回退到当前时间喵
            startedAt = Math.max(0L, configuration.getLong(STARTED_AT_PATH, System.currentTimeMillis()));
            // 读取玩家统计节点，缺失节点时按空数据处理喵
            ConfigurationSection playersSection = configuration.getConfigurationSection(PLAYERS_PATH);
            if (playersSection != null) {
                // 遍历所有保存过的玩家 UUID 节点喵
                for (String playerKey : playersSection.getKeys(false)) {
                    try {
                        // 将节点名称解析为玩家 UUID，非法节点会被单独跳过喵
                        UUID playerUUID = UUID.fromString(playerKey);
                        // 读取累计发电量并对负数执行安全回退喵
                        long produced = Math.max(0L, playersSection.getLong(playerKey + "." + PRODUCED_PATH, 0L));
                        // 读取累计耗电量并对负数执行安全回退喵
                        long consumed = Math.max(0L, playersSection.getLong(playerKey + "." + CONSUMED_PATH, 0L));
                        // 恢复玩家累计值，实时值仍然从零开始喵
                        totalsByPlayer.put(playerUUID, new MutableTotals(produced, consumed));
                    } catch (IllegalArgumentException exception) {
                        // 喵~防御：单个非法 UUID 不应阻止其他玩家统计恢复喵
                        plugin.getLogger().log(Level.WARNING, "跳过非法 Plan 能源统计玩家节点: " + playerKey, exception);
                    }
                }
            }
            // 发布加载完成后的完整不可变快照喵
            publishSnapshots();
            // 启动周期刷盘，避免长时间运行只依赖关服保存喵
            schedulePeriodicFlush();
        } catch (Exception exception) {
            // 喵~防御：文件损坏或权限异常时回退为空统计，保证 Plan provider 不崩溃喵
            plugin.getLogger().log(Level.WARNING, "读取 Plan 能源统计文件失败，将暂从零开始统计喵", exception);
            totalsByPlayer.clear();
            publishSnapshots();
            schedulePeriodicFlush();
        }
    }

    /**
     * 记录一个完整 EnergyNet 网络 tick 的玩家发电与耗电增量喵
     *
     * @param producedByPlayer 本 tick 按玩家聚合的发电量喵
     * @param consumedByPlayer 本 tick 按玩家聚合的耗电量喵
     */
    public synchronized void recordTick(
            @Nonnull Map<UUID, Long> producedByPlayer, @Nonnull Map<UUID, Long> consumedByPlayer) {
        // 喵~防御：关闭后拒绝新数据，避免关闭流程中产生不可保存的尾部统计喵
        if (closed) {
            return;
        }
        // 建立本 tick 的实时值表，避免复用调用方可变 Map 喵
        Map<UUID, MutableCurrent> currentByPlayer = new HashMap<>();
        // 清理旧实时值，确保本次没有活动的玩家返回零喵
        for (MutableTotals totals : totalsByPlayer.values()) {
            totals.currentProduced = 0L;
            totals.currentConsumed = 0L;
        }
        // 应用发电增量并更新对应玩家的累计值喵
        applyDelta(producedByPlayer, currentByPlayer, true);
        // 应用耗电增量并更新对应玩家的累计值喵
        applyDelta(consumedByPlayer, currentByPlayer, false);
        // 发布本 tick 完整快照，Plan 异步线程只观察到旧快照或新快照喵
        publishSnapshots();
        // 标记累计值发生变化，等待异步保存喵
        if (!producedByPlayer.isEmpty() || !consumedByPlayer.isEmpty()) {
            dirty = true;
        }
    }

    /**
     * 读取指定玩家的能源统计快照喵
     *
     * @param playerUUID 玩家 UUID 喵
     * @return 不可变统计快照，不存在时返回四项零值喵
     */
    @Nonnull
    public PlayerEnergySnapshot getSnapshot(@Nonnull UUID playerUUID) {
        // 从 volatile 不可变 Map 读取，避免 Plan provider 触碰主线程对象喵
        PlayerEnergySnapshot snapshot = publishedSnapshots.get(playerUUID);
        // 喵~防御：玩家尚未产生统计时返回安全的零快照喵
        return snapshot == null ? PlayerEnergySnapshot.ZERO : snapshot;
    }

    /**
     * 异步保存当前累计统计，实时值不会写入文件喵
     */
    public void flushAsync() {
        // 喵~防御：非服务器线程不能直接触碰 Bukkit 调度器，先切回主线程再执行 flush 喵
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::flushAsync);
            return;
        }
        // 关闭后不再提交新的保存任务喵
        if (closed || !dirty) {
            return;
        }
        // 在主线程锁内复制持久化所需数据，异步线程只操作副本喵
        Map<UUID, MutableTotals> copy;
        synchronized (this) {
            copy = copyTotals();
            dirty = false;
        }
        // 将文件写入放到单独线程，避免阻塞 EnergyNet 主线程喵
        pendingPersistenceTask = persistenceExecutor.submit(() -> saveCopy(copy));
    }

    /**
     * 同步保存当前累计统计并关闭写入线程喵
     */
    public void close() {
        // 喵~防御：关闭方法允许重复调用，避免生命周期异常导致二次关闭崩溃喵
        if (closed) {
            return;
        }
        // 先标记关闭，禁止新的 tick 继续提交统计喵
        closed = true;
        // 喵~防御：关闭前取消周期任务，防止任务继续访问已关闭的统计服务喵
        if (flushTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(flushTaskId);
            flushTaskId = -1;
        }
        // 在关闭前复制最新累计值，确保最后一批数据不会依赖异步任务调度喵
        Map<UUID, MutableTotals> copy;
        synchronized (this) {
            copy = copyTotals();
            dirty = false;
        }
        // 等待此前已经提交的异步保存任务，避免旧副本覆盖最新数据喵
        java.util.concurrent.Future<?> pendingTask = pendingPersistenceTask;
        if (pendingTask != null) {
            try {
                pendingTask.get(5, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception exception) {
                // 喵~防御：旧保存任务失败时继续执行最终同步保存，优先保住最新内存数据喵
                plugin.getLogger().log(Level.WARNING, "等待 Plan 能源统计异步保存失败喵", exception);
            }
        }
        // 等待已有保存任务完成后再写入最终副本喵
        persistenceExecutor.shutdown();
        try {
            if (!persistenceExecutor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                persistenceExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            // 喵~防御：关闭线程被中断时立即停止写入并恢复中断标记喵
            persistenceExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        // 最终同步写入，保证关闭时仍能保存最新内存值喵
        saveCopy(copy);
    }

    // 启动每分钟一次的异步刷盘任务，任务只负责调度安全保存喵
    private void schedulePeriodicFlush() {
        // 喵~防御：重复加载时不重复创建刷盘任务喵
        if (flushTaskId >= 0) {
            return;
        }
        // 使用 Bukkit 调度器让保存任务在服务生命周期内自动运行喵
        flushTaskId = Bukkit.getScheduler()
                .runTaskTimerAsynchronously(plugin, this::flushAsync, 20L * 60L, 20L * 60L)
                .getTaskId();
    }

    // 应用一组玩家 delta，并区分发电和耗电字段喵
    private void applyDelta(
            Map<UUID, Long> deltaByPlayer, Map<UUID, MutableCurrent> currentByPlayer, boolean produced) {
        // 遍历调用方提供的玩家 delta 喵
        for (Map.Entry<UUID, Long> entry : deltaByPlayer.entrySet()) {
            // 读取本次增量值喵
            Long deltaValue = entry.getValue();
            // 喵~防御：空 UUID、空数值、非正数都不计入统计喵
            if (entry.getKey() == null || deltaValue == null || deltaValue <= 0L) {
                continue;
            }
            // 获取或创建玩家累计对象喵
            MutableTotals totals = totalsByPlayer.computeIfAbsent(entry.getKey(), ignored -> new MutableTotals(0L, 0L));
            // 获取或创建玩家实时对象喵
            MutableCurrent current = currentByPlayer.computeIfAbsent(entry.getKey(), ignored -> new MutableCurrent());
            // 根据指标类型执行饱和累加，禁止 long 溢出回绕喵
            if (produced) {
                totals.totalProduced = saturatingAdd(totals.totalProduced, deltaValue);
                totals.currentProduced = saturatingAdd(0L, deltaValue);
                current.produced = saturatingAdd(current.produced, deltaValue);
            } else {
                totals.totalConsumed = saturatingAdd(totals.totalConsumed, deltaValue);
                totals.currentConsumed = saturatingAdd(0L, deltaValue);
                current.consumed = saturatingAdd(current.consumed, deltaValue);
            }
        }
    }

    // 创建不可变快照 Map，确保四个 provider 读取同一个统计版本喵
    private void publishSnapshots() {
        // 创建新的快照容器，避免修改已经发布的 Map 喵
        Map<UUID, PlayerEnergySnapshot> snapshots = new HashMap<>();
        // 将所有累计对象转换成不可变快照喵
        for (Map.Entry<UUID, MutableTotals> entry : totalsByPlayer.entrySet()) {
            MutableTotals totals = entry.getValue();
            snapshots.put(
                    entry.getKey(),
                    new PlayerEnergySnapshot(
                            totals.totalProduced,
                            totals.currentProduced,
                            totals.totalConsumed,
                            totals.currentConsumed));
        }
        // 发布不可修改的 Map，Plan 读取时不会遇到结构变化喵
        publishedSnapshots = Collections.unmodifiableMap(snapshots);
    }

    // 复制累计数据，避免异步保存时读取正在变化的可变对象喵
    private Map<UUID, MutableTotals> copyTotals() {
        // 创建独立副本容器喵
        Map<UUID, MutableTotals> copy = new HashMap<>();
        // 复制每个玩家的累计值，故意丢弃实时值喵
        for (Map.Entry<UUID, MutableTotals> entry : totalsByPlayer.entrySet()) {
            MutableTotals totals = entry.getValue();
            copy.put(entry.getKey(), new MutableTotals(totals.totalProduced, totals.totalConsumed));
        }
        // 返回异步线程专用副本喵
        return copy;
    }

    // 保存一份已经脱离主线程状态的累计数据副本喵
    private void saveCopy(Map<UUID, MutableTotals> copy) {
        // 创建新的 YAML 配置对象，避免并发修改旧配置喵
        YamlConfiguration configuration = new YamlConfiguration();
        // 写入文件格式版本喵
        configuration.set("schema-version", SCHEMA_VERSION);
        // 写入统计开始时间，确保重启不会改变本功能的数据边界喵
        configuration.set(STARTED_AT_PATH, startedAt <= 0L ? System.currentTimeMillis() : startedAt);
        // 遍历玩家累计数据并写入 long 值喵
        for (Map.Entry<UUID, MutableTotals> entry : copy.entrySet()) {
            String playerPath = PLAYERS_PATH + "." + entry.getKey();
            configuration.set(playerPath + "." + PRODUCED_PATH, entry.getValue().totalProduced);
            configuration.set(playerPath + "." + CONSUMED_PATH, entry.getValue().totalConsumed);
        }
        // 先写临时文件，降低进程中断造成主文件损坏的概率喵
        File temporaryFile = new File(statisticsFile.getPath() + ".tmp");
        try {
            configuration.save(temporaryFile);
            // 删除旧文件后替换为最新临时文件，失败时保留临时文件供人工恢复喵
            if (statisticsFile.exists() && !statisticsFile.delete()) {
                throw new IOException("无法删除旧 Plan 能源统计文件");
            }
            if (!temporaryFile.renameTo(statisticsFile)) {
                throw new IOException("无法替换 Plan 能源统计文件");
            }
        } catch (Exception exception) {
            // 喵~防御：持久化失败只记录错误，不让能源网络线程崩溃喵
            plugin.getLogger().log(Level.SEVERE, "保存 Plan 能源统计文件失败喵", exception);
        }
    }

    // 执行不会溢出的正数 long 加法，达到上限后固定为 Long.MAX_VALUE 喵
    private static long saturatingAdd(long currentValue, long deltaValue) {
        // 喵~防御：负数或零增量不改变累计结果喵
        if (deltaValue <= 0L) {
            return Math.max(0L, currentValue);
        }
        // 喵~防御：当前值异常为负数时从零开始计算喵
        long safeCurrentValue = Math.max(0L, currentValue);
        // 检查相加是否会超过 long 最大值喵
        if (safeCurrentValue > Long.MAX_VALUE - deltaValue) {
            return Long.MAX_VALUE;
        }
        // 返回安全相加结果喵
        return safeCurrentValue + deltaValue;
    }

    // 保存可变累计字段，实时字段只存在内存中喵
    private static final class MutableTotals {
        // 玩家累计发电量，单位：Slimefun 能量单位喵
        private long totalProduced;
        // 玩家累计耗电量，单位：Slimefun 能量单位喵
        private long totalConsumed;
        // 玩家最近一次采样发电量，单位：Slimefun 能量单位喵
        private long currentProduced;
        // 玩家最近一次采样耗电量，单位：Slimefun 能量单位喵
        private long currentConsumed;

        // 创建累计对象并初始化两个累计字段喵
        private MutableTotals(long totalProduced, long totalConsumed) {
            this.totalProduced = totalProduced;
            this.totalConsumed = totalConsumed;
        }
    }

    // 保存一次 tick 内玩家的实时发电和耗电增量喵
    private static final class MutableCurrent {
        // 当前 tick 发电量，单位：Slimefun 能量单位喵
        private long produced;
        // 当前 tick 耗电量，单位：Slimefun 能量单位喵
        private long consumed;
    }

    /**
     * 对外暴露的玩家能源统计不可变快照喵
     */
    public static final class PlayerEnergySnapshot {
        // 玩家不存在时返回的零快照喵
        public static final PlayerEnergySnapshot ZERO = new PlayerEnergySnapshot(0L, 0L, 0L, 0L);
        // 累计发电量，单位：Slimefun 能量单位喵
        private final long totalProduced;
        // 最近一次 tick 发电量，单位：Slimefun 能量单位喵
        private final long currentProduced;
        // 累计耗电量，单位：Slimefun 能量单位喵
        private final long totalConsumed;
        // 最近一次 tick 耗电量，单位：Slimefun 能量单位喵
        private final long currentConsumed;

        // 创建不可变快照并保存四个 long 字段喵
        private PlayerEnergySnapshot(
                long totalProduced, long currentProduced, long totalConsumed, long currentConsumed) {
            this.totalProduced = totalProduced;
            this.currentProduced = currentProduced;
            this.totalConsumed = totalConsumed;
            this.currentConsumed = currentConsumed;
        }

        // 返回累计发电量喵
        public long getTotalProduced() {
            return totalProduced;
        }

        // 返回最近一次 tick 发电量喵
        public long getCurrentProduced() {
            return currentProduced;
        }

        // 返回累计耗电量喵
        public long getTotalConsumed() {
            return totalConsumed;
        }

        // 返回最近一次 tick 耗电量喵
        public long getCurrentConsumed() {
            return currentConsumed;
        }
    }
}
