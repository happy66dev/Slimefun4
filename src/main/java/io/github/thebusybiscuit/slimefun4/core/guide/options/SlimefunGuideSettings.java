package io.github.thebusybiscuit.slimefun4.core.guide.options;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.services.LocalizationService;
import io.github.thebusybiscuit.slimefun4.core.services.github.GitHubService;
import io.github.thebusybiscuit.slimefun4.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * This static utility class offers various methods that provide access to the
 * Settings menu of our {@link SlimefunGuide}.
 *
 * This menu is used to allow a {@link Player} to change things such as the {@link Language}.
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunGuide
 *
 */
// 这是Slimefun指南设置菜单的静态工具类，提供玩家修改指南选项(如语言、烟花等)的入口喵~
public final class SlimefunGuideSettings {

    // 定义设置菜单中用于填充背景的槽位编号数组，这些槽位不放实际功能按钮而是用装饰性灰色玻璃填充喵~
    private static final int[] BACKGROUND_SLOTS = {
        1, 3, 5, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 26, 27, 35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 48,
        50, 52, 53
    };
    // 存储所有已注册的可配置选项列表，玩家可以在设置菜单中切换这些选项喵~
    private static final List<SlimefunGuideOption<?>> options = new ArrayList<>();

    // 静态初始化块：在类加载时自动注册四个内置选项到选项列表喵~
    static {
        // 添加指南模式切换选项(如生存模式/百科模式)喵~
        options.add(new GuideModeOption());
        // 添加解锁研究时是否播放烟花效果的选项喵~
        options.add(new FireworksOption());
        // 添加解锁研究时是否显示学习动画/进度消息的选项喵~
        options.add(new LearningAnimationOption());
        // 添加玩家语言切换选项喵~
        options.add(new PlayerLanguageOption());
    }

    // 私有构造器，防止外部实例化这个纯静态工具类喵~
    private SlimefunGuideSettings() {}

    /**
     * 向设置菜单注册一个自定义的可配置选项，第三方附加包可以调用此方法扩展设置菜单喵~
     *
     * @param option 要添加的选项对象，不能为null喵~
     */
    public static <T> void addOption(@Nonnull SlimefunGuideOption<T> option) {
        // 将新选项加入到选项列表末尾，下次打开设置菜单时会显示出来喵~
        options.add(option);
    }

    /**
     * 为玩家打开Slimefun指南的设置菜单喵~
     * 整体思路：创建一个箱子GUI，先绘制背景，再添加顶部导航栏按钮(返回/贡献者/版本等)，最后填入可配置选项喵~
     * 输入：要打开菜单的玩家p，以及他手持的指南物品guide喵~
     * 边界条件：p和guide均不能为null(由@ParametersAreNonnullByDefault保证)喵~
     */
    @ParametersAreNonnullByDefault
    public static void openSettings(Player p, ItemStack guide) {
        // 创建一个新的箱子菜单，标题从本地化服务中读取玩家对应语言的"guide.title.settings"文字喵~
        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(p, "guide.title.settings"));

        // 禁止玩家点击空槽位，防止把物品放进去或拿出来喵~
        menu.setEmptySlotsClickable(false);
        // 注册菜单打开时的音效处理器，打开设置界面时播放对应音效喵~
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_OPEN_SETTING_SOUND::playFor);

        // 用背景玻璃填充预定义的装饰槽位，让界面看起来更整洁喵~
        ChestMenuUtils.drawBackground(menu, BACKGROUND_SLOTS);

        // 添加顶部导航区的固定按钮(返回、贡献者、版本信息、源码、Wiki、附属插件等)喵~
        addHeader(p, menu, guide);
        // 添加玩家可配置的选项按钮(烟花、语言等)喵~
        addConfigurableOptions(p, menu, guide);

        // 打开菜单并展示给玩家喵~
        menu.open(p);
    }

    /**
     * 向设置菜单添加顶部固定的导航和信息按钮区域喵~
     * 整体思路：在菜单的0、2、4、6、8、47、49、51槽位依次放置返回键、贡献者、版本信息、源码、Wiki、附属插件、Bug追踪器、占位按钮喵~
     * 输入：玩家p、菜单对象menu、指南物品guide喵~
     */
    @ParametersAreNonnullByDefault
    private static void addHeader(Player p, ChestMenu menu, ItemStack guide) {
        // 获取本地化服务，用于读取玩家语言对应的UI文字喵~
        LocalizationService locale = Slimefun.getLocalization();

        // @formatter:off
        // 在槽位0放置"返回指南"按钮，显示指南物品图标和返回提示文字喵~
        menu.addItem(
                0,
                new CustomItemStack(
                        SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE),
                        "&e\u21E6 " + locale.getMessage(p, "guide.back.title"),
                        "",
                        "&7" + locale.getMessage(p, "guide.back.guide")));
        // @formatter:on

        // 注册槽位0的点击事件：点击后重新打开指南主界面并返回false阻止默认交互喵~
        menu.addMenuClickHandler(0, (pl, slot, item, action) -> {
            SlimefunGuide.openGuide(pl, guide);
            return false;
        });

        // 获取GitHub服务对象，用于读取贡献者数量、仓库统计等信息喵~
        GitHubService github = Slimefun.getGitHubService();

        // 创建贡献者按钮的描述文字列表，先加空行再加描述再加操作提示喵~
        List<String> contributorsLore = new ArrayList<>();
        contributorsLore.add("");
        // 从本地化消息中获取贡献者描述，并将%contributors%占位符替换为实际贡献者人数喵~
        contributorsLore.addAll(locale.getMessages(
                p,
                "guide.credits.description",
                msg -> msg.replace(
                        "%contributors%",
                        String.valueOf(github.getContributors().size()))));
        contributorsLore.add("");
        // 添加"点击查看"的操作提示文字喵~
        contributorsLore.add("&7\u21E8 &e" + locale.getMessage(p, "guide.credits.open"));

        // @formatter:off
        // 在槽位2放置"贡献者"按钮，使用自定义头颅贴图作为图标，展示贡献者数量喵~
        menu.addItem(
                2,
                new CustomItemStack(
                        SlimefunUtils.getCustomHead("e952d2b3f351a6b0487cc59db31bf5f2641133e5ba0006b18576e996a0293e52"),
                        "&c" + locale.getMessage(p, "guide.title.credits"),
                        contributorsLore.toArray(new String[0])));
        // @formatter:on

        // 注册槽位2的点击事件：点击后打开贡献者列表菜单(第0页)喵~
        menu.addMenuClickHandler(2, (pl, slot, action, item) -> {
            ContributorsMenu.open(pl, 0);
            return false;
        });

        // @formatter:off
        // 在槽位4放置"版本信息"按钮，展示汉化版信息、Minecraft版本和Slimefun版本喵~
        menu.addItem(
                4,
                new CustomItemStack(
                        Material.WRITABLE_BOOK,
                        ChatColor.GREEN + locale.getMessage(p, "guide.title.versions"),
                        "&7&o" + locale.getMessage(p, "guide.tooltips.versions-notice"),
                        "",
                        "&f汉化 By StarWishsama",
                        "&c请不要将此版本信息截图到 Discord/Github 反馈 Bug",
                        "&c而是优先到汉化页面反馈",
                        "",
                        "&cTHIS BUILD IS UNOFFICIAL BUILD, DO NOT REPORT TO SLIMEFUN DEV",
                        "",
                        "&fMinecraft: &a" + Bukkit.getBukkitVersion(),
                        "&fSlimefun: &a" + Slimefun.getVersion()),
                ChestMenuUtils.getEmptyClickHandler());
        // @formatter:on

        // @formatter:off
        // 在槽位6放置"源码/GitHub"按钮，展示仓库的最近活动时间、Fork数、Star数等统计信息喵~
        menu.addItem(
                6,
                new CustomItemStack(
                        Material.COMPARATOR,
                        "&e" + locale.getMessage(p, "guide.title.source"),
                        "",
                        "&7最近活动于: &a" + NumberUtils.getElapsedTime(github.getLastUpdate()) + " 前",
                        "&7Forks: &e" + github.getForks(),
                        "&7Stars: &e" + github.getStars(),
                        "",
                        "&7&oSlimefun 4 是一个由社区参与的项目,",
                        "&7&o源代码可以在 GitHub 上找到",
                        "&7&o如果你想让这个项目持续下去",
                        "&7&o你可以考虑对项目做出贡献",
                        "",
                        "&7\u21E8 &e点击前往汉化版 GitHub 仓库"));
        // @formatter:on

        // 注册槽位6的点击事件：关闭界面并向玩家发送汉化版GitHub仓库链接喵~
        menu.addMenuClickHandler(6, (pl, slot, item, action) -> {
            pl.closeInventory();
            ChatUtils.sendURL(pl, "https://github.com/SlimefunGuguProject/Slimefun4");
            return false;
        });

        // @formatter:off
        // 在槽位8放置"Wiki"按钮，引导玩家去查阅非官方中文Wiki喵~
        menu.addItem(
                8,
                new CustomItemStack(
                        Material.KNOWLEDGE_BOOK,
                        "&3" + locale.getMessage(p, "guide.title.wiki"),
                        "",
                        "&7你需要对物品或机器方面的帮助吗?",
                        "&7你不知道要干什么?",
                        "&7查看我们的由社区维护的维基",
                        "&7并考虑成为一名编辑者!",
                        "",
                        "&7\u21E8 &e点击前往非官方中文 Wiki"));
        // @formatter:on

        // 注册槽位8的点击事件：关闭界面并向玩家发送中文Wiki链接喵~
        menu.addMenuClickHandler(8, (pl, slot, item, action) -> {
            pl.closeInventory();
            ChatUtils.sendURL(pl, "https://slimefun-wiki.guizhanss.cn/");
            return false;
        });

        // @formatter:off
        // 在槽位47放置"附属插件"按钮，展示当前服务器已安装的附属插件数量并提供跳转链接喵~
        menu.addItem(
                47,
                new CustomItemStack(
                        Material.BOOKSHELF,
                        "&3" + locale.getMessage(p, "guide.title.addons"),
                        "",
                        "&7Slimefun 是一个大型项目，但附属插件的存在",
                        "&7能让 Slimefun 真正的发光发亮",
                        "&7看一看它们，也许你要寻找的附属插件就在那里!",
                        "",
                        "&7该服务器已安装附属插件: &b" + Slimefun.getInstalledAddons().size(),
                        "",
                        "&7\u21E8 &e点击查看 Slimefun4 可用的附属插件"));
        // @formatter:on

        // 注册槽位47的点击事件：关闭界面并向玩家发送附属插件Wiki页面链接喵~
        menu.addMenuClickHandler(47, (pl, slot, item, action) -> {
            pl.closeInventory();
            ChatUtils.sendURL(pl, "https://slimefun-wiki.guizhanss.cn/Addons");
            return false;
        });

        // 判断当前Slimefun是否为官方分支，官方分支才显示Bug追踪器按钮，非官方构建则显示背景占位喵~
        if (Slimefun.getUpdater().getBranch().isOfficial()) {
            // @formatter:off
            // 官方分支：在槽位49放置"Bug追踪器"按钮，展示当前未解决Issue数和等待中的PR数喵~
            menu.addItem(
                    49,
                    new CustomItemStack(
                            Material.REDSTONE_TORCH,
                            "&4" + locale.getMessage(p, "guide.title.bugs"),
                            "",
                            "&7&oBug reports have to be made in English!",
                            "",
                            "&7Open Issues: &a" + github.getOpenIssues(),
                            "&7Pending Pull Requests: &a" + github.getPendingPullRequests(),
                            "",
                            "&7\u21E8 &eClick to go to the Slimefun4 Bug Tracker"));
            // @formatter:on

            // 注册槽位49的点击事件：关闭界面并向玩家发送GitHub Issues页面链接喵~
            menu.addMenuClickHandler(49, (pl, slot, item, action) -> {
                pl.closeInventory();
                ChatUtils.sendURL(pl, "https://github.com/SlimefunGuguProject/Slimefun4/issues");
                return false;
            });
        } else {
            // 非官方分支：槽位49放背景占位，点击不响应，避免误导玩家去官方反馈喵~
            menu.addItem(49, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        // 在槽位51放置"开发中"占位按钮，当前点击无实际效果，是预留的扩展位喵~
        menu.addItem(
                51,
                new CustomItemStack(
                        Material.TOTEM_OF_UNDYING, ChatColor.RED + locale.getMessage(p, "guide.work-in-progress")),
                (pl, slot, item, action) -> {
                    // Add something here
                    return false;
                });
    }

    /**
     * 将所有已注册的可配置选项动态填充到设置菜单中喵~
     * 整体思路：从槽位19开始依次放置每个选项的图标，只显示getDisplayItem返回有值的选项喵~
     * 输入：玩家p、菜单对象menu、指南物品guide喵~
     * 边界条件：若某个选项的getDisplayItem返回空(不可见)，则跳过该选项不占用槽位喵~
     */
    @ParametersAreNonnullByDefault
    private static void addConfigurableOptions(Player p, ChestMenu menu, ItemStack guide) {
        // 从槽位19开始依次放置可配置选项，背景槽位已预留了中间区域喵~
        int i = 19;

        // 遍历所有已注册的选项，逐个检查是否对该玩家可见喵~
        for (SlimefunGuideOption<?> option : options) {
            // 获取该选项对当前玩家显示的图标，若选项不可见则返回空Optional喵~
            Optional<ItemStack> item = option.getDisplayItem(p, guide);

            // 喵~防御：只有选项确实有图标时才添加到菜单，避免放入空图标导致界面异常喵~
            if (item.isPresent()) {
                // 在当前槽位放置选项图标喵~
                menu.addItem(i, item.get());
                // 注册该槽位的点击事件：点击时调用选项的onClick方法执行切换逻辑喵~
                menu.addMenuClickHandler(i, (pl, slot, stack, action) -> {
                    option.onClick(p, guide); // 触发选项的切换/处理逻辑喵~
                    return false;
                });

                // 下一个选项放到下一个槽位喵~
                i++;
            }
        }
    }

    /**
     * This method checks if the given {@link Player} has enabled the {@link FireworksOption}
     * in their {@link SlimefunGuide}.
     * If they enabled this setting, they will see fireworks when they unlock a {@link Research}.
     *
     * @param p
     *            The {@link Player}
     *
     * @return Whether this {@link Player} wants to see fireworks when unlocking a {@link Research}
     */
    // 查询玩家是否开启了解锁研究时播放烟花效果的选项，默认开启喵~
    public static boolean hasFireworksEnabled(@Nonnull Player p) {
        // 调用通用取值方法，获取FireworksOption的当前值，玩家未设置时默认为true(开启)喵~
        return getOptionValue(p, FireworksOption.class, true);
    }

    /**
     * This method checks if the given {@link Player} has enabled the {@link LearningAnimationOption}
     * in their {@link SlimefunGuide}.
     * If they enabled this setting, they will see messages in chat about the progress of their {@link Research}.
     *
     * @param p
     *            The {@link Player}
     *
     * @return Whether this {@link Player} wants to info messages in chat when unlocking a {@link Research}
     */
    // 查询玩家是否开启了解锁研究时显示学习动画(聊天栏进度消息)的选项，默认开启喵~
    public static boolean hasLearningAnimationEnabled(@Nonnull Player p) {
        // 调用通用取值方法，获取LearningAnimationOption的当前值，玩家未设置时默认为true(开启)喵~
        return getOptionValue(p, LearningAnimationOption.class, true);
    }

    /**
     * Helper method to get the value of a {@link SlimefunGuideOption} that the {@link Player}
     * has set in their {@link SlimefunGuide}
     *
     * @param p
     *            The {@link Player}
     * @param optionsClass
     *            Class of the {@link SlimefunGuideOption} to get the value of
     * @param defaultValue
     *            Default value to return in case the option is not found at all or has no value set
     * @param <T>
     *            Type of the {@link SlimefunGuideOption}
     * @param <V>
     *            Type of the {@link SlimefunGuideOption} value
     *
     * @return The value of given {@link SlimefunGuideOption}
     */
    /**
     * 通用选项取值方法：在已注册的选项列表中找到指定类型的选项，并返回玩家为该选项设定的值喵~
     * 整体思路：遍历options列表，用Class.isInstance做类型匹配，找到后用orElse返回玩家设置或默认值喵~
     * 输入：玩家p、选项的Class对象optionsClass、找不到时的默认值defaultValue喵~
     * 边界条件：选项列表中不存在该类型时，直接返回defaultValue喵~
     */
    @Nonnull
    private static <T extends SlimefunGuideOption<V>, V> V getOptionValue(
            @Nonnull Player p, @Nonnull Class<T> optionsClass, @Nonnull V defaultValue) {
        // 遍历所有已注册的选项，寻找与目标类型匹配的那一个喵~
        for (SlimefunGuideOption<?> option : options) {
            // 用isInstance检查该选项是否是目标Class的实例(支持子类匹配)喵~
            if (optionsClass.isInstance(option)) {
                // 将选项强制转换为目标类型，以便调用其具体方法喵~
                T o = optionsClass.cast(option);
                // 获取生存模式下的指南物品，作为读取玩家选项设置的载体喵~
                ItemStack guide = SlimefunGuide.getItem(SlimefunGuideMode.SURVIVAL_MODE);
                // 读取玩家对该选项的设置值，若玩家未设置则回退到传入的默认值喵~
                return o.getSelectedOption(p, guide).orElse(defaultValue);
            }
        }

        // 喵~防御：选项列表中没有找到指定类型时返回默认值，避免返回null导致调用方空指针喵~
        return defaultValue;
    }
}
