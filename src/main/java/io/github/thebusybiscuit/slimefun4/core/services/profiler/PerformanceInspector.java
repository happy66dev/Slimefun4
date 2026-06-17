package io.github.thebusybiscuit.slimefun4.core.services.profiler;

import javax.annotation.Nonnull;
import org.bukkit.Server;

/**
 * This interface is used to identify someone as a {@link PerformanceInspector}.
 * A {@link PerformanceInspector} can query the {@link SlimefunProfiler} and get the
 * results send to them as a {@link PerformanceSummary}.
 *
 * 性能检测者接口，用于标识"谁可以查询SlimefunProfiler并接收性能报告"的身份喵~
 * 实现此接口的对象（如在线玩家、控制台）可以主动请求性能摘要数据并接受反馈喵~
 *
 * @author TheBusyBiscuit
 *
 */
public interface PerformanceInspector {

    /**
     * This returns whether this {@link PerformanceInspector} is still valid.
     * An inspector will become invalid if they leave the {@link Server}.
     *
     * 判断此检测者是否仍然有效喵~
     * 例如玩家离开服务器后，对应的检测者对象就会失效，避免向离线玩家发送消息喵~
     *
     * @return Whether this inspector is still valid
     */
    // 返回此性能检测者是否仍然有效（比如玩家是否还在线），失效则不应继续发送消息喵~
    boolean isValid();

    /**
     * This will send a text message to the {@link PerformanceInspector}.
     *
     * 向此性能检测者发送一条文本消息喵~
     * 消息内容通常是性能摘要PerformanceSummary中的各行文字喵~
     *
     * @param msg
     *            The message to send
     */
    // 将性能报告中的文字内容发送给检测者（可能是玩家聊天栏或控制台输出），msg不能为null喵~
    void sendMessage(@Nonnull String msg);

    /**
     * This determines whether the {@link PerformanceInspector} will get the full view
     * or a trimmed version which only shows the most urgent samples.
     *
     * 判断此检测者是否要求"详细模式"喵~
     * 返回true时会输出完整的性能摘要；返回false时只显示最耗时的关键条目，信息更精简喵~
     *
     * @return Whether to send the full {@link PerformanceSummary} or a trimmed version
     */
    // 控制输出详细度：true=完整摘要（所有性能条目），false=精简版（只显示最紧急/最耗时的条目）喵~
    boolean isVerbose();

    /**
     * The order type for the summary of timings.
     *
     * 获取性能摘要的排序方式喵~
     * 决定输出结果按什么顺序排列，例如按耗时从高到低、或按物品名称字母顺序等喵~
     *
     * @return The order type for the summary of timings.
     */
    @Nonnull
    // 返回性能摘要的排序类型SummaryOrderType，控制性能报告中各条目的排列顺序，不能为null喵~
    SummaryOrderType getOrderType();
}
