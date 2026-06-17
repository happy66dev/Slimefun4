package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedback;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedbackType;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;

/**
 * 机器反馈服务类，负责管理所有机器运行时的视觉与音效反馈喵~
 * 整体思路：
 *   - 机器启动时：触发 onMachineStart，点亮方块(如熔炉)，初始化粒子计数器和里程碑记录喵
 *   - 机器每tick：触发 onMachineTick，每隔1 tick 发射粒子，在进度达到25/50/75%时播放声音喵
 *   - 机器停止时：触发 onMachineStop，熄灭方块，清除所有状态数据喵
 *   - 所有对 Bukkit 世界(World/Block)的操作都通过 runTask 切回主线程执行，保证线程安全喵~
 */
public class MachineFeedbackService {

    // 进度里程碑百分比数组：当机器完成进度达到25%、50%、75%时分别触发音效喵
    private static final int[] MILESTONES = {25, 50, 75};

    // 持有 Slimefun 插件实例，用于向 Bukkit 调度器注册主线程任务喵
    private final Slimefun plugin;

    // 记录当前处于"已点亮"状态的方块位置集合，防止重复操作喵
    private final Set<BlockPosition> activeBlockStates = ConcurrentHashMap.newKeySet();
    // 记录每个机器方块已经触发过的进度里程碑，避免同一里程碑重复播放音效喵
    private final Map<BlockPosition, Set<Integer>> firedMilestones = new ConcurrentHashMap<>();
    // 记录每个机器方块当前的粒子 tick 计数，用于控制粒子生成频率喵
    private final Map<BlockPosition, Integer> particleTickCounters = new ConcurrentHashMap<>();
    // BlockPosition 对象缓存，避免对同一方块位置反复创建新对象，节省内存喵
    private final ConcurrentHashMap<BlockPosition, BlockPosition> positionCache = new ConcurrentHashMap<>();

    /**
     * 构造函数，注入 Slimefun 插件主实例喵~
     * @param plugin Slimefun 插件实例，不可为 null 喵
     */
    public MachineFeedbackService(@Nonnull Slimefun plugin) {
        // 保存插件引用，后续调度主线程任务时使用喵
        this.plugin = plugin;
    }

    /**
     * 获取方块对应的 BlockPosition，优先从缓存中取，避免重复创建对象喵~
     * 输入：Bukkit Block 对象喵
     * 输出：已缓存或新创建的 BlockPosition 对象喵
     * @param block 目标方块，不可为 null 喵
     * @return 该方块对应的 BlockPosition 实例喵
     */
    @Nonnull
    private BlockPosition getPosition(@Nonnull Block block) {
        // computeIfAbsent：若缓存中不存在则新建并放入，存在则直接返回缓存值喵
        return positionCache.computeIfAbsent(new BlockPosition(block), k -> k);
    }

    /**
     * 机器启动时调用，执行以下操作喵~
     * 整体思路：
     *   1. 若 type 为 null 则直接忽略，无需处理喵
     *   2. 在主线程中触发 MachineFeedback 的 onMachineStart 回调，并尝试点亮方块喵
     *   3. 初始化该方块的里程碑集合和粒子计数器喵
     *   4. 立即生成一次启动粒子效果喵
     * 输入：block=机器方块位置, type=反馈类型(可为null), operation=机器当前运算对象喵
     * 边界条件：type 为 null 时直接返回，不做任何操作喵
     *
     * @param block     机器所在方块，不可为 null 喵
     * @param type      反馈类型，可为 null 喵
     * @param operation 当前机器运算，不可为 null 喵
     */
    public void onMachineStart(
            @Nonnull Block block, @Nullable MachineFeedback type, @Nonnull MachineOperation operation) {
        // 喵~防御：type 为 null 时说明该机器无反馈配置，直接跳过避免空指针崩溃喵
        if (type == null) {
            return;
        }

        // 从缓存获取该方块的位置对象，用于后续各Map操作的键喵
        BlockPosition pos = getPosition(block);
        // 切回主线程执行世界操作，因为 Bukkit Block 相关 API 不能在异步线程调用喵
        Bukkit.getScheduler().runTask(plugin, () -> {
            // 触发 MachineFeedback 定义的机器启动回调（如播放音效、改变外观等）喵
            type.onMachineStart(block);
            // 判断该方块材质是否支持"点亮"属性（如熔炉、高炉、烟熏炉）喵
            if (hasLitProperty(block.getType())) {
                // 获取方块当前的 BlockData 数据对象喵
                BlockData data = block.getBlockData();
                // 用 instanceof 模式匹配判断 data 是否实现了 Lightable 接口喵
                if (data instanceof Lightable lightable) {
                    // 将方块点亮，模拟机器运行中的发光状态喵
                    lightable.setLit(true);
                    // 将修改后的 BlockData 写回方块，使视觉变化生效喵
                    block.setBlockData(data);
                    // 记录该方块已被点亮，停止时需要熄灭喵
                    activeBlockStates.add(pos);
                }
            }
        });

        // 初始化该方块的已触发里程碑集合（空集合表示尚未触发任何里程碑）喵
        firedMilestones.put(pos, ConcurrentHashMap.newKeySet());
        // 初始化粒子 tick 计数器为 0，从零开始计数喵
        particleTickCounters.put(pos, 0);

        // 机器启动时立即生成一次粒子效果，给玩家直观反馈喵
        spawnParticles(block, type);
    }

    /**
     * 机器每次 tick 时调用，执行以下操作喵~
     * 整体思路：
     *   1. 若 type 为 null 则直接跳过喵
     *   2. 每隔1 tick（即每2次 tick 调用）生成一次粒子，平衡视觉效果与性能喵
     *   3. 在主线程回调 MachineFeedback 的 onMachineTick 方法喵
     *   4. 计算当前进度百分比，检测是否到达新里程碑，若是则播放进度音效喵
     * 输入：block=机器方块, type=反馈类型(可为null), operation=包含当前进度和总 tick 数的运算对象喵
     * 边界条件：type 为 null 或 totalTicks<=0 时提前返回喵
     *
     * @param block     机器所在方块，不可为 null 喵
     * @param type      反馈类型，可为 null 喵
     * @param operation 当前机器运算，包含进度信息，不可为 null 喵
     */
    public void onMachineTick(
            @Nonnull Block block, @Nullable MachineFeedback type, @Nonnull MachineOperation operation) {
        // 喵~防御：type 为 null 时无反馈配置，直接跳过避免后续空指针喵
        if (type == null) {
            return;
        }

        // 从缓存获取该方块位置对象喵
        BlockPosition pos = getPosition(block);

        // 读取当前 tick 计数，若不存在则默认为 0 喵
        int counter = particleTickCounters.getOrDefault(pos, 0);
        // 计数器自增，记录已经 tick 的次数喵
        counter++;
        // 将更新后的计数写回 Map 喵
        particleTickCounters.put(pos, counter);

        // 每隔1个 tick 生成一次粒子（计数器为偶数时触发），避免每 tick 都生成导致粒子过密喵
        if (counter % 2 == 0) {
            spawnParticles(block, type);
        }

        // 切回主线程触发 MachineFeedback 的 tick 回调（处理方块外观等主线程操作）喵
        Bukkit.getScheduler().runTask(plugin, () -> {
            type.onMachineTick(block, operation);
        });

        // 获取该运算的总 tick 数，用于计算完成百分比喵
        int totalTicks = operation.getTotalTicks();
        // 喵~防御：totalTicks 为 0 或负数时无法计算百分比，直接返回防止除零异常喵
        if (totalTicks <= 0) {
            return;
        }

        // 计算当前进度百分比，使用 long 强转防止 int 相乘溢出喵
        int currPercent = (int) ((long) operation.getProgress() * 100 / totalTicks);

        // 获取(或初始化)该方块的已触发里程碑集合喵
        Set<Integer> milestones = firedMilestones.computeIfAbsent(pos, k -> ConcurrentHashMap.newKeySet());
        // 记录本次 tick 中新触达的最高里程碑，-1 表示没有新里程碑喵
        int highestNewMilestone = -1;
        // 遍历所有预设里程碑，找出当前进度已达到但尚未触发的最高里程碑喵
        for (int milestone : MILESTONES) {
            // 当前进度大于等于该里程碑且该里程碑尚未记录，则更新最高新里程碑喵
            if (currPercent >= milestone && !milestones.contains(milestone)) {
                highestNewMilestone = milestone;
            }
        }

        // 若找到了新的里程碑，则批量记录并播放音效喵
        if (highestNewMilestone >= 0) {
            // 将所有小于等于最高新里程碑的里程碑都标记为已触发，避免补发漏触的里程碑音效时遗漏喵
            for (int milestone : MILESTONES) {
                if (milestone <= highestNewMilestone) {
                    milestones.add(milestone);
                }
            }

            // 获取该反馈类型默认的进度音效喵
            Sound sound = type.getDefaultSound();
            // 获取方块所在世界喵
            World world = block.getWorld();
            // 获取方块的坐标位置，用于在指定位置播放音效喵
            Location loc = block.getLocation();
            // 切回主线程播放音效（Bukkit 音效 API 必须在主线程调用）喵
            Bukkit.getScheduler().runTask(plugin, () -> {
                // 在方块位置以 BLOCKS 分类播放音效，音量和音调均为 1.0 喵
                world.playSound(loc, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
            });
        }
    }

    /**
     * 机器停止时调用，执行以下操作喵~
     * 整体思路：
     *   1. 若 type 为 null 则直接跳过喵
     *   2. 在主线程触发 onMachineStop 回调，并将已点亮的方块熄灭喵
     *   3. 清除该方块的里程碑记录、粒子计数器和位置缓存，释放内存喵
     * 输入：block=机器方块, type=反馈类型(可为null)喵
     * 边界条件：type 为 null 时直接返回喵
     *
     * @param block 机器所在方块，不可为 null 喵
     * @param type  反馈类型，可为 null 喵
     */
    public void onMachineStop(@Nonnull Block block, @Nullable MachineFeedback type) {
        // 喵~防御：type 为 null 时无需任何停止处理，直接返回喵
        if (type == null) {
            return;
        }

        // 获取缓存的方块位置对象喵
        BlockPosition pos = getPosition(block);

        // 切回主线程执行方块状态变更操作喵
        Bukkit.getScheduler().runTask(plugin, () -> {
            // 触发 MachineFeedback 定义的机器停止回调（如恢复外观等）喵
            type.onMachineStop(block);
            // 若该方块在运行时被记录为已点亮状态，则将其从集合中移除并熄灭喵
            if (activeBlockStates.remove(pos)) {
                // 获取当前 BlockData 数据对象喵
                BlockData data = block.getBlockData();
                // 检查是否实现了 Lightable 接口（熔炉类方块）喵
                if (data instanceof Lightable lightable) {
                    // 将方块熄灭，恢复为未运行的外观喵
                    lightable.setLit(false);
                    // 将修改后的 BlockData 写回方块，使熄灭效果生效喵
                    block.setBlockData(data);
                }
            }
        });

        // 清除该方块的里程碑触发记录，释放内存喵
        firedMilestones.remove(pos);
        // 清除该方块的粒子 tick 计数器，释放内存喵
        particleTickCounters.remove(pos);
        // 从位置缓存中移除该方块，避免缓存无限增长喵
        positionCache.remove(pos);
    }

    /**
     * 在指定方块位置生成粒子效果，根据方块类型和偏移配置选择生成位置喵~
     * 整体思路：
     *   - 若粒子类型为 null 则跳过喵
     *   - 对于头颅类方块(Skull)，在头颅顶部随机散布4个粒子喵
     *   - 对于普通机器方块，根据 ParticleOffset 枚举值决定在顶部/侧面/底部/头部生成喵
     *   - 所有随机数通过 ThreadLocalRandom 生成，在异步线程中计算坐标后切回主线程生成粒子喵
     * 输入：block=目标方块, type=反馈类型(含粒子配置)喵
     * 边界条件：particle 为 null 时直接返回喵
     *
     * @param block 机器所在方块，不可为 null 喵
     * @param type  反馈类型，含粒子与偏移信息，不可为 null 喵
     */
    private void spawnParticles(@Nonnull Block block, @Nonnull MachineFeedback type) {
        // 获取该反馈类型配置的默认粒子效果喵
        Particle particle = type.getDefaultParticle();
        // 喵~防御：particle 为 null 说明该类型不需要粒子效果，直接跳过喵
        if (particle == null) {
            return;
        }

        // 获取粒子偏移配置，决定粒子生成在方块的哪个位置喵
        MachineFeedbackType.ParticleOffset offset = type.getParticleOffset();
        // 获取方块所在世界喵
        World world = block.getWorld();
        // 获取方块的 X 坐标（整数转 double 用于精确计算偏移量）喵
        double bx = block.getX();
        // 获取方块的 Y 坐标喵
        double by = block.getY();
        // 获取方块的 Z 坐标喵
        double bz = block.getZ();
        // 使用线程本地随机数，避免多线程竞争 Random 实例的性能开销喵
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // 预分配头颅粒子的坐标数组：4个粒子，每个粒子存储 x/y/z/speed 共 4 个值喵
        // 主人注意：这里为 4 个粒子预算坐标，数量固定不会增长，性能安全喵
        double[] skullOffsets = new double[16];
        // 循环计算 4 个头颅粒子的随机坐标和速度，提前在当前线程计算避免主线程阻塞喵
        for (int i = 0; i < 4; i++) {
            // 计算当前粒子数据在数组中的起始下标（每粒子占4格）喵
            int base = i * 4;
            // 粒子 X 坐标：在方块水平中心附近随机偏移 ±0.25 喵
            skullOffsets[base] = bx + 0.5 + rnd.nextDouble(-0.25, 0.25);
            // 粒子 Y 坐标：在头颅模型高度(+0.85)附近随机向上偏移 0~0.3 喵
            skullOffsets[base + 1] = by + 0.85 + rnd.nextDouble(0, 0.3);
            // 粒子 Z 坐标：在方块水平中心附近随机偏移 ±0.25 喵
            skullOffsets[base + 2] = bz + 0.5 + rnd.nextDouble(-0.25, 0.25);
            // 粒子速度：随机 0~0.02，控制粒子扩散快慢喵
            skullOffsets[base + 3] = rnd.nextDouble(0.02);
        }

        // 方块水平中心 X 坐标（+0.5 对齐方块中心）喵
        double cx = bx + 0.5;
        // 方块水平中心 Z 坐标喵
        double cz = bz + 0.5;
        // 侧面粒子 X 坐标：随机选择方块左侧或右侧偏移 ±0.6 喵
        double sideX = bx + 0.5 + (rnd.nextBoolean() ? 0.6 : -0.6);
        // 侧面粒子 Z 坐标：随机选择方块前侧或后侧偏移 ±0.6 喵
        double sideZ = bz + 0.5 + (rnd.nextBoolean() ? 0.6 : -0.6);
        // 通用粒子速度：随机 0~0.02 喵
        double speed = rnd.nextDouble(0.02);

        // 将在当前线程计算好的随机值转为 final 局部变量，供 lambda 捕获（Java 要求 lambda 捕获变量必须是 effectively final）喵
        final double[] fSkullOffsets = skullOffsets;
        final double fCx = cx, fCz = cz, fSideX = sideX, fSideZ = sideZ, fSpeed = speed;

        // 切回主线程生成粒子，因为 world.spawnParticle 必须在主线程调用喵
        Bukkit.getScheduler().runTask(plugin, () -> {
            // 若方块是头颅类型，使用专属的头颅粒子逻辑生成4个分散粒子后返回喵
            if (isSkull(block.getType())) {
                for (int i = 0; i < 4; i++) {
                    // 取出当前粒子的起始下标喵
                    int base = i * 4;
                    // 在预计算的头颅坐标处生成1个粒子，offsetY 和 speed 均使用存储的随机速度值喵
                    world.spawnParticle(
                            particle,
                            fSkullOffsets[base], // 粒子 X 坐标喵
                            fSkullOffsets[base + 1], // 粒子 Y 坐标喵
                            fSkullOffsets[base + 2], // 粒子 Z 坐标喵
                            1, // 粒子数量：1个喵
                            0, // offsetX：无水平扩散喵
                            fSkullOffsets[base + 3], // offsetY：用随机速度值控制垂直扩散喵
                            0, // offsetZ：无水平扩散喵
                            fSkullOffsets[base + 3]); // speed：用随机速度值喵
                }
                return; // 头颅类型处理完毕，不再进入 switch 逻辑喵
            }

            // 根据偏移配置决定粒子生成位置，覆盖 TOP/SIDE/BOTTOM/HEAD_ONLY 及默认情况喵
            switch (offset) {
                // 顶部偏移：在方块顶面(+1.05)稍微偏上位置生成2个粒子，有水平扩散喵
                case TOP -> {
                    world.spawnParticle(particle, fCx, by + 1.05, fCz, 2, 0.15, 0.05, 0.15, fSpeed);
                }
                // 侧面偏移：在方块侧面中段(+0.5)生成1个粒子，有垂直方向扩散喵
                case SIDE -> {
                    world.spawnParticle(particle, fSideX, by + 0.5, fSideZ, 1, 0, fSpeed, 0, fSpeed);
                }
                // 底部偏移：在方块底面(-0.05)稍微偏下位置生成2个粒子，有水平扩散喵
                case BOTTOM -> {
                    world.spawnParticle(particle, fCx, by - 0.05, fCz, 2, 0.15, 0.05, 0.15, fSpeed);
                }
                // 仅头部偏移：在方块顶部生成1个粒子，各方向均有小幅扩散喵
                case HEAD_ONLY -> {
                    world.spawnParticle(particle, fCx, by + 1.05, fCz, 1, 0.1, 0.1, 0.1, fSpeed);
                }
                // 默认情况：同时在顶部和侧面生成粒子，视觉效果最丰富喵
                default -> {
                    world.spawnParticle(particle, fCx, by + 1.05, fCz, 2, 0.15, 0.05, 0.15, fSpeed);
                    world.spawnParticle(particle, fSideX, by + 0.5, fSideZ, 1, 0, fSpeed, 0, fSpeed);
                }
            }
        });
    }

    /**
     * 判断指定材质的方块是否具有"可点亮(Lightable)"属性喵~
     * 当前支持：熔炉(FURNACE)、高炉(BLAST_FURNACE)、烟熏炉(SMOKER)喵
     *
     * @param mat 方块材质，不可为 null 喵
     * @return 若该材质支持 Lightable 则返回 true，否则返回 false 喵
     */
    private boolean hasLitProperty(@Nonnull Material mat) {
        // 检查材质是否为三种支持发光的熔炉类方块之一喵
        return mat == Material.FURNACE || mat == Material.BLAST_FURNACE || mat == Material.SMOKER;
    }

    /**
     * 判断指定材质是否为头颅(Skull)类方块喵~
     * 涵盖玩家头颅、骷髅、凋零骷髅、僵尸、爬行者的悬挂版与放置版喵
     *
     * @param mat 方块材质，不可为 null 喵
     * @return 若该材质是头颅类则返回 true，否则返回 false 喵
     */
    private boolean isSkull(@Nonnull Material mat) {
        // 逐一判断是否属于各种头颅材质（包括放置在地面和挂在墙上的变体）喵
        return mat == Material.PLAYER_HEAD // 玩家头颅（放地面）喵
                || mat == Material.PLAYER_WALL_HEAD // 玩家头颅（挂墙面）喵
                || mat == Material.SKELETON_SKULL // 骷髅头颅（放地面）喵
                || mat == Material.SKELETON_WALL_SKULL // 骷髅头颅（挂墙面）喵
                || mat == Material.WITHER_SKELETON_SKULL // 凋零骷髅头颅（放地面）喵
                || mat == Material.WITHER_SKELETON_WALL_SKULL // 凋零骷髅头颅（挂墙面）喵
                || mat == Material.ZOMBIE_HEAD // 僵尸头颅（放地面）喵
                || mat == Material.ZOMBIE_WALL_HEAD // 僵尸头颅（挂墙面）喵
                || mat == Material.CREEPER_HEAD // 爬行者头颅（放地面）喵
                || mat == Material.CREEPER_WALL_HEAD; // 爬行者头颅（挂墙面）喵
    }

    /**
     * 清理所有状态数据，通常在插件卸载或服务关闭时调用喵~
     * 清空后所有机器的反馈状态重置，防止内存泄漏喵
     */
    public void cleanup() {
        // 清空已点亮方块集合，防止重启后状态残留喵
        activeBlockStates.clear();
        // 清空所有里程碑触发记录喵
        firedMilestones.clear();
        // 清空所有粒子 tick 计数器喵
        particleTickCounters.clear();
        // 清空位置缓存，释放所有 BlockPosition 对象喵
        positionCache.clear();
    }
}
