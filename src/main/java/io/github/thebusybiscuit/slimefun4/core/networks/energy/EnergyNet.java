package io.github.thebusybiscuit.slimefun4.core.networks.energy;

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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

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

    /**
     * 静态辅助方法：清除指定位置的悬浮字（不需要电网实例）
     */
    public static void removeHologramAt(Location loc) {
        Location hologramLoc = loc.clone().add(0.5, 0.75, 0.5);
        Slimefun.getHologramsService().removeHologram(hologramLoc);
    }

    private final Map<Location, EnergyNetProvider> generators = new HashMap<>();
    private final Map<Location, EnergyNetComponent> capacitors = new HashMap<>();
    private final Map<Location, EnergyNetComponent> consumers = new HashMap<>();

    // 新规格说明添加的字段
    private final Map<Location, EnergyNetComponent> connectors = new HashMap<>();
    private final Map<Location, Long> connectorLoad = new HashMap<>();
    private final Map<Location, Set<EnergyPath>> generatorPaths = new HashMap<>();
    private final Map<Location, Set<EnergyPath>> capacitorPaths = new HashMap<>();
    private final Map<Location, Map<Location, EnergyPath>> generatorToCapacitorPaths = new HashMap<>();
    private final Map<Location, Long> nonChargeableSupply = new HashMap<>();
    private volatile boolean initializing = false;
    private volatile boolean initialized = false;
    private volatile boolean pendingInit = false;
    private volatile boolean abortRequested = false;
    private volatile boolean destroyed = false;
    private volatile boolean conflictMode = false;

    private volatile int initTotalWork = 0;
    private volatile int initWorkDone = 0;
    private volatile int pathSourcesDone = 0;
    private volatile long netNewEnergy = 0;
    private final Map<Location, Long> perGeneratorNewCharge = new HashMap<>();

    private static final AtomicInteger bfsDbQueryCount = new AtomicInteger(0);
    private static final int BFS_DB_QUERY_THROTTLE = 10;
    private static final int MAX_BFS_NODES = 100_000;
    private static final Set<Location> bfsDbQueried = new HashSet<>();

    private static final ExecutorService GRID_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Slimefun-Grid-Init");
        return t;
    });

    static {
        Runtime.getRuntime()
                .addShutdownHook(new Thread(
                        () -> {
                            GRID_EXECUTOR.shutdown();
                            try {
                                if (!GRID_EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                                    GRID_EXECUTOR.shutdownNow();
                                }
                            } catch (InterruptedException e) {
                                GRID_EXECUTOR.shutdownNow();
                                Thread.currentThread().interrupt();
                            }
                        },
                        "Slimefun-Grid-Shutdown"));
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
    public @Nonnull Map<Location, Map<Location, EnergyPath>> getGeneratorToCapacitorPaths() {
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
                updateHologram(b, "&c电网冲突：多个能源调节器相连", blockData::isPendingRemove);
                if (initializing) {
                    initializing = false;
                    abortRequested = true;
                }
                return;
            }

            if (conflictMode) {
                updateHologram(b, "&c电网冲突：电网交叉", () -> false);
                return;
            }

            if (!initialized) {
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
                    updateHologram(b, "&7电网已就绪，等待接入设备", blockData::isPendingRemove);
                } else {
                    updateHologram(b, "&4找不到能源网络", blockData::isPendingRemove);
                }
            } else {
                performEnergyTransfer();
                if (destroyed) {
                    return;
                }
                long supply = calculateTotalSupply();
                long demand = calculateTotalDemand();
                debugLog("tick: 电力传输完成 | 发电=" + supply + " 用电=" + demand
                        + " | 发电机=" + generators.size() + " 连接器=" + connectors.size()
                        + " 电容=" + capacitors.size() + " 用电器=" + consumers.size()
                        + " 路径=" + (countTotalPaths(generatorPaths) + countTotalPaths(capacitorPaths)));
                updateHologram(blockData, supply, demand);
            }
        } finally {
            Slimefun.getProfiler()
                    .closeEntry(b.getLocation(), SlimefunItems.ENERGY_REGULATOR.getItem(), timestamp.get());
        }
    }

    private long storeRemainingEnergy(long remainingEnergy) {
        debugLog("storeRemainingEnergy: 开始存储剩余能量 " + remainingEnergy + "J, 电容数=" + capacitors.size());
        long totalStored = 0;

        // 构建电容充电优先级：从generatorToCapacitorPaths获取每电容的最短路径长度
        Map<Location, Integer> capPriority = new HashMap<>();
        for (Map<Location, EnergyPath> capPaths : generatorToCapacitorPaths.values()) {
            for (Map.Entry<Location, EnergyPath> entry : capPaths.entrySet()) {
                capPriority.merge(entry.getKey(), entry.getValue().length, Math::min);
            }
        }

        // 按路径长度排序（短路径优先），没有路径的电容排最后
        List<Location> sortedCaps = new ArrayList<>(capacitors.keySet());
        sortedCaps.sort((a, b) -> {
            int pa = capPriority.getOrDefault(a, Integer.MAX_VALUE);
            int pb = capPriority.getOrDefault(b, Integer.MAX_VALUE);
            return Integer.compare(pa, pb);
        });

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

            if (remainingEnergy > 0) {
                long capacity = component.getCapacityLong();
                long canStore = capacity - oldCharge;
                debugLog("storeRemainingEnergy电容: " + formatLocation(loc) + " old=" + oldCharge + " cap=" + capacity
                        + " canStore=" + canStore + " remaining=" + remainingEnergy);

                if (canStore <= 0) {
                    continue;
                }

                if (remainingEnergy > canStore) {
                    component.setCharge(loc, (long) capacity);
                    remainingEnergy -= canStore;
                    totalStored += canStore;
                } else {
                    component.setCharge(loc, oldCharge + remainingEnergy);
                    totalStored += remainingEnergy;
                    remainingEnergy = 0;
                }
            }
            long newCharge = component.getChargeLong(loc);
            debugLog("storeRemainingEnergy电容: " + formatLocation(loc) + " setCharge后=" + newCharge + " remaining="
                    + remainingEnergy);

            long chargeDiff = Math.abs(newCharge - oldCharge);
            long capacity = component.getCapacityLong();

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

                // 记录连接器负载：找到所有发电机到此电容的路径，均摊chargeDiff
                int pathCount = 0;
                for (Map<Location, EnergyPath> paths : generatorToCapacitorPaths.values()) {
                    if (paths.containsKey(loc)) pathCount++;
                }
                if (pathCount > 0) {
                    long loadPerPath = chargeDiff / pathCount;
                    long loadRemainder = chargeDiff % pathCount;
                    int idx = 0;
                    for (Map<Location, EnergyPath> paths : generatorToCapacitorPaths.values()) {
                        EnergyPath path = paths.get(loc);
                        if (path != null) {
                            long pathLoad = loadPerPath + (idx < loadRemainder ? 1 : 0);
                            if (pathLoad > 0) {
                                recordConnectorLoad(path, pathLoad);
                            }
                            idx++;
                        }
                    }
                }
            }
        }
        debugLog("storeRemainingEnergy: 实际存储 " + totalStored + "J");
        return totalStored;
    }

    private long tickAllGenerators(@Nonnull LongConsumer timings) {
        Set<Location> explodedBlocks = new HashSet<>();
        long supply = 0;
        nonChargeableSupply.clear();
        netNewEnergy = 0;
        perGeneratorNewCharge.clear();

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
                    perGeneratorNewCharge.put(loc, newCharge);
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
        if (demand > supply) {
            String netLoss = NumberUtils.getCompactDouble(demand - supply);
            updateHologram(
                    data.getLocation().getBlock(), "&4&l- &c" + netLoss + " &7J &e\u26A1", data::isPendingRemove);
        } else {
            String netGain = NumberUtils.getCompactDouble(supply - demand);
            updateHologram(
                    data.getLocation().getBlock(), "&2&l+ &a" + netGain + " &7J &e\u26A1", data::isPendingRemove);
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

    @Nullable private static SlimefunItem querySlimefunItemFromDb(@Nonnull Location l) {
        if (!bfsDbQueried.add(l)) {
            return null;
        }
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
        pendingInit = false;
        debugLog("initializeNetworkAsync: 开始异步初始化");
        bfsDbQueryCount.set(0);
        bfsDbQueried.clear();
        synchronized (regulator.toString().intern()) {
            if (destroyed) {
                debugLog("initializeNetworkAsync: 电网已销毁，退出");
                return;
            }

            initializing = true;
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
                debugLog("初始化完成 ✓ 调节器=" + formatLocation(regulator)
                        + " 发电机=" + generators.size() + " 连接器=" + connectors.size()
                        + " 电容=" + capacitors.size() + " 用电器=" + consumers.size()
                        + " 路径=" + (countTotalPaths(generatorPaths) + countTotalPaths(capacitorPaths)));
                if (!destroyed) {
                    Slimefun.runSync(() -> {
                        long supply = calculateTotalSupply();
                        long demand = calculateTotalDemand();
                        if (demand > supply) {
                            String netLoss = NumberUtils.getCompactDouble(demand - supply);
                            updateHologram(regulator.getBlock(), "&4&l- &c" + netLoss + " &7J &e\u26A1", () -> false);
                        } else {
                            String netGain = NumberUtils.getCompactDouble(supply - demand);
                            updateHologram(regulator.getBlock(), "&2&l+ &a" + netGain + " &7J &e\u26A1", () -> false);
                        }
                    });
                }
            } finally {
                initializing = false;
                abortRequested = false;
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
        generatorPaths.clear();
        capacitorPaths.clear();
        generatorToCapacitorPaths.clear();
        nonChargeableSupply.clear();

        // 清空基类的节点集合
        regulatorNodes.clear();
        connectorNodes.clear();
        terminusNodes.clear();
        connectedLocations.clear();

        // 重新添加调节器
        regulatorNodes.add(regulator);
        connectedLocations.add(regulator);
        connectorLoad.put(regulator, 0L);
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
     * 验证从type1(loc1)到type2(loc2)的电力传输路径是否有效（单向验证）
     * 只有电容↔电容使用相邻规则，其余都基于连接器的连接范围
     * @param loc1 发送方位置
     * @param loc2 接收方位置
     * @param type1 发送方类型
     * @param type2 接收方类型
     * @return true如果连接有效
     */
    private boolean validateConnection(
            Location loc1, Location loc2, EnergyNetComponentType type1, EnergyNetComponentType type2) {
        EnergyNetComponent comp1 = getComponent(loc1);
        EnergyNetComponent comp2 = getComponent(loc2);
        if (comp1 == null || comp2 == null) {
            return false;
        }

        // 电容到电容的特殊情况：只需相邻，不进行范围验证
        if (type1 == EnergyNetComponentType.CAPACITOR && type2 == EnergyNetComponentType.CAPACITOR) {
            return isAdjacent(loc1, loc2);
        }

        // 电力必须通过连接器传输，至少一方是连接器
        if (type1 != EnergyNetComponentType.CONNECTOR && type2 != EnergyNetComponentType.CONNECTOR) {
            return false;
        }

        // 正向验证：type1(发送方)能否发送到type2(接收方)
        boolean forwardValid = false;
        if (type1 == EnergyNetComponentType.CONNECTOR) {
            int effectiveRange = comp1.getRange();
            if (type2 == EnergyNetComponentType.CONNECTOR && comp2 instanceof LongRangeConnector) {
                effectiveRange = comp2.getRange();
            }
            // 连接器用自身范围沿轴向覆盖目标（与processConnector轴向搜索一致）
            forwardValid = isWithinRangeAxial(loc1, loc2, effectiveRange);
        } else if (type1 == EnergyNetComponentType.GENERATOR) {
            // 发电机不需要正向验证，只需连接器能覆盖它即可（在反向中检查）
            forwardValid = true;
        } else if (type1 == EnergyNetComponentType.CAPACITOR) {
            // 电容到连接器：相邻（6方向），连接器可接收即可
            forwardValid = isAdjacent(loc1, loc2);
        }
        if (!forwardValid) {
            return false;
        }

        // 反向验证：type2(连接器)能否接收到type1的信号（连接器需沿轴向覆盖）
        // 连接器之间不需要反向验证（允许单向连接，高范围→低范围）
        if (type2 == EnergyNetComponentType.CONNECTOR && type1 != EnergyNetComponentType.CONNECTOR) {
            boolean reverseValid = isWithinRangeAxial(loc2, loc1, comp2.getRange());
            if (DEBUG_PATHS && type1 == EnergyNetComponentType.GENERATOR) {
                debugPathLog("validateConnection: 反向验证 连接器=" + formatLocation(loc2)
                        + " range=" + comp2.getRange()
                        + " 到发电机=" + formatLocation(loc1)
                        + " 轴向距离=" + getAxialDistance(loc2, loc1)
                        + " 结果=" + reverseValid);
            }
            return reverseValid;
        }

        return true;
    }

    /**
     * 预计算能量传输路径
     */
    private void precomputePaths() {
        generatorPaths.clear();
        capacitorPaths.clear();

        if (consumers.isEmpty()) {
            if (DEBUG_PATHS) {
                debugPathLog("precomputePaths: 无用电器，跳过路径计算");
            }
            return;
        }

        int totalSources = generators.size() + capacitors.size();
        if (totalSources == 0) {
            if (DEBUG_PATHS) {
                debugPathLog("precomputePaths: 无发电机和电容，跳过路径计算");
            }
            return;
        }

        if (DEBUG_PATHS) {
            debugPathLog("precomputePaths: 开始计算路径，sources=" + totalSources + " consumers=" + consumers.size());
        }
        pathSourcesDone = 0;

        // 计算所有发电机到消费者的路径
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

        // 预计算发电机到电容的路径（用于显示充电跳数）
        if (!capacitors.isEmpty() && !generators.isEmpty()) {
            if (DEBUG_PATHS) {
                debugPathLog("precomputePaths: 开始计算发电机到电容路径");
            }
            for (Location genLoc : generators.keySet()) {
                if (abortRequested || destroyed) return;
                Map<Location, EnergyPath> capPaths = findShortestPathsToCapacitors(genLoc);
                if (!capPaths.isEmpty()) {
                    generatorToCapacitorPaths.put(genLoc, capPaths);
                }
            }
        }
    }

    /**
     * 从源位置（发电机或电容）查找所有到消费者的最短路径
     * 使用父节点回溯的广度优先搜索（避免路径克隆消耗）
     */
    private Set<EnergyPath> findShortestPathsFromSource(Location source, int totalSources) {
        Set<EnergyPath> shortestPaths = new HashSet<>();
        Map<Location, Integer> shortestDistances = new HashMap<>();
        Map<Location, Integer> connectorShortest = new HashMap<>();

        // BFS节点列表：用ArrayList + head指针替代Queue
        List<BFSNode> nodes = new ArrayList<>();
        nodes.add(new BFSNode(source, -1, 0));
        Set<Location> bfsVisited = new HashSet<>();
        bfsVisited.add(source);
        int head = 0;
        int stepsSinceProgress = 0;

        while (head < nodes.size()) {
            // 每10步检查打断信号
            if (head % 10 == 0 && (abortRequested || destroyed)) {
                debugLog("findShortestPathsFromSource: 检测到打断，提前退出BFS");
                return shortestPaths;
            }
            if (nodes.size() > MAX_BFS_NODES) {
                debugLog("findShortestPathsFromSource: BFS节点超限(" + nodes.size() + ")，强制退出");
                return shortestPaths;
            }

            BFSNode current = nodes.get(head++);
            Location currentLoc = current.location;
            int currentLength = current.length;

            // 如果当前是消费者，记录路径
            if (DEBUG_PATHS) {
                debugPathLog("BFS步骤: 检查消费者 current=" + formatLocation(currentLoc)
                        + " consumers含=" + consumers.containsKey(currentLoc)
                        + " 是源=" + currentLoc.equals(source));
            }
            if (consumers.containsKey(currentLoc) && !currentLoc.equals(source)) {
                if (DEBUG_PATHS) {
                    debugPathLog("BFS步骤: 找到消费者 " + formatLocation(currentLoc) + " length=" + currentLength);
                }
                int existingDistance = shortestDistances.getOrDefault(currentLoc, Integer.MAX_VALUE);

                if (currentLength < existingDistance) {
                    shortestPaths.removeIf(path -> path.consumer.equals(currentLoc));
                    shortestDistances.put(currentLoc, currentLength);
                    shortestPaths.add(new EnergyPath(source, currentLoc, extractConnectorsFromPath(nodes, head - 1)));
                } else if (currentLength == existingDistance) {
                    shortestPaths.add(new EnergyPath(source, currentLoc, extractConnectorsFromPath(nodes, head - 1)));
                }
                continue;
            }

            // 连接器剪枝：如果已有更短路径到过这个连接器，跳过
            EnergyNetComponent component = getComponent(currentLoc);
            if (component != null && component.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
                int bestDist = connectorShortest.getOrDefault(currentLoc, Integer.MAX_VALUE);
                if (currentLength > bestDist) continue;
                if (currentLength < bestDist) {
                    connectorShortest.put(currentLoc, currentLength);
                }
            }

            // 获取邻居
            if (component == null) {
                // 调节器特殊处理
                if (currentLoc.equals(regulator)) {
                    if (currentLoc.equals(source)) {
                        debugPathLogLimited("BFS: 调节器是源，跳过(不会发生)");
                    } else {
                        debugPathLogLimited("BFS: 处理调节器 length=" + currentLength);
                        int regRange = getRange();
                        addRegulatorNeighbors(nodes, bfsVisited, head - 1, currentLength, regRange, source);
                    }
                }
                continue;
            }

            Set<Location> neighbors = getNeighbors(currentLoc, component.getEnergyComponentType());
            if (DEBUG_PATHS) {
                debugPathLog("BFS步骤: 当前=" + formatLocation(currentLoc)
                        + " 类型=" + component.getEnergyComponentType()
                        + " length=" + currentLength
                        + " 邻居数=" + neighbors.size());
            }

            for (Location neighbor : neighbors) {
                boolean isConsumer = consumers.containsKey(neighbor);
                if (isConsumer
                        && debugPathLogLimited("BFS邻居: 消费者=" + formatLocation(neighbor)
                                + " 当前=" + formatLocation(currentLoc)
                                + " bfsVisited.contains=" + bfsVisited.contains(neighbor))) {
                    debugPathLogLimited("BFS邻居: 消费者预检完成");
                }

                if (!bfsVisited.add(neighbor)) {
                    if (isConsumer) {
                        debugPathLogLimited("BFS邻居: 消费者已被访问，但允许重复加入(路径汇聚)! loc=" + formatLocation(neighbor));
                    } else {
                        continue;
                    }
                }

                if (isConsumer) {
                    debugPathLogLimited("BFS邻居: 消费者通过bfsVisited.add，即将加入nodes! loc=" + formatLocation(neighbor));
                }

                int newLength = currentLength;
                EnergyNetComponent neighborComponent = getComponent(neighbor);
                if (neighborComponent != null
                        && neighborComponent.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
                    newLength++;
                }

                nodes.add(new BFSNode(neighbor, head - 1, newLength));

                if (isConsumer) {
                    debugPathLogLimited("BFS邻居: 消费者已加入nodes! loc=" + formatLocation(neighbor) + " nodes.size="
                            + nodes.size() + " head=" + head);
                }
            }

            // 跨源进度追踪（基于动态工作量权重）
            if (++stepsSinceProgress >= 50) {
                stepsSinceProgress = 0;
                if (totalSources > 0) {
                    double collectFraction = (double) initWorkDone / initTotalWork;
                    double pathFraction = (double) pathSourcesDone / totalSources
                            + (double) head / (Math.max(1, nodes.size()) * totalSources);
                    int pct = Math.max(
                            0, Math.min(99, (int) ((collectFraction + (1.0 - collectFraction) * pathFraction) * 100)));
                    Slimefun.runSync(() -> {
                        if (!destroyed) {
                            updateHologram(regulator.getBlock(), "&e初始化电网中 " + pct + "%", () -> false);
                        }
                    });
                }
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

    private Map<Location, EnergyPath> findShortestPathsToCapacitors(Location source) {
        Map<Location, EnergyPath> result = new HashMap<>();
        Map<Location, Integer> connectorShortest = new HashMap<>();

        List<BFSNode> nodes = new ArrayList<>();
        nodes.add(new BFSNode(source, -1, 0));
        Set<Location> bfsVisited = new HashSet<>();
        bfsVisited.add(source);
        int head = 0;

        while (head < nodes.size()) {
            if (head % 10 == 0 && (abortRequested || destroyed)) {
                return result;
            }
            if (nodes.size() > MAX_BFS_NODES) {
                return result;
            }

            BFSNode current = nodes.get(head++);
            Location currentLoc = current.location;
            int currentLength = current.length;

            if (capacitors.containsKey(currentLoc) && !currentLoc.equals(source)) {
                if (!result.containsKey(currentLoc)) {
                    result.put(
                            currentLoc,
                            new EnergyPath(source, currentLoc, extractConnectorsFromPath(nodes, head - 1, currentLoc)));
                }
            }

            EnergyNetComponent component = getComponent(currentLoc);
            if (component != null && component.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
                int bestDist = connectorShortest.getOrDefault(currentLoc, Integer.MAX_VALUE);
                if (currentLength > bestDist) continue;
                if (currentLength < bestDist) {
                    connectorShortest.put(currentLoc, currentLength);
                }
            }

            if (component == null) {
                if (currentLoc.equals(regulator) && !currentLoc.equals(source)) {
                    addRegulatorNeighbors(nodes, bfsVisited, head - 1, currentLength, getRange(), source);
                }
                continue;
            }

            Set<Location> neighbors = getNeighbors(currentLoc, component.getEnergyComponentType());
            for (Location neighbor : neighbors) {
                if (!bfsVisited.add(neighbor)) {
                    continue;
                }
                int newLength = currentLength;
                EnergyNetComponent neighborComponent = getComponent(neighbor);
                if (neighborComponent != null
                        && neighborComponent.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
                    newLength++;
                } else if (neighbor.equals(regulator)) {
                    newLength++;
                } else if (component.getEnergyComponentType() == EnergyNetComponentType.CAPACITOR
                        && neighborComponent != null
                        && neighborComponent.getEnergyComponentType() == EnergyNetComponentType.CAPACITOR) {
                    newLength++;
                }
                nodes.add(new BFSNode(neighbor, head - 1, newLength));
            }
        }
        return result;
    }

    /**
     * 从调节器（不实现EnergyNetComponent）扩展搜索邻居节点
     */
    private void addRegulatorNeighbors(
            List<BFSNode> nodes,
            Set<Location> bfsVisited,
            int parentIndex,
            int currentLength,
            int regRange,
            Location source) {
        // 调节器本身作为特殊连接器，通过它需要+1跳
        int regLength = currentLength + 1;
        for (Location genLoc : generators.keySet()) {
            if (!genLoc.equals(source) && isWithinRangeAxial(regulator, genLoc, regRange) && bfsVisited.add(genLoc)) {
                nodes.add(new BFSNode(genLoc, parentIndex, regLength));
            }
        }
        for (Location conLoc : consumers.keySet()) {
            boolean inRange = isWithinRangeAxial(regulator, conLoc, regRange);
            boolean added = inRange && bfsVisited.add(conLoc);
            if (debugPathLogLimited("addRegulatorNeighbors: 消费者=" + formatLocation(conLoc)
                    + " inRange=" + inRange + " regRange=" + regRange
                    + " 距离=" + getDistance(regulator, conLoc)
                    + " added=" + added)) {
                // log consumed
            }
            if (added) {
                nodes.add(new BFSNode(conLoc, parentIndex, regLength));
                debugPathLogLimited("addRegulatorNeighbors: 消费者已加入nodes! loc=" + formatLocation(conLoc));
            }
        }
        for (Location capLoc : capacitors.keySet()) {
            if (isWithinRangeAxial(regulator, capLoc, regRange) && bfsVisited.add(capLoc)) {
                nodes.add(new BFSNode(capLoc, parentIndex, regLength));
            }
        }
        for (Location connLoc : connectors.keySet()) {
            if (isWithinRangeAxial(regulator, connLoc, regRange) && bfsVisited.add(connLoc)) {
                nodes.add(new BFSNode(connLoc, parentIndex, regLength + 1));
            }
        }
    }

    /**
     * 获取指定位置的邻居位置（根据组件类型）
     * 包含反向验证
     */
    private Set<Location> getNeighbors(Location location, EnergyNetComponentType type) {
        Set<Location> neighbors = new HashSet<>();

        switch (type) {
            case GENERATOR:
                // 调节器作为特殊连接点（沿轴向搜索范围=RANGE，与collectNetworkMembers一致）
                if (!location.equals(regulator)
                        && isWithinRangeAxial(location, regulator, getMaxConnectorRange())
                        && isWithinRangeAxial(regulator, location, RANGE)) {
                    neighbors.add(regulator);
                }
                // 发电机搜索电网内所有连接器，验证连接器范围能否覆盖发电机
                if (DEBUG_PATHS) {
                    debugPathLog(
                            "getNeighbors(GENERATOR): 源=" + formatLocation(location) + " 连接器总数=" + connectors.size());
                }
                for (Location connectorLoc : connectors.keySet()) {
                    boolean valid = validateConnection(
                            location, connectorLoc, EnergyNetComponentType.GENERATOR, EnergyNetComponentType.CONNECTOR);
                    if (DEBUG_PATHS) {
                        EnergyNetComponent connComp = getComponent(connectorLoc);
                        int connRange = connComp != null ? connComp.getRange() : -1;
                        debugPathLog("getNeighbors(GENERATOR): 检查连接器 " + formatLocation(connectorLoc)
                                + " range=" + connRange
                                + " 轴向距离=" + getAxialDistance(location, connectorLoc)
                                + " 结果=" + valid);
                    }
                    if (valid) {
                        neighbors.add(connectorLoc);
                    }
                }
                if (DEBUG_PATHS) {
                    debugPathLog("getNeighbors(GENERATOR): 最终邻居数=" + neighbors.size());
                }
                break;
            case CONSUMER:
                // 用电器不能作为发送方，没有出边
                break;

            case CAPACITOR:
                // 电容子网络 - 电容到电容只需相邻（6方向）
                for (Location capacitorLoc : capacitors.keySet()) {
                    if (!capacitorLoc.equals(location) && isAdjacent(location, capacitorLoc)) {
                        neighbors.add(capacitorLoc);
                    }
                }
                // 电容到调节器：相邻（6方向），调节器作为特殊连接器
                if (!location.equals(regulator) && isAdjacent(location, regulator)) {
                    neighbors.add(regulator);
                }
                // 电容到连接器：相邻（6方向）+ 连接器范围覆盖电容
                for (Location connectorLoc : connectors.keySet()) {
                    if (isAdjacent(location, connectorLoc)) {
                        if (validateConnection(
                                location,
                                connectorLoc,
                                EnergyNetComponentType.CAPACITOR,
                                EnergyNetComponentType.CONNECTOR)) {
                            neighbors.add(connectorLoc);
                        }
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
                // 连接器到连接器：沿轴向双向范围覆盖（与processConnector一致）
                if (DEBUG_PATHS) {
                    debugPathLog(
                            "getNeighbors(CONNECTOR): 当前=" + formatLocation(location) + " 连接器总数=" + connectors.size());
                }
                for (Location otherConnector : connectors.keySet()) {
                    if (!otherConnector.equals(location)) {
                        EnergyNetComponent otherComponent = getComponent(otherConnector);
                        boolean valid = otherComponent != null
                                && validateConnection(
                                        location,
                                        otherConnector,
                                        EnergyNetComponentType.CONNECTOR,
                                        EnergyNetComponentType.CONNECTOR);
                        if (DEBUG_PATHS) {
                            debugPathLog("getNeighbors(CONNECTOR): 检查连接器 " + formatLocation(otherConnector)
                                    + " 轴向距离=" + getAxialDistance(location, otherConnector)
                                    + " 结果=" + valid);
                        }
                        if (valid) {
                            neighbors.add(otherConnector);
                        }
                    }
                }
                if (!isLongRangeConnector) {
                    // 连接器到发电机：连接器沿轴向覆盖发电机即可
                    for (Location terminus : generators.keySet()) {
                        if (isWithinRangeAxial(location, terminus, connRange)) {
                            if (validateConnection(
                                    location,
                                    terminus,
                                    EnergyNetComponentType.CONNECTOR,
                                    EnergyNetComponentType.GENERATOR)) {
                                neighbors.add(terminus);
                            }
                        }
                    }
                    // 连接器到调节器：连接器沿轴向覆盖调节器即可
                    if (!location.equals(regulator) && isWithinRangeAxial(location, regulator, connRange)) {
                        neighbors.add(regulator);
                    }
                    // 连接器到用电器：连接器沿轴向覆盖用电器即可
                    if (DEBUG_PATHS) {
                        debugPathLog("getNeighbors(CONNECTOR): 当前=" + formatLocation(location)
                                + " range=" + connRange
                                + " 用电器总数=" + consumers.size());
                    }
                    for (Location consumer : consumers.keySet()) {
                        boolean inRange = isWithinRangeAxial(location, consumer, connRange);
                        EnergyNetComponent consumerComp = getComponent(consumer);
                        if (DEBUG_PATHS) {
                            debugPathLog("getNeighbors(CONNECTOR): 检查用电器 " + formatLocation(consumer)
                                    + " 轴向距离=" + getAxialDistance(location, consumer)
                                    + " 在范围内=" + inRange
                                    + " getComponent="
                                    + (consumerComp != null ? consumerComp.getEnergyComponentType() : "null"));
                        }
                        if (inRange) {
                            boolean valid = validateConnection(
                                    location,
                                    consumer,
                                    EnergyNetComponentType.CONNECTOR,
                                    EnergyNetComponentType.CONSUMER);
                            if (DEBUG_PATHS) {
                                debugPathLog("getNeighbors(CONNECTOR): validateConnection结果=" + valid);
                            }
                            if (valid) {
                                neighbors.add(consumer);
                            }
                        }
                    }
                    // 连接器到电容：连接器沿轴向覆盖电容即可
                    for (Location capacitor : capacitors.keySet()) {
                        if (isWithinRangeAxial(location, capacitor, connRange)) {
                            if (validateConnection(
                                    location,
                                    capacitor,
                                    EnergyNetComponentType.CONNECTOR,
                                    EnergyNetComponentType.CAPACITOR)) {
                                neighbors.add(capacitor);
                            }
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
     * BFS搜索节点（父节点回溯，避免路径克隆）
     */
    private static class BFSNode {
        final Location location;
        final int parentIndex; // nodes列表中的父节点索引，-1表示根
        final int length; // 路径长度（连接器数量）

        BFSNode(Location location, int parentIndex, int length) {
            this.location = location;
            this.parentIndex = parentIndex;
            this.length = length;
        }
    }

    /**
     * 从父节点链中提取连接器列表
     */
    private List<Location> extractConnectorsFromPath(List<BFSNode> nodes, int nodeIndex) {
        return extractConnectorsFromPath(nodes, nodeIndex, null);
    }

    private List<Location> extractConnectorsFromPath(
            List<BFSNode> nodes, int nodeIndex, @Nullable Location excludeTarget) {
        List<Location> connectors = new ArrayList<>();
        while (nodeIndex >= 0) {
            BFSNode n = nodes.get(nodeIndex);
            if (n.location.equals(excludeTarget)) {
                nodeIndex = n.parentIndex;
                continue;
            }
            EnergyNetComponent comp = getComponent(n.location);
            if (comp != null
                    && (comp.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR
                            || comp.getEnergyComponentType() == EnergyNetComponentType.CAPACITOR)) {
                connectors.add(n.location);
            } else if (n.location.equals(regulator)) {
                connectors.add(n.location);
            }
            nodeIndex = n.parentIndex;
        }
        // 回溯是从消费者→源端方向，反转成源端→消费者方向用于显示
        Collections.reverse(connectors);
        return connectors;
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
                if (targetComponent == null) {
                    SlimefunItem dbItem = querySlimefunItemFromDb(targetLoc);
                    if (dbItem instanceof EnergyNetComponent dbComp) {
                        targetComponent = dbComp;
                    } else {
                        continue;
                    }
                }
                EnergyNetComponentType targetType = targetComponent.getEnergyComponentType();
                if (isLocationInOtherGrid(targetLoc)) {
                    debugLog("collectNetworkMembers: 调节器扩展发现机器属于其他电网 @ " + formatLocation(targetLoc));
                    updateHologram(targetLoc.getBlock(), "&c电网冲突：该机器已属于其他电网", () -> false);
                    return false;
                }
                if (targetType == EnergyNetComponentType.CONNECTOR || targetType == EnergyNetComponentType.CAPACITOR) {
                    visited.add(targetLoc);
                    queue.add(targetLoc);
                } else if (targetType == EnergyNetComponentType.GENERATOR
                        || targetType == EnergyNetComponentType.CONSUMER) {
                    visited.add(targetLoc);
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
                        // 标记对方电网为冲突状态
                        EnergyNet otherNet = getNetworkFromLocation(current);
                        if (otherNet != null) {
                            otherNet.conflictMode = true;
                        }
                        // 在冲突调节器上显示冲突悬浮字
                        updateHologram(current.getBlock(), "&c电网冲突：多个能源调节器相连", () -> false);
                        // 在本电网调节器上也显示冲突悬浮字
                        Slimefun.runSync(() -> updateHologram(regulator.getBlock(), "&c电网冲突：多个能源调节器相连", () -> false));
                        return false;
                    }
                    continue;
                }
            }

            // 检查当前机器是否已被其他电网占用（排除调节器自身）
            if (isLocationInOtherGrid(current)) {
                debugLog("collectNetworkMembers: 机器已被其他电网占用 @ " + formatLocation(current));
                // 标记占用该机器的电网也为冲突状态
                EnergyNet otherNet = getNetworkFromLocation(current);
                if (otherNet != null && otherNet != this) {
                    otherNet.conflictMode = true;
                    Slimefun.runSync(
                            () -> otherNet.updateHologram(otherNet.regulator.getBlock(), "&c电网冲突：电网交叉", () -> false));
                }
                updateHologram(current.getBlock(), "&c电网冲突：该机器已属于其他电网", () -> false);
                // 本电网调节器也显示冲突
                Slimefun.runSync(() -> updateHologram(regulator.getBlock(), "&c电网冲突：电网交叉", () -> false));
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

        return true;
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
        for (int[] axis : axes) {
            for (int i = 1; i <= range; i++) {
                if (abortRequested || destroyed) {
                    debugLog("processConnector: 检测到打断");
                    return false;
                }
                Location targetLoc = connectorLoc.clone().add(axis[0] * i, axis[1] * i, axis[2] * i);

                if (visited.contains(targetLoc)) {
                    continue;
                }

                // 先尝试 EnergyNetComponent
                EnergyNetComponent targetComponent = getComponent(targetLoc);
                if (targetComponent != null) {
                    EnergyNetComponentType targetType = targetComponent.getEnergyComponentType();

                    if (!checkRangeValidation(connectorLoc, targetLoc, targetType)) {
                        continue;
                    }

                    if (isLocationInOtherGrid(targetLoc)) {
                        debugLog("processConnector: 冲突 - 机器已属于其他电网 @ " + formatLocation(targetLoc));
                        updateHologram(targetLoc.getBlock(), "&c电网冲突：该机器已属于其他电网", () -> false);
                        return false;
                    }

                    visited.add(targetLoc);
                    debugLog("processConnector: 发现 " + targetType + " @ " + formatLocation(targetLoc));

                    if (targetType == EnergyNetComponentType.CONNECTOR
                            || (!isLongRange && targetType == EnergyNetComponentType.CAPACITOR)) {
                        queue.add(targetLoc);
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
                        updateHologram(targetLoc.getBlock(), "&c电网冲突：该机器已属于其他电网", () -> false);
                        return false;
                    }

                    visited.add(targetLoc);
                    debugLog("processConnector: 发现(DB) " + targetType + " @ " + formatLocation(targetLoc));

                    if (targetType == EnergyNetComponentType.CONNECTOR
                            || (!isLongRange && targetType == EnergyNetComponentType.CAPACITOR)) {
                        queue.add(targetLoc);
                    }
                    continue;
                }

                // 发现调节器（调节器是GENERATOR类型但不实现EnergyNetComponent）
                if (sfItem.getId().equals("ENERGY_REGULATOR")) {
                    if (!targetLoc.equals(regulator)) {
                        debugLog("processConnector: 发现冲突调节器 @ " + formatLocation(targetLoc));
                        updateHologram(targetLoc.getBlock(), "&c电网冲突：多个能源调节器相连", () -> false);
                        Slimefun.runSync(() -> updateHologram(regulator.getBlock(), "&c电网冲突：多个能源调节器相连", () -> false));
                        return false;
                    }
                    // 是本电网的调节器，加入visited但不加入queue（已在BFS起点）
                    visited.add(targetLoc);
                    debugLog("processConnector: 发现本电网调节器 @ " + formatLocation(targetLoc));
                }
            }
        }
        return true;
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

            if (targetType == EnergyNetComponentType.CONNECTOR
                    && !checkRangeValidation(targetLoc, capacitorLoc, EnergyNetComponentType.CAPACITOR)) {
                continue;
            }

            if (isLocationInOtherGrid(targetLoc)) {
                updateHologram(targetLoc.getBlock(), "&c电网冲突：该机器已属于其他电网", () -> false);
                return false;
            }

            visited.add(targetLoc);
            queue.add(targetLoc);
        }
        return true;
    }

    /**
     * 基于预计算路径执行能量传输
     */
    private void performEnergyTransfer() {
        // 清空连接器负载记录
        connectorLoad.replaceAll((loc, load) -> 0L);

        // 先让发电机产出电力（燃烧燃料、更新charge）
        // 返回值包含可储电发电机(charge)和不可储电发电机(太阳能)的总供电
        long generatorSupply = tickAllGenerators(timestamp -> {});
        // 排除已损坏的电容
        tickAllCapacitors();

        // 计算总需求
        long totalDemand = calculateTotalDemand();

        // 根据规格说明实现传输逻辑
        if (generatorSupply >= totalDemand) {
            // 发电机供电充足，多余电力存入电容
            transferFromGenerators(totalDemand);
            long totalNew = netNewEnergy + calcNonChargeableRemaining();
            long usedFromNew = Math.min(totalNew, totalDemand);
            long excess = totalNew - usedFromNew;
            long stored = storeRemainingEnergy(excess);
            // 存完电容后扣减发电机，避免能量克隆（只扣实际存入电容的量）
            if (stored > 0 && netNewEnergy > 0) {
                for (Map.Entry<Location, Long> entry : perGeneratorNewCharge.entrySet()) {
                    Location genLoc = entry.getKey();
                    long newCharge = entry.getValue();
                    if (newCharge <= 0) continue;
                    EnergyNetProvider gen = generators.get(genLoc);
                    if (gen != null && gen.isChargeable()) {
                        double fraction = (double) newCharge / netNewEnergy;
                        long reduction = (long) (fraction * stored);
                        if (reduction > 0) {
                            gen.setCharge(genLoc, Math.max(0, gen.getChargeLong(genLoc) - reduction));
                        }
                    }
                }
            }
        } else {
            // 先用发电机供电，然后用电容供电
            long supplyLeft = transferFromGenerators(generatorSupply);
            long usedFromGenerators = generatorSupply - supplyLeft;
            long remainingDemand = totalDemand - usedFromGenerators;
            if (remainingDemand > 0) {
                transferFromCapacitors(remainingDemand);
            }
        }

        // 连接器老化处理（在所有负载记录完成后）
        ConnectorAgingManager.processAging(this);
    }

    /**
     * 从发电机传输能量到消费者
     * @param maxEnergy 最大传输能量
     * @return 剩余的未满足需求
     */
    private long transferFromGenerators(long maxEnergy) {
        long remainingEnergy = maxEnergy;

        // 按路径长度分组排序
        List<EnergyPath> sortedPaths = getSortedPaths(generatorPaths);

        // 按(源, 目标, 长度)分组路径（LinkedHashMap保留排序顺序）
        Map<String, List<EnergyPath>> pathGroups = new LinkedHashMap<>();
        for (EnergyPath path : sortedPaths) {
            String key = path.source.getBlockX() + "," + path.source.getBlockY() + "," + path.source.getBlockZ() + ","
                    + path.consumer.getBlockX() + "," + path.consumer.getBlockY() + "," + path.consumer.getBlockZ()
                    + ","
                    + path.length;
            pathGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
        }

        // 处理每组路径
        for (List<EnergyPath> pathGroup : pathGroups.values()) {
            if (remainingEnergy <= 0) {
                break;
            }

            // 组内所有路径具有相同的源、目标和长度
            EnergyPath firstPath = pathGroup.get(0);
            Location generatorLoc = firstPath.source;
            Location consumerLoc = firstPath.consumer;

            // 获取发电机和消费者组件
            EnergyNetProvider generator = generators.get(generatorLoc);
            EnergyNetComponent consumer = consumers.get(consumerLoc);

            if (generator == null || consumer == null) {
                continue;
            }

            // 检查发电机是否有电，消费者是否已满
            boolean isNonChargeable = !generator.isChargeable();
            long generatorCharge = isNonChargeable
                    ? nonChargeableSupply.getOrDefault(generatorLoc, 0L)
                    : generator.getChargeLong(generatorLoc);
            long consumerCharge = consumer.getChargeLong(consumerLoc);
            long consumerCapacity = consumer.getCapacityLong();

            if (generatorCharge <= 0 || consumerCharge >= consumerCapacity) {
                continue;
            }

            // 计算可传输的电量
            long availableFromGenerator = generatorCharge;
            long neededByConsumer = consumerCapacity - consumerCharge;
            long transferAmount = Math.min(Math.min(availableFromGenerator, neededByConsumer), remainingEnergy);

            if (transferAmount <= 0) {
                continue;
            }

            // 执行能量传输
            if (isNonChargeable) {
                long remaining = nonChargeableSupply.getOrDefault(generatorLoc, 0L) - transferAmount;
                if (remaining <= 0) {
                    nonChargeableSupply.remove(generatorLoc);
                } else {
                    nonChargeableSupply.put(generatorLoc, remaining);
                }
            } else {
                generator.setCharge(generatorLoc, generatorCharge - transferAmount);
            }
            consumer.setCharge(consumerLoc, consumerCharge + transferAmount);
            remainingEnergy -= transferAmount;

            // 记录连接器负载 - 将传输量均摊到组内的所有路径
            long loadPerPath = transferAmount / pathGroup.size();
            long remainder = transferAmount % pathGroup.size(); // 余数，如果有

            for (int i = 0; i < pathGroup.size(); i++) {
                EnergyPath path = pathGroup.get(i);
                // 分配负载，如果有余数则前几条路径多分配1
                long pathLoad = loadPerPath + (i < remainder ? 1 : 0);
                if (pathLoad > 0) {
                    recordConnectorLoad(path, pathLoad);
                }
            }
        }

        return Math.max(0, remainingEnergy); // 返回剩余的未满足需求
    }

    /**
     * 从电容传输能量到消费者
     * @param maxEnergy 最大传输能量
     * @return 剩余的未满足需求
     */
    private long transferFromCapacitors(long maxEnergy) {
        long remainingEnergy = maxEnergy;

        // 按路径长度分组排序
        List<EnergyPath> sortedPaths = getSortedPaths(capacitorPaths);

        // 按(源, 目标, 长度)分组路径（LinkedHashMap保留排序顺序）
        Map<String, List<EnergyPath>> pathGroups = new LinkedHashMap<>();
        for (EnergyPath path : sortedPaths) {
            String key = path.source.getBlockX() + "," + path.source.getBlockY() + "," + path.source.getBlockZ() + ","
                    + path.consumer.getBlockX() + "," + path.consumer.getBlockY() + "," + path.consumer.getBlockZ()
                    + ","
                    + path.length;
            pathGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
        }

        // 处理每组路径
        for (List<EnergyPath> pathGroup : pathGroups.values()) {
            if (remainingEnergy <= 0) {
                break;
            }

            // 组内所有路径具有相同的源、目标和长度
            EnergyPath firstPath = pathGroup.get(0);
            Location capacitorLoc = firstPath.source;
            Location consumerLoc = firstPath.consumer;

            // 获取电容和消费者组件
            EnergyNetComponent capacitor = capacitors.get(capacitorLoc);
            EnergyNetComponent consumer = consumers.get(consumerLoc);

            if (capacitor == null || consumer == null) {
                continue;
            }

            // 检查电容是否有电，消费者是否已满
            long capacitorCharge = capacitor.getChargeLong(capacitorLoc);
            long consumerCharge = consumer.getChargeLong(consumerLoc);
            long consumerCapacity = consumer.getCapacityLong();

            if (capacitorCharge <= 0 || consumerCharge >= consumerCapacity) {
                continue;
            }

            // 计算可传输的电量
            long availableFromCapacitor = capacitorCharge;
            long neededByConsumer = consumerCapacity - consumerCharge;
            long transferAmount = Math.min(Math.min(availableFromCapacitor, neededByConsumer), remainingEnergy);

            if (transferAmount <= 0) {
                continue;
            }

            // 执行能量传输
            capacitor.setCharge(capacitorLoc, capacitorCharge - transferAmount);
            consumer.setCharge(consumerLoc, consumerCharge + transferAmount);
            remainingEnergy -= transferAmount;

            // 记录连接器负载 - 将传输量均摊到组内的所有路径
            long loadPerPath = transferAmount / pathGroup.size();
            long remainder = transferAmount % pathGroup.size(); // 余数，如果有

            for (int i = 0; i < pathGroup.size(); i++) {
                EnergyPath path = pathGroup.get(i);
                // 分配负载，如果有余数则前几条路径多分配1
                long pathLoad = loadPerPath + (i < remainder ? 1 : 0);
                if (pathLoad > 0) {
                    recordConnectorLoad(path, pathLoad);
                }
            }
        }

        return Math.max(0, remainingEnergy); // 返回剩余的未满足需求
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
            long capacity = component.getCapacityLong();
            long charge = component.getChargeLong(loc);
            if (charge < capacity) {
                demand = NumberUtils.flowSafeAddition(demand, capacity - charge);
            }
        }
        return demand;
    }

    /**
     * 获取按路径长度排序的能量路径列表
     */
    private List<EnergyPath> getSortedPaths(Map<Location, Set<EnergyPath>> pathMap) {
        List<EnergyPath> allPaths = new ArrayList<>();
        for (Set<EnergyPath> paths : pathMap.values()) {
            allPaths.addAll(paths);
        }

        // 按路径长度排序
        allPaths.sort((p1, p2) -> Integer.compare(p1.length, p2.length));
        return allPaths;
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
        removeHologramAt(l);

        if (regulator.equals(l)) {
            destroyed = true;
            abortRequested = true;
            removeAllHolograms();
            manager.unregisterNetwork(this);
        } else {
            conflictMode = false;
            initialized = false;
            abortRequested = true;
            connectedLocations.remove(l);
            regulatorNodes.remove(l);
            connectorNodes.remove(l);
            terminusNodes.remove(l);
        }
    }

    /**
     * 清除电网内所有机器的悬浮字（用于电网销毁时清理冲突提示等）
     */
    private void removeAllHolograms() {
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
                    Map<Location, EnergyPath> capPaths = net.generatorToCapacitorPaths.get(target);
                    if (capPaths != null && !capPaths.isEmpty()) {
                        sb.append("&b▼ 电容充电路由 (出发)\n");
                        List<EnergyPath> capPathList = new ArrayList<>(capPaths.values());
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
        conflictMode = false;
        initialized = false;
        abortRequested = true;
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
