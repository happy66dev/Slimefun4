package io.github.thebusybiscuit.slimefun4.core.networks.cargo;

import com.xzavier0722.mc.plugin.slimefun4.storage.callback.IAsyncReadCallback;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunUniversalData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.debug.Debug;
import io.github.thebusybiscuit.slimefun4.core.debug.TestCase;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.cargo.CargoNode;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;

/**
 * {@link ItemFilter} 是货运网络 {@link CargoNet} 的性能优化组件喵~
 * 它保存了一个货运节点配置的快照，用于在传输时判断物品是否允许通过喵~
 *
 * @author TheBusyBiscuit
 * @see CargoNet
 * @see CargoNetworkTask
 *
 */
class ItemFilter implements Predicate<ItemStack> {

    /**
     * 用于存放过滤器槽位中物品的列表，最多容纳9个物品喵~
     * 如果为空则表示没有设置过滤条件喵~
     */
    // 初始化容量为9的列表，对应货运节点的9个过滤槽位喵~
    private final List<ItemStackWrapper> items = new ArrayList<>(9);

    /**
     * 过滤器的默认行为标志喵~
     * 为 true 时：找到匹配则拒绝（黑名单模式），找不到匹配则放行喵~
     * 为 false 时：找到匹配则放行（白名单模式），找不到匹配则拒绝喵~
     */
    // 是否在找到匹配时拒绝物品，true=黑名单，false=白名单喵~
    private boolean rejectOnMatch;

    /**
     * 是否在比较物品时同时检查物品的 lore（描述文本）喵~
     */
    // 是否启用 lore 比较，开启后过滤更严格，物品描述也要一致才算匹配喵~
    private boolean checkLore;

    /**
     * 如果过滤器被标记为 dirty（脏），说明配置已过时，需要在下一个 tick 重新加载喵~
     * 使用 volatile 保证多线程可见性喵~
     */
    // volatile 确保异步线程能立即看到 dirty 状态变化，初始为 true 表示刚创建时需要加载喵~
    private volatile boolean dirty = true;

    // volatile 标记是否正在异步加载数据，防止重复触发加载操作喵~
    private volatile boolean isLoading = false;

    /**
     * 根据给定的方块创建一个新的 {@link ItemFilter} 实例喵~
     * 会自动从该方块的数据中读取过滤器配置喵~
     *
     * @param b 货运节点所在的方块
     */
    public ItemFilter(@Nonnull Block b) {
        // 构造时立即触发一次更新，从方块数据中读取过滤配置喵~
        update(b);
    }

    /**
     * 更新或刷新 {@link ItemFilter}，从给定方块重新读取最新配置快照喵~
     *
     * 整体思路：
     *   1. 先判断是否需要更新（dirty=true 且没有正在加载）
     *   2. 从缓存获取方块数据容器
     *   3. 如果数据已加载则同步更新，否则异步加载后再更新
     *
     * 边界条件：
     *   - dirty=false 时直接跳过（避免重复加载）
     *   - isLoading=true 时直接跳过（避免并发重复加载）
     *
     * @param b 货运节点所在的方块
     */
    public void update(@Nonnull Block b) {
        // 喵~防御：如果过滤器不需要更新或者正在加载中，则跳过，避免重复加载浪费资源喵~
        if (!isDirty() || isLoading) {
            return;
        }

        // 从存储缓存中获取该位置的方块数据容器喵~
        var blockData = StorageCacheUtils.getDataContainer(b.getLocation());
        // 判断数据是否已在内存中加载完毕喵~
        if (blockData.isDataLoaded()) {
            // 数据已在内存中，直接同步更新过滤器喵~
            update(blockData);
        } else {
            // 数据未加载，先标记加载中状态防止并发，再触发异步加载喵~
            isLoading = true;
            // 向数据库控制器提交异步读取任务，加载完成后回调喵~
            Slimefun.getDatabaseManager().getBlockDataController().loadDataAsync(blockData, new IAsyncReadCallback<>() {
                @Override
                public void onResult(ASlimefunDataContainer result) {
                    // 异步加载完成后，使用已加载的 blockData 更新过滤器喵~
                    update(blockData);
                    // 清除加载中标记，允许下次再触发加载喵~
                    isLoading = false;
                }
            });
        }
    }

    /**
     * 内部方法：从数据容器中读取货运节点的过滤配置并更新过滤器状态喵~
     *
     * 整体思路：
     *   1. 再次检查 dirty 状态（防止异步回调时已被其他地方更新）
     *   2. 通过 sfId 查找对应的 SlimefunItem 并获取其菜单 UI
     *   3. 如果不是 CargoNode 或菜单不存在，清空过滤（拒绝所有）
     *   4. 如果 CargoNode 没有过滤器功能，清空过滤（放行所有）
     *   5. 否则读取过滤槽位的物品，以及 lore/白黑名单配置
     *
     * 输入：ASlimefunDataContainer — 包含方块 sfId 和持久化数据的容器
     * 输出：更新 items、checkLore、rejectOnMatch、dirty 字段
     * 边界：捕获所有异常防止货运网络崩溃喵~
     *
     * @param data 包含节点配置数据的容器
     */
    private void update(ASlimefunDataContainer data) {
        // 喵~防御：再次确认 dirty 状态，避免并发环境下重复执行更新逻辑喵~
        if (!isDirty()) {
            return;
        }

        // 根据数据容器中存储的 sfId 查找对应的 SlimefunItem 实例喵~
        SlimefunItem item = SlimefunItem.getById(data.getSfId());
        // 根据数据类型判断是普通方块数据还是通用数据，获取对应的菜单 UI 对象喵~
        var menu =
                data instanceof SlimefunBlockData sbd ? sbd.getBlockMenu() : ((SlimefunUniversalData) data).getMenu();

        // 喵~防御：如果找不到对应的 CargoNode 物品，或者菜单不存在，则清空过滤器并拒绝所有物品喵~
        if (!(item instanceof CargoNode) || menu == null) {
            // 节点不存在或菜单为空时，以安全模式清空过滤器（拒绝所有物品，避免非法传输）喵~
            clear(false);
        } else {
            try {
                // 将 SlimefunItem 向下转型为 CargoNode 以访问货运节点专属方法喵~
                CargoNode node = (CargoNode) item;

                // 检查该节点是否具有物品过滤功能喵~
                if (!node.hasItemFilter()) {
                    // 节点没有配置过滤器，清空并设置为放行所有物品喵~
                    clear(true);
                } else {
                    // 获取货运节点 GUI 中用于配置过滤的槽位编号数组喵~
                    int[] slots = CargoUtils.getFilteringSlots();
                    // 获取节点菜单的实际库存大小喵~
                    int inventorySize = menu.toInventory().getSize();

                    // 喵~防御：检查菜单库存大小是否足够容纳所有过滤槽位，防止索引越界喵~
                    if (inventorySize < slots[slots.length - 1]) {
                        /*
                         * 与 issue #2876 相关喵~
                         * 原因是上方过滤语句中缺少了一个取反操作喵~
                         * 如果再次发生此情况，可以通过警告日志定位问题喵~
                         */
                        // 输出警告日志：节点被标记为过滤节点但库存大小不足以容纳所有过滤槽喵~
                        item.warn("Cargo Node was marked as a 'filtering' node but has an insufficient inventory size"
                                + " ("
                                + inventorySize
                                + ")");
                        // 库存大小异常，直接退出不更新，保留旧状态喵~
                        return;
                    }

                    // 清空旧的过滤物品列表，准备重新加载喵~
                    this.items.clear();
                    // 从持久化数据中读取 "filter-lore" 字段，判断是否需要比较 lore 喵~
                    this.checkLore = Objects.equals(data.getData("filter-lore"), "true");
                    // 从持久化数据中读取 "filter-type" 字段，不是白名单则设为拒绝匹配（黑名单）喵~
                    this.rejectOnMatch = !Objects.equals(data.getData("filter-type"), "whitelist");

                    // 主人注意：此循环遍历所有过滤槽位（最多9个），性能影响较小，但每次 tick 更新时会执行喵~
                    // 遍历所有过滤槽位，读取其中的物品加入过滤列表喵~
                    for (int slot : slots) {
                        // 获取该槽位当前放置的物品喵~
                        ItemStack stack = menu.getItemInSlot(slot);

                        // 喵~防御：跳过空槽位和 AIR 方块，只处理真实的过滤物品喵~
                        if (stack != null && stack.getType() != Material.AIR) {
                            // 将物品包装为 ItemStackWrapper 存入过滤列表，提升后续比较性能喵~
                            this.items.add(ItemStackWrapper.wrap(stack));
                        }
                    }
                }
            } catch (Exception | LinkageError x) {
                // 捕获所有异常和链接错误，记录错误报告，防止货运网络因单个节点异常而崩溃喵~
                item.error("Something went wrong while updating the ItemFilter for this cargo node.", x);
            }
        }

        // 更新完成后清除 dirty 标记，表示过滤器配置已是最新状态喵~
        this.dirty = false;
    }

    /**
     * 清空过滤器中的所有物品，并设置默认行为模式喵~
     * 调用此方法后过滤器处于"空列表"状态，行为由 rejectOnMatch 决定喵~
     *
     * @param rejectOnMatch true=放行所有物品（黑名单空列表），false=拒绝所有物品（白名单空列表）
     */
    private void clear(boolean rejectOnMatch) {
        // 清空所有已保存的过滤物品喵~
        this.items.clear();
        // 重置 lore 检查标志为 false，不比较描述文本喵~
        this.checkLore = false;
        // 设置匹配时的默认行为（黑名单/白名单模式）喵~
        this.rejectOnMatch = rejectOnMatch;
    }

    /**
     * 判断当前过滤器配置是否已过时，需要重新加载喵~
     *
     * @return true 表示需要更新，false 表示配置仍然有效
     */
    public boolean isDirty() {
        // 返回 dirty 标志，表示过滤器是否需要刷新配置喵~
        return this.dirty;
    }

    /**
     * 将此过滤器标记为过时状态，下一次使用前会触发重新加载喵~
     */
    public void markDirty() {
        // 设置 dirty=true，下次 update 时会重新从方块数据中读取配置喵~
        this.dirty = true;
    }

    /**
     * 测试给定的物品是否能通过此过滤器喵~
     *
     * 整体思路（两阶段匹配优化）：
     *   第一阶段：只比较 Material 类型（廉价操作），统计潜在匹配数量
     *   第二阶段：对有潜在匹配的物品进行完整比较（含 ItemMeta/lore 等昂贵操作）
     *   这样可以避免对明显不匹配的物品执行 getItemMeta() 的高开销操作喵~
     *
     * 输入：要检测的 ItemStack
     * 输出：true=物品允许通过，false=物品被拒绝
     * 边界：
     *   - 过滤器 dirty 时直接拒绝（配置未就绪）
     *   - 过滤列表为空时直接返回默认值
     *
     * @param item 待检测的物品
     * @return 物品是否允许通过过滤器
     */
    @Override
    public boolean test(@Nonnull ItemStack item) {
        // 喵~防御：如果过滤器配置尚未加载完毕（dirty=true），直接拒绝所有物品保证安全喵~
        if (isDirty()) {
            return false;
        }

        // 输出调试日志，记录当前正在测试的物品信息喵~
        Debug.log(TestCase.CARGO_INPUT_TESTING, "ItemFilter#test({})", item);
        /*
         * 过滤列表为空时无需遍历，直接返回默认行为值喵~
         * 白名单模式下空列表=拒绝所有；黑名单模式下空列表=放行所有喵~
         */
        // 喵~防御：过滤列表为空，按 rejectOnMatch 决定放行或拒绝，避免空遍历喵~
        if (items.isEmpty()) {
            // 返回默认行为：黑名单模式(true)放行，白名单模式(false)拒绝喵~
            return rejectOnMatch;
        }

        // 用于统计与待测物品 Material 类型相同的过滤项数量喵~
        int potentialMatches = 0;

        /*
         * 第一阶段：仅比较 Material 类型（廉价操作）喵~
         * 如果没有任何 Material 匹配，则完全不需要执行耗时的 getItemMeta() 操作喵~
         * 主人注意：此处对 items 列表进行线性遍历，items 最多9个元素，性能影响极小喵~
         */
        // 遍历过滤列表，统计与待测物品 Material 相同的条目数量喵~
        for (ItemStackWrapper stack : items) {
            // 只比较物品的 Material 类型，这是最廉价的比较操作喵~
            if (stack.getType() == item.getType()) {
                // 发现一个 Material 类型匹配的潜在候选喵~
                potentialMatches++;
            }
        }

        // 喵~防御：没有任何 Material 匹配时，跳过昂贵的 ItemMeta 比较直接返回默认值喵~
        if (potentialMatches == 0) {
            // 没有潜在匹配，直接按默认行为返回喵~
            return rejectOnMatch;
        } else {
            /*
             * 第二阶段：对有潜在匹配的物品进行完整比较（含 ItemMeta）喵~
             * 如果潜在匹配超过1个，将 item 包装为 ItemStackWrapper 以缓存 ItemMeta 避免重复调用喵~
             * 如果只有1个潜在匹配，直接用原始 item 比较，节省包装开销喵~
             */
            // 主人注意：当 potentialMatches > 1 时会调用 ItemStackWrapper.wrap() 触发 getItemMeta()，
            // 但由于过滤列表最多9个，此处最坏情况也只会执行一次 wrap，影响较小喵~
            // 根据潜在匹配数量决定是否包装，避免多次调用 getItemMeta() 的重复开销喵~
            ItemStack subject = potentialMatches == 1 ? item : ItemStackWrapper.wrap(item);

            /*
             * 如果只有1个潜在匹配，使用原始 item 直接比较，getItemMeta() 只会执行一次喵~
             */
            // 遍历过滤列表进行完整的物品相似度比较（可能包括 lore 比较）喵~
            for (ItemStackWrapper stack : items) {
                // 使用 SlimefunUtils.isItemSimilar 进行完整比较，checkLore 控制是否包含 lore 喵~
                if (SlimefunUtils.isItemSimilar(subject, stack, checkLore, false)) {
                    /*
                     * 找到完整匹配喵~
                     * 返回 rejectOnMatch 的反值：
                     *   黑名单模式(rejectOnMatch=true)：匹配时返回 false（拒绝）
                     *   白名单模式(rejectOnMatch=false)：匹配时返回 true（放行）
                     */
                    // 找到匹配项，返回与默认行为相反的结果（匹配=通过白名单/触发黑名单）喵~
                    return !rejectOnMatch;
                }
            }

            // 遍历完所有过滤项都没有完整匹配，回退到默认行为值喵~
            return rejectOnMatch;
        }
    }
}
