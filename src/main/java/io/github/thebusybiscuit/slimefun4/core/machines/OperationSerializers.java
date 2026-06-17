package io.github.thebusybiscuit.slimefun4.core.machines;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.implementation.operations.FuelOperation;
import io.github.thebusybiscuit.slimefun4.implementation.operations.GEOMiningOperation;
import io.github.thebusybiscuit.slimefun4.implementation.operations.MiningOperation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * 机器操作序列化/反序列化注册表喵~
 * <p>
 * 内置的操作类型（{@link CraftingOperation}合成、{@link FuelOperation}燃料、{@link MiningOperation}挖矿、
 * {@link GEOMiningOperation}GEO挖矿）会在类加载时自动注册喵~
 * <p>
 * 附加包开发者可通过 {@link #register(String, Function)} 注册自定义操作类型的反序列化器喵~
 *
 * @see MachineOperation#getOperationTypeId()
 * @see MachineOperation#serialize()
 */
public final class OperationSerializers {

    /*
     * 整体思路喵~：
     * 这个类维护一个从"操作类型ID字符串"到"反序列化函数"的映射表。
     * 当机器操作需要从磁盘/存储中恢复时，先取出 typeId，再从这张表里
     * 找到对应的反序列化函数，把序列化字符串还原成 MachineOperation 对象喵~
     * 输入：typeId（操作类型唯一标识）+ data（序列化字符串）
     * 输出：还原好的 MachineOperation 实例，或者 null（类型未注册/反序列化失败）
     * 边界条件：typeId 不存在时返回 null 并打印警告；反序列化抛异常时捕获并返回 null 喵~
     */

    // 使用线程安全的 ConcurrentHashMap 存储所有已注册的反序列化函数，key 为操作类型ID字符串喵~
    private static final Map<String, Function<String, MachineOperation>> DESERIALIZERS = new ConcurrentHashMap<>();

    static {
        // 静态初始化块：类加载时自动注册四种内置机器操作类型的反序列化器喵~
        register(CraftingOperation.TYPE_ID, CraftingOperation::deserialize); // 注册合成操作的反序列化器喵~
        register(FuelOperation.TYPE_ID, FuelOperation::deserialize); // 注册燃料操作的反序列化器喵~
        register(MiningOperation.TYPE_ID, MiningOperation::deserialize); // 注册挖矿操作的反序列化器喵~
        register(GEOMiningOperation.TYPE_ID, GEOMiningOperation::deserialize); // 注册GEO挖矿操作的反序列化器喵~
    }

    // 私有构造方法，禁止外部实例化，这是一个纯静态工具注册表类喵~
    private OperationSerializers() {}

    /**
     * 向注册表中添加一个自定义 {@link MachineOperation} 类型的反序列化器喵~
     * <p>
     * 反序列化函数接收序列化字符串（来自 {@link MachineOperation#serialize()}），
     * 应当返回一个完整初始化好的 {@link MachineOperation} 实例，失败时返回 null喵~
     *
     * @param typeId
     *            操作类型的唯一标识符（必须与 {@link MachineOperation#getOperationTypeId()} 返回值一致）
     * @param deserializer
     *            从序列化字符串还原操作对象的函数
     */
    public static void register(@Nonnull String typeId, @Nonnull Function<String, MachineOperation> deserializer) {
        // 将 typeId 与对应的反序列化函数绑定并存入注册表中，允许后续通过 typeId 查找还原操作对象喵~
        DESERIALIZERS.put(typeId, deserializer);
    }

    /**
     * 根据操作类型ID和序列化数据，将 {@link MachineOperation} 从持久化存储中还原喵~
     *
     * <p>整体思路喵~：
     * 1. 先从注册表里根据 typeId 查找对应的反序列化函数
     * 2. 找不到则打印警告并返回 null
     * 3. 找到则执行反序列化函数，把 data 字符串还原成 MachineOperation 对象
     * 4. 若反序列化过程抛出任何异常，捕获后打印警告并返回 null
     *
     * @param typeId
     *            操作类型标识符，由 {@link MachineOperation#getOperationTypeId()} 返回
     * @param data
     *            序列化字符串，由 {@link MachineOperation#serialize()} 返回
     *
     * @return 成功则返回还原的 {@link MachineOperation} 实例，类型未知或反序列化失败则返回 null
     */
    @Nullable public static MachineOperation deserialize(@Nonnull String typeId, @Nonnull String data) {
        // 根据 typeId 从注册表中查找对应的反序列化函数喵~
        Function<String, MachineOperation> deserializer = DESERIALIZERS.get(typeId);
        // 喵~防御：找不到对应的反序列化器时，打印警告并返回 null，避免后续调用空函数崩溃喵~
        if (deserializer == null) {
            // 向服务器日志输出警告：该操作类型没有注册反序列化器，typeId 会被带入日志消息参数 {0} 中喵~
            Slimefun.logger().log(Level.WARNING, "No deserializer registered for operation type: {0}", typeId);
            return null;
        }

        try {
            // 调用已注册的反序列化函数，把序列化字符串 data 还原成 MachineOperation 对象喵~
            return deserializer.apply(data);
        } catch (Exception e) {
            // 喵~防御：捕获反序列化过程中的任意异常，打印带有 typeId 信息的警告日志并返回 null，防止异常向上传播导致机器崩溃喵~
            Slimefun.logger().log(Level.WARNING, "Failed to deserialize operation of type: " + typeId, e);
            return null;
        }
    }

    /**
     * 检查指定类型ID的反序列化器是否已注册喵~
     *
     * @param typeId
     *            要检查的操作类型标识符
     *
     * @return 如果该类型已注册反序列化器则返回 true，否则返回 false
     */
    public static boolean isRegistered(@Nonnull String typeId) {
        // 检查注册表中是否含有该 typeId 的映射记录，用于在尝试反序列化前预先校验类型是否受支持喵~
        return DESERIALIZERS.containsKey(typeId);
    }
}
