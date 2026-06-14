package io.github.thebusybiscuit.slimefun4.api.exceptions;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.attributes.DamageableItem;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.inventory.ItemStack;

/**
 * 当代码试图修改某个 ItemStack，但实际上应该修改的是另一个 ItemStack 时，抛出此异常喵~
 *
 * 例如：当一个 {@link DamageableItem}（可损坏物品）意外地对原始的 {@link SlimefunItem}
 * 造成了耐久损耗，而不是对玩家手持的 {@link ItemStack} 造成损耗时，就会抛出此异常喵~
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunItemStack
 * @see SlimefunItem
 *
 */
// 这是一个运行时异常类，用于标识"操作了错误的 ItemStack"这一编程错误喵~
public class WrongItemStackException extends RuntimeException {

    // 序列化版本号，用于保证异常对象在网络传输或持久化时的兼容性喵~
    private static final long serialVersionUID = 9144658137363309071L;

    /**
     * 构造一个新的 {@link WrongItemStackException}，并附带描述错误上下文的消息喵~
     *
     * 整体思路：调用父类 RuntimeException 的构造方法，将调用者传入的错误描述
     * 拼接到固定提示语之后，组成完整的异常消息，方便开发者定位问题喵~
     *
     * 输入：message - 描述具体出错原因的字符串，不能为 null（由 @ParametersAreNonnullByDefault 保证）喵~
     * 输出：一个携带完整错误信息的异常对象喵~
     *
     * @param message 用于展示的错误描述信息
     */
    @ParametersAreNonnullByDefault
    public WrongItemStackException(String message) {
        // 调用父类构造方法，将固定提示语与具体错误描述拼接后作为最终异常消息喵~
        super("You probably wanted to alter a different ItemStack: " + message);
    }
}
