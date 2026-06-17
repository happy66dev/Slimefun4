package io.github.thebusybiscuit.slimefun4.core.services.localization;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.bakedlibs.dough.config.Config;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.api.SlimefunBranch;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.services.LocalizationService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

/**
 * This is an abstract parent class of {@link LocalizationService}.
 * There is not really much more I can say besides that...
 *
 * @author TheBusyBiscuit
 *
 * @see LocalizationService
 *
 */
// Slimefun 本地化服务的抽象基类，实现了 Keyed 接口，提供多语言消息读取与发送的通用方法喵~
public abstract class SlimefunLocalization implements Keyed {

    // 默认配置文件，对应 messages.yml，用于读取消息前缀等全局配置喵~
    private final Config defaultConfig;

    // 构造方法：传入 Slimefun 插件实例，初始化默认消息配置文件 messages.yml 喵~
    protected SlimefunLocalization(@Nonnull Slimefun plugin) {
        // 从插件目录加载 messages.yml 作为默认配置文件喵~
        this.defaultConfig = new Config(plugin, "messages.yml");
    }

    // 获取默认配置文件对象，供子类读取 messages.yml 中的原始配置喵~
    protected @Nonnull Config getConfig() {
        return defaultConfig;
    }

    /**
     * Saves this Localization to its File
     */
    // 将默认配置（messages.yml）保存到磁盘，持久化任何内存中的修改喵~
    protected void save() {
        defaultConfig.save();
    }

    /**
     * This returns the chat prefix for our messages.
     * Every message (unless explicitly omitted) will have this
     * prefix prepended.
     *
     * @return The chat prefix
     */
    // 获取聊天消息前缀，所有发送给玩家的消息默认都会加上这个前缀喵~
    public @Nonnull String getChatPrefix() {
        // 从 messages.yml 读取键为 "prefix" 的消息作为聊天前缀喵~
        return getMessage("prefix");
    }

    /**
     * This method attempts to return the {@link Language} with the given
     * language code.
     *
     * @param id
     *            The language code
     *
     * @return A {@link Language} with the given id or null
     */
    // 抽象方法：根据语言代码（如 "en"、"zh-CN"）查找对应的 Language 对象，找不到返回 null 喵~
    public abstract @Nullable Language getLanguage(@Nonnull String id);

    /**
     * This method returns the currently selected {@link Language} of a {@link Player}.
     *
     * @param p
     *            The {@link Player} to query
     *
     * @return The {@link Language} that was selected by the given {@link Player}
     */
    // 抽象方法：获取指定玩家当前选择的语言，用于个性化多语言显示喵~
    public abstract @Nullable Language getLanguage(@Nonnull Player p);

    /**
     * This method returns the default {@link Language} of this {@link Server}
     *
     * @return The default {@link Language}
     */
    // 抽象方法：获取服务器配置的默认语言，当玩家没有单独语言设置时使用喵~
    public abstract @Nullable Language getDefaultLanguage();

    /**
     * This returns whether a {@link Language} with the given id exists within
     * the project resources.
     *
     * @param id
     *            The {@link Language} id
     *
     * @return Whether the project contains a {@link Language} with that id
     */
    // 抽象方法：检查插件资源包中是否存在指定 id 的语言文件（如 "zh-CN.yml"）喵~
    protected abstract boolean hasLanguage(@Nonnull String id);

    /**
     * This method returns a full {@link Collection} of every {@link Language} that was
     * found.
     *
     * @return A {@link Collection} that contains every installed {@link Language}
     */
    // 抽象方法：返回当前已加载的所有 Language 对象集合喵~
    public abstract @Nonnull Collection<Language> getLanguages();

    /**
     * This method adds a new {@link Language} with the given id and texture.
     *
     * @param id
     *            The {@link Language} id
     * @param texture
     *            The texture of how this {@link Language} should be displayed
     */
    // 抽象方法：向已加载语言列表中注册一种新语言，texture 是它在语言选择界面中的图标纹理喵~
    protected abstract void addLanguage(@Nonnull String id, @Nonnull String texture);

    /**
     * This will load every {@link LanguagePreset} into memory.
     * To be precise: It performs {@link #addLanguage(String, String)} for every
     * value of {@link LanguagePreset}.
     */
    /*
     * 整体思路：遍历所有预设语言枚举值，根据发布状态或分支决定是否加载喵~
     * 输入：LanguagePreset 枚举全部值（如英语、中文等）
     * 输出：将符合条件的语言注册进系统
     * 边界条件：非稳定分支（如开发版）会加载尚未完成的语言喵~
     */
    // 将所有内置语言预设加载进内存，稳定版只加载已准备好发布的语言喵~
    protected void loadEmbeddedLanguages() {
        // 遍历所有 LanguagePreset 枚举值，逐个检查是否需要加载喵~
        for (LanguagePreset lang : LanguagePreset.values()) {
            // 如果该语言已准备好发布，或者当前不是稳定分支（开发版允许加载未完成语言），则注册喵~
            if (lang.isReadyForRelease() || Slimefun.getUpdater().getBranch() != SlimefunBranch.STABLE) {
                // 调用抽象方法将该语言的代码和图标纹理注册进语言系统喵~
                addLanguage(lang.getLanguageCode(), lang.getTexture());
            }
        }
    }

    /*
     * 整体思路：获取英语作为兜底回退语言文件，当目标语言缺少翻译时使用喵~
     * 输入：LanguageFile 枚举（表示要读取哪类语言文件，如消息/研究/配方）
     * 输出：英语对应的 FileConfiguration 对象
     * 边界条件：如果英语语言对象或文件不存在，直接抛出 IllegalStateException 喵~
     */
    // 获取英语（"en"）语言对应的默认配置文件，作为翻译缺失时的回退数据源喵~
    private @Nonnull FileConfiguration getDefaultFile(@Nonnull LanguageFile file) {
        // 通过预设枚举获取英语语言对象，确保回退语言始终是英语喵~
        Language language = getLanguage(LanguagePreset.ENGLISH.getLanguageCode());

        // 喵~防御：英语语言对象不存在时抛出状态异常，防止后续空指针喵~
        if (language == null) {
            throw new IllegalStateException("Fallback language \"en\" is missing!");
        }

        // 获取英语语言对应的指定类型配置文件喵~
        FileConfiguration fallback = language.getFile(file);

        // 喵~防御：英语对应的文件也不存在时抛出异常，防止无法回退喵~
        if (fallback != null) {
            return fallback;
        } else {
            throw new IllegalStateException("Fallback file: \"" + file.getFilePath("en") + "\" is missing!");
        }
    }

    /*
     * 整体思路：先从指定语言文件中查找目标路径的字符串，找不到则回退英语文件，仍找不到返回 null 喵~
     * 输入：语言对象（可为null）、语言文件类型、配置路径
     * 输出：找到的字符串或 null
     * 边界条件：language 为 null 时视为单元测试场景，返回错误提示字符串喵~
     */
    @ParametersAreNonnullByDefault
    // 尝试从指定语言的指定文件中读取字符串，失败则回退英语，两者均无返回 null 喵~
    private @Nullable String getStringOrNull(@Nullable Language language, LanguageFile file, String path) {
        // 喵~防御：file 为 null 时立即报错，防止后续读取崩溃喵~
        Validate.notNull(file, "You need to provide a LanguageFile!");
        // 喵~防御：path 为 null 时立即报错，防止查找空路径喵~
        Validate.notNull(path, "The path cannot be null!");

        // 喵~防御：language 为 null 通常发生在单元测试中，返回提示字符串避免空指针喵~
        if (language == null) {
            // Unit-Test scenario (or something went horribly wrong)
            return "Error: No language present";
        }

        // 获取该语言对应类型的配置文件（如 messages.yml 的翻译文件）喵~
        FileConfiguration config = language.getFile(file);

        // 喵~防御：配置文件不为null时才尝试读取，防止对null配置调用getString崩溃喵~
        if (config != null) {
            // 从配置文件中按路径读取字符串值喵~
            String value = config.getString(path);

            // Return the found value (unless null)
            // 找到了翻译值，直接返回，无需回退英语喵~
            if (value != null) {
                return value;
            }
        }

        // Fallback to default configuration
        // 目标语言无此翻译，回退到英语默认文件喵~
        FileConfiguration defaults = getDefaultFile(file);
        // 从英语默认文件中读取对应路径的字符串喵~
        String defaultValue = defaults.getString(path);

        // Return the default value or an error message
        // 有英语默认值就返回，否则返回 null 表示完全缺失喵~
        return defaultValue != null ? defaultValue : null;
    }

    @ParametersAreNonnullByDefault
    // 与 getStringOrNull 类似，但保证非空返回——找不到时返回含路径名的错误提示字符串喵~
    private @Nonnull String getString(@Nullable Language language, LanguageFile file, String path) {
        // 先尝试获取字符串，可能为 null 喵~
        String string = getStringOrNull(language, file, path);
        // 喵~防御：string 为 null 时返回带路径的错误提示，避免返回 null 导致调用方崩溃喵~
        return string != null ? string : "! Missing string \"" + path + '"';
    }

    /*
     * 整体思路：先从指定语言文件中查找目标路径的字符串列表，找不到则回退英语文件，仍找不到返回 null 喵~
     * 输入：语言对象（可为null）、语言文件类型、配置路径
     * 输出：字符串列表或 null
     * 边界条件：language 为 null 时返回含错误提示的单元素列表；列表为空也视为"未找到"喵~
     */
    @ParametersAreNonnullByDefault
    // 尝试从指定语言文件中读取字符串列表，失败则回退英语，两者均无返回 null 喵~
    private @Nullable List<String> getStringListOrNull(@Nullable Language language, LanguageFile file, String path) {
        // 喵~防御：file 为 null 时立即报错喵~
        Validate.notNull(file, "You need to provide a LanguageFile!");
        // 喵~防御：path 为 null 时立即报错喵~
        Validate.notNull(path, "The path cannot be null!");

        // 喵~防御：language 为 null 时（单元测试场景）返回包含错误提示的列表，避免空指针喵~
        if (language == null) {
            // Unit-Test scenario (or something went horribly wrong)
            return Arrays.asList("Error: No language present");
        }

        // 获取该语言对应类型的配置文件喵~
        FileConfiguration config = language.getFile(file);

        // 喵~防御：配置文件不为null时才尝试读取列表喵~
        if (config != null) {
            // 从配置文件中按路径读取字符串列表喵~
            List<String> value = config.getStringList(path);

            // Return the found value (unless empty)
            // 列表非空说明找到了翻译，直接返回喵~
            if (!value.isEmpty()) {
                return value;
            }
        }

        // Fallback to default configuration
        // 目标语言列表为空，回退到英语默认文件读取喵~
        FileConfiguration defaults = getDefaultFile(file);
        // 从英语默认文件读取字符串列表喵~
        List<String> defaultValue = defaults.getStringList(path);

        // Return the default value or an error message
        // 英语默认列表非空就返回，否则返回 null 表示完全缺失喵~
        return !defaultValue.isEmpty() ? defaultValue : null;
    }

    @ParametersAreNonnullByDefault
    // 与 getStringListOrNull 类似，但保证非空返回——找不到时返回含路径名的错误提示列表喵~
    private @Nonnull List<String> getStringList(@Nullable Language language, LanguageFile file, String path) {
        // 先尝试获取字符串列表，可能为 null 喵~
        List<String> list = getStringListOrNull(language, file, path);
        // 喵~防御：list 为 null 时返回带路径的错误提示列表，避免返回 null 导致调用方崩溃喵~
        return list != null ? list : Arrays.asList("! Missing string \"" + path + '"');
    }

    // 根据消息键从服务器默认语言的 messages.yml 中获取消息字符串，找不到时从英语回退喵~
    public @Nonnull String getMessage(@Nonnull String key) {
        // 喵~防御：key 为 null 时立即报错，防止查找空键喵~
        Validate.notNull(key, "Message key must not be null!");

        // 获取服务器当前默认语言对象喵~
        Language language = getDefaultLanguage();

        // 喵~防御：language 为 null 时（无默认语言）message 赋为 null，后续会回退英语喵~
        String message = language == null
                ? null
                : language.getFile(LanguageFile.MESSAGES).getString(key);

        // 喵~防御：message 为 null 时从英语默认文件读取，保证不返回 null 喵~
        if (message == null) {
            return getDefaultFile(LanguageFile.MESSAGES).getString(key);
        }

        // 成功找到消息，直接返回喵~
        return message;
    }

    // 根据玩家语言设置和消息键获取该玩家对应语言的消息字符串喵~
    public @Nonnull String getMessage(@Nonnull Player p, @Nonnull String key) {
        // 喵~防御：玩家不能为 null，否则无法获取语言设置喵~
        Validate.notNull(p, "Player must not be null!");
        // 喵~防御：key 不能为 null，否则无法查找消息喵~
        Validate.notNull(key, "Message key must not be null!");

        // 获取玩家的语言设置后，从 MESSAGES 文件中读取对应的消息喵~
        return getString(getLanguage(p), LanguageFile.MESSAGES, key);
    }

    /**
     * Returns the Strings referring to the specified Key
     *
     * @param key
     *            The Key of those Messages
     * @return The List this key is referring to
     */
    // 从默认配置文件（messages.yml）中读取指定键对应的字符串列表，用于多行消息喵~
    public @Nonnull List<String> getDefaultMessages(@Nonnull String key) {
        return defaultConfig.getStringList(key);
    }

    // 根据玩家语言设置和消息键获取对应的多行消息字符串列表喵~
    public @Nonnull List<String> getMessages(@Nonnull Player p, @Nonnull String key) {
        // 喵~防御：玩家不能为 null 喵~
        Validate.notNull(p, "Player should not be null.");
        // 喵~防御：key 不能为 null 喵~
        Validate.notNull(key, "Message key cannot be null.");

        // 获取玩家语言后从 MESSAGES 文件读取字符串列表喵~
        return getStringList(getLanguage(p), LanguageFile.MESSAGES, key);
    }

    @ParametersAreNonnullByDefault
    // 获取玩家对应语言的多行消息列表，并用 function 对每行消息进行自定义变换（如替换占位符）喵~
    public @Nonnull List<String> getMessages(Player p, String key, UnaryOperator<String> function) {
        // 喵~防御：玩家不能为 null 喵~
        Validate.notNull(p, "Player cannot be null.");
        // 喵~防御：key 不能为 null 喵~
        Validate.notNull(key, "Message key cannot be null.");
        // 喵~防御：function 不能为 null，否则 replaceAll 会抛出异常喵~
        Validate.notNull(function, "Function cannot be null.");

        // 先获取玩家语言对应的消息列表喵~
        List<String> messages = getMessages(p, key);
        // 对列表中每条消息应用变换函数（如将占位符替换为实际内容）喵~
        messages.replaceAll(function);

        return messages;
    }

    // 获取指定玩家语言下某研究的本地化名称，找不到时返回 null 喵~
    public @Nullable String getResearchName(@Nonnull Player p, @Nonnull NamespacedKey key) {
        // 喵~防御：玩家不能为 null 喵~
        Validate.notNull(p, "Player must not be null.");
        // 喵~防御：NamespacedKey 不能为 null，否则无法拼接路径喵~
        Validate.notNull(key, "NamespacedKey cannot be null.");

        // 将 NamespacedKey 的命名空间和键名拼成配置路径（如 "slimefun.research_name"），然后从 RESEARCHES 文件读取喵~
        return getStringOrNull(getLanguage(p), LanguageFile.RESEARCHES, key.getNamespace() + '.' + key.getKey());
    }

    // 获取指定玩家语言下某物品分组的本地化名称，找不到时返回 null 喵~
    public @Nullable String getItemGroupName(@Nonnull Player p, @Nonnull NamespacedKey key) {
        // 喵~防御：玩家不能为 null 喵~
        Validate.notNull(p, "Player must not be null.");
        // 喵~防御：NamespacedKey 不能为 null 喵~
        Validate.notNull(key, "NamespacedKey cannot be null!");

        // 将命名空间和键拼成路径从 CATEGORIES（分类）文件中读取本地化名称喵~
        return getStringOrNull(getLanguage(p), LanguageFile.CATEGORIES, key.getNamespace() + '.' + key.getKey());
    }

    // 获取指定玩家语言下某 GEO 资源的本地化字符串，找不到时返回 null 喵~
    public @Nullable String getResourceString(@Nonnull Player p, @Nonnull String key) {
        // 喵~防御：玩家不能为 null 喵~
        Validate.notNull(p, "Player should not be null!");
        // 喵~防御：key 不能为 null 喵~
        Validate.notNull(key, "Message key should not be null!");

        // 从 RESOURCES 文件中读取对应玩家语言的资源名称喵~
        return getStringOrNull(getLanguage(p), LanguageFile.RESOURCES, key);
    }

    /*
     * 整体思路：根据玩家语言和合成类型构建一个带本地化名称和 Lore 的 ItemStack，用于在合成指南中展示喵~
     * 输入：玩家对象（用于获取语言）、RecipeType（合成类型对象）
     * 输出：带有本地化显示名称和 Lore 的 ItemStack
     * 边界条件：recipeType.toItem() 为 null 时返回空气物品；displayName/lore 找不到则保留原始默认值喵~
     */
    // 根据玩家语言获取合成类型的本地化展示物品（带翻译名称和 Lore）喵~
    public @Nonnull ItemStack getRecipeTypeItem(@Nonnull Player p, @Nonnull RecipeType recipeType) {
        // 喵~防御：玩家不能为 null 喵~
        Validate.notNull(p, "Player cannot be null!");
        // 喵~防御：合成类型不能为 null 喵~
        Validate.notNull(recipeType, "Recipe type cannot be null!");

        // 将合成类型转换为对应的图标 ItemStack 喵~
        ItemStack item = recipeType.toItem();

        // 喵~防御：item 为 null 时（见 Issue #3088）返回空气物品，防止后续操作空指针崩溃喵~
        if (item == null) {
            // Fixes #3088
            return new ItemStack(Material.AIR);
        }

        // 获取玩家的语言设置，用于后续读取本地化文本喵~
        Language language = getLanguage(p);

        // 获取合成类型的 NamespacedKey，用于拼接配置文件中的路径喵~
        NamespacedKey key = recipeType.getKey();

        // 创建一个基于原物品的自定义 ItemStack，并通过 Lambda 修改其 Meta（名称和 Lore）喵~
        return new CustomItemStack(item, meta -> {
            // 拼接路径（如 "slimefun.enhanced_crafting_table.name"）从 RECIPES 文件读取显示名称喵~
            String displayName =
                    getStringOrNull(language, LanguageFile.RECIPES, key.getNamespace() + "." + key.getKey() + ".name");

            // Set the display name if possible, else keep the default item name.
            // 找到本地化名称时设置为水蓝色显示名，否则保留原始默认名喵~
            if (displayName != null) {
                meta.setDisplayName(ChatColor.AQUA + displayName);
            }

            // 拼接路径（如 "slimefun.enhanced_crafting_table.lore"）从 RECIPES 文件读取 Lore 列表喵~
            List<String> lore = getStringListOrNull(
                    language, LanguageFile.RECIPES, key.getNamespace() + "." + key.getKey() + ".lore");

            // Set the lore if possible, else keep the default lore.
            // 找到本地化 Lore 时，将每行设为灰色并应用到物品上，否则保留原始 Lore 喵~
            if (lore != null) {
                // 给每行 Lore 前面加上灰色颜色代码，让界面更美观喵~
                lore.replaceAll(line -> ChatColor.GRAY + line);
                meta.setLore(lore);
            }

            // 隐藏物品属性（如攻击力）以保持界面整洁喵~
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            // 隐藏附魔信息以保持界面整洁喵~
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
    }

    // 向命令发送者发送一条带有语言翻译的消息，支持控制是否加前缀喵~
    public void sendMessage(@Nonnull CommandSender recipient, @Nonnull String key, boolean addPrefix) {
        // 喵~防御：接收者不能为 null，否则无法发送消息喵~
        Validate.notNull(recipient, "Recipient cannot be null!");
        // 喵~防御：key 不能为 null 喵~
        Validate.notNull(key, "Message key cannot be null!");

        // 根据 addPrefix 决定是否拼接聊天前缀，不加前缀时用空字符串喵~
        String prefix = addPrefix ? getChatPrefix() : "";

        // 判断接收者是否是玩家，玩家需要翻译成其对应语言喵~
        if (recipient instanceof Player player) {
            // 向玩家发送颜色解析后的本地化消息（带前缀）喵~
            recipient.sendMessage(ChatColors.color(prefix + getMessage(player, key)));
        } else {
            // 非玩家（如控制台）发送时需要去除颜色代码，因为控制台不支持颜色喵~
            recipient.sendMessage(ChatColor.stripColor(ChatColors.color(prefix + getMessage(key))));
        }
    }

    // 向玩家的动作栏（屏幕下方 HUD 区域）发送一条本地化消息喵~
    public void sendActionbarMessage(@Nonnull Player player, @Nonnull String key, boolean addPrefix) {
        // 喵~防御：玩家不能为 null 喵~
        Validate.notNull(player, "Player cannot be null!");
        // 喵~防御：key 不能为 null 喵~
        Validate.notNull(key, "Message key cannot be null!");

        // 根据 addPrefix 决定是否拼接聊天前缀喵~
        String prefix = addPrefix ? getChatPrefix() : "";
        // 解析颜色代码，拼接前缀和翻译后的消息内容喵~
        String message = ChatColors.color(prefix + getMessage(player, key));

        // 将普通文本转换为 Spigot 支持的 BaseComponent 数组（动作栏需要此格式）喵~
        BaseComponent[] components = TextComponent.fromLegacyText(message);
        // 通过 Spigot API 将消息发送到玩家动作栏（ACTION_BAR 位置）喵~
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, components);
    }

    // sendMessage 的重载版本，默认加前缀发送消息喵~
    public void sendMessage(@Nonnull CommandSender recipient, @Nonnull String key) {
        // 调用三参数版本，addPrefix 固定为 true 喵~
        sendMessage(recipient, key, true);
    }

    @ParametersAreNonnullByDefault
    // sendMessage 的重载版本，默认加前缀，并允许传入 function 对消息进行自定义变换（如替换占位符）喵~
    public void sendMessage(CommandSender recipient, String key, UnaryOperator<String> function) {
        // 调用四参数版本，addPrefix 固定为 true 喵~
        sendMessage(recipient, key, true, function);
    }

    @ParametersAreNonnullByDefault
    // 发送带自定义变换的本地化消息，支持控制前缀，单元测试环境下直接跳过喵~
    public void sendMessage(CommandSender recipient, String key, boolean addPrefix, UnaryOperator<String> function) {
        // 喵~防御：单元测试环境（UNIT_TEST）下不发送消息，避免测试时报错喵~
        if (Slimefun.getMinecraftVersion() == MinecraftVersion.UNIT_TEST) {
            return;
        }

        // 根据 addPrefix 决定是否拼接聊天前缀喵~
        String prefix = addPrefix ? getChatPrefix() : "";

        // 判断接收者是否是玩家喵~
        if (recipient instanceof Player player) {
            // 向玩家发送经 function 变换后的本地化消息（带颜色解析）喵~
            recipient.sendMessage(ChatColors.color(prefix + function.apply(getMessage(player, key))));
        } else {
            // 控制台等非玩家接收者：去除颜色代码后发送变换后的消息喵~
            recipient.sendMessage(ChatColor.stripColor(ChatColors.color(prefix + function.apply(getMessage(key)))));
        }
    }

    // 向命令发送者发送多行本地化消息（默认带前缀），每行分别发送喵~
    public void sendMessages(@Nonnull CommandSender recipient, @Nonnull String key) {
        // 获取聊天前缀，每行消息都会加上喵~
        String prefix = getChatPrefix();

        // 判断接收者是否是玩家喵~
        if (recipient instanceof Player player) {
            // 遍历玩家语言对应的多行消息列表，逐行发送喵~
            for (String translation : getMessages(player, key)) {
                // 解析颜色代码，拼接前缀后发送给玩家喵~
                String message = ChatColors.color(prefix + translation);
                recipient.sendMessage(message);
            }
        } else {
            // 非玩家接收者：从默认配置读取消息，去除颜色代码后逐行发送喵~
            for (String translation : getDefaultMessages(key)) {
                // 去除颜色代码，使控制台正常显示喵~
                String message = ChatColors.color(prefix + translation);
                recipient.sendMessage(ChatColor.stripColor(message));
            }
        }
    }

    @ParametersAreNonnullByDefault
    // 向命令发送者发送经 function 变换的多行本地化消息，支持控制是否加前缀喵~
    public void sendMessages(CommandSender recipient, String key, boolean addPrefix, UnaryOperator<String> function) {
        // 根据 addPrefix 决定是否拼接聊天前缀喵~
        String prefix = addPrefix ? getChatPrefix() : "";

        // 判断接收者是否是玩家喵~
        if (recipient instanceof Player player) {
            // 遍历玩家语言对应的多行消息列表，逐行经 function 变换后发送喵~
            for (String translation : getMessages(player, key)) {
                // 解析颜色，拼接前缀，应用 function 变换后发送给玩家喵~
                String message = ChatColors.color(prefix + function.apply(translation));
                recipient.sendMessage(message);
            }
        } else {
            // 非玩家接收者：从默认消息读取，去除颜色代码后发送喵~
            for (String translation : getDefaultMessages(key)) {
                // 去除颜色代码并应用 function 变换后发送给控制台等接收者喵~
                String message = ChatColors.color(prefix + function.apply(translation));
                recipient.sendMessage(ChatColor.stripColor(message));
            }
        }
    }

    @ParametersAreNonnullByDefault
    // sendMessages 的重载版本，默认加前缀并应用 function 变换喵~
    public void sendMessages(CommandSender recipient, String key, UnaryOperator<String> function) {
        // 调用四参数版本，addPrefix 固定为 true 喵~
        sendMessages(recipient, key, true, function);
    }

    // 获取指定语言所有配置文件中的全部键集合，用于统计翻译覆盖率等场景喵~
    protected @Nonnull Set<String> getTotalKeys(@Nonnull Language lang) {
        // 获取该语言的所有配置文件数组，传给 getKeys 方法合并所有键喵~
        return getKeys(lang.getFiles());
    }

    /*
     * 整体思路：遍历多个配置文件，收集所有键（包括嵌套子键）合并到一个 Set 中喵~
     * 输入：任意数量的 FileConfiguration 对象（varargs）
     * 输出：所有文件中所有键的并集（不含重复）
     * 边界条件：files 为空时直接返回空 Set 喵~
     */
    // 合并多个语言配置文件中的所有键（含嵌套键），返回去重后的键集合喵~
    protected @Nonnull Set<String> getKeys(@Nonnull FileConfiguration... files) {
        // 创建 HashSet 用于存放所有键，自动去重喵~
        Set<String> keys = new HashSet<>();

        // 遍历传入的每个配置文件喵~
        for (FileConfiguration cfg : files) {
            // getKeys(true) 表示递归获取所有嵌套键，将结果合并到 keys 集合中喵~
            keys.addAll(cfg.getKeys(true));
        }

        return keys;
    }
}
