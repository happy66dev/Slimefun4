package io.github.thebusybiscuit.slimefun4.implementation.items.androids;

import io.github.bakedlibs.dough.config.Config;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * A {@link Script} represents runnable code for a {@link ProgrammableAndroid}.
 *
 * @author TheBusyBiscuit
 *
 */
// 脚本类，代表可以被可编程机器人执行的一段代码程序喵~
public final class Script {

    // 存储脚本配置数据的Config对象，包含名称/代码/作者/评分等信息喵~
    private final Config config;
    // 脚本的显示名称，用于在GUI界面展示给玩家看喵~
    private final String name;
    // 脚本作者的游戏名，用于展示是谁上传了这个脚本喵~
    private final String author;
    // 脚本的实际代码内容，是一串描述机器人指令执行顺序的字符串喵~
    private final String code;

    /**
     * This constructs a new {@link Script} from the given {@link Config}.
     *
     * @param config
     *            The {@link Config}
     */
    /*
     * 从配置文件构造脚本对象的构造方法喵~
     * 整体思路：读取配置文件中的各个字段，通过UUID找到作者的游戏名，完成对象初始化喵~
     * 输入：一个包含脚本数据的Config配置文件对象喵~
     * 边界条件：name/code/uuid/评分列表任意一个为null都会抛出异常，
     *           且uuid必须是合法的UUID格式否则UUID.fromString会报错喵~
     */
    private Script(@Nonnull Config config) {
        // 喵~防御：config为null时立即抛出异常，避免后续操作空指针崩溃喵
        Validate.notNull(config);

        // 将传入的config保存到成员变量，后续评分/下载等操作都需要用到喵~
        this.config = config;
        // 从配置文件读取脚本名称喵~
        this.name = config.getString("name");
        // 从配置文件读取脚本代码内容喵~
        this.code = config.getString("code");
        // 从配置文件读取作者的UUID字符串，用于后续查找玩家游戏名喵~
        String uuid = config.getString("author");

        // 喵~防御：name为null说明配置文件缺少name字段，脚本数据不完整，立即报错喵
        Validate.notNull(name);
        // 喵~防御：code为null说明配置文件缺少code字段，没有可执行代码，立即报错喵
        Validate.notNull(code);
        // 喵~防御：uuid为null说明配置文件缺少author字段，无法确认作者，立即报错喵
        Validate.notNull(uuid);
        // 喵~防御：确保好评列表字段存在，避免后续读取评分时空指针喵
        Validate.notNull(config.getStringList("rating.positive"));
        // 喵~防御：确保差评列表字段存在，避免后续读取评分时空指针喵
        Validate.notNull(config.getStringList("rating.negative"));

        // 根据UUID字符串获取离线玩家对象，用于取得作者的游戏名喵~
        OfflinePlayer player = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
        // 优先使用玩家当前游戏名，如果玩家从未登录过服务器则回退到配置中存储的备用名喵~
        this.author = player.getName() != null ? player.getName() : config.getString("author_name");
    }

    /**
     * This returns the name of this {@link Script}.
     *
     * @return The name
     */
    // 返回脚本的显示名称喵~
    @Nonnull
    public String getName() {
        return name;
    }

    /**
     * This returns the author of this {@link Script}.
     * The author is the person who initially created and uploaded this {@link Script}.
     *
     * @return The author of this {@link Script}
     */
    // 返回脚本作者的游戏名，即最初上传这个脚本的玩家喵~
    @Nonnull
    public String getAuthor() {
        return author;
    }

    /**
     * This method returns the actual code of this {@link Script}.
     * It is basically a {@link String} describing the order of {@link Instruction Instructions} that
     * shall be executed.
     *
     * @return The code for this {@link Script}
     */
    // 返回脚本的实际代码内容，是机器人执行指令的序列字符串喵~
    @Nonnull
    public String getSourceCode() {
        return code;
    }

    /**
     * This method determines whether the given {@link OfflinePlayer} is the author of
     * this {@link Script}.
     *
     * @param p
     *            The {@link OfflinePlayer} to check for
     *
     * @return Whether the given {@link OfflinePlayer} is the author of this {@link Script}.
     */
    // 判断指定玩家是否是这个脚本的作者，通过比对UUID来确认身份喵~
    public boolean isAuthor(@Nonnull OfflinePlayer p) {
        // 将玩家的UUID与配置中存储的author UUID进行比对，相同则说明是作者喵~
        return p.getUniqueId().equals(config.getUUID("author"));
    }

    /**
     * This method checks whether a given {@link Player} is able to leave a rating for this {@link Script}.
     * A {@link Player} is unable to rate his own {@link Script} or a {@link Script} he already rated before.
     *
     * @param p
     *            The {@link Player} to check for
     *
     * @return Whether the given {@link Player} is able to rate this {@link Script}
     */
    /*
     * 检查玩家是否有资格对这个脚本进行评分喵~
     * 整体思路：作者不能给自己的脚本评分，已经评过分的玩家也不能重复评喵~
     * 输入：在线玩家对象喵~
     * 输出：true=可以评分，false=不可以评分喵~
     */
    public boolean canRate(@Nonnull Player p) {
        // 喵~防御：如果是脚本作者本人，直接返回false禁止自评，防止刷好评喵
        if (isAuthor(p)) {
            return false;
        }

        // 读取已经好评过的玩家UUID列表喵~
        List<String> upvoters = config.getStringList("rating.positive");
        // 读取已经差评过的玩家UUID列表喵~
        List<String> downvoters = config.getStringList("rating.negative");
        // 只有玩家既没有好评也没有差评过，才能继续评分喵~
        return !upvoters.contains(p.getUniqueId().toString())
                && !downvoters.contains(p.getUniqueId().toString());
    }

    /*
     * 将脚本转换为GUI中显示的ItemStack物品形式喵~
     * 整体思路：构建显示lore（包含作者/下载量/评分/操作提示），
     *           然后以机器人物品为模板生成一个带有脚本信息的CustomItemStack喵~
     * 输入：机器人物品对象、查看的玩家喵~
     * 输出：带有脚本信息lore的ItemStack，用于在脚本列表GUI中展示喵~
     */
    @Nonnull
    ItemStack getAsItemStack(@Nonnull ProgrammableAndroid android, @Nonnull Player p) {
        // 创建lore列表，用于存放物品悬浮文字的每一行内容喵~
        List<String> lore = new LinkedList<>();
        // 添加作者名称显示行喵~
        lore.add("&7作者 &f" + getAuthor());
        // 添加空行作为分隔喵~
        lore.add("");
        // 添加下载量显示行喵~
        lore.add("&7下载量: &f" + getDownloads());
        // 添加评分百分比显示行，颜色根据评分高低动态变化喵~
        lore.add("&7评分: " + getScriptRatingPercentage());
        // 添加好评数和差评数的并排显示行，笑脸代表好评，哭脸代表差评喵~
        lore.add("&a" + getUpvotes() + " ☺ &7| &4☹ " + getDownvotes());
        // 添加空行分隔操作提示喵~
        lore.add("");
        // 添加左键下载的操作提示喵~
        lore.add("&e左键 &f下载脚本");
        // 添加覆盖警告，提醒玩家下载会替换现有脚本喵~
        lore.add("&4(将会覆盖你现有的脚本!)");

        // 只有有评分资格的玩家才显示评分操作提示喵~
        if (canRate(p)) {
            // 添加空行分隔喵~
            lore.add("");
            // 添加Shift+左键好评的操作提示喵~
            lore.add("&eShift + 左键 &f好评");
            // 添加Shift+右键差评的操作提示喵~
            lore.add("&eShift + 右键 &f差评");
        }

        // 以机器人的物品外观为模板，生成带有脚本名称(青色)和lore的物品，用于GUI展示喵~
        return new CustomItemStack(android.getItem(), "&b" + getName(), lore.toArray(new String[0]));
    }

    /*
     * 获取格式化后的评分百分比字符串，带颜色代码喵~
     * 整体思路：先计算评分浮点数，再根据分值选择对应颜色，
     *           最后拼接成"颜色代码+百分比数字+%"的格式喵~
     */
    @Nonnull
    private String getScriptRatingPercentage() {
        // 计算脚本的好评率浮点数喵~
        float percentage = getRating();
        // 根据百分比数值选取对应颜色，拼成带颜色的百分比字符串喵~
        return NumberUtils.getColorFromPercentage(percentage) + String.valueOf(percentage) + ChatColor.WHITE + "% ";
    }

    /**
     * This method returns the amount of upvotes this {@link Script} has received.
     *
     * @return The amount of upvotes
     */
    // 返回这个脚本收到的好评数量，通过读取配置中好评UUID列表的长度来统计喵~
    public int getUpvotes() {
        return config.getStringList("rating.positive").size();
    }

    /**
     * This method returns the amount of downvotes this {@link Script} has received.
     *
     * @return The amount of downvotes
     */
    // 返回这个脚本收到的差评数量，通过读取配置中差评UUID列表的长度来统计喵~
    public int getDownvotes() {
        return config.getStringList("rating.negative").size();
    }

    /**
     * This returns how often this {@link Script} has been downloaded.
     *
     * @return The amount of downloads for this {@link Script}.
     */
    // 返回这个脚本被下载的总次数，从配置文件中读取downloads字段喵~
    public int getDownloads() {
        return config.getInt("downloads");
    }

    /**
     * This returns the "rating" of this {@link Script}.
     * This value is calculated from the up- and downvotes this {@link Script} received.
     *
     * @return The rating for this {@link Script}
     */
    /*
     * 计算脚本的综合好评率（0到1之间的浮点数）喵~
     * 整体思路：好评数+1作为分子(加1是为了避免全0时除零问题)，
     *           好评+差评+1作为分母，计算百分比后四舍五入保留两位小数喵~
     * 边界条件：全为0票时 positive=1, negative=0, 结果=100%（新脚本默认满分）喵~
     */
    public float getRating() {
        // 好评数+1，加1是为了让新脚本从满分开始（也防止分母为0导致除零错误）喵~
        int positive = getUpvotes() + 1;
        // 差评数，直接读取喵~
        int negative = getDownvotes();
        // 计算好评率：好评/(好评+差评)，结果乘100转为百分比，用Math.round保留两位小数喵~
        return Math.round((positive / (float) (positive + negative)) * 100.0F) / 100.0F;
    }

    /**
     * This method increases the amount of downloads by one.
     */
    /*
     * 将脚本下载次数加一并保存到文件喵~
     * 整体思路：先reload避免使用过时数据，然后写入新的下载量，最后保存到磁盘喵~
     */
    public void download() {
        // 先重新加载配置，防止与其他操作并发时读到过时的下载数喵~
        config.reload();
        // 将当前下载次数+1写入配置喵~
        config.setValue("downloads", getDownloads() + 1);
        // 将更新后的配置保存到磁盘文件喵~
        config.save();
    }

    /*
     * 为脚本添加一条好评或差评，并保存到文件喵~
     * 整体思路：先reload避免数据竞争，根据positive参数决定写入好评还是差评列表，
     *           将玩家UUID追加到对应列表后保存喵~
     * 输入：玩家对象（取其UUID），positive=true表示好评/false表示差评喵~
     */
    public void rate(@Nonnull Player p, boolean positive) {
        // 先重新加载配置，防止与其他操作并发时覆盖掉别人的评分喵~
        config.reload();

        // 根据positive参数构造配置路径：好评写rating.positive，差评写rating.negative喵~
        String path = "rating." + (positive ? "positive" : "negative");
        // 读取当前的评分UUID列表喵~
        List<String> list = config.getStringList(path);
        // 将当前玩家的UUID字符串追加到评分列表喵~
        list.add(p.getUniqueId().toString());

        // 将更新后的列表写回配置喵~
        config.setValue(path, list);
        // 保存配置到磁盘文件喵~
        config.save();
    }

    /*
     * 获取指定机器人类型的所有已上传脚本列表，按评分排序喵~
     * 整体思路：先加载当前类型的脚本，再加载通用类型(NONE)的脚本，
     *           最后按照(差评-好评)升序排列，好评多的排在前面喵~
     * 输入：AndroidType枚举，指定要查询哪种机器人类型的脚本喵~
     * 输出：按评分降序排列的Script列表喵~
     */
    @Nonnull
    public static List<Script> getUploadedScripts(@Nonnull AndroidType androidType) {
        // 创建存放脚本的列表喵~
        List<Script> scripts = new LinkedList<>();

        // 加载指定机器人类型的脚本喵~
        loadScripts(scripts, androidType);

        // 如果不是通用类型，还要额外加载通用类型(NONE)的脚本，因为通用脚本对所有机器人都适用喵~
        if (androidType != AndroidType.NONE) {
            loadScripts(scripts, AndroidType.NONE);
        }

        // 按照综合评分降序排序：(-好评+1-差评)越小说明好评越多，排在越前喵~
        // 主人注意：当脚本数量极大时排序时间复杂度为O(n log n)，通常情况下无性能问题喵~
        Collections.sort(scripts, Comparator.comparingInt(script -> -script.getUpvotes() + 1 - script.getDownvotes()));
        return scripts;
    }

    /*
     * 从磁盘加载指定类型目录下的所有脚本文件到scripts列表喵~
     * 整体思路：找到对应类型的目录，遍历所有.sfs文件，
     *           跳过数据不完整的文件，捕获异常防止单个损坏文件影响全部加载喵~
     * 输入：脚本列表（直接修改，追加结果），AndroidType类型名作为子目录名喵~
     * 边界条件：目录不存在时自动创建；文件缺少code或author字段时跳过；解析异常时只记录日志不中断喵~
     */
    private static void loadScripts(@Nonnull List<Script> scripts, @Nonnull AndroidType type) {
        // 构造脚本存储目录路径，不同类型的机器人脚本分开存放喵~
        File directory = new File("plugins/Slimefun/scripts/" + type.name());
        // 喵~防御：目录不存在时自动创建，防止listFiles()返回null导致空指针崩溃喵
        if (!directory.exists()) {
            directory.mkdirs();
        }

        // 遍历目录下所有文件，逐个尝试加载为Script对象喵~
        // 主人注意：如果scripts目录下文件数量极多(>10000)，listFiles()返回的数组较大，遍历耗时增加喵~
        for (File file : directory.listFiles()) {
            // 只处理扩展名为.sfs(Slimefun Script)的脚本文件，过滤掉其他文件喵~
            if (file.getName().endsWith(".sfs")) {
                try {
                    // 读取脚本配置文件喵~
                    Config config = new Config(file);

                    // Some older versions somehow allowed null values to slip in here sometimes
                    // So we need this check for compatibility with older scripts
                    // 喵~防御：兼容旧版本可能缺少code或author字段的脚本文件，不完整的直接跳过喵
                    if (config.contains("code") && config.contains("author")) {
                        // 数据完整才添加到脚本列表喵~
                        scripts.add(new Script(config));
                    }
                } catch (Exception x) {
                    // 喵~防御：捕获单个脚本文件加载时的所有异常，只记录日志，不影响其他脚本继续加载喵
                    Slimefun.logger()
                            .log(
                                    Level.SEVERE,
                                    x,
                                    () -> "An Exception occurred while trying to load Android Script '"
                                            + file.getName()
                                            + "'");
                }
            }
        }
    }

    /*
     * 将玩家编写的脚本上传（保存）到磁盘喵~
     * 整体思路：根据机器人类型、玩家名和脚本ID构造唯一文件名，
     *           将作者UUID/名称/脚本名/代码/下载量/评分等信息写入.sfs配置文件并保存喵~
     * 输入：上传的玩家、机器人类型、脚本编号、脚本名称、脚本代码内容喵~
     * 边界条件：参数均标注@ParametersAreNonnullByDefault，不允许null传入喵~
     */
    @ParametersAreNonnullByDefault
    public static void upload(Player p, AndroidType androidType, int id, String name, String code) {
        // 构造脚本的配置文件路径，文件名由玩家名+编号组成，保证唯一性喵~
        Config config =
                new Config("plugins/Slimefun/scripts/" + androidType.name() + '/' + p.getName() + ' ' + id + ".sfs");

        // 存储作者UUID，用于后续身份验证（isAuthor等方法）喵~
        config.setValue("author", p.getUniqueId().toString());
        // 存储作者游戏名作为备用（当玩家UUID查不到名称时使用）喵~
        config.setValue("author_name", p.getName());
        // 存储脚本名称，去除颜色代码防止乱码或注入问题喵~
        config.setValue("name", ChatUtils.removeColorCodes(name));
        // 存储脚本代码内容喵~
        config.setValue("code", code);
        // 初始化下载量为0，新脚本刚上传时还没有人下载喵~
        config.setValue("downloads", 0);
        // 存储此脚本适用的机器人类型喵~
        config.setValue("android", androidType.name());
        // 初始化好评列表为空，新脚本还没有人评分喵~
        config.setValue("rating.positive", new ArrayList<String>());
        // 初始化差评列表为空，新脚本还没有人评分喵~
        config.setValue("rating.negative", new ArrayList<String>());
        // 将所有配置写入磁盘文件，完成上传喵~
        config.save();
    }
}
