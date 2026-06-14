package io.github.thebusybiscuit.slimefun4.api.player;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.callback.IAsyncReadCallback;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.api.events.AsyncProfileLoadEvent;
import io.github.thebusybiscuit.slimefun4.api.gps.Waypoint;
import io.github.thebusybiscuit.slimefun4.api.items.HashedArmorpiece;
import io.github.thebusybiscuit.slimefun4.api.items.ItemState;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectiveArmor;
import io.github.thebusybiscuit.slimefun4.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.armor.SlimefunArmorPiece;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * A class that can store a Player's {@link Research} progress for caching purposes.
 * It also holds the backpacks of a {@link Player}.
 *
 * @author TheBusyBiscuit
 *
 * @see Research
 * @see Waypoint
 * @see HashedArmorpiece
 *
 */
public class PlayerProfile {
    // 用于跟踪正在处理中的玩家档案UUID，防止并发重复加载，键为UUID、值固定为true喵
    private static final Map<UUID, Boolean> processProfiles = new ConcurrentHashMap<>();

    // 该档案所属玩家的唯一标识UUID，档案创建后不可变喵
    private final UUID owner;
    // 该玩家背包数量的计数器，用于生成新背包编号喵
    private int backpackNum;
    // 存储玩家路径点数据的配置文件对象，对应data-storage目录下的yml文件喵
    private final Config waypointsFile;

    /**
     * `dirty` indicates whether the profile has unsaved changes.
     */
    // 标记档案是否有未保存的变更，true代表存在待写入磁盘的数据喵
    private boolean dirty = false;

    /**
     * `markedForDeletion` indicates whether the profile is marked for deletion.
     * If true, means the profile should be removed from memory later.
     */
    // 标记档案是否等待从内存中删除，玩家下线后置为true，等待GC清理喵
    private boolean markedForDeletion = false;

    // 该玩家已解锁的所有Research的集合，使用HashSet保证唯一性喵
    private final Set<Research> researches;
    // 玩家的路径点列表，使用线程安全的CopyOnWriteArrayList，支持并发读写喵
    private final List<Waypoint> waypoints = new CopyOnWriteArrayList<>();
    // 玩家在Slimefun指南中的浏览历史，用于支持返回上一页功能喵
    private final GuideHistory guideHistory = new GuideHistory(this);

    // 玩家的四件套盔甲缓存数组，下标0-3分别对应头盔/胸甲/护腿/靴子喵
    private final HashedArmorpiece[] armor = {
        new HashedArmorpiece(), new HashedArmorpiece(), new HashedArmorpiece(), new HashedArmorpiece()
    };

    public PlayerProfile(@Nonnull OfflinePlayer p, int backpackNum) {
        // 委托给带researches参数的构造函数，并传入空的HashSet作为初始研究集合喵
        this(p, backpackNum, new HashSet<>());
    }

    /*
     * 构造函数：根据OfflinePlayer、背包数量和已有研究集合初始化玩家档案喵
     * 输入：OfflinePlayer（玩家对象）、backpackNum（背包计数）、researches（已解锁研究集合）
     * 边界条件：p不能为null，否则UUID获取会抛出NPE喵
     */
    public PlayerProfile(@Nonnull OfflinePlayer p, int backpackNum, Set<Research> researches) {
        // 从玩家对象获取唯一标识UUID并存储为档案的所有者喵
        owner = p.getUniqueId();
        // 记录当前背包数量，用于后续分配新背包编号喵
        this.backpackNum = backpackNum;
        // 将传入的研究集合赋值给档案的研究字段喵
        this.researches = researches;

        // 根据玩家UUID拼接路径点配置文件的路径并初始化Config对象喵
        waypointsFile = new Config("data-storage/Slimefun/waypoints/" + p.getUniqueId() + ".yml");
        // 从配置文件加载已保存的路径点数据到内存喵
        loadWaypoint();
    }

    /*
     * 从路径点配置文件加载所有路径点数据到内存列表喵
     * 整体思路：遍历yml配置文件的所有key，检查对应世界是否存在，若存在则构造Waypoint对象加入列表喵
     * 边界条件：若世界已不存在（服务器删除了对应世界），则跳过该路径点；加载异常时记录WARNING日志而不崩溃喵
     */
    private void loadWaypoint() {
        // 遍历配置文件中所有路径点的key喵
        for (String key : waypointsFile.getKeys()) {
            try {
                // 喵~防御：检查世界字段是否存在且世界仍在运行，防止加载已删除世界的路径点崩溃喵
                if (waypointsFile.contains(key + ".world")
                        && Bukkit.getWorld(waypointsFile.getString(key + ".world")) != null) {
                    // 读取该路径点的显示名称喵
                    String waypointName = waypointsFile.getString(key + ".name");
                    // 读取该路径点的Location坐标信息喵
                    Location loc = waypointsFile.getLocation(key);
                    // 构造Waypoint对象并加入内存列表喵
                    waypoints.add(new Waypoint(this, key, loc, waypointName));
                }
            } catch (Exception x) {
                // 喵~防御：捕获所有异常，避免单个路径点加载失败导致整个档案加载失败，记录警告日志方便排查喵
                Slimefun.logger()
                        .log(
                                Level.WARNING,
                                x,
                                () -> "Could not load Waypoint \"" + key + "\" for Player \""
                                        + getOwner().getName() + '"');
            }
        }
    }

    /**
     * This method provides a fast way to access the armor of a {@link Player}.
     * It returns a cached version, represented by {@link HashedArmorpiece}.
     *
     * @return The cached armor for this {@link Player}
     */
    public @Nonnull HashedArmorpiece[] getArmor() {
        return armor;
    }

    /**
     * This returns the {@link UUID} this {@link PlayerProfile} is linked to.
     *
     * @return The {@link UUID} of our {@link PlayerProfile}
     */
    public @Nonnull UUID getUUID() {
        return owner;
    }

    /**
     * This method returns whether the {@link Player} has logged off.
     * If this is true, then the Profile can be removed from RAM.
     *
     * @return Whether the Profile is marked for deletion
     */
    public boolean isMarkedForDeletion() {
        return markedForDeletion;
    }

    /**
     * This method returns whether the Profile has unsaved changes
     *
     * @return Whether there are unsaved changes
     */
    public boolean isDirty() {
        return dirty;
    }

    /**
     * This method will save the Player's Researches and Backpacks to the hard drive
     */
    /*
     * 保存玩家档案的同步方法，将路径点配置写入磁盘喵
     * 整体思路：通过processProfiles防止并发重复保存，保存完成后重置dirty标记喵
     * 边界条件：若该UUID已在处理队列中则直接返回，避免重复写盘喵
     */
    public void save() {
        // 喵~防御：若该玩家档案已在保存流程中，直接返回避免重入喵
        if (processProfiles.containsKey(owner)) {
            return;
        }

        try {
            // 将本档案的UUID加入正在处理的Map，标记为保存中喵
            processProfiles.put(owner, true);
            // As waypoints still store in file, just keep this method here for now...
            // 将路径点数据写入yml配置文件喵
            waypointsFile.save();
            // 保存完成后清除脏标记，表示已无未保存变更喵
            dirty = false;
        } finally {
            // 无论保存成功还是失败，都从处理队列中移除，防止死锁喵
            processProfiles.remove(owner);
        }
    }
    /**
     * This method will save the Player's waypoint to the hard drive
     */
    public void saveAsync() {
        // 通过数据库管理器的档案数据控制器，将路径点数据异步写入数据库喵
        Slimefun.getDatabaseManager().getProfileDataController().saveWaypoints(this);
    }

    /**
     * This method sets the Player's "researched" status for this Research.
     * Use the boolean to unlock or lock the {@link Research}
     *
     * @param research
     *            The {@link Research} that should be unlocked or locked
     * @param unlock
     *            Whether the {@link Research} should be unlocked or locked
     */
    public void setResearched(@Nonnull Research research, boolean unlock) {
        // 喵~防御：research不能为null，否则后续操作会空指针崩溃喵
        Validate.notNull(research, "Research must not be null!");
        // markDirty();

        // 根据unlock参数决定解锁还是锁定该Research喵
        if (unlock) {
            // 将该Research加入已解锁集合喵
            researches.add(research);
        } else {
            // 从已解锁集合中移除该Research喵
            researches.remove(research);
        }

        // 同步将解锁状态持久化到数据库，以玩家UUID和Research的key作为标识喵
        Slimefun.getDatabaseManager()
                .getProfileDataController()
                .setResearch(owner.toString(), research.getKey(), unlock);
    }

    /**
     * This method returns whether the {@link Player} has unlocked the given {@link Research}
     *
     * @param research
     *            The {@link Research} that is being queried
     *
     * @return Whether this {@link Research} has been unlocked
     */
    public boolean hasUnlocked(@Nullable Research research) {
        // 喵~防御：research为null表示该物品无需研究，直接返回true允许使用喵
        if (research == null) {
            // No Research, no restriction
            return true;
        }

        // 若Research被禁用则视为已解锁；否则检查玩家的已解锁集合中是否包含该Research喵
        return !research.isEnabled() || researches.contains(research);
    }

    /**
     * This method returns whether this {@link Player} has unlocked all {@link Research Researches}.
     *
     * @return Whether they unlocked every {@link Research}
     */
    public boolean hasUnlockedEverything() {
        // 遍历服务器中注册的所有Research，逐一检查玩家是否已解锁喵
        for (Research research : Slimefun.getRegistry().getResearches()) {
            // If there is a single Research not unlocked: They haven't unlocked everything.
            // 一旦发现任何一个Research未解锁，立即返回false节省遍历时间喵
            if (!hasUnlocked(research)) {
                return false;
            }
        }

        // Player has everything unlocked - Hooray!
        // 循环结束没有提前返回，说明全部Research都已解锁，返回true喵
        return true;
    }

    /**
     * This Method will return all Researches that this {@link Player} has unlocked
     *
     * @return A {@code Hashset<Research>} of all Researches this {@link Player} has unlocked
     */
    public @Nonnull Set<Research> getResearches() {
        // 返回已解锁Research集合的不可变副本，防止外部代码直接修改内部集合喵
        return ImmutableSet.copyOf(researches);
    }

    /**
     * This returns a {@link List} of all {@link Waypoint Waypoints} belonging to this
     * {@link PlayerProfile}.
     *
     * @return A {@link List} containing every {@link Waypoint}
     */
    public @Nonnull List<Waypoint> getWaypoints() {
        // 返回路径点列表的不可变副本，防止外部代码修改内部路径点列表喵
        return ImmutableList.copyOf(waypoints);
    }

    /**
     * This adds the given {@link Waypoint} to the {@link List} of {@link Waypoint Waypoints}
     * of this {@link PlayerProfile}.
     *
     * @param waypoint
     *            The {@link Waypoint} to add
     */
    public void addWaypoint(@Nonnull Waypoint waypoint) {
        // 喵~防御：waypoint不能为null，否则后续getId()会空指针崩溃喵
        Validate.notNull(waypoint, "Cannot add a 'null' waypoint!");

        // 检查是否已存在相同ID的路径点，避免重复添加喵
        for (Waypoint wp : waypoints) {
            // 喵~防御：若发现相同ID的路径点则抛出异常，防止数据混乱喵
            if (wp.getId().equals(waypoint.getId())) {
                throw new IllegalArgumentException("A Waypoint with that id already exists for this Player");
            }
        }

        // 检查路径点数量是否达到上限，未达上限才允许添加喵
        if (waypoints.size() < Slimefun.getGPSNetwork().getMaxWaypoints()) {
            // 将路径点加入内存列表喵
            waypoints.add(waypoint);

            // 将路径点的Location坐标写入配置文件喵
            waypointsFile.setValue(waypoint.getId(), waypoint.getLocation());
            // 将路径点的显示名称写入配置文件喵
            waypointsFile.setValue(waypoint.getId() + ".name", waypoint.getName());
            // 标记档案有未保存变更喵
            markDirty();
            // just save async immediately
            // 立即触发异步保存，将变更持久化到数据库喵
            saveAsync();
        }
    }

    /**
     * This removes the given {@link Waypoint} from the {@link List} of {@link Waypoint Waypoints}
     * of this {@link PlayerProfile}.
     *
     * @param waypoint
     *            The {@link Waypoint} to remove
     */
    public void removeWaypoint(@Nonnull Waypoint waypoint) {
        // 喵~防御：waypoint不能为null，否则remove操作会空指针崩溃喵
        Validate.notNull(waypoint, "Cannot remove a 'null' waypoint!");

        // 尝试从内存列表中移除路径点，若成功则同步更新配置文件喵
        if (waypoints.remove(waypoint)) {
            // 将配置文件中该路径点的值设为null以删除记录喵
            waypointsFile.setValue(waypoint.getId(), null);
            // 标记档案有未保存变更喵
            markDirty();
            // just save async immediately
            // 立即触发异步保存，将删除操作持久化到数据库喵
            saveAsync();
        }
    }

    /**
     * Call this method if the Player has left.
     * The profile can then be removed from RAM.
     */
    public final void markForDeletion() {
        // 将删除标记置为true，表示玩家已离线，档案可以从内存中释放喵
        markedForDeletion = true;
    }

    /**
     * Call this method if this Profile has unsaved changes.
     */
    public final void markDirty() {
        // 将dirty标记置为true，表示该档案存在待持久化的变更喵
        dirty = true;
    }

    /*
     * 分配下一个背包编号并持久化更新喵
     * 整体思路：将背包计数器自增，然后将新数值同步保存到数据库，最后返回新编号喵
     */
    public int nextBackpackNum() {
        // 背包计数自增，分配新的背包编号喵
        backpackNum++;
        // 将最新背包数量同步保存到数据库喵
        Slimefun.getDatabaseManager().getProfileDataController().saveProfileBackpackCount(this);
        // 返回新分配的背包编号喵
        return backpackNum;
    }

    public int getBackpackCount() {
        // 返回当前已创建的背包总数量喵
        return backpackNum;
    }

    public void setBackpackCount(int count) {
        // 取当前值和传入值的较大者，确保背包数量只增不减，防止数据回退喵
        backpackNum = Math.max(backpackNum, count);
        // 将更新后的背包数量同步保存到数据库喵
        Slimefun.getDatabaseManager().getProfileDataController().saveProfileBackpackCount(this);
    }

    /*
     * 统计传入研究集合中"至少有一个已启用物品"的Research数量喵
     * 输入：researches——要统计的Research集合喵
     * 输出：有效Research的数量（排除没有任何已启用物品的Research）喵
     * 边界条件：若集合为空返回0喵
     */
    private int countNonEmptyResearches(@Nonnull Collection<Research> researches) {
        // 初始化有效Research计数器喵
        int count = 0;
        // 遍历集合中每一个Research，判断其是否有已启用的物品喵
        for (Research research : researches) {
            // 只统计包含已启用物品的Research，避免空Research影响进度计算喵
            if (research.hasEnabledItems()) {
                count++;
            }
        }
        return count;
    }

    /**
     * This method gets the research title, as defined in {@code config.yml},
     * of this {@link PlayerProfile} based on the fraction
     * of unlocked {@link Research}es of this player.
     *
     * @return The research title of this {@link PlayerProfile}
     */
    public @Nonnull String getTitle() {
        // 从注册表获取所有研究等级称号列表，顺序对应从低到高的研究进度喵
        List<String> titles = Slimefun.getRegistry().getResearchRanks();

        // 统计服务器上所有有效Research（含已启用物品）的总数量喵
        int allResearches = countNonEmptyResearches(Slimefun.getRegistry().getResearches());
        // 计算玩家已解锁Research占总数的比例（0.0到1.0之间）喵
        float fraction = (float) countNonEmptyResearches(researches) / allResearches;
        // 根据比例映射到称号列表的索引位置，比例越高索引越大对应越高等级称号喵
        int index = (int) (fraction * (titles.size() - 1));

        // 返回对应索引的称号字符串喵
        return titles.get(index);
    }

    /**
     * This sends the statistics for the specified {@link CommandSender}
     * to the {@link CommandSender}. This includes research title, research progress
     * and total xp spent.
     *
     * @param sender The {@link CommandSender} for which to get the statistics and send them to.
     */
    public void sendStats(@Nonnull CommandSender sender) {
        // 统计该玩家已解锁的有效Research数量喵
        int unlockedResearches = countNonEmptyResearches(getResearches());
        // 计算解锁所有研究总共消耗的经验等级数量喵
        int levels = getResearches().stream().mapToInt(Research::getLevelCost).sum();
        // 统计服务器上所有有效Research的总数量喵
        int allResearches = countNonEmptyResearches(Slimefun.getRegistry().getResearches());

        // 计算研究完成百分比，保留两位小数（先*10000再除以100.0F等价于四舍五入到小数点后两位）喵
        float progress = Math.round(((unlockedResearches * 100.0F) / allResearches) * 100.0F) / 100.0F;

        // 发送空行作为间隔增加可读性喵
        sender.sendMessage("");
        // 发送玩家名称标题行喵
        sender.sendMessage(ChatColors.color("&7玩家研究统计: &b" + getPlayer()));
        sender.sendMessage("");
        // 发送当前研究等级称号喵
        sender.sendMessage(ChatColors.color("&7研究等级: " + ChatColor.AQUA + getTitle()));
        // 发送研究进度百分比及已解锁/总数的详细信息，颜色随进度动态变化喵
        sender.sendMessage(ChatColors.color("&7研究进度: "
                + NumberUtils.getColorFromPercentage(progress)
                + progress
                + " &r% "
                + ChatColor.YELLOW
                + '('
                + unlockedResearches
                + " / "
                + allResearches
                + ')'));
        // 发送解锁研究所有消耗的经验等级总计喵
        sender.sendMessage(ChatColors.color("&7解锁总耗费经验: " + ChatColor.AQUA + levels));
    }

    /**
     * This returns the {@link Player} who this {@link PlayerProfile} belongs to.
     * If the {@link Player} is offline, null will be returned.
     *
     * @return The {@link Player} of this {@link PlayerProfile} or null
     */
    public @Nullable Player getPlayer() {
        // 喵~防御：先检查owner是否不为null才调用getPlayer()，避免空指针喵
        if (getOwner() != null) {
            // 通过OfflinePlayer获取在线的Player对象，若玩家已下线则返回null喵
            return getOwner().getPlayer();
        }

        return null;
    }

    /**
     * This returns the {@link GuideHistory} of this {@link Player}.
     * It is basically that player's browsing history.
     *
     * @return The {@link GuideHistory} of this {@link Player}
     */
    public @Nonnull GuideHistory getGuideHistory() {
        // 返回该玩家的Slimefun指南浏览历史对象喵
        return guideHistory;
    }

    /*
     * 通过UUID获取玩家档案并异步回调，内部将UUID转换为OfflinePlayer后委托给get()方法喵
     */
    public static boolean fromUUID(@Nonnull UUID uuid, @Nonnull Consumer<PlayerProfile> callback) {
        // 通过Bukkit根据UUID获取OfflinePlayer对象，再调用get方法查找或加载档案喵
        return get(Bukkit.getOfflinePlayer(uuid), callback);
    }

    /**
     * Get the {@link PlayerProfile} for a {@link OfflinePlayer} asynchronously.
     *
     * @param p
     *            The {@link OfflinePlayer} who's {@link PlayerProfile} to retrieve
     * @param callback
     *            The callback with the {@link PlayerProfile}
     *
     * @return If the {@link OfflinePlayer} was cached or not.
     */
    public static boolean get(@Nonnull OfflinePlayer p, @Nonnull Consumer<PlayerProfile> callback) {
        // 喵~防御：p不能为null，否则getUniqueId()会空指针崩溃喵
        Validate.notNull(p, "Cannot get a PlayerProfile for: null!");

        // 获取玩家的UUID用于后续Map查找喵
        UUID uuid = p.getUniqueId();
        // 尝试从缓存注册表中直接获取已存在的玩家档案喵
        PlayerProfile profile = Slimefun.getRegistry().getPlayerProfiles().get(uuid);

        // 若缓存中存在且未被标记删除，直接执行回调并返回true（命中缓存）喵
        if (profile != null && !profile.markedForDeletion) {
            callback.accept(profile);
            return true;
        }

        // 喵~防御：若该UUID已在processProfiles中，说明正在异步加载中，避免重复触发加载喵
        if (processProfiles.containsKey(uuid)) {
            // 当前玩家档案正在加载
            return false;
        }

        // 将UUID加入处理队列，标记为正在加载中防止并发重复加载喵
        processProfiles.put(uuid, true);

        // 异步获取或创建玩家档案喵
        getOrCreate(p, callback);

        // 档案未命中缓存，返回false表示需要等待异步加载完成喵
        return false;
    }

    /**
     * This requests an instance of {@link PlayerProfile} to be loaded for the given {@link OfflinePlayer}.
     * This method will return true if the {@link PlayerProfile} was already found.
     *
     * @param p
     *            The {@link OfflinePlayer} to request the {@link PlayerProfile} for.
     *
     * @return Whether the {@link PlayerProfile} was already loaded
     */
    public static boolean request(@Nonnull OfflinePlayer p) {
        // 喵~防御：p不能为null，否则getUniqueId()会空指针崩溃喵
        Validate.notNull(p, "Cannot request a Profile for null");

        // 尝试从缓存中获取玩家档案喵
        var profile = Slimefun.getRegistry().getPlayerProfiles().get(p.getUniqueId());
        // 若缓存中不存在或已被标记删除，则需要触发异步加载喵
        if (profile == null || profile.markedForDeletion) {
            // 当前玩家档案正在被加载
            // 喵~防御：若已在加载队列中则直接返回false，防止重复触发加载喵
            if (processProfiles.containsKey(p.getUniqueId())) {
                return false;
            }

            // 触发异步加载档案，不设置回调（仅预加载到缓存）喵
            getOrCreate(p, null);
            return false;
        }

        // 缓存命中且有效，返回true表示档案已就绪喵
        return true;
    }

    /**
     * This method tries to search for a {@link PlayerProfile} of the given {@link OfflinePlayer}.
     * The result of this method is an {@link Optional}, if no {@link PlayerProfile} was found, an empty
     * {@link Optional} will be returned.
     *
     * @param p
     *            The {@link OfflinePlayer} to get the {@link PlayerProfile} for
     *
     * @return An {@link Optional} describing the result
     */
    public static @Nonnull Optional<PlayerProfile> find(@Nonnull OfflinePlayer p) {
        // 从缓存注册表中查找该玩家的档案喵
        var re = Slimefun.getRegistry().getPlayerProfiles().get(p.getUniqueId());
        // 喵~防御：若缓存中不存在或已标记删除，返回空Optional避免返回无效档案喵
        if (re == null || re.markedForDeletion) {
            return Optional.empty();
        }
        // 档案有效，包装为Optional返回喵
        return Optional.of(re);
    }

    /*
     * 返回当前所有缓存玩家档案的迭代器，可用于遍历服务器所有在线玩家档案喵
     */
    public static @Nonnull Iterator<PlayerProfile> iterator() {
        // 获取注册表中所有玩家档案Map的values集合的迭代器喵
        return Slimefun.getRegistry().getPlayerProfiles().values().iterator();
    }

    /*
     * 检查玩家是否穿戴了能提供指定ProtectionType完整保护的盔甲套装喵
     * 整体思路：遍历四件盔甲槽位，找到有ProtectiveArmor属性且匹配指定类型的护甲；
     *   若护甲不需要完整套装则立即返回true；否则统计同套装ID的匹配护甲数量，等于4时视为完整保护喵
     * 输入：type——要检查的保护类型（如防火、防毒等）喵
     * 输出：是否具有完整保护（true/false）喵
     * 边界条件：不同套装的护甲不能混合计数，setId确保只统计同一套装喵
     */
    public boolean hasFullProtectionAgainst(@Nonnull ProtectionType type) {
        // 喵~防御：type不能为null，否则后续比较会空指针崩溃喵
        Validate.notNull(type, "ProtectionType must not be null.");

        // 统计匹配该保护类型的护甲件数喵
        int armorCount = 0;
        // 记录匹配的套装ID，用于确保同一套装才能计数喵
        NamespacedKey setId = null;

        // 遍历玩家所有盔甲槽（头盔/胸甲/护腿/靴子）喵
        for (HashedArmorpiece armorpiece : armor) {
            // 获取该盔甲槽当前装备的SlimefunArmorPiece，若槽位为空返回empty喵
            Optional<SlimefunArmorPiece> armorPiece = armorpiece.getItem();
            // 检查盔甲是否存在且实现了ProtectiveArmor接口喵
            if (armorPiece.isPresent() && armorPiece.get() instanceof ProtectiveArmor protectiveArmor) {
                // 遍历该护甲提供的所有保护类型喵
                for (ProtectionType protectionType : protectiveArmor.getProtectionTypes()) {
                    // 检查护甲的保护类型是否与目标类型匹配喵
                    if (protectionType == type) {
                        // 若该护甲不需要完整套装即可生效，直接返回true喵
                        if (!protectiveArmor.isFullSetRequired()) {
                            return true;
                        } else if (setId == null || setId.equals(protectiveArmor.getArmorSetId())) {
                            // 套装ID相同才计入匹配数量，并记录当前套装ID喵
                            armorCount++;
                            setId = protectiveArmor.getArmorSetId();
                        }
                    }
                }
            }
        }

        // 当同套装的匹配护甲数量达到4件（全套）时，视为完整保护喵
        return armorCount == 4;
    }

    @Override
    public int hashCode() {
        // 使用owner UUID的hashCode作为PlayerProfile的哈希值，确保同一玩家的档案哈希一致喵
        return owner.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        // 若obj是PlayerProfile实例且owner UUID相同，则视为同一档案喵
        return obj instanceof PlayerProfile profile && owner.equals(profile.owner);
    }

    @Override
    public String toString() {
        // 返回包含owner UUID的字符串表示，便于日志调试喵
        return "PlayerProfile {" + owner + "}";
    }

    public OfflinePlayer getOwner() {
        // 通过Bukkit API根据owner UUID获取OfflinePlayer对象，可能返回已下线玩家喵
        return Bukkit.getOfflinePlayer(owner);
    }

    // returns the amount of researches with at least 1 enabled item
    // 统计注册表中所有含已启用物品的Research数量（与countNonEmptyResearches不同，此方法不接受外部集合参数）喵
    private int nonEmptyResearches() {
        // 主人注意：此处使用Stream流遍历所有Research并过滤，当研究数量极多时可能有轻微性能开销，通常无需优化喵
        return (int) Slimefun.getRegistry().getResearches().stream()
                // 过滤：只保留至少有一个已启用物品的Research喵
                .filter(research ->
                        research.getAffectedItems().stream().anyMatch(item -> item.getState() == ItemState.ENABLED))
                // 将流中剩余元素数量转换为int并返回喵
                .count();
    }

    /*
     * 从数据库异步获取或新建玩家档案，完成后执行回调喵
     * 整体思路：通过ProfileDataController发起异步读取；若读到则回调；若读不到则创建新档案再回调喵
     * 边界条件：cb为null时不执行回调（适用于仅预加载场景）；无论成功失败都会从processProfiles中移除该UUID喵
     */
    private static void getOrCreate(OfflinePlayer p, Consumer<PlayerProfile> cb) {
        // 获取档案数据控制器用于执行数据库操作喵
        var controller = Slimefun.getDatabaseManager().getProfileDataController();
        // 发起异步读取档案，传入回调接口处理结果喵
        controller.getProfileAsync(p, new IAsyncReadCallback<>() {
            @Override
            public void onResult(PlayerProfile result) {
                // 读取到已有档案，执行回调（第二个参数false表示不是新建的）喵
                invokeCb(result, false);
                // 档案加载完成后从处理队列中移除UUID喵
                processProfiles.remove(result.getUUID());
            }

            @Override
            public void onResultNotFound() {
                try {
                    // 数据库中无对应档案，为该玩家创建新档案喵
                    var pf = controller.createProfile(p);
                    // 执行回调（第二个参数true表示新建档案）喵
                    invokeCb(pf, true);
                } finally {
                    // 无论创建成功还是失败，都从处理队列中移除UUID防止死锁喵
                    processProfiles.remove(p.getUniqueId());
                }
            }

            /*
             * 执行外部回调并在新建档案时触发AsyncProfileLoadEvent事件喵
             * 输入：pf——玩家档案对象；newlyCreated——是否为本次新建喵
             */
            private void invokeCb(PlayerProfile pf, boolean newlyCreated) {
                // 仅新建档案时才触发异步加载事件，通知其他插件喵
                if (newlyCreated) {
                    AsyncProfileLoadEvent event = new AsyncProfileLoadEvent(pf);
                    // 触发事件，允许其他插件监听并处理新档案喵
                    Bukkit.getPluginManager().callEvent(event);
                }

                // 喵~防御：cb为null时跳过回调，适用于仅预加载不需要后续处理的场景喵
                if (cb != null) {
                    cb.accept(pf);
                }
            }
        });
    }
}
