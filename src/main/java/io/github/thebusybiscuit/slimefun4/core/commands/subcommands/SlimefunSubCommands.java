// 声明此文件所属的包，位于子命令模块下喵~
package io.github.thebusybiscuit.slimefun4.core.commands.subcommands;

// 引入 Slimefun 主命令类，用于获取插件实例和关联命令喵~
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
// 引入子命令接口，所有子命令都实现此接口喵~
import io.github.thebusybiscuit.slimefun4.core.commands.SubCommand;
// 引入 Slimefun 插件主类，用于实例化各个子命令喵~
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
// 引入集合接口，作为方法返回值类型，方便调用方以集合形式使用子命令列表喵~
import java.util.Collection;
// 引入链表实现，用于构建有序的子命令列表，适合频繁追加操作喵~
import java.util.LinkedList;
// 引入 List 接口，作为本地变量类型声明喵~
import java.util.List;
// 引入非空注解，标记参数和返回值不允许为 null 喵~
import javax.annotation.Nonnull;

/**
 * This class holds the implementations of every {@link SubCommand}.
 * The implementations themselves are package-private, this class only provides
 * a static setup method
 *
 * 本类是所有 Slimefun 子命令的注册汇总入口喵~
 * 通过静态方法 getAllCommands 统一创建并返回全部子命令实例，
 * 各子命令类本身是包私有的，外部只需调用此方法即可获取完整命令列表喵~
 *
 * @author TheBusyBiscuit
 *
 */
public final class SlimefunSubCommands {

    // 私有构造方法，禁止外部实例化此工具类，因为它只提供静态方法喵~
    private SlimefunSubCommands() {}

    /*
     * 整体思路：集中注册所有 /sf 子命令喵~
     * 输入：SlimefunCommand 主命令对象，用于关联插件实例和父命令
     * 输出：包含所有子命令实例的集合，供命令分发器逐一注册
     * 边界条件：cmd 不能为 null（由 @Nonnull 注解约束），
     *           若某个子命令构造失败会直接抛出异常喵~
     */
    @Nonnull
    public static Collection<SubCommand> getAllCommands(@Nonnull SlimefunCommand cmd) {
        // 从主命令中获取 Slimefun 插件实例，用于后续构造每个子命令喵~
        Slimefun plugin = cmd.getPlugin();
        // 创建一个空链表，用于依次收集所有子命令实例喵~
        List<SubCommand> commands = new LinkedList<>();

        // 注册 /sf help：显示 Slimefun 命令帮助信息喵~
        commands.add(new HelpCommand(plugin, cmd));
        // 注册 /sf versions：显示插件及依赖版本信息喵~
        commands.add(new VersionsCommand(plugin, cmd));
        // 注册 /sf cheat：以作弊模式给予玩家物品（需要权限）喵~
        commands.add(new CheatCommand(plugin, cmd));
        // 注册 /sf guide：给予玩家 Slimefun 指南书喵~
        commands.add(new GuideCommand(plugin, cmd));
        // 注册 /sf give：给予玩家指定的 Slimefun 物品喵~
        commands.add(new GiveCommand(plugin, cmd));
        // 注册 /sf getresearch：查询玩家的研究解锁状态喵~
        commands.add(new GetResearchCommand(plugin, cmd));
        // 注册 /sf research：为玩家解锁或管理研究进度喵~
        commands.add(new ResearchCommand(plugin, cmd));
        // 注册 /sf stats：查看玩家的 Slimefun 统计数据喵~
        commands.add(new StatsCommand(plugin, cmd));
        // 注册 /sf timings：查看各组件的 tick 耗时性能数据喵~
        commands.add(new TimingsCommand(plugin, cmd));
        // 注册 /sf teleporter：管理 GPS 传送器功能喵~
        commands.add(new TeleporterCommand(plugin, cmd));
        // 注册 /sf open_guide：直接打开 Slimefun 指南界面喵~
        commands.add(new OpenGuideCommand(plugin, cmd));
        // 注册 /sf search：在 Slimefun 物品中按关键词搜索喵~
        commands.add(new SearchCommand(plugin, cmd));
        // 注册 /sf debug_fish：调试用途的特殊物品命令喵~
        commands.add(new DebugFishCommand(plugin, cmd));
        // 注册 /sf backpack：管理玩家背包数据喵~
        commands.add(new BackpackCommand(plugin, cmd));
        // 注册 /sf charge：为玩家充能或查看充能状态喵~
        commands.add(new ChargeCommand(plugin, cmd));
        // 注册 /sf debug：开启或关闭调试模式喵~
        commands.add(new DebugCommand(plugin, cmd));
        // 注册 /sf itemid：获取玩家手持物品的 Slimefun 唯一 ID 喵~
        commands.add(new ItemIdCommand(plugin, cmd));
        // 注册 /sf reload：热重载 Slimefun 配置文件喵~
        commands.add(new ReloadCommand(plugin, cmd));
        // 注册 /sf migrate：执行数据迁移操作喵~
        commands.add(new MigrateCommand(plugin, cmd));
        // 注册 /sf blockdata：查看或修改方块存储的数据喵~
        commands.add(new BlockDataCommand(plugin, cmd));
        // 注册 /sf banitem：禁用指定的 Slimefun 物品喵~
        commands.add(new BanItemCommand(plugin, cmd));
        // 注册 /sf unbanitem：解禁之前被禁用的 Slimefun 物品喵~
        commands.add(new UnbanItemCommand(plugin, cmd));
        // 注册 /sf cleardata：清除玩家或世界的 Slimefun 数据喵~
        commands.add(new ClearDataCommand(plugin, cmd));
        // 注册 /sf machinedamage：查看或调试机器损耗状态喵~
        commands.add(new MachineDamageCommand(plugin, cmd));
        // 注册 /sf grid：管理或查看物品网格信息喵~
        commands.add(new GridCommand(plugin, cmd));
        // 注册 /sf multimeter：使用多功能电表测量网络数据喵~
        commands.add(new MultimeterCommand(plugin, cmd));
        // 返回构建完毕的子命令集合，供命令分发器注册使用喵~
        return commands;
    }
}
