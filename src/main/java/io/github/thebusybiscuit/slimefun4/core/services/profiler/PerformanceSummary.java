package io.github.thebusybiscuit.slimefun4.core.services.profiler;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.core.services.profiler.inspectors.PlayerPerformanceInspector;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Content;
import net.md_5.bungee.api.chat.hover.content.Text;

/**
 * 性能摘要类，负责将 SlimefunProfiler 采集的一次 tick 性能数据汇总并发送给查询者喵~
 *
 * 整体思路：
 *   - 构造时从 profiler 中拉取本次 tick 的耗时、方块统计、区块统计、插件统计等数据喵~
 *   - send() 方法将这些数据格式化后通过 PerformanceInspector 接口发送给查询方喵~
 *   - 对玩家查询方使用悬浮文本组件(TextComponent)，对控制台查询方使用纯字符串喵~
 *   - 每类统计（方块/区块/插件）都通过 summarizeTimings() 统一处理喵~
 *
 * 输入：SlimefunProfiler 实例、总耗时(纳秒)、总 tick 方块数喵~
 * 输出：格式化的性能报告文本或 TextComponent，发送给 PerformanceInspector 喵~
 */
class PerformanceSummary {

    // 方块或区块耗时超过此阈值(纳秒)才在 /sf timings 报告中显示，过滤掉微小耗时避免刷屏喵~
    // The threshold at which a Block or Chunk is significant enough to appear in /sf timings
    private static final int VISIBILITY_THRESHOLD = 260_000;
    // 不论耗时多少，最少强制显示的条目数，保证报告不为空喵~
    private static final int MIN_ITEMS = 6;
    // 最多显示的条目数，超出部分折叠成"+ N more"避免报告过长喵~
    private static final int MAX_ITEMS = 20;

    // 持有 profiler 引用，用于后续按需查询各类统计数据喵~
    private final SlimefunProfiler profiler;
    // 本次 tick 的综合性能评级枚举值喵~
    private final PerformanceRating rating;
    // 本次 tick 所有 Slimefun 方块处理的总耗时，单位纳秒喵~
    private final long totalElapsedTime;
    // 本次 tick 中被处理的 Slimefun 方块总数喵~
    private final int totalTickedBlocks;
    // Slimefun tick 占整个服务器 tick 时间的百分比，用于评分可视化喵~
    private final float percentage;
    // 当前配置的 ticker 运行间隔，单位为游戏 tick(1 tick = 50ms)喵~
    private final int tickRate;

    // 按区块坐标分组的耗时统计 Map，key 为区块标识，value 为总耗时(纳秒)喵~
    private final Map<String, Long> chunks;
    // 按插件名称分组的耗时统计 Map，key 为插件名，value 为总耗时(纳秒)喵~
    private final Map<String, Long> plugins;
    // 按 Slimefun 物品 ID 分组的耗时统计 Map，key 为物品 ID，value 为总耗时(纳秒)喵~
    private final Map<String, Long> items;

    /**
     * 构造函数：从 profiler 中提取本次 tick 的全部统计数据并保存到字段喵~
     *
     * 输入：
     *   profiler          - 已完成本次 tick 采集的 SlimefunProfiler 实例喵~
     *   totalElapsedTime  - 本次 tick 所有 Slimefun 方块的总耗时(纳秒)喵~
     *   totalTickedBlocks - 本次 tick 被处理的 Slimefun 方块总数喵~
     */
    PerformanceSummary(@Nonnull SlimefunProfiler profiler, long totalElapsedTime, int totalTickedBlocks) {
        // 保存 profiler 引用，后续查询各种分类统计需要用到喵~
        this.profiler = profiler;
        // 从 profiler 获取本次 tick 综合性能评级喵~
        this.rating = profiler.getPerformance();
        // 从 profiler 获取 Slimefun 占服务器 tick 时间的百分比喵~
        this.percentage = profiler.getPercentageOfTick();
        // 保存外部传入的总耗时(纳秒)喵~
        this.totalElapsedTime = totalElapsedTime;
        // 保存外部传入的被处理方块总数喵~
        this.totalTickedBlocks = totalTickedBlocks;
        // 从 profiler 获取当前 ticker 的运行间隔(游戏 tick 数)喵~
        this.tickRate = profiler.getTickRate();

        // 获取按区块分组的耗时 Map，用于后续区块维度的报告喵~
        chunks = profiler.getByChunk();
        // 获取按插件分组的耗时 Map，用于后续插件维度的报告喵~
        plugins = profiler.getByPlugin();
        // 获取按物品 ID 分组的耗时 Map，用于后续物品维度的报告喵~
        items = profiler.getByItem();
    }

    /**
     * 将本次 tick 的性能摘要发送给查询方喵~
     *
     * 整体思路：
     *   1. 先打印头部汇总信息（总耗时、运行周期、性能评分）喵~
     *   2. 分三段分别统计物品、区块、插件的耗时排行喵~
     *   3. 若查询方开启了 verbose 模式，额外打印线程池状态喵~
     *
     * 输入：sender - 实现了 PerformanceInspector 接口的查询方（玩家或控制台）喵~
     */
    public void send(@Nonnull PerformanceInspector sender) {
        // 发送空行作为视觉分隔喵~
        sender.sendMessage("");
        // 发送报告标题头部绿色文字喵~
        sender.sendMessage(ChatColor.GREEN + "===== Slimefun 性能分析器 =====");
        // 发送本次 tick 总耗时，纳秒转换为毫秒显示喵~
        sender.sendMessage(
                ChatColor.GOLD + "Tick 总用时: " + ChatColor.YELLOW + NumberUtils.getAsMillis(totalElapsedTime));
        // 发送 ticker 运行周期，tickRate 除以 20 得到秒数并保留小数显示喵~
        sender.sendMessage(ChatColor.GOLD
                + "Ticker 运行周期: "
                + ChatColor.YELLOW
                + NumberUtils.roundDecimalNumber(tickRate / 20.0)
                + "s ("
                + tickRate
                + " ticks)");
        // 发送性能评分，内含可视化进度条与评级文字喵~
        sender.sendMessage(ChatColor.GOLD + "性能评分: " + getPerformanceRating());
        // 发送空行分隔头部信息与明细列表喵~
        sender.sendMessage("");

        /*
         * 统计各 Slimefun 物品 ID 的耗时排行喵~
         * Lambda 格式化每条记录：
         *   - 只有 1 个方块时直接显示总耗时喵~
         *   - 多个方块时根据排序方式决定先显示平均还是总耗时喵~
         */
        summarizeTimings(totalTickedBlocks, "block", sender, items, entry -> {
            // 查询该物品 ID 当前 tick 中有多少个方块被处理喵~
            int count = profiler.getBlocksOfId(entry.getKey());
            // 将该物品 ID 的总耗时(纳秒)格式化为毫秒字符串喵~
            String time = NumberUtils.getAsMillis(entry.getValue());
            // 构造基础显示格式：物品ID - 数量x (耗时占位)喵~
            String message = entry.getKey() + " - " + count + "x (%s)";

            // 喵~防御：只有 1 个或 0 个方块时没必要显示平均值，直接返回总耗时喵~
            if (count <= 1) {
                return String.format(message, time);
            }

            // 计算该物品 ID 的每方块平均耗时(纳秒)并格式化为毫秒字符串喵~
            String average = NumberUtils.getAsMillis(entry.getValue() / count);

            // 按平均值排序时，主显示平均用时并附注总耗时喵~
            if (sender.getOrderType() == SummaryOrderType.AVERAGE) {
                return String.format(message, average + " | 总用时: " + time);
            } else {
                // 按总耗时排序时，主显示总耗时并附注平均用时喵~
                return String.format(message, time + " | 平均用时: " + average);
            }
        });

        // 统计各区块的耗时排行，显示区块坐标与其中活跃方块数喵~
        summarizeTimings(chunks.size(), "chunk", sender, chunks, entry -> {
            // 查询该区块当前 tick 中有多少个 Slimefun 方块被处理喵~
            int count = profiler.getBlocksInChunk(entry.getKey());
            // 将该区块总耗时格式化为毫秒字符串喵~
            String time = NumberUtils.getAsMillis(entry.getValue());

            // 拼接区块坐标 + 方块数 + 耗时，方块数为 1 时不加复数 s 喵~
            return entry.getKey() + " - " + count + " block" + (count != 1 ? 's' : "") + " (" + time + ")";
        });

        // 统计各插件的耗时排行，显示插件名与其贡献的方块数喵~
        summarizeTimings(plugins.size(), "plugin", sender, plugins, entry -> {
            // 查询该插件贡献的被处理方块数喵~
            int count = profiler.getBlocksFromPlugin(entry.getKey());
            // 将该插件总耗时格式化为毫秒字符串喵~
            String time = NumberUtils.getAsMillis(entry.getValue());

            // 拼接插件名 + 方块数 + 耗时，方块数为 1 时不加复数 s 喵~
            return entry.getKey() + " - " + count + " block" + (count != 1 ? 's' : "") + " (" + time + ")";
        });

        // 如果查询方开启了详细模式，额外输出线程池状态信息喵~
        if (sender.isVerbose()) {
            // 空行分隔线程池信息与上面的统计列表喵~
            sender.sendMessage("");
            // 发送当前 Slimefun 线程池的运行状态字符串喵~
            sender.sendMessage(profiler.getThreadPoolStatus());
        }
    }

    /**
     * 通用耗时摘要方法：根据查询方类型选择合适的发送格式喵~
     *
     * 整体思路：
     *   - 对 Map 中的条目按照查询方指定的排序方式排列喵~
     *   - 若查询方是在线玩家(PlayerPerformanceInspector)则生成带悬浮提示的 TextComponent 喵~
     *   - 否则生成纯字符串发送给控制台或其他接收方喵~
     *
     * 输入：
     *   count     - 统计维度的总数（方块总数/区块总数/插件总数）喵~
     *   name      - 统计维度的单数名称（如 "block"/"chunk"/"plugin"）喵~
     *   inspector - 查询方，决定输出格式和排序喵~
     *   map       - 原始耗时数据 Map，key 为名称，value 为纳秒耗时喵~
     *   formatter - 将单条 Map.Entry 格式化为可读字符串的函数喵~
     */
    @ParametersAreNonnullByDefault
    private void summarizeTimings(
            int count,
            String name,
            PerformanceInspector inspector,
            Map<String, Long> map,
            Function<Map.Entry<String, Long>, String> formatter) {
        // 获取 Map 的条目集合，用于后续排序喵~
        Set<Entry<String, Long>> entrySet = map.entrySet();
        // 按查询方指定的排序规则对条目进行排序，返回排好序的 List 喵~
        List<Entry<String, Long>> results = inspector.getOrderType().sort(profiler, entrySet);
        // 拼接统计头：如 "42 blocks"，数量为 1 时不加复数 s 喵~
        String prefix = count + " " + name + (count != 1 ? 's' : "");

        // 判断查询方是否为在线玩家，玩家可以看到悬浮文本组件喵~
        if (inspector instanceof PlayerPerformanceInspector playerPerformanceInspector) {
            // 为玩家生成带悬浮提示的 TextComponent 并发送喵~
            TextComponent component = summarizeAsTextComponent(count, prefix, results, formatter);
            playerPerformanceInspector.sendMessage(component);
        } else {
            // 为控制台或其他接收方生成纯文本字符串并发送喵~
            String text = summarizeAsString(inspector, count, prefix, results, formatter);
            inspector.sendMessage(text);
        }
    }

    /**
     * 为在线玩家生成带悬浮提示的 TextComponent 性能摘要喵~
     *
     * 整体思路：
     *   - 主组件显示统计头（如 "42 blocks"）并附加一个"鼠标悬浮查看详情"的提示文字喵~
     *   - 详情通过 HoverEvent 悬浮文本承载，格式化各条目后拼入 StringBuilder 喵~
     *   - 超过 MAX_ITEMS 且耗时低于 VISIBILITY_THRESHOLD 的条目折叠显示喵~
     *
     * 输入：
     *   count     - 统计维度总数喵~
     *   prefix    - 已拼好的统计头字符串（如 "42 blocks"）喵~
     *   results   - 已排好序的条目 List 喵~
     *   formatter - 条目格式化函数喵~
     * 输出：可直接发送给玩家的 TextComponent 喵~
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    private TextComponent summarizeAsTextComponent(
            int count,
            String prefix,
            List<Map.Entry<String, Long>> results,
            Function<Entry<String, Long>, String> formatter) {
        // 创建主组件，文字内容为统计头（如 "42 blocks"）喵~
        TextComponent component = new TextComponent(prefix);
        // 将主组件颜色设为黄色，醒目显示喵~
        component.setColor(ChatColor.YELLOW);

        // 喵~防御：count 为 0 时不生成悬浮内容，避免空悬浮框干扰视觉喵~
        if (count > 0) {
            // 创建提示组件，告知玩家将鼠标悬停可查看详细耗时数据喵~
            TextComponent hoverComponent = new TextComponent("  (将鼠标放置到此处获取更多信息)");
            // 提示文字用灰色，不喧宾夺主喵~
            hoverComponent.setColor(ChatColor.GRAY);
            // 用于拼接悬浮框内所有条目文本的 StringBuilder 喵~
            StringBuilder builder = new StringBuilder();

            // 已显示的条目计数，控制不超过 MAX_ITEMS 喵~
            int shownEntries = 0;
            // 被折叠隐藏的条目计数，最后显示"+ N more"喵~
            int hiddenEntries = 0;

            /*
             * 主人注意：此处遍历所有排序后的条目，当条目数量非常多时（如上千个物品）
             * 每次 send() 调用都会全量遍历，可能有轻微性能压力喵~
             * 建议后续考虑分页或只传入截断后的子列表喵~
             */
            for (Map.Entry<String, Long> entry : results) {
                // 判断该条目是否需要显示：未超出最大数量，且满足最小保底数或耗时超过可见阈值喵~
                if (shownEntries < MAX_ITEMS && (shownEntries < MIN_ITEMS || entry.getValue() > VISIBILITY_THRESHOLD)) {
                    // 换行后追加黄色格式化条目文本喵~
                    builder.append("\n").append(ChatColor.YELLOW).append(formatter.apply(entry));
                    // 已显示条目数加一喵~
                    shownEntries++;
                } else {
                    // 不符合显示条件的条目计入隐藏数量喵~
                    hiddenEntries++;
                }
            }

            // 如果有被折叠的条目，在末尾追加"+ N more"提示喵~
            if (hiddenEntries > 0) {
                builder.append("\n\n&c+ &6").append(hiddenEntries).append(" more");
            }

            // 将 StringBuilder 中的旧版颜色代码(&x)转换为 ChatColor，再封装为悬浮文本 Content 喵~
            Content content = new Text(TextComponent.fromLegacyText(ChatColors.color(builder.toString())));
            // 将悬浮内容绑定到提示组件的 HoverEvent 上，玩家悬停鼠标时显示喵~
            hoverComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, content));

            // 将带悬浮事件的提示组件附加到主组件后面，玩家看到的是"42 blocks  (将鼠标放置到此处...)"喵~
            component.addExtra(hoverComponent);
        }

        return component;
    }

    /**
     * 为控制台或非玩家查询方生成纯文本性能摘要字符串喵~
     *
     * 整体思路：
     *   - verbose 模式下显示全部条目不截断喵~
     *   - 非 verbose 模式下按 MIN_ITEMS/MAX_ITEMS/VISIBILITY_THRESHOLD 进行截断喵~
     *   - 超出部分在末尾追加"+ N more..."喵~
     *
     * 输入：
     *   inspector - 查询方，用于判断是否 verbose 模式及格式化函数喵~
     *   count     - 统计维度总数喵~
     *   prefix    - 统计头字符串（如 "42 blocks"）喵~
     *   results   - 已排好序的条目 List 喵~
     *   formatter - 条目格式化函数喵~
     * 输出：可直接发送的纯文本字符串喵~
     */
    @Nonnull
    @ParametersAreNonnullByDefault
    private String summarizeAsString(
            PerformanceInspector inspector,
            int count,
            String prefix,
            List<Entry<String, Long>> results,
            Function<Entry<String, Long>, String> formatter) {
        // 已显示的条目计数喵~
        int shownEntries = 0;
        // 被折叠隐藏的条目计数喵~
        int hiddenEntries = 0;

        // 构建输出字符串的 StringBuilder 喵~
        StringBuilder builder = new StringBuilder();
        // 先追加金色的统计头（如 "42 blocks"）喵~
        builder.append(ChatColor.GOLD).append(prefix);

        // 喵~防御：count 为 0 时不追加任何条目，避免打印空列表喵~
        if (count > 0) {
            // 切换为黄色输出后续各条目文本喵~
            builder.append(ChatColor.YELLOW);

            /*
             * 主人注意：同上，此处全量遍历所有条目，条目极多时请关注性能喵~
             */
            for (Map.Entry<String, Long> entry : results) {
                // verbose 模式显示全部；否则检查数量和耗时阈值决定是否显示喵~
                if (inspector.isVerbose()
                        || (shownEntries < MAX_ITEMS
                                && (shownEntries < MIN_ITEMS || entry.getValue() > VISIBILITY_THRESHOLD))) {
                    // 每条记录前缩进两个空格，提升可读性喵~
                    builder.append("\n  ");
                    // 去除格式化字符串中的颜色代码，控制台不支持颜色喵~
                    builder.append(ChatColor.stripColor(formatter.apply(entry)));
                    // 已显示条目数加一喵~
                    shownEntries++;
                } else {
                    // 不符合显示条件计入隐藏数量喵~
                    hiddenEntries++;
                }
            }

            // 如果有被折叠的条目，追加"+ N more..."提示喵~
            if (hiddenEntries > 0) {
                builder.append("\n+ ").append(hiddenEntries).append(" more...");
            }
        }

        // 返回拼接好的完整字符串喵~
        return builder.toString();
    }

    /**
     * 生成性能评分的可视化字符串，包含彩色进度条和评级文字喵~
     *
     * 整体思路：
     *   - 根据 percentage（Slimefun 占 tick 时间比）从右往左填充彩色冒号作为进度条喵~
     *   - percentage 越高代表性能越差，进度条颜色越红喵~
     *   - 未填充部分用深灰色冒号补足至总长度 20 格喵~
     *   - 末尾附加评级枚举名（GREAT/GOOD/etc.）和具体百分比数值喵~
     *
     * 输出：格式化的性能评分字符串，含颜色代码喵~
     */
    @Nonnull
    private String getPerformanceRating() {
        // 创建 StringBuilder 构建进度条字符串喵~
        StringBuilder builder = new StringBuilder();
        // 根据(100 - percentage)计算进度条颜色：使用率越低颜色越绿喵~
        builder.append(NumberUtils.getColorFromPercentage(100 - Math.min(percentage, 100)));

        // 进度条总格数为 20，每 5% 占一格，计算已用格数后的剩余格数喵~
        int rest = 20;
        // 每 5% 追加一个冒号作为已用格，同时剩余格数减一喵~
        for (int i = (int) Math.min(percentage, 100); i >= 5; i = i - 5) {
            builder.append(':');
            // 剩余空格数随已填充格数递减喵~
            rest--;
        }

        // 追加深灰色冒号填充剩余格数，Math.max 防止 rest 为负数时 repeat 抛异常喵~
        builder.append(ChatColor.DARK_GRAY)
                .append(":".repeat(Math.max(0, rest)))
                // 追加分隔符喵~
                .append(" - ")
                // 追加评级对应的颜色代码喵~
                .append(rating.getColor())
                // 追加评级枚举名的人类可读形式（如 GREAT → Great）喵~
                .append(ChatUtils.humanize(rating.name()))
                // 切换为灰色追加括号内的百分比数值喵~
                .append(ChatColor.GRAY)
                .append(" (")
                // 将百分比保留指定小数位后追加喵~
                .append(NumberUtils.roundDecimalNumber(percentage))
                .append("%)");

        // 返回拼好的进度条 + 评级字符串喵~
        return builder.toString();
    }
}
