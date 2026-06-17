package io.github.thebusybiscuit.slimefun4.core.networks.cargo;

import io.github.thebusybiscuit.slimefun4.utils.itemstack.ItemStackWrapper;
import javax.annotation.Nonnull;
import org.apache.commons.lang.Validate;
import org.bukkit.inventory.ItemStack;

/**
 * 货运网络中用于将 ItemStack 与一个整数(数量/优先级等)绑定在一起的轻量容器喵~
 * 整体思路：货运系统在分拣物品时既需要知道物品本身，又需要记录对应的数量或槽位索引，
 * 本类将两者打包成一个对象方便传递，避免到处用两个变量喵~
 * 边界条件：构造时 item 不允许为 null，否则直接抛出异常喵~
 */
class ItemStackAndInteger {

    // 货运操作的目标物品，存储实际的 ItemStack 对象喵~
    private ItemStack item;
    // item 的轻量只读包装，用于高效比较物品而不触发深拷贝喵~
    private ItemStackWrapper wrapper;
    // 与物品关联的整数，通常代表数量或槽位索引喵~
    private int number;

    /**
     * 构造一个物品+整数的绑定对象喵~
     * 输入：item 不可为 null 的目标物品；amount 关联的整数值喵~
     * 输出：初始化完毕的 ItemStackAndInteger 实例喵~
     * 边界：item 为 null 时 Validate 会抛出 IllegalArgumentException，阻止后续空指针喵~
     */
    ItemStackAndInteger(@Nonnull ItemStack item, int amount) {
        // 喵~防御：item 为 null 时立即抛出异常，防止后续所有操作发生空指针崩溃喵~
        Validate.notNull(item, "Item cannot be null!");
        // 将传入的关联整数(数量/槽位等)保存到字段喵~
        this.number = amount;
        // 将传入的目标 ItemStack 保存到字段喵~
        this.item = item;
    }

    // 获取当前关联的整数值(数量或槽位索引等)，供货运逻辑读取喵~
    public int getInt() {
        return number;
    }

    /**
     * 获取实际可操作的 ItemStack 对象喵~
     * 如果内部存的是 ItemStackWrapper(只读包装)，会先转换成真正的 ItemStack 副本再返回喵~
     */
    public @Nonnull ItemStack getItem() {
        // 确保返回的是真实 ItemStack，而非只读的 Wrapper 包装喵~
        initializeItem();
        return item;
    }

    /**
     * 获取物品的 ItemStackWrapper 轻量只读包装喵~
     * 整体思路：ItemStackWrapper 比直接比较 ItemStack 性能更好，货运系统用它做物品匹配喵~
     * 采用懒加载策略：首次调用时才创建 wrapper，避免不必要的对象分配喵~
     */
    public @Nonnull ItemStackWrapper getItemStackWrapper() {
        // 喵~防御：wrapper 尚未初始化时才创建，避免重复创建浪费内存喵~
        if (wrapper == null) {
            // 用 ItemStackWrapper.wrap 把当前 item 包装成轻量只读对象喵~
            wrapper = ItemStackWrapper.wrap(item);
        }

        // 返回已缓存的 wrapper 对象喵~
        return wrapper;
    }

    /**
     * 将关联整数增加指定数量喵~
     * 货运系统在合并相同物品的数量时调用此方法喵~
     */
    public void add(int amount) {
        // 把传入的增量叠加到当前整数字段上，实现数量累加喵~
        number += amount;
    }

    /**
     * 将内部存储的 ItemStackWrapper 转换为真正可操作的 ItemStack 副本喵~
     * 整体思路：ItemStackWrapper 是只读包装，不能直接用于写操作；
     * 此方法检测 item 是否是 Wrapper，如果是则复制出一个新的真实 ItemStack
     * 同时保留原有的物品类型、数量和 ItemMeta(显示名/附魔/Lore等)喵~
     * 边界条件：item 已经是普通 ItemStack 时直接跳过，无需转换喵~
     */
    private void initializeItem() {
        // 喵~防御：只有 item 是 ItemStackWrapper 实例时才需要转换，避免无谓的对象创建喵~
        if (this.item instanceof ItemStackWrapper) {
            // 以原 item 的材质和数量创建一个全新的普通 ItemStack 副本喵~
            ItemStack copy = new ItemStack(item.getType(), item.getAmount());
            // 喵~防御：只有原 item 带有 ItemMeta(显示名/附魔等)时才复制，避免空 Meta 异常喵~
            if (this.item.hasItemMeta()) {
                // 将原 item 的 ItemMeta 完整复制到新副本，保留所有自定义属性喵~
                copy.setItemMeta(this.item.getItemMeta());
            }
            // 用新创建的普通 ItemStack 替换掉内部的 Wrapper 引用喵~
            this.item = copy;
        }
    }
}
