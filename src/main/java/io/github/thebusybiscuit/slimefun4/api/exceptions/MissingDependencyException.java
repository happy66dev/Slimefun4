// 声明当前类所在的包，属于 Slimefun4 API 的异常模块喵~
package io.github.thebusybiscuit.slimefun4.api.exceptions;

// 导入 SlimefunAddon 接口，用于获取插件名称等信息喵~
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
// 导入注解：标记该方法的所有参数都不能为 null 喵~
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 当某个 {@link SlimefunAddon}（Slimefun 扩展插件）尝试注册物品，
 * 但没有在 plugin.yml 中将 Slimefun 声明为 depend 或 softdepend 时，
 * 就会抛出这个 {@link MissingDependencyException} 异常喵~
 *
 * @author TheBusyBiscuit
 * @see SlimefunAddon
 */
// 继承 RuntimeException，表示这是一个运行时异常，无需强制 try-catch 捕获喵~
public class MissingDependencyException extends RuntimeException {

    // 序列化版本号，用于保证类在序列化和反序列化时版本一致喵~
    private static final long serialVersionUID = -2255888430181930571L;

    /**
     * 构造一个新的 {@link MissingDependencyException} 异常对象喵~
     * 整体思路：将传入的插件名和缺失的依赖名拼接成一条清晰的错误提示信息，
     * 告知开发者哪个插件忘记在 plugin.yml 中声明依赖喵~
     * 输入：addon - 触发异常的 SlimefunAddon 插件实例；dependency - 缺失的依赖名称（通常是 "Slimefun"）
     * 输出：携带完整错误描述的异常对象喵~
     * 边界条件：@ParametersAreNonnullByDefault 已声明两个参数均不允许为 null 喵~
     *
     * @param addon      触发此异常的 {@link SlimefunAddon} 插件实例喵~
     * @param dependency 缺失的依赖名称，通常为 "Slimefun" 喵~
     */
    // 标记该构造方法的所有参数不能传入 null，防止空指针异常喵~
    @ParametersAreNonnullByDefault
    public MissingDependencyException(SlimefunAddon addon, String dependency) {
        // 调用父类 RuntimeException 的构造方法，传入拼接好的错误信息字符串喵~
        super("Slimefun Addon \""
                // 获取触发异常的插件名称，拼入错误信息中方便定位问题喵~
                + addon.getName()
                + "\" forgot to define \""
                // 拼入缺失的依赖名称，提示开发者需要在 plugin.yml 中声明哪个依赖喵~
                + dependency
                // 说明应该在 plugin.yml 的 depend 或 softdepend 字段中添加该依赖喵~
                + "\" as a depend or softdepend inside the plugin.yml file");
    }
}
