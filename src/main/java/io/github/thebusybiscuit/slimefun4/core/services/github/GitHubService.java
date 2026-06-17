package io.github.thebusybiscuit.slimefun4.core.services.github;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.HeadTexture;
import java.io.File;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;

/**
 * This Service is responsible for grabbing every {@link Contributor} to this project
 * from GitHub and holding data associated to the project repository, such
 * as open issues or pending pull requests.
 *
 * @author TheBusyBiscuit
 *
 */
// GitHub服务类：负责从GitHub抓取所有贡献者信息，并保存仓库相关数据（如Issue数量、PR数量等）喵~
public class GitHubService {

    // 仓库标识符，格式为 "用户名/仓库名"，例如 "Slimefun/Slimefun4" 喵~
    private final String repository;
    // 存储所有GitHub连接器的集合，每个连接器负责从GitHub获取一类数据喵~
    private final Set<GitHubConnector> connectors;
    // 线程安全的贡献者Map，键为用户名，值为Contributor对象，支持多线程并发读写喵~
    private final ConcurrentMap<String, Contributor> contributors;

    // UUID缓存配置文件，将贡献者的Minecraft UUID持久化保存到本地，避免每次都请求API喵~
    private final Config uuidCache = new Config("plugins/Slimefun/cache/github/uuids.yml");
    // 皮肤纹理缓存配置文件，将贡献者头像纹理保存到本地，减少对外部API的请求次数喵~
    private final Config texturesCache = new Config("plugins/Slimefun/cache/github/skins.yml");

    // 是否启用日志输出的开关，默认关闭，避免刷屏喵~
    private boolean logging = false;

    // 最后一次GitHub更新时间，初始化为当前时间，后续由定时任务更新喵~
    private LocalDateTime lastUpdate = LocalDateTime.now();

    // 仓库当前开放的Issue数量，由GitHubIssuesConnector异步更新喵~
    private int openIssues = 0;
    // 仓库待合并的Pull Request数量，由GitHubIssuesConnector异步更新喵~
    private int pendingPullRequests = 0;
    // 仓库的公开Fork数量，由GitHubActivityConnector异步更新喵~
    private int publicForks = 0;
    // 仓库的Star收藏数量，由GitHubActivityConnector异步更新喵~
    private int stargazers = 0;

    /**
     * This creates a new {@link GitHubService} for the given repository.
     *
     * @param repository
     *            The repository to create this {@link GitHubService} for
     */
    /*
     * 构造方法整体思路：
     * 输入：仓库名称字符串（格式如 "Slimefun/Slimefun4"）
     * 输出：初始化完毕的GitHubService实例
     * 边界条件：repository不能为null，否则后续HTTP请求会出错喵~
     */
    // 创建一个新的GitHubService实例，绑定到指定的GitHub仓库喵~
    public GitHubService(@Nonnull String repository) {
        // 保存仓库标识符到成员变量，供后续各连接器使用喵~
        this.repository = repository;

        // 初始化连接器集合为普通HashSet，后续在loadConnectors中填充喵~
        connectors = new HashSet<>();
        // 初始化贡献者Map为线程安全的ConcurrentHashMap，支持异步Task并发写入喵~
        contributors = new ConcurrentHashMap<>();
    }

    /**
     * This will start the {@link GitHubService} and run the asynchronous {@link GitHubTask}
     * every so often to update its data.
     *
     * @param plugin
     *            Our instance of {@link Slimefun}
     */
    /*
     * start方法整体思路：
     * 1. 先加载所有连接器和默认贡献者
     * 2. 创建GitHubTask异步任务对象
     * 3. 通过Bukkit调度器每1小时执行一次异步更新，延迟30秒后首次执行
     * 输入：Slimefun插件主实例
     * 输出：无（副作用：启动周期性异步任务）
     * 边界条件：plugin不能为null喵~
     */
    // 启动GitHub服务，注册定时异步任务来周期性刷新贡献者和仓库数据喵~
    public void start(@Nonnull Slimefun plugin) {
        // 加载所有GitHub连接器，不开启日志模式喵~
        loadConnectors(false);

        // 将1小时转换为毫秒作为任务重复执行的间隔周期喵~
        long period = TimeUnit.HOURS.toMillis(1);
        // 创建GitHubTask任务对象，封装了向GitHub API发起请求的逻辑喵~
        GitHubTask task = new GitHubTask(this);

        // 注册异步定时任务：延迟30秒（30*20 ticks）后首次执行，之后每1小时执行一次喵~
        plugin.getServer().getScheduler().runTaskTimerAsynchronously(plugin, task, 30 * 20L, period);
    }

    /**
     * This method adds a few default {@link Contributor Contributors}.
     * Think of them like honorable mentions that aren't listed through
     * the usual methods.
     */
    /*
     * addDefaultContributors方法整体思路：
     * 手动添加一些无法通过GitHub API自动抓取的特殊贡献者（如美术师、翻译者、活动获奖者）
     * 输入：无
     * 输出：无（副作用：向contributors Map中添加条目）
     * 边界条件：TranslatorsReader加载失败时记录SEVERE日志但不中断启动流程喵~
     */
    // 添加默认贡献者（特殊荣誉提名，不在GitHub代码贡献列表中）喵~
    private void addDefaultContributors() {
        // Artists
        // 添加美术师贡献者 Fuffles_，角色标签为粉色"Artist"喵~
        addContributor("Fuffles_", "&dArtist");
        // 添加美术师贡献者 IMS_Art，提供其GitHub主页链接，提交数为0喵~
        addContributor("IMS_Art", "https://github.com/IAmSorryArt", "&dArtist", 0);

        // Addon Jam winners
        // 添加2020年Addon Jam活动获奖者 nahkd123喵~
        addContributor("nahkd123", "&aWinner of the 2020 Addon Jam");

        // Translators
        try {
            // 创建翻译者读取器，从translators.json文件中加载所有翻译贡献者喵~
            TranslatorsReader translators = new TranslatorsReader(this);
            // 执行加载逻辑，将翻译者添加到贡献者Map中喵~
            translators.load();
        } catch (Exception x) {
            // 喵~防御：捕获所有异常，translators.json格式错误或文件缺失时记录错误日志但不崩溃喵~
            Slimefun.logger().log(Level.SEVERE, "Failed to read 'translators.json'", x);
        }
    }

    // 私有辅助方法：仅用名称和角色添加贡献者，UUID从本地缓存中读取，提交数默认为0喵~
    private void addContributor(@Nonnull String name, @Nonnull String role) {
        // 根据名称创建新的Contributor对象喵~
        Contributor contributor = new Contributor(name);
        // 设置该贡献者的角色和提交数（0表示特殊贡献，非代码提交）喵~
        contributor.setContributions(role, 0);
        // 从本地UUID缓存文件中读取该用户名对应的Minecraft UUID，避免重复API请求喵~
        contributor.setUniqueId(uuidCache.getUUID(name));
        // 将贡献者以名称为键放入线程安全的Map中喵~
        contributors.put(name, contributor);
    }

    /*
     * addContributor（完整参数版）整体思路：
     * 通过GitHub主页URL解析用户名，然后计算或复用已有的Contributor对象，
     * 再设置该贡献者的角色和提交数，以及从缓存读取其UUID
     * 输入：Minecraft用户名、GitHub主页URL、角色描述、提交数
     * 输出：添加/更新后的Contributor对象
     * 边界条件：
     *   - minecraftName/profileURL/role 不能为null（Validate会抛出异常）
     *   - commits 不能为负数
     *   - profileURL 必须包含"/"，否则substring会抛出StringIndexOutOfBoundsException喵~
     */
    // 公开方法：根据Minecraft用户名、GitHub主页URL、角色和提交数添加或更新贡献者喵~
    public @Nonnull Contributor addContributor(
            @Nonnull String minecraftName, @Nonnull String profileURL, @Nonnull String role, int commits) {
        // 喵~防御：校验minecraftName不为null，否则后续操作无法正确创建Contributor喵~
        Validate.notNull(minecraftName, "Minecraft username must not be null.");
        // 喵~防御：校验profileURL不为null，否则无法解析GitHub用户名喵~
        Validate.notNull(profileURL, "GitHub profile url must not be null.");
        // 喵~防御：校验role不为null，保证贡献者角色信息完整喵~
        Validate.notNull(role, "Role should not be null.");
        // 喵~防御：校验提交数不为负数，负数在业务上没有意义喵~
        Validate.isTrue(commits >= 0, "Commit count cannot be negative.");

        // 从GitHub主页URL中截取最后一段作为GitHub用户名，例如 "https://github.com/TheBusyBiscuit" → "TheBusyBiscuit" 喵~
        String username = profileURL.substring(profileURL.lastIndexOf('/') + 1);

        // 如果该GitHub用户名已存在于Map中则复用，否则用Minecraft名+URL创建新Contributor喵~
        Contributor contributor =
                contributors.computeIfAbsent(username, key -> new Contributor(minecraftName, profileURL));
        // 设置该贡献者的角色和代码提交次数喵~
        contributor.setContributions(role, commits);
        // 从本地UUID缓存中读取Minecraft用户名对应的UUID，减少对Mojang API的请求喵~
        contributor.setUniqueId(uuidCache.getUUID(minecraftName));
        // 返回添加或更新后的贡献者对象，方便调用方链式操作喵~
        return contributor;
    }

    /*
     * addContributor（仅用户名版）整体思路：
     * 与完整参数版类似，但没有GitHub主页URL，仅使用用户名创建Contributor
     * 输入：用户名、角色描述、提交数
     * 输出：添加/更新后的Contributor对象
     * 边界条件：username/role不能为null，commits不能为负数喵~
     */
    // 公开方法：仅用用户名、角色和提交数添加或更新贡献者（没有GitHub主页URL）喵~
    public @Nonnull Contributor addContributor(@Nonnull String username, @Nonnull String role, int commits) {
        // 喵~防御：校验username不为null，否则无法作为Map的key喵~
        Validate.notNull(username, "Username must not be null.");
        // 喵~防御：校验role不为null，保证角色信息完整喵~
        Validate.notNull(role, "Role should not be null.");
        // 喵~防御：校验提交数不为负数喵~
        Validate.isTrue(commits >= 0, "Commit count cannot be negative.");

        // 如果用户名已在Map中则复用，否则创建新的仅含用户名的Contributor对象喵~
        Contributor contributor = contributors.computeIfAbsent(username, key -> new Contributor(username));
        // 设置角色和提交数喵~
        contributor.setContributions(role, commits);
        // 返回贡献者对象喵~
        return contributor;
    }

    /*
     * loadConnectors方法整体思路：
     * 初始化所有GitHubConnector，每个连接器对应一类GitHub API请求：
     *   - ContributionsConnector：抓取代码/Wiki/资源包贡献者列表（分页，每页最多一个Connector）
     *   - GitHubIssuesConnector：抓取开放Issue数和PR数
     *   - GitHubActivityConnector：抓取Fork数、Star数和最新提交日期
     * 输入：是否开启日志
     * 输出：无（副作用：填充connectors集合）
     * 边界条件：代码贡献者分3页抓取，如果超过3页的贡献者将无法被记录喵~
     */
    // 加载所有GitHub数据连接器，并预先添加默认贡献者喵~
    private void loadConnectors(boolean logging) {
        // 保存日志开关状态到成员变量，供连接器调用时判断喵~
        this.logging = logging;
        // 先添加美术师、获奖者和翻译者等默认贡献者喵~
        addDefaultContributors();

        // TheBusyBiscuit/Slimefun4 (multiple times because there may me multiple pages)
        // 添加第1页代码贡献者连接器，抓取GitHub代码贡献列表第1页喵~
        connectors.add(new ContributionsConnector(this, "code", 1, repository, ContributorRole.DEVELOPER));
        // 添加第2页代码贡献者连接器喵~
        connectors.add(new ContributionsConnector(this, "code2", 2, repository, ContributorRole.DEVELOPER));
        // 添加第3页代码贡献者连接器喵~
        connectors.add(new ContributionsConnector(this, "code3", 3, repository, ContributorRole.DEVELOPER));

        // TheBusyBiscuit/Slimefun4-Wiki
        // 添加Wiki文档贡献者连接器，抓取Slimefun/Wiki仓库的贡献者第1页喵~
        connectors.add(new ContributionsConnector(this, "wiki", 1, "Slimefun/Wiki", ContributorRole.WIKI_EDITOR));

        // TheBusyBiscuit/Slimefun4-Resourcepack
        // 添加资源包贡献者连接器，抓取Slimefun/Resourcepack仓库的贡献者第1页喵~
        connectors.add(new ContributionsConnector(
                this, "resourcepack", 1, "Slimefun/Resourcepack", ContributorRole.RESOURCEPACK_ARTIST));

        // Issues and Pull Requests
        // 添加Issue和PR数量连接器，回调lambda将抓取到的数据更新到成员变量喵~
        connectors.add(new GitHubIssuesConnector(this, repository, (issues, pullRequests) -> {
            // 将抓取到的开放Issue数赋值给成员变量喵~
            this.openIssues = issues;
            // 将抓取到的待合并PR数赋值给成员变量喵~
            this.pendingPullRequests = pullRequests;
        }));

        // Forks, star count and last commit date
        // 添加仓库活跃度连接器，回调lambda将Fork数、Star数和最新提交日期更新到成员变量喵~
        connectors.add(new GitHubActivityConnector(this, repository, (forks, stars, date) -> {
            // 将抓取到的公开Fork数量赋值给成员变量喵~
            this.publicForks = forks;
            // 将抓取到的Star数量赋值给成员变量喵~
            this.stargazers = stars;
            // 将最新提交日期赋值给成员变量，用于展示最后更新时间喵~
            this.lastUpdate = date;
        }));
    }

    // 包内可见方法：返回所有已注册的GitHub连接器集合，供GitHubTask遍历执行喵~
    protected @Nonnull Set<GitHubConnector> getConnectors() {
        return connectors;
    }

    // 包内可见方法：返回当前是否启用了日志输出，供各连接器判断是否打印调试信息喵~
    protected boolean isLoggingEnabled() {
        return logging;
    }

    /**
     * This returns the {@link Contributor Contributors} to this project.
     *
     * @return A {@link ConcurrentMap} containing all {@link Contributor Contributors}
     */
    // 返回所有贡献者的线程安全Map，键为GitHub用户名，值为Contributor对象喵~
    public @Nonnull ConcurrentMap<String, Contributor> getContributors() {
        return contributors;
    }

    /**
     * This returns the amount of forks of our repository
     *
     * @return The amount of forks
     */
    // 返回仓库的公开Fork数量，数据由GitHubActivityConnector异步更新喵~
    public int getForks() {
        return publicForks;
    }

    /**
     * This method returns the amount of stargazers of the repository.
     *
     * @return The amount of people who starred the repository
     */
    // 返回仓库的Star收藏数量，数据由GitHubActivityConnector异步更新喵~
    public int getStars() {
        return stargazers;
    }

    /**
     * This returns the amount of open Issues on our repository.
     *
     * @return The amount of open issues
     */
    // 返回仓库当前开放的Issue数量，数据由GitHubIssuesConnector异步更新喵~
    public int getOpenIssues() {
        return openIssues;
    }

    /**
     * Returns the id of Slimefun's GitHub Repository. (e.g. "Slimefun/Slimefun4").
     *
     * @return The id of our GitHub Repository
     */
    // 返回该服务绑定的GitHub仓库标识符，格式为 "用户名/仓库名" 喵~
    public @Nonnull String getRepository() {
        return repository;
    }

    /**
     * This method returns the amount of pending pull requests.
     *
     * @return The amount of pending pull requests
     */
    // 返回仓库当前待合并的Pull Request数量，数据由GitHubIssuesConnector异步更新喵~
    public int getPendingPullRequests() {
        return pendingPullRequests;
    }

    /**
     * This returns the date and time of the last commit to this repository.
     *
     * @return A {@link LocalDateTime} object representing the date and time of the latest commit
     */
    // 返回仓库最后一次提交的日期和时间，数据由GitHubActivityConnector异步更新喵~
    public @Nonnull LocalDateTime getLastUpdate() {
        return lastUpdate;
    }

    /**
     * This will store the {@link UUID} and texture of all {@link Contributor Contributors}
     * in memory in a {@link File} to save requests the next time we iterate over them.
     */
    /*
     * saveCache方法整体思路：
     * 遍历所有贡献者，将他们的Minecraft UUID和头颅皮肤纹理分别写入本地yml缓存文件，
     * 以便下次服务器启动时直接读取缓存而无需重新请求Mojang/GitHub API
     * 输入：无
     * 输出：无（副作用：更新并保存uuidCache和texturesCache到磁盘）
     * 边界条件：
     *   - uuid为Optional，可能为空，需用ifPresent保护
     *   - 纹理可能是UNKNOWN占位符，此类不应写入缓存
     * 主人注意：contributors Map较大时遍历可能有轻微开销，但通常贡献者数量可控，不影响性能喵~
     */
    // 将所有贡献者的UUID和皮肤纹理缓存持久化保存到本地yml文件中喵~
    protected void saveCache() {
        // 遍历所有贡献者条目，逐一提取并保存UUID和纹理信息喵~
        for (Contributor contributor : contributors.values()) {
            // 获取贡献者的Minecraft UUID（Optional包装，可能不存在）喵~
            Optional<UUID> uuid = contributor.getUniqueId();
            // 喵~防御：uuid存在时才写入缓存，避免将null存入yml文件导致读取报错喵~
            uuid.ifPresent(value -> uuidCache.setValue(contributor.getName(), value));

            // 检查该贡献者是否已有皮肤纹理数据喵~
            if (contributor.hasTexture()) {
                // 获取贡献者当前的头颅皮肤Base64纹理字符串喵~
                String texture = contributor.getTexture(this);

                // 喵~防御：过滤掉UNKNOWN占位纹理，避免将无效纹理写入缓存文件喵~
                if (!texture.equals(HeadTexture.UNKNOWN.getTexture())) {
                    // 将有效纹理以贡献者名称为键写入纹理缓存喵~
                    texturesCache.setValue(contributor.getName(), texture);
                }
            }
        }

        // 将UUID缓存数据写入磁盘，保存到 plugins/Slimefun/cache/github/uuids.yml 喵~
        uuidCache.save();
        // 将皮肤纹理缓存数据写入磁盘，保存到 plugins/Slimefun/cache/github/skins.yml 喵~
        texturesCache.save();
    }

    /**
     * This returns the cached skin texture for a given username.
     *
     * @param username
     *            The minecraft username
     *
     * @return The cached skin texture for that user (or null)
     */
    // 包内可见方法：从本地缓存文件中查询指定Minecraft用户名对应的皮肤纹理，未缓存时返回null喵~
    protected @Nullable String getCachedTexture(@Nonnull String username) {
        // 从texturesCache中按用户名查询已保存的Base64皮肤纹理字符串喵~
        return texturesCache.getString(username);
    }
}
