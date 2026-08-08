package io.github.thebusybiscuit.slimefun4.implementation.items.medical;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ComponentBuilder;
import org.bukkit.Material;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

/**
 * 管理医疗用品读条、打断与玩家共用冷却喵~
 */
public final class MedicalSupplyUseManager {

    // 医疗用品成功后的共用冷却时间，单位：毫秒喵~
    private static final long SHARED_COOLDOWN_MILLIS = 30_000L;
    // 使用进度 Action Bar 的刷新间隔，单位：tick喵~
    private static final long PROGRESS_UPDATE_INTERVAL_TICKS = 10L;
    // Bukkit amplifier 从零开始，数值六会向玩家显示“缓慢 VII”喵~
    private static final int SLOWNESS_AMPLIFIER = 6;
    // 缓慢效果的额外保护时间，单位：tick喵~
    private static final int SLOWNESS_PADDING_TICKS = 10;

    // 插件实例用于创建同步 Bukkit 任务喵~
    private final Slimefun plugin;
    // 正在使用医疗用品的玩家会话，键为玩家 UUID喵~
    private final Map<UUID, UseSession> activeSessions = new HashMap<>();
    // 医疗用品共用冷却结束时间，键为玩家 UUID、值为 Unix 毫秒时间戳喵~
    private final Map<UUID, Long> cooldownEndTimes = new HashMap<>();
    // 单调递增 token 用于让旧任务无法完成新会话喵~
    private long nextSessionToken;

    /**
     * 创建医疗用品使用管理器喵~
     *
     * @param plugin 用于同步调度任务的 Slimefun 插件实例喵~
     */
    public MedicalSupplyUseManager(@Nonnull Slimefun plugin) {
        // 喵~防御：插件实例不能为空，否则无法安全访问 Bukkit 调度器喵~
        if (plugin == null) {
            throw new IllegalArgumentException("plugin cannot be null");
        }

        // 保存插件实例供本管理器的全部同步任务使用喵~
        this.plugin = plugin;
    }

    /**
     * 开始一次医疗用品读条，或拒绝本次右键使用喵~
     *
     * @param event 当前的 Slimefun 右键事件喵~
     * @param supply 被使用的医疗用品喵~
     */
    public void startUse(@Nonnull PlayerRightClickEvent event, @Nonnull MedicalSupply supply) {
        // 喵~防御：事件或医疗用品为空时不能创建会话，直接返回避免空指针喵~
        if (event == null || supply == null) {
            return;
        }

        // 取得实际发起右键的玩家喵~
        Player player = event.getPlayer();
        // 拒绝原版交互和药水饮用，后续所有医疗效果都必须在读条完成后执行喵~
        event.cancel();

        // 喵~防御：离线、死亡或无效玩家不能开始会话喵~
        if (!isValidPlayer(player)) {
            return;
        }

        // 取得玩家的唯一标识，用于会话和冷却索引喵~
        UUID playerId = player.getUniqueId();

        // 已有会话时不允许双手事件或连续右键创建第二个读条喵~
        if (activeSessions.containsKey(playerId)) {
            sendActionBar(player, "actionbar.medical-supply.already-using");
            return;
        }

        // 读取并清理可能已经过期的冷却记录喵~
        long remainingCooldownMillis = getRemainingCooldownMillis(playerId);
        // 冷却未结束时显示剩余时间并拒绝开始喵~
        if (remainingCooldownMillis > 0L) {
            sendActionBar(
                    player, "actionbar.medical-supply.cooldown", "%remaining%", formatSeconds(remainingCooldownMillis));
            return;
        }

        // 仅船和矿车允许在载具中读条，其他载具一律拒绝喵~
        if (!isAllowedVehicle(player.getVehicle())) {
            sendActionBar(player, "actionbar.medical-supply.vehicle-restricted");
            return;
        }

        // 读取本次右键实际使用的手部喵~
        EquipmentSlot hand = event.getHand();
        // 喵~防御：医疗用品只支持主手和副手，未知槽位不能安全消费喵~
        if (hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND) {
            return;
        }

        // 从玩家实际手部重新获取物品，不能信任事件中的可变堆栈引用喵~
        ItemStack handItem = getHandItem(player, hand);
        // 物品必须仍是本次医疗用品且至少有一个，防止异步背包修改导致错误会话喵~
        if (!isMatchingSupply(handItem, supply)) {
            return;
        }

        // 保留破布、绷带和夹板在满血且未着火时不启动的原有规则喵~
        if (!supply.canStartUse(player)) {
            return;
        }

        // 保存使用前的缓慢效果，读条结束后用于恢复原状态喵~
        PotionEffect originalSlowness = player.getPotionEffect(PotionEffectType.SLOWNESS);
        // 将当前时间与读条毫秒数相加得到完成时刻喵~
        long completionTimeMillis = System.currentTimeMillis() + supply.getUseDurationTicks() * 50L;
        // 生成新 token，让已取消会话的旧延迟任务自动失效喵~
        long sessionToken = ++nextSessionToken;
        // 创建会话并先注册到映射，确保同 tick 的重复事件会被拒绝喵~
        UseSession session =
                new UseSession(playerId, supply, hand, sessionToken, completionTimeMillis, originalSlowness);
        activeSessions.put(playerId, session);

        // 施加持续时间覆盖读条的“缓慢 VII”效果喵~
        int slownessDurationTicks = Math.toIntExact(supply.getUseDurationTicks() + SLOWNESS_PADDING_TICKS);
        player.addPotionEffect(new PotionEffect(
                PotionEffectType.SLOWNESS, slownessDurationTicks, SLOWNESS_AMPLIFIER, false, true, true));
        // 播放医疗用品开始读条提示音效喵~
        SoundEffect.MEDICAL_SUPPLY_START_SOUND.playFor(player);
        // 显示医疗用品中文名和开始状态喵~
        sendActionBar(player, "actionbar.medical-supply.started", "%item%", supply.getItemName());

        // 使用同步延迟任务完成会话，Bukkit API 不会从异步线程访问喵~
        session.completionTask =
                Slimefun.runSync(() -> completeUse(playerId, sessionToken), supply.getUseDurationTicks());
        // 使用同步周期任务显示进度，十 tick 更新一次避免 Action Bar 刷屏喵~
        session.progressTask = plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        () -> updateProgress(playerId, sessionToken),
                        PROGRESS_UPDATE_INTERVAL_TICKS,
                        PROGRESS_UPDATE_INTERVAL_TICKS);
    }

    /**
     * 判断玩家是否存在活动医疗使用会话喵~
     *
     * @param player 玩家喵~
     * @return 玩家有活动会话时返回 true喵~
     */
    public boolean hasActiveSession(@Nonnull Player player) {
        // 喵~防御：空玩家不能拥有会话喵~
        return player != null && activeSessions.containsKey(player.getUniqueId());
    }

    /**
     * 根据事件原因取消玩家的当前医疗使用会话喵~
     *
     * @param player 玩家喵~
     * @param showInterruptionMessage 是否显示中断 Action Bar 喵~
     */
    public void cancelUse(@Nonnull Player player, boolean showInterruptionMessage) {
        // 喵~防御：空玩家没有可取消的会话喵~
        if (player == null) {
            return;
        }

        // 从映射移除会话以取得唯一清理权，重复取消会自然失效喵~
        UseSession session = activeSessions.remove(player.getUniqueId());
        // 没有活动会话时无需执行任何副作用喵~
        if (session == null) {
            return;
        }

        // 取消尚未执行的任务，token 校验仍会保护已经排队的任务喵~
        cancelTasks(session);
        // 清除本会话施加的缓慢并谨慎恢复原有效果喵~
        restoreSlowness(player, session);

        // 只有可见的跳跃或伤害中断才显示提示和播放音效，退出和死亡不刷消息喵~
        if (showInterruptionMessage && isValidPlayer(player)) {
            // 播放医疗用品读条被打断的提示音效喵~
            SoundEffect.MEDICAL_SUPPLY_INTERRUPT_SOUND.playFor(player);
            // 显示医疗用品读条中断提示喵~
            sendActionBar(player, "actionbar.medical-supply.interrupted");
        }
    }

    /**
     * 关闭管理器并清理所有在线会话效果喵~
     */
    public void shutdown() {
        // 复制会话键，避免遍历时删除映射导致并发修改异常喵~
        UUID[] playerIds = activeSessions.keySet().toArray(new UUID[0]);
        // 逐个清理在线玩家的会话缓慢和任务喵~
        for (UUID playerId : playerIds) {
            Player player = plugin.getServer().getPlayer(playerId);
            // 在线玩家走标准取消逻辑以恢复可恢复的缓慢效果喵~
            if (player != null) {
                cancelUse(player, false);
            } else {
                // 离线玩家无法操作 PotionEffect，仅丢弃状态和取消任务喵~
                UseSession session = activeSessions.remove(playerId);
                if (session != null) {
                    cancelTasks(session);
                }
            }
        }

        // 关闭时不保留冷却，避免重载后残留内存状态喵~
        cooldownEndTimes.clear();
    }

    /**
     * 完成与 token 匹配的会话，旧任务或已取消会话不会产生副作用喵~
     *
     * @param playerId 玩家 UUID喵~
     * @param sessionToken 会话唯一 token喵~
     */
    private void completeUse(@Nonnull UUID playerId, long sessionToken) {
        // 获取当前会话，旧任务找不到会话时立即无效喵~
        UseSession session = activeSessions.get(playerId);
        // token 不同代表已有新会话或旧会话已取消，不能完成喵~
        if (session == null || session.token != sessionToken || !activeSessions.remove(playerId, session)) {
            return;
        }

        // 会话已从映射移除，后续伤害或移动事件不能再重复取消或结算喵~
        cancelTasks(session);
        // 重新定位玩家，玩家离线时不能消费或施加效果喵~
        Player player = plugin.getServer().getPlayer(playerId);
        // 完成前再次校验玩家状态与手部物品身份喵~
        if (!isValidPlayer(player) || !isMatchingSupply(getHandItem(player, session.hand), session.supply)) {
            // 玩家仍在线时恢复会话缓慢，离线时没有 Bukkit 实体可操作喵~
            if (player != null) {
                restoreSlowness(player, session);
            }
            return;
        }

        // 先清理读条缓慢，再执行维他命或药物的负面效果清理，避免恢复冲突喵~
        restoreSlowness(player, session);
        // 非创造模式只在成功完成时消费当前手部的一个医疗用品喵~
        if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            ItemUtils.consumeItem(getHandItem(player, session.hand), false);
        }

        // 执行物品原有的音效、治疗、灭火和状态清理逻辑喵~
        session.supply.applySuccessfulUse(player);
        // 药物改为手动消费后显式返还空玻璃瓶，保留原版容器语义喵~
        if (session.supply.returnsGlassBottle() && player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            returnGlassBottle(player);
        }

        // 成功完成后才写入跨医疗用品共用的三十秒冷却喵~
        cooldownEndTimes.put(playerId, System.currentTimeMillis() + SHARED_COOLDOWN_MILLIS);
    }

    /**
     * 更新一个仍有效会话的进度提示喵~
     *
     * @param playerId 玩家 UUID喵~
     * @param sessionToken 会话 token喵~
     */
    private void updateProgress(@Nonnull UUID playerId, long sessionToken) {
        // 获取会话并拒绝旧周期任务喵~
        UseSession session = activeSessions.get(playerId);
        if (session == null || session.token != sessionToken) {
            return;
        }

        // 离线或死亡时按无提示中断处理喵~
        Player player = plugin.getServer().getPlayer(playerId);
        if (!isValidPlayer(player)) {
            cancelUseIfCurrent(playerId, sessionToken, player, false);
            return;
        }

        // 读条中切换或移除医疗用品时立刻取消，防止完成时扣错物品喵~
        if (!isMatchingSupply(getHandItem(player, session.hand), session.supply)) {
            cancelUseIfCurrent(playerId, sessionToken, player, true);
            return;
        }

        // 计算仍需等待的毫秒数并显示一位小数秒喵~
        long remainingMillis = Math.max(0L, session.completionTimeMillis - System.currentTimeMillis());
        sendActionBar(
                player,
                "actionbar.medical-supply.progress",
                "%item%",
                session.supply.getItemName(),
                "%remaining%",
                formatSeconds(remainingMillis));
    }

    /**
     * 仅在 token 仍是当前会话时取消，避免旧任务误取消新会话喵~
     */
    private void cancelUseIfCurrent(
            @Nonnull UUID playerId, long sessionToken, Player player, boolean showInterruptionMessage) {
        // 获取当前会话用于 token 对比喵~
        UseSession session = activeSessions.get(playerId);
        // 只有 token 一致时才允许执行清理喵~
        if (session != null && session.token == sessionToken) {
            // 在线玩家执行完整取消，离线玩家只失效会话任务喵~
            if (player != null) {
                cancelUse(player, showInterruptionMessage);
            } else if (activeSessions.remove(playerId, session)) {
                cancelTasks(session);
            }
        }
    }

    /**
     * 获取并清理玩家未过期的共用冷却剩余时间喵~
     */
    private long getRemainingCooldownMillis(@Nonnull UUID playerId) {
        // 读取冷却结束时间喵~
        Long cooldownEndTime = cooldownEndTimes.get(playerId);
        // 没有冷却记录时直接允许使用喵~
        if (cooldownEndTime == null) {
            return 0L;
        }

        // 计算当前仍剩余的毫秒数喵~
        long remainingMillis = cooldownEndTime - System.currentTimeMillis();
        // 到期时删除记录，避免映射无限累积喵~
        if (remainingMillis <= 0L) {
            cooldownEndTimes.remove(playerId);
            return 0L;
        }

        // 返回仍有效的冷却时间喵~
        return remainingMillis;
    }

    /**
     * 判断载具是否允许医疗用品读条喵~
     */
    private boolean isAllowedVehicle(Entity vehicle) {
        // 无载具时允许正常使用喵~
        return vehicle == null || vehicle instanceof Boat || vehicle instanceof Minecart;
    }

    /**
     * 判断玩家是否仍可安全操作 Bukkit 状态喵~
     */
    private boolean isValidPlayer(Player player) {
        // 喵~防御：离线、死亡或无效实体不允许读取背包、治疗或施加药水效果喵~
        return player != null && player.isOnline() && player.isValid() && !player.isDead();
    }

    /**
     * 获取给定手部的实时物品堆栈喵~
     */
    private ItemStack getHandItem(@Nonnull Player player, @Nonnull EquipmentSlot hand) {
        // 主手返回玩家主手物品喵~
        if (hand == EquipmentSlot.HAND) {
            return player.getInventory().getItemInMainHand();
        }

        // 副手返回玩家副手物品喵~
        return player.getInventory().getItemInOffHand();
    }

    /**
     * 判断实时物品是否仍然对应预期医疗用品喵~
     */
    private boolean isMatchingSupply(ItemStack item, @Nonnull MedicalSupply supply) {
        // 喵~防御：空堆栈、空气或数量为零都不能被消费喵~
        if (item == null || item.getType() == Material.AIR || item.getAmount() < 1) {
            return false;
        }

        // 通过 Slimefun 注册项身份核对，避免只按材质误识别其他自定义物品喵~
        return SlimefunItem.getByItem(item) == supply;
    }

    /**
     * 取消一个会话持有的全部调度任务喵~
     */
    private void cancelTasks(@Nonnull UseSession session) {
        // 完成任务存在时取消，已执行任务的取消调用安全无副作用喵~
        if (session.completionTask != null) {
            session.completionTask.cancel();
        }

        // 进度任务存在时取消，避免会话结束后继续发送 Action Bar喵~
        if (session.progressTask != null) {
            session.progressTask.cancel();
        }
    }

    /**
     * 移除管理器施加的缓慢并保守恢复开始前效果喵~
     */
    private void restoreSlowness(@Nonnull Player player, @Nonnull UseSession session) {
        // 读取当前缓慢效果用于避免覆盖其他插件的新效果喵~
        PotionEffect currentSlowness = player.getPotionEffect(PotionEffectType.SLOWNESS);
        // 仅当前仍是本会话的缓慢等级时移除，外部更改则保持原样喵~
        if (currentSlowness != null && currentSlowness.getAmplifier() == SLOWNESS_AMPLIFIER) {
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            // 使用前存在缓慢时恢复其完整快照喵~
            if (session.originalSlowness != null) {
                player.addPotionEffect(session.originalSlowness);
            }
        }
    }

    /**
     * 向玩家返还药物消耗产生的空玻璃瓶喵~
     */
    private void returnGlassBottle(@Nonnull Player player) {
        // 创建一个原版空玻璃瓶作为药物容器返还物喵~
        ItemStack glassBottle = new ItemStack(Material.GLASS_BOTTLE);
        // 尝试放入背包，返回值包含未放入的剩余物品喵~
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(glassBottle);
        // 背包已满时在玩家位置自然掉落，避免容器静默丢失喵~
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    /**
     * 格式化毫秒剩余时间为一位小数的秒数喵~
     */
    private String formatSeconds(long remainingMillis) {
        // 将毫秒转换为秒并固定显示一位小数喵~
        return String.format(java.util.Locale.ROOT, "%.1f", remainingMillis / 1000.0D);
    }

    /**
     * 读取本地化 Action Bar 文本、替换占位符并发送给玩家喵~
     */
    private void sendActionBar(@Nonnull Player player, @Nonnull String key, String... replacements) {
        // 读取玩家当前语言对应的 Action Bar 模板喵~
        String message = Slimefun.getLocalization().getMessage(player, key);
        // 成对处理占位符和替换值，奇数参数安全忽略最后一个值喵~
        for (int index = 0; index + 1 < replacements.length; index += 2) {
            message = message.replace(replacements[index], replacements[index + 1]);
        }

        // 使用既有 Bungee Action Bar API 发送带颜色的本地化消息喵~
        player.spigot()
                .sendMessage(
                        ChatMessageType.ACTION_BAR,
                        new ComponentBuilder().append(ChatColors.color(message)).create());
    }

    /**
     * 单次医疗用品读条的不可变会话元数据与可取消任务引用喵~
     */
    private static final class UseSession {

        // 使用者 UUID，用于任务中重新获取在线玩家喵~
        private final UUID playerId;
        // 本次会话绑定的医疗用品实例喵~
        private final MedicalSupply supply;
        // 发起使用的主手或副手喵~
        private final EquipmentSlot hand;
        // 会话 token，用于拒绝旧任务喵~
        private final long token;
        // 预计完成时间，单位：Unix 毫秒喵~
        private final long completionTimeMillis;
        // 读条开始前已有的缓慢效果快照，可为空喵~
        private final PotionEffect originalSlowness;
        // 延迟完成任务引用，供取消路径停止任务喵~
        private BukkitTask completionTask;
        // 周期进度任务引用，供取消路径停止 Action Bar 刷新喵~
        private BukkitTask progressTask;

        /**
         * 创建一个医疗用品使用会话喵~
         */
        private UseSession(
                UUID playerId,
                MedicalSupply supply,
                EquipmentSlot hand,
                long token,
                long completionTimeMillis,
                PotionEffect originalSlowness) {
            // 保存使用者 UUID喵~
            this.playerId = playerId;
            // 保存本会话的医疗用品实例喵~
            this.supply = supply;
            // 保存发起使用的手部喵~
            this.hand = hand;
            // 保存用于失效旧任务的 token喵~
            this.token = token;
            // 保存进度显示和完成判断使用的结束时间喵~
            this.completionTimeMillis = completionTimeMillis;
            // 保存可恢复的原有缓慢效果喵~
            this.originalSlowness = originalSlowness;
        }
    }
}
