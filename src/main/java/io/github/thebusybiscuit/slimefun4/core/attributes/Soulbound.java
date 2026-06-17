package io.github.thebusybiscuit.slimefun4.core.attributes;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.magical.SoulboundItem;

/**
 * 灵魂绑定接口，实现此接口的 {@link SlimefunItem} 子类物品将被标记为灵魂绑定物品喵~
 * 灵魂绑定的物品在玩家死亡时不会掉落，而是保留在玩家身上喵~
 *
 * @author TheBusyBiscuit
 * @see SoulboundItem
 */
// 继承 ItemAttribute 接口，表示这是一种物品属性标记喵~
public interface Soulbound extends ItemAttribute {}
