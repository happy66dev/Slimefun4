package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.medical.MedicalSupplyUseManager;
import javax.annotation.Nonnull;
import org.bukkit.Location;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * 将玩家外部事件转换为医疗用品读条中断请求喵~
 */
public final class MedicalSupplyUseListener implements Listener {

    // 普通跳跃首个移动包的最小向上距离，单位：方块喵~
    private static final double MINIMUM_JUMP_ASCENT = 0.04D;
    // 统一医疗用品会话管理器喵~
    private final MedicalSupplyUseManager useManager;

    /**
     * 创建并注册医疗用品使用中断监听器喵~
     *
     * @param plugin 注册 Bukkit 监听器的插件实例喵~
     * @param useManager 管理医疗用品会话的管理器喵~
     */
    public MedicalSupplyUseListener(@Nonnull Slimefun plugin, @Nonnull MedicalSupplyUseManager useManager) {
        // 喵~防御：插件实例不能为空，否则监听器无法注册喵~
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }

        // 喵~防御：管理器不能为空，否则事件无法安全取消会话喵~
        if (useManager == null) {
            throw new IllegalArgumentException("useManager cannot be null");
        }

        // 保存统一会话管理器供事件回调使用喵~
        this.useManager = useManager;
        // 向 Bukkit 注册本监听器喵~
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 检测普通跳跃并中断正在读条的医疗用品喵~
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        // 取得移动玩家喵~
        Player player = event.getPlayer();
        // 无会话玩家直接返回，避免每次移动执行复杂判断喵~
        if (!useManager.hasActiveSession(player)) {
            return;
        }

        // 船和矿车中的垂直位移不是普通跳跃，明确不打断喵~
        if (isBoatOrMinecart(player.getVehicle())) {
            return;
        }

        // 读取移动起点与终点，终点为 null 时不能判定喵~
        Location from = event.getFrom();
        Location to = event.getTo();
        // 喵~防御：跨世界或缺少位置时不将传送误判为跳跃喵~
        if (to == null || from.getWorld() != to.getWorld()) {
            return;
        }

        // 计算本次移动的垂直增量喵~
        double verticalChange = to.getY() - from.getY();
        // 非明显上升移动不能构成普通跳跃喵~
        if (verticalChange < MINIMUM_JUMP_ASCENT) {
            return;
        }

        // 飞行、鞘翅、游泳和攀爬导致的上升不属于普通跳跃喵~
        if (player.isFlying() || player.isGliding() || player.isSwimming() || player.isClimbing()) {
            return;
        }

        // 离地向上移动时视为普通跳跃，取消读条且不消费物品喵~
        useManager.cancelUse(player, true);
    }

    /**
     * 仅在伤害实际生效时中断医疗用品读条喵~
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        // 非玩家实体的伤害不会影响医疗用品会话喵~
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        // 无会话玩家无需继续判断伤害数值喵~
        if (!useManager.hasActiveSession(player)) {
            return;
        }

        // 零伤害或被护甲完全抵消的事件不算“受到伤害”喵~
        if (event.getFinalDamage() <= 0.0D) {
            return;
        }

        // 实际伤害生效后取消读条，保留物品且不进入冷却喵~
        useManager.cancelUse(player, true);
    }

    /**
     * 玩家退出时取消会话并清理读条缓慢喵~
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // 离线不会显示中断消息，但会释放会话任务与效果喵~
        useManager.cancelUse(event.getPlayer(), false);
    }

    /**
     * 玩家死亡时取消会话，防止同 tick 延迟任务继续结算喵~
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // 死亡不显示中断消息，也不消费医疗用品或设置冷却喵~
        useManager.cancelUse(event.getEntity(), false);
    }

    /**
     * 判断实体是否为允许读条的船或矿车载具喵~
     */
    private boolean isBoatOrMinecart(Entity vehicle) {
        // 船和矿车允许使用，且其中位移不应触发跳跃打断喵~
        return vehicle instanceof Boat || vehicle instanceof Minecart;
    }
}
