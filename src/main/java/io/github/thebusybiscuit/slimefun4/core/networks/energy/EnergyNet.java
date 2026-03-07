package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    private static final int RANGE = 6;

    private final Map<Location, EnergyNetProvider> generators = new HashMap<>();
    private final Map<Location, EnergyNetComponent> capacitors = new HashMap<>();
    private final Map<Location, EnergyNetComponent> consumers = new HashMap<>();

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
                        // 电容只能连接周围1格的电容
                        discoverCapacitorNeighbors(l);
                    } else {
                        // 其他连接器使用正常范围
                        discoverNeighbors(l);
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
                updateHologram(b, "&4检测到附近有其他调节器", blockData::isPendingRemove);

                return;
            }

            super.tick();

            if (connectorNodes.isEmpty() && terminusNodes.isEmpty()) {
                updateHologram(b, "&4找不到能源网络", blockData::isPendingRemove);
            } else {
                long generatorsSupply = tickAllGenerators(timestamp::getAndAdd);
                long capacitorsSupply = tickAllCapacitors();
                long supply = NumberUtils.flowSafeAddition(generatorsSupply, capacitorsSupply);
                long remainingEnergy = supply;
                long demand = 0;

                for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
                    Location loc = entry.getKey();

                    var data = StorageCacheUtils.getDataContainer(loc);
                    if (data == null || data.isPendingRemove()) {
                        continue;
                    }

                    // 检查机器是否损坏，如果损坏则跳过处理
                    if (Slimefun.getMachineDamageService().isMachineDamaged(data)) {
                        continue;
                    }

                    EnergyNetComponent component = entry.getValue();
                    if (!((SlimefunItem) component).getId().equals(data.getSfId())) {
                        var newItem = SlimefunItem.getById(data.getSfId());
                        if (!(newItem instanceof EnergyNetComponent newComponent)
                                || newComponent.getEnergyComponentType() != EnergyNetComponentType.CONSUMER) {
                            continue;
                        }
                        consumers.put(loc, newComponent);
                        component = newComponent;
                    }

                    if (!data.isDataLoaded()) {
                        StorageCacheUtils.requestLoad(data);
                        continue;
                    }

                    long capacity = component.getCapacityLong();
                    long charge = component.getChargeLong(loc);

                    if (charge < capacity) {
                        long availableSpace = capacity - charge;
                        demand = NumberUtils.flowSafeAddition(demand, availableSpace);

                        if (remainingEnergy > 0) {
                            if (remainingEnergy > availableSpace) {
                                component.setCharge(loc, capacity);
                                remainingEnergy -= availableSpace;
                            } else {
                                long curCharge = NumberUtils.flowSafeAddition(charge, remainingEnergy);
                                component.setCharge(loc, (long) curCharge);

                                remainingEnergy = 0;
                            }

                            // 注意：此处不处理机器损坏，因为充电过程不应计入工作刻
                            // 只有当机器实际消耗能量进行工作时才应计入工作刻
                        }
                    }
                }
                
                // 处理连接器的机器损坏逻辑
                for (Location loc : connectorNodes) {
                    var data = StorageCacheUtils.getDataContainer(loc);
                    if (data == null || data.isPendingRemove() || !data.isDataLoaded()) {
                        continue;
                    }
                    
                    EnergyNetComponent component = getComponent(loc);
                    if (component != null && component.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
                        SlimefunItem item = (SlimefunItem) component;
                        // 损坏率增加逻辑/工作刻与机器损坏不一致，先空，后续实现
                    }
                }
                
                storeRemainingEnergy(remainingEnergy);
                updateHologram(blockData, supply, demand);
            }
        } finally {
            // We have subtracted the timings from Generators, so they do not show up twice.
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
            if (data == null || data.isPendingRemove() || !data.isDataLoaded() || Slimefun.getMachineDamageService().isMachineDamaged(data)) {
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
}
