package io.github.thebusybiscuit.slimefun4.api.items.settings;

import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Material;
import org.bukkit.Tag;

/**
 * This variation of {@link ItemSetting} allows you to define a default {@link Tag}.
 * The {@link Tag} will be translated into a {@link String} {@link List} which the user
 * can then configure as they wish.
 *
 * It also validates all inputs to be a valid {@link Material}.
 *
 * @author TheBusyBiscuit
 *
 * @see ItemSetting
 *
 */
// 这个类是 ItemSetting 的特化版本，用于存储"材料Tag"类型的设置喵~
// Tag<Material> 是 Bukkit 提供的一组材料集合（例如所有木头类型），玩家可通过配置自定义这个列表喵~
public class MaterialTagSetting extends ItemSetting<List<String>> {

    // 保存默认的材料 Tag，当玩家没有自定义时使用这个默认值喵~
    private final Tag<Material> defaultTag;

    /*
     * 构造方法整体思路喵~
     * 输入：所属的 SlimefunItem、配置键名 key、以及默认的材料 Tag
     * 输出：一个初始化好的 MaterialTagSetting 对象
     * 边界条件：所有参数均不可为 null，由 @ParametersAreNonnullByDefault 注解保证喵~
     */
    @ParametersAreNonnullByDefault
    public MaterialTagSetting(SlimefunItem item, String key, Tag<Material> defaultTag) {
        // 调用父类构造方法，将 Tag 先转成字符串列表再传入，作为该设置的默认值喵~
        super(item, key, getAsStringList(defaultTag));

        // 将默认 Tag 保存到成员变量，供 getDefaultTag() 方法返回喵~
        this.defaultTag = defaultTag;
    }

    /**
     * This {@link Tag} holds the default values for this {@link MaterialTagSetting}.
     *
     * @return The default {@link Tag}
     */
    // 返回该设置的默认材料 Tag，外部可以通过这个方法获取原始默认 Tag 对象喵~
    public @Nonnull Tag<Material> getDefaultTag() {
        return defaultTag;
    }

    // 重写父类方法，返回当用户输入不合法时显示的错误提示信息喵~
    @Override
    protected @Nonnull String getErrorMessage() {
        // 提示用户列表只能包含有效的材料名称，格式如 REDSTONE_BLOCK喵~
        return "This List can only contain Materials in the format of e.g. REDSTONE_BLOCK";
    }

    /*
     * validateInput 整体思路喵~
     * 先调用父类做基础校验，再逐个检查每个字符串是否能匹配到有效的 Bukkit Material 枚举值喵~
     * 输入：用户在配置中填写的材料名称字符串列表
     * 输出：true 表示全部合法，false 表示存在非法材料名喵~
     * 边界条件：列表中任意一项不是有效 Material 时立即返回 false，不继续遍历喵~
     */
    @Override
    public boolean validateInput(List<String> input) {
        // 先调用父类的基础校验（如非 null、类型匹配等），父类校验不通过直接返回 false喵~
        if (super.validateInput(input)) {
            // 遍历用户输入的每一个材料名称字符串，逐一验证是否是有效的 Material喵~
            for (String value : input) {
                // 尝试将字符串匹配为 Bukkit 的 Material 枚举值，匹配失败时返回 null喵~
                Material material = Material.matchMaterial(value);

                // This value is not a valid material, the setting is not valid.
                // 喵~防御：material 为 null 说明该字符串不对应任何有效材料，直接判定输入不合法返回 false喵~
                if (material == null) {
                    return false;
                }
            }

            // 所有材料名称都验证通过，返回 true 表示输入合法喵~
            return true;
        } else {
            // 父类基础校验未通过，输入不合法，返回 false喵~
            return false;
        }
    }

    /**
     * Internal method to turn a {@link Tag} into a {@link List} of {@link String Strings}.
     *
     * @param tag
     *            Our {@link Tag}
     *
     * @return The {@link String} {@link List}
     */
    // 私有静态工具方法：将 Tag<Material> 转换为材料名称的字符串列表，供构造方法初始化默认值使用喵~
    // 整体思路：取出 Tag 中所有 Material 枚举值，通过 Stream 流映射为名称字符串，再收集为 List<String>喵~
    private static @Nonnull List<String> getAsStringList(@Nonnull Tag<Material> tag) {
        // 用 Stream 流将 Tag 内所有 Material 枚举值转为其名称字符串，最终收集为一个字符串列表喵~
        return tag.getValues().stream().map(Material::name).collect(Collectors.toList());
    }
}
