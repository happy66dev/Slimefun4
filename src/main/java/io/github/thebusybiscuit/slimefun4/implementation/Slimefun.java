// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 happy <k666kkk666k@163.com>
// Based on Slimefun4 by TheBusyBiscuit and contributors, licensed under GPL-3.0
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program.  If not, see <https://www.gnu.org/licenses/>.
package io.github.thebusybiscuit.slimefun4.implementation;

import city.norain.slimefun4.ServerVersion;
import city.norain.slimefun4.SlimefunExtended;
import city.norain.slimefun4.timings.SQLProfiler;
import com.xzavier0722.mc.plugin.slimefun4.chat.PlayerChatCatcher;
import com.xzavier0722.mc.plugin.slimefun4.storage.migrator.BlockStorageMigrator;
import com.xzavier0722.mc.plugin.slimefun4.storage.migrator.PlayerProfileMigrator;
import com.xzavier0722.mc.plugin.slimefuncomplib.ICompatibleSlimefun;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import io.github.bakedlibs.dough.config.Config;
import io.github.bakedlibs.dough.protection.ProtectionManager;
import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.exceptions.TagMisconfigurationException;
import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import io.github.thebusybiscuit.slimefun4.api.gps.GPSNetwork;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.SlimefunRegistry;
import io.github.thebusybiscuit.slimefun4.core.commands.SlimefunCommand;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunConfigManager;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunDatabaseManager;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunMachineDamageManager;
import io.github.thebusybiscuit.slimefun4.core.networks.NetworkManager;
import io.github.thebusybiscuit.slimefun4.core.services.AnalyticsService;
import io.github.thebusybiscuit.slimefun4.core.services.AutoSavingService;
import io.github.thebusybiscuit.slimefun4.core.services.BackupService;
import io.github.thebusybiscuit.slimefun4.core.services.BlockDataService;
import io.github.thebusybiscuit.slimefun4.core.services.CustomItemDataService;
import io.github.thebusybiscuit.slimefun4.core.services.CustomTextureService;
import io.github.thebusybiscuit.slimefun4.core.services.ItemStackService;
import io.github.thebusybiscuit.slimefun4.core.services.LocalizationService;
import io.github.thebusybiscuit.slimefun4.core.services.MachineDamageService;
import io.github.thebusybiscuit.slimefun4.core.services.MachineFeedbackService;
import io.github.thebusybiscuit.slimefun4.core.services.MetricsService;
import io.github.thebusybiscuit.slimefun4.core.services.MinecraftRecipeService;
import io.github.thebusybiscuit.slimefun4.core.services.PerWorldSettingsService;
import io.github.thebusybiscuit.slimefun4.core.services.PermissionsService;
import io.github.thebusybiscuit.slimefun4.core.services.ThreadService;
import io.github.thebusybiscuit.slimefun4.core.services.UpdaterService;
import io.github.thebusybiscuit.slimefun4.core.services.github.GitHubService;
import io.github.thebusybiscuit.slimefun4.core.services.holograms.HologramsService;
import io.github.thebusybiscuit.slimefun4.core.services.profiler.SlimefunProfiler;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundService;
import io.github.thebusybiscuit.slimefun4.implementation.items.altar.AncientAltar;
import io.github.thebusybiscuit.slimefun4.implementation.items.altar.AncientPedestal;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.Cooler;
import io.github.thebusybiscuit.slimefun4.implementation.items.magical.BeeWings;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.GrapplingHook;
import io.github.thebusybiscuit.slimefun4.implementation.items.weapons.SeismicAxe;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.AncientAltarListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.AutoCrafterListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BackpackListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BeeWingsListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BlockListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.BlockPhysicsListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.ButcherAndroidListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.CargoNodeListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.CoolerListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.DeathpointListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.DebugFishListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.DispenserListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.ElytraImpactListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.EnhancedFurnaceListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.ExplosionsListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.GadgetsListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.GrapplingHookListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.HopperListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.ItemDropListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.ItemPickupListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.JoinListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.MachineDamageListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.MachineDamageNotificationListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.MiddleClickListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.MiningAndroidListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.MultiBlockListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.NetworkListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.PlayerProfileListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.RadioactivityListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SeismicAxeListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunBootsListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunBowListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunGuideListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunItemConsumeListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunItemHitListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SlimefunItemInteractListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.SoulboundListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.TalismanListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.VersionedMiddleClickListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.VillagerTradingListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.AnvilListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.BrewingStandListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.CartographyTableListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.CauldronListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.CraftingTableListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.GrindstoneListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.SmithingTableListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.crafting.VanillaCrafterListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.BeeListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.EntityInteractionListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.FireworksListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.IronGolemListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.MobDropListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.PiglinListener;
import io.github.thebusybiscuit.slimefun4.implementation.listeners.entity.WitherListener;
import io.github.thebusybiscuit.slimefun4.implementation.resources.GEOResourcesSetup;
import io.github.thebusybiscuit.slimefun4.implementation.setup.PostSetup;
import io.github.thebusybiscuit.slimefun4.implementation.setup.ResearchSetup;
import io.github.thebusybiscuit.slimefun4.implementation.setup.SlimefunItemSetup;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.SlimefunStartupTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.TickerTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.armor.RadiationTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.armor.RainbowArmorTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.armor.SlimefunArmorTask;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.armor.SolarHelmetTask;
import io.github.thebusybiscuit.slimefun4.integrations.IntegrationsManager;
import io.github.thebusybiscuit.slimefun4.utils.MachineStatePersistence;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import io.github.thebusybiscuit.slimefun4.utils.tags.SlimefunTag;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.MenuListener;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Recipe;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.java.JavaPluginLoader;
import org.bukkit.scheduler.BukkitTask;

/**
 * This is the main class of Slimefun.
 * This is where all the magic starts, take a look around.
 *
 * @author TheBusyBiscuit
 */
public final class Slimefun extends JavaPlugin implements SlimefunAddon, ICompatibleSlimefun {

    /**
     * This is the Java version we recommend server owners to use.
     * This does not necessarily mean that it's the minimum version
     * required to run Slimefun.
     */
    // 推荐服务器管理员使用的最低Java版本号，低于此版本会在启动时打印警告喵~
    private static final int RECOMMENDED_JAVA_VERSION = 17;

    /**
     * Our static instance of {@link Slimefun}.
     * Make sure to clean this up in {@link #onDisable()}!
     */
    // Slimefun插件的全局静态单例引用，用于在任何地方通过静态方法访问插件功能喵~
    private static Slimefun instance;

    /**
     * This is the instance of {@link AuraSkillsApi}
     */
    // AuraSkills技能系统API的静态引用，用于与技能插件进行交互喵~
    private static AuraSkillsApi auraSkills;

    /**
     * Keep track of which {@link MinecraftVersion} we are on.
     */
    // 当前服务端的Minecraft版本枚举值，默认为UNKNOWN等待启动时检测赋值喵~
    private MinecraftVersion minecraftVersion = MinecraftVersion.UNKNOWN;

    /**
     * Keep track of whether this is a fresh install or a regular boot up.
     */
    // 标记本次是否是全新安装（data-storage目录不存在），用于统计新安装次数喵~
    private boolean isNewlyInstalled = false;

    // Various things we need
    // 配置文件管理器，负责加载和管理所有config.yml等配置文件喵~
    private final SlimefunConfigManager cfgManager = new SlimefunConfigManager(this);
    // 数据库管理器，负责初始化和关闭玩家档案与方块数据的持久化存储喵~
    private final SlimefunDatabaseManager databaseManager = new SlimefunDatabaseManager(this);
    // 注册表，存储所有已注册的Slimefun物品、物品组、研究等全局数据喵~
    private final SlimefunRegistry registry = new SlimefunRegistry();
    // /sf命令处理器，负责注册并响应玩家输入的slimefun命令喵~
    private final SlimefunCommand command = new SlimefunCommand(this);
    // 方块定时器任务，每tick遍历所有有BlockTicker的Slimefun方块并触发其逻辑喵~
    private final TickerTask ticker = new TickerTask();
    // 聊天输入捕获器，用于拦截玩家聊天消息实现GUI输入框功能喵~
    private PlayerChatCatcher chatCatcher;

    // Services - Systems that fulfill certain tasks, treat them as a black box
    // 物品NBT数据服务，负责向ItemStack写入和读取Slimefun物品ID标记喵~
    private final CustomItemDataService itemDataService = new CustomItemDataService(this, "slimefun_item");
    // 方块NBT数据服务，负责向方块写入和读取Slimefun方块ID等自定义数据喵~
    private final BlockDataService blockDataService = new BlockDataService(this, "slimefun_block");
    // 自定义贴图服务，从item-models.yml读取配置并为物品注册自定义模型数据喵~
    private final CustomTextureService textureService = new CustomTextureService(new Config(this, "item-models.yml"));
    // GitHub服务，用于从Slimefun4的GitHub仓库获取贡献者信息和更新数据喵~
    private final GitHubService gitHubService = new GitHubService("SlimefunGuguProject/Slimefun4");
    // 自动更新服务，检测并下载Slimefun的最新版本喵~
    private final UpdaterService updaterService =
            new UpdaterService(this, getDescription().getVersion(), getFile());
    // bStats统计服务，向统计平台上报服务器使用数据喵~
    private final MetricsService metricsService = new MetricsService(this);
    // 自动保存服务，定期将内存中的玩家档案写入数据库避免数据丢失喵~
    private final AutoSavingService autoSavingService = new AutoSavingService();
    // 备份服务，关服时将data-storage数据打包为zip备份文件喵~
    private final BackupService backupService = new BackupService();
    // 权限服务，管理每个Slimefun物品的自定义权限节点配置喵~
    private final PermissionsService permissionsService = new PermissionsService(this);
    // 按世界设置服务，管理在不同World中禁用或启用特定Slimefun物品喵~
    private final PerWorldSettingsService worldSettingsService = new PerWorldSettingsService(this);
    // 原版配方服务，用于查询原版熔炉输出和合成台配方信息喵~
    private final MinecraftRecipeService recipeService = new MinecraftRecipeService(this);
    // 全息文字服务，负责在Slimefun方块上方创建和刷新悬浮文字喵~
    private final HologramsService hologramsService = new HologramsService(this);
    // 音效服务，管理Slimefun各功能使用的音效配置，支持自定义喵~
    private final SoundService soundService = new SoundService(this);
    // 线程服务，为Slimefun提供专用的异步线程池管理喵~
    private final ThreadService threadService = new ThreadService(this);
    // 数据分析服务，收集并上报插件使用情况的分析数据喵~
    private final AnalyticsService analyticsService = new AnalyticsService(this);
    // 物品栈比较和操作服务，提供物品匹配、放入背包等高级功能喵~
    private final ItemStackService itemStackService = new ItemStackService();

    // Some other things we need
    // 第三方插件集成管理器，负责与WorldGuard/Vault/AuraSkills等插件对接喵~
    private final IntegrationsManager integrations = new IntegrationsManager(this);
    // 性能分析器，记录每个Slimefun方块tick耗时并提供性能报告喵~
    private final SlimefunProfiler profiler = new SlimefunProfiler();
    // SQL性能分析器，专门记录数据库查询的慢查询情况喵~
    private final SQLProfiler sqlProfiler = new SQLProfiler();
    // GPS网络管理器，追踪玩家的GPS发射器、路径点与传送功能喵~
    private final GPSNetwork gpsNetwork = new GPSNetwork(this);

    // Even more things we need
    // 网络管理器，统一管理所有能量网络和货运网络的注册与更新，延迟初始化喵~
    private NetworkManager networkManager;
    // 本地化服务，管理多语言文件加载与玩家语言偏好，延迟初始化喵~
    private LocalizationService local;

    // Important config files for Slimefun
    // Items.yml配置文件，存储所有Slimefun物品的启用/禁用等配置喵~
    private final Config items = new Config(this, "Items.yml");
    // Researches.yml配置文件，存储所有研究的费用和启用状态配置喵~
    private final Config researches = new Config(this, "Researches.yml");
    // 机器损坏配置管理器，从配置文件加载机器损坏相关参数，延迟初始化喵~
    private SlimefunMachineDamageManager machineDamageManager;

    // Listeners that need to be accessed elsewhere
    // 飞钩监听器，需要被其他地方引用所以单独持有引用喵~
    private final GrapplingHookListener grapplingHookListener = new GrapplingHookListener();
    // 背包监听器，需要被其他地方引用所以单独持有引用喵~
    private final BackpackListener backpackListener = new BackpackListener();
    // 弓箭监听器，需要被其他地方引用所以单独持有引用喵~
    private final SlimefunBowListener bowListener = new SlimefunBowListener();
    // 机器损坏逻辑服务，处理机器运行时的随机损坏、修复和报废逻辑，延迟初始化喵~
    private MachineDamageService machineDamageService;
    // 机器工作反馈服务，管理机器运行时的粒子和音效效果，延迟初始化喵~
    private MachineFeedbackService machineFeedbackService;

    /**
     * Our default constructor for {@link Slimefun}.
     */
    // 默认无参构造函数，由Bukkit框架在正常服务器环境下调用喵~
    public Slimefun() {
        super();
    }

    /**
     * This constructor is invoked in Unit Test environments only.
     *
     * @param loader
     *            Our {@link JavaPluginLoader}
     * @param description
     *            A {@link PluginDescriptionFile}
     * @param dataFolder
     *            The data folder
     * @param file
     *            A {@link File} for this {@link Plugin}
     */
    @ParametersAreNonnullByDefault
    // 单元测试专用构造函数，通过JavaPluginLoader模拟插件加载环境喵~
    public Slimefun(JavaPluginLoader loader, PluginDescriptionFile description, File dataFolder, File file) {
        // 调用父类构造函数，传入测试用的加载器和描述信息喵~
        super(loader, description, dataFolder, file);

        // This is only invoked during a Unit Test
        // 将版本标记为单元测试模式，让后续逻辑跳过真实服务器初始化喵~
        minecraftVersion = MinecraftVersion.UNIT_TEST;
    }

    /**
     * This is called when the {@link Plugin} has been loaded and enabled on a {@link Server}.
     */
    @Override
    public void onEnable() {
        // 将当前插件实例设为全局单例，让静态方法可以访问喵~
        setInstance(this);
        // 获取AuraSkills技能API实例，后续技能联动功能需要用到喵~
        auraSkills = AuraSkillsApi.get();

        if (isUnitTest()) {
            // We handle Unit Tests seperately.
            // 单元测试环境下走独立的启动流程，跳过真实服务器相关逻辑喵~
            onUnitTestStart();
        } else if (isVersionUnsupported()) {
            // We wanna ensure that the Server uses a compatible version of Minecraft.
            // 检测到不支持的Minecraft版本时，立即禁用插件避免运行出错喵~
            getServer().getPluginManager().disablePlugin(this);
        } else if (!SlimefunExtended.checkEnvironment(this)) {
            // We want to ensure that the Server uses a compatible server software and have no
            // incompatible plugins
            // 运行环境检查未通过（如不兼容的服务端软件或冲突插件），禁用插件喵~
            getServer().getPluginManager().disablePlugin(this);
        } else {
            // The Environment has been validated.
            // 所有环境检查通过，正式启动插件的完整初始化流程喵~
            onPluginStart();
        }
    }

    /**
     * This is our start method for a Unit Test environment.
     */
    private void onUnitTestStart() {
        // 初始化本地化服务，单元测试无需真实语言文件所以传空字符串和null喵~
        local = new LocalizationService(this, "", null);
        // 创建网络管理器，单元测试中最大节点数设为200喵~
        networkManager = new NetworkManager(200);
        // 注册 /sf 命令到服务器喵~
        command.register();
        // 加载插件配置文件到内存缓存喵~
        cfgManager.load();
        // 初始化全局注册表，加载已注册的Slimefun物品喵~
        registry.load(this);
        // 加载所有自定义Tag标签定义喵~
        loadTags();
        // 重新加载音效配置，false表示不发送加载完成消息给玩家喵~
        soundService.reload(false);
    }

    /**
     * This is our start method for a correct Slimefun installation.
     */
    private void onPluginStart() {
        // 记录插件启动开始时间，用于最后计算总耗时喵~
        long timestamp = System.nanoTime();
        // 获取本插件的日志记录器，用于输出控制台信息喵~
        Logger logger = getLogger();

        // 喵~防御：检查CS-CoreLib是否存在，该库已废弃不再需要，若存在则阻止启动防止冲突喵~
        if (getServer().getPluginManager().getPlugin("CS-CoreLib") != null) {
            // 打印CS-CoreLib已弃用的警告信息喵~
            StartupWarnings.discourageCSCoreLib(logger);
            // 强制禁用本插件，因为环境不兼容喵~
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 喵~防御：检查Vault插件是否安装，Vault是经济系统的必要依赖，缺失则无法运行喵~
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            logger.log(Level.SEVERE, "==============================================");
            logger.log(Level.SEVERE, "Vault 未安装! Slimefun 需要 Vault 才能运行。");
            logger.log(Level.SEVERE, "请安装 Vault 插件: https://www.spigotmc.org/resources/vault.34315/");
            logger.log(Level.SEVERE, "==============================================");
            // Vault缺失时禁用插件喵~
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 喵~防御：检查AuraSkills插件是否安装，AuraSkills是技能系统的必要依赖喵~
        if (getServer().getPluginManager().getPlugin("AuraSkills") == null) {
            logger.log(Level.SEVERE, "==============================================");
            logger.log(Level.SEVERE, "AuraSkills 未安装! Slimefun 需要 AuraSkills 才能运行。");
            logger.log(Level.SEVERE, "请安装 AuraSkills 插件: https://www.spigotmc.org/resources/auraskills.81069/");
            logger.log(Level.SEVERE, "==============================================");
            // AuraSkills缺失时禁用插件喵~
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // 检查当前Java版本是否低于推荐版本，低于则打印升级建议喵~
        if (NumberUtils.getJavaVersion() < RECOMMENDED_JAVA_VERSION) {
            // 提示服主升级Java版本以获得更好的性能和兼容性喵~
            StartupWarnings.oldJavaVersion(logger, RECOMMENDED_JAVA_VERSION);
        }

        // 通过data-storage/Slimefun目录是否存在来判断是否为首次安装，首次安装时标记以便统计喵~
        isNewlyInstalled = !new File("data-storage/Slimefun").exists();

        // 创建Slimefun运行所需的各种目录结构喵~
        logger.log(Level.INFO, "正在创建文件夹...");
        // 调用createDirectories()创建数据目录和插件目录喵~
        createDirectories();

        // 从配置文件加载各种设置到内存缓存中喵~
        cfgManager.load();
        // 加载所有注册表数据（物品/研究/多方块结构等）喵~
        registry.load(this);

        // 初始化机器损坏管理器，用于读取机器损坏相关的配置文件喵~
        machineDamageManager = new SlimefunMachineDamageManager(this);
        // 创建机器损坏服务，负责处理实际的机器损坏/修复/报废逻辑喵~
        machineDamageService = new MachineDamageService(this);
        // 启动机器损坏服务的后台线程喵~
        machineDamageService.start();
        // 初始化机器反馈服务，用于管理机器工作时的粒子和音效喵~
        machineFeedbackService = new MachineFeedbackService(this);

        // 记录日志：开始加载数据库喵~
        logger.log(Level.INFO, "正在加载数据库...");
        // 检测是否存在旧版文件存储的玩家数据或方块数据，存在则提示迁移喵~
        if (PlayerProfileMigrator.getInstance().hasOldData()
                || BlockStorageMigrator.getInstance().hasOldData()) {
            // 打印分隔线警告，提醒服主有旧数据需要迁移喵~
            Slimefun.logger().warning("====================================================");
            Slimefun.logger().warning("\n");
            // 打印警告：检测到旧版文件存储格式的数据喵~
            Slimefun.logger().log(Level.WARNING, "!!! 检测到使用文件储存的旧玩家数据 !!!");
            // 提示使用 /sf migrate confirm 命令进行数据迁移喵~
            Slimefun.logger().warning("请在服务器加载完成后, 使用 /sf migrate confirm 进行迁移!");
            // 警告：若不迁移旧数据将失效喵~
            Slimefun.logger().warning("如果不迁移, 旧版本的数据将会失效!!!");
            Slimefun.logger().warning("\n");
            // 提示需要使用数据库的用户如何配置喵~
            Slimefun.logger().warning("需要使用数据库的用户, 请关服后在以下配置文件中配置数据库:");
            // 说明需要配置的两个yml文件名称喵~
            Slimefun.logger().warning("block-storage.yml 和 profile-storage.yml");
            Slimefun.logger().warning("\n");
            Slimefun.logger().warning("====================================================");
        }
        // 初始化数据库连接，开始接受数据读写请求喵~
        databaseManager.init();

        // 记录日志：开始加载语言文件喵~
        logger.log(Level.INFO, "正在加载语言文件...");

        // 从配置管理器获取插件主配置对象喵~
        var config = cfgManager.getPluginConfig();
        // 读取聊天消息前缀配置，例如"[Slimefun]"喵~
        String chatPrefix = config.getString("options.chat-prefix");
        // 读取服务器默认语言代码，例如"zh-CN"喵~
        String serverDefaultLanguage = config.getString("options.language");
        // 创建本地化服务实例，加载对应语言的翻译文件喵~
        local = new LocalizationService(this, chatPrefix, serverDefaultLanguage);

        // 从配置文件读取货运/能量网络最大节点数量限制喵~
        int networkSize = config.getInt("networks.max-size");

        // 喵~防御：networkSize小于1时不合法，重置为1避免网络管理器出错喵~
        if (networkSize < 1) {
            // 打印警告提示服主配置错误并说明当前错误值喵~
            logger.log(Level.WARNING, "'networks.max-size' 大小设置错误! 它必须大于1, 而你设置的是: {0}", networkSize);
            // 将网络大小重置为最小合法值1喵~
            networkSize = 1;
        }

        // 创建网络管理器，传入最大节点数/是否启用可视化/是否删除多余物品三个配置喵~
        networkManager = new NetworkManager(
                networkSize,
                config.getBoolean("networks.enable-visualizer"),
                config.getBoolean("networks.delete-excess-items"));

        // 在独立线程中启动bStats统计服务，避免阻塞主线程喵~
        new Thread(metricsService::start, "Slimefun Metrics").start();
        // 启动数据分析服务，收集匿名使用数据喵~
        analyticsService.start();

        // 记录日志：开始注册所有GEO地下矿物资源喵~
        logger.log(Level.INFO, "加载矿物资源...");
        // 注册所有内置GEO资源类型（石油/煤矿/冰晶石等）喵~
        GEOResourcesSetup.setup();

        // 记录日志：开始加载自定义物品Tag（用于配方匹配）喵~
        logger.log(Level.INFO, "加载自定义标签...");
        // 加载所有SlimefunTag，供配方系统和方块匹配使用喵~
        loadTags();

        // 记录日志：开始加载所有Slimefun物品喵~
        logger.log(Level.INFO, "加载物品...");
        // 注册并初始化所有内置Slimefun物品喵~
        loadItems();

        // 记录日志：开始加载研究配置喵~
        logger.log(Level.INFO, "加载研究项目...");
        // 注册所有研究节点，将物品与研究关联喵~
        loadResearches();

        // 初始化Wiki链接映射表，供指南GUI中的"查看Wiki"按钮使用喵~
        PostSetup.setupWiki();

        // 记录日志：开始注册Bukkit事件监听器喵~
        logger.log(Level.INFO, "正在注册监听器...");

        // 调用SlimefunExtended注入下游扩展的额外组件喵~
        SlimefunExtended.init(this);

        // 批量注册所有Slimefun事件监听器喵~
        registerListeners();

        /*
         * 服务器加载完成后延迟0ms执行启动任务喵~
         * 整体思路：
         *   - 使用SlimefunStartupTask在服务器启动完毕后再做最终初始化
         *   - 包括贴图注册、权限更新、音效重载、配方刷新等喵~
         *   - delay=0 表示下一个tick执行，确保所有插件都已加载完成喵~
         */
        runSync(
                new SlimefunStartupTask(this, () -> {
                    // 初始化慢SQL检测器，监控数据库查询性能喵~
                    sqlProfiler.initSlowSqlCheck(this);
                    // 为所有已注册的Slimefun物品注册自定义贴图（物品外观模型）喵~
                    textureService.register(registry.getAllSlimefunItems(), true);
                    // 更新所有已注册Slimefun物品的权限节点配置喵~
                    permissionsService.update(registry.getAllSlimefunItems(), true);
                    // 重新加载音效配置文件，使音效设置生效喵~
                    soundService.reload(true);

                    // 喵~防御：某些有Bug的Spigot版本遍历配方时会抛异常，用try/catch防止阻断物品加载喵~
                    try {
                        // 刷新原版配方缓存，让指南能正确显示原版合成信息喵~
                        recipeService.refresh();
                    } catch (Exception | LinkageError x) {
                        // 捕获配方刷新异常并记录到日志，不影响Slimefun正常运行喵~
                        logger.log(
                                Level.SEVERE,
                                x,
                                () -> "An Exception occured while iterating through the Recipe list on Minecraft"
                                        + " Version "
                                        + minecraftVersion.getName()
                                        + " (Slimefun v"
                                        + getVersion()
                                        + ")");
                    }
                }),
                0);

        // 注册 /slimefun 命令，喵~防御：用try/catch防止LinkageError导致启动失败喵~
        try {
            // 向Bukkit注册Slimefun的命令处理器喵~
            command.register();
        } catch (Exception | LinkageError x) {
            // 命令注册失败时记录错误日志，不影响插件其他功能喵~
            logger.log(Level.SEVERE, "An Exception occurred while registering the /slimefun command", x);
        }

        // 根据配置决定是否启动护甲效果相关的定时任务喵~
        if (config.getBoolean("options.enable-armor-effects")) {
            // 启动护甲效果检测任务，interval单位是秒，乘以20换算为游戏tick(每秒20tick)喵~
            new SlimefunArmorTask().schedule(this, config.getInt("options.armor-update-interval") * 20L);
            // 如果同时开启了辐射效果，则启动辐射定时任务喵~
            if (config.getBoolean("options.enable-radiation")) {
                // 启动辐射效果检测任务，同样换算为tick喵~
                new RadiationTask().schedule(this, config.getInt("options.radiation-update-interval") * 20L);
            }
            // 启动彩虹护甲颜色变换任务喵~
            new RainbowArmorTask().schedule(this, config.getInt("options.rainbow-armor-update-interval") * 20L);
            // 启动太阳能头盔充电任务，直接使用秒为单位喵~
            new SolarHelmetTask().schedule(this, config.getInt("options.armor-update-interval"));
        } else if (config.getBoolean("options.enable-radiation")) {
            // 护甲效果未启用时无法单独启用辐射，输出警告喵~
            logger.log(Level.WARNING, "Cannot enable radiation while armor effects are disabled.");
        }

        // 启动自动保存服务，按配置的分钟间隔定期保存玩家数据喵~
        autoSavingService.start(this, config.getInt("options.auto-save-delay-in-minutes"));
        // 启动全息文字服务，用于在游戏内显示机器信息悬浮文字喵~
        hologramsService.start();
        // 启动方块tick处理器，这是Slimefun机器运作的核心定时器喵~
        ticker.start(this);

        // 加载第三方插件集成（如WorldEdit、Vault等）喵~
        logger.log(Level.INFO, "正在加载第三方插件支持...");
        // 初始化所有已注册的第三方插件集成喵~
        integrations.start();

        // 启动GitHub服务，用于从GitHub获取贡献者信息等数据喵~
        gitHubService.start(this);

        // 记录Slimefun完成加载的耗时信息喵~
        logger.log(Level.INFO, "Slimefun 完成加载, 耗时 {0}", getStartupTime(timestamp));
    }

    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Override
    public String getBugTrackerURL() {
        return "https://github.com/SlimefunGuguProject/Slimefun4/issues";
    }

    @Override
    public String getWikiURL() {
        return "https://slimefun-wiki.guizhanss.cn/{0}";
    }

    /**
     * This method gets called when the {@link Plugin} gets disabled.
     * Most often it is called when the {@link Server} is shutting down or reloading.
     */
    @Override
    public void onDisable() {
        // 喵~防御：如果插件从未成功加载(instance为null)或是单元测试环境，直接跳过关闭逻辑避免空指针喵~
        if (instance() == null || minecraftVersion == MinecraftVersion.UNIT_TEST) {
            return;
        }

        // 关闭SlimefunExtended扩展模块喵~
        SlimefunExtended.shutdown();
        // 关闭SQL性能分析器，停止慢查询检测喵~
        getSQLProfiler().shutdown();

        // 停止机器损坏服务，释放相关资源喵~
        // 喵~防御：先判断machineDamageService非null再调用stop，防止插件未完全初始化时报错喵~
        if (machineDamageService != null) {
            // 停止机器损坏后台服务线程喵~
            machineDamageService.stop();
        }

        // 喵~防御：同样判断machineFeedbackService非null再清理，避免未初始化时空指针喵~
        if (machineFeedbackService != null) {
            // 清理机器反馈服务（粒子/音效等），防止内存泄漏喵~
            machineFeedbackService.cleanup();
        }

        // 立即取消本插件注册的所有Bukkit定时任务，防止关服后还在运行喵~
        Bukkit.getScheduler().cancelTasks(this);

        // 暂停方块tick处理器，不再处理新的方块tick喵~
        ticker.setPaused(true);
        // 等待ticker完全停止，确保正在处理的tick全部结束喵~
        ticker.halt();

        // 保存所有活跃的机器操作到数据库（在 ticker 暂停后执行，确保数据一致性）喵~
        MachineStatePersistence.saveAllOperationsToDatabase();

        // 停止性能分析器的后台线程，释放资源喵~
        profiler.kill();

        // 遍历内存中所有已加载的玩家档案，将有修改的档案保存到数据库喵~
        // 主人注意：forEachRemaining遍历所有已加载玩家档案，玩家数量多时可能稍有延迟喵~
        PlayerProfile.iterator().forEachRemaining(profile -> {
            // 喵~防御：只保存有修改(isDirty)的档案，避免无谓的数据库写操作喵~
            if (profile.isDirty()) {
                // 同步保存该玩家档案到磁盘/数据库喵~
                profile.save();
            }
        });

        // 关闭数据库连接，释放连接池资源喵~
        databaseManager.shutdown();

        // 如果配置了backup-data选项，在关服时自动生成一份数据备份zip包喵~
        if (cfgManager.getPluginConfig().getBoolean("options.backup-data")) {
            // 执行备份任务，将当前数据打包保存喵~
            backupService.run();
        }

        // 关闭并清理Metrics服务，停止向bStats发送统计数据喵~
        metricsService.cleanUp();

        // 将插件实例设为null，标记插件已完全关闭喵~
        setInstance(null);

        /**
         * Close all inventories on the server to prevent item dupes
         * (Incase some idiot uses /reload)
         */
        // 强制关闭所有在线玩家的背包界面，防止/reload时物品复制漏洞喵~
        for (Player p : Bukkit.getOnlinePlayers()) {
            // 关闭该玩家当前打开的背包/GUI界面喵~
            p.closeInventory();
        }
    }

    /**
     * This is a private internal method to set the de-facto instance of {@link Slimefun}.
     * Having this as a seperate method ensures the seperation between static and non-static fields.
     * It also makes sonarcloud happy :)
     * Only ever use it during {@link #onEnable()} or {@link #onDisable()}.
     *
     * @param pluginInstance Our instance of {@link Slimefun} or null
     */
    private static void setInstance(@Nullable Slimefun pluginInstance) {
        // 将全局静态instance字段设置为传入的插件实例（或null表示插件关闭）喵~
        instance = pluginInstance;
    }

    /**
     * This private method gives us a {@link Collection} of every {@link MinecraftVersion}
     * that Slimefun is compatible with (as a {@link String} representation).
     * <p>
     * Example:
     *
     * <pre>
     * { 1.14.x, 1.15.x, 1.16.x }
     * </pre>
     *
     * @return A {@link Collection} of all compatible minecraft versions as strings
     */
    static @Nonnull Collection<String> getSupportedVersions() {
        // 创建一个字符串列表，用于收集所有支持的Minecraft版本名称喵~
        List<String> list = new ArrayList<>();

        // 遍历所有MinecraftVersion枚举值，筛选出非虚拟版本（真实存在的游戏版本）喵~
        for (MinecraftVersion version : MinecraftVersion.values()) {
            // 喵~防御：isVirtual()为true的是占位符版本(如UNKNOWN/UNIT_TEST)，不应出现在支持列表里喵
            if (!version.isVirtual()) {
                // 将该版本的名称字符串（如"1.20.4"）加入列表喵~
                list.add(version.getName());
            }
        }

        // 返回所有真实支持的Minecraft版本名称集合喵~
        return list;
    }

    /**
     * This returns the {@link Logger} instance that Slimefun uses.
     * <p>
     * <strong>Any {@link SlimefunAddon} should use their own {@link Logger} instance!</strong>
     *
     * @return Our {@link Logger} instance
     */
    public static @Nonnull Logger logger() {
        // 喵~防御：调用前确保instance不为null，否则抛出明确异常喵
        validateInstance();
        // 返回当前插件实例的Logger，用于向控制台输出日志喵~
        return instance.getLogger();
    }

    /**
     * This method checks for the {@link MinecraftVersion} of the {@link Server}.
     * If the version is unsupported, a warning will be printed to the console.
     *
     * @return Whether the {@link MinecraftVersion} is unsupported
     */
    private boolean isVersionUnsupported() {
        try {
            // Now check the actual Version of Minecraft
            // the Minecraft version id (e.g. "1.20.4", "1.20.2-pre2", "23w31a")
            // 从SlimefunExtended获取服务端版本详情对象，包含major/minor/patch三段版本号喵~
            ServerVersion serverVerDetail = SlimefunExtended.getServerVerDetail(getServer());

            // 喵~防御：serverVerDetail为null说明无法解析版本，记录警告但不阻止启动喵
            if (serverVerDetail == null) {
                getLogger()
                        .log(
                                Level.WARNING,
                                "我们无法识别你正在使用的 Minecraft 版本 ({0})",
                                getServer().getMinecraftVersion());
                return false;
            }

            // 提取版本号的主版本、次版本、补丁版本，用于后续与已知版本列表比对喵~
            int major = serverVerDetail.getMajor();
            int minor = serverVerDetail.getMinor();
            int patch = serverVerDetail.getPatch();

            // Check all supported versions of Minecraft
            // 遍历所有枚举中已支持的 MinecraftVersion，逐一比对当前服务端版本喵~
            for (MinecraftVersion supportedVersion : MinecraftVersion.values()) {
                // 若找到匹配的支持版本，则记录并返回false(表示版本受支持)喵~
                if (supportedVersion.isMinecraftVersion(major, minor, patch)) {
                    // 将当前 Minecraft 版本保存到字段，供后续 getMinecraftVersion() 使用喵~
                    minecraftVersion = supportedVersion;
                    return false;
                }
            }

            // Looks like you are using an unsupported Minecraft Version
            // 没有找到匹配的支持版本，打印不支持版本警告信息喵~
            StartupWarnings.invalidMinecraftVersion(
                    getLogger(),
                    getServer().getMinecraftVersion(),
                    getDescription().getVersion());
            // 版本不受支持，返回true让调用方禁用插件喵~
            return true;
        } catch (Exception | LinkageError x) {
            // 喵~防御：版本检测过程中抛出异常(如Spigot内部改动)，记录严重错误并视为不支持喵
            getLogger()
                    .log(
                            Level.SEVERE,
                            x,
                            () -> "错误: 无法识别服务器 Minecraft 版本, Slimefun v"
                                    + getDescription().getVersion());

            // We assume "unsupported" if something went wrong.
            // 出错时保守处理：视为不支持版本喵~
            return true;
        }
    }

    /**
     * This returns our {@link GPSNetwork} instance.
     * The {@link GPSNetwork} is responsible for handling any GPS-related
     * operations and for managing any {@link GEOResource}.
     *
     * @return Our {@link GPSNetwork} instance
     */
    public static @Nonnull GPSNetwork getGPSNetwork() {
        validateInstance();
        return instance.gpsNetwork;
    }

    /**
     * This method creates all necessary directories (and sub directories) for Slimefun.
     */
    private void createDirectories() {
        // 需要在 data-storage/Slimefun 下创建的子目录：路径点和方块备份喵~
        String[] storageFolders = {"waypoints", "block-backups"};
        // 需要在 plugins/Slimefun 下创建的子目录：脚本、错误报告、GitHub缓存、世界设置喵~
        String[] pluginFolders = {"scripts", "error-reports", "cache/github", "world-settings"};

        // 遍历数据存储目录列表，逐个确保目录存在喵~
        for (String folder : storageFolders) {
            // 拼接完整路径，例如 data-storage/Slimefun/waypoints 喵~
            File file = new File("data-storage/Slimefun", folder);

            // 喵~防御：目录不存在时才创建，避免重复操作喵
            if (!file.exists()) {
                // mkdirs() 会同时创建所有缺失的上级目录喵~
                file.mkdirs();
            }
        }

        // 遍历插件目录列表，逐个确保目录存在喵~
        for (String folder : pluginFolders) {
            // 拼接完整路径，例如 plugins/Slimefun/error-reports 喵~
            File file = new File("plugins/Slimefun", folder);

            // 喵~防御：目录不存在时才创建，避免重复操作喵
            if (!file.exists()) {
                // 创建目录及所有必要的父级目录喵~
                file.mkdirs();
            }
        }
    }

    /**
     * This method registers all of our {@link Listener Listeners}.
     */
    private void registerListeners() {
        // 初始化玩家聊天捕获器，用于拦截玩家聊天输入（如GUI中的文字输入）喵~
        chatCatcher = new PlayerChatCatcher(this);
        // 注册旧版 CS-CoreLib 的菜单监听器（已弃用但保留兼容性）喵~
        new MenuListener(this);

        // 注册靴子特效监听器（如喷气背包靴子等）喵~
        new SlimefunBootsListener(this);
        // 注册物品交互监听器，处理玩家右键使用Slimefun物品的事件喵~
        new SlimefunItemInteractListener(this);
        // 注册物品消耗监听器，处理玩家食用/饮用Slimefun物品的事件喵~
        new SlimefunItemConsumeListener(this);
        // 注册方块物理监听器，防止Slimefun方块被沙子、水等破坏喵~
        new BlockPhysicsListener(this);
        // 注册货运节点监听器，处理货运网络节点的放置和破坏喵~
        new CargoNodeListener(this);
        // 注册多方块结构监听器，处理玩家与多方块机器的交互喵~
        new MultiBlockListener(this);
        // 注册小工具监听器，处理各类小工具物品的使用逻辑喵~
        new GadgetsListener(this);
        // 注册发射器监听器，处理发射器发射Slimefun物品的事件喵~
        new DispenserListener(this);
        // 注册通用方块监听器，处理Slimefun方块的放置和破坏喵~
        new BlockListener(this);
        // 注册强化熔炉监听器，处理强化熔炉的加工事件喵~
        new EnhancedFurnaceListener(this);
        // 注册物品拾取监听器，处理玩家拾取Slimefun物品的事件喵~
        new ItemPickupListener(this);
        // 注册物品丢弃监听器，处理玩家丢弃Slimefun物品的事件喵~
        new ItemDropListener(this);
        // 注册死亡路径点监听器，玩家死亡时自动记录坐标喵~
        new DeathpointListener(this);
        // 注册爆炸监听器，防止爆炸破坏受保护的Slimefun方块喵~
        new ExplosionsListener(this);
        // 注册调试鱼监听器，用于开发者调试Slimefun方块数据喵~
        new DebugFishListener(this);
        // 注册烟花监听器，处理Slimefun相关烟花事件喵~
        new FireworksListener(this);
        // 注册凋零监听器，防止凋零Boss破坏受保护的Slimefun方块喵~
        new WitherListener(this);
        // 注册铁傀儡监听器，处理铁傀儡相关的Slimefun逻辑喵~
        new IronGolemListener(this);
        // 注册实体交互监听器，处理玩家与实体右键交互的Slimefun事件喵~
        new EntityInteractionListener(this);
        // 注册怪物掉落监听器，实现Slimefun物品的自定义掉落喵~
        new MobDropListener(this);
        // 注册村民交易监听器，处理村民交易相关的Slimefun逻辑喵~
        new VillagerTradingListener(this);
        // 注册鞘翅撞墙监听器，配合能量过载护甲保护玩家喵~
        new ElytraImpactListener(this);
        // 注册合成台监听器，防止在合成台中合成被禁用的Slimefun物品喵~
        new CraftingTableListener(this);
        // 注册铁砧监听器，防止通过铁砧绕过Slimefun物品限制喵~
        new AnvilListener(this);
        // 注册酿造台监听器，防止通过酿造台绕过限制喵~
        new BrewingStandListener(this);
        // 注册炼药锅监听器，处理炼药锅相关的Slimefun事件喵~
        new CauldronListener(this);
        // 注册砂轮监听器，防止通过砂轮绕过Slimefun附魔限制喵~
        new GrindstoneListener(this);
        // 注册制图台监听器，防止通过制图台绕过物品限制喵~
        new CartographyTableListener(this);
        // 注册屠夫机器人监听器，处理屠夫型机器人的击杀逻辑喵~
        new ButcherAndroidListener(this);
        // 注册采矿机器人监听器，处理采矿型机器人的挖矿逻辑喵~
        new MiningAndroidListener(this);
        // 注册网络监听器，监听能量和货运网络节点的变化喵~
        new NetworkListener(this, networkManager);
        // 注册漏斗监听器，防止漏斗与被标记为 NotHopperable 的容器交互喵~
        new HopperListener(this);
        // 注册护符监听器，处理护符被激活的事件喵~
        new TalismanListener(this);
        // 注册灵魂绑定监听器，防止灵魂绑定物品在玩家死亡时掉落喵~
        new SoulboundListener(this);
        // 注册自动合成器监听器，处理自动合成器工作时的事件喵~
        new AutoCrafterListener(this);
        // 注册Slimefun物品命中监听器，处理用Slimefun武器攻击实体的事件喵~
        new SlimefunItemHitListener(this);
        // 根据服务端版本决定注册哪种中键点击监听器喵~
        if (SlimefunExtended.isAtLeast(1, 21, 5)) {
            // 1.21.5+ 使用新版中键点击监听器（API有变化）喵~
            new VersionedMiddleClickListener(this);
        } else {
            // 1.21.5以下使用旧版中键点击监听器喵~
            new MiddleClickListener(this);
        }
        // 注册蜜蜂监听器，处理蜜蜂相关的Slimefun防护事件喵~
        new BeeListener(this);
        // 注册蜜蜂翅膀监听器，需要传入蜜蜂翅膀物品实例喵~
        new BeeWingsListener(this, (BeeWings) SlimefunItems.BEE_WINGS.getItem());
        // 注册猪灵监听器，处理猪灵以物易物时的自定义掉落喵~
        new PiglinListener(this);
        // 注册锻造台监听器，处理锻造台升级相关的Slimefun限制喵~
        new SmithingTableListener(this);
        // 注册原版合成器监听器，处理原版合成相关的Slimefun事件喵~
        new VanillaCrafterListener(this);
        // 注册加入服务器监听器，处理玩家加入时的初始化逻辑喵~
        new JoinListener(this);

        // Item-specific Listeners
        // 注册冷却器监听器，需要传入冷却器物品实例喵~
        new CoolerListener(this, (Cooler) SlimefunItems.COOLER.getItem());
        // 注册地震斧监听器，需要传入地震斧物品实例喵~
        new SeismicAxeListener(this, (SeismicAxe) SlimefunItems.SEISMIC_AXE.getItem());
        // 注册放射性监听器，检测玩家背包中的放射性物品并施加辐射效果喵~
        new RadioactivityListener(this);
        // 注册古代祭坛监听器，需要传入祭坛和祭坛基座物品实例喵~
        new AncientAltarListener(this, (AncientAltar) SlimefunItems.ANCIENT_ALTAR.getItem(), (AncientPedestal)
                SlimefunItems.ANCIENT_PEDESTAL.getItem());
        // 注册抓钩监听器，需要传入抓钩物品实例喵~
        grapplingHookListener.register(this, (GrapplingHook) SlimefunItems.GRAPPLING_HOOK.getItem());
        // 注册弓箭监听器，处理Slimefun自定义弓的射击事件喵~
        bowListener.register(this);
        // 注册背包监听器，处理玩家打开和操作Slimefun背包的事件喵~
        backpackListener.register(this);

        // Handle Slimefun Guide being given on Join
        // 注册Slimefun指南监听器，控制玩家首次加入时是否自动获得指南喵~
        new SlimefunGuideListener(this, cfgManager.getPluginConfig().getBoolean("guide.receive-on-first-join"));

        // Clear the Slimefun Guide History upon Player Leaving
        // 注册玩家档案监听器，玩家退出时清理其指南浏览历史喵~
        new PlayerProfileListener(this);

        // Machine damage listener for item lore updates
        // 注册机器损坏监听器，负责更新机器物品的 lore 显示损坏状态喵~
        new MachineDamageListener(this);
        // Machine damage notification listener for player notifications
        // 注册机器损坏通知监听器，负责向玩家发送机器损坏的聊天消息提醒喵~
        new MachineDamageNotificationListener(this);
    }

    /**
     * This (re)loads every {@link SlimefunTag}.
     */
    private void loadTags() {
        // 遍历所有 SlimefunTag 枚举值，逐一加载自定义方块标签喵~
        for (SlimefunTag tag : SlimefunTag.values()) {
            try {
                // Only reload "empty" (or unloaded) Tags
                // 喵~防御：只加载尚未加载过的空标签，避免重复加载浪费资源喵~
                if (tag.isEmpty()) {
                    // 重新加载此标签的配置内容（如木头类、栅栏类等材质组）喵~
                    tag.reload();
                }
            } catch (TagMisconfigurationException e) {
                // 喵~防御：捕获标签配置错误，记录严重日志并跳过该标签，不影响其他标签加载喵~
                getLogger().log(Level.SEVERE, e, () -> "Failed to load Tag: " + tag.name());
            }
        }
    }

    /**
     * This loads all of our items.
     */
    private void loadItems() {
        try {
            // 调用物品注册器，将所有内置Slimefun物品注册到注册表中喵~
            SlimefunItemSetup.setup(this);
        } catch (Exception | LinkageError x) {
            // 喵~防御：捕获物品初始化时的任意异常，记录严重日志但不阻断服务器启动喵~
            getLogger()
                    .log(
                            Level.SEVERE,
                            x,
                            () -> "An Error occurred while initializing SlimefunItems for Slimefun " + getVersion());
        }
    }

    /**
     * This loads our researches.
     */
    private void loadResearches() {
        try {
            // 调用研究注册器，将所有内置研究注册到注册表中喵~
            ResearchSetup.setupResearches();
        } catch (Exception | LinkageError x) {
            // 喵~防御：捕获研究初始化时的任意异常，记录严重日志但不阻断服务器启动喵~
            getLogger()
                    .log(
                            Level.SEVERE,
                            x,
                            () -> "An Error occurred while initializing Slimefun Researches for Slimefun "
                                    + getVersion());
        }
    }

    /**
     * This returns the global instance of {@link Slimefun}.
     * This may return null if the {@link Plugin} was disabled.
     *
     * @return The {@link Slimefun} instance
     */
    public static @Nullable Slimefun instance() {
        return instance;
    }

    /**
     * This private static method allows us to throw a proper {@link Exception}
     * whenever someone tries to access a static method while the instance is null.
     * This happens when the method is invoked before {@link #onEnable()} or after {@link #onDisable()}.
     * <p>
     * Use it whenever a null check is needed to avoid a non-descriptive {@link NullPointerException}.
     */
    private static void validateInstance() {
        // 喵~防御：如果instance为null说明插件尚未启动或已被禁用，抛出明确异常防止空指针崩溃喵~
        if (instance == null) {
            // 抛出包含明确原因的非法状态异常，方便开发者定位问题喵~
            throw new IllegalStateException("Cannot invoke static method, Slimefun instance is null.");
        }
    }

    /**
     * This method returns out {@link MinecraftRecipeService} for Slimefun.
     * This service is responsible for finding/identifying {@link Recipe Recipes}
     * from vanilla Minecraft.
     *
     * @return Slimefun's {@link MinecraftRecipeService} instance
     */
    public static @Nonnull MinecraftRecipeService getMinecraftRecipeService() {
        validateInstance();
        return instance.recipeService;
    }

    /**
     * This returns the version of Slimefun that is currently installed.
     *
     * @return The currently installed version of Slimefun
     */
    public static @Nonnull String getVersion() {
        validateInstance();
        return instance.getDescription().getVersion();
    }

    public static @Nonnull Config getCfg() {
        // 确保插件实例存在，否则抛出异常喵~
        validateInstance();
        // 返回主配置文件对象（对应 config.yml）喵~
        return instance.cfgManager.getPluginConfig();
    }

    public static @Nonnull Config getResearchCfg() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回研究系统的配置文件对象（对应 Researches.yml）喵~
        return instance.researches;
    }

    public static @Nonnull Config getItemCfg() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回物品配置文件对象（对应 Items.yml）喵~
        return instance.items;
    }

    /**
     * This method returns out world settings service.
     * That service is responsible for managing item settings per
     * {@link World}, such as disabling a {@link SlimefunItem} in a
     * specific {@link World}.
     *
     * @return Our instance of {@link PerWorldSettingsService}
     */
    public static @Nonnull PerWorldSettingsService getWorldSettingsService() {
        validateInstance();
        return instance.worldSettingsService;
    }

    public static @Nonnull TickerTask getTickerTask() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回方块 tick 调度任务，负责驱动所有 Slimefun 方块定时运作喵~
        return instance.ticker;
    }

    /**
     * This returns the {@link LocalizationService} of Slimefun.
     *
     * @return The {@link LocalizationService} of Slimefun
     */
    public static @Nonnull LocalizationService getLocalization() {
        validateInstance();
        return instance.local;
    }

    /**
     * This returns our {@link HologramsService} which handles the creation and
     * cleanup of any holograms.
     *
     * @return Our instance of {@link HologramsService}
     */
    public static @Nonnull HologramsService getHologramsService() {
        validateInstance();
        return instance.hologramsService;
    }

    public static @Nonnull CustomItemDataService getItemDataService() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回物品 NBT 数据服务，用于读写 Slimefun 物品 ID 标识喵~
        return instance.itemDataService;
    }

    public static @Nonnull CustomTextureService getItemTextureService() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回自定义纹理服务，负责给物品应用自定义模型数据喵~
        return instance.textureService;
    }

    public static @Nonnull PermissionsService getPermissionsService() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回权限服务，管理哪些玩家有权限使用特定 Slimefun 物品喵~
        return instance.permissionsService;
    }

    public static @Nonnull BlockDataService getBlockDataService() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回方块 NBT 数据服务，用于读写已放置方块的自定义标记喵~
        return instance.blockDataService;
    }

    public static @Nonnull ItemStackService getItemStackService() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回物品栈服务，提供物品比较、匹配与放入背包等高级操作喵~
        return instance.itemStackService;
    }

    /**
     * This returns our instance of {@link IntegrationsManager}.
     * This is responsible for managing any integrations with third party {@link Plugin plugins}.
     *
     * @return Our instance of {@link IntegrationsManager}
     */
    public static @Nonnull IntegrationsManager getIntegrations() {
        validateInstance();
        return instance.integrations;
    }

    /**
     * This returns out instance of the {@link ProtectionManager}.
     * This bridge is used to hook into any third-party protection {@link Plugin}.
     *
     * @return Our instanceof of the {@link ProtectionManager}
     */
    public static @Nonnull ProtectionManager getProtectionManager() {
        return getIntegrations().getProtectionManager();
    }

    /**
     * This returns our {@link  SoundService} which handles the configuration of all sounds used in Slimefun
     *
     * @return Our instance of {@link SoundService}
     */
    @Nonnull
    public static SoundService getSoundService() {
        validateInstance();
        return instance.soundService;
    }

    /**
     * This returns our {@link NetworkManager} which is responsible
     * for handling the Cargo and Energy networks.
     *
     * @return Our {@link NetworkManager} instance
     */
    public static @Nonnull NetworkManager getNetworkManager() {
        validateInstance();
        return instance.networkManager;
    }

    /**
     * This returns our {@link SlimefunMachineDamageManager} which is responsible
     * for managing machine damage configurations.
     *
     * @return Our {@link SlimefunMachineDamageManager} instance
     */
    public static @Nonnull SlimefunMachineDamageManager getMachineDamageManager() {
        validateInstance();
        return instance.machineDamageManager;
    }

    /**
     * This returns our {@link MachineDamageService} which is responsible
     * for handling machine damage logic.
     *
     * @return Our {@link MachineDamageService} instance
     */
    public static @Nonnull MachineDamageService getMachineDamageService() {
        validateInstance();
        return instance.machineDamageService;
    }

    /**
     * This returns our {@link MachineFeedbackService} which is responsible
     * for handling machine work feedback (particles, sounds, block states).
     *
     * @return Our {@link MachineFeedbackService} instance
     */
    public static @Nonnull MachineFeedbackService getMachineFeedbackService() {
        validateInstance();
        return instance.machineFeedbackService;
    }

    /**
     * This returns the time it took to load Slimefun (given a starting point).
     *
     * @param timestamp The time at which we started to load Slimefun.
     * @return The total time it took to load Slimefun (in ms or s)
     */
    private @Nonnull String getStartupTime(long timestamp) {
        // 将纳秒差值转换为毫秒，方便人类阅读喵~
        long ms = (System.nanoTime() - timestamp) / 1000000;

        // 超过1000ms时换算成秒显示，更加直观喵~
        if (ms > 1000) {
            // 耗时超过1秒，转换为秒并四舍五入后拼上's'后缀喵~
            return NumberUtils.roundDecimalNumber(ms / 1000.0) + 's';
        } else {
            // 耗时不足1秒，直接显示毫秒数拼上"ms"后缀喵~
            return NumberUtils.roundDecimalNumber(ms) + "ms";
        }
    }

    /**
     * This method returns the {@link UpdaterService} of Slimefun.
     * It is used to handle automatic updates.
     *
     * @return The {@link UpdaterService} for Slimefun
     */
    public static @Nonnull UpdaterService getUpdater() {
        validateInstance();
        return instance.updaterService;
    }

    /**
     * This method returns the {@link MetricsService} of Slimefun.
     * It is used to handle sending metric information to bStats.
     *
     * @return The {@link MetricsService} for Slimefun
     */
    public static @Nonnull MetricsService getMetricsService() {
        validateInstance();
        return instance.metricsService;
    }

    /**
     * This method returns the {@link AnalyticsService} of Slimefun.
     * It is used to handle sending analytic information.
     *
     * @return The {@link AnalyticsService} for Slimefun
     */
    public static @Nonnull AnalyticsService getAnalyticsService() {
        validateInstance();
        return instance.analyticsService;
    }

    /**
     * This method returns the {@link GitHubService} of Slimefun.
     * It is used to retrieve data from GitHub repositories.
     *
     * @return The {@link GitHubService} for Slimefun
     */
    public static @Nonnull GitHubService getGitHubService() {
        validateInstance();
        return instance.gitHubService;
    }

    /**
     * This method checks if this is currently running in a unit test
     * environment.
     *
     * @return Whether we are inside a unit test
     */
    public boolean isUnitTest() {
        // 通过对比 minecraftVersion 是否等于单元测试专用版本来判断当前运行环境喵~
        return minecraftVersion == MinecraftVersion.UNIT_TEST;
    }

    public static @Nonnull SlimefunConfigManager getConfigManager() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回配置管理器，负责加载和读取 config.yml 等配置文件喵~
        return instance.cfgManager;
    }

    public static @Nonnull SlimefunDatabaseManager getDatabaseManager() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回数据库管理器，负责初始化和关闭玩家/方块数据存储喵~
        return instance.databaseManager;
    }

    public static @Nonnull SlimefunRegistry getRegistry() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回全局注册表，存储所有已注册的物品、物品组、研究等核心数据喵~
        return instance.registry;
    }

    public static @Nonnull GrapplingHookListener getGrapplingHookListener() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回钩爪监听器，处理钩爪物品的发射与收回逻辑喵~
        return instance.grapplingHookListener;
    }

    public static @Nonnull BackpackListener getBackpackListener() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回背包监听器，处理 Slimefun 背包物品的打开与交互喵~
        return instance.backpackListener;
    }

    public static @Nonnull SlimefunBowListener getBowListener() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回弓箭监听器，处理 Slimefun 弓箭物品的射击事件喵~
        return instance.bowListener;
    }

    /**
     * The {@link Command} that was added by Slimefun.
     *
     * @return Slimefun's command
     */
    public static @Nonnull SlimefunCommand getCommand() {
        validateInstance();
        return instance.command;
    }

    /**
     * This returns our instance of the {@link SlimefunProfiler}, a tool that is used
     * to analyse performance and lag.
     *
     * @return The {@link SlimefunProfiler}
     */
    public static @Nonnull SlimefunProfiler getProfiler() {
        validateInstance();
        return instance.profiler;
    }

    public static @Nonnull SQLProfiler getSQLProfiler() {
        // 确保插件实例存在喵~
        validateInstance();
        // 返回 SQL 慢查询分析器，用于检测数据库操作的性能瓶颈喵~
        return instance.sqlProfiler;
    }

    /**
     * This returns the currently installed version of Minecraft.
     *
     * @return The current version of Minecraft
     */
    public static @Nonnull MinecraftVersion getMinecraftVersion() {
        validateInstance();
        return instance.minecraftVersion;
    }

    /**
     * This method returns whether this version of Slimefun was newly installed.
     * It will return true if this {@link Server} uses Slimefun for the very first time.
     *
     * @return Whether this is a new installation of Slimefun
     */
    public static boolean isNewlyInstalled() {
        validateInstance();
        return instance.isNewlyInstalled;
    }

    /**
     * This method returns a {@link Set} of every {@link Plugin} that lists Slimefun
     * as a required or optional dependency.
     * <p>
     * We will just assume this to be a list of our addons.
     *
     * @return A {@link Set} of every {@link Plugin} that is dependent on Slimefun
     */
    public static @Nonnull Set<Plugin> getInstalledAddons() {
        // 确保插件实例存在喵~
        validateInstance();
        // 获取本插件的名称，用于在其他插件的依赖列表中匹配喵~
        String pluginName = instance.getName();

        // @formatter:off - Collect any Plugin that (soft)-depends on Slimefun
        // 遍历服务器所有已加载的插件，筛选出声明依赖(depend或softdepend) Slimefun 的插件喵~
        // 主人注意：这里流式遍历服务器所有插件，插件数量很多时效率还好，但不建议高频调用喵~
        return Arrays.stream(instance.getServer().getPluginManager().getPlugins())
                .filter(plugin -> {
                    // 读取每个插件的描述文件，检查是否包含对 Slimefun 的依赖声明喵~
                    PluginDescriptionFile description = plugin.getDescription();
                    // depend：硬依赖(必须依赖)；softdepend：软依赖(可选依赖)，两者都算附加包喵~
                    return description.getDepend().contains(pluginName)
                            || description.getSoftDepend().contains(pluginName);
                })
                // 将过滤结果收集到 Set 中去重喵~
                .collect(Collectors.toSet());
        // @formatter:on
    }

    /**
     * This method schedules a delayed synchronous task for Slimefun.
     * <strong>For Slimefun only, not for addons.</strong>
     *
     * This method should only be invoked by Slimefun itself.
     * Addons must schedule their own tasks using their own {@link Plugin} instance.
     *
     * @param runnable
     *            The {@link Runnable} to run
     * @param delay
     *            The delay for this task
     *
     * @return The resulting {@link BukkitTask} or null if Slimefun was disabled
     */
    public static @Nullable BukkitTask runSync(@Nonnull Runnable runnable, long delay) {
        // 喵~防御：runnable为null时立即抛出异常，避免调度空任务喵~
        Validate.notNull(runnable, "Cannot run null");
        // 喵~防御：delay为负数时立即抛出异常，Bukkit调度器不接受负延迟喵~
        Validate.isTrue(delay >= 0, "The delay cannot be negative");

        // 单元测试环境下直接同步执行，不依赖 Bukkit 调度器喵~
        if (getMinecraftVersion() == MinecraftVersion.UNIT_TEST) {
            runnable.run();
            return null;
        }

        // 喵~防御：插件实例为空或已被禁用时不调度任务，返回null防止崩溃喵~
        if (instance == null || !instance.isEnabled()) {
            return null;
        }

        // 通过 Bukkit 调度器在指定延迟后在主线程执行 runnable 喵~
        return instance.getServer().getScheduler().runTaskLater(instance, runnable, delay);
    }

    /**
     * This method schedules a synchronous task for Slimefun.
     * <strong>For Slimefun only, not for addons.</strong>
     *
     * This method should only be invoked by Slimefun itself.
     * Addons must schedule their own tasks using their own {@link Plugin} instance.
     *
     * @param runnable
     *            The {@link Runnable} to run
     *
     * @return The resulting {@link BukkitTask} or null if Slimefun was disabled
     */
    public static @Nullable BukkitTask runSync(@Nonnull Runnable runnable) {
        // 喵~防御：runnable为null时立即抛出异常，不允许调度空任务喵~
        Validate.notNull(runnable, "Cannot run null");

        // 单元测试环境下直接同步执行，不依赖 Bukkit 调度器喵~
        if (getMinecraftVersion() == MinecraftVersion.UNIT_TEST) {
            runnable.run();
            return null;
        }

        // 喵~防御：插件实例为空或已禁用时不调度，返回null安全退出喵~
        if (instance == null || !instance.isEnabled()) {
            return null;
        }

        // 在下一个服务器 tick 于主线程执行 runnable 喵~
        return instance.getServer().getScheduler().runTask(instance, runnable);
    }

    // 返回此插件对应的 jar 文件对象，供更新服务等组件获取插件文件路径喵~
    @Nonnull
    public File getFile() {
        return super.getFile();
    }

    // 返回玩家聊天输入捕获器，用于捕获玩家在聊天框输入的内容喵~
    public static @Nonnull PlayerChatCatcher getChatCatcher() {
        // 喵~防御：实例未初始化时抛出异常，避免空指针喵~
        validateInstance();
        return instance.chatCatcher;
    }

    /**
     * This method returns the {@link ThreadService} of Slimefun.
     * <b>Do not use this if you're an addon. Please make your own {@link ThreadService}.</b>
     *
     * @return The {@link ThreadService} for Slimefun
     */
    // 返回线程服务，用于管理 Slimefun 内部的异步/定时线程池，附加包请勿调用本方法喵~
    public static @Nonnull ThreadService getThreadService() {
        // 喵~防御：直接通过 instance() 获取，instance() 返回 null 时会触发 NPE，与其他方法保持一致喵~
        return instance().threadService;
    }
}
