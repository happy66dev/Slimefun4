package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import io.github.bakedlibs.dough.common.PlayerList;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Optional;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// 研究系统的子命令类，处理 /sf research <玩家> <all/reset/研究ID> 指令逻辑喵~
class ResearchCommand extends SubCommand {

    // 指令消息中用于替换玩家名的占位符常量喵~
    private static final String PLACEHOLDER_PLAYER = "%player%";
    // 指令消息中用于替换研究名称的占位符常量喵~
    private static final String PLACEHOLDER_RESEARCH = "%research%";

    @ParametersAreNonnullByDefault
    ResearchCommand(Slimefun plugin, SlimefunCommand cmd) {
        // 调用父类构造器，注册 "research" 子命令，false 表示控制台也可使用喵~
        super(plugin, cmd, "research", false);
    }

    @Override
    protected String getDescription() {
        // 返回本地化键名，用于在帮助信息中显示该指令的描述文字喵~
        return "commands.research.description";
    }

    /*
     * 整体思路：执行 /sf research 指令的核心方法喵~
     * 输入：sender=指令发送者(玩家或控制台)，args=指令参数数组
     * 流程：
     *   1. 检查服务器是否开启了研究功能，未开启则发送提示并中止喵~
     *   2. 检查参数数量是否为3(sf research <玩家> <操作>)喵~
     *   3. 验证发送者权限(非玩家或拥有 slimefun.cheat.researches 权限)喵~
     *   4. 按玩家名查找在线玩家，找不到则提示未在线喵~
     *   5. 异步加载目标玩家档案，根据第三个参数分发到对应处理方法喵~
     * 边界条件：参数不足时发送用法提示喵~
     */
    @Override
    public void onExecute(CommandSender sender, String[] args) {
        // Check if researching is even enabled
        // 喵~防御：如果服务器配置禁用了研究功能，立即提示并返回，避免无效操作喵~
        if (!Slimefun.getConfigManager().isResearchingEnabled()) {
            // 向发送者发送"研究功能已禁用"的本地化消息喵~
            Slimefun.getLocalization().sendMessage(sender, "messages.researching-is-disabled");
            return;
        }

        // 检查参数数量是否恰好为3：/sf research <玩家名> <操作>喵~
        if (args.length == 3) {
            // 验证发送者是控制台(非Player实例)或拥有作弊研究的权限节点喵~
            if (!(sender instanceof Player) || sender.hasPermission("slimefun.cheat.researches")) {
                // 通过玩家名在在线玩家列表中搜索目标玩家喵~
                Optional<Player> player = PlayerList.findByName(args[1]);

                // 喵~防御：玩家不在线时 Optional 为空，需提示"玩家不在线"消息喵~
                if (player.isPresent()) {
                    // 取出 Optional 中的在线玩家对象喵~
                    Player p = player.get();

                    // Getting the PlayerProfile async
                    // 异步加载目标玩家的 Slimefun 档案(PlayerProfile)，避免主线程阻塞喵~
                    PlayerProfile.get(p, profile -> {
                        // 判断第三个参数是否为 "all"，若是则解锁全部研究喵~
                        if (args[2].equalsIgnoreCase("all")) {
                            researchAll(sender, profile, p);
                            // 判断第三个参数是否为 "reset"，若是则重置全部研究进度喵~
                        } else if (args[2].equalsIgnoreCase("reset")) {
                            reset(profile, p);
                            // 否则视为具体研究ID，尝试为玩家解锁该研究喵~
                        } else {
                            giveResearch(sender, p, args[2]);
                        }
                    });
                } else {
                    // 目标玩家不在线，向发送者发送提示消息，并将占位符替换为输入的玩家名喵~
                    Slimefun.getLocalization()
                            .sendMessage(
                                    sender,
                                    "messages.not-online",
                                    true,
                                    msg -> msg.replace(PLACEHOLDER_PLAYER, args[1]));
                }
            } else {
                // 发送者是玩家且没有权限，发送"无权限"提示消息喵~
                Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            }
        } else {
            // 参数数量不对，向发送者发送用法提示，指引正确的指令格式喵~
            Slimefun.getLocalization()
                    .sendMessage(
                            sender,
                            "messages.usage",
                            true,
                            msg -> msg.replace("%usage%", "/sf research <Player> <all/reset/Research>"));
        }
    }

    /*
     * 整体思路：为指定玩家解锁单条研究的方法喵~
     * 输入：sender=指令发送者，p=目标玩家，input=研究的命名空间字符串ID喵~
     * 流程：
     *   1. 将字符串转换为 Research 对象(通过 getResearchFromString)喵~
     *   2. 找到则调用 unlock() 解锁并向玩家发送成功消息喵~
     *   3. 找不到则向发送者发送"无效研究"提示并替换研究名占位符喵~
     * 边界条件：input 不含冒号或不匹配任何研究时返回 empty喵~
     */
    @ParametersAreNonnullByDefault
    private void giveResearch(CommandSender sender, Player p, String input) {
        // 将字符串输入转换为 Research 对象，返回 Optional 便于判断是否找到喵~
        Optional<Research> research = getResearchFromString(input);

        // 喵~防御：Optional 为空表示找不到该研究，需提示无效研究名，避免空指针喵~
        if (research.isPresent()) {
            // 调用 Research.unlock() 为玩家解锁该研究，回调中发送解锁成功消息喵~
            research.get().unlock(p, true, player -> {
                // 构建消息占位符替换函数，将消息中的玩家名和研究名替换为实际值喵~
                UnaryOperator<String> variables = msg -> msg.replace(PLACEHOLDER_PLAYER, player.getName())
                        .replace(PLACEHOLDER_RESEARCH, research.get().getName(player));
                // 向玩家发送"获得研究"的本地化提示消息喵~
                Slimefun.getLocalization().sendMessage(player, "messages.give-research", true, variables);
            });
        } else {
            // 找不到对应研究，向发送者发送"无效研究"消息，并替换研究名占位符喵~
            Slimefun.getLocalization()
                    .sendMessage(
                            sender, "messages.invalid-research", true, msg -> msg.replace(PLACEHOLDER_RESEARCH, input));
        }
    }

    /*
     * 整体思路：为指定玩家一次性解锁服务器中所有研究的方法喵~
     * 输入：sender=指令发送者，profile=目标玩家档案，p=目标玩家喵~
     * 流程：遍历所有已注册研究，若玩家尚未解锁则发送通知消息，然后执行解锁喵~
     * 主人注意：遍历所有研究并逐一调用 unlock()，当研究数量较多时可能产生较多消息，
     *   一般研究总量不超过几百，性能影响可接受喵~
     */
    @ParametersAreNonnullByDefault
    private void researchAll(CommandSender sender, PlayerProfile profile, Player p) {
        // 遍历服务器注册表中所有已注册的研究对象喵~
        for (Research res : Slimefun.getRegistry().getResearches()) {
            // 仅对玩家尚未解锁的研究发送"获得研究"通知消息，已解锁的不重复通知喵~
            if (!profile.hasUnlocked(res)) {
                // 向发送者发送通知消息，告知某玩家获得了某研究，并替换占位符喵~
                Slimefun.getLocalization()
                        .sendMessage(
                                sender,
                                "messages.give-research",
                                true,
                                msg -> msg.replace(PLACEHOLDER_PLAYER, p.getName())
                                        .replace(PLACEHOLDER_RESEARCH, res.getName(p)));
            }

            // 无论之前是否解锁，都强制调用 unlock() 确保玩家拥有该研究喵~
            res.unlock(p, true);
        }
    }

    /*
     * 整体思路：重置玩家所有研究进度的方法，将所有研究标记为未解锁喵~
     * 输入：profile=目标玩家档案，p=目标玩家喵~
     * 流程：遍历所有研究，对每条研究调用 setResearched(false) 清除解锁状态，
     *   最后发送重置成功的本地化消息喵~
     */
    @ParametersAreNonnullByDefault
    private void reset(PlayerProfile profile, Player p) {
        // 遍历所有已注册研究，逐一清除玩家的解锁状态喵~
        for (Research research : Slimefun.getRegistry().getResearches()) {
            // 将该研究在玩家档案中的解锁状态设为 false，即重置为未解锁喵~
            profile.setResearched(research, false);
        }

        // 向玩家发送研究重置成功的本地化消息，并替换玩家名占位符喵~
        Slimefun.getLocalization()
                .sendMessage(p, "commands.research.reset", true, msg -> msg.replace(PLACEHOLDER_PLAYER, p.getName()));
    }

    /*
     * 整体思路：将字符串形式的研究ID转换为 Research 对象的工具方法喵~
     * 输入：input=玩家输入的研究ID字符串，格式应为 "命名空间:键名" 如 "slimefun:fire_cake"喵~
     * 输出：Optional<Research>，匹配则包含对应研究，否则为 empty喵~
     * 边界条件：input 不含冒号时直接返回 empty，避免遍历无意义的格式喵~
     */
    @Nonnull
    private Optional<Research> getResearchFromString(@Nonnull String input) {
        // 喵~防御：研究ID必须包含冒号(命名空间格式如 "slimefun:xxx")，否则直接返回空喵~
        if (!input.contains(":")) {
            return Optional.empty();
        }

        // 遍历所有已注册研究，按命名空间键名进行不区分大小写的匹配喵~
        for (Research research : Slimefun.getRegistry().getResearches()) {
            // 将研究的 NamespacedKey 转为字符串后与输入进行不区分大小写的比较喵~
            if (research.getKey().toString().equalsIgnoreCase(input)) {
                // 找到匹配的研究，包装为 Optional 返回喵~
                return Optional.of(research);
            }
        }

        // 遍历完毕未找到匹配，返回空 Optional 表示研究不存在喵~
        return Optional.empty();
    }
}
