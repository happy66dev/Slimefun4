package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Iterator;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

/**
 * This Service is responsible for automatically saving {@link Player} and {@link Block}
 * data.
 *
 * @author TheBusyBiscuit
 *
 */
// 自动保存服务类，负责周期性地将玩家档案和方块数据持久化到磁盘喵~
public class AutoSavingService {

    // 自动保存的时间间隔，单位为分钟喵~
    private int interval;

    /**
     * This method starts the {@link AutoSavingService} with the given interval.
     *
     * @param plugin
     *            The current instance of Slimefun
     * @param interval
     *            The interval in which to run this task
     */
    /*
     * 启动自动保存服务的整体思路喵~：
     * 1. 记录保存间隔（分钟）到成员变量
     * 2. 注册一个同步定时任务，每隔 interval 分钟在主线程保存所有玩家数据
     * 3. 注册一个异步定时任务，每隔 interval 分钟在后台线程保存所有方块背包数据
     * 两个任务都在服务器启动后约 2000 tick（约100秒）后首次执行，避免启动高峰期造成卡顿喵~
     * 边界条件：interval 必须是正整数，否则定时器间隔会异常喵~
     */
    public void start(@Nonnull Slimefun plugin, int interval) {
        // 将传入的保存间隔存到成员变量，供后续日志或其他逻辑使用喵~
        this.interval = interval;

        // 注册同步定时任务：在主线程每隔 interval 分钟调用 saveAllPlayers() 保存玩家数据喵~
        // 2000L 是首次延迟（tick），interval * 60L * 20L 是周期（分钟 × 60秒 × 20tick/秒 = tick数）
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::saveAllPlayers, 2000L, interval * 60L * 20L);

        // 注册异步定时任务：在后台线程每隔 interval 分钟保存所有方块背包和通用背包数据喵~
        // 使用异步是为了避免大量 IO 操作阻塞主线程导致服务器卡顿喵~
        plugin.getServer()
                .getScheduler()
                .runTaskTimerAsynchronously(
                        plugin,
                        () -> {
                            // 通过数据库管理器获取方块数据控制器，保存所有方块背包内容到磁盘喵~
                            Slimefun.getDatabaseManager()
                                    .getBlockDataController()
                                    .saveAllBlockInventories();

                            // 保存所有通用背包（跨方块共享的背包）内容到磁盘喵~
                            Slimefun.getDatabaseManager()
                                    .getBlockDataController()
                                    .saveAllUniversalInventories();
                        },
                        // 首次延迟同样是 2000 tick，与玩家数据保存任务错开执行时机喵~
                        2000L,
                        // 周期同样是 interval 分钟转换为 tick 数喵~
                        interval * 60L * 20L);
    }

    /**
     * This method saves every {@link PlayerProfile} in memory and removes profiles
     * that were marked for deletion.
     */
    /*
     * 保存所有玩家档案的整体思路喵~：
     * 1. 获取当前内存中所有 PlayerProfile 的迭代器
     * 2. 遍历每个档案，如果档案"脏了"（有未保存的修改），则保存它并计数
     * 3. 如果档案被标记为待删除（玩家已离线且数据已处理完），则从内存中移除
     * 4. 遍历结束后，如果有保存的档案数量 > 0，输出 INFO 日志告知管理员喵~
     * 边界条件：迭代器遍历时使用 iterator.remove() 安全删除，避免 ConcurrentModificationException喵~
     */
    private void saveAllPlayers() {
        // 获取所有已加载的 PlayerProfile 的迭代器，用于安全地遍历并按需删除喵~
        Iterator<PlayerProfile> iterator = PlayerProfile.iterator();

        // 记录本次保存了多少个有改动的玩家档案，用于日志输出喵~
        int players = 0;

        // 遍历内存中的所有玩家档案喵~
        while (iterator.hasNext()) {
            // 取出下一个玩家档案喵~
            PlayerProfile profile = iterator.next();

            // 检查档案是否"脏"——即有尚未写入磁盘的修改（如研究解锁、路径点变更等）喵~
            if (profile.isDirty()) {
                // 有改动，则计入本次保存数量喵~
                players++;
                // 同步保存该玩家档案到磁盘喵~
                profile.save();
            }

            // 检查档案是否被标记为待删除（通常是玩家已离线后触发）喵~
            if (profile.isMarkedForDeletion()) {
                // 喵~防御：使用迭代器自身的 remove() 方法删除，避免在遍历过程中直接操作集合引发 ConcurrentModificationException喵~
                iterator.remove();
            }
        }

        // 喵~防御：只有确实保存了至少一个档案才输出日志，避免无改动时刷屏控制台喵~
        if (players > 0) {
            // 向服务器控制台输出 INFO 级别的日志，告知管理员本次自动保存了多少玩家的数据喵~
            Slimefun.logger().log(Level.INFO, "成功保存了 {0} 个玩家的数据!", players);
        }
    }
}
