package io.github.thebusybiscuit.slimefun4.core.attributes;

import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import javax.annotation.Nonnull;

/**
 * 这是一个空接口，作用是将所有"物品属性"类型的子接口归拢到同一个类型体系下，方便统一识别和管理喵~
 * 所有实现了 {@link ItemAttribute} 的接口，都必须依附在一个 {@link SlimefunItem} 物品注册对象上才能生效喵~
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunItem
 * @see ItemHandler
 *
 */
public interface ItemAttribute {

    /**
     * 返回与此属性关联的 {@link SlimefunItem} 的唯一字符串ID喵~
     * 通过这个ID可以定位到对应的Slimefun物品注册实例喵~
     *
     * @return 关联 {@link SlimefunItem} 的唯一标识符字符串喵~
     */
    @Nonnull
    String getId();
}
