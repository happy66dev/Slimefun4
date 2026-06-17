// 声明此文件所属的包路径，对应Slimefun4插件核心处理器模块喵~
package io.github.thebusybiscuit.slimefun4.core.handlers;

// 引入物品处理器接口，是所有Slimefun物品行为处理器的基础接口喵~
import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
// 引入Slimefun自定义物品类，代表所有自定义物品的核心基类喵~
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
// 引入Java列表集合，用于存储方块破坏后的掉落物列表喵~
import java.util.List;
// 引入Bukkit方块类，代表游戏世界中的一个方块喵~
import org.bukkit.block.Block;
// 引入附魔枚举类，用于表示时运(Fortune)等附魔类型喵~
import org.bukkit.enchantments.Enchantment;
// 引入方块破坏事件类，玩家破坏方块时会触发此事件喵~
import org.bukkit.event.block.BlockBreakEvent;
// 引入物品堆叠类，代表游戏中的一组物品喵~
import org.bukkit.inventory.ItemStack;

/**
 * This {@link ItemHandler} is called when a {@link Block} is broken with a {@link SlimefunItem}
 * as its tool.
 * 当玩家使用Slimefun工具破坏方块时，此处理器接口会被触发喵~
 *
 * @author TheBusyBiscuit
 *
 * @see BlockBreakHandler
 *
 */
// @FunctionalInterface 注解表示这是一个函数式接口，只允许有一个抽象方法，可用Lambda表达式实现喵~
@FunctionalInterface
// 定义工具使用处理器接口，继承ItemHandler，专门处理Slimefun工具破坏方块的逻辑喵~
public interface ToolUseHandler extends ItemHandler {

    /**
     * This method is called whenever a {@link BlockBreakEvent} was fired when using this
     * {@link SlimefunItem} to break a {@link Block}.
     * 当玩家使用此Slimefun物品(工具)破坏方块并触发BlockBreakEvent时，此方法会被调用喵~
     *
     * @param e
     *            The {@link BlockBreakEvent} - 方块破坏事件对象，包含破坏者、被破坏方块等信息喵~
     * @param tool
     *            The tool that was used - 玩家手持的工具ItemStack对象喵~
     * @param fortune
     *            The amount of bonus drops to be expected from the fortune {@link Enchantment}.
     *            时运附魔的额外掉落倍数，0表示没有时运加成喵~
     * @param drops
     *            The dropped items - 方块破坏后实际掉落的物品列表，可在此方法中修改喵~
     *
     */
    /*
     * 整体思路：
     * 此方法是接口的唯一抽象方法(函数式接口规定)，由具体实现类提供业务逻辑喵~
     * 输入：方块破坏事件、使用的工具、时运附魔等级(0~3)、当前掉落物列表
     * 输出：无返回值，通过修改 drops 列表或调用 e 的方法来影响最终结果
     * 边界条件：fortune 为0时表示无时运加成；drops 可能为空列表(方块无掉落物)喵~
     */
    // 声明工具使用回调方法，实现类需重写此方法以定义Slimefun工具破坏方块时的特殊逻辑喵~
    void onToolUse(BlockBreakEvent e, ItemStack tool, int fortune, List<ItemStack> drops);

    // 重写父接口ItemHandler的getIdentifier方法，返回当前处理器的类型标识符喵~
    @Override
    // 默认实现方法：返回ToolUseHandler.class作为此处理器的唯一标识，用于Slimefun内部区分不同类型的处理器喵~
    default Class<? extends ItemHandler> getIdentifier() {
        // 返回当前接口的Class对象，作为处理器类型的唯一标识键喵~
        return ToolUseHandler.class;
    }
}
