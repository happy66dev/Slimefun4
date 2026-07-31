package io.github.thebusybiscuit.slimefun4.integrations;

import com.djrapitops.plan.extension.Caller;
import com.djrapitops.plan.extension.ExtensionService;
import io.github.thebusybiscuit.slimefun4.api.events.AsyncProfileLoadEvent;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/*
 * Plan（Player Analytics）集成入口：负责注册DataExtension并监听Slimefun的档案加载事件，
 * 在玩家档案真正加载完成后触发Plan刷新该玩家的统计数据喵
 *
 * 整体思路：
 * 1. 在服务器启动时向Plan注册SlimefunDataExtension，拿到Caller对象喵
 * 2. 监听Slimefun的AsyncProfileLoadEvent（粘液科技异步加载玩家档案完成）喵
 * 3. 档案加载完成后，通过Caller通知Plan重新采集该玩家的统计数据喵
 *
 * 边界条件：
 * - Plan未安装时，此类根本不会被加载（在IntegrationsManager里判断后才new）喵
 * - Plan已安装但ExtensionService尚未初始化时（IllegalStateException），记录警告后跳过注册喵
 */
class PlanIntegration implements Listener {

    // 保存向Plan注册后得到的Caller对象，用于手动触发数据刷新喵
    private Caller caller;

    /*
     * 向Plan注册SlimefunDataExtension，并把本类作为Bukkit事件监听器注册到服务器喵
     * 输入：Slimefun插件主类实例（用于注册Bukkit事件监听器）
     */
    void register(@Nonnull Slimefun plugin) {
        // 注册事件监听器，使本类的@EventHandler方法生效喵
        Bukkit.getPluginManager().registerEvents(this, plugin);

        try {
            // 向Plan注册数据扩展，拿到可选的Caller（用户在Plan配置里禁用该扩展时，Optional为empty）喵
            Optional<Caller> callerOptional = ExtensionService.getInstance().register(new SlimefunDataExtension());
            callerOptional.ifPresent(c -> {
                this.caller = c;
                Slimefun.logger().log(Level.INFO, "已成功向Plan注册Slimefun数据扩展喵~");
            });
        } catch (NoClassDefFoundError noClassDefFoundError) {
            // 喵~防御：Plan API类找不到，说明Plan版本过旧或者plan-api jar没有加载，静默跳过喵
            Slimefun.logger().log(Level.WARNING, "检测到Plan插件，但API版本不兼容，跳过Slimefun扩展注册喵");
        } catch (IllegalStateException illegalStateException) {
            // 喵~防御：ExtensionService尚未初始化（Plan启动顺序问题），记录警告不崩溃喵
            Slimefun.logger().log(Level.WARNING, "Plan ExtensionService尚未就绪，跳过Slimefun扩展注册喵");
        } catch (IllegalArgumentException illegalArgumentException) {
            // 喵~防御：SlimefunDataExtension有注解配置违规（如字符串超长），记录具体错误方便排查喵
            Slimefun.logger().log(Level.WARNING, illegalArgumentException, () -> "SlimefunDataExtension注册失败，请检查注解配置喵");
        }
    }

    /*
     * 监听粘液科技玩家档案异步加载完成事件喵
     * 触发时机：Slimefun将玩家的研究、背包、路径点数据从数据库读取并存入内存后触发喵
     * 这里调用caller.updatePlayerData()，通知Plan：档案已经准备好，可以重新采集统计数据了喵
     * 边界条件：
     * - caller为null时（Plan未成功注册扩展），静默跳过，不做任何操作喵
     * - 玩家可能已经离线（AsyncProfileLoadEvent不要求玩家在线），getPlayer()可能返回null，
     *   此时用事件直接提供的UUID和PlayerProfile中的getOwner()来获取名字喵
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onProfileLoad(@Nonnull AsyncProfileLoadEvent event) {
        // 喵~防御：caller为null代表Plan扩展注册失败，直接跳过不操作喵
        if (caller == null) {
            return;
        }

        // 拿到档案所属的玩家UUID喵
        java.util.UUID playerUUID = event.getPlayerUUID();

        // 尝试获取在线玩家名字（离线玩家可能为null）喵
        Player onlinePlayer = Bukkit.getPlayer(playerUUID);
        String playerName = onlinePlayer != null
                ? onlinePlayer.getName()
                : event.getProfile().getOwner().getName();

        // 通知Plan该玩家的数据需要重新采集，Plan会异步调用SlimefunDataExtension里的provider方法喵
        caller.updatePlayerData(playerUUID, playerName);
    }
}
