package io.github.thebusybiscuit.slimefun4.core.commands;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.generator.WorldInfo;

// Slimefun指令的Tab自动补全处理器，实现TabCompleter接口，负责根据玩家已输入的指令参数动态生成候选提示列表喵~
class SlimefunTabCompleter implements TabCompleter {

    // 每次Tab补全最多返回80条候选建议，防止列表过长卡顿客户端喵~
    private static final int MAX_SUGGESTIONS = 80;

    // 持有SlimefunCommand引用，用于获取所有子指令名称喵~
    private final SlimefunCommand command;

    // 构造函数：接收SlimefunCommand实例并保存，以便后续查询子指令列表喵~
    public SlimefunTabCompleter(@Nonnull SlimefunCommand command) {
        this.command = command; // 将传入的指令对象赋值给成员变量，供其他方法使用喵~
    }

    /*
     * 整体思路：根据玩家当前已输入的参数数量（args.length）分情况生成候选列表喵~
     * - 第1个参数：补全子指令名称（如give、research、banitem等）
     * - 第2个参数：根据第1个参数的子指令类型，补全对应的目标（物品ID/世界名等）
     * - 第3个参数：根据子指令类型，补全研究key、数据类型等
     * - 第4个参数：仅give指令时补全数量
     * - 其他情况：返回null让Bukkit默认补全在线玩家名喵~
     * 输入：CommandSender发送者、Command命令对象、label指令标签、args已输入的参数数组
     * 输出：候选字符串列表，或null表示使用默认在线玩家补全
     * 边界条件：args长度为0时不会进入任何分支，直接跳到else返回null喵~
     */
    @Nullable @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            // 玩家正在输入第1个参数，补全所有可用的子指令名称喵~
            return createReturnList(command.getSubCommandNames(), args[0]);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("banitem")) {
                // 第1个参数是"banitem"（禁用物品），第2个参数补全所有已启用的Slimefun物品ID供选择喵~
                return createReturnList(getSlimefunItems(), args[1]);
            } else if (args[0].equalsIgnoreCase("unbanitem")) {
                // 第1个参数是"unbanitem"（解禁物品），从注册表中获取所有已被禁用的物品ID列表喵~
                List<String> list = Slimefun.getRegistry().getDisabledSlimefunItems().stream()
                        // 将每个SlimefunItem对象转换为它的唯一字符串ID喵~
                        .map(SlimefunItem::getId)
                        // 将Stream收集为ArrayList返回喵~
                        .collect(Collectors.toList());
                return createReturnList(list, args[1]); // 根据玩家已输入内容过滤并返回候选列表喵~
            } else if (args[0].equalsIgnoreCase("cleardata")) {
                // 第1个参数是"cleardata"（清除数据），第2个参数补全所有世界名称喵~
                List<String> list = new ArrayList<>(
                        // 获取服务器所有已加载的世界，并提取每个世界的名称喵~
                        Bukkit.getWorlds().stream().map(WorldInfo::getName).toList());
                list.add("*"); // 添加通配符"*"表示清除所有世界的数据喵~
                return createReturnList(list, args[1]); // 根据玩家已输入内容过滤世界名喵~
            } else if (args[0].equalsIgnoreCase("machine-damage")) {
                // 第1个参数是"machine-damage"（机器损坏），第2个参数补全3个操作选项喵~
                List<String> list = Arrays.asList("scrap", "repair", "info");
                return createReturnList(list, args[1]); // 返回scrap/repair/info三个候选操作喵~
            }
            // 喵~防御：其他未识别的子指令在第2个参数时返回null，回退到Bukkit默认的在线玩家补全喵
            return null;
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give")) {
                // 第1个参数是"give"（给予物品），第3个参数补全所有已启用的Slimefun物品ID喵~
                return createReturnList(getSlimefunItems(), args[2]);
            } else if (args[0].equalsIgnoreCase("research")) {
                // 第1个参数是"research"（研究），从注册表获取所有已注册的研究对象列表喵~
                List<Research> researches = Slimefun.getRegistry().getResearches();
                // 使用LinkedList存储候选建议，因为会频繁在末尾追加元素喵~
                List<String> suggestions = new LinkedList<>();

                suggestions.add("all"); // 添加"all"选项，表示解锁所有研究喵~
                suggestions.add("reset"); // 添加"reset"选项，表示重置所有研究进度喵~

                // 主人注意：当已注册研究数量很多时，此循环遍历可能略有耗时，不过通常研究数量有限不超过几百条喵~
                for (Research research : researches) {
                    // 将每个研究的命名空间键转换为小写字符串（如"slimefun:coal_generator"）并加入候选列表喵~
                    suggestions.add(research.getKey().toString().toLowerCase(Locale.ROOT));
                }

                return createReturnList(suggestions, args[2]); // 根据玩家已输入内容过滤研究键喵~
            } else if (args[0].equalsIgnoreCase("cleardata")) {
                // 第1个参数是"cleardata"，第3个参数补全清除的数据类型：方块数据/油数据/所有数据喵~
                return createReturnList(List.of("block", "oil", "*"), args[2]);
            } else {
                // 返回null让Bukkit使用默认补全（在线玩家名），适用于给予物品等需要指定玩家的指令喵~
                return null;
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            // give指令的第4个参数是物品数量，提供常用数量选项（1到64的典型值）供快速选择喵~
            return createReturnList(Arrays.asList("1", "2", "4", "8", "16", "32", "64"), args[3]);
        } else {
            // 返回null让Bukkit使用默认补全（在线玩家名），适用于参数超出预期范围的情况喵~
            return null;
        }
    }

    /***
     * 从给定列表中筛选出包含输入字符串的元素，返回过滤后的候选补全列表喵~
     * 整体思路：如果用户尚未输入任何字符，直接截取前MAX_SUGGESTIONS条返回；
     * 否则将用户输入转小写后遍历列表，把包含该字符串的元素收集到结果列表中，
     * 超过MAX_SUGGESTIONS时立即截断避免客户端卡顿喵~
     * 输入：list—完整候选列表；string—玩家已输入的字符串（可能为空）
     * 输出：过滤后最多MAX_SUGGESTIONS条的候选字符串列表，永不为null喵~
     * 边界条件：string为空时返回整个列表（截断至上限）；完全匹配时返回空列表让Bukkit补全完成喵~
     *
     * @param list
     *            The list to process
     * @param string
     *            The typed string
     * @return Sublist if string is not empty
     */
    @Nonnull
    private List<String> createReturnList(@Nonnull List<String> list, @Nonnull String string) {
        if (string.isEmpty()) {
            // 喵~防御：用户还没有输入任何字符时，直接对完整列表进行上限截断再返回喵
            if (list.size() >= MAX_SUGGESTIONS) {
                // 列表超过80条时只返回前80条，防止发送过多数据给客户端导致卡顿喵~
                return list.subList(0, MAX_SUGGESTIONS);
            } else {
                // 列表未超过上限，直接返回完整列表喵~
                return list;
            }
        }

        String input = string.toLowerCase(Locale.ROOT); // 将用户输入统一转为小写，实现大小写不敏感的模糊匹配喵~
        List<String> returnList = new LinkedList<>(); // 使用LinkedList存放筛选后的候选结果，适合频繁追加操作喵~

        // 主人注意：此处遍历完整候选列表进行字符串匹配，当list条目超过数千时可能有轻微性能开销，不过通常物品数量有限喵~
        for (String item : list) {
            if (item.toLowerCase(Locale.ROOT).contains(input)) {
                // 候选项转小写后包含用户输入的字符串，符合模糊匹配条件，加入结果列表喵~
                returnList.add(item);

                if (returnList.size() >= MAX_SUGGESTIONS) {
                    // 已收集到足够多的候选项，提前退出循环避免继续浪费性能喵~
                    break;
                }
            } else if (item.equalsIgnoreCase(input)) {
                // 候选项与用户输入完全匹配（忽略大小写），说明用户已输入完整，返回空列表让Tab补全静默完成喵~
                return Collections.emptyList();
            }
        }

        return returnList; // 返回过滤后的候选列表，可能为空列表（无匹配项）喵~
    }

    // 从Slimefun注册表中获取所有已启用物品的唯一ID字符串列表，用于Tab补全候选项喵~
    @Nonnull
    private List<String> getSlimefunItems() {
        List<SlimefunItem> items = Slimefun.getRegistry().getEnabledSlimefunItems(); // 从全局注册表获取所有已启用的Slimefun物品对象列表喵~
        List<String> list = new ArrayList<>(items.size()); // 预分配与物品数量相同的容量，避免ArrayList多次扩容带来的性能开销喵~

        for (SlimefunItem item : items) {
            list.add(item.getId()); // 提取每个Slimefun物品的唯一字符串ID（如"ENHANCED_FURNACE"）并追加到列表喵~
        }

        return list; // 返回所有已启用Slimefun物品的ID字符串列表喵~
    }
}
