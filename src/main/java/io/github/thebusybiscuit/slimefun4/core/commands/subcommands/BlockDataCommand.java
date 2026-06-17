package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.ParametersAreNonnullByDefault;
import net.guizhanss.slimefun4.utils.ChatUtils;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * 该指令可直接对 Slimefun 方块数据进行设置。
 * 支持 get（查询）、set（设置）、remove（移除）三种子操作，作用于玩家准星对准的 Slimefun 方块喵~
 *
 * @author ybw0014
 */
class BlockDataCommand extends SubCommand {
    // 注解声明该构造方法的所有参数均不允许为 null，防止空参数传入导致后续方法崩溃喵~
    @ParametersAreNonnullByDefault
    // 构造方法：将 "blockdata" 子命令注册到主命令中，false 表示不限制仅 OP 可用喵~
    BlockDataCommand(Slimefun plugin, SlimefunCommand cmd) {
        super(plugin, cmd, "blockdata", false);
    }

    // 重写父类方法，返回该子命令对应的本地化描述键名，用于在帮助列表中显示说明喵~
    @Override
    protected String getDescription() {
        return "commands.blockdata.description";
    }

    /*
     * onExecute 方法：执行 /sf blockdata 指令的核心逻辑喵~
     *
     * 整体思路：
     *   1. 验证执行者必须是玩家（非控制台）喵~
     *   2. 验证执行者拥有 slimefun.command.blockdata 权限喵~
     *   3. 验证参数数量不少于 3 个（至少含操作类型和 key）喵~
     *   4. 获取玩家准星对准的方块及其 Slimefun 数据容器喵~
     *   5. 根据操作类型（get/set/remove）分支执行对应逻辑喵~
     *
     * 输入：sender（指令执行者），args（指令参数数组，args[1]=操作类型，args[2]=key，args[3]=value）
     * 边界条件：target 为 null / 空气方块 / 非 Slimefun 方块时提前退出喵~
     */
    @Override
    public void onExecute(CommandSender sender, String[] args) {
        // 喵~防御：若执行者不是玩家（如控制台），则发送仅限玩家使用的提示并中止执行，避免后续调用玩家专属方法报错喵~
        if (!(sender instanceof Player player)) {
            Slimefun.getLocalization().sendMessage(sender, "messages.only-players", true);
            return;
        }

        // 喵~防御：检查执行者是否拥有 blockdata 指令权限，无权限时发送提示并中止，防止越权操作喵~
        if (!sender.hasPermission("slimefun.command.blockdata")) {
            Slimefun.getLocalization().sendMessage(sender, "messages.no-permission", true);
            return;
        }

        // 喵~防御：参数数量不足 3 个（缺少操作类型或 key），发送用法提示并中止，避免后续 args 越界访问喵~
        if (args.length < 3) {
            Slimefun.getLocalization()
                    .sendMessage(
                            sender,
                            "messages.usage",
                            true,
                            // 用 lambda 将消息中的 %usage% 占位符替换为实际用法说明喵~
                            msg -> msg.replace("%usage%", "/sf blockdata get/set/remove <key> [value]"));
            return;
        }

        // 获取玩家准星对准的方块，最远距离 8 格，不穿透液体方块喵~
        Block target = player.getTargetBlockExact(8, FluidCollisionMode.NEVER);
        // 从 StorageCacheUtils 缓存中读取该位置方块的 Slimefun 数据容器（可能为 null）喵~
        var blockData = StorageCacheUtils.getDataContainer(target.getLocation());

        // 喵~防御：target 为 null（没看向方块）、方块是空气、或该方块没有 Slimefun 数据时，提示玩家并中止喵~
        if (target == null || target.getType().isAir() || blockData == null) {
            ChatUtils.sendMessage(player, "&c你需要看向一个 Slimefun 方块才能执行该指令!");
            return;
        }

        // 从参数数组第 3 位（下标 2）取出要操作的数据 key 名称喵~
        String key = args[2];

        // 根据操作类型（args[1]）分支处理：get 查询、set 设置、remove 移除，其余输出用法提示喵~
        switch (args[1]) {
            case "get" -> {
                // 从方块数据容器中读取指定 key 对应的值（不存在则返回 null）喵~
                String value = blockData.getData(key);
                // 向玩家发送格式化的查询结果，若值为 null 则显示字符串 "null" 避免空指针展示异常喵~
                ChatUtils.sendMessage(
                        player,
                        "&a该方块 &b%key% &a的值为: &e%value%",
                        msg -> msg.replace("%key%", key).replace("%value%", value == null ? "null" : value));
            }
            case "set" -> {
                // 喵~防御：set 操作需要第 4 个参数作为 value，参数不足时提示用法并中止，避免 args[3] 越界喵~
                if (args.length < 4) {
                    Slimefun.getLocalization()
                            .sendMessage(
                                    sender,
                                    "messages.usage",
                                    true,
                                    msg -> msg.replace("%usage%", "/sf blockdata set <key> <value>"));
                    return;
                }

                // 喵~防御：禁止通过指令修改方块的 "id" 字段，防止数据污染破坏方块的 Slimefun 类型标识喵~
                if (key.equalsIgnoreCase("id")) {
                    ChatUtils.sendMessage(player, "&c你不能修改方块的 ID!");
                    return;
                }

                // 从参数数组第 4 位（下标 3）取出要写入的值喵~
                String value = args[3];

                // 将指定 key 的值写入方块数据容器（持久化存储）喵~
                blockData.setData(key, value);
                // 向玩家发送设置成功的确认消息，用实际 key 和 value 替换占位符喵~
                ChatUtils.sendMessage(
                        player,
                        "&a已设置该方块 &b%key% &a的值为: &e%value%",
                        msg -> msg.replace("%key%", key).replace("%value%", value));
            }
            case "remove" -> {
                // 喵~防御：同样禁止移除方块的 "id" 字段，防止删除核心标识导致方块行为异常喵~
                if (key.equalsIgnoreCase("id")) {
                    ChatUtils.sendMessage(player, "&c你不能修改方块的 ID!");
                    return;
                }

                // 从方块数据容器中删除指定 key 的数据条目喵~
                blockData.removeData(key);
                // 向玩家发送移除成功的确认消息，用实际 key 替换占位符喵~
                ChatUtils.sendMessage(player, "&a已移除该方块 &b%key% &a的值", msg -> msg.replace("%key%", key));
            }
            default -> {
                // 操作类型不合法时，向玩家发送完整用法提示喵~
                Slimefun.getLocalization()
                        .sendMessage(
                                sender,
                                "messages.usage",
                                true,
                                msg -> msg.replace("%usage%", "/sf blockdata get/set/remove <key> [value]"));
            }
        }
    }
}
