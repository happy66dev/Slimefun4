package io.github.thebusybiscuit.slimefun4.core.machines;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 机器反馈注册表 —— 用于集中管理所有 MachineFeedback 实例喵~
 *
 * 整体思路：
 *   - 维护一个静态 HashMap，以小写字符串作为 key，MachineFeedback 实例作为 value 喵~
 *   - 在类加载时（static 块）自动把所有内置枚举类型 MachineFeedbackType 注册进来喵~
 *   - 外部插件/附加包可以通过 register() 方法扩展注册自定义反馈类型喵~
 *   - get() 方法根据 key 查询对应的反馈实例，找不到时返回 null 喵~
 *   - getAll() 返回注册表的防御性拷贝，避免外部直接修改内部状态喵~
 *
 * 输入：字符串 key（不区分大小写）和 MachineFeedback 实例喵~
 * 输出：通过 key 检索到的 MachineFeedback 实例，或所有已注册项的快照喵~
 * 边界条件：key 会自动转小写统一比较，避免大小写不一致导致查找失败喵~
 */
public final class MachineFeedbackRegistry {

    // 存储所有已注册的机器反馈类型，key 为小写字符串标识，value 为对应的反馈实现喵~
    private static final Map<String, MachineFeedback> registry = new HashMap<>();

    // 静态初始化块：类加载时自动把 MachineFeedbackType 枚举中的所有内置类型注册进来喵~
    static {
        // 遍历 MachineFeedbackType 枚举的所有成员，逐个注册到 registry 喵~
        for (MachineFeedbackType type : MachineFeedbackType.values()) {
            // 将枚举名称转为小写作为 key，保证 key 统一为小写格式喵~
            register(type.name().toLowerCase(), type);
        }
    }

    // 私有构造方法，禁止外部实例化 —— 这是一个纯静态工具类喵~
    private MachineFeedbackRegistry() {}

    /**
     * 向注册表中注册一个新的机器反馈类型喵~
     *
     * @param key      反馈类型的字符串标识（不区分大小写，内部自动转小写）喵~
     * @param feedback 对应的 MachineFeedback 实例喵~
     */
    public static void register(@Nonnull String key, @Nonnull MachineFeedback feedback) {
        // 将 key 统一转为小写后存入注册表，确保查找时大小写不敏感喵~
        registry.put(key.toLowerCase(), feedback);
    }

    /**
     * 根据字符串 key 查找对应的机器反馈实例喵~
     *
     * @param key 反馈类型的字符串标识（不区分大小写）喵~
     * @return 找到则返回对应的 MachineFeedback 实例，找不到则返回 null 喵~
     */
    @Nullable public static MachineFeedback get(@Nonnull String key) {
        // 将 key 转为小写后查询，保证与注册时的 key 格式一致喵~
        return registry.get(key.toLowerCase());
    }

    /**
     * 获取当前注册表中所有已注册反馈类型的快照喵~
     *
     * 返回注册表的防御性拷贝（新 HashMap），调用方修改返回值不会影响内部状态喵~
     *
     * @return 包含所有注册项的新 HashMap 喵~
     */
    @Nonnull
    public static Map<String, MachineFeedback> getAll() {
        // 喵~防御：返回副本而非原始 registry，防止调用方意外清空或修改注册表导致全局状态损坏喵~
        return new HashMap<>(registry);
    }
}
