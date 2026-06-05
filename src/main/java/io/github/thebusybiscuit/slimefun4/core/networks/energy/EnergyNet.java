package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.ErrorReport;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.network.Network;
import io.github.thebusybiscuit.slimefun4.api.network.NetworkComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.LongRangeConnector;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/**
 * The {@link EnergyNet} is an implementation of {@link Network} that deals with
 * electrical energy being sent from and to nodes.
 *
 * @author meiamsome
 * @author TheBusyBiscuit
 *
 * @see Network
 * @see EnergyNetComponent
 * @see EnergyNetProvider
 * @see EnergyNetComponentType
 *
 */
public class EnergyNet extends Network implements HologramOwner {

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_PATHS = false;
    private static final int RANGE = 6;

    public static boolean isDebugEnabled() {
        return DEBUG;
    }

    // BFS调试日志计数器：每条pathDebugLog只打印前N次，防止刷屏
    private static final int MAX_BFS_DEBUG_LOG = 200;
    private final AtomicInteger bfsDebugLogCount = new AtomicInteger(0);

    private static void debugLog(String msg) {
        if (DEBUG) {
            String fullMsg = ChatColors.color("&e[电网调试] &f" + msg);
            Slimefun.logger().info("[EnergyNet-DEBUG] " + msg);
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.isOp() || p.hasPermission("slimefun.debug")) {
                    p.sendMessage(fullMsg);
                }
            }
        }
    }

    private static void debugPathLog(String msg) {
        if (DEBUG_PATHS) {
            String fullMsg = ChatColors.color("&e[电网调试] &f" + msg);
            Slimefun.logger().info("[EnergyNet-DEBUG] " + msg);
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.isOp() || p.hasPermission("slimefun.debug")) {
                    p.sendMessage(fullMsg);
                }
            }
        }
    }

    private boolean debugPathLogLimited(String msg) {
        if (DEBUG_PATHS && bfsDebugLogCount.getAndIncrement() < MAX_BFS_DEBUG_LOG) {
            String fullMsg = ChatColors.color("&e[电网调试] &f" + msg);
            Slimefun.logger().info("[EnergyNet-DEBUG] " + msg);
            for (org.bukkit.entity.Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (p.isOp() || p.hasPermission("slimefun.debug")) {
                    p.sendMessage(fullMsg);
                }
            }
            return true;
        }
        return false;
    }

    private static final Vector HOLOGRAM_OFFSET = new Vector(0.5, 0.75, 0.5);
    private static final Vector MULTILINE_HOLOGRAM_OFFSET = new Vector(0.5, 1.35, 0.5);

    /**
     * 静态辅助方法：清除指定位置的悬浮字（不需要电网实例）
     */
    public static void removeHologramAt(Location loc) {
        Location hologramLoc = loc.clone().add(HOLOGRAM_OFFSET);
        Slimefun.getHologramsService().removeHologram(hologramLoc);
        Slimefun.getHologramsService().removeMultiLineHologram(loc.clone().add(MULTILINE_HOLOGRAM_OFFSET));
    }

    @Nonnull
    @Override
    public Vector getHologramOffset(@Nonnull Block block) {
        return HOLOGRAM_OFFSET;
    }

    @Override
    public void updateMultiLineHologram(@Nonnull Block b, @Nonnull String... lines) {
        Location multilineLoc = b.getLocation().add(MULTILINE_HOLOGRAM_OFFSET);
        Location singleLoc = b.getLocation().add(HOLOGRAM_OFFSET);
        Slimefun.getHologramsService().removeHologram(singleLoc);
        Slimefun.getHologramsService().setMultiLineHologram(multilineLoc, lines);
    }

    @Override
    public void removeMultiLineHologram(@Nonnull Block b) {
        Location loc = b.getLocation().add(MULTILINE_HOLOGRAM_OFFSET);
        Slimefun.getHologramsService().removeMultiLineHologram(loc);
    }

    private final Map<Location, EnergyNetProvider> generators = new HashMap<>();
    private final Map<Location, EnergyNetComponent> capacitors = new HashMap<>();
    private final Map<Location, EnergyNetComponent> consumers = new HashMap<>();

    // 新规格说明添加的字段
    private final Map<Location, EnergyNetComponent> connectors = new HashMap<>();
    private final Map<Location, Long> connectorLoad = new HashMap<>();
    private final Map<Location, Long> connectorLimits = new ConcurrentHashMap<>();
    private final Map<Location, Map<Integer, AxisTarget>> longRangeAxisTargets = new HashMap<>();
    private int longRangeCheckTickCounter = 0;
    private final Map<Location, Set<EnergyPath>> generatorPaths = new HashMap<>();
    private final Map<Location, Set<EnergyPath>> capacitorPaths = new HashMap<>();
    private final Map<Location, Map<Location, Set<EnergyPath>>> generatorToCapacitorPaths = new HashMap<>();
    private final Map<Location, Long> nonChargeableSupply = new HashMap<>();
    private volatile boolean initializing = false;
    private volatile boolean initialized = false;
    private volatile boolean pendingInit = false;
    private volatile boolean abortRequested = false;
    private volatile boolean destroyed = false;
    private volatile boolean conflictMode = false;
    private volatile EnergyNet conflictPartner;
    private final Set<Location> conflictHologramTargets = ConcurrentHashMap.newKeySet();

    private volatile int initTotalWork = 0;
    private volatile int initWorkDone = 0;
    private volatile int pathSourcesDone = 0;
    private volatile long netNewEnergy = 0;

    private long totalProducedThisTick = 0;
    private long totalConsumedThisTick = 0;
    private long totalStoredThisTick = 0;
    private long totalNetStoredThisTick = 0;
    private long lastTotalCharge = 0;

    private int selfTickTaskId = -1;
    private long lastSupply;
    private long lastDemand;
    private volatile boolean firstTickDone = false;
    private final AtomicBoolean selfTicking = new AtomicBoolean(false);
    private final AtomicInteger skipTicks = new AtomicInteger(0);
    private int tickInterval;
    private int initRetryCount = 0;

    private final AtomicInteger bfsDbQueryCount = new AtomicInteger(0);
    private static final int BFS_DB_QUERY_THROTTLE = 10;
    private static final int MAX_BFS_NODES = 100_000;
    private final Set<Location> bfsDbQueried = new HashSet<>();

    private static final ExecutorService GRID_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Slimefun-Grid-Init");
        return t;
    });

    private static final ExecutorService GRID_TICK_EXECUTOR =
            Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), r -> {
                Thread t = new Thread(r, "Slimefun-Grid-Tick");
                t.setDaemon(true);
                return t;
            });

    static {
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            shutdownExecutor(GRID_EXECUTOR);
                            shutdownExecutor(GRID_TICK_EXECUTOR);
                        },
                        "Slimefun-Grid-Shutdown"));
    }

    private static void shutdownExecutor(@Nonnull ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    protected EnergyNet(@Nonnull Location l) {
        super(Slimefun.getNetworkManager(), l);
    }

    @Override
    public int getRange() {
        return RANGE;
    }

    /**
     * This creates an immutable {@link Map} of {@link EnergyNetProvider}s within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of generators
     */
    public @Nonnull Map<Location, EnergyNetProvider> getGenerators() {
        return Collections.unmodifiableMap(generators);
    }

    /**
     * This creates an immutable {@link Map} of {@link EnergyNetComponentType#CAPACITOR} {@link EnergyNetComponent}s within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of capacitors
     */
    public @Nonnull Map<Location, EnergyNetComponent> getCapacitors() {
        return Collections.unmodifiableMap(capacitors);
    }

    /**
     * This creates an immutable {@link Map} of {@link EnergyNetComponentType#CONSUMER} {@link EnergyNetComponent}s within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of consumers
     */
    public @Nonnull Map<Location, EnergyNetComponent> getConsumers() {
        return Collections.unmodifiableMap(consumers);
    }

    /**
     * This creates an immutable {@link Map} of {@link EnergyNetComponentType#CONNECTOR} {@link EnergyNetComponent}s within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of connectors
     */
    public @Nonnull Map<Location, EnergyNetComponent> getConnectors() {
        return Collections.unmodifiableMap(connectors);
    }

    /**
     * This creates an immutable {@link Map} of connector loads within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of connector loads
     */
    public @Nonnull Map<Location, Long> getConnectorLoad() {
        return Collections.unmodifiableMap(connectorLoad);
    }

    /**
     * This creates an immutable {@link Map} of generator energy paths within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of generator paths
     */
    public @Nonnull Map<Location, Set<EnergyPath>> getGeneratorPaths() {
        return Collections.unmodifiableMap(generatorPaths);
    }

    /**
     * This creates an immutable {@link Map} of capacitor energy paths within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of capacitor paths
     */
    public @Nonnull Map<Location, Set<EnergyPath>> getCapacitorPaths() {
        return Collections.unmodifiableMap(capacitorPaths);
    }

    /**
     * This creates an immutable {@link Map} of generator-to-capacitor energy paths within this {@link EnergyNet} instance.
     *
     * @return An immutable {@link Map} of generator-to-capacitor paths
     */
    public @Nonnull Map<Location, Map<Location, Set<EnergyPath>>> getGeneratorToCapacitorPaths() {
        return Collections.unmodifiableMap(generatorToCapacitorPaths);
    }

    /**
     * Gets the {@link Location} of the regulator of this {@link EnergyNet}.
     *
     * @return The regulator location
     */
    public @Nonnull Location getRegulator() {
        return regulator;
    }

    /**
     * Checks whether this {@link EnergyNet} is currently initializing.
     *
     * @return true if initializing
     */
    public boolean isInitializing() {
        return initializing;
    }

    /**
     * Checks whether this {@link EnergyNet} has been initialized.
     *
     * @return true if initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public @Nonnull String getId() {
        return "ENERGY_NETWORK";
    }

    @Override
    public NetworkComponent classifyLocation(@Nonnull Location l) {
        if (regulator.equals(l)) {
            return NetworkComponent.REGULATOR;
        }

        EnergyNetComponent component = getComponent(l);

        if (component == null) {
            return null;
        } else {
            // 检查机器是否损坏
            var data = StorageCacheUtils.getDataContainer(l);
            if (data != null && Slimefun.getMachineDamageService().isMachineDamaged(data)) {
                // 损坏的机器不能作为连接器
                if (component.getEnergyComponentType() == EnergyNetComponentType.CAPACITOR
                        || component.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
                    return null;
                }
            }

            return switch (component.getEnergyComponentType()) {
                case CONNECTOR -> NetworkComponent.CONNECTOR;
                case CAPACITOR -> NetworkComponent.CONNECTOR;
                case CONSUMER, GENERATOR -> NetworkComponent.TERMINUS;
                default -> null;
            };
        }
    }

    @Override
    public void onClassificationChange(Location l, NetworkComponent from, NetworkComponent to) {
        if (from == NetworkComponent.TERMINUS) {
            generators.remove(l);
            consumers.remove(l);
        } else if (from == NetworkComponent.CONNECTOR) {
            capacitors.remove(l);
            connectors.remove(l);
            connectorLoad.remove(l);
        }

        EnergyNetComponent component = getComponent(l);

        if (component != null) {
            switch (component.getEnergyComponentType()) {
                case CAPACITOR:
                    capacitors.put(l, component);
                    break;
                case CONSUMER:
                    consumers.put(l, component);
                    break;
                case GENERATOR:
                    if (component instanceof EnergyNetProvider provider) {
                        generators.put(l, provider);
                    } else if (component instanceof SlimefunItem item) {
                        item.warn("This Item is marked as a GENERATOR but does not implement the interface"
                                + " EnergyNetProvider!");
                    }
                    break;
                case CONNECTOR:
                    connectors.put(l, component);
                    connectorLoad.put(l, 0L);
                    break;
                default:
                    break;
            }
        }
    }

    private void discoverStep() {
        int maxSteps = manager.getMaxSize();
        int steps = 0;

        while (nodeQueue.peek() != null) {
            Location l = nodeQueue.poll();
            NetworkComponent currentAssignment = getCurrentClassification(l);
            NetworkComponent classification = classifyLocation(l);

            if (classification != currentAssignment) {
                if (currentAssignment == NetworkComponent.REGULATOR
                        || currentAssignment == NetworkComponent.CONNECTOR) {
                    // Requires a complete rebuild of the network, so we just throw the current one away.
                    manager.unregisterNetwork(this);
                    return;
                } else if (currentAssignment == NetworkComponent.TERMINUS) {
                    terminusNodes.remove(l);
                }

                if (classification == NetworkComponent.REGULATOR) {
                    regulatorNodes.add(l);
                    discoverNeighbors(l);
                } else if (classification == NetworkComponent.CONNECTOR) {
                    connectorNodes.add(l);
                    EnergyNetComponent component = getComponent(l);
                    if (component != null && component.getEnergyComponentType() == EnergyNetComponentType.CAPACITOR) {
                        discoverCapacitorNeighbors(l);
                    } else if (component != null) {
                        int range = component.getRange();
                        discoverNeighbors(l, 1.0, 0.0, 0.0, range);
                        discoverNeighbors(l, -1.0, 0.0, 0.0, range);
                        discoverNeighbors(l, 0.0, 1.0, 0.0, range);
                        discoverNeighbors(l, 0.0, -1.0, 0.0, range);
                        discoverNeighbors(l, 0.0, 0.0, 1.0, range);
                        discoverNeighbors(l, 0.0, 0.0, -1.0, range);
                    }
                } else if (classification == NetworkComponent.TERMINUS) {
                    terminusNodes.add(l);
                }

                onClassificationChange(l, currentAssignment, classification);
            }

            steps += 1;

            if (steps >= maxSteps) {
                break;
            }
        }
    }

    private void discoverCapacitorNeighbors(@Nonnull Location l) {
        // 电容只能连接周围1格的电容
        discoverNeighbors(l, 1.0, 0.0, 0.0, 1);
        discoverNeighbors(l, -1.0, 0.0, 0.0, 1);
        discoverNeighbors(l, 0.0, 1.0, 0.0, 1);
        discoverNeighbors(l, 0.0, -1.0, 0.0, 1);
        discoverNeighbors(l, 0.0, 0.0, 1.0, 1);
        discoverNeighbors(l, 0.0, 0.0, -1.0, 1);
    }

    private void discoverNeighbors(@Nonnull Location l, double xDiff, double yDiff, double zDiff, int range) {
        for (int i = range; i > 0; i--) {
            Location newLocation = l.clone().add(i * xDiff, i * yDiff, i * zDiff);
            addLocationToNetwork(newLocation);
        }
    }

    @Nullable private NetworkComponent getCurrentClassification(@Nonnull Location l) {
        if (regulatorNodes.contains(l)) {
            return NetworkComponent.REGULATOR;
        } else if (connectorNodes.contains(l)) {
            return NetworkComponent.CONNECTOR;
        } else if (terminusNodes.contains(l)) {
            return NetworkComponent.TERMINUS;
        }

        return null;
    }

    private void discoverNeighbors(@Nonnull Location l) {
        discoverNeighbors(l, 1.0, 0.0, 0.0, getRange());
        discoverNeighbors(l, -1.0, 0.0, 0.0, getRange());
        discoverNeighbors(l, 0.0, 1.0, 0.0, getRange());
        discoverNeighbors(l, 0.0, -1.0, 0.0, getRange());
        discoverNeighbors(l, 0.0, 0.0, 1.0, getRange());
        discoverNeighbors(l, 0.0, 0.0, -1.0, getRange());
    }

    public void tick(@Nonnull Block b, SlimefunBlockData blockData) {
        AtomicLong timestamp = new AtomicLong(Slimefun.getProfiler().newEntry());
        try {
            if (!regulator.equals(b.getLocation())) {
                debugLog("tick: 调节器不匹配，预期=" + formatLocation(regulator) + " 实际=" + formatLocation(b.getLocation()));
                removeMultiLineHologram(b);
                updateHologram(b, "&c电网冲突：多个能源调节器相连", blockData::isPendingRemove);
                if (initializing) {
                    abortRequested = true;
                    initializing = false;
                }
                return;
            }

            if (conflictMode) {
                removeMultiLineHologram(b);
                updateHologram(b, "&c电网冲突：电网交叉", () -> false);
                return;
            }

            if (!initialized) {
                removeMultiLineHologram(b);
                if (!initializing && !pendingInit) {
                    pendingInit = true;
                    debugLog("tick: 未初始化，提交异步初始化任务");
                    GRID_EXECUTOR.submit(this::initializeNetworkAsync);
                } else if (pendingInit) {
                    updateHologram(b, "&e初始化排队中", blockData::isPendingRemove);
                }
                return;
            }

            if (initializing) {
                return;
            }

            boolean hasMembers = !connectorNodes.isEmpty() || !terminusNodes.isEmpty();
            if (!hasMembers) {
                debugLog("tick: connectorNodes和terminusNodes均为空，但regulatorNodes=" + regulatorNodes.size()
                        + " initialized=" + initialized);
                if (!regulatorNodes.isEmpty()) {
                    removeMultiLineHologram(b);
                    updateHologram(b, "&7电网已就绪，等待接入设备", blockData::isPendingRemove);
                } else {
                    removeMultiLineHologram(b);
                    updateHologram(b, "&4找不到能源网络", blockData::isPendingRemove);
                }
            } else if (!firstTickDone) {
                removeMultiLineHologram(b);
                updateHologram(b, "&e初始化完成，等待首个tick数据...", blockData::isPendingRemove);
            } else {
                updateHologram(blockData, lastSupply, lastDemand);
            }
        } finally {
            Slimefun.getProfiler()
                    .closeEntry(b.getLocation(), SlimefunItems.ENERGY_REGULATOR.getItem(), timestamp.get());
        }
    }

    private long storeRemainingEnergy(long remainingEnergy) {
        debugLog("storeRemainingEnergy: 开始存储剩余能量 " + remainingEnergy + "J, 电容数=" + capacitors.size());
        long totalStored = 0;

        Map<Location, Long> storageLoads = new HashMap<>();

        // 构建电容充电优先级：从generatorToCapacitorPaths获取每电容的最短路径长度
        Map<Location, Integer> capPriority = new HashMap<>();
        for (Map<Location, Set<EnergyPath>> capPaths : generatorToCapacitorPaths.values()) {
            for (Map.Entry<Location, Set<EnergyPath>> entry : capPaths.entrySet()) {
                int minLen = Integer.MAX_VALUE;
                for (EnergyPath p : entry.getValue()) {
                    if (p.length < minLen) minLen = p.length;
                }
                capPriority.merge(entry.getKey(), minLen, Math::min);
            }
        }

        debugLog("storeRemainingEnergy: capPriority | generators=" + generators.size() + " genToCapPaths="
                + generatorToCapacitorPaths.size());
        for (Map.Entry<Location, Integer> entry : capPriority.entrySet()) {
            debugLog("storeRemainingEnergy: capPriority[" + formatLocation(entry.getKey()) + "] = " + entry.getValue()
                    + " hops");
        }

        // 按路径长度排序（短路径优先），没有路径的电容排最后
        List<Location> sortedCaps = new ArrayList<>(capacitors.keySet());
        sortedCaps.sort((a, b) -> {
            int pa = capPriority.getOrDefault(a, Integer.MAX_VALUE);
            int pb = capPriority.getOrDefault(b, Integer.MAX_VALUE);
            return Integer.compare(pa, pb);
        });

        debugLog("storeRemainingEnergy: sortedCaps顺序 (共" + sortedCaps.size() + "个):");
        int sortIdx = 0;
        for (Location loc : sortedCaps) {
            int pri = capPriority.getOrDefault(loc, -1);
            long capCharge = -1;
            EnergyNetComponent comp = capacitors.get(loc);
            if (comp != null) {
                capCharge = comp.getChargeLong(loc);
            }
            debugLog("storeRemainingEnergy:   #" + (sortIdx++) + " " + formatLocation(loc) + " hops=" + pri + " charge="
                    + capCharge);
        }

        for (Location loc : sortedCaps) {
            EnergyNetComponent component = capacitors.get(loc);

            var data = StorageCacheUtils.getDataContainer(loc);
            if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
                debugLog("storeRemainingEnergy电容: 跳过 " + formatLocation(loc) + " (数据不可用)");
                continue;
            }

            // 检查机器是否损坏，如果损坏则跳过处理
            if (Slimefun.getMachineDamageService().isMachineDamaged(data)) {
                debugLog("storeRemainingEnergy电容: 跳过 " + formatLocation(loc) + " (已损坏)");
                continue;
            }

            // 修复物品机制与机器损坏机制一致
            if (!((SlimefunItem) component).getId().equals(data.getSfId())) {
                var newItem = SlimefunItem.getById(data.getSfId());
                if (!(newItem instanceof EnergyNetComponent newComponent)
                        || newComponent.getEnergyComponentType() != EnergyNetComponentType.CAPACITOR) {
                    debugLog("storeRemainingEnergy电容: 跳过 " + formatLocation(loc) + " (ID不匹配)");
                    continue;
                }
                capacitors.put(loc, newComponent);
                component = newComponent;
            }

            SlimefunItem item = (SlimefunItem) component;
            long oldCharge = component.getChargeLong(loc);

            List<EnergyPath> allCapPaths = new ArrayList<>();
            for (Map<Location, Set<EnergyPath>> pathsByGen : generatorToCapacitorPaths.values()) {
                Set<EnergyPath> capSet = pathsByGen.get(loc);
                if (capSet != null) {
                    allCapPaths.addAll(capSet);
                }
            }
            if (allCapPaths.isEmpty()) {
                debugLog("storeRemainingEnergy电容: 跳过 " + formatLocation(loc) + " (无BFS路径，不传输)");
                continue;
            }

            if (remainingEnergy > 0) {
                long capacity = component.getChargeCapacityLong(loc);
                long canStore = capacity - oldCharge;
                debugLog("storeRemainingEnergy电容: " + formatLocation(loc) + " old=" + oldCharge + " cap=" + capacity
                        + " canStore=" + canStore + " remaining=" + remainingEnergy);

                if (canStore <= 0) {
                    continue;
                }

                long limitAvailable = computeCapStorageLimitAvailable(loc, storageLoads);
                if (limitAvailable <= 0) {
                    debugLog("storeRemainingEnergy电容: " + formatLocation(loc) + " 被连接器限流阻止 (limitAvailable=0)");
                    continue;
                }
                canStore = Math.min(canStore, limitAvailable);
                if (limitAvailable < capacity - oldCharge) {
                    debugLog("storeRemainingEnergy电容: " + formatLocation(loc) + " canStore被限制为 " + canStore + " (原始="
                            + (capacity - oldCharge) + ")");
                }

                long actualStore = Math.min(remainingEnergy, canStore);
                if (actualStore <= 0) continue;

                component.setCharge(loc, oldCharge + actualStore);
                remainingEnergy -= actualStore;
                totalStored += actualStore;

                recordCapStorageLoads(loc, actualStore, storageLoads);
            }
            long newCharge = component.getChargeLong(loc);
            debugLog("storeRemainingEnergy: → @" + formatLocation(loc) + " stored=" + (newCharge - oldCharge)
                    + " charge=" + oldCharge + "→" + newCharge + " remaining=" + remainingEnergy);

            long chargeDiff = Math.abs(newCharge - oldCharge);
            long capacity = component.getChargeCapacityLong(loc);

            if (capacity > 0 && chargeDiff > 0) {
                // 获取当前计数器值
                double chargeCounter = 0.0;
                String counterValue = data.getData("machine_damage_charge_counter");
                if (counterValue != null) {
                    try {
                        chargeCounter = Double.parseDouble(counterValue);
                    } catch (NumberFormatException e) {
                        chargeCounter = 0.0;
                    }
                }

                // 累加充放电量
                chargeCounter += chargeDiff;

                // 计算电容量的1%
                double capacityPercent = capacity * 0.01;

                // 当累计充放电量达到电容量的1%时执行报废检查
                // 使用循环处理充放电量大于1%容量的情况
                while (chargeCounter >= capacityPercent) {
                    // 处理机器损坏 - 电容充放电时尝试触发报废检查
                    Slimefun.getMachineDamageService().processMachineWork(loc, item);

                    // 计数器减少电容量的1%
                    chargeCounter -= capacityPercent;
                }

                // 保存计数器值
                data.setData("machine_damage_charge_counter", String.valueOf(chargeCounter));
            }

            if (chargeDiff > 0) {
                // 按(源, 长度)分组，均摊负载到多条路径
                Map<String, List<EnergyPath>> pathGroups = new LinkedHashMap<>();
                for (EnergyPath p : allCapPaths) {
                    String key = p.source.getBlockX() + "," + p.source.getBlockY() + "," + p.source.getBlockZ() + ","
                            + p.length;
                    pathGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
                }
                long totalPaths = allCapPaths.size();
                long loadPerPath = chargeDiff / totalPaths;
                long loadRemainder = chargeDiff % totalPaths;
                int idx = 0;
                for (List<EnergyPath> group : pathGroups.values()) {
                    for (EnergyPath path : group) {
                        long pathLoad = loadPerPath + (idx < loadRemainder ? 1 : 0);
                        if (pathLoad > 0) {
                            recordConnectorLoad(path, pathLoad);
                        }
                        idx++;
                    }
                }
            }
        }
        debugLog("storeRemainingEnergy: 实际存储 " + totalStored + "J");
        return totalStored;
    }

    @Nonnull
    private Set<Location> collectUniqueConnectorsForCap(@Nonnull Location capLoc) {
        Set<Location> uniqueConns = new HashSet<>();
        for (Map<Location, Set<EnergyPath>> pathsByGen : generatorToCapacitorPaths.values()) {
            Set<EnergyPath> capSet = pathsByGen.get(capLoc);
            if (capSet != null) {
                for (EnergyPath p : capSet) {
                    for (Location connLoc : p.connectors) {
                        if (!connLoc.equals(p.source)) {
                            uniqueConns.add(connLoc);
                        }
                    }
                }
            }
        }
        return uniqueConns;
    }

    private long computeCapStorageLimitAvailable(@Nonnull Location capLoc, @Nonnull Map<Location, Long> storageLoads) {
        Set<Location> uniqueConns = collectUniqueConnectorsForCap(capLoc);
        if (uniqueConns.isEmpty()) {
            return Long.MAX_VALUE;
        }

        long available = Long.MAX_VALUE;
        for (Location connLoc : uniqueConns) {
            Long limit = connectorLimits.get(connLoc);
            if (limit == null) {
                continue;
            }
            if (limit == 0) {
                return 0;
            }
            long globalUsed = connectorLoad.getOrDefault(connLoc, 0L);
            long localUsed = storageLoads.getOrDefault(connLoc, 0L);
            long remaining = limit - globalUsed - localUsed;
            if (remaining < available) {
                available = remaining;
            }
            if (remaining <= 0) {
                return 0;
            }
        }
        return available;
    }

    private void recordCapStorageLoads(
            @Nonnull Location capLoc, long amount, @Nonnull Map<Location, Long> storageLoads) {
        Set<Location> uniqueConns = collectUniqueConnectorsForCap(capLoc);
        if (uniqueConns.isEmpty() || amount <= 0) {
            return;
        }

        long perConn = amount / uniqueConns.size();
        long remainder = amount % uniqueConns.size();
        int i = 0;
        for (Location connLoc : uniqueConns) {
            long load = perConn + (i < remainder ? 1 : 0);
            storageLoads.merge(connLoc, load, Long::sum);
            i++;
        }
    }

    private long tickAllGenerators(@Nonnull LongConsumer timings) {
        Set<Location> explodedBlocks = new HashSet<>();
        long supply = 0;
        nonChargeableSupply.clear();
        netNewEnergy = 0;

        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            long timestamp = Slimefun.getProfiler().newEntry();
            Location loc = entry.getKey();
            EnergyNetProvider provider = entry.getValue();
            SlimefunItem item = (SlimefunItem) provider;

            try {
                var data = StorageCacheUtils.getDataContainer(loc);
                if (data == null || data.isPendingRemove()) {
                    continue;
                }

                if (Slimefun.getMachineDamageService().isMachineDamaged(data)) {
                    continue;
                }

                if (!item.getId().equals(data.getSfId())) {
                    var newItem = SlimefunItem.getById(data.getSfId());
                    if (!(newItem instanceof EnergyNetProvider newProvider)) {
                        continue;
                    }
                    generators.put(loc, newProvider);
                    provider = newProvider;
                }

                if (!data.isDataLoaded()) {
                    StorageCacheUtils.requestLoad(data);
                    continue;
                }

                long generatedEnergy = provider.getGeneratedOutputLong(loc, data);

                if (provider.isChargeable()) {
                    long chargeBefore = provider.getChargeLong(loc);
                    if (generatedEnergy > 0) {
                        provider.addCharge(loc, generatedEnergy);
                    }
                    long chargeAfter = provider.getChargeLong(loc);
                    long newCharge = chargeAfter - chargeBefore;
                    netNewEnergy += newCharge;
                    supply += chargeAfter;
                } else {
                    nonChargeableSupply.put(loc, generatedEnergy);
                    supply += generatedEnergy;
                }

                if (provider.willExplode(loc, data)) {
                    explodedBlocks.add(loc);
                    Slimefun.getDatabaseManager().getBlockDataController().removeBlock(loc);

                    Slimefun.runSync(() -> {
                        loc.getBlock().setType(Material.LAVA);
                        loc.getWorld().createExplosion(loc, 0F, false);
                    });
                } else {
                    // 处理机器损坏 - 只有当发电机实际产生能量时才触发报废检查
                    if (generatedEnergy > 0) {
                        Slimefun.getMachineDamageService().processMachineWork(loc, item);
                    }
                }
            } catch (Exception | LinkageError throwable) {
                explodedBlocks.add(loc);
                new ErrorReport<>(throwable, loc, item);
            }

            long time = Slimefun.getProfiler().closeEntry(loc, item, timestamp);
            timings.accept(time);
        }

        // Remove all generators which have exploded
        if (!explodedBlocks.isEmpty()) {
            generators.keySet().removeAll(explodedBlocks);
        }

        return supply;
    }

    private long tickAllCapacitors() {
        long supply = 0;

        for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
            Location loc = entry.getKey();
            var data = StorageCacheUtils.getDataContainer(loc);
            // 检查电容是否损坏，如果损坏则跳过处理
            if (data == null
                    || data.isPendingRemove()
                    || !data.isDataLoaded()
                    || Slimefun.getMachineDamageService().isMachineDamaged(data)) {
                continue;
            }

            EnergyNetComponent component = entry.getValue();
            // 修复物品机制与机器损坏机制一致
            if (!((SlimefunItem) component).getId().equals(data.getSfId())) {
                var newItem = SlimefunItem.getById(data.getSfId());
                if (!(newItem instanceof EnergyNetComponent newComponent)
                        || newComponent.getEnergyComponentType() != EnergyNetComponentType.CAPACITOR) {
                    continue;
                }
                capacitors.put(loc, newComponent);
                component = newComponent;
            }

            supply = NumberUtils.flowSafeAddition(supply, component.getChargeLong(loc));
        }

        return supply;
    }

    private void updateHologram(@Nonnull SlimefunBlockData data, double supply, double demand) {
        String netLine;
        if (demand > supply) {
            String netLoss = NumberUtils.getCompactDouble(demand - supply);
            netLine = "&e可调度电量 &7| &4&l- &c" + netLoss + " &7J &e\u26A1";
        } else {
            String netGain = NumberUtils.getCompactDouble(supply - demand);
            netLine = "&e可调度电量 &7| &2&l+ &a" + netGain + " &7J &e\u26A1";
        }

        String prodStr = NumberUtils.getCompactDouble(totalProducedThisTick);
        String consStr = NumberUtils.getCompactDouble(totalConsumedThisTick);
        long absNet = Math.abs(totalNetStoredThisTick);
        String netPrefix = totalNetStoredThisTick > 0 ? "+" : (totalNetStoredThisTick < 0 ? "-" : "±");
        String netColor = totalNetStoredThisTick > 0 ? "&a" : (totalNetStoredThisTick < 0 ? "&c" : "&7");
        String netStr = NumberUtils.getCompactDouble(absNet);
        String prodConsLine = "&2产出 +" + prodStr + " J &7| &e净值 " + netColor + netPrefix + netStr + " &7J &7| &c消耗 -"
                + consStr + " J";

        long totalCharge = calculateTotalCharge();
        long totalCapacity = calculateTotalCapacity();
        String chargeStr = NumberUtils.getCompactDouble(totalCharge);
        String capacityStr = NumberUtils.getCompactDouble(totalCapacity);
        String storageLine = "&e储能 " + chargeStr + " &7/ &e" + capacityStr + " &7J";

        updateMultiLineHologram(
                data.getLocation().getBlock(), data::isPendingRemove, netLine, prodConsLine, storageLine);
    }

    private void updateHologramOnMain(@Nonnull Location loc, @Nonnull String message) {
        Runnable update = () -> updateHologram(loc.getBlock(), message, () -> false);
        if (Bukkit.isPrimaryThread()) {
            update.run();
        } else {
            Slimefun.runSync(update);
        }
    }

    @Nullable private static EnergyNetComponent getComponent(@Nonnull Location l) {
        var data = StorageCacheUtils.getDataContainer(l);
        if (data == null || data.isPendingRemove()) {
            return null;
        }
        SlimefunItem item = StorageCacheUtils.getSlimefunItem(l);

        if (item instanceof EnergyNetComponent component) {
            return component;
        }

        return null;
    }

    private boolean isConnectorLocation(@Nonnull Location loc) {
        EnergyNetComponent component = getComponent(loc);
        if (component != null) {
            return component.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR;
        }

        SlimefunItem item = StorageCacheUtils.getSlimefunItem(loc);
        if (item == null) {
            item = querySlimefunItemFromDb(loc);
        }

        return item instanceof EnergyNetComponent energyComponent
                && energyComponent.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR;
    }

    @Nullable private SlimefunItem querySlimefunItemFromDb(@Nonnull Location l) {
        if (!bfsDbQueried.add(l)) {
            return null;
        }
        return querySlimefunItemFromDbDirect(l);
    }

    @Nullable private SlimefunItem querySlimefunItemFromDbDirect(@Nonnull Location l) {
        SlimefunBlockData blockData =
                Slimefun.getDatabaseManager().getBlockDataController().getBlockData(l);
        if (blockData == null) {
            return null;
        }
        int count = bfsDbQueryCount.incrementAndGet();
        if (count % BFS_DB_QUERY_THROTTLE == 0) {
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return SlimefunItem.getById(blockData.getSfId());
    }

    /**
     * This attempts to get an {@link EnergyNet} from a given {@link Location}.
     * If no suitable {@link EnergyNet} could be found, {@code null} will be returned.
     *
     * @param l
     *            The target {@link Location}
     *
     * @return The {@link EnergyNet} at that {@link Location}, or {@code null}
     */
    @Nullable public static EnergyNet getNetworkFromLocation(@Nonnull Location l) {
        return Slimefun.getNetworkManager()
                .getNetworkFromLocation(l, EnergyNet.class)
                .orElse(null);
    }

    /**
     * 异步初始化电网 - 包括成员收集和路线预计算
     * 在异步线程中执行，不阻塞主线程
     */
    private void initializeNetworkAsync() {
        cancelSelfTick();
        debugLog("initializeNetworkAsync: 开始异步初始化");
        bfsDbQueryCount.set(0);
        bfsDbQueried.clear();
        synchronized (regulator.toString().intern()) {
            if (destroyed) {
                debugLog("initializeNetworkAsync: 电网已销毁，退出");
                return;
            }

            initializing = true;
            pendingInit = false;
            abortRequested = false;
            debugLog("initializeNetworkAsync: initializing=true");
            try {
                Slimefun.runSync(() -> {
                    if (!destroyed) {
                        updateHologram(regulator.getBlock(), "&e初始化电网中 0%", () -> false);
                    }
                });

                clearNetworkData();
                debugLog("initializeNetworkAsync: 数据已清空");

                if (abortRequested || destroyed) {
                    debugLog("initializeNetworkAsync: 在collectNetworkMembers前被打断");
                    return;
                }

                boolean collectResult = collectNetworkMembers();
                debugLog("initializeNetworkAsync: collectNetworkMembers返回 " + collectResult
                        + ", generators=" + generators.size()
                        + " connectors=" + connectors.size()
                        + " capacitors=" + capacitors.size()
                        + " consumers=" + consumers.size()
                        + " connectorNodes=" + connectorNodes.size()
                        + " terminusNodes=" + terminusNodes.size());

                if (!collectResult) {
                    if (abortRequested || destroyed) {
                        debugLog("initializeNetworkAsync: collectNetworkMembers被打断，不进入冲突模式");
                        conflictMode = false;
                    } else {
                        debugLog("initializeNetworkAsync: collectNetworkMembers失败，进入冲突模式");
                        conflictMode = true;
                    }
                    return;
                }

                // 基于实际数据动态计算各阶段工作量权重
                initWorkDone = generators.size() + connectors.size() + capacitors.size() + consumers.size();
                int sourceCount = generators.size() + capacitors.size();
                int pathWorkEstimate = sourceCount * Math.max(1, connectorNodes.size());
                initTotalWork = Math.max(1, initWorkDone + pathWorkEstimate);
                pathSourcesDone = 0;

                if (abortRequested || destroyed) {
                    debugLog("initializeNetworkAsync: 在precomputePaths前被打断");
                    return;
                }

                precomputePaths();
                debugLog("initializeNetworkAsync: precomputePaths完成"
                        + " generatorPaths=" + countTotalPaths(generatorPaths)
                        + " capacitorPaths=" + countTotalPaths(capacitorPaths));

                if (abortRequested || destroyed) {
                    debugLog("initializeNetworkAsync: 在初始化完成前被打断");
                    return;
                }

                initialized = true;
                initRetryCount = 0;
                scheduleSelfTick();
                debugLog("初始化完成 ✓ 调节器=" + formatLocation(regulator)
                        + " 发电机=" + generators.size() + " 连接器=" + connectors.size()
                        + " 电容=" + capacitors.size() + " 用电器=" + consumers.size()
                        + " 路径=" + (countTotalPaths(generatorPaths) + countTotalPaths(capacitorPaths)));
                if (!destroyed) {
                    Slimefun.runSync(() -> {
                        removeMultiLineHologram(regulator.getBlock());
                        updateHologram(regulator.getBlock(), "&e初始化完成，等待首个tick数据...", () -> false);
                    });
                }
            } finally {
                initializing = false;
                abortRequested = false;
                if (!initialized && !destroyed && !pendingInit && !conflictMode && initRetryCount < 3) {
                    initRetryCount++;
                    debugLog("initializeNetworkAsync: 初始化被打断，自动重试 #" + initRetryCount);
                    pendingInit = true;
                    GRID_EXECUTOR.submit(this::initializeNetworkAsync);
                } else if (!initialized && initRetryCount >= 3) {
                    Slimefun.logger().warning("EnergyNet: 初始化重试次数超限 @" + formatLocation(regulator));
                    initRetryCount = 0;
                }
            }
        }
    }

    /**
     * 电网初始化 - 同步版本（保留用于兼容，实际使用异步版本）
     */
    public void initializeNetwork() {
        initializeNetworkAsync();
    }

    /**
     * 清空电网数据
     */
    private void clearNetworkData() {
        generators.clear();
        capacitors.clear();
        consumers.clear();
        connectors.clear();
        connectorLoad.clear();
        conflictHologramTargets.clear();
        generatorPaths.clear();
        capacitorPaths.clear();
        generatorToCapacitorPaths.clear();
        nonChargeableSupply.clear();

        // 清空基类的节点集合
        regulatorNodes.clear();
        connectorNodes.clear();
        terminusNodes.clear();
        connectedLocations.clear();
        connectorLimits.clear();
        longRangeAxisTargets.clear();
        // debugLog("clearNetworkData: connectorLimits已清空");

        // 重新添加调节器
        regulatorNodes.add(regulator);
        connectedLocations.add(regulator);
        connectorLoad.put(regulator, 0L);
        firstTickDone = false;
    }

    /**
     * 检查连接器是否能覆盖目标机器（单向验证）
     * 连接器到任何设备（包括连接器）都只需连接器范围覆盖目标
     * @param connectorLoc 连接器位置
     * @param machineLoc 机器位置
     * @param machineType 机器类型
     * @return true如果连接有效
     */
    private boolean checkRangeValidation(
            Location connectorLoc, Location machineLoc, EnergyNetComponentType machineType) {
        EnergyNetComponent connectorComponent = getComponent(connectorLoc);

        if (connectorComponent == null) {
            return false;
        }

        int connectorRange = connectorComponent.getRange();
        return isWithinRangeAxial(connectorLoc, machineLoc, connectorRange);
    }

    /**
     * 检查两个位置是否相邻（距离<=1）
     */
    private static boolean isAdjacent(Location loc1, Location loc2) {
        return Math.abs(loc1.getBlockX() - loc2.getBlockX())
                        + Math.abs(loc1.getBlockY() - loc2.getBlockY())
                        + Math.abs(loc1.getBlockZ() - loc2.getBlockZ())
                == 1;
    }

    /**
     * 检查目标位置是否在源位置的范围内（曼哈顿距离）
     */
    private static boolean isWithinRange(Location source, Location target, int range) {
        return Math.abs(source.getBlockX() - target.getBlockX()) <= range
                && Math.abs(source.getBlockY() - target.getBlockY()) <= range
                && Math.abs(source.getBlockZ() - target.getBlockZ()) <= range;
    }

    /**
     * 检查目标是否在源位置的同一轴线上且距离不超过range
     * 与processConnector的6轴向搜索一致：两个坐标轴差为0，第三个≤range
     */
    private static boolean isWithinRangeAxial(Location source, Location target, int range) {
        int dx = Math.abs(source.getBlockX() - target.getBlockX());
        int dy = Math.abs(source.getBlockY() - target.getBlockY());
        int dz = Math.abs(source.getBlockZ() - target.getBlockZ());
        // 两个轴向差为0(A)，第三个轴向差≤range且>0(B)
        // 注意：完全相同的坐标（全0）返回false
        return (dx == 0 && dy == 0 && dz <= range && dz > 0)
                || (dx == 0 && dz == 0 && dy <= range && dy > 0)
                || (dy == 0 && dz == 0 && dx <= range && dx > 0);
    }

    /**
     * 计算两个位置的最大轴向距离（用于调试显示）
     */
    private static int getDistance(Location loc1, Location loc2) {
        int dx = Math.abs(loc1.getBlockX() - loc2.getBlockX());
        int dy = Math.abs(loc1.getBlockY() - loc2.getBlockY());
        int dz = Math.abs(loc1.getBlockZ() - loc2.getBlockZ());
        return Math.max(dx, Math.max(dy, dz));
    }

    /**
     * 计算两个位置的轴向距离（两个坐标相同轴的差值）
     */
    private static int getAxialDistance(Location loc1, Location loc2) {
        int dx = Math.abs(loc1.getBlockX() - loc2.getBlockX());
        int dy = Math.abs(loc1.getBlockY() - loc2.getBlockY());
        int dz = Math.abs(loc1.getBlockZ() - loc2.getBlockZ());
        return dx + dy + dz;
    }

    /**
     * 检查指定位置是否已被其他电网占用
     * @param loc 要检查的位置
     * @return true如果该位置属于其他电网
     */
    private boolean isLocationInOtherGrid(Location loc) {
        Optional<EnergyNet> existingNet = Slimefun.getNetworkManager().getNetworkFromLocation(loc, EnergyNet.class);
        return existingNet.isPresent() && existingNet.get() != this;
    }

    /**
     * 获取电网内连接器的最大连接范围
     */
    private int getMaxConnectorRange() {
        int max = RANGE;
        for (EnergyNetComponent comp : connectors.values()) {
            if (comp instanceof LongRangeConnector) {
                continue;
            }
            int range = comp.getRange();
            if (range > max) {
                max = range;
            }
        }
        return max;
    }

    /**
     * 验证从type1(loc1)到type2(loc2)的电力传输路径是否有效（单向验证 A→B）
     * 只有电容→电容使用相邻规则，其余都基于发送方的连接范围
     * @param loc1 发送方位置
     * @param loc2 接收方位置
     * @param type1 发送方类型
     * @param type2 接收方类型
     * @return true如果连接有效
     */
    private boolean validateConnection(
            Location loc1, Location loc2, EnergyNetComponentType type1, EnergyNetComponentType type2) {
        if (type1 == EnergyNetComponentType.CAPACITOR && type2 == EnergyNetComponentType.CAPACITOR) {
            return isAdjacent(loc1, loc2);
        }

        if (type1 == EnergyNetComponentType.CAPACITOR && type2 == EnergyNetComponentType.CONNECTOR) {
            return isAdjacent(loc1, loc2);
        }

        if (type1 == EnergyNetComponentType.CONNECTOR) {
            EnergyNetComponent comp1 = getComponent(loc1);
            if (comp1 == null) {
                return false;
            }
            return isWithinRangeAxial(loc1, loc2, comp1.getRange());
        }

        if (type1 == EnergyNetComponentType.GENERATOR && type2 == EnergyNetComponentType.CONNECTOR) {
            EnergyNetComponent comp2 = getComponent(loc2);
            if (comp2 == null) {
                return false;
            }
            return isWithinRangeAxial(loc2, loc1, comp2.getRange());
        }

        return false;
    }

    /**
     * 预计算能量传输路径
     */
    private void precomputePaths() {
        generatorPaths.clear();
        capacitorPaths.clear();

        int totalSources = generators.size() + capacitors.size();
        if (totalSources == 0) {
            if (DEBUG_PATHS) {
                debugPathLog("precomputePaths: 无发电机和电容，跳过路径计算");
            }
            return;
        }

        if (DEBUG_PATHS) {
            debugPathLog("precomputePaths: 开始计算路径，sources=" + totalSources + " consumers=" + consumers.size()
                    + " generators=" + generators.size() + " capacitors=" + capacitors.size());
        }
        pathSourcesDone = 0;

        // 计算所有发电机到消费者的路径
        if (!consumers.isEmpty()) {
            for (Location generatorLoc : generators.keySet()) {
                if (abortRequested || destroyed) return;
                Set<EnergyPath> paths = findShortestPathsFromSource(generatorLoc, totalSources);
                if (!paths.isEmpty()) {
                    generatorPaths.put(generatorLoc, paths);
                }
                pathSourcesDone++;
            }

            // 计算所有电容到消费者的路径
            for (Location capacitorLoc : capacitors.keySet()) {
                if (abortRequested || destroyed) return;
                Set<EnergyPath> paths = findShortestPathsFromSource(capacitorLoc, totalSources);
                if (!paths.isEmpty()) {
                    capacitorPaths.put(capacitorLoc, paths);
                } else {
                    if (DEBUG_PATHS) {
                        debugPathLog("precomputePaths: 电容 " + formatLocation(capacitorLoc) + " 未找到到任何用电器的路径");
                    }
                }
                pathSourcesDone++;
            }

            if (DEBUG_PATHS) {
                debugPathLog("precomputePaths: 电容路径计算完成, capacitorPaths=" + countTotalPaths(capacitorPaths) + " 条");
            }
        } else if (DEBUG_PATHS) {
            debugPathLog("precomputePaths: 无用电器，跳过generatorPaths/capacitorPaths计算");
        }

        // 预计算发电机到电容的路径（用于显示充电跳数和负载记录）
        if (!capacitors.isEmpty() && !generators.isEmpty()) {
            if (DEBUG_PATHS) {
                debugPathLog("precomputePaths: 开始计算发电机到电容路径");
            }
            for (Location genLoc : generators.keySet()) {
                if (abortRequested || destroyed) return;
                Map<Location, Set<EnergyPath>> capPaths = findShortestPathsToCapacitors(genLoc);
                if (!capPaths.isEmpty()) {
                    generatorToCapacitorPaths.put(genLoc, capPaths);
                }
            }
        }
    }

    /**
     * 从源位置（发电机或电容）查找所有到消费者的最短路径
     * 使用多前驱BFS：记录每个节点的最短跳数和所有前驱，然后回溯生成全部最短路径
     */
    private Set<EnergyPath> findShortestPathsFromSource(Location source, int totalSources) {
        Set<EnergyPath> shortestPaths = new LinkedHashSet<>();
        Map<Location, Integer> dist = new HashMap<>();
        Map<Location, List<Location>> predecessors = new HashMap<>();
        Queue<Location> queue = new ArrayDeque<>();

        dist.put(source, 0);
        queue.add(source);

        Set<Location> targets = new LinkedHashSet<>();
        int nodeCount = 0;
        int stepsSinceProgress = 0;

        while (!queue.isEmpty()) {
            if (++nodeCount % 10 == 0 && (abortRequested || destroyed)) {
                debugLog("findShortestPathsFromSource: 检测到打断，提前退出BFS");
                return shortestPaths;
            }
            if (nodeCount > MAX_BFS_NODES) {
                debugLog("findShortestPathsFromSource: BFS节点超限(" + nodeCount + ")，强制退出");
                return shortestPaths;
            }

            Location current = queue.poll();
            int currentDist = dist.get(current);

            if (consumers.containsKey(current) && !current.equals(source)) {
                targets.add(current);
                if (DEBUG_PATHS) {
                    debugPathLog("BFS步骤: 找到消费者 " + formatLocation(current) + " dist=" + currentDist);
                }
                continue;
            }

            EnergyNetComponent component = getComponent(current);
            Set<Location> neighbors;
            if (component != null) {
                neighbors = getNeighbors(current, component.getEnergyComponentType());
            } else if (current.equals(regulator)) {
                neighbors = getRegulatorNeighbors();
            } else {
                continue;
            }

            if (DEBUG_PATHS) {
                debugPathLog("BFS步骤: 当前=" + formatLocation(current)
                        + " 类型=" + (component != null ? component.getEnergyComponentType() : "REGULATOR")
                        + " dist=" + currentDist
                        + " 邻居数=" + neighbors.size());
            }

            for (Location neighbor : neighbors) {
                int newDist = currentDist + 1;
                if (!dist.containsKey(neighbor)) {
                    dist.put(neighbor, newDist);
                    List<Location> predList = new ArrayList<>();
                    predList.add(current);
                    predecessors.put(neighbor, predList);
                    queue.add(neighbor);
                } else if (newDist == dist.get(neighbor)) {
                    predecessors.get(neighbor).add(current);
                }
            }

            if (++stepsSinceProgress >= 50) {
                stepsSinceProgress = 0;
                if (totalSources > 0) {
                    double collectFraction = (double) initWorkDone / initTotalWork;
                    double pathFraction = (double) pathSourcesDone / totalSources
                            + (double) nodeCount / (Math.max(1, dist.size()) * totalSources);
                    int pct = Math.max(0, Math.min(99, (int)
                            ((collectFraction + (1.0 - collectFraction) * pathFraction) * 100)));
                    Slimefun.runSync(() -> {
                        if (!destroyed) {
                            updateHologram(regulator.getBlock(), "&e初始化电网中 " + pct + "%", () -> false);
                        }
                    });
                }
            }
        }

        for (Location target : targets) {
            List<List<Location>> allPaths = new ArrayList<>();
            backtrackPaths(source, target, predecessors, allPaths, new ArrayList<>());
            for (List<Location> path : allPaths) {
                List<Location> connList = extractConnectorsFromPath(path);
                shortestPaths.add(new EnergyPath(source, target, connList));
            }
        }

        if (DEBUG_PATHS) {
            StringBuilder sb = new StringBuilder();
            sb.append("BFS: 源=").append(formatLocation(source));
            EnergyNetComponent srcComp = getComponent(source);
            sb.append(" 类型=").append(srcComp != null ? srcComp.getEnergyComponentType() : "null");
            sb.append(" 找到路径数=").append(shortestPaths.size());
            if (!shortestPaths.isEmpty()) {
                sb.append(" 路径:");
                for (EnergyPath p : shortestPaths) {
                    sb.append(" ")
                            .append(formatLocation(p.consumer))
                            .append("(L=")
                            .append(p.length)
                            .append(")");
                }
            }
            debugPathLog(sb.toString());
        }
        return shortestPaths;
    }

    private Map<Location, Set<EnergyPath>> findShortestPathsToCapacitors(Location source) {
        Map<Location, Set<EnergyPath>> result = new HashMap<>();
        Map<Location, Integer> dist = new HashMap<>();
        Map<Location, List<Location>> predecessors = new HashMap<>();
        Queue<Location> queue = new ArrayDeque<>();

        dist.put(source, 0);
        queue.add(source);

        int nodeCount = 0;

        while (!queue.isEmpty()) {
            if (++nodeCount % 10 == 0 && (abortRequested || destroyed)) {
                return result;
            }
            if (nodeCount > MAX_BFS_NODES) {
                return result;
            }

            Location current = queue.poll();
            int currentDist = dist.get(current);

            if (capacitors.containsKey(current) && !current.equals(source)) {
                List<List<Location>> allPaths = new ArrayList<>();
                backtrackPaths(source, current, predecessors, allPaths, new ArrayList<>());
                Set<EnergyPath> paths = new LinkedHashSet<>();
                for (List<Location> path : allPaths) {
                    List<Location> connList = extractConnectorsFromPath(path);
                    paths.add(new EnergyPath(source, current, connList));
                }
                result.put(current, paths);
            }

            EnergyNetComponent component = getComponent(current);
            Set<Location> neighbors;
            if (component != null) {
                neighbors = getNeighbors(current, component.getEnergyComponentType());
            } else if (current.equals(regulator)) {
                neighbors = getRegulatorNeighbors();
            } else {
                continue;
            }

            for (Location neighbor : neighbors) {
                int newDist = currentDist + 1;
                if (!dist.containsKey(neighbor)) {
                    dist.put(neighbor, newDist);
                    List<Location> predList = new ArrayList<>();
                    predList.add(current);
                    predecessors.put(neighbor, predList);
                    queue.add(neighbor);
                } else if (newDist == dist.get(neighbor)) {
                    predecessors.get(neighbor).add(current);
                }
            }
        }
        return result;
    }

    /**
     * 获取调节器的邻居节点集合（调节器不实现EnergyNetComponent，需特殊处理）
     * 调节器作为特殊连接器，range=RANGE
     */
    private Set<Location> getRegulatorNeighbors() {
        Set<Location> neighbors = new HashSet<>();
        int regRange = getRange();
        for (Location connLoc : connectors.keySet()) {
            if (isWithinRangeAxial(regulator, connLoc, regRange)) {
                neighbors.add(connLoc);
            }
        }
        for (Location capLoc : capacitors.keySet()) {
            if (isWithinRangeAxial(regulator, capLoc, regRange)) {
                neighbors.add(capLoc);
            }
        }
        for (Location conLoc : consumers.keySet()) {
            if (isWithinRangeAxial(regulator, conLoc, regRange)) {
                neighbors.add(conLoc);
            }
        }
        return neighbors;
    }

    /**
     * 获取指定位置的邻居位置（统一单向边界规则 A→B）
     *
     * 统一规则:
     * - G→Connector: dist(G,C) ≤ C.range 且不允许直达LongRangeConnector
     * - G→Capacitor: FORBIDDEN
     * - G→Consumer: FORBIDDEN
     * - Connector→Connector: dist(A,B) ≤ A.range (发送方的范围)
     * - Connector→Capacitor: dist ≤ 1 (仅相邻)
     * - Connector→Consumer: dist ≤ A.range
     * - Capacitor→Connector: dist ≤ 1 (仅相邻，且不包含LongRangeConnector)
     * - Capacitor→Capacitor: dist ≤ 1 (仅相邻)
     * - Capacitor→Consumer: FORBIDDEN
     * - Consumer→anything: FORBIDDEN (终端)
     * - LongRangeConnector→Capacitor/Consumer: FORBIDDEN
     * - 调节器: 特殊连接器，range=RANGE
     */
    private Set<Location> getNeighbors(Location location, EnergyNetComponentType type) {
        Set<Location> neighbors = new HashSet<>();

        switch (type) {
            case GENERATOR:
                if (!location.equals(regulator)
                        && isWithinRangeAxial(location, regulator, getMaxConnectorRange())
                        && isWithinRangeAxial(regulator, location, RANGE)) {
                    neighbors.add(regulator);
                }
                for (Location connectorLoc : connectors.keySet()) {
                    EnergyNetComponent connectorComp = getComponent(connectorLoc);
                    if (connectorComp instanceof LongRangeConnector) {
                        continue;
                    }
                    if (validateConnection(
                            location,
                            connectorLoc,
                            EnergyNetComponentType.GENERATOR,
                            EnergyNetComponentType.CONNECTOR)) {
                        neighbors.add(connectorLoc);
                    }
                }
                break;

            case CONSUMER:
                break;

            case CAPACITOR:
                for (Location capacitorLoc : capacitors.keySet()) {
                    if (!capacitorLoc.equals(location) && isAdjacent(location, capacitorLoc)) {
                        neighbors.add(capacitorLoc);
                    }
                }
                if (!location.equals(regulator) && isAdjacent(location, regulator)) {
                    neighbors.add(regulator);
                }
                for (Location connectorLoc : connectors.keySet()) {
                    EnergyNetComponent connectorComp = getComponent(connectorLoc);
                    if (connectorComp instanceof LongRangeConnector) {
                        continue;
                    }
                    if (isAdjacent(location, connectorLoc)) {
                        neighbors.add(connectorLoc);
                    }
                }
                break;

            case CONNECTOR:
                EnergyNetComponent connComponent = getComponent(location);
                if (connComponent == null) {
                    break;
                }
                boolean isLongRangeConnector = connComponent instanceof LongRangeConnector;
                int connRange = connComponent.getRange();

                if (isLongRangeConnector) {
                    int[][] axes = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                    for (int[] axis : axes) {
                        for (int i = 1; i <= connRange; i++) {
                            Location targetLoc = location.clone().add(axis[0] * i, axis[1] * i, axis[2] * i);
                            EnergyNetComponent targetComp = getComponent(targetLoc);
                            if (targetComp == null) {
                                SlimefunItem dbItem = querySlimefunItemFromDb(targetLoc);
                                if (dbItem instanceof EnergyNetComponent ec) {
                                    targetComp = ec;
                                }
                            }
                            if (targetComp == null
                                    || targetComp.getEnergyComponentType() != EnergyNetComponentType.CONNECTOR) {
                                continue;
                            }
                            if (!ConnectorAgingManager.isConnectorDamaged(targetLoc)
                                    && !isConnectorBlocked(targetLoc)
                                    && connectors.containsKey(targetLoc)
                                    && isWithinRangeAxial(location, targetLoc, connRange)) {
                                neighbors.add(targetLoc);
                            }
                            break;
                        }
                    }
                } else {
                    for (Location otherConnector : connectors.keySet()) {
                        if (!otherConnector.equals(location)) {
                            if (validateConnection(
                                    location,
                                    otherConnector,
                                    EnergyNetComponentType.CONNECTOR,
                                    EnergyNetComponentType.CONNECTOR)) {
                                neighbors.add(otherConnector);
                            }
                        }
                    }
                    if (!location.equals(regulator) && isWithinRangeAxial(location, regulator, connRange)) {
                        neighbors.add(regulator);
                    }
                    for (Location consumer : consumers.keySet()) {
                        if (isWithinRangeAxial(location, consumer, connRange)) {
                            neighbors.add(consumer);
                        }
                    }
                    for (Location capacitor : capacitors.keySet()) {
                        if (isAdjacent(location, capacitor)) {
                            neighbors.add(capacitor);
                        }
                    }
                }
                break;

            default:
                break;
        }

        return neighbors;
    }

    /**
     * 从目标节点通过前驱映射回溯生成所有最短路径
     * @param source 源节点
     * @param target 目标节点
     * @param predecessors 多前驱映射
     * @param result 收集所有完整路径
     * @param currentPath 当前正在构建的路径（反向）
     */
    private void backtrackPaths(
            Location source,
            Location target,
            Map<Location, List<Location>> predecessors,
            List<List<Location>> result,
            List<Location> currentPath) {
        Deque<Map.Entry<Location, List<Location>>> stack = new ArrayDeque<>();
        stack.push(new java.util.AbstractMap.SimpleEntry<>(target, new ArrayList<>(currentPath)));

        while (!stack.isEmpty()) {
            Map.Entry<Location, List<Location>> entry = stack.pop();
            Location current = entry.getKey();
            List<Location> path = entry.getValue();
            path.add(current);

            if (current.equals(source)) {
                List<Location> fullPath = new ArrayList<>(path);
                Collections.reverse(fullPath);
                result.add(fullPath);
            } else {
                List<Location> preds = predecessors.get(current);
                if (preds != null) {
                    for (Location pred : preds) {
                        stack.push(new java.util.AbstractMap.SimpleEntry<>(pred, new ArrayList<>(path)));
                    }
                }
            }
        }
    }

    /**
     * 从完整路径（Source→...→Consumer）中提取连接器和电容列表
     * 排除源节点和消费者节点，保留中间的连接器、电容和调节器
     */
    private List<Location> extractConnectorsFromPath(List<Location> path) {
        List<Location> result = new ArrayList<>();
        for (int i = 1; i < path.size() - 1; i++) {
            Location loc = path.get(i);
            EnergyNetComponent comp = getComponent(loc);
            if (comp != null
                    && (comp.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR
                            || comp.getEnergyComponentType() == EnergyNetComponentType.CAPACITOR)) {
                result.add(loc);
            } else if (loc.equals(regulator)) {
                result.add(loc);
            }
        }
        return result;
    }

    /**
     * 收集电网成员 - 根据新规格说明实现
     * @return true如果成功，false如果失败（如调节器冲突）
     */
    private boolean collectNetworkMembers() {
        Queue<Location> queue = new ArrayDeque<>();
        Set<Location> visited = new HashSet<>();

        // 从调节器开始
        queue.add(regulator);
        visited.add(regulator);
        debugLog("collectNetworkMembers: 从调节器 " + formatLocation(regulator) + " 开始搜索");

        // 从调节器沿6个轴向扩展搜索周围RANGE格内的所有可接入机器
        // 必须在BFS循环外执行，因为EnergyRegulator不实现EnergyNetComponent
        int regRange = getRange();
        int[][] axes = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] axis : axes) {
            for (int i = 1; i <= regRange; i++) {
                Location targetLoc = regulator.clone().add(axis[0] * i, axis[1] * i, axis[2] * i);
                if (abortRequested || destroyed) {
                    debugLog("collectNetworkMembers: 调节器扩展检测到打断");
                    return false;
                }
                if (visited.contains(targetLoc)) continue;
                EnergyNetComponent targetComponent = getComponent(targetLoc);
                debugLog("collectNetworkMembers: regScan @" + formatLocation(targetLoc)
                        + " getComponent="
                        + (targetComponent != null
                                ? targetComponent.getEnergyComponentType().toString()
                                : "null"));
                if (targetComponent == null) {
                    SlimefunItem dbItem = querySlimefunItemFromDb(targetLoc);
                    debugLog("collectNetworkMembers: regScan @" + formatLocation(targetLoc) + " DB查询="
                            + (dbItem != null ? dbItem.getId() : "null"));
                    if (dbItem instanceof EnergyNetComponent dbComp) {
                        targetComponent = dbComp;
                    } else if (dbItem != null && dbItem.getId().equals("ENERGY_REGULATOR")) {
                        // 调节器不实现 EnergyNetComponent，需显式冲突检测
                        if (!targetLoc.equals(regulator)) {
                            debugLog("collectNetworkMembers: 调节器扩展发现冲突调节器 @ " + formatLocation(targetLoc));
                            this.conflictMode = true;
                            EnergyNet otherNet = getNetworkFromLocation(targetLoc);
                            if (otherNet != null && otherNet != this) {
                                otherNet.conflictMode = true;
                                this.conflictPartner = otherNet;
                                otherNet.conflictPartner = this;
                            }
                            updateHologramOnMain(targetLoc, "&c电网冲突：多个能源调节器相连");
                            updateHologramOnMain(regulator, "&c电网冲突：多个能源调节器相连");
                            addConflictHologramsToConnectors();
                            return false;
                        }
                        continue;
                    } else {
                        continue;
                    }
                }
                EnergyNetComponentType targetType = targetComponent.getEnergyComponentType();
                if (targetType == EnergyNetComponentType.CONNECTOR && isConnectorBlocked(targetLoc)) {
                    continue;
                }
                if (isLocationInOtherGrid(targetLoc)) {
                    debugLog("collectNetworkMembers: 调节器扩展发现机器属于其他电网 @ " + formatLocation(targetLoc));
                    EnergyNet otherNet = getNetworkFromLocation(targetLoc);
                    enterConflict(otherNet, targetLoc, "&c电网冲突：电网交叉", "&c电网冲突：该机器已属于其他电网");
                    return false;
                }
                if (targetType == EnergyNetComponentType.CONNECTOR || targetType == EnergyNetComponentType.CAPACITOR) {
                    if (targetType == EnergyNetComponentType.CONNECTOR
                            && ConnectorAgingManager.isConnectorDamaged(targetLoc)) {
                        continue;
                    }
                    if (targetType == EnergyNetComponentType.CONNECTOR) {
                        conflictHologramTargets.add(targetLoc);
                    }
                    visited.add(targetLoc);
                    queue.add(targetLoc);
                    debugLog("collectNetworkMembers: regScan 添加 " + targetType + " @ " + formatLocation(targetLoc)
                            + " visited+queue");
                } else if (targetType == EnergyNetComponentType.GENERATOR
                        || targetType == EnergyNetComponentType.CONSUMER) {
                    visited.add(targetLoc);
                    debugLog("collectNetworkMembers: regScan 添加 " + targetType + " @ " + formatLocation(targetLoc)
                            + " visited");
                }
            }
        }

        while (!queue.isEmpty()) {
            Location current = queue.poll();
            EnergyNetComponent component = getComponent(current);

            // 每处理10个节点检查打断信号
            if (visited.size() % 10 == 0 && (abortRequested || destroyed)) {
                debugLog("collectNetworkMembers: 检测到打断，提前退出BFS");
                return false;
            }

            if (component == null) {
                SlimefunItem dbItem = querySlimefunItemFromDb(current);
                if (dbItem instanceof EnergyNetComponent dbComp) {
                    component = dbComp;
                } else {
                    continue;
                }
            }

            EnergyNetComponentType type = component.getEnergyComponentType();

            // 进度提示：每处理20个节点更新一次（基于BFS队列的实时估算）
            int totalEstimate = Math.max(1, queue.size() + visited.size());
            if (visited.size() % 20 == 0 && visited.size() > 0) {
                int pct = Math.min(90, visited.size() * 90 / totalEstimate);
                initWorkDone = visited.size();
                Slimefun.runSync(() -> {
                    if (!destroyed) {
                        updateHologram(regulator.getBlock(), "&e初始化电网中 " + pct + "%", () -> false);
                    }
                });
            }

            // 检查是否是调节器（冲突检测）
            if (type == EnergyNetComponentType.GENERATOR) {
                SlimefunItem item = (SlimefunItem) component;
                if (item.getId().equals("ENERGY_REGULATOR")) {
                    if (!current.equals(regulator)) {
                        debugLog("collectNetworkMembers: 发现冲突调节器 @ " + formatLocation(current));
                        this.conflictMode = true;
                        // 标记对方电网为冲突状态
                        EnergyNet otherNet = getNetworkFromLocation(current);
                        if (otherNet != null && otherNet != this) {
                            otherNet.conflictMode = true;
                            this.conflictPartner = otherNet;
                            otherNet.conflictPartner = this;
                        }
                        updateHologramOnMain(current, "&c电网冲突：多个能源调节器相连");
                        updateHologramOnMain(regulator, "&c电网冲突：多个能源调节器相连");
                        addConflictHologramsToConnectors();
                        return false;
                    }
                    continue;
                }
            }

            // 检查当前机器是否已被其他电网占用（排除调节器自身）
            if (isLocationInOtherGrid(current)) {
                debugLog("collectNetworkMembers: 机器已被其他电网占用 @ " + formatLocation(current));
                this.conflictMode = true;
                // 标记占用该机器的电网也为冲突状态
                EnergyNet otherNet = getNetworkFromLocation(current);
                if (otherNet != null && otherNet != this) {
                    otherNet.conflictMode = true;
                    this.conflictPartner = otherNet;
                    otherNet.conflictPartner = this;
                    otherNet.updateHologramOnMain(otherNet.regulator, "&c电网冲突：电网交叉");
                }
                // 已注释：不在冲突机器上显示全息（调节器保留）
                // updateHologramOnMain(current, "&c电网冲突：该机器已属于其他电网");
                updateHologramOnMain(regulator, "&c电网冲突：电网交叉");
                addConflictHologramsToConnectors();
                return false;
            }

            debugLog("collectNetworkMembers: 处理 " + type + " @ " + formatLocation(current));

            // 根据组件类型处理
            switch (type) {
                case CONNECTOR:
                    if (!processConnector(current, component, queue, visited)) {
                        return false;
                    }
                    break;
                case CAPACITOR:
                    if (!processCapacitor(current, component, queue, visited)) {
                        return false;
                    }
                    break;
                case GENERATOR:
                case CONSUMER:
                    break;
                default:
                    break;
            }
        }

        debugLog("collectNetworkMembers: BFS完成，visited=" + visited.size() + "个位置");

        // 将已访问的位置分类到相应的映射和集合中
        for (Location loc : visited) {
            EnergyNetComponent component = getComponent(loc);
            if (component == null) {
                continue;
            }

            EnergyNetComponentType type = component.getEnergyComponentType();
            switch (type) {
                case GENERATOR:
                    if (component instanceof EnergyNetProvider provider) {
                        generators.put(loc, provider);
                    }
                    terminusNodes.add(loc);
                    break;
                case CONSUMER:
                    consumers.put(loc, component);
                    terminusNodes.add(loc);
                    break;
                case CAPACITOR:
                    capacitors.put(loc, component);
                    connectorNodes.add(loc);
                    break;
                case CONNECTOR:
                    connectors.put(loc, component);
                    connectorLoad.put(loc, 0L);
                    connectorNodes.add(loc);
                    break;
                default:
                    break;
            }
            connectedLocations.add(loc);
        }

        debugLog("collectNetworkMembers: 分类完成 | 发电机=" + generators.size()
                + " 连接器=" + connectors.size() + " 电容=" + capacitors.size()
                + " 用电器=" + consumers.size() + " 调节器节点=" + regulatorNodes.size()
                + " 连接器节点=" + connectorNodes.size() + " 终端节点=" + terminusNodes.size()
                + " connectedLocations=" + connectedLocations.size());

        debugLog("collectNetworkMembers: 扫描限电器 | connectors=" + connectors.size());
        int limiterFound = 0;
        int limiterNoData = 0;
        int limiterNoLimit = 0;
        for (Location loc : connectors.keySet()) {
            Location above = loc.clone().add(0, 1, 0);
            var limiterData = StorageCacheUtils.getDataContainer(above);
            if (limiterData == null || limiterData.isPendingRemove()) {
                limiterNoData++;
                continue;
            }
            if (!"CURRENT_LIMITER".equals(limiterData.getSfId())) continue;
            limiterFound++;
            String limitStr = limiterData.getData("current-limit");
            // debugLog("collectNetworkMembers: 限电器 @" + formatLocation(above) + " limitStr='" + limitStr + "' connLoc="
            //         + formatLocation(loc));
            if (limitStr != null) {
                try {
                    long limit = Long.parseLong(limitStr);
                    connectorLimits.put(loc, limit);
                    // debugLog("collectNetworkMembers: 设置限电器 @" + formatLocation(loc) + " limit=" + limit);
                } catch (NumberFormatException ignored) {
                    // debugLog("collectNetworkMembers: 限电器 @" + formatLocation(loc) + " 解析失败: " + limitStr);
                }
            } else {
                limiterNoLimit++;
            }
        }
        debugLog("collectNetworkMembers: 限电器扫描结果 | found=" + limiterFound
                + " noLimitStr=" + limiterNoLimit + " noData=" + limiterNoData
                + " connectorLimits.size=" + connectorLimits.size());
        for (Map.Entry<Location, Long> entry : connectorLimits.entrySet()) {
            debugLog("collectNetworkMembers: connectorLimits[" + formatLocation(entry.getKey()) + "] = "
                    + entry.getValue());
        }

        connectorLimits.keySet().removeIf(limitLoc -> !connectors.containsKey(limitLoc));

        connectors.entrySet().removeIf(entry -> {
            Long limit = connectorLimits.get(entry.getKey());
            if (limit != null && limit == 0) {
                connectorNodes.remove(entry.getKey());
                connectorLoad.remove(entry.getKey());
                connectedLocations.remove(entry.getKey());
                return true;
            }
            return false;
        });

        return true;
    }

    private void addConflictHologramsToConnectors() {
        // 已注释：冲突时不在连接器上显示全息
        // Set<Location> targets = new HashSet<>(connectorNodes);
        // targets.addAll(conflictHologramTargets);
        // for (Location connector : targets) {
        //     updateHologramOnMain(connector, "&c电网冲突：电网交叉");
        // }
    }

    private void enterConflict(
            @Nullable EnergyNet otherNet,
            @Nonnull Location conflictLoc,
            @Nonnull String ownMessage,
            @Nonnull String otherMessage) {
        this.conflictMode = true;
        conflictHologramTargets.add(conflictLoc);
        // 已注释：不在冲突位置显示全息
        // updateHologramOnMain(conflictLoc, ownMessage);
        if (otherNet != null && otherNet != this) {
            otherNet.conflictMode = true;
            this.conflictPartner = otherNet;
            otherNet.conflictPartner = this;
            otherNet.updateHologramOnMain(otherNet.regulator, otherMessage);
        }
        addConflictHologramsToConnectors();
    }

    /**
     * 处理连接器节点
     * @return true如果处理成功，false如果发现冲突
     */
    private boolean processConnector(
            Location connectorLoc, EnergyNetComponent connector, Queue<Location> queue, Set<Location> visited) {
        boolean isLongRange = connector instanceof LongRangeConnector;
        int range = connector.getRange();
        debugLog("processConnector: 范围=" + range + " @ " + formatLocation(connectorLoc) + " 长途=" + isLongRange);

        // 搜索6个轴向（上下左右前后）range格内的所有可能位置
        int[][] axes = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        Map<Integer, AxisTarget> axisTargets = isLongRange ? new HashMap<>() : null;
        for (int axisIdx = 0; axisIdx < axes.length; axisIdx++) {
            int[] axis = axes[axisIdx];
            for (int i = 1; i <= range; i++) {
                if (abortRequested || destroyed) {
                    debugLog("processConnector: 检测到打断");
                    return false;
                }
                Location targetLoc = connectorLoc.clone().add(axis[0] * i, axis[1] * i, axis[2] * i);

                if (visited.contains(targetLoc)) {
                    debugLog("processConnector: 扫描 @" + formatLocation(targetLoc)
                            + " visited已包含"
                            + (isLongRange ? " 长途=" + isLongRange : ""));
                    if (isLongRange && isConnectorLocation(targetLoc)) {
                        debugLog("processConnector: 长途连接器遇已访问连接器，break");
                        break;
                    }
                    continue;
                }

                // 先尝试 EnergyNetComponent
                EnergyNetComponent targetComponent = getComponent(targetLoc);
                debugLog("processConnector: 扫描 @" + formatLocation(targetLoc)
                        + " getComponent="
                        + (targetComponent != null
                                ? targetComponent.getEnergyComponentType().toString()
                                : "null")
                        + " (connector@" + formatLocation(connectorLoc) + " range=" + range + ")");
                if (targetComponent != null) {
                    EnergyNetComponentType targetType = targetComponent.getEnergyComponentType();

                    if (!checkRangeValidation(connectorLoc, targetLoc, targetType)) {
                        continue;
                    }

                    if (isLocationInOtherGrid(targetLoc)) {
                        debugLog("processConnector: 冲突 - 机器已属于其他电网 @ " + formatLocation(targetLoc));
                        EnergyNet otherNet = getNetworkFromLocation(targetLoc);
                        enterConflict(otherNet, targetLoc, "&c电网冲突：电网交叉", "&c电网冲突：该机器已属于其他电网");
                        return false;
                    }

                    debugLog("processConnector: 发现 " + targetType + " @ " + formatLocation(targetLoc));

                    if (targetType == EnergyNetComponentType.CONNECTOR) {
                        if (ConnectorAgingManager.isConnectorDamaged(targetLoc)) {
                            if (isLongRange) {
                                axisTargets.put(axisIdx, new AxisTarget(targetLoc, true));
                                break;
                            }
                            continue;
                        }
                        if (isConnectorBlocked(targetLoc)) {
                            if (isLongRange) {
                                axisTargets.put(axisIdx, new AxisTarget(targetLoc, true));
                                break;
                            }
                            continue;
                        }
                        visited.add(targetLoc);
                        queue.add(targetLoc);
                        if (isLongRange) {
                            axisTargets.put(axisIdx, new AxisTarget(targetLoc, false));
                            break;
                        }
                    } else if (isLongRange) {
                        continue;
                    } else {
                        visited.add(targetLoc);
                        if (targetType == EnergyNetComponentType.CAPACITOR) {
                            queue.add(targetLoc);
                        }
                    }
                    continue;
                }

                // 缓存未命中时尝试从数据库查询
                SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(targetLoc);
                if (sfItem == null) {
                    sfItem = querySlimefunItemFromDb(targetLoc);
                }
                if (sfItem == null) {
                    continue;
                }

                // 从数据库找到的 EnergyNetComponent
                if (sfItem instanceof EnergyNetComponent dbComponent) {
                    EnergyNetComponentType targetType = dbComponent.getEnergyComponentType();

                    if (!checkRangeValidation(connectorLoc, targetLoc, targetType)) {
                        continue;
                    }

                    if (isLocationInOtherGrid(targetLoc)) {
                        debugLog("processConnector: 冲突 - 机器已属于其他电网 @ " + formatLocation(targetLoc));
                        EnergyNet otherNet = getNetworkFromLocation(targetLoc);
                        enterConflict(otherNet, targetLoc, "&c电网冲突：电网交叉", "&c电网冲突：该机器已属于其他电网");
                        return false;
                    }

                    visited.add(targetLoc);
                    debugLog("processConnector: 发现(DB) " + targetType + " @ " + formatLocation(targetLoc));

                    if (targetType == EnergyNetComponentType.CONNECTOR) {
                        if (ConnectorAgingManager.isConnectorDamaged(targetLoc)) {
                            visited.remove(targetLoc);
                            if (isLongRange) {
                                axisTargets.put(axisIdx, new AxisTarget(targetLoc, true));
                                break;
                            }
                            continue;
                        }
                        if (isConnectorBlocked(targetLoc)) {
                            visited.remove(targetLoc);
                            if (isLongRange) {
                                axisTargets.put(axisIdx, new AxisTarget(targetLoc, true));
                                break;
                            }
                            continue;
                        }
                        queue.add(targetLoc);
                        if (isLongRange) {
                            axisTargets.put(axisIdx, new AxisTarget(targetLoc, false));
                            break;
                        }
                    } else if (isLongRange) {
                        visited.remove(targetLoc);
                        continue;
                    } else {
                        if (targetType == EnergyNetComponentType.CAPACITOR) {
                            queue.add(targetLoc);
                        }
                    }
                    continue;
                }

                // 发现调节器（调节器是GENERATOR类型但不实现EnergyNetComponent）
                if (sfItem.getId().equals("ENERGY_REGULATOR")) {
                    if (!targetLoc.equals(regulator)) {
                        debugLog("processConnector: 发现冲突调节器 @ " + formatLocation(targetLoc));
                        this.conflictMode = true;
                        EnergyNet otherNet = getNetworkFromLocation(targetLoc);
                        if (otherNet != null && otherNet != this) {
                            otherNet.conflictMode = true;
                            this.conflictPartner = otherNet;
                            otherNet.conflictPartner = this;
                        }
                        updateHologramOnMain(targetLoc, "&c电网冲突：多个能源调节器相连");
                        updateHologramOnMain(regulator, "&c电网冲突：多个能源调节器相连");
                        addConflictHologramsToConnectors();
                        return false;
                    }
                    // 是本电网的调节器，加入visited但不加入queue（已在BFS起点）
                    visited.add(targetLoc);
                    debugLog("processConnector: 发现本电网调节器 @ " + formatLocation(targetLoc));
                }
            }
        }
        if (isLongRange && axisTargets != null) {
            longRangeAxisTargets.put(connectorLoc, axisTargets);
        }
        return true;
    }

    private void checkLongRangeAxisTargets() {
        if (longRangeAxisTargets.isEmpty()) {
            return;
        }
        longRangeCheckTickCounter++;
        if (longRangeCheckTickCounter < 4) {
            return;
        }
        longRangeCheckTickCounter = 0;

        for (Map.Entry<Location, Map<Integer, AxisTarget>> entry : longRangeAxisTargets.entrySet()) {
            Location connLoc = entry.getKey();
            Map<Integer, AxisTarget> axisTargets = entry.getValue();
            for (Map.Entry<Integer, AxisTarget> axisEntry : axisTargets.entrySet()) {
                AxisTarget axisTarget = axisEntry.getValue();
                Location targetLoc = axisTarget.location;
                boolean wasDamaged = axisTarget.wasDamaged;
                SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(targetLoc);
                boolean targetExists = sfItem instanceof EnergyNetComponent
                        && ((EnergyNetComponent) sfItem).getEnergyComponentType() == EnergyNetComponentType.CONNECTOR;
                boolean isDamaged =
                        ConnectorAgingManager.isConnectorDamaged(targetLoc) || isConnectorBlocked(targetLoc);
                if (!targetExists || wasDamaged != isDamaged) {
                    debugLog("checkLongRangeAxisTargets: 长途连接器@" + formatLocation(connLoc)
                            + " 轴向目标@" + formatLocation(targetLoc)
                            + " 状态变化 exists=" + targetExists
                            + " wasDamaged=" + wasDamaged + " isDamaged=" + isDamaged
                            + " → 触发重新初始化");
                    markDirty(connLoc);
                    return;
                }
            }
        }
    }

    /**
     * 处理电容器节点 - 电容子网络搜索
     * @return true如果处理成功，false如果发现冲突
     */
    private boolean processCapacitor(
            Location capacitorLoc, EnergyNetComponent capacitor, Queue<Location> queue, Set<Location> visited) {
        // 电容只能连接6方向相邻的电容或连接器
        int[][] axes = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (int[] axis : axes) {
            if (abortRequested || destroyed) {
                return false;
            }

            Location targetLoc = capacitorLoc.clone().add(axis[0], axis[1], axis[2]);

            if (visited.contains(targetLoc)) {
                continue;
            }

            EnergyNetComponent targetComponent = getComponent(targetLoc);
            if (targetComponent == null) {
                SlimefunItem dbItem = querySlimefunItemFromDb(targetLoc);
                if (dbItem instanceof EnergyNetComponent dbComp
                        && (dbComp.getEnergyComponentType() == EnergyNetComponentType.CAPACITOR
                                || dbComp.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR)) {
                    targetComponent = dbComp;
                } else {
                    continue;
                }
            }

            EnergyNetComponentType targetType = targetComponent.getEnergyComponentType();
            if (targetType != EnergyNetComponentType.CAPACITOR && targetType != EnergyNetComponentType.CONNECTOR) {
                continue;
            }

            if (targetType == EnergyNetComponentType.CONNECTOR) {
                if (ConnectorAgingManager.isConnectorDamaged(targetLoc) || isConnectorBlocked(targetLoc)) {
                    continue;
                }
                if (!checkRangeValidation(targetLoc, capacitorLoc, EnergyNetComponentType.CAPACITOR)) {
                    continue;
                }
            }

            if (targetType == EnergyNetComponentType.CAPACITOR) {
                var data = StorageCacheUtils.getDataContainer(targetLoc);
                if (data != null && Slimefun.getMachineDamageService().isMachineDamaged(data)) {
                    continue;
                }
            }

            if (isLocationInOtherGrid(targetLoc)) {
                // 已注释：不在冲突机器上显示全息
                // updateHologramOnMain(targetLoc, "&c电网冲突：该机器已属于其他电网");
                return false;
            }

            visited.add(targetLoc);
            queue.add(targetLoc);
        }
        return true;
    }

    /**
     * 将连接器负载传播到上方的电量计数器
     */
    private void propagateToEnergyMeters() {
        for (Map.Entry<Location, Long> entry : connectorLoad.entrySet()) {
            long load = entry.getValue();
            if (load <= 0) continue;
            Location above = entry.getKey().clone().add(0, 1, 0);
            var data = StorageCacheUtils.getDataContainer(above);
            if (data == null || data.isPendingRemove()) continue;
            if (!"ENERGY_METER".equals(data.getSfId())) continue;
            long acc = parseLongOrZero(data, "energy-counter");
            acc = NumberUtils.flowSafeAddition(acc, load);
            data.setData("energy-counter", String.valueOf(acc));
        }
    }

    private void scheduleSelfTick() {
        cancelSelfTick();
        int delay = DEBUG ? 40 : Slimefun.getCfg().getInt("URID.custom-ticker-delay");
        tickInterval = delay;
        selfTickTaskId = Bukkit.getScheduler()
                .runTaskTimer(Slimefun.instance(), this::tickSelfMainThread, delay, delay)
                .getTaskId();
        debugLog("scheduleSelfTick: 自调度已启动 delay=" + delay + " (DEBUG=" + DEBUG + ")");
    }

    private void cancelSelfTick() {
        if (selfTickTaskId >= 0) {
            Bukkit.getScheduler().cancelTask(selfTickTaskId);
            selfTickTaskId = -1;
            debugLog("cancelSelfTick: 自调度已取消");
        }
        skipTicks.set(0);
    }

    private void tickSelfMainThread() {
        if (skipTicks.get() > 0) {
            skipTicks.decrementAndGet();
            return;
        }
        if (!selfTicking.compareAndSet(false, true)) {
            return;
        }
        boolean submitted = false;
        try {
            if (destroyed || !initialized || initializing || conflictMode) {
                return;
            }

            boolean hasMembers = !connectorNodes.isEmpty() || !terminusNodes.isEmpty();
            if (!hasMembers) {
                return;
            }

            long phaseStartNanos = System.nanoTime();

            // Phase 1: Bukkit/Storage I/O stays on the main thread.
            debugLog("tickSelf: [Phase1] 采样开始 | 发电机=" + generators.size()
                    + " 连接器=" + connectors.size() + " 电容=" + capacitors.size()
                    + " 用电器=" + consumers.size()
                    + " genPaths=" + countTotalPaths(generatorPaths)
                    + " capPaths=" + countTotalPaths(capacitorPaths));
            tickAllGenerators(timestamp -> {});
            tickAllCapacitors();

            totalProducedThisTick = netNewEnergy + calcNonChargeableRemaining();

            debugLog("tickSelf: [Phase1] 采样完成 | totalProduced=" + totalProducedThisTick
                    + " netNewEnergy=" + netNewEnergy
                    + " nonChargeableRemaining=" + calcNonChargeableRemaining());

            GridTickSnapshot snapshot = collectTickSnapshot();
            if (snapshot == null) {
                return;
            }

            GRID_TICK_EXECUTOR.submit(() -> {
                TransferResult result;
                if (destroyed) {
                    selfTicking.set(false);
                    return;
                }
                try {
                    result = computeTransfers(snapshot);
                } catch (Exception | LinkageError throwable) {
                    Slimefun.logger()
                            .log(java.util.logging.Level.SEVERE, "EnergyNet self tick calculation failed", throwable);
                    selfTicking.set(false);
                    return;
                }

                var writebackTask = Slimefun.runSync(() -> {
                    try {
                        if (!destroyed && initialized && !initializing && !conflictMode) {
                            applyTransferResult(result);
                            checkLongRangeAxisTargets();
                        }
                    } catch (Exception | LinkageError throwable) {
                        Slimefun.logger()
                                .log(java.util.logging.Level.SEVERE, "EnergyNet self tick writeback failed", throwable);
                    } finally {
                        long elapsedNanos = System.nanoTime() - phaseStartNanos;
                        long tickIntervalNanos = tickInterval * 50_000_000L;
                        if (elapsedNanos > tickIntervalNanos) {
                            skipTicks.set(1);
                            String msg = "[EnergyNet] tick耗时 " + (elapsedNanos / 1_000_000) + "ms > "
                                    + (tickInterval * 50) + "ms, 跳过下一次tick";
                            Slimefun.logger().info(msg);
                            debugLog(msg);
                        }
                        selfTicking.set(false);
                    }
                });
                if (writebackTask == null) {
                    selfTicking.set(false);
                }
            });
            submitted = true;
        } finally {
            if (!submitted) {
                selfTicking.set(false);
            }
        }
    }

    @Nullable private GridTickSnapshot collectTickSnapshot() {
        Map<Location, Long> generatorCharges = new HashMap<>();
        Map<Location, Long> generatorCapacities = new HashMap<>();
        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            Location loc = entry.getKey();
            EnergyNetProvider generator = entry.getValue();
            if (generator.isChargeable()) {
                generatorCharges.put(loc, generator.getChargeLong(loc));
            }
            generatorCapacities.put(loc, generator.getChargeCapacityLong(loc));
        }

        Map<Location, Long> capacitorCharges = new HashMap<>();
        Map<Location, Long> capacitorCapacities = new HashMap<>();
        for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
            Location loc = entry.getKey();
            EnergyNetComponent capacitor = entry.getValue();
            capacitorCharges.put(loc, capacitor.getChargeLong(loc));
            capacitorCapacities.put(loc, capacitor.getChargeCapacityLong(loc));
        }

        Map<Location, Long> consumerCharges = new HashMap<>();
        Map<Location, Long> consumerCapacities = new HashMap<>();
        for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
            Location loc = entry.getKey();
            EnergyNetComponent consumer = entry.getValue();
            consumerCharges.put(loc, consumer.getChargeLong(loc));
            consumerCapacities.put(loc, consumer.getChargeCapacityLong(loc));
        }

        // debugLog("collectTickSnapshot: connectorLimits=" + connectorLimits.size());
        // for (Map.Entry<Location, Long> entry : connectorLimits.entrySet()) {
        //     debugLog("collectTickSnapshot: limit[" + formatLocation(entry.getKey()) + "] = " + entry.getValue());
        // }

        return new GridTickSnapshot(
                generatorCharges,
                generatorCapacities,
                new HashMap<>(nonChargeableSupply),
                capacitorCharges,
                capacitorCapacities,
                consumerCharges,
                consumerCapacities,
                new HashMap<>(connectorLimits),
                Collections.unmodifiableMap(generatorPaths),
                Collections.unmodifiableMap(capacitorPaths),
                netNewEnergy);
    }

    @Nonnull
    private static TransferResult computeTransfers(@Nonnull GridTickSnapshot snap) {
        Map<Location, Long> generatorDeltas = new HashMap<>();
        Map<Location, Long> capacitorDeltas = new HashMap<>();
        Map<Location, Long> consumerDeltas = new HashMap<>();
        Map<Location, Long> connectorLoads = new HashMap<>();
        Map<Location, Long> remainingGeneratorCharges = new HashMap<>(snap.generatorCharges);
        Map<Location, Long> remainingNonChargeableSupply = new HashMap<>(snap.nonChargeableSupply);

        // debugLog("computeTransfers: snap.connectorLimits=" + snap.connectorLimits.size());
        // for (Map.Entry<Location, Long> entry : snap.connectorLimits.entrySet()) {
        //     debugLog("computeTransfers: snap.limit[" + formatLocation(entry.getKey()) + "] = " + entry.getValue());
        // }

        long generatorSupply = 0;
        for (long charge : snap.generatorCharges.values()) {
            generatorSupply = NumberUtils.flowSafeAddition(generatorSupply, charge);
        }
        for (long charge : snap.nonChargeableSupply.values()) {
            generatorSupply = NumberUtils.flowSafeAddition(generatorSupply, charge);
        }

        long totalDemand = calculateTotalDemandSnapshot(snap.consumerCharges, snap.consumerCapacities);
        long maxGeneratorTransfer = generatorSupply >= totalDemand ? totalDemand : generatorSupply;
        long supplyLeft = transferFromGeneratorsSnapshot(
                snap,
                remainingGeneratorCharges,
                remainingNonChargeableSupply,
                generatorDeltas,
                consumerDeltas,
                connectorLoads,
                maxGeneratorTransfer);
        long generatorTransferred = Math.max(0, maxGeneratorTransfer - supplyLeft);

        long remainingDemand = totalDemand - generatorTransferred;
        if (remainingDemand > 0) {
            transferFromCapacitorsSnapshot(snap, capacitorDeltas, consumerDeltas, connectorLoads, remainingDemand);
        }

        long nonChargeableExcessEnergy = 0;
        long chargeableExcessEnergy = 0;
        if (generatorSupply >= totalDemand) {
            nonChargeableExcessEnergy = sumValues(remainingNonChargeableSupply);
            chargeableExcessEnergy = sumValues(remainingGeneratorCharges);
        }
        long excessEnergy = NumberUtils.flowSafeAddition(nonChargeableExcessEnergy, chargeableExcessEnergy);

        debugLog("computeTransfers: 发电供给=" + generatorSupply + " 总需求=" + totalDemand
                + " 发电机传输=" + generatorTransferred + " 剩余需求=" + remainingDemand
                + " excessEnergy=" + excessEnergy
                + " (非充电=" + nonChargeableExcessEnergy + " 充电=" + chargeableExcessEnergy + ")"
                + " genDeltas=" + generatorDeltas.size()
                + " capDeltas=" + capacitorDeltas.size()
                + " conDeltas=" + consumerDeltas.size());

        return new TransferResult(
                generatorDeltas,
                capacitorDeltas,
                consumerDeltas,
                connectorLoads,
                remainingNonChargeableSupply,
                excessEnergy,
                nonChargeableExcessEnergy);
    }

    private static long transferFromGeneratorsSnapshot(
            @Nonnull GridTickSnapshot snap,
            @Nonnull Map<Location, Long> generatorCharges,
            @Nonnull Map<Location, Long> nonChargeableSupply,
            @Nonnull Map<Location, Long> generatorDeltas,
            @Nonnull Map<Location, Long> consumerDeltas,
            @Nonnull Map<Location, Long> connectorLoads,
            long maxEnergy) {
        long remainingEnergy = maxEnergy;

        for (List<EnergyPath> pathGroup : groupPathsBySourceConsumerAndLength(snap.generatorPaths)) {
            if (remainingEnergy <= 0) {
                break;
            }

            EnergyPath firstPath = pathGroup.get(0);
            Location generatorLoc = firstPath.source;
            Location consumerLoc = firstPath.consumer;
            boolean nonChargeable = !snap.generatorCharges.containsKey(generatorLoc);
            long generatorCharge = nonChargeable
                    ? nonChargeableSupply.getOrDefault(generatorLoc, 0L)
                    : generatorCharges.getOrDefault(generatorLoc, 0L);
            long consumerCharge =
                    snap.consumerCharges.getOrDefault(consumerLoc, 0L) + consumerDeltas.getOrDefault(consumerLoc, 0L);
            long consumerCapacity = snap.consumerCapacities.getOrDefault(consumerLoc, 0L);

            if (generatorCharge <= 0 || consumerCharge >= consumerCapacity) {
                debugLog("transferGen: 跳过 | gen=" + formatLocation(generatorLoc)
                        + " con=" + formatLocation(consumerLoc)
                        + " genCharge=" + generatorCharge
                        + " conCharge=" + consumerCharge + "/" + consumerCapacity
                        + " remainingEnergy=" + remainingEnergy
                        + " nonChargeable=" + nonChargeable
                        + " paths=" + pathGroup.size());
                continue;
            }

            long rawAmount = Math.min(Math.min(generatorCharge, consumerCapacity - consumerCharge), remainingEnergy);
            long transferAmount =
                    allocatePathGroupWithLimits(pathGroup, snap.connectorLimits, connectorLoads, rawAmount);
            if (transferAmount <= 0) {
                debugLog("transferGen: 跳过(限电器/0) | gen=" + formatLocation(generatorLoc)
                        + " con=" + formatLocation(consumerLoc)
                        + " rawAmount=" + rawAmount
                        + " paths=" + pathGroup.size());
                continue;
            }

            if (nonChargeable) {
                long remaining = nonChargeableSupply.getOrDefault(generatorLoc, 0L) - transferAmount;
                if (remaining <= 0) {
                    nonChargeableSupply.remove(generatorLoc);
                } else {
                    nonChargeableSupply.put(generatorLoc, remaining);
                }
            } else {
                generatorDeltas.merge(generatorLoc, -transferAmount, Long::sum);
                generatorCharges.merge(generatorLoc, -transferAmount, Long::sum);
            }

            consumerDeltas.merge(consumerLoc, transferAmount, Long::sum);
            remainingEnergy -= transferAmount;
        }

        return Math.max(0, remainingEnergy);
    }

    private static long transferFromCapacitorsSnapshot(
            @Nonnull GridTickSnapshot snap,
            @Nonnull Map<Location, Long> capacitorDeltas,
            @Nonnull Map<Location, Long> consumerDeltas,
            @Nonnull Map<Location, Long> connectorLoads,
            long maxEnergy) {
        long remainingEnergy = maxEnergy;
        Map<Location, Long> capacitorCharges = new HashMap<>(snap.capacitorCharges);

        for (List<EnergyPath> pathGroup : groupPathsBySourceConsumerAndLength(snap.capacitorPaths)) {
            if (remainingEnergy <= 0) {
                break;
            }

            EnergyPath firstPath = pathGroup.get(0);
            Location capacitorLoc = firstPath.source;
            Location consumerLoc = firstPath.consumer;
            long capacitorCharge = capacitorCharges.getOrDefault(capacitorLoc, 0L);
            long consumerCharge =
                    snap.consumerCharges.getOrDefault(consumerLoc, 0L) + consumerDeltas.getOrDefault(consumerLoc, 0L);
            long consumerCapacity = snap.consumerCapacities.getOrDefault(consumerLoc, 0L);

            if (capacitorCharge <= 0 || consumerCharge >= consumerCapacity) {
                continue;
            }

            long rawAmount = Math.min(Math.min(capacitorCharge, consumerCapacity - consumerCharge), remainingEnergy);
            long transferAmount =
                    allocatePathGroupWithLimits(pathGroup, snap.connectorLimits, connectorLoads, rawAmount);
            if (transferAmount <= 0) {
                continue;
            }

            capacitorDeltas.merge(capacitorLoc, -transferAmount, Long::sum);
            capacitorCharges.merge(capacitorLoc, -transferAmount, Long::sum);
            consumerDeltas.merge(consumerLoc, transferAmount, Long::sum);
            remainingEnergy -= transferAmount;
        }

        return Math.max(0, remainingEnergy);
    }

    @Nonnull
    private static Collection<List<EnergyPath>> groupPathsBySourceConsumerAndLength(
            @Nonnull Map<Location, Set<EnergyPath>> pathMap) {
        Map<PathGroupKey, List<EnergyPath>> pathGroups = new LinkedHashMap<>();
        for (EnergyPath path : getSortedPathsStatic(pathMap)) {
            PathGroupKey key = new PathGroupKey(
                    path.source.getBlockX(),
                    path.source.getBlockY(),
                    path.source.getBlockZ(),
                    path.consumer.getBlockX(),
                    path.consumer.getBlockY(),
                    path.consumer.getBlockZ(),
                    path.length);
            pathGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
        }
        return pathGroups.values();
    }

    private record PathGroupKey(
            int sourceX, int sourceY, int sourceZ, int consumerX, int consumerY, int consumerZ, int length) {}

    @Nonnull
    private static List<EnergyPath> getSortedPathsStatic(@Nonnull Map<Location, Set<EnergyPath>> pathMap) {
        List<EnergyPath> allPaths = new ArrayList<>();
        for (Set<EnergyPath> paths : pathMap.values()) {
            allPaths.addAll(paths);
        }
        allPaths.sort((p1, p2) -> Integer.compare(p1.length, p2.length));
        return allPaths;
    }

    private static void recordConnectorLoadSnapshot(
            @Nonnull List<EnergyPath> pathGroup, long transferAmount, @Nonnull Map<Location, Long> connectorLoads) {
        long loadPerPath = transferAmount / pathGroup.size();
        long remainder = transferAmount % pathGroup.size();
        for (int i = 0; i < pathGroup.size(); i++) {
            long pathLoad = loadPerPath + (i < remainder ? 1 : 0);
            if (pathLoad <= 0) {
                continue;
            }
            for (Location connectorLoc : pathGroup.get(i).connectors) {
                connectorLoads.merge(connectorLoc, pathLoad, Long::sum);
            }
        }
    }

    private static long allocatePathGroupWithLimits(
            @Nonnull List<EnergyPath> pathGroup,
            @Nonnull Map<Location, Long> limits,
            @Nonnull Map<Location, Long> connectorLoads,
            long maxAmount) {
        int n = pathGroup.size();
        if (n == 0 || maxAmount <= 0) return 0;

        boolean hasLimiters = false;
        for (EnergyPath path : pathGroup) {
            for (Location connLoc : path.connectors) {
                if (path.source.equals(connLoc)) continue;
                if (limits.containsKey(connLoc)) {
                    hasLimiters = true;
                    break;
                }
            }
            if (hasLimiters) break;
        }

        if (!hasLimiters) {
            debugLog("allocatePathGroup: 无限器 | paths=" + n + " maxAmount=" + maxAmount + " => 不受限");
            recordConnectorLoadSnapshot(pathGroup, maxAmount, connectorLoads);
            return maxAmount;
        }

        EnergyPath firstPath = pathGroup.get(0);
        debugLog("allocatePathGroup: ENTRY | src=" + formatLocation(firstPath.source)
                + " dst=" + formatLocation(firstPath.consumer)
                + " paths=" + n + " maxAmount=" + maxAmount
                + " limits.size=" + limits.size()
                + " connectorLoads.size=" + connectorLoads.size());

        Map<Location, Integer> limiterPathCount = new HashMap<>();
        for (EnergyPath path : pathGroup) {
            for (Location connLoc : path.connectors) {
                if (path.source.equals(connLoc)) continue;
                if (limits.containsKey(connLoc)) {
                    limiterPathCount.merge(connLoc, 1, Integer::sum);
                }
            }
        }

        long[] pathShare = new long[n];
        Map<Location, Long> localLoads = new HashMap<>();
        for (int i = 0; i < n; i++) {
            pathShare[i] = computePathShare(pathGroup.get(i), limits, connectorLoads, localLoads, limiterPathCount);
        }

        boolean allSufficient = true;
        long equalBase = maxAmount / n;
        for (int i = 0; i < n; i++) {
            if (pathShare[i] < equalBase) {
                allSufficient = false;
                break;
            }
        }
        if (allSufficient) {
            debugLog("allocatePathGroup: 快速路径 | 所有路径份额充足, 均分 maxAmount=" + maxAmount);
            recordConnectorLoadSnapshot(pathGroup, maxAmount, connectorLoads);
            return maxAmount;
        }

        long[] pathAllocated = new long[n];
        boolean[] active = new boolean[n];
        for (int i = 0; i < n; i++) active[i] = true;
        int activeCount = n;
        long remaining = maxAmount;

        int round = 0;
        while (remaining > 0 && activeCount > 0) {
            round++;
            long equalShare = remaining / activeCount;
            if (equalShare <= 0) break;

            int cappedIdx = -1;

            for (int i = 0; i < n; i++) {
                if (!active[i]) continue;
                long alloc = Math.min(pathShare[i], equalShare);
                if (alloc < equalShare) {
                    pathAllocated[i] += alloc;
                    remaining -= alloc;
                    active[i] = false;
                    activeCount--;
                    cappedIdx = i;

                    addToLocalLoads(localLoads, pathGroup.get(i), alloc);
                    for (Location connLoc : pathGroup.get(i).connectors) {
                        if (pathGroup.get(i).source.equals(connLoc)) continue;
                        if (limiterPathCount.containsKey(connLoc)) {
                            limiterPathCount.merge(connLoc, -1, Integer::sum);
                            if (limiterPathCount.get(connLoc) <= 0) {
                                limiterPathCount.remove(connLoc);
                            }
                        }
                    }
                    break;
                }
            }

            if (cappedIdx >= 0) {
                for (int i = 0; i < n; i++) {
                    if (!active[i]) continue;
                    pathShare[i] =
                            computePathShare(pathGroup.get(i), limits, connectorLoads, localLoads, limiterPathCount);
                }
                continue;
            }

            for (int i = 0; i < n; i++) {
                if (!active[i]) continue;
                pathAllocated[i] += equalShare;
                addToLocalLoads(localLoads, pathGroup.get(i), equalShare);
            }
            remaining = 0;
            break;
        }

        long totalAllocated = 0;
        for (int i = 0; i < n; i++) {
            if (pathAllocated[i] > 0) {
                totalAllocated = NumberUtils.flowSafeAddition(totalAllocated, pathAllocated[i]);
                for (Location connLoc : pathGroup.get(i).connectors) {
                    if (pathGroup.get(i).source.equals(connLoc)) continue;
                    connectorLoads.merge(connLoc, pathAllocated[i], Long::sum);
                }
            }
        }

        debugLog("allocatePathGroup: RESULT | totalAllocated=" + totalAllocated
                + " / maxAmount=" + maxAmount
                + " constrained=" + (totalAllocated < maxAmount));
        for (int i = 0; i < n; i++) {
            if (pathAllocated[i] > 0) {
                debugLog("allocatePathGroup: path#" + i + " allocated=" + pathAllocated[i] + " connectors="
                        + pathGroup.get(i).connectors.size());
            }
        }

        return totalAllocated;
    }

    private static long computePathLimiterAvailable(
            @Nonnull EnergyPath path,
            @Nonnull Map<Location, Long> limits,
            @Nonnull Map<Location, Long> globalLoads,
            @Nonnull Map<Location, Long> localLoads) {
        long available = Long.MAX_VALUE;
        for (Location connLoc : path.connectors) {
            if (path.source.equals(connLoc)) continue;
            Long limit = limits.get(connLoc);
            if (limit == null) continue;
            if (limit == 0) return 0;
            long globalUsed = globalLoads.getOrDefault(connLoc, 0L);
            long localUsed = localLoads.getOrDefault(connLoc, 0L);
            long remaining = limit - globalUsed - localUsed;
            if (remaining < available) available = remaining;
            if (remaining <= 0) return 0;
        }
        return available;
    }

    private static void addToLocalLoads(
            @Nonnull Map<Location, Long> localLoads, @Nonnull EnergyPath path, long amount) {
        for (Location connLoc : path.connectors) {
            if (path.source.equals(connLoc)) continue;
            localLoads.merge(connLoc, amount, Long::sum);
        }
    }

    private static long computePathShare(
            @Nonnull EnergyPath path,
            @Nonnull Map<Location, Long> limits,
            @Nonnull Map<Location, Long> globalLoads,
            @Nonnull Map<Location, Long> localLoads,
            @Nonnull Map<Location, Integer> limiterPathCount) {
        long share = Long.MAX_VALUE;
        for (Location connLoc : path.connectors) {
            if (path.source.equals(connLoc)) continue;
            Long limit = limits.get(connLoc);
            if (limit == null) continue;
            if (limit == 0) return 0;
            int pathCount = limiterPathCount.getOrDefault(connLoc, 1);
            long globalUsed = globalLoads.getOrDefault(connLoc, 0L);
            long localUsed = localLoads.getOrDefault(connLoc, 0L);
            long perPath = (limit - globalUsed - localUsed) / pathCount;
            if (perPath < share) share = perPath;
            if (perPath <= 0) return 0;
        }
        return share;
    }

    private static long calculateTotalDemandSnapshot(
            @Nonnull Map<Location, Long> consumerCharges, @Nonnull Map<Location, Long> consumerCapacities) {
        long demand = 0;
        for (Map.Entry<Location, Long> entry : consumerCharges.entrySet()) {
            long capacity = consumerCapacities.getOrDefault(entry.getKey(), 0L);
            long charge = entry.getValue();
            if (charge < capacity) {
                demand = NumberUtils.flowSafeAddition(demand, capacity - charge);
            }
        }
        return demand;
    }

    private static long sumValues(@Nonnull Map<Location, Long> values) {
        long total = 0;
        for (long value : values.values()) {
            total = NumberUtils.flowSafeAddition(total, value);
        }
        return total;
    }

    private void applyTransferResult(@Nonnull TransferResult result) {
        debugLog("applyTransferResult: [Phase3] 写回开始"
                + " | genDeltas=" + result.generatorChargeDeltas.size()
                + " capDeltas=" + result.capacitorChargeDeltas.size()
                + " conDeltas=" + result.consumerChargeDeltas.size()
                + " excessEnergy=" + result.excessEnergy
                + " connectorLoads=" + result.connectorLoads.size());

        for (Map.Entry<Location, Long> entry : result.generatorChargeDeltas.entrySet()) {
            EnergyNetProvider generator = generators.get(entry.getKey());
            if (generator != null && generator.isChargeable()) {
                long oldCharge = generator.getChargeLong(entry.getKey());
                generator.setCharge(entry.getKey(), Math.max(0, oldCharge + entry.getValue()));
            }
        }

        for (Map.Entry<Location, Long> entry : result.consumerChargeDeltas.entrySet()) {
            EnergyNetComponent consumer = consumers.get(entry.getKey());
            if (consumer != null) {
                long oldCharge = consumer.getChargeLong(entry.getKey());
                long capacity = consumer.getChargeCapacityLong(entry.getKey());
                consumer.setCharge(entry.getKey(), Math.min(capacity, oldCharge + entry.getValue()));
            }
        }

        for (Map.Entry<Location, Long> entry : result.capacitorChargeDeltas.entrySet()) {
            EnergyNetComponent capacitor = capacitors.get(entry.getKey());
            if (capacitor != null) {
                long oldCharge = capacitor.getChargeLong(entry.getKey());
                capacitor.setCharge(entry.getKey(), Math.max(0, oldCharge + entry.getValue()));
            }
        }

        long consumedByGrid = 0;
        for (long delta : result.consumerChargeDeltas.values()) {
            if (delta > 0) {
                consumedByGrid = NumberUtils.flowSafeAddition(consumedByGrid, delta);
            }
        }
        totalConsumedThisTick = consumedByGrid;

        connectorLoad.clear();
        connectorLoad.putAll(result.connectorLoads);
        nonChargeableSupply.clear();
        nonChargeableSupply.putAll(result.remainingNonChargeableSupply);

        long stored = storeRemainingEnergy(result.excessEnergy);
        totalStoredThisTick = stored;

        if (result.excessEnergy > 0 && stored == 0) {
            int paths = 0;
            for (Map<Location, Set<EnergyPath>> capPaths : generatorToCapacitorPaths.values()) {
                paths += capPaths.size();
            }
            debugLog("tickSelf: excessEnergy=" + result.excessEnergy
                    + " but stored=0 | genToCapPaths=" + paths
                    + " caps=" + capacitors.size());
        }
        long nonChargeableStored = Math.min(stored, result.nonChargeableExcessEnergy);
        long chargeableStored = Math.max(0, stored - nonChargeableStored);
        if (nonChargeableStored > 0) {
            reduceSupply(result.remainingNonChargeableSupply, nonChargeableStored);
        }
        nonChargeableSupply.clear();
        nonChargeableSupply.putAll(result.remainingNonChargeableSupply);
        reduceStoredChargeableEnergy(chargeableStored);

        propagateToEnergyMeters();
        ConnectorAgingManager.processAging(this);

        long currentTotalCharge = calculateTotalCharge();
        totalNetStoredThisTick = currentTotalCharge - lastTotalCharge;
        lastTotalCharge = currentTotalCharge;
        lastSupply = calculateTotalSupply();
        lastDemand = calculateTotalDemand();
        firstTickDone = true;

        debugLog("tickSelf: 电力传输完成 | 发电=" + lastSupply + " 用电=" + lastDemand
                + " | 发电机=" + generators.size() + " 连接器=" + connectors.size()
                + " 电容=" + capacitors.size() + " 用电器=" + consumers.size()
                + " 路径=" + (countTotalPaths(generatorPaths) + countTotalPaths(capacitorPaths)));

        if (regulator.getWorld() != null && regulator.getChunk().isLoaded()) {
            var data = StorageCacheUtils.getBlock(regulator);
            if (data != null && !data.isPendingRemove()) {
                updateHologram(data, lastSupply, lastDemand);
            }
        }
    }

    private void reduceStoredChargeableEnergy(long chargeableStored) {
        if (chargeableStored <= 0) {
            return;
        }

        long totalCharge = 0;
        Map<Location, Long> genCharges = new HashMap<>();
        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            EnergyNetProvider generator = entry.getValue();
            if (generator.isChargeable()) {
                long charge = generator.getChargeLong(entry.getKey());
                if (charge > 0) {
                    genCharges.put(entry.getKey(), charge);
                    totalCharge += charge;
                }
            }
        }
        if (totalCharge <= 0) {
            return;
        }

        long targetReduction = Math.min(chargeableStored, totalCharge);
        long allocated = 0;

        for (Map.Entry<Location, Long> entry : genCharges.entrySet()) {
            if (allocated >= targetReduction) {
                break;
            }
            long reduction = Math.min((entry.getValue() * targetReduction) / totalCharge, targetReduction - allocated);
            if (reduction <= 0) {
                reduction = Math.min(1, targetReduction - allocated);
            }
            EnergyNetProvider generator = generators.get(entry.getKey());
            if (generator != null && generator.isChargeable()) {
                generator.setCharge(entry.getKey(), Math.max(0, generator.getChargeLong(entry.getKey()) - reduction));
                debugLog("reduceStoredChargeableEnergy: gen@" + formatLocation(entry.getKey()) + " 扣除=" + reduction
                        + "J (chargeableStored=" + chargeableStored + ")");
            }
            allocated += reduction;
        }

        long remaining = targetReduction - allocated;
        if (remaining > 0) {
            for (Map.Entry<Location, Long> entry : genCharges.entrySet()) {
                if (remaining <= 0) {
                    break;
                }
                long alreadyReduced = Math.min((entry.getValue() * targetReduction) / totalCharge, targetReduction);
                long available = entry.getValue() - alreadyReduced;
                if (available <= 0) {
                    continue;
                }
                long extra = Math.min(available, remaining);
                EnergyNetProvider generator = generators.get(entry.getKey());
                if (generator != null && generator.isChargeable()) {
                    generator.setCharge(entry.getKey(), Math.max(0, generator.getChargeLong(entry.getKey()) - extra));
                }
                remaining -= extra;
            }
        }
    }

    private static void reduceSupply(@Nonnull Map<Location, Long> supply, long amount) {
        long remaining = amount;
        for (Iterator<Map.Entry<Location, Long>> it = supply.entrySet().iterator(); it.hasNext() && remaining > 0; ) {
            Map.Entry<Location, Long> entry = it.next();
            long current = entry.getValue();
            long reduction = Math.min(current, remaining);
            long updated = current - reduction;
            if (updated <= 0) {
                it.remove();
            } else {
                entry.setValue(updated);
            }
            remaining -= reduction;
        }
    }

    /**
     * 计算总发电量（发电机当前存储的电量）
     */
    private long calculateTotalSupply() {
        long supply = 0;
        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            supply = NumberUtils.flowSafeAddition(supply, entry.getValue().getChargeLong(entry.getKey()));
        }
        for (long ncSupply : nonChargeableSupply.values()) {
            supply = NumberUtils.flowSafeAddition(supply, ncSupply);
        }
        for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
            supply = NumberUtils.flowSafeAddition(supply, entry.getValue().getChargeLong(entry.getKey()));
        }
        debugLog("calculateTotalSupply: 总供应=" + supply + " (发电机=" + generators.size() + " 电容=" + capacitors.size()
                + ")");
        return supply;
    }

    /**
     * 计算总用电需求（所有用电器充满所需的电量缺口）
     */
    private long calculateTotalDemand() {
        long demand = 0;
        for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
            Location loc = entry.getKey();
            EnergyNetComponent component = entry.getValue();
            long capacity = component.getChargeCapacityLong(loc);
            long charge = component.getChargeLong(loc);
            if (charge < capacity) {
                demand = NumberUtils.flowSafeAddition(demand, capacity - charge);
            }
        }
        return demand;
    }

    /**
     * 计算电网内所有机器的当前总存电量（发电机 + 电容 + 用电器）
     */
    private long calculateTotalCharge() {
        long charge = 0;
        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            charge = NumberUtils.flowSafeAddition(charge, entry.getValue().getChargeLong(entry.getKey()));
        }
        for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
            charge = NumberUtils.flowSafeAddition(charge, entry.getValue().getChargeLong(entry.getKey()));
        }
        for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
            charge = NumberUtils.flowSafeAddition(charge, entry.getValue().getChargeLong(entry.getKey()));
        }
        return charge;
    }

    /**
     * 计算电网内所有机器的最大可存电量总和（发电机 + 电容 + 用电器）
     */
    private long calculateTotalCapacity() {
        long capacity = 0;
        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            capacity = NumberUtils.flowSafeAddition(capacity, entry.getValue().getChargeCapacityLong(entry.getKey()));
        }
        for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
            capacity = NumberUtils.flowSafeAddition(capacity, entry.getValue().getChargeCapacityLong(entry.getKey()));
        }
        for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
            capacity = NumberUtils.flowSafeAddition(capacity, entry.getValue().getChargeCapacityLong(entry.getKey()));
        }
        return capacity;
    }

    /**
     * 记录连接器负载
     * transferAmount已经是分摊到该路径的电量
     */
    private void recordConnectorLoad(EnergyPath path, long transferAmount) {
        // 直接记录每个连接器的负载
        for (Location connectorLoc : path.connectors) {
            long currentLoad = connectorLoad.getOrDefault(connectorLoc, 0L);
            connectorLoad.put(connectorLoc, currentLoad + transferAmount);
        }
    }

    private boolean isConnectorBlocked(@Nonnull Location connLoc) {
        Long limit = connectorLimits.get(connLoc);
        if (limit != null) {
            boolean blocked = limit == 0;
            // debugLog("isConnectorBlocked(@" + formatLocation(connLoc) + ") limitInMap=" + limit + " blocked=" +
            // blocked);
            return blocked;
        }

        Location limiterLoc = connLoc.clone().add(0, 1, 0);
        var limiterData = StorageCacheUtils.getDataContainer(limiterLoc);
        if (limiterData == null || limiterData.isPendingRemove() || !"CURRENT_LIMITER".equals(limiterData.getSfId())) {
            // debugLog("isConnectorBlocked(@" + formatLocation(connLoc)
            //         + ") limiterData="
            //         + (limiterData == null
            //                 ? "null"
            //                 : limiterData.isPendingRemove() ? "pendingRemove" : limiterData.getSfId())
            //         + " => false");
            return false;
        }

        String limitStr = limiterData.getData("current-limit");
        boolean blocked = "0".equals(limitStr);
        // debugLog("isConnectorBlocked(@" + formatLocation(connLoc) + ") limiterData OK, limitStr='" + limitStr
        //         + "' blocked=" + blocked);
        if (!blocked) {
            return false;
        }

        connectorLimits.put(connLoc.clone(), 0L);
        return true;
    }

    private void requestReinitialization() {
        // debugLog("requestReinitialization: initializing=" + initializing
        //         + " pendingInit=" + pendingInit + " destroyed=" + destroyed
        //         + " initialized=" + initialized);
        if (!initializing && !pendingInit && !destroyed) {
            initialized = false;
            pendingInit = true;
            // debugLog("requestReinitialization: 提交异步重新初始化");
            GRID_EXECUTOR.submit(this::initializeNetworkAsync);
            // } else {
            //     debugLog("requestReinitialization: 跳过 (条件不满足)");
        }
    }

    public void setConnectorLimit(Location connLoc, long limit) {
        debugLog("setConnectorLimit: @" + formatLocation(connLoc) + " limit=" + limit + " | init=" + initialized);
        Long old = connectorLimits.get(connLoc);
        if (old == null) {
            Location limiterLoc = connLoc.clone().add(0, 1, 0);
            var limiterData = StorageCacheUtils.getDataContainer(limiterLoc);
            if (limiterData != null && "CURRENT_LIMITER".equals(limiterData.getSfId())) {
                String oldStr = limiterData.getData("current-limit");
                if (oldStr != null) {
                    try {
                        old = Long.parseLong(oldStr);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
        }
        boolean oldBlocked = (old != null && old == 0);
        boolean newBlocked = (limit == 0);
        connectorLimits.put(connLoc.clone(), limit);

        Location limiterLoc = connLoc.clone().add(0, 1, 0);
        var limiterData = StorageCacheUtils.getDataContainer(limiterLoc);
        if (limiterData != null && "CURRENT_LIMITER".equals(limiterData.getSfId())) {
            limiterData.setData("current-limit", String.valueOf(limit));
        }

        if (oldBlocked != newBlocked && !regulator.equals(connLoc)) {
            debugLog("setConnectorLimit: blocked状态变化 (" + oldBlocked + "->" + newBlocked + ") 触发重新初始化");
            requestReinitialization();
        }
    }

    public void removeConnectorLimit(Location connLoc) {
        debugLog("removeConnectorLimit: @" + formatLocation(connLoc) + " init=" + initialized);
        Long old = connectorLimits.remove(connLoc);
        if (old == null) {
            Location limiterLoc = connLoc.clone().add(0, 1, 0);
            var limiterData = StorageCacheUtils.getDataContainer(limiterLoc);
            if (limiterData != null && "CURRENT_LIMITER".equals(limiterData.getSfId())) {
                String oldStr = limiterData.getData("current-limit");
                if (oldStr != null) {
                    try {
                        old = Long.parseLong(oldStr);
                    } catch (NumberFormatException ignored) {
                    }
                }
                limiterData.setData("current-limit", null);
            }
        } else {
            Location limiterLoc = connLoc.clone().add(0, 1, 0);
            var limiterData = StorageCacheUtils.getDataContainer(limiterLoc);
            if (limiterData != null && "CURRENT_LIMITER".equals(limiterData.getSfId())) {
                limiterData.setData("current-limit", null);
            }
        }
        if (old != null && old == 0 && !regulator.equals(connLoc)) {
            debugLog("removeConnectorLimit: 从blocked恢复，触发重新初始化");
            requestReinitialization();
        }
    }

    public long getConnectorLimit(Location connLoc) {
        Long limit = connectorLimits.get(connLoc);
        return limit != null ? limit : -1;
    }

    private long calcNonChargeableRemaining() {
        long total = 0;
        for (long v : nonChargeableSupply.values()) {
            total += v;
        }
        return total;
    }

    /**
     * 处理网络更新（当机器被放置或拆除时调用）
     * 根据新规格说明，触发电网重新初始化
     */
    @Override
    public void markDirty(@Nonnull Location l) {
        debugLog("markDirty @" + formatLocation(l)
                + " | 电网=@" + formatLocation(regulator)
                + " | initialized=" + initialized + " initializing=" + initializing
                + " pendingInit=" + pendingInit + " destroyed=" + destroyed
                + " | l==regulator=" + regulator.equals(l));
        Runnable hologramCleanup = () -> {
            removeHologramAt(l);
            if (regulator.equals(l)) {
                removeAllHolograms();
            }
        };
        if (Bukkit.isPrimaryThread()) {
            hologramCleanup.run();
        } else {
            Slimefun.runSync(hologramCleanup);
        }

        if (regulator.equals(l)) {
            cancelSelfTick();
            destroyed = true;
            abortRequested = true;
            manager.unregisterNetwork(this);
        } else {
            conflictMode = false;
            initialized = false;
            if (initializing || pendingInit) {
                abortRequested = true;
            }
            connectedLocations.remove(l);
            regulatorNodes.remove(l);
            connectorNodes.remove(l);
            terminusNodes.remove(l);

            EnergyNet partner = conflictPartner;
            if (partner != null) {
                conflictPartner = null;
                Slimefun.runSync(partner::wakeUp);
            }

            if (!initializing && !pendingInit) {
                pendingInit = true;
                Slimefun.runSync(() -> {
                    if (!destroyed) {
                        GRID_EXECUTOR.submit(this::initializeNetworkAsync);
                    }
                });
            }
        }
    }

    /**
     * 清除电网内所有机器的悬浮字（用于电网销毁时清理冲突提示等）
     */
    private void removeAllHolograms() {
        removeMultiLineHologram(regulator.getBlock());
        removeHologram(regulator.getBlock());
        for (Location loc : connectedLocations) {
            if (!loc.equals(regulator)) {
                removeHologram(loc.getBlock());
            }
        }
    }

    /**
     * 处理方块放置事件 - 静态入口
     * 搜索新机器周围18格内任何已有电网的节点（连接器或调节器），
     * 验证通过后触发其电网重新初始化
     * @param newMachineLoc 新放置的机器位置
     */
    public static void onMachinePlaced(Location newMachineLoc) {
        // 清除该位置可能残留的旧悬浮字
        removeHologramAt(newMachineLoc);

        debugLog("放置机器 @ " + formatLocation(newMachineLoc));

        // 检查新机器是否已被电网占用
        Optional<EnergyNet> existingNet =
                Slimefun.getNetworkManager().getNetworkFromLocation(newMachineLoc, EnergyNet.class);
        if (existingNet.isPresent()) {
            debugLog("  机器已被电网占用，显示冲突");
            existingNet.get().updateHologram(newMachineLoc.getBlock(), "&c电网冲突：该机器已属于其他电网", () -> false);
            return;
        }

        // 扫描所有已注册的电网，检查新机器是否在任一电网成员附近
        int bestDistance = Integer.MAX_VALUE;
        EnergyNet bestNetwork = null;

        for (Network net : Slimefun.getNetworkManager().getNetworkList()) {
            if (!(net instanceof EnergyNet energyNet)) {
                continue;
            }

            // 检查新机器是否在该电网的调节器RANGE=6范围内（轴向）
            if (isWithinRangeAxial(energyNet.regulator, newMachineLoc, RANGE)) {
                int dist = getDistance(energyNet.regulator, newMachineLoc);
                debugLog("  电网@" + formatLocation(energyNet.regulator) + " 调节器轴向距离=" + dist);
                if (dist < bestDistance) {
                    bestDistance = dist;
                    bestNetwork = energyNet;
                }
                continue;
            }

            // 检查新机器是否在该电网任一连接器的range范围内（轴向）
            for (Location connLoc : energyNet.connectors.keySet()) {
                EnergyNetComponent connComp = energyNet.connectors.get(connLoc);
                if (isWithinRangeAxial(connLoc, newMachineLoc, connComp.getRange())) {
                    int dist = getDistance(connLoc, newMachineLoc);
                    debugLog("  电网@" + formatLocation(energyNet.regulator) + " 连接器=" + formatLocation(connLoc)
                            + " 轴向距离=" + dist);
                    if (dist < bestDistance) {
                        bestDistance = dist;
                        bestNetwork = energyNet;
                    }
                }
            }
        }

        if (bestNetwork != null) {
            // 检查新机器是否是调节器 → 直接冲突，不等异步初始化
            SlimefunItem newItem = StorageCacheUtils.getSlimefunItem(newMachineLoc);
            if (newItem != null && newItem.getId().equals("ENERGY_REGULATOR")) {
                debugLog("  → 新放置的是调节器，但附近已有电网，显示冲突");
                bestNetwork.updateHologram(newMachineLoc.getBlock(), "&c电网冲突：多个能源调节器相连", () -> false);
                bestNetwork.markDirty(newMachineLoc);
                return;
            }
            debugLog("  → 关联到电网 @" + formatLocation(bestNetwork.regulator) + " 触发重新初始化");
            bestNetwork.markDirty(newMachineLoc);
        } else {
            debugLog("  未找到可关联电网");
        }
    }

    /**
     * 获取指定位置所在电网的组件详细信息（静态入口，用于指令查询）
     * @param target 玩家看向的方块位置
     * @return 格式化的信息字符串
     */
    @Nonnull
    public static String getComponentInfo(@Nonnull Location target) {
        EnergyNetComponent comp = getComponent(target);
        StringBuilder sb = new StringBuilder();

        Optional<EnergyNet> opt = Slimefun.getNetworkManager().getNetworkFromLocation(target, EnergyNet.class);
        if (!opt.isPresent()) {
            sb.append("&c该机器不属于任何电网\n");
            if (comp != null) {
                sb.append("&7类型: &f").append(comp.getEnergyComponentType()).append("\n");
            }
            return ChatColors.color(sb.toString());
        }

        EnergyNet net = opt.get();
        sb.append("&6=== &e电网信息 &6===\n");
        sb.append("&7调节器: &f").append(formatLocation(net.regulator)).append("\n");
        sb.append("&7状态: &f")
                .append(net.initializing ? "初始化中" : net.initialized ? "已就绪" : "未初始化")
                .append("\n");

        if (comp != null) {
            EnergyNetComponentType type = comp.getEnergyComponentType();
            switch (type) {
                case GENERATOR: {
                    sb.append("&b▼ 发电机路由 (出发)\n");
                    Set<EnergyPath> pathSet = net.generatorPaths.get(target);
                    if (pathSet == null || pathSet.isEmpty()) {
                        sb.append("  &7无可用路径\n");
                    } else {
                        appendGroupedPaths(sb, pathSet, false, null);
                    }
                    Map<Location, Set<EnergyPath>> capPaths = net.generatorToCapacitorPaths.get(target);
                    if (capPaths != null && !capPaths.isEmpty()) {
                        sb.append("&b▼ 电容充电路由 (出发)\n");
                        List<EnergyPath> capPathList = new ArrayList<>();
                        for (Set<EnergyPath> pathGroup : capPaths.values()) {
                            capPathList.addAll(pathGroup);
                        }
                        appendGroupedPaths(sb, capPathList, false, "&7[电容] &f");
                    }
                    break;
                }
                case CONSUMER: {
                    sb.append("&b▼ 用电器路由 (到达)\n");
                    List<EnergyPath> reachingPaths = new ArrayList<>();
                    for (Set<EnergyPath> paths : net.generatorPaths.values()) {
                        for (EnergyPath p : paths) {
                            if (p.consumer.equals(target)) {
                                reachingPaths.add(p);
                            }
                        }
                    }
                    for (Set<EnergyPath> paths : net.capacitorPaths.values()) {
                        for (EnergyPath p : paths) {
                            if (p.consumer.equals(target)) {
                                reachingPaths.add(p);
                            }
                        }
                    }
                    if (reachingPaths.isEmpty()) {
                        sb.append("  &7无到达路径\n");
                    } else {
                        // 按源类型分组
                        List<EnergyPath> fromGen = new ArrayList<>();
                        List<EnergyPath> fromCap = new ArrayList<>();
                        for (EnergyPath p : reachingPaths) {
                            if (net.generators.containsKey(p.source)) {
                                fromGen.add(p);
                            } else {
                                fromCap.add(p);
                            }
                        }
                        if (!fromGen.isEmpty()) {
                            sb.append("  &7来自发电机:\n");
                            appendGroupedPaths(sb, fromGen, true, "&a← &f");
                        }
                        if (!fromCap.isEmpty()) {
                            sb.append("  &7来自电容:\n");
                            appendGroupedPaths(sb, fromCap, true, "&a← &7[电容] &f");
                        }
                    }
                    break;
                }
                case CONNECTOR: {
                    sb.append("&b▼ 连接器信息\n");
                    sb.append("  &7范围: &f").append(comp.getRange()).append(" 格\n");
                    long load = net.connectorLoad.getOrDefault(target, 0L);
                    sb.append("  &7本刻负载: &f").append(load).append(" J\n");
                    break;
                }
                case CAPACITOR: {
                    sb.append("&b▼ 电容路由 (出发)\n");
                    Set<EnergyPath> pathSet = net.capacitorPaths.get(target);
                    if (pathSet == null || pathSet.isEmpty()) {
                        sb.append("  &7无可用路径\n");
                    } else {
                        appendGroupedPaths(sb, pathSet, false, null);
                    }
                    sb.append("&b▼ 电容桥接\n");
                    boolean hasBridge = false;
                    for (Location capLoc : net.capacitors.keySet()) {
                        if (!capLoc.equals(target) && isAdjacent(target, capLoc)) {
                            sb.append("  &7").append(formatLocation(capLoc)).append(" &f(相邻)\n");
                            hasBridge = true;
                        }
                    }
                    if (!hasBridge) {
                        sb.append("  &7无相邻电容\n");
                    }
                    break;
                }
                default:
                    break;
            }
        }

        // 如果在看调节器，显示完整电网概览
        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(target);
        if (sfItem != null && sfItem.getId().equals("ENERGY_REGULATOR")) {
            sb.append("&b▼ 电网概览\n");
            sb.append("  &7发电机: &f").append(net.generators.size()).append("\n");
            sb.append("  &7连接器: &f").append(net.connectors.size()).append("\n");
            sb.append("  &7电容: &f").append(net.capacitors.size()).append("\n");
            sb.append("  &7用电器: &f").append(net.consumers.size()).append("\n");
            sb.append("  &7路径总数: &f")
                    .append(countTotalPathsStatic(net.generatorPaths) + countTotalPathsStatic(net.capacitorPaths))
                    .append("\n");
            long regLoad = net.connectorLoad.getOrDefault(target, 0L);
            sb.append("  &7调节器本刻负载: &f").append(regLoad).append(" J\n");
        }

        return ChatColors.color(sb.toString());
    }

    private static int countTotalPathsStatic(Map<Location, Set<EnergyPath>> pathMap) {
        int count = 0;
        for (Set<EnergyPath> paths : pathMap.values()) {
            count += paths.size();
        }
        return count;
    }

    private static long parseLongOrZero(@Nonnull ASlimefunDataContainer data, @Nonnull String key) {
        String value = data.getData(key);
        if (value == null || value.isEmpty()) {
            return 0L;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            data.removeData(key);
            return 0L;
        }
    }

    /**
     * 获取电网详细信息（用于管理员查询指令）
     * @return 包含电网信息的字符串
     */
    public String getGridInfo() {
        StringBuilder info = new StringBuilder();
        info.append("=== 电网信息 ===\n");
        info.append("调节器位置: ").append(formatLocation(regulator)).append("\n");
        info.append("状态: ")
                .append(initializing ? "初始化中" : initialized ? "已就绪" : "未初始化")
                .append("\n");
        info.append("成员统计:\n");
        info.append("  发电机: ").append(generators.size()).append(" 个\n");
        info.append("  电容器: ").append(capacitors.size()).append(" 个\n");
        info.append("  用电器: ").append(consumers.size()).append(" 个\n");
        info.append("  连接器: ").append(connectors.size()).append(" 个\n");
        info.append("预计算路径:\n");
        info.append("  发电机路径: ").append(countTotalPaths(generatorPaths)).append(" 条\n");
        info.append("  电容器路径: ").append(countTotalPaths(capacitorPaths)).append(" 条\n");

        // 可选：列出具体路径详情（小规模电网时可用）
        if (countTotalPaths(generatorPaths) + countTotalPaths(capacitorPaths) <= 20) {
            info.append("\n路径详情:\n");

            info.append("发电机路径:\n");
            for (Map.Entry<Location, Set<EnergyPath>> entry : generatorPaths.entrySet()) {
                for (EnergyPath path : entry.getValue()) {
                    info.append("  ")
                            .append(formatLocation(path.source))
                            .append(" -> ")
                            .append(formatLocation(path.consumer))
                            .append(" (长度: ")
                            .append(path.length)
                            .append(")\n");
                }
            }

            info.append("电容器路径:\n");
            for (Map.Entry<Location, Set<EnergyPath>> entry : capacitorPaths.entrySet()) {
                for (EnergyPath path : entry.getValue()) {
                    info.append("  ")
                            .append(formatLocation(path.source))
                            .append(" -> ")
                            .append(formatLocation(path.consumer))
                            .append(" (长度: ")
                            .append(path.length)
                            .append(")\n");
                }
            }
        }

        return info.toString();
    }

    /**
     * 格式化位置信息为字符串
     */
    public static String formatLocation(Location loc) {
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + ")";
    }

    private static void appendPathHops(
            StringBuilder sb, @Nullable Location source, List<Location> connectors, @Nullable Location consumer) {
        if (source != null) {
            sb.append("&f").append(formatLocation(source));
        } else {
            sb.append("&f本机");
        }
        for (Location conn : connectors) {
            sb.append(" &7→ &e(")
                    .append(conn.getBlockX())
                    .append(", ")
                    .append(conn.getBlockY())
                    .append(", ")
                    .append(conn.getBlockZ())
                    .append(")");
        }
        sb.append(" &7→ &f");
        if (consumer != null) {
            sb.append(formatLocation(consumer));
        } else {
            sb.append("本机");
        }
    }

    /**
     * 按(目标, 跳数)分组显示路径，展示路线组数量和各路线详情
     */
    private static void appendGroupedPaths(
            StringBuilder sb, Collection<EnergyPath> paths, boolean isSourceShown, @Nullable String prefix) {
        // 按(consumer坐标, length)分组
        Map<String, List<EnergyPath>> groups = new LinkedHashMap<>();
        for (EnergyPath p : paths) {
            String key = p.consumer.getBlockX() + "," + p.consumer.getBlockY() + "," + p.consumer.getBlockZ() + ","
                    + p.length;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
        }

        // 按跳数排序组
        List<Map.Entry<String, List<EnergyPath>>> sortedGroups = new ArrayList<>(groups.entrySet());
        sortedGroups.sort((a, b) -> {
            int lenA = a.getValue().get(0).length;
            int lenB = b.getValue().get(0).length;
            return Integer.compare(lenA, lenB);
        });

        for (Map.Entry<String, List<EnergyPath>> groupEntry : sortedGroups) {
            List<EnergyPath> group = groupEntry.getValue();
            EnergyPath first = group.get(0);
            int count = group.size();

            sb.append("  &a").append(prefix != null ? prefix : "");
            if (isSourceShown) {
                appendPathHops(sb, first.source, first.connectors, null);
            } else {
                appendPathHops(sb, null, first.connectors, first.consumer);
            }
            sb.append(" &7(跳数: ").append(first.length).append(")");
            if (count > 1) {
                sb.append(" &e×").append(count).append("条路线");
            }
            sb.append("\n");

            // 如果有多条路线，逐条显示连接器链
            if (count > 1) {
                int idx = 1;
                for (EnergyPath p : group) {
                    sb.append("    &7路线 ").append(idx++).append(": &e");
                    boolean firstHop = true;
                    for (Location conn : p.connectors) {
                        if (!firstHop) sb.append(" &7→ &e");
                        sb.append("(")
                                .append(conn.getBlockX())
                                .append(", ")
                                .append(conn.getBlockY())
                                .append(", ")
                                .append(conn.getBlockZ())
                                .append(")");
                        firstHop = false;
                    }
                    sb.append("\n");
                }
            }
        }
    }

    /**
     * 计算总路径数量
     */
    private int countTotalPaths(Map<Location, Set<EnergyPath>> pathMap) {
        int count = 0;
        for (Set<EnergyPath> paths : pathMap.values()) {
            count += paths.size();
        }
        return count;
    }

    /**
     * This attempts to get an {@link EnergyNet} from a given {@link Location}.
     * If no suitable {@link EnergyNet} could be found, a new one will be created.
     *
     * @param l
     *            The target {@link Location}
     *
     * @return The {@link EnergyNet} at that {@link Location}, or a new one
     */
    @Nonnull
    public static EnergyNet getNetworkFromLocationOrCreate(@Nonnull Location l) {
        Optional<EnergyNet> energyNetwork = Slimefun.getNetworkManager().getNetworkFromLocation(l, EnergyNet.class);

        if (energyNetwork.isPresent()) {
            return energyNetwork.get();
        } else {
            EnergyNet network = new EnergyNet(l);
            Slimefun.getNetworkManager().registerNetwork(network);
            return network;
        }
    }

    public boolean isInConflict() {
        return conflictMode;
    }

    public void wakeUp() {
        debugLog("wakeUp @" + formatLocation(regulator)
                + " | initialized=" + initialized + " initializing=" + initializing
                + " pendingInit=" + pendingInit + " conflictMode=" + conflictMode);
        conflictMode = false;
        initialized = false;
        if (initializing || pendingInit) {
            abortRequested = true;
        }
        conflictPartner = null;
        Slimefun.runSync(this::clearConflictHolograms);
        if (!initializing && !pendingInit) {
            pendingInit = true;
            GRID_EXECUTOR.submit(this::initializeNetworkAsync);
        }
    }

    private void clearConflictHolograms() {
        if (!Bukkit.isPrimaryThread()) {
            Slimefun.runSync(this::clearConflictHolograms);
            return;
        }
        removeHologram(regulator.getBlock());
        for (Location loc : connectorNodes) {
            removeHologram(loc.getBlock());
        }
    }

    public static void wakeUpConflictNets() {
        for (Network net : Slimefun.getNetworkManager().getNetworkList()) {
            if (net instanceof EnergyNet energyNet && energyNet.isInConflict()) {
                energyNet.wakeUp();
            }
        }
    }

    public static void abortAllInitializing() {
        for (Network net : Slimefun.getNetworkManager().getNetworkList()) {
            if (net instanceof EnergyNet energyNet && energyNet.initializing) {
                energyNet.abortRequested = true;
            }
        }
    }

    private static final class GridTickSnapshot {
        final Map<Location, Long> generatorCharges;
        final Map<Location, Long> generatorCapacities;
        final Map<Location, Long> nonChargeableSupply;
        final Map<Location, Long> capacitorCharges;
        final Map<Location, Long> capacitorCapacities;
        final Map<Location, Long> consumerCharges;
        final Map<Location, Long> consumerCapacities;
        final Map<Location, Long> connectorLimits;
        final Map<Location, Set<EnergyPath>> generatorPaths;
        final Map<Location, Set<EnergyPath>> capacitorPaths;
        final long netNewEnergy;

        GridTickSnapshot(
                @Nonnull Map<Location, Long> generatorCharges,
                @Nonnull Map<Location, Long> generatorCapacities,
                @Nonnull Map<Location, Long> nonChargeableSupply,
                @Nonnull Map<Location, Long> capacitorCharges,
                @Nonnull Map<Location, Long> capacitorCapacities,
                @Nonnull Map<Location, Long> consumerCharges,
                @Nonnull Map<Location, Long> consumerCapacities,
                @Nonnull Map<Location, Long> connectorLimits,
                @Nonnull Map<Location, Set<EnergyPath>> generatorPaths,
                @Nonnull Map<Location, Set<EnergyPath>> capacitorPaths,
                long netNewEnergy) {
            this.generatorCharges = generatorCharges;
            this.generatorCapacities = generatorCapacities;
            this.nonChargeableSupply = nonChargeableSupply;
            this.capacitorCharges = capacitorCharges;
            this.capacitorCapacities = capacitorCapacities;
            this.consumerCharges = consumerCharges;
            this.consumerCapacities = consumerCapacities;
            this.connectorLimits = connectorLimits;
            this.generatorPaths = generatorPaths;
            this.capacitorPaths = capacitorPaths;
            this.netNewEnergy = netNewEnergy;
        }
    }

    private static final class TransferResult {
        final Map<Location, Long> generatorChargeDeltas;
        final Map<Location, Long> capacitorChargeDeltas;
        final Map<Location, Long> consumerChargeDeltas;
        final Map<Location, Long> connectorLoads;
        final Map<Location, Long> remainingNonChargeableSupply;
        final long excessEnergy;
        final long nonChargeableExcessEnergy;

        TransferResult(
                @Nonnull Map<Location, Long> generatorChargeDeltas,
                @Nonnull Map<Location, Long> capacitorChargeDeltas,
                @Nonnull Map<Location, Long> consumerChargeDeltas,
                @Nonnull Map<Location, Long> connectorLoads,
                @Nonnull Map<Location, Long> remainingNonChargeableSupply,
                long excessEnergy,
                long nonChargeableExcessEnergy) {
            this.generatorChargeDeltas = generatorChargeDeltas;
            this.capacitorChargeDeltas = capacitorChargeDeltas;
            this.consumerChargeDeltas = consumerChargeDeltas;
            this.connectorLoads = connectorLoads;
            this.remainingNonChargeableSupply = remainingNonChargeableSupply;
            this.excessEnergy = excessEnergy;
            this.nonChargeableExcessEnergy = nonChargeableExcessEnergy;
        }
    }

    private static class AxisTarget {
        final Location location;
        final boolean wasDamaged;

        AxisTarget(Location location, boolean wasDamaged) {
            this.location = location;
            this.wasDamaged = wasDamaged;
        }
    }

    /**
     * 表示从发电机/电容到消费者的能量传输路径
     */
    public static class EnergyPath {
        private final Location source;
        private final Location consumer;
        private final List<Location> connectors;
        private final int length;

        public EnergyPath(Location source, Location consumer, List<Location> connectors) {
            this.source = source;
            this.consumer = consumer;
            this.connectors = Collections.unmodifiableList(new ArrayList<>(connectors));
            this.length = connectors.size();
        }

        public Location getSource() {
            return source;
        }

        public Location getConsumer() {
            return consumer;
        }

        public List<Location> getConnectors() {
            return connectors;
        }

        public int getLength() {
            return length;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EnergyPath that = (EnergyPath) o;
            return source.equals(that.source) && consumer.equals(that.consumer) && connectors.equals(that.connectors);
        }

        @Override
        public int hashCode() {
            return Objects.hash(source, consumer, connectors);
        }
    }
}
