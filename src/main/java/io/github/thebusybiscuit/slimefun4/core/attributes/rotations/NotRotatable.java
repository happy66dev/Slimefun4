package io.github.thebusybiscuit.slimefun4.core.attributes.rotations;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.ItemAttribute;
import org.bukkit.block.BlockFace;

/**
 * 实现此接口的 {@link SlimefunItem} 将禁止被旋转喵~
 * 即玩家放置该物品时，方向固定不会随玩家朝向改变喵~
 *
 * @author Ddggdd135
 *
 */
public interface NotRotatable extends ItemAttribute {
    // 返回该物品放置时固定使用的朝向，默认朝北，子类可重写此方法改变固定方向喵~
    default BlockFace getRotation() {
        // 固定返回 NORTH（北方），确保此物品放置后始终面朝北方，不响应玩家的朝向喵~
        return BlockFace.NORTH;
    }
}
