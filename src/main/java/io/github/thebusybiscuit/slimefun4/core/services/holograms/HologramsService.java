package io.github.thebusybiscuit.slimefun4.core.services.holograms;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

/**
 * This service is responsible for handling holograms.
 *
 * @author TheBusyBiscuit
 *
 * @see HologramOwner
 */
public class HologramsService implements Listener {

    /**
     * The radius in which we scan for holograms
     */
    private static final double RADIUS = 0.5;

    /**
     * The frequency at which to purge.
     * Every 45 seconds.
     */
    private static final long PURGE_RATE = 45L * 20L;

    /**
     * Our {@link Plugin} instance
     */
    private final Plugin plugin;

    /**
     * The default hologram offset
     */
    private final Vector defaultOffset = new Vector(0.5, 0.75, 0.5);

    /**
     * The {@link NamespacedKey} used to store data on a hologram
     */
    private final NamespacedKey persistentDataKey;

    private final NamespacedKey multiLineKey;

    private final NamespacedKey multiLineBaseKey;

    /**
     * Our cache to save {@link Entity} lookups
     */
    private final Map<BlockPosition, Hologram> cache = new HashMap<>();

    private final Map<BlockPosition, List<Hologram>> multiLineCache = new HashMap<>();

    private static final double LINE_SPACING = 0.3;

    // 多行全息最多管理的行数，限制缓存恢复扫描范围并防止异常索引扩大扫描开销喵~
    private static final int MAX_MULTI_LINE_HOLOGRAM_LINES = 16;

    /**
     * This constructs a new {@link HologramsService}.
     *
     * @param plugin
     *            Our {@link Plugin} instance
     */
    public HologramsService(@Nonnull Plugin plugin) {
        this.plugin = plugin;

        // Null-Validation is performed in the NamespacedKey constructor
        persistentDataKey = new NamespacedKey(plugin, "hologram_id");
        multiLineKey = new NamespacedKey(plugin, "multiline_id");
        multiLineBaseKey = new NamespacedKey(plugin, "multiline_base_id");
    }

    /**
     * This will start the {@link HologramsService} and schedule a repeating
     * purge-task.
     */
    public void start() {
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::purge, PURGE_RATE, PURGE_RATE);
        // 注册区块加载监听器，用于延后补扫区块内孤儿全息
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        cleanupOrphanHologramsOnStartup();
    }

    /**
     * 当区块加载时，延后一 tick 扫描并删除该区块内残留的孤儿全息 ArmorStand
     * 解决服务器重启时未加载区块中旧全息未被清理的问题喵~
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        if (e.isNewChunk()) {
            // 喵~防御：新生成的区块不可能有旧全息，直接跳过
            return;
        }
        Chunk chunk = e.getChunk();
        // 延后一 tick 等待实体从磁盘完整反序列化后再扫描
        plugin.getServer().getScheduler().runTask(plugin, () -> cleanupChunkHolograms(chunk));
    }

    /**
     * 扫描指定区块内所有 ArmorStand，仅删除能够确认失去 Slimefun 方块归属的全息实体
     *
     * @param chunk 要扫描的区块
     */
    private void cleanupChunkHolograms(@Nonnull Chunk chunk) {
        // 喵~防御：区块可能在延后的一 tick 内被卸载
        if (!chunk.isLoaded()) {
            return;
        }
        for (Entity entity : chunk.getEntities()) {
            if (isOrphanHologram(entity)) {
                entity.remove();
            }
        }
    }

    /**
     * 判断实体是否是可安全删除的 Slimefun 孤儿全息
     *
     * @param entity 待检查的实体
     * @return 实体带有可验证归属且所属方块不存在时返回 true
     */
    private boolean isOrphanHologram(@Nonnull Entity entity) {
        if (!isHologram(entity)) {
            return false;
        }

        PersistentDataContainer container = entity.getPersistentDataContainer();
        Long singleLinePosition = container.get(persistentDataKey, PersistentDataType.LONG);
        Long multiLineBasePosition = container.get(multiLineBaseKey, PersistentDataType.LONG);

        if (singleLinePosition != null) {
            return !hasBlockDataNearby(new BlockPosition(entity.getWorld(), singleLinePosition));
        }

        if (multiLineBasePosition != null) {
            return !hasBlockDataNearby(new BlockPosition(entity.getWorld(), multiLineBasePosition));
        }

        // 喵~防御：旧版多行全息没有所属位置，无法证明为孤儿时必须保留，避免误删合法显示
        return false;
    }

    private void cleanupOrphanHologramsOnStartup() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntitiesByClass(ArmorStand.class)) {
                if (isOrphanHologram(entity)) {
                    entity.remove();
                    removed++;
                }
            }
        }
        if (removed > 0) {
            Slimefun.logger().info("启动时清除了 " + removed + " 个孤儿全息实体");
        }
    }

    /**
     * This returns the default {@link Hologram} offset.
     *
     * @return The default offset
     */
    @Nonnull
    public Vector getDefaultOffset() {
        return defaultOffset;
    }

    /**
     * This purges any expired {@link Hologram} and orphan holograms
     * whose underlying block no longer has Slimefun data.
     */
    private void purge() {
        Iterator<Map.Entry<BlockPosition, Hologram>> singleIt = cache.entrySet().iterator();

        while (singleIt.hasNext()) {
            Map.Entry<BlockPosition, Hologram> entry = singleIt.next();
            Hologram hologram = entry.getValue();

            if (hologram.hasExpired() || !hasBlockDataNearby(entry.getKey())) {
                hologram.remove();
                singleIt.remove();
            }
        }

        Iterator<Map.Entry<BlockPosition, List<Hologram>>> multiIt =
                multiLineCache.entrySet().iterator();
        while (multiIt.hasNext()) {
            Map.Entry<BlockPosition, List<Hologram>> entry = multiIt.next();
            List<Hologram> lines = entry.getValue();
            boolean shouldRemove = lines.stream().anyMatch(Hologram::hasExpired) || !hasBlockDataNearby(entry.getKey());

            if (shouldRemove) {
                for (Hologram hologram : lines) {
                    hologram.remove();
                }
                lines.clear();
            }
            if (lines.isEmpty()) {
                multiIt.remove();
            }
        }
    }

    private boolean hasBlockDataNearby(@Nonnull BlockPosition bp) {
        Location loc = bp.toLocation();
        if (loc.getWorld() == null) {
            return true;
        }

        if (StorageCacheUtils.hasSlimefunBlock(loc)) {
            return true;
        }

        loc.subtract(0, 1, 0);
        return StorageCacheUtils.hasSlimefunBlock(loc);
    }

    /**
     * 玩家手动干预时清理方块位置附近的孤儿全息。
     * 当玩家在可能曾存在机器的位置放置或破坏方块时调用此方法，
     * 立即检查和清除可能残留的全息（单行 + 多行）。
     * 此方法必须在主线程上调用。
     *
     * @param blockLoc
     *            方块的 {@link Location}（机器可能曾在此位置）
     */
    public void cleanOrphanHolograms(@Nonnull Location blockLoc) {
        Validate.notNull(blockLoc, "Location cannot be null");

        if (!Bukkit.isPrimaryThread()) {
            Slimefun.runSync(() -> cleanOrphanHolograms(blockLoc));
            return;
        }

        BlockPosition bpSame = new BlockPosition(blockLoc);
        BlockPosition bpAbove = new BlockPosition(blockLoc.clone().add(0, 1, 0));

        removeHologram(bpSame.toLocation());
        removeHologram(bpAbove.toLocation());
        removeMultiLineHologram(bpSame.toLocation());
        removeMultiLineHologram(bpAbove.toLocation());
    }

    /**
     * This returns the {@link Hologram} associated with the given {@link Location}.
     * If createIfNoneExists is set to true a new {@link ArmorStand} will be spawned
     * if no existing one could be found.
     *
     * @param loc
     *            The {@link Location}
     * @param createIfNoneExists
     *            Whether to create a new {@link ArmorStand} if none was found
     *
     * @return The existing (or newly created) hologram
     */
    @Nullable private Hologram getHologram(@Nonnull Location loc, boolean createIfNoneExists) {
        Validate.notNull(loc, "Location cannot be null");

        BlockPosition position = new BlockPosition(loc);
        Hologram hologram = cache.get(position);

        // Check if the ArmorStand was cached and still exists
        if (hologram != null && !hologram.hasDespawned()) {
            return hologram;
        }

        // Scan all nearby entities which could be possible holograms
        Collection<Entity> holograms = loc.getWorld().getNearbyEntities(loc, RADIUS, RADIUS, RADIUS, this::isHologram);

        for (Entity n : holograms) {
            if (n instanceof ArmorStand) {
                PersistentDataContainer container = n.getPersistentDataContainer();

                /*
                 * Any hologram we created will have a persistent data key for identification.
                 * Make sure that the value matches our BlockPosition.
                 */
                if (hasHologramData(container, position)) {
                    if (hologram != null) {
                        // Fixes #2927 - Remove any duplicates we find
                        n.remove();
                    } else {
                        hologram = getAsHologram(position, n, container);
                    }
                }
            }
        }

        if (hologram == null && createIfNoneExists) {
            // Spawn a new ArmorStand
            ArmorStand armorstand = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
            PersistentDataContainer container = armorstand.getPersistentDataContainer();

            return getAsHologram(position, armorstand, container);
        } else {
            return hologram;
        }
    }

    @ParametersAreNonnullByDefault
    private boolean hasHologramData(PersistentDataContainer container, BlockPosition position) {
        if (container.has(persistentDataKey, PersistentDataType.LONG)) {
            long value = container.get(persistentDataKey, PersistentDataType.LONG);
            return value == position.getPosition();
        } else {
            return false;
        }
    }

    /**
     * This checks if a given {@link Entity} is an {@link ArmorStand}
     * and whether it has the correct attributes to be considered a {@link Hologram}.
     *
     * @param n
     *            The {@link Entity} to check
     *
     * @return Whether this could be a hologram
     */
    private boolean isHologram(@Nonnull Entity n) {
        if (n instanceof ArmorStand armorStand) {
            // The absolute minimum requirements to count as a hologram
            return !armorStand.isVisible() && armorStand.isSilent() && !armorStand.hasGravity();
        } else {
            return false;
        }
    }

    /**
     * This will cast the {@link Entity} to an {@link ArmorStand} and it will apply
     * all necessary attributes to the {@link ArmorStand}, then return a {@link Hologram}.
     *
     * @param position
     *            The {@link BlockPosition} of this hologram
     * @param entity
     *            The {@link Entity}
     * @param container
     *            The {@link PersistentDataContainer} of the given {@link Entity}
     *
     * @return The {@link Hologram}
     */
    @Nullable private Hologram getAsHologram(
            @Nonnull BlockPosition position, @Nonnull Entity entity, @Nonnull PersistentDataContainer container) {
        if (entity instanceof ArmorStand armorStand) {
            armorStand.setVisible(false);
            armorStand.setInvulnerable(true);
            armorStand.setSilent(true);
            armorStand.setMarker(true);
            armorStand.setAI(false);
            armorStand.setGravity(false);
            armorStand.setRemoveWhenFarAway(false);

            // Set a persistent tag to re-identify the correct hologram later
            container.set(persistentDataKey, PersistentDataType.LONG, position.getPosition());

            // Store in cache for faster access
            Hologram hologram = new Hologram(armorStand.getUniqueId());
            cache.put(position, hologram);

            return hologram;
        } else {
            // This should never be reached
            return null;
        }
    }

    /**
     * This updates the {@link Hologram}.
     * You can use it to set the nametag or other properties.
     * <p>
     * <strong>This method must be executed on the main {@link Server} {@link Thread}.</strong>
     *
     * @param loc
     *            The {@link Location}
     * @param consumer
     *            The callback to run
     */
    private void updateHologram(@Nonnull Location loc, @Nonnull Consumer<Hologram> consumer) {
        Validate.notNull(loc, "Location must not be null");
        Validate.notNull(consumer, "Callbacks must not be null");

        Runnable runnable = () -> {
            try {
                Hologram hologram = getHologram(loc, true);

                if (hologram != null) {
                    consumer.accept(hologram);
                }
            } catch (Exception | LinkageError x) {
                Slimefun.logger().log(Level.SEVERE, "Hologram located at {0}", new BlockPosition(loc));
                Slimefun.logger().log(Level.SEVERE, "Something went wrong while trying to update this hologram", x);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            runnable.run();
        } else {
            Slimefun.runSync(runnable);
        }
    }

    /**
     * This removes the {@link Hologram} at that given {@link Location}.
     * <p>
     * <strong>This method must be executed on the main {@link Server} {@link Thread}.</strong>
     *
     * @param loc
     *            The {@link Location}
     *
     * @return Whether the {@link Hologram} could be removed, false if the {@link Hologram} does not
     *         exist or was already removed
     */
    public boolean removeHologram(@Nonnull Location loc) {
        Validate.notNull(loc, "Location cannot be null");

        if (Bukkit.isPrimaryThread()) {
            try {
                Hologram hologram = getHologram(loc, false);

                if (hologram != null) {
                    cache.remove(new BlockPosition(loc));
                    hologram.remove();
                    return true;
                } else {
                    return false;
                }
            } catch (Exception | LinkageError x) {
                Slimefun.logger().log(Level.SEVERE, "Hologram located at {0}", new BlockPosition(loc));
                Slimefun.logger().log(Level.SEVERE, "Something went wrong while trying to remove this hologram", x);
                return false;
            }
        } else {
            throw new UnsupportedOperationException("You cannot remove a hologram asynchronously.");
        }
    }

    /**
     * This will update the label of the {@link Hologram}.
     *
     * @param loc
     *            The {@link Location} of this {@link Hologram}
     * @param label
     *            The label to set, can be null
     */
    public void setHologramLabel(@Nonnull Location loc, @Nullable String label) {
        Validate.notNull(loc, "Location must not be null");

        updateHologram(loc, hologram -> hologram.setLabel(label));
    }

    /**
     * Creates or updates a multi-line hologram at the given base {@link Location}.
     * Each line is rendered as a separate {@link ArmorStand} stacked vertically.
     *
     * @param baseLoc
     *            The base {@link Location} (block position)
     * @param lines
     *            The text lines to display (each line is one ArmorStand)
     */
    public void setMultiLineHologram(@Nonnull Location baseLoc, @Nonnull String... lines) {
        Validate.notNull(baseLoc, "Location must not be null");

        if (!Bukkit.isPrimaryThread()) {
            Slimefun.runSync(() -> setMultiLineHologram(baseLoc, lines));
            return;
        }

        BlockPosition position = new BlockPosition(baseLoc);

        // 同时清除该位置已有的单行全息（避免闪烁/重叠）
        removeHologram(baseLoc);

        // 优先复用完整缓存，避免能源调节器高频刷新时重复扫描世界实体喵~
        List<Hologram> existing = getValidMultiLineCache(position);
        // 缓存缺失或失效时，从带归属标签的实体恢复行列表并顺带去重喵~
        if (existing == null) {
            existing = restoreMultiLineHolograms(baseLoc, position);
        }

        // 空文本列表表示此基准位置不应继续保留任何多行显示喵~
        if (lines.length == 0) {
            // 删除所有可验证归属的旧行，避免缓存重建后继续叠加喵~
            removeMultiLineHologram(baseLoc);
            return;
        }

        // 逐行复用有效实体或仅为缺失行创建新的 ArmorStand 喵~
        for (int index = 0; index < lines.length; index++) {
            // 读取当前索引对应的缓存行，缺失索引会返回 null 喵~
            Hologram hologram = index < existing.size() ? existing.get(index) : null;
            // 检查缓存行仍存在且归属于当前多行全息喵~
            if (isValidMultiLineHologram(hologram, position, index)) {
                // 更新现有行文本，不生成新的实体喵~
                hologram.setLabel(lines[index] != null ? ChatColors.color(lines[index]) : null);
                continue;
            }

            // 根据行索引计算缺失行的垂直显示坐标喵~
            Location lineLoc = baseLoc.clone().subtract(0, index * LINE_SPACING, 0);
            // 创建前只清理同一基准和同一索引的可验证重复行喵~
            removeOwnedMultiLineHolograms(lineLoc, position, index);
            // 为当前缺失索引创建并标记新的多行全息实体喵~
            Hologram createdHologram = createLineArmorStand(lineLoc, position, index, lines[index]);
            // 将列表扩展到当前索引，保持行索引与列表索引一一对应喵~
            while (existing.size() <= index) {
                existing.add(null);
            }
            // 回填新创建的行，供后续更新快速复用喵~
            existing.set(index, createdHologram);
        }

        // 删除本次显示行数之外的旧行，避免内容缩短时遗留实体喵~
        while (existing.size() > lines.length) {
            // 从末尾移除，使剩余列表继续按行索引排列喵~
            Hologram removedHologram = existing.remove(existing.size() - 1);
            // 喵~防御：缓存恢复可能留下索引空洞，空条目无需删除实体喵~
            if (removedHologram != null) {
                // 删除不再需要的旧行实体喵~
                removedHologram.remove();
            }
        }
        // 将规范化后的行列表写回缓存，后续 tick 可走无扫描快速路径喵~
        multiLineCache.put(position, existing);
    }

    /**
     * 验证缓存中的多行全息是否仍完整地对应当前基准位置。
     *
     * @param basePosition 当前多行全息的基准位置编码
     * @return 缓存完整时返回对应行列表，否则返回 null 触发世界实体恢复
     */
    @Nullable private List<Hologram> getValidMultiLineCache(@Nonnull BlockPosition basePosition) {
        // 读取当前基准位置的内存缓存，正常 tick 应从这里快速返回喵~
        List<Hologram> cachedHolograms = multiLineCache.get(basePosition);
        // 喵~防御：没有缓存时必须扫描已持久化标记的实体，不能盲目创建新行喵~
        if (cachedHolograms == null) {
            return null;
        }
        // 逐个确认缓存行仍有效且与它应有的行索引一致喵~
        for (int index = 0; index < cachedHolograms.size(); index++) {
            // 缓存行失效时返回 null，让恢复流程重新发现并去重喵~
            if (!isValidMultiLineHologram(cachedHolograms.get(index), basePosition, index)) {
                return null;
            }
        }
        // 返回已验证的缓存，避免高频更新扫描附近实体喵~
        return cachedHolograms;
    }

    /**
     * 检查一条缓存全息是否仍是指定基准位置和行索引的有效实体。
     *
     * @param hologram 待检查的缓存全息
     * @param basePosition 预期的多行全息基准位置
     * @param index 预期的行索引
     * @return 实体存在且 PDC 归属信息完全匹配时返回 true
     */
    private boolean isValidMultiLineHologram(
            @Nullable Hologram hologram, @Nonnull BlockPosition basePosition, int index) {
        // 喵~防御：空缓存槽位无法代表有效实体，必须触发恢复或创建喵~
        if (hologram == null) {
            return false;
        }
        // 读取 ArmorStand 同时检测实体是否已被外部删除喵~
        ArmorStand armorStand = hologram.getArmorStand();
        // 喵~防御：失效实体不能继续复用，避免更新写入已删除对象喵~
        if (armorStand == null || !armorStand.isValid()) {
            return false;
        }
        // 根据 PDC 标签验证实体归属，避免缓存位置复用时串用其他机器的全息喵~
        return isOwnedMultiLineHologram(armorStand, basePosition, index);
    }

    /**
     * 判断 ArmorStand 是否由当前服务创建，并精确归属于指定多行全息。
     *
     * @param entity 待验证的实体
     * @param basePosition 预期的多行全息基准位置
     * @param expectedIndex 预期的行索引，传入负数时忽略行索引校验
     * @return PDC 标签完整且归属匹配时返回 true
     */
    private boolean isOwnedMultiLineHologram(
            @Nonnull Entity entity, @Nonnull BlockPosition basePosition, int expectedIndex) {
        // 喵~防御：仅处理本服务外观定义的 ArmorStand，避免误删其他类型实体喵~
        if (!(entity instanceof ArmorStand) || !isHologram(entity)) {
            return false;
        }
        // 读取实体的持久化标签以验证多行全息所有权喵~
        PersistentDataContainer container = entity.getPersistentDataContainer();
        // 读取创建时写入的共享基准位置编码喵~
        Long storedBasePosition = container.get(multiLineBaseKey, PersistentDataType.LONG);
        // 读取创建时写入的行索引喵~
        Integer storedIndex = container.get(multiLineKey, PersistentDataType.INTEGER);
        // 喵~防御：缺少任一标签的旧实体无法可靠归属，保守不处理喵~
        if (storedBasePosition == null || storedIndex == null) {
            return false;
        }
        // 基准位置不相同表示属于其他机器，绝不能触碰喵~
        if (storedBasePosition.longValue() != basePosition.getPosition()) {
            return false;
        }
        // 负行索引不是合法 API 产物，拒绝作为正常缓存行复用喵~
        if (storedIndex < 0) {
            return false;
        }
        // 负期望索引表示调用方只验证 base 所有权，否则要求索引精确匹配喵~
        return expectedIndex < 0 || storedIndex == expectedIndex;
    }

    /**
     * 从世界中恢复指定基准位置的多行实体，并删除每行的重复实例。
     *
     * @param baseLoc 多行显示的基准坐标
     * @param basePosition 多行显示的基准位置编码
     * @return 按行索引排列的已恢复缓存列表
     */
    @Nonnull
    private List<Hologram> restoreMultiLineHolograms(@Nonnull Location baseLoc, @Nonnull BlockPosition basePosition) {
        // 创建按行索引排列的恢复列表，空槽位代表该行实体尚不存在喵~
        List<Hologram> restoredHolograms = new ArrayList<>();
        // 逐行扫描有限的垂直显示列，避免遍历整个区块的实体喵~
        for (int index = 0; index < MAX_MULTI_LINE_HOLOGRAM_LINES; index++) {
            // 计算当前候选行的预期坐标喵~
            Location lineLoc = baseLoc.clone().subtract(0, index * LINE_SPACING, 0);
            // 主人注意：仅在缓存失效时扫描附近实体，正常高频更新会走缓存快速路径喵~
            Collection<Entity> candidates =
                    lineLoc.getWorld().getNearbyEntities(lineLoc, RADIUS, RADIUS, RADIUS, this::isHologram);
            // 用于保留当前索引的第一条合法实体，其余同索引实体会被删除喵~
            Hologram restoredHologram = null;
            // 检查候选实体的 PDC 所有权和行索引喵~
            for (Entity candidate : candidates) {
                // 不是当前 base 与当前行索引的多行实体时跳过喵~
                if (!isOwnedMultiLineHologram(candidate, basePosition, index)) {
                    continue;
                }
                // 第一个合法实体成为该行的缓存对象喵~
                if (restoredHologram == null) {
                    restoredHologram = new Hologram(candidate.getUniqueId());
                } else {
                    // 同一机器同行索引的额外实体属于可验证重复项，立即删除喵~
                    candidate.remove();
                }
            }
            // 发现该行时将列表补齐并写入其对应索引喵~
            if (restoredHologram != null) {
                while (restoredHolograms.size() <= index) {
                    restoredHolograms.add(null);
                }
                restoredHolograms.set(index, restoredHologram);
            }
        }
        // 把恢复结果回填缓存，防止下一次更新再次创建重复实体喵~
        multiLineCache.put(basePosition, restoredHolograms);
        // 返回恢复后的行列表供本次更新继续复用喵~
        return restoredHolograms;
    }

    /**
     * 删除指定基准位置和行索引的所有可验证多行实体。
     *
     * @param lineLoc 当前行的预期坐标
     * @param basePosition 当前多行全息的基准位置
     * @param index 需要删除的行索引
     */
    private void removeOwnedMultiLineHolograms(
            @Nonnull Location lineLoc, @Nonnull BlockPosition basePosition, int index) {
        // 在当前行附近寻找实体，并仅移除 PDC 归属完全匹配的重复行喵~
        for (Entity entity : lineLoc.getWorld().getNearbyEntities(lineLoc, RADIUS, RADIUS, RADIUS, this::isHologram)) {
            // 只删除同一调节器同一行的旧实体，保护相邻机器和其他插件显示喵~
            if (isOwnedMultiLineHologram(entity, basePosition, index)) {
                entity.remove();
            }
        }
    }

    @Nonnull
    private Hologram createLineArmorStand(
            @Nonnull Location lineLoc, @Nonnull BlockPosition basePosition, int index, @Nullable String text) {
        // 创建新的 ArmorStand 以补齐确认缺失的多行显示行喵~
        ArmorStand armorstand = (ArmorStand) lineLoc.getWorld().spawnEntity(lineLoc, EntityType.ARMOR_STAND);
        armorstand.setVisible(false);
        armorstand.setInvulnerable(true);
        armorstand.setSilent(true);
        armorstand.setMarker(true);
        armorstand.setAI(false);
        armorstand.setGravity(false);
        armorstand.setRemoveWhenFarAway(false);

        if (text != null) {
            armorstand.setCustomNameVisible(true);
            armorstand.setCustomName(ChatColors.color(text));
        }

        PersistentDataContainer container = armorstand.getPersistentDataContainer();
        container.set(multiLineKey, PersistentDataType.INTEGER, index);
        // 多行全息所有行共享 base 位置，用于重启后验证实体归属
        container.set(multiLineBaseKey, PersistentDataType.LONG, basePosition.getPosition());

        return new Hologram(armorstand.getUniqueId());
    }

    /**
     * 统计指定基准位置当前可管理的全息实体数量。
     *
     * @param baseLoc 全息基准位置喵~
     * @return 单行与多行全息实体总数喵~
     */
    public int countHolograms(@Nonnull Location baseLoc) {
        // 喵~防御：全息实体查询必须在主线程执行，异步调用返回安全值喵~
        if (!Bukkit.isPrimaryThread()) {
            return 0;
        }

        // 统计该位置缓存中的单行全息实体喵~
        int hologramCount = cache.containsKey(new BlockPosition(baseLoc)) ? 1 : 0;
        // 读取该位置缓存中的多行全息实体喵~
        List<Hologram> multilineHolograms = multiLineCache.get(new BlockPosition(baseLoc));
        // 喵~防御：缓存缺失时按零行处理，避免空指针异常喵~
        if (multilineHolograms != null) {
            // 只统计仍然有效的多行实体，避免失效缓存误判数量喵~
            for (Hologram hologram : multilineHolograms) {
                if (hologram != null && hologram.getArmorStand() != null && hologram.getArmorStand().isValid()) {
                    hologramCount++;
                }
            }
        }
        // 返回当前缓存可确认的全息数量喵~
        return hologramCount;
    }

    /**
     * Removes the multi-line hologram at the given base {@link Location}.
     *
     * @param baseLoc
     *            The base {@link Location} (block position)
     */
    public void removeMultiLineHologram(@Nonnull Location baseLoc) {
        // 喵~防御：基准位置不能为空，否则无法判断应清理的实体归属喵~
        Validate.notNull(baseLoc, "Location cannot be null");
        // 异步调用统一切换到主线程，确保 Bukkit 实体操作线程安全喵~
        if (!Bukkit.isPrimaryThread()) {
            // 在主线程重新执行完整清理，避免异步读取世界实体喵~
            Slimefun.runSync(() -> removeMultiLineHologram(baseLoc));
            return;
        }

        // 将基准坐标转换为缓存与 PDC 使用的位置编码喵~
        BlockPosition position = new BlockPosition(baseLoc);
        // 先移除缓存引用，防止清理后旧行被后续更新继续复用喵~
        List<Hologram> cachedHolograms = multiLineCache.remove(position);
        // 喵~防御：缓存可能缺失，只有存在时才遍历对应实体喵~
        if (cachedHolograms != null) {
            // 删除缓存中记录的所有行实体喵~
            for (Hologram hologram : cachedHolograms) {
                // 喵~防御：缓存恢复过程可能包含空槽位，空槽位没有实体可删除喵~
                if (hologram != null) {
                    hologram.remove();
                }
            }
        }

        // 继续扫描可管理的所有行，清除缓存外但 PDC 可验证归属的重复实体喵~
        for (int index = 0; index < MAX_MULTI_LINE_HOLOGRAM_LINES; index++) {
            // 计算当前行的预期垂直坐标喵~
            Location lineLoc = baseLoc.clone().subtract(0, index * LINE_SPACING, 0);
            // 仅删除同一基准位置与同行索引的实体，不能按外观或距离误删喵~
            removeOwnedMultiLineHolograms(lineLoc, position, index);
        }
    }
}
