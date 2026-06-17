package io.github.thebusybiscuit.slimefun4.core.handlers;

import io.github.thebusybiscuit.slimefun4.api.exceptions.IncompatibleItemHandlerException;
import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.BlockPlacer;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.event.block.BlockDispenseEvent;

/**
 * This {@link ItemHandler} is triggered when the {@link SlimefunItem} it was assigned to
 * is a {@link Dispenser} and was triggered.
 *
 * This {@link ItemHandler} is used for the {@link BlockPlacer}.
 *
 * @author TheBusyBiscuit
 *
 * @see ItemHandler
 * @see BlockPlacer
 *
 */
// 函数式接口：专门处理发射器(Dispenser)分发Slimefun物品时的行为逻辑喵~
@FunctionalInterface
public interface BlockDispenseHandler extends ItemHandler {

    /*
     * 整体思路：校验某个SlimefunItem是否允许绑定BlockDispenseHandler喵~
     * 输入：待校验的SlimefunItem对象
     * 输出：不兼容时返回包含IncompatibleItemHandlerException的Optional，兼容时返回空Optional喵~
     * 边界条件：物品标记了NotPlaceable（不可放置），或物品材质不是发射器，均视为不兼容喵~
     */
    @Override
    default Optional<IncompatibleItemHandlerException> validate(SlimefunItem item) {
        // 喵~防御：物品实现了NotPlaceable接口或材质不是DISPENSER时，说明不兼容此处理器，返回异常避免错误绑定喵
        if (item instanceof NotPlaceable || item.getItem().getType() != Material.DISPENSER) {
            // 构建不兼容异常：只有未标记NotPlaceable的发射器类型物品才能绑定BlockDispenseHandler喵~
            return Optional.of(new IncompatibleItemHandlerException(
                    "Only dispensers that are not marked as 'NotPlaceable' can have a" + " BlockDispenseHandler.",
                    item,
                    this));
        }

        // 校验通过，返回空Optional表示此物品可以绑定BlockDispenseHandler喵~
        return Optional.empty();
    }

    // 发射器触发分发事件时调用：e为原始分发事件，dispenser为触发的发射器实体，facedBlock为发射器朝向的目标方块，machine为对应的Slimefun物品喵~
    void onBlockDispense(BlockDispenseEvent e, Dispenser dispenser, Block facedBlock, SlimefunItem machine);

    @Override
    // 返回BlockDispenseHandler.class作为此处理器的唯一标识类型，Slimefun内部用它区分不同处理器喵~
    default Class<? extends ItemHandler> getIdentifier() {
        return BlockDispenseHandler.class;
    }
}
