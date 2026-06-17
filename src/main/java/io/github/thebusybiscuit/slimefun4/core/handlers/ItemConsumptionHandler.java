package io.github.thebusybiscuit.slimefun4.core.handlers;

import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.food.DietCookie;
import io.github.thebusybiscuit.slimefun4.implementation.items.food.FortuneCookie;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

/**
 * 物品消耗处理器接口，当玩家吃掉/喝掉绑定了此处理器的 {@link SlimefunItem} 时触发喵~
 *
 * 注意：此处理器只对食物或药水类物品有效，其他类型物品无法触发喵~
 *
 * 使用方式：将此接口实现后通过 {@link SlimefunItem#addItemHandler} 注册到对应物品喵~
 *
 * @author TheBusyBiscuit
 *
 * @see ItemHandler
 * @see SimpleSlimefunItem
 *
 * @see FortuneCookie
 * @see DietCookie
 *
 */
@FunctionalInterface
// 标记为函数式接口，表示此接口只有一个抽象方法，可用 Lambda 表达式实现喵~
public interface ItemConsumptionHandler extends ItemHandler {

    /**
     * 当玩家消耗（吃/喝）某个绑定了此处理器的 {@link SlimefunItem} 时，此方法会被触发喵~
     *
     * 整体思路：
     * - 输入：消耗事件对象 e、消耗物品的玩家 p、被消耗的物品 item
     * - 输出：无返回值，但可在方法体内修改事件（如取消消耗）或对玩家执行额外逻辑
     * - 边界条件：此方法仅在物品真正被消耗时触发，如事件被其他插件取消则不触发喵~
     *
     * @param e
     *            触发的 {@link PlayerItemConsumeEvent} 事件对象，可通过它取消消耗行为喵~
     * @param p
     *            消耗物品的 {@link Player} 玩家对象喵~
     * @param item
     *            被消耗的 {@link ItemStack} 物品堆叠对象喵~
     */
    void onConsume(PlayerItemConsumeEvent e, Player p, ItemStack item);

    // 重写 ItemHandler 接口中的 getIdentifier 方法，用于标识此处理器的唯一类型喵~
    @Override
    default Class<? extends ItemHandler> getIdentifier() {
        // 返回当前处理器接口的 Class 对象，作为在物品处理器系统中的唯一标识符喵~
        return ItemConsumptionHandler.class;
    }
}
