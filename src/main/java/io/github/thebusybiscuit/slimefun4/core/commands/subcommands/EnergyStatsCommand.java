package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetProvider;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.integrations.PlayerEnergyStatisticsService;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 输出 Plan 玩家能源统计的内部状态，便于排查报告数值为 0 的原因。
 * <p>
 * 命令会依次检查三件事：统计服务是否记录到数据、当前玩家的四项快照数值、
 * 以及玩家附近的发电机与用电器是否写入了 machine_owner_uuid 归属字段。
 * 缺少归属字段的机器无法计入任何玩家的统计，这是数值为 0 最常见的原因。
 *
 * @author happy
 */
public class EnergyStatsCommand extends SubCommand {

    // 附近机器扫描半径，单位：方块。半径过大会造成明显卡顿，这里保守取值喵
    private static final int SCAN_RADIUS = 12;

    protected EnergyStatsCommand(@Nonnull Slimefun plugin, @Nonnull SlimefunCommand cmd) {
        super(plugin, cmd, "energystats", false);
    }

    @Override
    protected @Nonnull String getDescription() {
        return "commands.energystats.description";
    }

    @Override
    public void onExecute(@Nonnull CommandSender sender, @Nonnull String[] args) {
        // 喵~防御：复用调试权限，避免普通玩家读取其他玩家的能源统计喵
        if (!sender.hasPermission("slimefun.command.debug")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        // 喵~防御：附近机器扫描需要玩家坐标，控制台执行时只能输出服务状态喵
        boolean isPlayer = sender instanceof Player;

        // 读取统计服务单例，服务在 onEnable 阶段创建所以不会为 null 喵
        PlayerEnergyStatisticsService service = Slimefun.getPlayerEnergyStatisticsService();

        // 输出统计服务整体状态标题喵
        sender.sendMessage(ChatColors.color("&a===== Plan 能源统计服务状态 ====="));
        // 输出当前已经跟踪到统计数据的玩家数量，为 0 说明从未成功记录过任何能源喵
        sender.sendMessage(ChatColors.color("&7已跟踪玩家数: &e" + service.getTrackedPlayerCount()));
        // 输出统计文件是否已经落盘，首次启用后一分钟内可能还没有文件喵
        sender.sendMessage(ChatColors.color("&7统计文件: &e" + service.getStatisticsFilePath() + " &7(存在: &e"
                + service.isStatisticsFilePresent() + "&7)"));

        // 输出统计起始时间，用于确认累计值覆盖的时间范围喵
        long startedAt = service.getStartedAt();
        if (startedAt > 0L) {
            // 将毫秒时间戳格式化为可读时间喵
            String startedAtText = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(startedAt));
            sender.sendMessage(ChatColors.color("&7统计开始时间: &e" + startedAtText));
        } else {
            // 喵~防御：起始时间为 0 说明 load 尚未执行完成喵
            sender.sendMessage(ChatColors.color("&c统计开始时间未初始化，load 可能未执行喵~"));
        }

        // 控制台执行时到此结束，后续步骤都需要玩家坐标喵
        if (!isPlayer) {
            sender.sendMessage(ChatColors.color("&7控制台无法扫描附近机器，请由玩家执行本命令喵~"));
            return;
        }

        // 转换为玩家对象，用于读取 UUID 和坐标喵
        Player player = (Player) sender;

        // 读取当前玩家的四项统计快照喵
        PlayerEnergyStatisticsService.PlayerEnergySnapshot snapshot = service.getSnapshot(player.getUniqueId());
        // 输出玩家统计快照标题喵
        sender.sendMessage(ChatColors.color("&a===== 你的能源统计快照 ====="));
        // 输出累计发电量，单位：Slimefun 能量单位喵
        sender.sendMessage(ChatColors.color("&7累计发电量: &e" + snapshot.getTotalProduced() + "J"));
        // 输出最近一次电网周期的发电量喵
        sender.sendMessage(ChatColors.color("&7实时发电量: &e" + snapshot.getCurrentProduced() + "J"));
        // 输出累计耗电量，单位：Slimefun 能量单位喵
        sender.sendMessage(ChatColors.color("&7累计耗电量: &e" + snapshot.getTotalConsumed() + "J"));
        // 输出最近一次电网周期的耗电量喵
        sender.sendMessage(ChatColors.color("&7实时耗电量: &e" + snapshot.getCurrentConsumed() + "J"));

        // 扫描附近机器的归属字段，定位是否因为缺少 owner 导致统计为空喵
        scanNearbyMachines(player);
    }

    /*
     * 扫描玩家附近的发电机与用电器，统计归属字段的完整程度喵
     * 输入：执行命令的玩家；输出：直接向玩家发送扫描结果消息
     * 边界条件：数据尚未从数据库加载的机器单独计数，它们既不算有归属也不算无归属喵
     */
    private void scanNearbyMachines(@Nonnull Player player) {
        // 记录扫描到的发电机总数喵
        int generatorCount = 0;
        // 记录已经写入归属字段的发电机数量喵
        int generatorWithOwnerCount = 0;
        // 记录扫描到的用电器总数喵
        int consumerCount = 0;
        // 记录已经写入归属字段的用电器数量喵
        int consumerWithOwnerCount = 0;
        // 记录数据尚未加载完成的机器数量，这类机器本次无法判断归属喵
        int unloadedCount = 0;
        // 记录归属为当前执行玩家的机器数量喵
        int ownedBySenderCount = 0;

        // 读取玩家当前位置作为扫描中心喵
        Location center = player.getLocation();
        // 保存玩家 UUID 文本，用于比对机器归属喵
        String senderUUIDText = player.getUniqueId().toString();

        /*
         * 主人注意：这里是三重循环，扫描体积为 (2*12+1)^3 = 15625 个方块喵。
         * 每个方块都会查询一次方块数据缓存，作为管理员调试命令偶尔执行没问题，
         * 但不要把这段逻辑放进定时任务或事件监听里，否则会造成明显卡顿喵~
         */
        for (int offsetX = -SCAN_RADIUS; offsetX <= SCAN_RADIUS; offsetX++) {
            for (int offsetY = -SCAN_RADIUS; offsetY <= SCAN_RADIUS; offsetY++) {
                for (int offsetZ = -SCAN_RADIUS; offsetZ <= SCAN_RADIUS; offsetZ++) {
                    // 计算当前扫描坐标喵
                    Location scanLocation = center.clone().add(offsetX, offsetY, offsetZ);

                    // 读取该坐标的 Slimefun 物品定义，非 Slimefun 方块返回 null 喵
                    SlimefunItem item = StorageCacheUtils.getSlimefunItem(scanLocation);
                    // 喵~防御：普通方块和空气直接跳过喵
                    if (item == null) {
                        continue;
                    }

                    // 判断是否为发电机或用电器，其他 Slimefun 方块不参与能源统计喵
                    boolean isGenerator = item instanceof EnergyNetProvider;
                    boolean isConsumer = !isGenerator && item instanceof EnergyNetComponent;
                    // 喵~防御：与能源网络无关的方块跳过喵
                    if (!isGenerator && !isConsumer) {
                        continue;
                    }

                    // 累计对应类别的机器总数喵
                    if (isGenerator) {
                        generatorCount++;
                    } else {
                        consumerCount++;
                    }

                    // 读取方块数据容器，用于检查归属字段喵
                    var data = StorageCacheUtils.getDataContainer(scanLocation);
                    // 喵~防御：数据容器缺失时无法判断归属，按未加载处理喵
                    if (data == null) {
                        unloadedCount++;
                        continue;
                    }
                    // 喵~防御：数据尚未从数据库加载完成时禁止调用 getData，否则会抛异常喵
                    if (!data.isDataLoaded()) {
                        unloadedCount++;
                        continue;
                    }

                    // 读取放置人 UUID 字段，旧机器可能完全没有这个字段喵
                    String ownerUUIDText = data.getData("machine_owner_uuid");
                    // 喵~防御：字段缺失或为空白时该机器无法计入任何玩家统计喵
                    if (ownerUUIDText == null || ownerUUIDText.isBlank()) {
                        continue;
                    }

                    // 累计已经写入归属字段的机器数量喵
                    if (isGenerator) {
                        generatorWithOwnerCount++;
                    } else {
                        consumerWithOwnerCount++;
                    }

                    // 统计归属为当前玩家的机器数量喵
                    if (senderUUIDText.equals(ownerUUIDText)) {
                        ownedBySenderCount++;
                    }
                }
            }
        }

        // 输出附近机器扫描结果标题喵
        sender().sendMessage(player, ChatColors.color("&a===== 附近 " + SCAN_RADIUS + " 格机器归属扫描 ====="));
        // 输出发电机归属完整度喵
        player.sendMessage(
                ChatColors.color("&7发电机: &e" + generatorWithOwnerCount + "&7/&e" + generatorCount + " &7有归属"));
        // 输出用电器归属完整度喵
        player.sendMessage(ChatColors.color("&7用电器: &e" + consumerWithOwnerCount + "&7/&e" + consumerCount + " &7有归属"));
        // 输出数据未加载的机器数量喵
        player.sendMessage(ChatColors.color("&7数据未加载: &e" + unloadedCount));
        // 输出归属为当前玩家的机器数量喵
        player.sendMessage(ChatColors.color("&7归属于你: &e" + ownedBySenderCount));

        // 根据扫描结果给出针对性诊断结论喵
        if (generatorCount == 0 && consumerCount == 0) {
            // 附近没有任何能源机器，无法据此判断统计是否正常喵
            player.sendMessage(ChatColors.color("&e附近没有发现能源机器，请站在电网附近再执行一次喵~"));
        } else if (generatorWithOwnerCount == 0 && consumerWithOwnerCount == 0) {
            // 所有机器都缺少归属字段，说明这些机器是归属功能上线前放置的喵
            player.sendMessage(ChatColors.color("&c附近机器全部缺少 machine_owner_uuid 归属字段喵~"));
            player.sendMessage(ChatColors.color("&c这些机器无法计入任何玩家统计，需要重新放置才会记录喵~"));
        } else if (ownedBySenderCount == 0) {
            // 附近机器有归属但不属于当前玩家，本玩家的统计自然为 0 喵
            player.sendMessage(ChatColors.color("&e附近机器有归属，但没有一台属于你，所以你的统计为 0 喵~"));
        } else {
            // 归属字段正常，统计应该会在电网运行后累积喵
            player.sendMessage(ChatColors.color("&a归属字段正常，电网运行后统计会持续累积喵~"));
        }
    }

    /*
     * 提供统一的消息发送入口，避免标题行与后续行使用不同发送方式喵
     * 输入：无；输出：消息发送器
     */
    private MessageSender sender() {
        // 返回一个简单的转发实现，保持标题与内容发送逻辑一致喵
        return (target, message) -> target.sendMessage(message);
    }

    /*
     * 消息发送函数式接口，仅在本命令内部使用喵
     */
    @FunctionalInterface
    private interface MessageSender {
        // 向指定玩家发送一条已经着色的消息喵
        void sendMessage(@Nonnull Player target, @Nonnull String message);
    }
}
