package io.github.thebusybiscuit.slimefun4.integrations;

import com.djrapitops.plan.extension.CallEvents;
import com.djrapitops.plan.extension.DataExtension;
import com.djrapitops.plan.extension.NotReadyException;
import com.djrapitops.plan.extension.annotation.NumberProvider;
import com.djrapitops.plan.extension.annotation.PercentageProvider;
import com.djrapitops.plan.extension.annotation.PluginInfo;
import com.djrapitops.plan.extension.annotation.Tab;
import com.djrapitops.plan.extension.annotation.TabInfo;
import com.djrapitops.plan.extension.icon.Color;
import com.djrapitops.plan.extension.icon.Family;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

/*
 * Plan（Player Analytics）数据扩展：向Plan统计面板提供粘液科技(Slimefun)的玩家研究进度与GPS网络数据喵
 * 整体思路：Plan会在玩家加入/离开时调用下面带Provider注解的方法，方法内部直接读取Slimefun已缓存在内存中的PlayerProfile喵
 * 输入：由Plan框架自动传入的玩家UUID（部分方法无参数，代表服务器整体数据）
 * 输出：long（数量类）或double（0.0~1.0的百分比）
 * 边界条件：玩家档案尚未加载到内存时（PlayerProfile.find返回empty），抛出NotReadyException让Plan静默跳过本次采集，不会报错喵
 */
// 声明这是名为"Slimefun"的插件扩展，图标使用flask（烧杯），颜色为绿色，贴合粘液科技的主题喵
@PluginInfo(name = "Slimefun", iconName = "flask", iconFamily = Family.SOLID, color = Color.GREEN)
// "研究"标签页使用书本图标喵
@TabInfo(
        tab = "研究",
        iconName = "book",
        iconFamily = Family.SOLID,
        elementOrder = {})
// "GPS"标签页使用定位图标喵
@TabInfo(
        tab = "GPS",
        iconName = "satellite-dish",
        iconFamily = Family.SOLID,
        elementOrder = {})
public class SlimefunDataExtension implements DataExtension {

    // 获取玩家能源统计服务，Plan provider 只读取不可变快照喵
    private PlayerEnergyStatisticsService energyStatisticsService() {
        return Slimefun.getPlayerEnergyStatisticsService();
    }

    // 根据 Plan 传入的玩家 UUID 读取能源快照，不访问 EnergyNet 或 Bukkit 网络对象喵
    private PlayerEnergyStatisticsService.PlayerEnergySnapshot energySnapshot(UUID playerUUID) {
        // 喵~防御：Plan 不应传入 null UUID，异常输入统一返回零快照喵
        if (playerUUID == null) {
            return PlayerEnergyStatisticsService.PlayerEnergySnapshot.ZERO;
        }
        return energyStatisticsService().getSnapshot(playerUUID);
    }

    /*
     * 指定Plan在哪些事件发生时自动调用本扩展的Provider方法喵
     * PLAYER_JOIN/PLAYER_LEAVE：玩家上下线时；SERVER_EXTENSION_REGISTER：插件启动注册时（用于拉取服务器整体研究总数）喵
     * SERVER_PERIODICAL：定期重新采集服务器研究总数，因为SlimefunCustomGuide在启动后可能修改研究注册表，导致注册时采集到的数据过期喵
     */
    @Override
    public CallEvents[] callExtensionMethodsOn() {
        return new CallEvents[] {
            CallEvents.PLAYER_JOIN,
            CallEvents.PLAYER_LEAVE,
            CallEvents.PLAYER_PERIODICAL,
            CallEvents.SERVER_EXTENSION_REGISTER,
            CallEvents.SERVER_PERIODICAL
        };
    }

    /*
     * 根据玩家UUID查找已缓存的Slimefun玩家档案喵
     * 边界条件：档案未加载到内存时（例如异步加载尚未完成）返回empty，调用处需要自行处理喵
     */
    private Optional<PlayerProfile> findProfile(UUID playerUUID) {
        // 喵~防御：OfflinePlayer由Bukkit按UUID离线查询获得，即使玩家从未上线过Bukkit也会返回一个占位对象，不会是null喵
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
        return PlayerProfile.find(offlinePlayer);
    }

    /*
     * 已解锁研究数量喵
     * 输入：玩家UUID；输出：该玩家已解锁的Research数量（long）
     * 喵~防御：档案未加载时抛NotReadyException，Plan会静默跳过本次采集而不是报错喵
     */
    @NumberProvider(
            text = "已解锁研究",
            description = "该玩家已解锁的Slimefun研究数量",
            iconName = "flask-vial",
            iconColor = Color.GREEN,
            showInPlayerTable = true)
    @Tab("研究")
    public long unlockedResearches(UUID playerUUID) {
        PlayerProfile profile = findProfile(playerUUID).orElseThrow(NotReadyException::new);
        return profile.getResearches().size();
    }

    /*
     * 研究解锁百分比喵
     * 输入：玩家UUID；输出：0.0~1.0之间的小数，代表已解锁研究占服务器总研究数的比例
     * 边界条件：当服务器总研究数为0时（未加载任何研究配置），直接返回0.0避免除零异常喵
     */
    @PercentageProvider(
            text = "研究解锁进度",
            description = "已解锁研究数占服务器全部研究数的百分比",
            iconName = "chart-pie",
            iconColor = Color.GREEN)
    @Tab("研究")
    public double researchPercentage(UUID playerUUID) {
        PlayerProfile profile = findProfile(playerUUID).orElseThrow(NotReadyException::new);
        int totalResearchCount = Slimefun.getRegistry().getResearches().size();
        // 喵~防御：除零保护，服务器还没有加载任何研究定义时totalResearchCount为0喵
        if (totalResearchCount == 0) {
            return 0.0;
        }
        Set<Research> unlockedResearches = profile.getResearches();
        return unlockedResearches.size() / (double) totalResearchCount;
    }

    /*
     * 已创建背包数量喵
     * 输入：玩家UUID；输出：该玩家已经拥有的Slimefun背包(Backpack)数量
     */
    @NumberProvider(
            text = "背包数量",
            description = "该玩家已创建的Slimefun背包数量",
            iconName = "briefcase",
            iconColor = Color.BROWN,
            showInPlayerTable = true)
    public long backpackCount(UUID playerUUID) {
        PlayerProfile profile = findProfile(playerUUID).orElseThrow(NotReadyException::new);
        return profile.getBackpackCount();
    }

    /*
     * GPS路径点数量喵
     * 输入：玩家UUID；输出：该玩家保存的GPS路径点(Waypoint)总数
     */
    @NumberProvider(text = "路径点数量", description = "该玩家保存的GPS路径点数量", iconName = "map-pin", iconColor = Color.LIGHT_BLUE)
    @Tab("GPS")
    public long waypointCount(UUID playerUUID) {
        PlayerProfile profile = findProfile(playerUUID).orElseThrow(NotReadyException::new);
        return profile.getWaypoints().size();
    }

    /*
     * GPS网络复杂度喵
     * 输入：玩家UUID；输出：该玩家的GPS网络复杂度数值（用于判断GPS发射器/接收器的搭建规模）
     */
    @NumberProvider(
            text = "GPS网络复杂度",
            description = "该玩家的GPS发射网络复杂度，数值越高代表搭建的传输网络越庞大",
            iconName = "satellite-dish",
            iconColor = Color.LIGHT_BLUE)
    @Tab("GPS")
    public long gpsComplexity(UUID playerUUID) {
        return Slimefun.getGPSNetwork().getNetworkComplexity(playerUUID);
    }

    /*
     * 服务器总研究数量喵（服务器整体数据，不需要玩家参数）
     * 输入：无；输出：服务器当前已注册的Research总数
     */
    @NumberProvider(text = "服务器研究总数", description = "该服务器已注册的Slimefun研究总数", iconName = "book", iconColor = Color.GREEN)
    @Tab("研究")
    public long totalResearches() {
        return Slimefun.getRegistry().getResearches().size();
    }

    // 返回玩家功能上线后的累计发电量，单位：Slimefun 能量单位喵
    @NumberProvider(
            text = "累计发电量",
            description = "该玩家自能源统计功能启用后累计产生的能量",
            iconName = "bolt",
            iconColor = Color.YELLOW,
            showInPlayerTable = true)
    public long totalEnergyProduced(UUID playerUUID) {
        return energySnapshot(playerUUID).getTotalProduced();
    }

    // 返回最近一次完整 EnergyNet tick 的发电量，单位：Slimefun 能量单位喵
    @NumberProvider(text = "实时发电量", description = "该玩家最近一次完整电网周期实际产生的能量", iconName = "bolt", iconColor = Color.ORANGE)
    public long currentEnergyProduced(UUID playerUUID) {
        return energySnapshot(playerUUID).getCurrentProduced();
    }

    // 返回玩家功能上线后的累计耗电量，单位：Slimefun 能量单位喵
    @NumberProvider(
            text = "累计耗电量",
            description = "该玩家自能源统计功能启用后累计消耗的能量",
            iconName = "plug",
            iconColor = Color.RED,
            showInPlayerTable = true)
    public long totalEnergyConsumed(UUID playerUUID) {
        return energySnapshot(playerUUID).getTotalConsumed();
    }

    // 返回最近一次完整 EnergyNet tick 的耗电量，单位：Slimefun 能量单位喵
    @NumberProvider(text = "实时耗电量", description = "该玩家最近一次完整电网周期实际消耗的能量", iconName = "plug", iconColor = Color.RED)
    public long currentEnergyConsumed(UUID playerUUID) {
        return energySnapshot(playerUUID).getCurrentConsumed();
    }
}
