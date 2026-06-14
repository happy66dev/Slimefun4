// 包声明：此类属于 Slimefun4 物品设置相关 API 包喵~
package io.github.thebusybiscuit.slimefun4.api.items.settings;

// 导入物品设置基类，IntRangeSetting 继承自它喵~
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
// 导入 SlimefunItem，构造函数需要绑定到具体物品喵~
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
// 导入非空注解，用于标记返回值不为 null 喵~
import javax.annotation.Nonnull;
// 导入参数非空注解，声明该方法所有参数默认不为 null 喵~
import javax.annotation.ParametersAreNonnullByDefault;
// 导入 Apache 工具类的参数校验工具喵~
import org.apache.commons.lang.Validate;

/**
 * 这是 {@link ItemSetting} 的整数范围限定版本，
 * 允许开发者定义一个整数的合法范围（最小值到最大值），
 * 并通过 {@link #validateInput(Integer)} 方法强制执行该范围约束喵~
 *
 * 典型用途：比如限制某物品的某个配置项只能在 1~100 之间喵~
 *
 * @author TheBusyBiscuit
 *
 * @see ItemSetting
 * @see DoubleRangeSetting
 *
 */
public class IntRangeSetting extends ItemSetting<Integer> {

    // 允许的最小整数值，构造后不可修改喵~
    private final int min;
    // 允许的最大整数值，构造后不可修改喵~
    private final int max;

    /**
     * 构造一个带整数范围约束的物品设置项喵~
     *
     * 整体思路：
     *   1. 调用父类构造，绑定物品、配置键名和默认值喵~
     *   2. 校验默认值必须在 [min, max] 范围内，否则直接抛出异常喵~
     *   3. 保存最小值和最大值，供后续校验使用喵~
     *
     * 输入：
     *   item         - 该设置绑定的 SlimefunItem 物品实例喵~
     *   key          - 配置文件中的键名字符串喵~
     *   min          - 允许的最小整数值（含边界）喵~
     *   defaultValue - 默认整数值，必须满足 min <= defaultValue <= max 喵~
     *   max          - 允许的最大整数值（含边界）喵~
     *
     * 边界条件：
     *   若 defaultValue 超出 [min, max] 范围，会立即抛出 IllegalArgumentException 喵~
     */
    @ParametersAreNonnullByDefault
    public IntRangeSetting(SlimefunItem item, String key, int min, int defaultValue, int max) {
        // 调用父类构造方法，将物品、配置键名和默认值传递给基类进行初始化喵~
        super(item, key, defaultValue);
        // 喵~防御：校验默认值必须在 [min, max] 范围内，防止错误配置在运行时才暴露问题喵~
        Validate.isTrue(defaultValue >= min && defaultValue <= max, "The default value is not in range.");

        // 将合法的最小值保存到字段，后续校验时会用到喵~
        this.min = min;
        // 将合法的最大值保存到字段，后续校验时会用到喵~
        this.max = max;
    }

    /**
     * 返回当输入值不合法时展示给用户的错误提示信息喵~
     * 提示内容会包含当前允许的整数范围（含两端边界）喵~
     */
    @Nonnull
    @Override
    protected String getErrorMessage() {
        // 拼接错误提示字符串，告知用户只能输入 min 到 max 之间的整数（含边界）喵~
        return "Only whole numbers from " + min + '-' + max + "(inclusive) are allowed!";
    }

    /**
     * 校验用户输入的整数是否合法喵~
     *
     * 整体思路：
     *   先调用父类的基础校验（如非 null 检查），
     *   再额外判断输入值是否在 [min, max] 范围内喵~
     *
     * 输入：input - 用户提供的整数值喵~
     * 输出：true 表示合法，false 表示非法喵~
     */
    @Override
    public boolean validateInput(Integer input) {
        // 先通过父类校验（通常检查 null 等基础条件），再确认值在允许的整数范围内喵~
        return super.validateInput(input) && input >= min && input <= max;
    }

    /**
     * This returns the minimum value of this {@link IntRangeSetting}.
     *
     * @return The minimum value
     */
    public final int getMinimum() {
        // 返回构造时设定的最小允许值喵~
        return min;
    }

    /**
     * This returns the maximum value of this {@link IntRangeSetting}.
     *
     * @return The maximum value
     */
    public final int getMaximum() {
        // 返回构造时设定的最大允许值喵~
        return max;
    }
}
