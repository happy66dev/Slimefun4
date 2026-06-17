package io.github.thebusybiscuit.slimefun4.api.items;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.lang.Validate;

/**
 * This class represents a Setting for a {@link SlimefunItem} that can be modified via
 * the {@code Items.yml} {@link Config} file.
 *
 * @author TheBusyBiscuit
 *
 * @param <T>
 *            The type of data stored under this {@link ItemSetting}
 */
// 泛型配置项类，代表某个 SlimefunItem 在 Items.yml 中的一个可配置选项，T 是配置值的数据类型喵~
public class ItemSetting<T> {

    // 该配置项所属的 SlimefunItem 物品实例喵~
    private final SlimefunItem item;

    // 配置项的键名，用于在 Items.yml 中唯一定位该配置喵~
    private final String key;
    // 配置项的默认值，当配置文件未设置或值无效时作为兜底喵~
    private final T defaultValue;

    // 配置项的当前运行时值，由 reload() 从配置文件加载后赋值，初始为 null 喵~
    private T value;

    /**
     * This creates a new {@link ItemSetting} with the given key and default value
     *
     * @param item
     *            The {@link SlimefunItem} this {@link ItemSetting} belongs to
     * @param key
     *            The key under which this setting will be stored (relative to the {@link SlimefunItem})
     * @param defaultValue
     *            The default value for this {@link ItemSetting}
     */
    /*
     * 构造方法整体说明：
     * 输入：所属物品实例 item、配置键名 key、默认值 defaultValue（三者均不允许为 null）
     * 输出：初始化完成的 ItemSetting 对象，value 字段此时为 null，等待 reload() 赋值
     * 边界条件：任一参数为 null 时 Validate 会立即抛出 IllegalArgumentException 中断构造喵~
     */
    @ParametersAreNonnullByDefault
    public ItemSetting(SlimefunItem item, String key, T defaultValue) {
        // 喵~防御：item 为 null 时立即抛出异常，避免后续操作出现空指针崩溃喵
        Validate.notNull(item, "The provided SlimefunItem must not be null!");
        // 喵~防御：key 为 null 时抛出异常，配置项必须有合法键名才能在 yml 中定位喵
        Validate.notNull(key, "The key of an ItemSetting is not allowed to be null!");
        // 喵~防御：defaultValue 为 null 时抛出异常，默认值不能为 null 否则类型信息会丢失喵
        Validate.notNull(defaultValue, "The default value of an ItemSetting is not allowed to be null!");

        // 将传入的物品实例保存到成员变量喵~
        this.item = item;
        // 将传入的键名保存到成员变量喵~
        this.key = key;
        // 将传入的默认值保存到成员变量喵~
        this.defaultValue = defaultValue;
    }

    /**
     * This method checks if a given input would be valid as a value for this
     * {@link ItemSetting}. You can override this method to implement your own checks.
     *
     * @param input
     *            The input value to validate
     *
     * @return Whether the given input was valid
     */
    // 校验传入值是否合法，子类可覆盖此方法添加额外的业务校验规则喵~
    public boolean validateInput(T input) {
        // 默认实现：只要值不为 null 就认为合法，返回 true 表示通过校验喵~
        return input != null;
    }

    /**
     * This method updates this {@link ItemSetting} with the given value.
     * Override this method to catch changes of a value.
     * A value may never be null.
     *
     * @param newValue
     *            The new value for this {@link ItemSetting}
     */
    /*
     * update 方法整体说明：
     * 输入：新的配置值 newValue，不允许为 null
     * 输出：校验通过则更新 this.value；校验失败则抛出 IllegalArgumentException
     * 边界条件：newValue 为 null 时 validateInput 返回 false，进入异常分支喵~
     */
    public void update(@Nonnull T newValue) {
        // 先通过 validateInput 校验新值是否合法喵~
        if (validateInput(newValue)) {
            // 校验通过，将新值赋给当前运行时值字段喵~
            this.value = newValue;
        } else {
            // 校验失败（例如传入了 null），抛出带提示信息的异常喵~
            throw new IllegalArgumentException("The passed value was not valid. (Maybe null?)");
        }

        // Feel free to override this as necessary.
    }

    /**
     * This returns the key of this {@link ItemSetting}.
     *
     * @return The key under which this setting is stored (relative to the {@link SlimefunItem})
     */
    // 返回该配置项的键名，供外部读取在 Items.yml 中的定位路径喵~
    public @Nonnull String getKey() {
        return key;
    }

    /**
     * This returns the associated {@link SlimefunItem} for this {@link ItemSetting}.
     *
     * @return The associated {@link SlimefunItem}
     */
    // 返回该配置项所属的 SlimefunItem 物品实例，仅子类可访问（protected 可见性）喵~
    protected @Nonnull SlimefunItem getItem() {
        return item;
    }

    /**
     * This returns the <strong>current</strong> value of this {@link ItemSetting}.
     *
     * @return The current value
     */
    /*
     * getValue 方法整体说明：
     * 输入：无
     * 输出：已加载的配置值；若未初始化且处于单元测试则抛出异常；若未初始化且在正常环境则打印警告并返回默认值
     * 边界条件：reload() 未被调用时 value 为 null，此时行为取决于运行环境喵~
     */
    public @Nonnull T getValue() {
        // 喵~防御：value 不为 null 说明已通过 reload() 正确初始化，直接返回喵
        if (value != null) {
            /**
             * If the value has been initialized, return it immediately.
             */
            return value;
        } else if (Slimefun.instance().isUnitTest()) {
            // 单元测试环境中，未初始化的配置项访问应让测试失败，方便开发者发现问题喵~
            /*
             * In Unit Tests, we want the test to fail. So we know there is
             * something that needs to be fixed.
             */
            throw new IllegalStateException("ItemSetting '" + key + "' was invoked but was not initialized yet.");
        } else {
            // 正常运行环境中，未初始化只打印警告并降级使用默认值，避免服务器崩溃喵~
            /*
             * In a normal environment, we can mitigate the issue
             * easily and just print a warning instead.
             */
            // 向控制台输出警告，提示该配置项尚未初始化喵~
            item.warn("ItemSetting '" + key + "' was invoked but was not initialized yet.");
            // 降级返回默认值，保证程序继续正常运行喵~
            return defaultValue;
        }
    }

    /**
     * This returns the <strong>default</strong> value of this {@link ItemSetting}.
     *
     * @return The default value
     */
    // 返回该配置项的默认值，在配置未加载或值无效时作为兜底使用喵~
    public @Nonnull T getDefaultValue() {
        return defaultValue;
    }

    /**
     * This method checks if this {@link ItemSetting} stores the given data type.
     *
     * @param c
     *            The class of data type you want to compare
     *
     * @return Whether this {@link ItemSetting} stores the given type
     */
    // 判断该配置项的默认值是否为指定类型的实例，用于类型安全检查喵~
    public boolean isType(@Nonnull Class<?> c) {
        // 利用 Class.isInstance 检查 defaultValue 是否属于传入的类型 c 喵~
        return c.isInstance(defaultValue);
    }

    /**
     * This is an error message which should provide further context on what values
     * are allowed.
     *
     * @return An error message which is displayed when this {@link ItemSetting} is misconfigured.
     */
    // 返回配置项填写错误时的提示信息，子类可覆盖以提供更具体的说明喵~
    protected @Nonnull String getErrorMessage() {
        // 拼接提示语，告知用户应填写的数据类型范围喵~
        return "请使用在 '" + defaultValue.getClass().getSimpleName() + "' 范围内的值!";
    }

    /**
     * This method is called by a {@link SlimefunItem} which wants to load its {@link ItemSetting}
     * from the {@link Config} file.
     *
     */
    /*
     * reload 方法整体说明：
     * 作用：从 Items.yml 配置文件中读取该配置项的值，校验类型和合法性后赋给 this.value
     * 输入：无（依赖成员变量 item、key、defaultValue）
     * 输出：成功时 this.value 被更新为配置文件中的值；失败时保持默认值并打印警告
     * 边界条件：
     *   1. configuredValue 为 null（配置文件没有该键）→ 类型判断为 false，进入 else 使用默认值
     *   2. configuredValue 类型与 defaultValue 不匹配 → 进入 else 打印类型不匹配警告
     *   3. configuredValue 类型匹配但 validateInput 校验失败 → 打印值无效警告，不更新 value
     *   4. configuredValue 为 List 且 defaultValue 也为 List → 允许绕过泛型检查直接赋值喵~
     */
    @SuppressWarnings("unchecked")
    public void reload() {
        // 喵~防御：item 为 null 时直接抛出异常，无法为不存在的物品加载配置喵
        Validate.notNull(item, "Cannot apply settings for a non-existing SlimefunItem");

        // 将默认值写入配置文件（若该键不存在则自动创建），键名格式为"物品ID.配置键"喵~
        Slimefun.getItemCfg().setDefaultValue(item.getId() + '.' + getKey(), getDefaultValue());
        // 从配置文件中读取该键对应的当前值喵~
        Object configuredValue = Slimefun.getItemCfg().getValue(item.getId() + '.' + getKey());

        // 检查读取到的值是否与默认值类型匹配（包含 List 类型的特殊兼容处理）喵~
        if (defaultValue.getClass().isInstance(configuredValue)
                || (configuredValue instanceof List && defaultValue instanceof List)) {
            // We can do an unsafe cast here, we did an isInstance(...) check before!
            // 类型检查通过后进行强制转换，将 Object 类型的配置值转为泛型 T 喵~
            T newValue = (T) configuredValue;

            // 再通过 validateInput 校验值的业务合法性喵~
            if (validateInput(newValue)) {
                // 校验通过，更新当前运行时值喵~
                this.value = newValue;
            } else {
                // 值类型正确但业务校验失败，打印详细警告信息告知服务器管理员喵~
                // @formatter:off
                item.warn("发现在 Items.yml 中有无效的物品设置!"
                        + "\n  在 \""
                        + item.getId()
                        + "."
                        + getKey()
                        + "\""
                        + "\n  "
                        + configuredValue
                        + " 不是一个有效值!"
                        + "\n"
                        + getErrorMessage());
                // @formatter:on
            }
        } else {
            // 类型不匹配，回退到默认值以保证程序正常运行喵~
            this.value = defaultValue;
            // 获取实际读取到的类型名称，null 时显示字符串 "null" 喵~
            String found = configuredValue == null
                    ? "null"
                    : configuredValue.getClass().getSimpleName();

            // 打印类型不匹配的详细警告，告知期望类型与实际填写类型的差异喵~
            // @formatter:off
            item.warn("发现在 Items.yml 中有无效的物品设置!"
                    + "\n请只设置有效的值."
                    + "\n  在 \""
                    + item.getId()
                    + "."
                    + getKey()
                    + "\""
                    + "\n  期望值为 \""
                    + defaultValue.getClass().getSimpleName()
                    + "\" 但填写了: \""
                    + found
                    + "\"");
            // @formatter:on
        }
    }

    // 返回该配置项的字符串描述，格式为"类名 {键名 = 当前值 (default: 默认值)}"，方便调试喵~
    @Override
    public String toString() {
        // 若 value 已初始化则用当前值，否则降级使用默认值显示喵~
        T currentValue = this.value != null ? this.value : defaultValue;
        // 拼接类名、键名、当前值、默认值组成可读的调试字符串喵~
        return getClass().getSimpleName()
                + " {"
                + getKey()
                + " = "
                + currentValue
                + " (default: "
                + getDefaultValue()
                + ")";
    }

    // 根据所属物品和键名计算哈希值，用于在 HashMap/HashSet 中正确存放和查找喵~
    @Override
    public final int hashCode() {
        // 将 item 和 key 组合计算哈希，保证同一物品同一键的配置项哈希相同喵~
        return Objects.hash(item, key);
    }

    /*
     * equals 方法整体说明：
     * 判断两个 ItemSetting 是否相等：当且仅当键名相同且所属物品相同时视为同一配置项
     * 输入：任意 Object
     * 输出：true 表示相等，false 表示不等
     * 边界条件：obj 为 null 或非 ItemSetting 类型时直接返回 false 喵~
     */
    @Override
    public final boolean equals(Object obj) {
        // 先判断 obj 是否为 ItemSetting 类型，不是则直接返回 false 喵~
        if (obj instanceof ItemSetting) {
            // 强制转换为 ItemSetting<?> 以访问其键名和所属物品喵~
            ItemSetting<?> setting = (ItemSetting<?>) obj;
            // 同时比较键名和所属物品，两者都相等才认为是同一个配置项喵~
            return Objects.equals(getKey(), setting.getKey()) && Objects.equals(getItem(), setting.getItem());
        } else {
            // obj 不是 ItemSetting 类型，必然不相等，返回 false 喵~
            return false;
        }
    }
}
