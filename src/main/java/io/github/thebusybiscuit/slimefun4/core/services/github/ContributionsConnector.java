package io.github.thebusybiscuit.slimefun4.core.services.github;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;

// 贡献者连接器类，负责从 GitHub API 拉取仓库贡献者列表并注册到 GitHubService 中喵~
class ContributionsConnector extends GitHubConnector {

    /**
     * GitHub Bots that do not count as Contributors
     * (includes "invalid-email-address" because it is an invalid contributor)
     */
    // 存放需要忽略的账号名称列表，包括 Bot 账号和无效邮件地址账号喵~
    private final List<String> ignoredAccounts = new ArrayList<>();

    /**
     * Matches a GitHub name with a Minecraft name.
     */
    // GitHub 用户名 -> Minecraft 用户名的映射表，用于处理两边名字不一致的特殊贡献者喵~
    private final Map<String, String> aliases = new HashMap<>();

    // 缓存文件名前缀，用于区分不同仓库/角色的缓存文件喵~
    private final String prefix;
    // 贡献者角色 ID 字符串，例如 "developer" 或 "translator" 等喵~
    private final String role;
    // GitHub API 分页页码，用于拉取超过单页上限的贡献者数据喵~
    private final int page;

    // 标记此连接器是否已完成任务（成功或失败都算完成）喵~
    private boolean finished = false;

    /*
     * 整体思路：
     *   构造函数接收 GitHubService 实例、文件名前缀、分页页码、仓库名和贡献者角色，
     *   通过父类 GitHubConnector 绑定仓库，然后调用 loadConfiguration() 初始化忽略列表和别名映射喵~
     * 输入：github（服务实例）、prefix（文件前缀）、page（分页页码）、repository（仓库名）、role（贡献者角色枚举）
     * 边界条件：所有参数均标注 @ParametersAreNonnullByDefault，不允许传 null喵~
     */
    @ParametersAreNonnullByDefault
    ContributionsConnector(GitHubService github, String prefix, int page, String repository, ContributorRole role) {
        // 调用父类构造函数，将 github 服务实例和仓库名传入 GitHubConnector喵~
        super(github, repository);

        // 保存文件名前缀，后续 getFileName() 会用到喵~
        this.prefix = prefix;
        // 保存分页页码，后续 getParameters() 会用到喵~
        this.page = page;
        // 从枚举中取出角色 ID 字符串并保存，后续注册贡献者时会用到喵~
        this.role = role.getId();

        // 初始化忽略账号列表和别名映射喵~
        loadConfiguration();
    }

    /**
     * This method loads all aliases.
     * This mapping matches a GitHub username with a Minecraft username.
     * These people are... "special cases".
     */
    /*
     * 整体思路：
     *   依次将已知的 Bot 账号和无效账号加入 ignoredAccounts 忽略列表，
     *   再将 GitHub 用户名与对应 Minecraft 用户名的映射关系存入 aliases喵~
     * 输入：无（直接操作类字段）
     * 边界条件：此方法只应在构造时调用一次，重复调用会重复添加条目（但对逻辑无影响，只是冗余）喵~
     */
    private void loadConfiguration() {
        // 以下为需要忽略的 Bot 账号或无效账号，不计入贡献者统计喵~
        // 添加无效邮件地址占位账号到忽略列表喵~
        ignoredAccounts.add("invalid-email-address");
        // 添加 renovate 自动化依赖更新 Bot 账号喵~
        ignoredAccounts.add("renovate");
        // 添加 renovate-bot 的另一种账号形式喵~
        ignoredAccounts.add("renovate-bot");
        // 添加 renovate 的 GitHub App 账号形式喵~
        ignoredAccounts.add("renovate[bot]");
        // 添加项目自用 Bot 账号 TheBusyBot喵~
        ignoredAccounts.add("TheBusyBot");
        // 添加图片压缩 Bot ImgBotApp喵~
        ignoredAccounts.add("ImgBotApp");
        // 添加 imgbot 的另一种小写账号形式喵~
        ignoredAccounts.add("imgbot");
        // 添加 imgbot 的 GitHub App 账号形式喵~
        ignoredAccounts.add("imgbot[bot]");
        // 添加 GitHub Actions 自动化 Bot 账号喵~
        ignoredAccounts.add("github-actions[bot]");
        // 添加 GitLocalize 翻译服务账号喵~
        ignoredAccounts.add("gitlocalize-app");
        // 添加 GitLocalize 的 GitHub App 账号形式喵~
        ignoredAccounts.add("gitlocalize-app[bot]");
        // 添加 mt-gitlocalize 翻译 Bot 账号喵~
        ignoredAccounts.add("mt-gitlocalize");

        // 以下为 GitHub 用户名 -> Minecraft 用户名的别名映射，处理两边名字不同的特殊情况喵~
        // WalshyDev 的 Minecraft 名是 HumanRightsAct喵~
        aliases.put("WalshyDev", "HumanRightsAct");
        // J3fftw1 的 Minecraft 名是 _lagpc_喵~
        aliases.put("J3fftw1", "_lagpc_");
        // ajan-12 的 Minecraft 名是 ajan_12（连字符换成下划线）喵~
        aliases.put("ajan-12", "ajan_12");
        // mrcoffee1026 的 Minecraft 名是 mr_coffee1026喵~
        aliases.put("mrcoffee1026", "mr_coffee1026");
        // Cyber-MC 的 Minecraft 名是 CyberPatriot喵~
        aliases.put("Cyber-MC", "CyberPatriot");
        // BurningBrimstone 的 Minecraft 名是 Bluedevil74喵~
        aliases.put("BurningBrimstone", "Bluedevil74");
        // bverhoeven 的 Minecraft 名是 soczol喵~
        aliases.put("bverhoeven", "soczol");
        // ramdon-person 的 Minecraft 名是 ramdon_person（连字符换成下划线）喵~
        aliases.put("ramdon-person", "ramdon_person");
        // NCBPFluffyBear 的 Minecraft 名是 FluffyBear_喵~
        aliases.put("NCBPFluffyBear", "FluffyBear_");
        // martinbrom 的 Minecraft 名是 OneTime97喵~
        aliases.put("martinbrom", "OneTime97");
        // LilBC 的 Minecraft 名是 Lil_BC喵~
        aliases.put("LilBC", "Lil_BC");
        // st392 的 Minecraft 名是 BlueWood喵~
        aliases.put("st392", "BlueWood");
    }

    /**
     * This returns whether this {@link ContributionsConnector} has finished its task.
     *
     * @return Whether it is finished
     */
    // 返回此连接器是否已完成任务，外部可轮询此方法等待结果喵~
    public boolean hasFinished() {
        return finished;
    }

    /*
     * 整体思路：
     *   当 GitHub API 请求成功时调用此方法，将 finished 标记为 true，
     *   然后检查响应是否为 JSON 数组，是则解析贡献者数据，
     *   否则记录 WARNING 日志提示响应异常（可能是超时或 API 返回了错误格式）喵~
     * 输入：response（GitHub API 返回的 JSON 元素，不为 null）
     * 边界条件：response 可能是 JsonObject（如错误响应）而非 JsonArray，需要类型判断喵~
     */
    @Override
    public void onSuccess(@Nonnull JsonElement response) {
        // 标记任务已完成（无论解析是否成功都算完成）喵~
        finished = true;

        // 喵~防御：检查响应是否为 JSON 数组，API 超时或错误时可能返回 JsonObject 而非 JsonArray喵~
        if (response.isJsonArray()) {
            // 响应格式正确，解析贡献者列表喵~
            computeContributors(response.getAsJsonArray());
        } else {
            // 响应格式异常，记录警告日志并附上原始响应内容方便排查喵~
            Slimefun.logger()
                    .log(Level.WARNING, "Received an unusual answer from GitHub, possibly a timeout? ({0})", response);
        }
    }

    // 当 GitHub API 请求失败时调用，仅将 finished 标记为 true 表示任务结束（失败也算结束）喵~
    @Override
    public void onFailure() {
        // 标记任务已完成（失败状态，不做任何数据处理）喵~
        finished = true;
    }

    // 返回缓存文件名，格式为 "前缀_contributors"，用于本地缓存 GitHub 响应数据喵~
    @Override
    public String getFileName() {
        // 拼接前缀与固定后缀 "_contributors" 生成缓存文件名喵~
        return prefix + "_contributors";
    }

    // 返回 GitHub API 的请求端点路径，固定为 "/contributors"喵~
    @Override
    public String getEndpoint() {
        return "/contributors";
    }

    /*
     * 整体思路：
     *   构建 GitHub API 的查询参数 Map，指定每页返回 100 条数据（API 最大值）和当前请求的页码，
     *   用于支持贡献者数量超过 100 人时的分页拉取喵~
     * 输入：无
     * 输出：包含 "per_page" 和 "page" 两个参数的 Map喵~
     */
    @Override
    public Map<String, Object> getParameters() {
        // 创建查询参数 Map，用于拼接到 GitHub API 请求 URL喵~
        Map<String, Object> parameters = new HashMap<>();
        // 每页最多返回 100 条贡献者数据（GitHub API 允许的最大值）喵~
        parameters.put("per_page", 100);
        // 指定当前请求的分页页码，支持超过 100 人的大仓库喵~
        parameters.put("page", page);
        return parameters;
    }

    /*
     * 整体思路：
     *   遍历 GitHub API 返回的贡献者 JSON 数组，逐条提取用户名、贡献次数和主页链接，
     *   跳过 ignoredAccounts 中的 Bot 账号，对 aliases 中有映射的用户转换为 Minecraft 用户名，
     *   最后调用 github.addContributor() 将其注册到 GitHubService 中喵~
     * 输入：array（GitHub API /contributors 端点返回的 JSON 数组，不为 null）
     * 边界条件：数组可能为空（仓库无贡献者），忽略列表中的账号会被跳过不注册喵~
     */
    // 主人注意：此方法遍历贡献者数组，当贡献者数量极多（接近 100 条上限）时循环次数较多，但通常不会造成性能问题喵~
    private void computeContributors(@Nonnull JsonArray array) {
        // 遍历 JSON 数组中的每一个贡献者元素喵~
        for (JsonElement element : array) {
            // 将当前 JSON 元素转为 JsonObject，以便按字段名读取数据喵~
            JsonObject object = element.getAsJsonObject();

            // 从 JSON 对象中读取 GitHub 用户名（login 字段）喵~
            String name = object.get("login").getAsString();
            // 从 JSON 对象中读取该用户的总提交次数（contributions 字段）喵~
            int commits = object.get("contributions").getAsInt();
            // 从 JSON 对象中读取该用户的 GitHub 主页链接（html_url 字段）喵~
            String profile = object.get("html_url").getAsString();

            // 喵~防御：跳过 ignoredAccounts 中的 Bot 账号和无效账号，避免将它们误注册为贡献者喵~
            if (!ignoredAccounts.contains(name)) {
                // 查找 aliases 映射表中是否有对应的 Minecraft 用户名，没有则使用原 GitHub 用户名喵~
                String username = aliases.getOrDefault(name, name);
                // 将贡献者信息（Minecraft 名、主页、角色、提交数）注册到 GitHubService喵~
                github.addContributor(username, profile, role, commits);
            }
        }
    }
}
