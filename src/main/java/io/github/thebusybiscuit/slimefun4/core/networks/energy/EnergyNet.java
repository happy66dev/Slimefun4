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
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
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

    private static final boolean DEBUG = true;
    private static final int RANGE = 6;

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

    private final Map<Location, EnergyNetProvider> generators = new HashMap<>();
    private final Map<Location, EnergyNetComponent> capacitors = new HashMap<>();
    private final Map<Location, EnergyNetComponent> consumers = new HashMap<>();

    // 新规格说明添加的字段
    private final Map<Location, EnergyNetComponent> connectors = new HashMap<>();
    private final Map<Location, Long> connectorLoad = new HashMap<>();
    private final Map<Location, Set<EnergyPath>> generatorPaths = new HashMap<>();
    private final Map<Location, Set<EnergyPath>> capacitorPaths = new HashMap<>();
    private volatile boolean initializing = false;
    private volatile boolean initialized = false;
    private volatile boolean abortRequested = false;
    private final Set<String> weaklyLoadedChunks = new HashSet<>();

    private static final ExecutorService GRID_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Slimefun-Grid-Init");
        t.setDaemon(true);
        return t;
    });

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

            if (!initialized) {
                if (!initializing) {
                    debugLog("tick: 未初始化，提交异步初始化任务");
                    GRID_EXECUTOR.submit(this::initializeNetworkAsync);
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

    private void storeRemainingEnergy(long remainingEnergy) {
        for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
            Location loc = entry.getKey();

            var data = StorageCacheUtils.getDataContainer(loc);
            if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
                continue;
            }

            // 检查机器是否损坏，如果损坏则跳过处理
            if (Slimefun.getMachineDamageService().isMachineDamaged(data)) {
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

            SlimefunItem item = (SlimefunItem) component;
            long oldCharge = component.getChargeLong(loc);

            if (remainingEnergy > 0) {
                long capacity = component.getCapacityLong();

                if (remainingEnergy > capacity) {
                    component.setCharge(loc, (long) capacity);
                    remainingEnergy -= capacity;
                } else {
                    component.setCharge(loc, (long) remainingEnergy);
                    remainingEnergy = 0;
                }
            } else {
                component.setCharge(loc, 0L);
            }

            // 计算充放电量
            long newCharge = component.getChargeLong(loc);
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
            }
        }

        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            Location loc = entry.getKey();

            var data = StorageCacheUtils.getDataContainer(loc);
            if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
                continue;
            }

            EnergyNetProvider component = entry.getValue();
            // 修复物品机制与机器损坏机制一致
            if (!((SlimefunItem) component).getId().equals(data.getSfId())) {
                var newItem = SlimefunItem.getById(data.getSfId());
                if (!(newItem instanceof EnergyNetProvider newProvider)) {
                    continue;
                }
                generators.put(loc, newProvider);
                component = newProvider;
            }

            long capacity = component.getCapacityLong();

            if (remainingEnergy > 0) {
                if (remainingEnergy > capacity) {
                    component.setCharge(loc, capacity);
                    remainingEnergy -= capacity;
                } else {
                    component.setCharge(loc, remainingEnergy);
                    remainingEnergy = 0;
                }
            } else {
                component.setCharge(loc, 0L);
            }
        }
    }

    private long tickAllGenerators(@Nonnull LongConsumer timings) {
        Set<Location> explodedBlocks = new HashSet<>();
        long supply = 0;

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

                // 检查机器是否损坏，如果损坏则跳过处理
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
                long storedEnergy = 0;

                if (provider.isChargeable()) {
                    storedEnergy = provider.getChargeLong(loc);
                }

                long totalEnergy = NumberUtils.flowSafeAddition(generatedEnergy, storedEnergy);

                if (provider.willExplode(loc, data)) {
                    explodedBlocks.add(loc);
                    Slimefun.getDatabaseManager().getBlockDataController().removeBlock(loc);

                    Slimefun.runSync(() -> {
                        loc.getBlock().setType(Material.LAVA);
                        loc.getWorld().createExplosion(loc, 0F, false);
                    });
                } else {
                    supply = NumberUtils.flowSafeAddition(supply, totalEnergy);
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
        SlimefunItem item = StorageCacheUtils.getSlimefunItem(l);

        if (item instanceof EnergyNetComponent component) {
            return component;
        }

        return null;
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
        debugLog("initializeNetworkAsync: 开始异步初始化");
        synchronized (regulator.toString().intern()) {
            if (abortRequested) {
                debugLog("initializeNetworkAsync: 被abortRequested打断，退出");
                abortRequested = false;
                return;
            }

            initializing = true;
            debugLog("initializeNetworkAsync: initializing=true");
            try {
                Slimefun.runSync(() -> updateHologram(regulator.getBlock(), "&e初始化电网中", () -> false));

                clearNetworkData();
                debugLog("initializeNetworkAsync: 数据已清空");

                if (abortRequested) {
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
                    debugLog("initializeNetworkAsync: collectNetworkMembers失败，注销电网");
                    Slimefun.runSync(() -> manager.unregisterNetwork(this));
                    return;
                }

                if (abortRequested) {
                    debugLog("initializeNetworkAsync: 在precomputePaths前被打断");
                    return;
                }

                precomputePaths();
                debugLog("initializeNetworkAsync: precomputePaths完成");

                if (abortRequested) {
                    debugLog("initializeNetworkAsync: 在初始化完成前被打断");
                    return;
                }

                initialized = true;
                debugLog("初始化完成 ✓ 调节器=" + formatLocation(regulator)
                        + " 发电机=" + generators.size() + " 连接器=" + connectors.size()
                        + " 电容=" + capacitors.size() + " 用电器=" + consumers.size()
                        + " 路径=" + (countTotalPaths(generatorPaths) + countTotalPaths(capacitorPaths)));
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
                    loadGridChunks();
                });
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

        // 清空基类的节点集合
        regulatorNodes.clear();
        connectorNodes.clear();
        terminusNodes.clear();
        connectedLocations.clear();

        // 重新添加调节器
        regulatorNodes.add(regulator);
        connectedLocations.add(regulator);
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
        return isWithinRange(connectorLoc, machineLoc, connectorRange);
    }

    /**
     * 检查两个位置是否相邻（距离<=1）
     */
    private boolean isAdjacent(Location loc1, Location loc2) {
        return Math.abs(loc1.getBlockX() - loc2.getBlockX()) <= 1
                && Math.abs(loc1.getBlockY() - loc2.getBlockY()) <= 1
                && Math.abs(loc1.getBlockZ() - loc2.getBlockZ()) <= 1;
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
     * 计算两个位置的最大轴向距离（用于调试显示）
     */
    private static int getDistance(Location loc1, Location loc2) {
        int dx = Math.abs(loc1.getBlockX() - loc2.getBlockX());
        int dy = Math.abs(loc1.getBlockY() - loc2.getBlockY());
        int dz = Math.abs(loc1.getBlockZ() - loc2.getBlockZ());
        return Math.max(dx, Math.max(dy, dz));
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
            // 连接器用自身范围覆盖目标
            forwardValid = isWithinRange(loc1, loc2, comp1.getRange());
        } else if (type1 == EnergyNetComponentType.GENERATOR) {
            // 发电机不需要正向验证，只需连接器能覆盖它即可（在反向中检查）
            forwardValid = true;
        } else if (type1 == EnergyNetComponentType.CAPACITOR) {
            // 电容必须相邻才能发送到连接器
            forwardValid = isAdjacent(loc1, loc2);
        }
        if (!forwardValid) {
            return false;
        }

        // 反向验证：type2(连接器)能否接收到type1的信号
        // 连接器之间不需要反向验证（允许单向连接，高范围→低范围）
        if (type2 == EnergyNetComponentType.CONNECTOR && type1 != EnergyNetComponentType.CONNECTOR) {
            return isWithinRange(loc2, loc1, comp2.getRange());
        }

        return true;
    }

    /**
     * 预计算能量传输路径
     */
    private void precomputePaths() {
        generatorPaths.clear();
        capacitorPaths.clear();

        // 计算所有发电机到消费者的路径
        for (Location generatorLoc : generators.keySet()) {
            Set<EnergyPath> paths = findShortestPathsFromSource(generatorLoc);
            if (!paths.isEmpty()) {
                generatorPaths.put(generatorLoc, paths);
            }
        }

        // 计算所有电容到消费者的路径
        for (Location capacitorLoc : capacitors.keySet()) {
            Set<EnergyPath> paths = findShortestPathsFromSource(capacitorLoc);
            if (!paths.isEmpty()) {
                capacitorPaths.put(capacitorLoc, paths);
            }
        }
    }

    /**
     * 从源位置（发电机或电容）查找所有到消费者的最短路径
     * 使用带路径去环的广度优先搜索
     */
    private Set<EnergyPath> findShortestPathsFromSource(Location source) {
        Set<EnergyPath> shortestPaths = new HashSet<>();
        Map<Location, Integer> shortestDistances = new HashMap<>();

        // BFS队列：存储（当前位置，路径，路径长度）
        Queue<BFSNode> queue = new ArrayDeque<>();
        List<Location> initialPath = new ArrayList<>();
        initialPath.add(source);
        queue.add(new BFSNode(source, initialPath, 0));

        while (!queue.isEmpty()) {
            BFSNode current = queue.poll();
            Location currentLoc = current.location;
            List<Location> currentPath = current.path;
            int currentLength = current.length;

            // 如果当前是消费者，记录路径
            if (consumers.containsKey(currentLoc) && !currentLoc.equals(source)) {
                int existingDistance = shortestDistances.getOrDefault(currentLoc, Integer.MAX_VALUE);

                if (currentLength < existingDistance) {
                    // 发现更短的路径，清空之前的路径
                    shortestPaths.removeIf(path -> path.consumer.equals(currentLoc));
                    shortestDistances.put(currentLoc, currentLength);
                    shortestPaths.add(new EnergyPath(source, currentLoc, extractConnectors(currentPath)));
                } else if (currentLength == existingDistance) {
                    // 相同长度的路径，添加
                    shortestPaths.add(new EnergyPath(source, currentLoc, extractConnectors(currentPath)));
                }
                // 如果当前路径更长，跳过
                continue;
            }

            // 获取当前位置的组件
            EnergyNetComponent component = getComponent(currentLoc);
            if (component == null) {
                continue;
            }

            // 根据组件类型确定可以移动到的邻居
            Set<Location> neighbors = getNeighbors(currentLoc, component.getEnergyComponentType());

            for (Location neighbor : neighbors) {
                // 检查邻居是否已在当前路径中（去环）
                if (currentPath.contains(neighbor)) {
                    continue;
                }

                // 创建新路径
                List<Location> newPath = new ArrayList<>(currentPath);
                newPath.add(neighbor);

                // 计算新路径长度（只计算连接器数量）
                int newLength = currentLength;
                EnergyNetComponent neighborComponent = getComponent(neighbor);
                if (neighborComponent != null
                        && neighborComponent.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
                    newLength++;
                }

                queue.add(new BFSNode(neighbor, newPath, newLength));
            }
        }

        return shortestPaths;
    }

    /**
     * 获取指定位置的邻居位置（根据组件类型）
     * 包含反向验证
     */
    private Set<Location> getNeighbors(Location location, EnergyNetComponentType type) {
        Set<Location> neighbors = new HashSet<>();

        switch (type) {
            case GENERATOR:
                // 发电机搜索电网内所有连接器，验证连接器范围能否覆盖发电机
                for (Location connectorLoc : connectors.keySet()) {
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
                // 用电器不能作为发送方，没有出边
                break;

            case CAPACITOR:
                // 电容子网络 - 电容到电容只需相邻
                for (Location capacitorLoc : capacitors.keySet()) {
                    if (!capacitorLoc.equals(location) && isAdjacent(location, capacitorLoc)) {
                        neighbors.add(capacitorLoc);
                    }
                }
                // 电容到连接器：必须相邻 + 连接器范围覆盖电容
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
                int connRange = getComponent(location).getRange();
                // 连接器到连接器：双向范围覆盖
                for (Location otherConnector : connectors.keySet()) {
                    if (!otherConnector.equals(location)) {
                        EnergyNetComponent otherComponent = getComponent(otherConnector);
                        if (otherComponent != null
                                && validateConnection(
                                        location,
                                        otherConnector,
                                        EnergyNetComponentType.CONNECTOR,
                                        EnergyNetComponentType.CONNECTOR)) {
                            neighbors.add(otherConnector);
                        }
                    }
                }
                // 连接器到发电机：连接器范围覆盖发电机即可
                for (Location terminus : generators.keySet()) {
                    if (isWithinRange(location, terminus, connRange)) {
                        if (validateConnection(
                                location,
                                terminus,
                                EnergyNetComponentType.CONNECTOR,
                                EnergyNetComponentType.GENERATOR)) {
                            neighbors.add(terminus);
                        }
                    }
                }
                // 连接器到用电器：连接器范围覆盖用电器即可
                for (Location consumer : consumers.keySet()) {
                    if (isWithinRange(location, consumer, connRange)) {
                        if (validateConnection(
                                location,
                                consumer,
                                EnergyNetComponentType.CONNECTOR,
                                EnergyNetComponentType.CONSUMER)) {
                            neighbors.add(consumer);
                        }
                    }
                }
                // 连接器到电容：连接器范围覆盖电容即可
                for (Location capacitor : capacitors.keySet()) {
                    if (isWithinRange(location, capacitor, connRange)) {
                        if (validateConnection(
                                location,
                                capacitor,
                                EnergyNetComponentType.CONNECTOR,
                                EnergyNetComponentType.CAPACITOR)) {
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
     * 从完整路径中提取连接器位置
     */
    private List<Location> extractConnectors(List<Location> fullPath) {
        List<Location> connectorsInPath = new ArrayList<>();
        for (Location loc : fullPath) {
            EnergyNetComponent component = getComponent(loc);
            if (component != null && component.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
                connectorsInPath.add(loc);
            }
        }
        return connectorsInPath;
    }

    /**
     * BFS搜索节点
     */
    private static class BFSNode {
        final Location location;
        final List<Location> path;
        final int length; // 路径长度（连接器数量）

        BFSNode(Location location, List<Location> path, int length) {
            this.location = location;
            this.path = path;
            this.length = length;
        }
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

        // 从调节器扩展搜索周围RANGE格内的连接器和电容
        // 必须在BFS循环外执行，因为EnergyRegulator不实现EnergyNetComponent
        int regRange = getRange();
        for (int dx = -regRange; dx <= regRange; dx++) {
            for (int dy = -regRange; dy <= regRange; dy++) {
                for (int dz = -regRange; dz <= regRange; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    Location targetLoc = regulator.clone().add(dx, dy, dz);
                    if (visited.contains(targetLoc)) continue;
                    EnergyNetComponent targetComponent = getComponent(targetLoc);
                    if (targetComponent == null) continue;
                    EnergyNetComponentType targetType = targetComponent.getEnergyComponentType();
                    if (targetType == EnergyNetComponentType.CONNECTOR
                            || targetType == EnergyNetComponentType.CAPACITOR) {
                        visited.add(targetLoc);
                        queue.add(targetLoc);
                        debugLog("collectNetworkMembers: 调节器发现 " + targetType + " @ " + formatLocation(targetLoc));
                    }
                }
            }
        }

        while (!queue.isEmpty()) {
            Location current = queue.poll();
            EnergyNetComponent component = getComponent(current);

            if (component == null) {
                debugLog("collectNetworkMembers: BFS跳过 null组件 @ " + formatLocation(current));
                continue;
            }

            EnergyNetComponentType type = component.getEnergyComponentType();

            // 检查是否是调节器（冲突检测）
            if (type == EnergyNetComponentType.GENERATOR) {
                SlimefunItem item = (SlimefunItem) component;
                if (item.getId().equals("ENERGY_REGULATOR")) {
                    if (!current.equals(regulator)) {
                        debugLog("collectNetworkMembers: 发现冲突调节器 @ " + formatLocation(current));
                        updateHologram(current.getBlock(), "&c电网冲突：多个能源调节器相连", () -> false);
                        return false;
                    }
                    continue;
                }
            }

            // 检查当前机器是否已被其他电网占用（排除调节器自身）
            if (isLocationInOtherGrid(current)) {
                debugLog("collectNetworkMembers: 机器已被其他电网占用 @ " + formatLocation(current));
                updateHologram(current.getBlock(), "&c电网冲突：该机器已属于其他电网", () -> false);
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
        int range = connector.getRange();
        debugLog("processConnector: 范围=" + range + " @ " + formatLocation(connectorLoc));

        // 搜索连接器范围内的所有可能位置
        for (int dx = -range; dx <= range; dx++) {
            for (int dy = -range; dy <= range; dy++) {
                for (int dz = -range; dz <= range; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    Location targetLoc = connectorLoc.clone().add(dx, dy, dz);

                    if (visited.contains(targetLoc)) {
                        continue;
                    }

                    EnergyNetComponent targetComponent = getComponent(targetLoc);
                    if (targetComponent == null) {
                        continue;
                    }

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
                            || targetType == EnergyNetComponentType.CAPACITOR) {
                        queue.add(targetLoc);
                    }
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
        // 电容只能连接相邻的电容
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;

                    Location targetLoc = capacitorLoc.clone().add(dx, dy, dz);

                    // 检查是否已访问
                    if (visited.contains(targetLoc)) {
                        continue;
                    }

                    EnergyNetComponent targetComponent = getComponent(targetLoc);
                    if (targetComponent == null) {
                        continue;
                    }

                    // 电容只能连接电容
                    if (targetComponent.getEnergyComponentType() != EnergyNetComponentType.CAPACITOR) {
                        continue;
                    }

                    // 检查该电容是否已被其他电网占用
                    if (isLocationInOtherGrid(targetLoc)) {
                        updateHologram(targetLoc.getBlock(), "&c电网冲突：该电容已属于其他电网", () -> false);
                        return false;
                    }

                    // 电容之间不进行反向验证
                    visited.add(targetLoc);
                    queue.add(targetLoc);
                }
            }
        }
        return true;
    }

    /**
     * 基于预计算路径执行能量传输
     */
    private void performEnergyTransfer() {
        // 清空连接器负载记录
        connectorLoad.replaceAll((loc, load) -> 0L);

        // 计算总发电量和总需求量
        long totalSupply = calculateTotalSupply();
        long totalDemand = calculateTotalDemand();

        // 根据规格说明实现传输逻辑
        if (totalSupply >= totalDemand) {
            // 仅使用发电机供电
            transferFromGenerators(totalDemand);
        } else {
            // 先用发电机供电，然后用电容供电
            long remainingDemand = transferFromGenerators(totalSupply);
            if (remainingDemand > 0) {
                transferFromCapacitors(remainingDemand);
            }
        }
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

        // 按(源, 目标, 长度)分组路径
        Map<String, List<EnergyPath>> pathGroups = new HashMap<>();
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
            long generatorCharge = generator.getChargeLong(generatorLoc);
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
            generator.setCharge(generatorLoc, generatorCharge - transferAmount);
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

        return Math.max(0, -remainingEnergy); // 返回剩余的未满足需求
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

        // 按(源, 目标, 长度)分组路径
        Map<String, List<EnergyPath>> pathGroups = new HashMap<>();
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

        return Math.max(0, -remainingEnergy); // 返回剩余的未满足需求
    }

    /**
     * 计算总发电量（发电机当前存储的电量）
     */
    private long calculateTotalSupply() {
        long supply = 0;
        for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
            supply = NumberUtils.flowSafeAddition(supply, entry.getValue().getChargeLong(entry.getKey()));
        }
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

    /**
     * 处理网络更新（当机器被放置或拆除时调用）
     * 根据新规格说明，触发电网重新初始化
     */
    @Override
    public void markDirty(@Nonnull Location l) {
        if (regulator.equals(l)) {
            unloadGridChunks();
            manager.unregisterNetwork(this);
        } else {
            initialized = false;
            abortRequested = true;
        }
    }

    /**
     * 处理方块放置事件 - 静态入口
     * 搜索新机器周围18格内任何已有电网的节点（连接器或调节器），
     * 验证通过后触发其电网重新初始化
     * @param newMachineLoc 新放置的机器位置
     */
    public static void onMachinePlaced(Location newMachineLoc) {
        EnergyNetComponent machineComponent = getComponent(newMachineLoc);
        if (machineComponent == null) {
            return;
        }

        EnergyNetComponentType machineType = machineComponent.getEnergyComponentType();
        debugLog("放置机器 " + machineType + " @ " + formatLocation(newMachineLoc));

        Optional<EnergyNet> existingNet =
                Slimefun.getNetworkManager().getNetworkFromLocation(newMachineLoc, EnergyNet.class);
        if (existingNet.isPresent()) {
            debugLog("  机器已被电网占用，显示冲突");
            Block b = newMachineLoc.getBlock();
            existingNet.get().updateHologram(b, "&c电网冲突：该机器已属于其他电网", () -> false);
            return;
        }

        int searchRange = 18;
        int checked = 0;
        boolean foundRegulator = false;
        boolean foundConnector = false;
        for (int dx = -searchRange; dx <= searchRange; dx++) {
            for (int dy = -searchRange; dy <= searchRange; dy++) {
                for (int dz = -searchRange; dz <= searchRange; dz++) {
                    Location targetLoc = newMachineLoc.clone().add(dx, dy, dz);
                    if (targetLoc.equals(newMachineLoc)) continue;

                    Optional<EnergyNet> networkOpt =
                            Slimefun.getNetworkManager().getNetworkFromLocation(targetLoc, EnergyNet.class);
                    if (!networkOpt.isPresent()) {
                        continue;
                    }

                    EnergyNet network = networkOpt.get();

                    SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(targetLoc);
                    if (sfItem == null) {
                        continue;
                    }

                    checked++;

                    if (sfItem.getId().equals("ENERGY_REGULATOR")) {
                        foundRegulator = true;
                        boolean inRange = isWithinRange(targetLoc, newMachineLoc, RANGE);
                        debugLog("  找到调节器 @ " + formatLocation(targetLoc)
                                + " 距离=" + getDistance(targetLoc, newMachineLoc)
                                + " 范围内=" + inRange + " RANGE=" + RANGE);
                        if (inRange) {
                            debugLog("  → 通过调节器关联电网，触重新初始化");
                            network.markDirty(newMachineLoc);
                            return;
                        }
                        continue;
                    }

                    if (sfItem instanceof EnergyNetComponent component) {
                        EnergyNetComponentType targetType = component.getEnergyComponentType();

                        if (targetType == EnergyNetComponentType.CONNECTOR) {
                            foundConnector = true;
                            boolean validated = network.checkRangeValidation(targetLoc, newMachineLoc, machineType);
                            debugLog("  找到连接器 @ " + formatLocation(targetLoc)
                                    + " 范围=" + component.getRange()
                                    + " 验证=" + validated);
                            if (validated) {
                                debugLog("  → 通过连接器关联电网，触发重新初始化");
                                network.markDirty(newMachineLoc);
                                return;
                            }
                        }
                    }
                }
            }
        }
        debugLog(
                "  扫描完成: 检查了" + checked + "个电网节点" + " 找到调节器=" + foundRegulator + " 找到连接器=" + foundConnector + " 均无法关联");
    }

    /**
     * 计算相同源和目标位置的路径数量
     */
    private int countSameSourceTargetPaths(EnergyPath targetPath) {
        int count = 0;

        // 检查发电机路径
        for (Set<EnergyPath> paths : generatorPaths.values()) {
            for (EnergyPath path : paths) {
                if (path.source.equals(targetPath.source)
                        && path.consumer.equals(targetPath.consumer)
                        && path.length == targetPath.length) {
                    count++;
                }
            }
        }

        // 检查电容路径
        for (Set<EnergyPath> paths : capacitorPaths.values()) {
            for (EnergyPath path : paths) {
                if (path.source.equals(targetPath.source)
                        && path.consumer.equals(targetPath.consumer)
                        && path.length == targetPath.length) {
                    count++;
                }
            }
        }

        return Math.max(1, count); // 至少为1
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
     * 弱加载电网所有机器所在的区块（可读取方块，但不处理生物/红石/方块逻辑）
     */
    private void loadGridChunks() {
        unloadGridChunks();

        for (Location loc : connectedLocations) {
            World world = loc.getWorld();
            if (world == null) continue;
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;
            String key = world.getName() + "," + cx + "," + cz;

            if (!weaklyLoadedChunks.contains(key)) {
                Chunk chunk = world.getChunkAt(cx, cz);
                chunk.setForceLoaded(true);
                weaklyLoadedChunks.add(key);
            }
        }
    }

    /**
     * 释放所有弱加载的区块
     */
    private void unloadGridChunks() {
        for (String key : weaklyLoadedChunks) {
            String[] parts = key.split(",");
            World world = Bukkit.getWorld(parts[0]);
            if (world != null) {
                int cx = Integer.parseInt(parts[1]);
                int cz = Integer.parseInt(parts[2]);
                Chunk chunk = world.getChunkAt(cx, cz);
                chunk.setForceLoaded(false);
            }
        }
        weaklyLoadedChunks.clear();
    }

    /**
     * 格式化位置信息为字符串
     */
    private static String formatLocation(Location loc) {
        return loc.getWorld().getName() + " (" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                + ")";
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

    /**
     * 表示从发电机/电容到消费者的能量传输路径
     */
    private static class EnergyPath {
        final Location source; // 发电机或电容的位置
        final Location consumer; // 消费者的位置
        final List<Location> connectors; // 路径经过的连接器位置
        final int length; // 路径长度（连接器数量）

        EnergyPath(Location source, Location consumer, List<Location> connectors) {
            this.source = source;
            this.consumer = consumer;
            this.connectors = Collections.unmodifiableList(new ArrayList<>(connectors));
            this.length = connectors.size();
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
