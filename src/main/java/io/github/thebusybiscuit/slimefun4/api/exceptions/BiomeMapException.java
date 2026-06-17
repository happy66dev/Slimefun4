package io.github.thebusybiscuit.slimefun4.api.exceptions;

import io.github.thebusybiscuit.slimefun4.utils.biomes.BiomeMap;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.NamespacedKey;

/**
 * 当 {@link BiomeMap} 包含非法、无效或未知的值时抛出此异常喵~
 * 用于在解析生物群系配置文件出错时向外部报告具体的错误原因喵~
 *
 * @author TheBusyBiscuit
 *
 */
public class BiomeMapException extends Exception {

    // 序列化版本号，用于保证反序列化时类的版本一致性，防止版本不匹配导致反序列化失败喵~
    private static final long serialVersionUID = -1894334121194788527L;

    /**
     * 通过文字消息构造一个 {@link BiomeMapException} 异常喵~
     * 整体思路：接收出错的 BiomeMap 的 NamespacedKey 和描述错误原因的字符串，
     * 拼接成可读的错误信息传给父类 Exception，方便调用者定位是哪个 BiomeMap 配置出了问题喵~
     * 输入：key - 出问题的 BiomeMap 的命名空间键；message - 具体错误描述喵~
     * 输出：携带完整错误信息的异常对象喵~
     *
     * @param key
     *            出问题的 {@link BiomeMap} 的 {@link NamespacedKey}（命名空间键）喵~
     * @param message
     *            具体的错误描述文字喵~
     */
    @ParametersAreNonnullByDefault
    public BiomeMapException(NamespacedKey key, String message) {
        // 将 BiomeMap 的 key 和错误描述拼接成完整的异常信息，传给父类 Exception 喵~
        super("Biome Map '" + key + "' has been misconfigured: " + message);
    }

    /**
     * 通过另一个异常（cause）构造一个 {@link BiomeMapException} 异常喵~
     * 整体思路：接收出错的 BiomeMap 的 NamespacedKey 和引发本次错误的原始异常，
     * 将原始异常的消息嵌入到错误描述中，并把原始异常作为 cause 链传给父类，
     * 方便通过异常链追踪最根本的报错原因喵~
     * 输入：key - 出问题的 BiomeMap 的命名空间键；cause - 引发此异常的原始 Throwable 喵~
     * 输出：携带完整错误信息及异常链的异常对象喵~
     *
     * @param key
     *            出问题的 {@link BiomeMap} 的 {@link NamespacedKey}（命名空间键）喵~
     * @param cause
     *            引发本次异常的根本原因 {@link Throwable} 对象喵~
     */
    @ParametersAreNonnullByDefault
    public BiomeMapException(NamespacedKey key, Throwable cause) {
        // 将 key、原始异常的消息拼接进错误文字，并把 cause 传给父类以保留完整异常链喵~
        super("Biome Map '" + key + "' has been misconfigured (" + cause.getMessage() + ')', cause);
    }
}
