package io.github.thebusybiscuit.slimefun4.api.player;

import city.norain.slimefun4.holder.SlimefunInventoryHolder;
import city.norain.slimefun4.utils.InventoryUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.InvSnapshot;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.bakedlibs.dough.common.CommonPatterns;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.SlimefunBackpack;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BackpackListener;
import io.github.thebusybiscuit.slimefun4.utils.ThreadUtils;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * This class represents the instance of a {@link SlimefunBackpack} that is ready to
 * be opened.
 *
 * It holds an actual {@link Inventory} and represents the backpack on the
 * level of an individual {@link ItemStack} as opposed to the class {@link SlimefunBackpack}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunBackpack
 * @see BackpackListener
 */
public class PlayerBackpack extends SlimefunInventoryHolder {
    // 背包物品描述中“所有者”标签的原始未着色文本，供外部引用喵
    public static final String LORE_OWNER = "&7所有者: ";
    // 将LORE_OWNER转换为带颜色代码的实际显示文本，用于比对和展示喵
    private static final String COLORED_LORE_OWNER = ChatColors.color(LORE_OWNER);
    // 用于在物品PDC(持久数据容器)中存储背包UUID的命名空间键，标识该物品对应哪个背包喵
    private static final NamespacedKey KEY_BACKPACK_UUID = new NamespacedKey(Slimefun.instance(), "B_UUID");
    // 用于在物品PDC中存储背包主人 UUID的命名空间键，记录谁是这个背包的主人喵
    private static final NamespacedKey KEY_OWNER_UUID = new NamespacedKey(Slimefun.instance(), "OWNER_UUID");
    // 背包的所有者玩家对象（可以是离线玩家）喵
    private final OfflinePlayer owner;
    // 背包的唯一标识符UUID，每个背包实例都不同喵
    private final UUID uuid;
    // 背包的数字ID，用于数据库存储和旧版本兼容喵
    private final int id;
    // 背包的自定义显示名称，可为空字符串（表示使用默认名“背包”）喵
    private String name;
    // 背包格子数量，必须是9的倍数且在9到54之间喵
    private int size;
    // 标记背包是否已失效（比如主人换了背包或数据出错），失效后禁止打开喵
    private boolean isInvalid = false;
    // 这个快照保存了背包上次保存时的内容，每次写入数据库后需要调用PlayerBackpack#refreshSnapshot刷新喵
    @Nonnull
    @Getter
    private InvSnapshot snapshot;

    /**
     * 异步获取背包实例的静态方法（带回调版本）。
     * 整体思路：先尝试用物品PDC中存储的新版UUID查询背包，若不存在则兼容旧版本的lore解析方式。
     * 输入：item物品、callback查询完成后的回调函数、runCbOnMainThread是否在主线程执行回调。
     * 输出：无返回值，结果通过callback回调传递。
     * 边界条件：item为null、无ItemMeta、无lore时直接返回不触发回调喵。
     */
    public static void getAsync(ItemStack item, Consumer<PlayerBackpack> callback, boolean runCbOnMainThread) {
        // 喵~防御：item为null或没有物品元数据或没有lore时直接返回，避免后续空指针异常喵
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return;
        }
        // 根据runCbOnMainThread决定回调在哪个线程执行：主线程延迟执行或数据库回调线程喵
        Executor executor = runCbOnMainThread
                ? ThreadUtils.getMainDelayedExecutor()
                : Slimefun.getDatabaseManager().getProfileDataController().getCallbackExecutor();
        // 尝试从物品的PDC中读取新版背包UUID喵
        var bUuid = getBackpackUUID(item.getItemMeta());
        // 新版背包：PDC中有UUID，直接用UUID异步查询数据库喵
        if (bUuid.isPresent()) {
            Slimefun.getDatabaseManager()
                    .getProfileDataController()
                    .getBackpackAsync(bUuid.get())
                    .thenAcceptAsync(
                            (result) -> {
                                // 喵~防御：查询结果不为null才触发回调，避免传入null的背包对象喵
                                if (result != null) {
                                    callback.accept(result);
                                }
                            },
                            executor);
            return;
        }

        // 旧版背包物品：PDC中没有UUID，需要从lore文本中解析玩家UUID和背包数字ID喵
        OptionalInt id = OptionalInt.empty(); // 解析出的背包数字ID，初始为空喵
        String uuid = ""; // 解析出的玩家UUID字符串，初始为空喵
        // 遍历物品lore的每一行，寻找含有"&7ID: "前缀和"#"分隔符的行喵
        for (String line : item.getItemMeta().getLore()) {
            // 匹配含有ID信息的lore行格式为"&7ID: 玩家UUID#背包编号"喵
            if (line.startsWith(ChatColors.color("&7ID: ")) && line.indexOf('#') != -1) {
                // 用#号分割lore行，得到玩家UUID部分和背包数字ID部分喵
                String[] splitLine = CommonPatterns.HASH.split(line);

                // 喵~防御：用正则确认第二段是纯数字，防止解析到非数字字符导致parseInt崩溃喵
                if (CommonPatterns.NUMERIC.matcher(splitLine[1]).matches()) {
                    // 去掉ID前缀，提取出玩家UUID字符串喵
                    uuid = splitLine[0].replace(ChatColors.color("&7ID: "), "");
                    // 将字符串转为整数并包装成OptionalInt，表示成功解析到背包ID喵
                    id = OptionalInt.of(Integer.parseInt(splitLine[1]));
                }
            }
        }

        // 如果成功从lore中解析到背包数字ID，则用旧版方式查询数据库喵
        if (id.isPresent()) {
            int number = id.getAsInt(); // 取出背包数字ID的整数值喵
            Slimefun.getDatabaseManager()
                    .getProfileDataController()
                    .getBackpackAsync(Bukkit.getOfflinePlayer(UUID.fromString(uuid)), number)
                    .thenAcceptAsync(
                            (result) -> {
                                // 喵~防御：查询结果不为null才处理，避免空指针喵
                                if (result != null) {
                                    // 旧版背包查询成功后，把新版UUID写入物品PDC，升级物品数据格式喵
                                    var meta = item.getItemMeta();
                                    meta.getPersistentDataContainer()
                                            .set(KEY_BACKPACK_UUID, PersistentDataType.STRING, result.uuid.toString());
                                    item.setItemMeta(meta); // 把更新后的meta写回物品喵
                                    // TODO: upgrade lore
                                    callback.accept(result); // 触发外部回调，传入查询到的背包实例喵
                                }
                            },
                            executor);
        }
    }

    /**
     * 异步获取背包实例的静态方法（返回CompletableFuture版本）。
     * 整体思路：与上方回调版本逻辑相同，但将结果包装为CompletableFuture返回，方便链式调用。
     * 输入：item物品ItemStack。
     * 输出：CompletableFuture包装的PlayerBackpack实例，物品无效时返回已完成的null Future。
     * 边界条件：item为null、无meta、无lore时立即返回completedFuture(null)喵。
     */
    public static CompletableFuture<PlayerBackpack> getAsync(ItemStack item) {
        // 喵~防御：item为null或没有物品元数据或没有lore，直接返回内容为null的已完成Future喵
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasLore()) {
            return CompletableFuture.completedFuture(null);
        }

        // 尝试从物品PDC读取新版背包UUID喵
        var bUuid = getBackpackUUID(item.getItemMeta());
        // 新版背包：有UUID时直接走数据库异步查询，返回Future喵
        if (bUuid.isPresent()) {
            return Slimefun.getDatabaseManager().getProfileDataController().getBackpackAsync(bUuid.get());
        }

        // 旧版背包物品兼容逻辑：需要从lore中解析ID喵
        OptionalInt id = OptionalInt.empty(); // 解析到的背包数字ID，初始为空喵
        String uuid = ""; // 解析到的玩家UUID字符串，初始为空喵

        // 遍历物品lore，寻找旧版格式的ID行喵
        for (String line : item.getItemMeta().getLore()) {
            // 匹配包含"&7ID: "前缀和"#"分隔符的行喵
            if (line.startsWith(ChatColors.color("&7ID: ")) && line.indexOf('#') != -1) {
                // 用#号拆分lore行，分别获取玩家UUID和背包数字ID喵
                String[] splitLine = CommonPatterns.HASH.split(line);

                // 喵~防御：校验拆分后的第二段是否为纯数字，防止NumberFormatException喵
                if (CommonPatterns.NUMERIC.matcher(splitLine[1]).matches()) {
                    // 去除ID前缀，保留玩家UUID字符串喵
                    uuid = splitLine[0].replace(ChatColors.color("&7ID: "), "");
                    // 解析背包数字ID喵
                    id = OptionalInt.of(Integer.parseInt(splitLine[1]));
                }
            }
        }

        // 解析到旧版背包ID时，通过玩家UUID+背包数字ID组合查询数据库喵
        if (id.isPresent()) {
            int number = id.getAsInt(); // 取出背包数字ID喵
            return Slimefun.getDatabaseManager()
                    .getProfileDataController()
                    .getBackpackAsync(Bukkit.getOfflinePlayer(UUID.fromString(uuid)), number);
        }
        // 既没有新版UUID也没有旧版ID时，返回内容为null的已完成Future喵
        return CompletableFuture.completedFuture(null);
    }

    /**
     * 从物品元数据的PDC中读取背包UUID字符串喵
     * 输入：ItemMeta物品元数据。
     * 输出：Optional包装的背包UUID字符串，若不存在则为空Optional喵。
     */
    public static Optional<String> getBackpackUUID(ItemMeta meta) {
        // 喵~防御：meta为null时直接返回空Optional，避免调用null对象的方法喵
        if (meta == null) {
            return Optional.empty();
        }
        // 从PDC中取出KEY_BACKPACK_UUID对应的字符串值，用Optional包装（值不存在时为empty）喵
        return Optional.ofNullable(meta.getPersistentDataContainer().get(KEY_BACKPACK_UUID, PersistentDataType.STRING));
    }

    /**
     * 从物品元数据的PDC中读取背包主人UUID字符串喵
     * 输入：ItemMeta物品元数据。
     * 输出：Optional包装的主人UUID字符串，若不存在则为空Optional喵。
     */
    public static Optional<String> getOwnerUUID(ItemMeta meta) {
        // 喵~防御：meta为null时直接返回空Optional，避免空指针崩溃喵
        if (meta == null) {
            return Optional.empty();
        }
        // 从PDC中取出KEY_OWNER_UUID对应的字符串值，用Optional包装喵
        return Optional.ofNullable(meta.getPersistentDataContainer().get(KEY_OWNER_UUID, PersistentDataType.STRING));
    }

    /**
     * 从物品元数据的lore中解析旧版背包的数字ID喵
     * 整体思路：遍历lore每行，找到符合旧版格式的ID行并解析出数字ID。
     * 输入：ItemMeta物品元数据。
     * 输出：OptionalInt包装的背包数字ID，不存在或解析失败则返回空OptionalInt喵。
     */
    public static OptionalInt getBackpackID(ItemMeta meta) {
        // 喵~防御：meta为null时直接返回空OptionalInt，防止空指针喵
        if (meta == null) {
            return OptionalInt.empty();
        }

        // 遍历物品lore的每一行，寻找旧版格式的背包ID信息喵
        for (String line : meta.getLore()) {
            // 匹配含有"&7ID: "前缀和"#"分隔符的行（旧版背包lore格式）喵
            if (line.startsWith(ChatColors.color("&7ID: ")) && line.contains("#")) {
                try {
                    // 去掉前缀后用#号拆分，取第二段解析为整数，即背包数字ID喵
                    return OptionalInt.of(Integer.parseInt(
                            CommonPatterns.HASH.split(line.replace(ChatColors.color("&7ID: "), ""))[1]));
                } catch (NumberFormatException e) {
                    // 喵~防御：解析失败时打印堆栈但不崩溃，继续查找下一行喵
                    e.printStackTrace();
                }
            }
        }

        // 遍历完所有行都没有找到有效的背包ID时返回空值喵
        return OptionalInt.empty();
    }

    /**
     * 将背包UUID和主人UUID写入物品的PDC持久数据容器喵
     * 输入：item目标物品、bpUuid背包UUID字符串、ownerUuid主人UUID字符串喵。
     */
    public static void setItemPdc(ItemStack item, String bpUuid, String ownerUuid) {
        ItemMeta meta = item.getItemMeta(); // 获取物品的元数据对象喵
        setPdc(meta, bpUuid, ownerUuid); // 向元数据的PDC中写入两个UUID喵
        item.setItemMeta(meta); // 将修改后的元数据写回物品喵
    }

    /**
     * 将背包的UUID和主人信息绑定到物品上（同时更新PDC和lore显示）喵
     * 输入：item要绑定的物品ItemStack、bp背包实例PlayerBackpack喵。
     */
    public static void bindItem(ItemStack item, PlayerBackpack bp) {
        var meta = item.getItemMeta(); // 获取物品元数据喵
        setPdc(meta, bp.uuid.toString(), bp.owner.getUniqueId().toString()); // 写入背包UUID和主人UUID到PDC喵
        setItem(meta, bp); // 更新lore中的所有者显示名和背包显示名喵
        item.setItemMeta(meta); // 将修改后的元数据写回物品喵
    }

    /**
     * 只更新物品的显示信息（lore和displayName），不修改PDC数据喵
     * 输入：item目标物品、bp背包实例喵。
     */
    public static void setItemDisplayInfo(ItemStack item, PlayerBackpack bp) {
        var meta = item.getItemMeta(); // 获取物品元数据喵
        setItem(meta, bp); // 仅更新lore中的所有者名称和背包显示名喵
        item.setItemMeta(meta); // 将修改后的元数据写回物品喵
    }
    /**
     * 判断背包主人当前是否在线（或配置允许离线主人的背包被打开）喵
     * 输入：ItemMeta物品元数据。
     * 输出：true表示可以打开（主人在线或允许离线打开），false表示不可打开喵。
     */
    public static boolean isOwnerOnline(ItemMeta meta) {
        // 如果配置文件中启用了"允许主人离线时打开背包"，则直接返回true喵
        if (Slimefun.getCfg().getBoolean("backpack.allow-open-when-owner-offline")) {
            return true;
        }
        // 从物品PDC中读取主人UUID喵
        var ownerUuid = PlayerBackpack.getOwnerUUID(meta);
        // ownerUuid为空（旧版背包无主人记录）视为允许打开；不为空则检查主人是否在线喵
        return ownerUuid.isEmpty() || Bukkit.getPlayer(UUID.fromString(ownerUuid.get())) != null;
    }

    /**
     * 私有工具方法：将背包UUID和主人UUID写入ItemMeta的PDC喵
     * 输入：meta物品元数据、bpUuid背包UUID字符串、ownerUuid主人UUID字符串喵。
     */
    private static void setPdc(ItemMeta meta, String bpUuid, String ownerUuid) {
        var pdc = meta.getPersistentDataContainer(); // 获取元数据的持久数据容器喵
        pdc.set(PlayerBackpack.KEY_BACKPACK_UUID, PersistentDataType.STRING, bpUuid); // 写入背包UUID喵
        pdc.set(PlayerBackpack.KEY_OWNER_UUID, PersistentDataType.STRING, ownerUuid); // 写入主人UUID喵
    }

    /**
     * 私有工具方法：更新ItemMeta中lore的所有者名称显示，以及物品displayName喵
     * 整体思路：遍历lore找到所有者行并替换为带玩家名的版本，然后设置displayName。
     * 输入：meta物品元数据、bp背包实例喵。
     */
    private static void setItem(ItemMeta meta, PlayerBackpack bp) {
        var lore = meta.getLore(); // 获取物品当前的lore列表喵
        // 遍历lore每一行，找到所有者标签行并替换为带玩家名的版本喵
        for (var i = 0; i < lore.size(); i++) {
            var line = lore.get(i);
            // 找到所有者那一行喵
            if (COLORED_LORE_OWNER.equals(line)) {
                // 在所有者标签后面追加玩家名称喵
                lore.set(i, COLORED_LORE_OWNER + bp.getOwner().getName());
                break; // 找到后立即退出循环，不需要继续遍历喵
            }
        }
        meta.setLore(lore); // 将更新后的lore写回元数据喵

        // 喵~防御：背包名称为空时不设置displayName，保持物品原有名称喵
        if (bp.name.isEmpty() || bp.name.isBlank()) {
            return;
        }
        meta.setDisplayName(ChatColors.color(bp.name)); // 将背包自定义名称（处理颜色代码后）设为物品显示名喵
    }
    /**
     * PlayerBackpack构造函数，创建一个已准备好可打开的背包实例喵
     * 整体思路：验证格子数量合法性，初始化所有字段，若传入内容则填充背包，并创建初始快照。
     * 输入：owner背包主人、uuid背包唯一ID、name背包显示名、id数字ID、size格子数、contents初始内容（可为null）。
     * 边界条件：size不合法或size与contents长度不匹配时抛出IllegalArgumentException喵。
     */
    @ParametersAreNonnullByDefault
    public PlayerBackpack(
            OfflinePlayer owner, UUID uuid, String name, int id, int size, @Nullable ItemStack[] contents) {
        // 喵~防御：格子数必须在9~54之间且是9的倍数，否则抛出异常，不允许创建非法大小的背包喵
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("Invalid size! Size must be one of: [9, 18, 27, 36, 45, 54]");
        }

        this.owner = owner; // 记录背包所有者玩家对象喵
        this.uuid = uuid; // 记录背包的唯一UUID标识符喵
        this.name = name; // 记录背包的显示名称喵
        this.id = id; // 记录背包的数字ID喵
        this.size = size; // 记录背包的格子数量喵
        inventory = newInv(); // 根据名称和格子数创建实际的Inventory对象喵

        // 如果传入了初始内容数组，则填充背包格子喵
        if (contents != null) {
            // 喵~防御：内容数组长度必须与背包格子数匹配，否则抛出异常防止数组越界喵
            if (size != contents.length) {
                throw new IllegalArgumentException("Invalid contents: size mismatched!");
            }
            inventory.setContents(contents); // 将传入的物品内容填充到背包格子中喵
        }

        this.snapshot = new InvSnapshot(inventory); // 创建初始背包快照，记录当前内容作为基准状态喵
    }

    /**
     * This refreshes the internal snapshot,
     * It should be called after every database writing task
     * It should not be called elsewhere
     * 刷新内部快照，每次数据库写入完成后必须调用此方法以保持快照与数据库内容同步喵
     */
    public void refreshSnapshot() {
        this.snapshot = new InvSnapshot(inventory); // 用当前背包内容重新创建快照，覆盖旧快照喵
    }

    /**
     * This returns the id of this {@link PlayerBackpack}
     *
     * @return The id of this {@link PlayerBackpack}
     * 返回此背包的数字ID，用于数据库查询和旧版本兼容喵
     */
    public int getId() {
        return id; // 返回背包数字ID喵
    }

    /**
     * This method returns the {@link PlayerProfile} this {@link PlayerBackpack} belongs to
     *
     * @return The owning {@link PlayerProfile}
     * 返回背包所属的玩家对象（可以是离线玩家OfflinePlayer）喵
     */
    @Nonnull
    public OfflinePlayer getOwner() {
        return owner; // 返回背包主人的玩家对象喵
    }

    /**
     * This returns the size of this {@link PlayerBackpack}.
     *
     * @return The size of this {@link PlayerBackpack}
     * 返回背包的格子数量（9~54之间的9的倍数）喵
     */
    public int getSize() {
        return size; // 返回背包格子数量喵
    }

    /**
     * This method returns the {@link Inventory} of this {@link PlayerBackpack}
     *
     * @return The {@link Inventory} of this {@link PlayerBackpack}
     * 返回背包对应的实际Inventory对象，可用于获取或修改背包内容喵
     */
    @Nonnull
    public Inventory getInventory() {
        return inventory; // 返回背包的Inventory实例喵
    }
    /**
     * This will open the {@link Inventory} of this backpack to every {@link Player}
     * that was passed onto this method.
     * <p>
     * 二进制兼容
     *
     * @param p The player who this Backpack will be shown to
     * 向指定玩家打开此背包的Inventory界面喵
     */
    public void open(Player p) {
        // 喵~防御：背包已失效时禁止打开，防止玩家操作损坏数据的背包喵
        if (isInvalid) {
            return;
        }

        InventoryUtil.openInventory(p, inventory); // 调用工具方法为玩家打开背包界面喵
    }

    /**
     * This will change the current size of this Backpack to the specified size.
     *
     * @param size
     *            The new size for this Backpack
     * 修改背包格子数量，并重建Inventory并持久化到数据库喵
     */
    public void setSize(int size) {
        // 喵~防御：格子数不合法时抛出异常，防止创建非法大小的背包Inventory喵
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("Invalid size! Size must be one of: [9, 18, 27, 36, 45, 54]");
        }

        this.size = size; // 更新内存中的格子数量字段喵
        updateInv(); // 关闭旧Inventory并根据新格子数重建Inventory喵
        Slimefun.getDatabaseManager().getProfileDataController().saveBackpackInfo(this); // 将背包信息更新保存到数据库喵
    }

    /**
     * 返回背包的唯一UUID标识符，用于新版本数据库查询喵
     */
    public UUID getUniqueId() {
        return uuid; // 返回背包UUID喵
    }

    /**
     * 设置背包的自定义显示名称，更新Inventory标题并持久化喵
     * 输入：name新的背包显示名称（支持颜色代码）喵。
     */
    public void setName(String name) {
        this.name = name; // 更新内存中的背包显示名称字段喵
        updateInv(); // 重建Inventory以刷新Inventory标题显示新名称喵
        Slimefun.getDatabaseManager().getProfileDataController().saveBackpackInfo(this); // 将背包名称更新保存到数据库喵
    }

    /**
     * 返回背包的自定义显示名称（未着色的原始字符串，含颜色代码）喵
     */
    public String getName() {
        return name; // 返回背包名称字符串喵
    }

    /**
     * 将背包标记为失效状态，关闭所有打开的界面并保存背包内容到数据库喵
     * 通常在背包物品被替换或数据异常时调用喵。
     */
    public void markInvalid() {
        isInvalid = true; // 标记背包为失效状态，防止后续被打开喵
        InventoryUtil.closeInventory(this.inventory); // 强制关闭所有玩家当前正在查看的此背包界面喵
        Slimefun.getDatabaseManager().getProfileDataController().saveBackpackInventory(this); // 将背包当前内容保存到数据库喵
    }

    /**
     * 返回背包当前是否处于失效状态喵
     */
    public boolean isInvalid() {
        return isInvalid; // 返回失效标志位喵
    }

    /**
     * Construct a new backpack inventory.
     * <p>
     * Warning: You should **manually** update inventory contents!
     *
     * @return new {@link Inventory}
     * 私有方法：根据当前名称和格子数创建一个新的Bukkit Inventory对象喵
     */
    private Inventory newInv() {
        // 创建以背包自身为持有者(InventoryHolder)的Inventory，标题含背包名和格子数喵
        // 背包名为空时显示"背包"，否则着色处理背包名并添加重置符号喵
        return Bukkit.createInventory(
                this, size, (name.isEmpty() ? "背包" : ChatColors.color(name + "&r")) + " [大小 " + size + "]");
    }

    /**
     * 私有方法：重建Inventory（关闭旧的、创建新的、迁移内容）喵
     * 整体思路：关闭旧Inventory让玩家退出界面，创建新Inventory，将旧内容复制过去，清空旧Inventory引用。
     * 通常在背包大小或名称发生变化时调用喵。
     */
    private void updateInv() {
        InventoryUtil.closeInventory(this.inventory); // 关闭旧Inventory，让正在查看的玩家退出界面喵
        var inv = newInv(); // 用新的大小/名称创建全新的Inventory对象喵
        inv.setContents(this.inventory.getContents()); // 将旧Inventory的物品内容复制到新Inventory中喵
        this.inventory.clear(); // 清空旧Inventory的内容，防止物品重复存在喵
        this.inventory = inv; // 将当前背包的inventory引用指向新创建的Inventory喵
        setInventory(inv); // 调用父类方法同步更新持有的Inventory引用喵
    }
}
