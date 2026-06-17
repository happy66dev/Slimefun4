package io.github.thebusybiscuit.slimefun4.api.exceptions;

import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.plugin.Plugin;

/**
 * 当某个 {@link Plugin} 尝试向 {@link SlimefunItem} 添加该物品不支持的 {@link ItemHandler} 类型时，
 * 会抛出此异常喵~
 *
 * 例如 {@link BlockUseHandler} 只能添加给方块类物品，若添加给非方块物品则触发此异常喵~
 *
 * @author TheBusyBiscuit
 *
 * @see ItemHandler
 * @see SlimefunItem
 *
 */
public class IncompatibleItemHandlerException extends RuntimeException {

    // 序列化版本号，保证此异常类在序列化与反序列化之间版本兼容喵~
    private static final long serialVersionUID = -6723066421114874138L;

    /**
     * 构造一个新的 {@link IncompatibleItemHandlerException}，描述哪个物品与哪个处理器不兼容喵~
     *
     * 整体思路：把不兼容的 ItemHandler 类型名、受影响的 SlimefunItem 以及不兼容原因拼成一条
     * 人类可读的错误信息，传给父类 RuntimeException，方便开发者快速定位问题喵~
     * 输入：不兼容原因、受影响物品、被尝试添加的处理器
     * 输出：携带完整错误描述信息的运行时异常喵~
     *
     * @param message
     *            不兼容的具体原因喵~
     * @param item
     *            受影响的 {@link SlimefunItem} 喵~
     * @param handler
     *            被尝试添加的 {@link ItemHandler} 喵~
     */
    @ParametersAreNonnullByDefault
    public IncompatibleItemHandlerException(String message, SlimefunItem item, ItemHandler handler) {
        super("The item handler type: \""
                // 获取 ItemHandler 接口类型的简短类名，让开发者一眼看出是哪种处理器喵~
                + handler.getIdentifier().getSimpleName()
                + "\" is not compatible with "
                // 拼入受影响的 SlimefunItem 字符串表示，方便确认是哪个物品触发了异常喵~
                + item
                + " ("
                // 拼入调用方传入的不兼容原因，给出更详细的排错提示喵~
                + message
                + ')');
    }
}
