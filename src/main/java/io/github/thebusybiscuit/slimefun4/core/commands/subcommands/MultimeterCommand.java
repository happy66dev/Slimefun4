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
package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.ConnectorAgingManager;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet.EnergyPath;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.MultimeterDisplayManager;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

class MultimeterCommand extends SubCommand {

    protected MultimeterCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "multimeter", false);
    }

    @Override
    protected String getDescription() {
        return "commands.multimeter.description";
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof Player p)) {
            Slimefun.getLocalization().sendMessage(sender, "commands.multimeter.player-only", true);
            return;
        }

        if (!sender.hasPermission("slimefun.command.multimeter")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        boolean showDisplay = false;
        for (String arg : args) {
            if (arg.equalsIgnoreCase("-p") || arg.equalsIgnoreCase("--particle")) {
                showDisplay = true;
                break;
            }
        }

        Block target = p.getTargetBlockExact(10);
        if (target == null) {
            Slimefun.getLocalization().sendMessage(sender, "commands.multimeter.no-target", true);
            return;
        }

        Location loc = target.getLocation();
        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(loc);

        if (sfItem == null) {
            Slimefun.getLocalization().sendMessage(sender, "commands.multimeter.no-slimefun", true);
            return;
        }

        if (!(sfItem instanceof EnergyNetComponent component)) {
            Slimefun.getLocalization().sendMessage(sender, "commands.multimeter.not-component", true);
            return;
        }

        String info = buildComponentInfo(p, loc, component);
        sender.sendMessage(ChatColors.color(info));

        if (showDisplay) {
            handleDisplay(p, loc, component);
        }
    }

    private void handleDisplay(Player p, Location loc, EnergyNetComponent component) {
        EnergyNetComponentType type = component.getEnergyComponentType();

        if (type == EnergyNetComponentType.CONNECTOR) {
            EnergyNet net = EnergyNet.getNetworkFromLocation(loc);
            if (net != null) {
                MultimeterDisplayManager.showConnectorLoad(p, loc, net);
                Slimefun.getLocalization().sendMessage(p, "commands.multimeter.connector-load-shown", true);
            } else {
                Slimefun.getLocalization().sendMessage(p, "commands.multimeter.no-network", true);
            }
            return;
        }

        EnergyNet net = EnergyNet.getNetworkFromLocation(loc);
        if (net == null) {
            Slimefun.getLocalization().sendMessage(p, "commands.multimeter.no-network", true);
            return;
        }

        List<EnergyPath> paths = collectPaths(net, loc, type);
        if (paths.isEmpty()) {
            Slimefun.getLocalization().sendMessage(p, "commands.multimeter.no-paths", true);
            return;
        }

        boolean activated = MultimeterDisplayManager.togglePathDisplay(p, loc, paths, net, type);
        if (activated) {
            Slimefun.getLocalization().sendMessage(p, "commands.multimeter.paths-shown", true);
            Slimefun.getLocalization().sendMessage(p, "commands.multimeter.paths-legend", true);
        } else {
            Slimefun.getLocalization().sendMessage(p, "commands.multimeter.paths-hidden", true);
        }
    }

    private String buildComponentInfo(Player p, Location loc, EnergyNetComponent component) {
        StringBuilder sb = new StringBuilder();
        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(loc);
        String itemName = sfItem != null ? sfItem.getItemName() : "未知";

        sb.append("\n")
                .append(Slimefun.getLocalization().getMessage(p, "commands.multimeter.inspecting"))
                .append("\n");
        sb.append("&7物品: &f").append(itemName).append("\n");
        sb.append("&7类型: &f").append(component.getEnergyComponentType()).append("\n");
        sb.append("&7位置: &f").append(EnergyNet.formatLocation(loc)).append("\n");

        long charge = component.getChargeLong(loc);
        long capacity = component.getCapacityLong();

        if (capacity > 0) {
            double pct = (double) charge / capacity * 100;
            sb.append("&7电量: &f")
                    .append(charge)
                    .append(" &7/ &f")
                    .append(capacity)
                    .append(" &7J ")
                    .append(String.format("&e(%.1f%%)", pct))
                    .append("\n");
        } else {
            sb.append("&7电量: &f").append(charge).append(" &7J &e(不可储电)\n");
        }

        EnergyNet net = EnergyNet.getNetworkFromLocation(loc);
        if (net == null) {
            sb.append("&7电网: &c")
                    .append(Slimefun.getLocalization().getMessage(p, "commands.multimeter.no-network"))
                    .append("\n");
            // 离网连接器仍可从方块数据读取老化状态，不能因缺少电网而省略喵~
            if (component.getEnergyComponentType() == EnergyNetComponentType.CONNECTOR) {
                // 读取连接器当前耐久度，用于复用联网连接器的状态分段逻辑喵~
                float durability = ConnectorAgingManager.getDurability(loc);
                // 根据耐久度取得对应的状态颜色，保持查询结果与联网连接器一致喵~
                String statusColor = ConnectorAgingManager.getStatusColor(durability);
                // 根据耐久度取得模糊老化状态文字，避免暴露精确耐久百分比喵~
                String statusText = ConnectorAgingManager.getStatusText(durability);
                // 输出离网连接器的模糊老化状态，不伪造负载、路径或剩余吞吐数据喵~
                sb.append("&7状态: ").append(statusColor).append(statusText).append("\n");
            }
            return sb.toString();
        }
        sb.append("&7电网调节器: &f")
                .append(EnergyNet.formatLocation(net.getRegulator()))
                .append("\n");
        sb.append("&7电网状态: &f")
                .append(net.isInitializing() ? "初始化中" : net.isInitialized() ? "已就绪" : "未初始化")
                .append("\n");

        EnergyNetComponentType type = component.getEnergyComponentType();
        switch (type) {
            case GENERATOR -> appendGeneratorInfo(sb, net, loc, component);
            case CONSUMER -> appendConsumerInfo(sb, net, loc);
            case CONNECTOR -> appendConnectorInfo(sb, net, loc, component);
            case CAPACITOR -> appendCapacitorInfo(sb, net, loc);
            default -> {}
        }

        return sb.toString();
    }

    private void appendGeneratorInfo(StringBuilder sb, EnergyNet net, Location loc, EnergyNetComponent component) {
        if (component instanceof EnergyNetProvider provider) {
            if (!provider.isChargeable()) {
                sb.append("&7发电类型: &e持续供电 (太阳能/不可储电)\n");
            }
        }

        Set<EnergyPath> paths = net.getGeneratorPaths().get(loc);
        if (paths != null && !paths.isEmpty()) {
            sb.append("&b▼ 输电路径 (").append(paths.size()).append(" 条)\n");
            appendPathsSummary(sb, paths, false);
        } else {
            sb.append("&7输电路径: &e无\n");
        }

        Map<Location, Set<EnergyPath>> capPaths =
                net.getGeneratorToCapacitorPaths().get(loc);
        if (capPaths != null && !capPaths.isEmpty()) {
            int totalCapPaths = 0;
            for (Set<EnergyPath> pathSet : capPaths.values()) {
                totalCapPaths += pathSet.size();
            }
            sb.append("&b▼ 电容充电路径 (").append(totalCapPaths).append(" 条)\n");
            for (Set<EnergyPath> pathSet : capPaths.values()) {
                for (EnergyPath p : pathSet) {
                    sb.append("  &7[电容] &f").append(EnergyNet.formatLocation(p.getConsumer()));
                    sb.append(" &7(跳数: ").append(p.getLength()).append(")\n");
                }
            }
        }
    }

    private void appendConsumerInfo(StringBuilder sb, EnergyNet net, Location loc) {
        List<EnergyPath> reachingPaths = new ArrayList<>();
        for (Set<EnergyPath> paths : net.getGeneratorPaths().values()) {
            for (EnergyPath p : paths) {
                if (p.getConsumer().equals(loc)) reachingPaths.add(p);
            }
        }
        for (Set<EnergyPath> paths : net.getCapacitorPaths().values()) {
            for (EnergyPath p : paths) {
                if (p.getConsumer().equals(loc)) reachingPaths.add(p);
            }
        }

        if (reachingPaths.isEmpty()) {
            sb.append("&7供电路径: &e无\n");
        } else {
            sb.append("&b▼ 供电路径 (").append(reachingPaths.size()).append(" 条)\n");
            appendPathsSummary(sb, reachingPaths, true);
        }
    }

    private void appendConnectorInfo(StringBuilder sb, EnergyNet net, Location loc, EnergyNetComponent component) {
        sb.append("&7连接范围: &f").append(component.getRange()).append(" 格\n");
        long load = net.getConnectorLoad().getOrDefault(loc, 0L);
        sb.append("&7本刻负载: &f").append(load).append(" J\n");

        float durability = ConnectorAgingManager.getDurability(loc);
        String statusColor = ConnectorAgingManager.getStatusColor(durability);
        String statusText = ConnectorAgingManager.getStatusText(durability);
        sb.append("&7耐久: ")
                .append(statusColor)
                .append(String.format("%.1f", durability * 100))
                .append("% &7(")
                .append(statusText)
                .append(")\n");
        long remaining = ConnectorAgingManager.getRemainingJoules(loc);
        if (remaining > 0) {
            sb.append("&7剩余吞吐: &f")
                    .append(ConnectorAgingManager.formatJoules(remaining))
                    .append(" &7J\n");
        }

        int connectedCount = 0;
        for (Set<EnergyPath> paths : net.getGeneratorPaths().values()) {
            for (EnergyPath p : paths) {
                if (p.getConnectors().contains(loc)) connectedCount++;
            }
        }
        for (Set<EnergyPath> paths : net.getCapacitorPaths().values()) {
            for (EnergyPath p : paths) {
                if (p.getConnectors().contains(loc)) connectedCount++;
            }
        }
        sb.append("&7参与路径: &f").append(connectedCount).append(" 条\n");
    }

    private void appendCapacitorInfo(StringBuilder sb, EnergyNet net, Location loc) {
        EnergyNetComponent comp = net.getCapacitors().get(loc);
        if (comp != null) {
            long charge = comp.getChargeLong(loc);
            long capacity = comp.getCapacityLong();
            if (capacity > 0) {
                double pct = (double) charge / capacity * 100;
                sb.append("&7储能: &f")
                        .append(charge)
                        .append(" &7/ &f")
                        .append(capacity)
                        .append(" &7J ")
                        .append(String.format("&e(%.1f%%)", pct))
                        .append("\n");
            }
        }

        Set<EnergyPath> paths = net.getCapacitorPaths().get(loc);
        if (paths != null && !paths.isEmpty()) {
            sb.append("&b▼ 供电路径 (").append(paths.size()).append(" 条)\n");
            appendPathsSummary(sb, paths, false);
        }

        sb.append("&b▼ 桥接电容\n");
        boolean hasBridge = false;
        for (Location capLoc : net.getCapacitors().keySet()) {
            if (!capLoc.equals(loc) && isAdjacent(loc, capLoc)) {
                sb.append("  &7").append(EnergyNet.formatLocation(capLoc)).append(" &f(相邻)\n");
                hasBridge = true;
            }
        }
        if (!hasBridge) {
            sb.append("  &7无相邻桥接电容\n");
        }
    }

    private void appendPathsSummary(StringBuilder sb, Collection<EnergyPath> paths, boolean showSource) {
        for (EnergyPath p : paths) {
            sb.append("  &a");
            if (showSource) {
                sb.append(EnergyNet.formatLocation(p.getSource()));
            } else {
                sb.append(EnergyNet.formatLocation(p.getConsumer()));
            }
            sb.append(" &7(跳数: ").append(p.getLength()).append(")");
            sb.append(" &7路径: &e");
            boolean first = true;
            for (Location conn : p.getConnectors()) {
                if (!first) sb.append(" &7→ &e");
                sb.append("(")
                        .append(conn.getBlockX())
                        .append(", ")
                        .append(conn.getBlockY())
                        .append(", ")
                        .append(conn.getBlockZ())
                        .append(")");
                first = false;
            }
            sb.append("\n");
        }
    }

    private List<EnergyPath> collectPaths(EnergyNet net, Location loc, EnergyNetComponentType type) {
        Set<EnergyPath> result = new java.util.LinkedHashSet<>();
        switch (type) {
            case GENERATOR -> {
                Set<EnergyPath> paths = net.getGeneratorPaths().get(loc);
                if (paths != null) result.addAll(paths);
                Map<Location, Set<EnergyPath>> capPaths =
                        net.getGeneratorToCapacitorPaths().get(loc);
                if (capPaths != null) {
                    for (Set<EnergyPath> pathSet : capPaths.values()) {
                        result.addAll(pathSet);
                    }
                }
            }
            case CAPACITOR -> {
                Set<EnergyPath> paths = net.getCapacitorPaths().get(loc);
                if (paths != null) result.addAll(paths);
            }
            case CONSUMER -> {
                for (Set<EnergyPath> paths : net.getGeneratorPaths().values())
                    for (EnergyPath p : paths) if (p.getConsumer().equals(loc)) result.add(p);
                for (Set<EnergyPath> paths : net.getCapacitorPaths().values())
                    for (EnergyPath p : paths) if (p.getConsumer().equals(loc)) result.add(p);
            }
            case CONNECTOR -> {
                for (Set<EnergyPath> paths : net.getGeneratorPaths().values())
                    for (EnergyPath p : paths) if (p.getConnectors().contains(loc)) result.add(p);
                for (Set<EnergyPath> paths : net.getCapacitorPaths().values())
                    for (EnergyPath p : paths) if (p.getConnectors().contains(loc)) result.add(p);
            }
            default -> {}
        }
        return new ArrayList<>(result);
    }

    private static boolean isAdjacent(Location loc1, Location loc2) {
        return Math.abs(loc1.getBlockX() - loc2.getBlockX())
                        + Math.abs(loc1.getBlockY() - loc2.getBlockY())
                        + Math.abs(loc1.getBlockZ() - loc2.getBlockZ())
                == 1;
    }
}
