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
package io.github.thebusybiscuit.slimefun4.core.networks.energy;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet.EnergyPath;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedParticle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class MultimeterDisplayManager implements Listener {

    private static final int PARTICLES_PER_METER = 4;
    private static final double MAX_DISTANCE = 32.0;
    private static final int DISPLAY_SECONDS = 15;
    private static final Color SHARED_COLOR = Color.WHITE;
    private static final Color[] CAPACITOR_COLORS = {
        Color.fromRGB(80, 130, 255),
        Color.fromRGB(80, 255, 255),
        Color.fromRGB(50, 80, 200),
        Color.fromRGB(130, 100, 255),
    };

    private static final Color[] CONSUMER_COLORS = {
        Color.fromRGB(255, 80, 80),
        Color.fromRGB(255, 160, 50),
        Color.fromRGB(255, 255, 80),
        Color.fromRGB(255, 130, 200),
    };

    private static final Map<UUID, Map<Location, PathDisplay>> pathDisplays = new HashMap<>();
    private static final Map<UUID, Map<Location, ConnectorDisplay>> connectorDisplays = new HashMap<>();
    private static boolean registered = false;

    private MultimeterDisplayManager() {}

    public static void init() {
        if (!registered) {
            Slimefun s = Slimefun.instance();
            if (s != null) {
                s.getServer().getPluginManager().registerEvents(new MultimeterDisplayManager(), s);
                registered = true;
            }
        }
    }

    public static boolean togglePathDisplay(
            @Nonnull Player p,
            @Nonnull Location machineLoc,
            @Nonnull List<EnergyPath> paths,
            @Nonnull EnergyNet net,
            @Nonnull EnergyNetComponentType type) {
        init();
        UUID uid = p.getUniqueId();
        Map<Location, PathDisplay> map = pathDisplays.computeIfAbsent(uid, k -> new HashMap<>());
        PathDisplay existing = map.get(machineLoc);
        if (existing != null) {
            existing.cancel();
            map.remove(machineLoc);
            return false;
        }
        PathDisplay display = new PathDisplay(p, machineLoc, paths, net, type);
        map.put(machineLoc, display);
        display.start();
        return true;
    }

    public static boolean isPathDisplayActive(@Nonnull Player p, @Nonnull Location machineLoc) {
        Map<Location, PathDisplay> map = pathDisplays.get(p.getUniqueId());
        return map != null && map.containsKey(machineLoc);
    }

    public static void showConnectorLoad(@Nonnull Player p, @Nonnull Location connLoc, @Nonnull EnergyNet net) {
        init();
        UUID uid = p.getUniqueId();
        Map<Location, ConnectorDisplay> map = connectorDisplays.computeIfAbsent(uid, k -> new HashMap<>());
        ConnectorDisplay existing = map.get(connLoc);
        if (existing != null) {
            existing.resetTimer();
            return;
        }
        ConnectorDisplay display = new ConnectorDisplay(p, connLoc, net);
        map.put(connLoc, display);
        display.start();
    }

    public static void cleanupPlayer(@Nonnull Player p) {
        UUID uid = p.getUniqueId();
        Map<Location, PathDisplay> pMap = pathDisplays.remove(uid);
        if (pMap != null) {
            for (PathDisplay d : pMap.values()) d.cancel();
        }
        Map<Location, ConnectorDisplay> cMap = connectorDisplays.remove(uid);
        if (cMap != null) {
            for (ConnectorDisplay d : cMap.values()) d.cancel();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        cleanupPlayer(e.getPlayer());
    }

    private static void safeRemoveHologram(Location loc) {
        try {
            Slimefun.getHologramsService().removeHologram(loc);
        } catch (Exception ignored) {
        }
    }

    private static void safeCreateHologram(Location loc, String text) {
        try {
            Slimefun.getHologramsService().setHologramLabel(loc, ChatColors.color(text));
        } catch (Exception ignored) {
        }
    }

    private static Location hologramLoc(Location blockLoc) {
        return blockLoc.clone().add(0.5, 0.75, 0.5);
    }

    private static boolean hasNearbyPlayer(Location loc) {
        if (!loc.isWorldLoaded()) return false;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getWorld().equals(loc.getWorld())
                    && online.getLocation().distanceSquared(loc) < MAX_DISTANCE * MAX_DISTANCE) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasNearbyPlayerOnSegment(Location from, Location to) {
        if (from.getWorld() == null || to.getWorld() == null || !from.getWorld().equals(to.getWorld())) {
            return false;
        }

        double minX = Math.min(from.getX(), to.getX()) - MAX_DISTANCE;
        double maxX = Math.max(from.getX(), to.getX()) + MAX_DISTANCE;
        double minY = Math.min(from.getY(), to.getY()) - MAX_DISTANCE;
        double maxY = Math.max(from.getY(), to.getY()) + MAX_DISTANCE;
        double minZ = Math.min(from.getZ(), to.getZ()) - MAX_DISTANCE;
        double maxZ = Math.max(from.getZ(), to.getZ()) + MAX_DISTANCE;
        double maxDistanceSquared = MAX_DISTANCE * MAX_DISTANCE;

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.getWorld().equals(from.getWorld())) {
                continue;
            }

            Location playerLoc = online.getLocation();
            if (playerLoc.getX() < minX
                    || playerLoc.getX() > maxX
                    || playerLoc.getY() < minY
                    || playerLoc.getY() > maxY
                    || playerLoc.getZ() < minZ
                    || playerLoc.getZ() > maxZ) {
                continue;
            }

            if (distanceSquaredToSegment(playerLoc, from, to) <= maxDistanceSquared) {
                return true;
            }
        }

        return false;
    }

    private static double distanceSquaredToSegment(Location point, Location from, Location to) {
        double px = point.getX();
        double py = point.getY();
        double pz = point.getZ();
        double ax = from.getX();
        double ay = from.getY();
        double az = from.getZ();
        double bx = to.getX();
        double by = to.getY();
        double bz = to.getZ();

        double dx = bx - ax;
        double dy = by - ay;
        double dz = bz - az;
        double lengthSquared = dx * dx + dy * dy + dz * dz;
        if (lengthSquared <= 0.0) {
            return point.distanceSquared(from);
        }

        double t = ((px - ax) * dx + (py - ay) * dy + (pz - az) * dz) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));

        double closestX = ax + dx * t;
        double closestY = ay + dy * t;
        double closestZ = az + dz * t;

        double diffX = px - closestX;
        double diffY = py - closestY;
        double diffZ = pz - closestZ;
        return diffX * diffX + diffY * diffY + diffZ * diffZ;
    }

    // ======================== PathDisplay ========================

    private static class PathDisplay {
        final Player player;
        final Location machineLoc;
        final List<EnergyPath> paths;
        final EnergyNet net;
        final EnergyNetComponentType type;
        final Set<Location> hologramLocs = new HashSet<>();
        final Set<Location> endpointHologramLocs = new HashSet<>();
        final List<List<Location>> fullPaths = new ArrayList<>();
        final Map<String, Set<Location>> segmentConsumers = new LinkedHashMap<>();
        final Map<Location, Color> consumerColors;
        int particleTaskId = -1;
        int hologramTaskId = -1;
        int secondsLeft = DISPLAY_SECONDS;

        PathDisplay(
                Player player,
                Location machineLoc,
                List<EnergyPath> paths,
                EnergyNet net,
                EnergyNetComponentType type) {
            this.player = player;
            this.machineLoc = machineLoc;
            this.paths = paths;
            this.net = net;
            this.type = type;

            this.consumerColors = assignConsumerColors(paths, net);

            for (EnergyPath path : paths) {
                List<Location> fullPath = new ArrayList<>();
                fullPath.add(path.getSource());
                fullPath.addAll(path.getConnectors());
                fullPath.add(path.getConsumer());
                fullPaths.add(fullPath);

                for (int i = 0; i < fullPath.size() - 1; i++) {
                    String key = segmentKey(fullPath.get(i), fullPath.get(i + 1));
                    segmentConsumers.computeIfAbsent(key, k -> new HashSet<>()).add(path.getConsumer());
                }
            }
        }

        void start() {
            spawnParticles();
            createHolograms();
            particleTaskId = Bukkit.getScheduler()
                    .runTaskTimer(Slimefun.instance(), this::spawnParticles, 20L, 20L)
                    .getTaskId();
            hologramTaskId = Bukkit.getScheduler()
                    .runTaskTimer(
                            Slimefun.instance(),
                            () -> {
                                secondsLeft--;
                                updateHolograms();
                                if (secondsLeft <= 0) {
                                    cancel();
                                    removeFromMap();
                                }
                            },
                            20L,
                            20L)
                    .getTaskId();
        }

        void cancel() {
            if (particleTaskId != -1) {
                Bukkit.getScheduler().cancelTask(particleTaskId);
                particleTaskId = -1;
            }
            if (hologramTaskId != -1) {
                Bukkit.getScheduler().cancelTask(hologramTaskId);
                hologramTaskId = -1;
            }
            removeHolograms();
        }

        private void removeFromMap() {
            Map<Location, PathDisplay> map = pathDisplays.get(player.getUniqueId());
            if (map != null) {
                map.remove(machineLoc);
                if (map.isEmpty()) pathDisplays.remove(player.getUniqueId());
            }
        }

        void spawnParticles() {
            Color defaultColor = CONSUMER_COLORS[0];
            for (int pi = 0; pi < paths.size(); pi++) {
                EnergyPath path = paths.get(pi);
                List<Location> fullPath = fullPaths.get(pi);

                for (int i = 0; i < fullPath.size() - 1; i++) {
                    Location from = fullPath.get(i);
                    Location to = fullPath.get(i + 1);

                    if (!hasNearbyPlayerOnSegment(from, to)) continue;

                    String segKey = segmentKey(from, to);
                    Set<Location> consumers = segmentConsumers.get(segKey);
                    Color color = SHARED_COLOR;
                    if (consumers != null && consumers.size() <= 1) {
                        color = consumerColors.getOrDefault(path.getConsumer(), defaultColor);
                    }

                    spawnLineParticles(from, to, color);
                }
            }
        }

        void spawnLineParticles(Location from, Location to, Color color) {
            double dx = to.getX() - from.getX();
            double dy = to.getY() - from.getY();
            double dz = to.getZ() - from.getZ();
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance < 0.1) return;
            int steps = Math.max(1, (int) (distance * PARTICLES_PER_METER));

            double stepX = dx / steps;
            double stepY = dy / steps;
            double stepZ = dz / steps;

            Particle.DustOptions opts = new Particle.DustOptions(color, 1.5F);

            for (int i = 0; i <= steps; i++) {
                double x = from.getX() + 0.5 + stepX * i;
                double y = from.getY() + 0.5 + stepY * i;
                double z = from.getZ() + 0.5 + stepZ * i;
                Location pos = new Location(from.getWorld(), x, y, z);
                if (!hasNearbyPlayer(pos)) continue;
                pos.getWorld().spawnParticle(VersionedParticle.DUST, x, y, z, 1, 0, 0, 0, 1, opts);
            }
        }

        void createHolograms() {
            Set<Location> added = new HashSet<>();
            for (EnergyPath path : paths) {
                addEndpointHologram(path.getSource(), added);
                addEndpointHologram(path.getConsumer(), added);
                for (Location conn : path.getConnectors()) {
                    if (added.add(conn)) {
                        long load = net.getConnectorLoad().getOrDefault(conn, 0L);
                        Location hl = hologramLoc(conn);
                        hologramLocs.add(hl);
                        safeCreateHologram(hl, connectorHologramText(conn, load, false));
                    }
                }
            }
        }

        private void addEndpointHologram(Location loc, Set<Location> added) {
            if (!added.add(loc)) return;
            long charge = 0;
            long capacity = 0;
            EnergyNetComponent comp = net.getGenerators().get(loc);
            if (comp == null) comp = net.getConsumers().get(loc);
            if (comp == null) comp = net.getCapacitors().get(loc);
            if (comp != null) {
                charge = comp.getChargeLong(loc);
                capacity = comp.getCapacityLong();
            }
            Location hl = hologramLoc(loc);
            hologramLocs.add(hl);
            endpointHologramLocs.add(hl);
            if (capacity > 0) {
                safeCreateHologram(hl, "&e总储能: &f" + charge + " &7/ &f" + capacity + " &7J");
            } else {
                safeCreateHologram(hl, "&e总电量: &f" + charge + " &7J");
            }
        }

        void updateHolograms() {
            for (Location hl : endpointHologramLocs) {
                Location blockLoc =
                        hl.clone().subtract(0.5, 0.75, 0.5).getBlock().getLocation();
                EnergyNetComponent comp = net.getGenerators().get(blockLoc);
                if (comp == null) comp = net.getConsumers().get(blockLoc);
                if (comp == null) comp = net.getCapacitors().get(blockLoc);
                if (comp != null) {
                    long charge = comp.getChargeLong(blockLoc);
                    long capacity = comp.getCapacityLong();
                    if (capacity > 0) {
                        safeCreateHologram(hl, "&e总储能: &f" + charge + " &7/ &f" + capacity + " &7J");
                    } else {
                        safeCreateHologram(hl, "&e总电量: &f" + charge + " &7J");
                    }
                }
            }
            Set<Location> connUpdated = new HashSet<>();
            for (EnergyPath path : paths) {
                for (Location conn : path.getConnectors()) {
                    if (!connUpdated.add(conn)) continue;
                    long load = net.getConnectorLoad().getOrDefault(conn, 0L);
                    safeCreateHologram(hologramLoc(conn), connectorHologramText(conn, load, false));
                }
            }
        }

        void removeHolograms() {
            for (Location hl : hologramLocs) {
                safeRemoveHologram(hl);
            }
            hologramLocs.clear();
            endpointHologramLocs.clear();
        }
    }

    // ======================== ConnectorDisplay ========================

    private static class ConnectorDisplay {
        final Player player;
        final Location connLoc;
        final EnergyNet net;
        final Location hologramLoc;
        int taskId = -1;
        int secondsLeft = DISPLAY_SECONDS;

        ConnectorDisplay(Player player, Location connLoc, EnergyNet net) {
            this.player = player;
            this.connLoc = connLoc;
            this.net = net;
            this.hologramLoc = hologramLoc(connLoc);
        }

        void start() {
            updateHologram();
            taskId = Bukkit.getScheduler()
                    .runTaskTimer(
                            Slimefun.instance(),
                            () -> {
                                secondsLeft--;
                                updateHologram();
                                if (secondsLeft <= 0) {
                                    cancel();
                                    removeFromMap();
                                }
                            },
                            20L,
                            20L)
                    .getTaskId();
        }

        void resetTimer() {
            secondsLeft = DISPLAY_SECONDS;
            updateHologram();
        }

        void cancel() {
            if (taskId != -1) {
                Bukkit.getScheduler().cancelTask(taskId);
                taskId = -1;
            }
            safeRemoveHologram(hologramLoc);
        }

        private void removeFromMap() {
            Map<Location, ConnectorDisplay> map = connectorDisplays.get(player.getUniqueId());
            if (map != null) {
                map.remove(connLoc);
                if (map.isEmpty()) connectorDisplays.remove(player.getUniqueId());
            }
        }

        void updateHologram() {
            long load = net.getConnectorLoad().getOrDefault(connLoc, 0L);
            safeCreateHologram(hologramLoc, connectorHologramText(connLoc, load, true));
        }
    }

    // ======================== Utility ========================

    private static Map<Location, Color> assignConsumerColors(List<EnergyPath> paths, EnergyNet net) {
        Map<Location, Color> map = new HashMap<>();
        int capIdx = 0;
        int consIdx = 0;
        for (EnergyPath path : paths) {
            Location consumer = path.getConsumer();
            if (!map.containsKey(consumer)) {
                if (net.getCapacitors().containsKey(consumer)) {
                    map.put(consumer, CAPACITOR_COLORS[capIdx++ % CAPACITOR_COLORS.length]);
                } else {
                    map.put(consumer, CONSUMER_COLORS[consIdx++ % CONSUMER_COLORS.length]);
                }
            }
        }
        return map;
    }

    private static String segmentKey(Location from, Location to) {
        return from.getBlockX() + "," + from.getBlockY() + "," + from.getBlockZ() + "->" + to.getBlockX() + ","
                + to.getBlockY() + "," + to.getBlockZ();
    }

    private static String connectorHologramText(Location conn, long load, boolean showTimer) {
        SlimefunItem sfItem = com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils.getSlimefunItem(conn);
        boolean isCapacitor = sfItem instanceof EnergyNetComponent ec
                && ec.getEnergyComponentType() == EnergyNetComponentType.CAPACITOR;
        if (isCapacitor) {
            return ChatColors.color("&e负载: &f" + load + " &7J  &b[电容桥接]");
        }
        float durability = ConnectorAgingManager.getDurability(conn);
        String statusColor = ConnectorAgingManager.getStatusColor(durability);
        String statusText = ConnectorAgingManager.getStatusText(durability);
        return ChatColors.color("&e负载: &f" + load + " &7J  " + statusColor + statusText);
    }
}
