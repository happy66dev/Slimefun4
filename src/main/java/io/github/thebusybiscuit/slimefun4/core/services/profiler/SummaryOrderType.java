package io.github.thebusybiscuit.slimefun4.core.services.profiler;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.ParametersAreNonnullByDefault;

/**
 * 性能分析摘要的排序方式枚举，决定性能报告中物品计时数据的展示顺序喵~
 *
 * @author Walshy
 */
public enum SummaryOrderType {

    /**
     * 按总耗时从高到低排序，最耗性能的物品排在最前面，方便快速找到性能瓶颈喵~
     */
    HIGHEST,
    /**
     * 按总耗时从低到高排序，耗时最少的物品排在最前面喵~
     */
    LOWEST,
    /**
     * 按每个物品的平均耗时从高到低排序，更公平地比较不同数量物品的性能喵~
     */
    AVERAGE;

    /*
     * 整体思路：根据当前枚举值(HIGHEST/LOWEST/AVERAGE)对物品计时数据进行不同方式的排序喵~
     * 输入：profiler - 性能分析器，用于获取各物品ID对应的方块数量；
     *       entrySet - 物品ID到总耗时(纳秒)的映射条目集合喵~
     * 输出：排序后的 Map.Entry 列表，每条记录包含物品ID和对应的耗时数值喵~
     * 边界条件：AVERAGE模式下若某物品方块数为0则直接使用总耗时作为平均值，避免除零异常喵~
     */
    @ParametersAreNonnullByDefault
    List<Map.Entry<String, Long>> sort(SlimefunProfiler profiler, Set<Map.Entry<String, Long>> entrySet) {
        // 根据当前枚举值选择对应的排序策略喵~
        switch (this) {
            case HIGHEST:
                // 将条目集合转为流，按耗时值从大到小(reverseOrder)排序，最耗性能的排最前喵~
                return entrySet.stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .collect(Collectors.toList()); // 收集排序结果为 List 并返回喵~
            case LOWEST:
                // 将条目集合转为流，按耗时值从小到大排序，耗时最少的排最前喵~
                return entrySet.stream()
                        .sorted(Comparator.comparingLong(Map.Entry::getValue))
                        .collect(Collectors.toList()); // 收集排序结果为 List 并返回喵~
            default:
                // AVERAGE 模式：先计算每个物品ID的平均耗时，再按平均值从高到低排序喵~
                final Map<String, Long> map = new HashMap<>(); // 用于存储物品ID到平均耗时的映射喵~
                for (Map.Entry<String, Long> entry : entrySet) {
                    // 从 profiler 获取该物品ID对应的已加载方块数量，用来计算平均耗时喵~
                    int count = profiler.getBlocksOfId(entry.getKey());
                    // 喵~防御：count为0时避免除零错误，直接使用总耗时作为平均值喵~
                    long avg = count > 0 ? entry.getValue() / count : entry.getValue();

                    // 将计算好的平均耗时存入临时 map，以物品ID为键喵~
                    map.put(entry.getKey(), avg);
                }
                // 将平均耗时 map 转为流，按平均值从大到小排序后收集为 List 返回喵~
                return map.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .collect(Collectors.toList());
        }
    }
}
