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
import java.util.HashSet;
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
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColors.color("&c只有玩家可以使用此指令"));
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
            sender.sendMessage(ChatColors.color("&c请看向一个方块"));
            return;
        }

        Location loc = target.getLocation();
        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(loc);

        if (sfItem == null) {
            sender.sendMessage(ChatColors.color("&c这个位置没有 Slimefun 物品"));
            return;
        }

        if (!(sfItem instanceof EnergyNetComponent component)) {
            sender.sendMessage(ChatColors.color("&c这个物品不是电网组件"));
            return;
        }

        String info = buildComponentInfo(loc, component);
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
                p.sendMessage(ChatColors.color("&a已显示连接器负载 (15s)"));
            }
            return;
        }

        EnergyNet net = EnergyNet.getNetworkFromLocation(loc);
        if (net == null) return;

        List<EnergyPath> paths = collectPaths(net, loc, type);

        boolean activated = MultimeterDisplayManager.togglePathDisplay(p, loc, paths, net, type);
        if (activated) {
            p.sendMessage(ChatColors.color("&a已开启路径显示 (15s)"));
            p.sendMessage(ChatColors.color("&f● &7白色=共享段 &9● &b淡蓝=电容 &c● &6橙&e●&d粉=用电器"));
        } else {
            p.sendMessage(ChatColors.color("&e已关闭路径显示"));
        }
    }

    private String buildComponentInfo(Location loc, EnergyNetComponent component) {
        StringBuilder sb = new StringBuilder();
        SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(loc);
        String itemName = sfItem != null ? sfItem.getItemName() : "未知";

        sb.append("\n&6=== &e万用表 - 电网设备信息 &6===\n");
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
            sb.append("&7电网: &c不属于任何电网\n");
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
        Set<EnergyPath> result = new HashSet<>();
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
