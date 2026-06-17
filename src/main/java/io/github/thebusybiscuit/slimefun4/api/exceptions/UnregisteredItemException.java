package io.github.thebusybiscuit.slimefun4.api.exceptions;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.plugin.Plugin;

/**
 * 当某个 {@link Plugin} 过早调用了 {@link SlimefunItem} 中需要注册后才能使用的方法时，
 * 就会抛出此 {@link UnregisteredItemException} 异常喵~
 *
 * 换句话说，在物品注册完成之前调用这些方法是没有意义的，因此定义为异常喵~
 *
 * @author TheBusyBiscuit
 *
 */
public class UnregisteredItemException extends RuntimeException {

    // 序列化版本号，用于保证反序列化时的类版本一致性喵~
    private static final long serialVersionUID = -4684752240435069678L;

    /**
     * 构造一个针对指定 {@link SlimefunItem} 的 {@link UnregisteredItemException} 异常实例喵~
     *
     * 整体思路：将未注册的物品信息拼接到错误消息中，方便开发者快速定位问题物品喵~
     * 输入：尚未注册的 SlimefunItem 对象喵~
     * 输出：携带错误描述信息的运行时异常喵~
     * 边界条件：item 不允许为 null（由 @ParametersAreNonnullByDefault 注解保证）喵~
     *
     * @param item
     *            触发此异常的 {@link SlimefunItem} 对象喵~
     */
    @ParametersAreNonnullByDefault
    public UnregisteredItemException(SlimefunItem item) {
        // 调用父类构造器，将物品的字符串描述拼接到错误信息中，告知调用者哪个物品还未注册喵~
        super(item.toString() + " has not been registered yet.");
    }
}
