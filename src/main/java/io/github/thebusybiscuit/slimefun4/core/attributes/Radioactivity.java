package io.github.thebusybiscuit.slimefun4.core.attributes;

import io.github.thebusybiscuit.slimefun4.implementation.tasks.armor.RadiationTask;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

/**
 * 辐射等级枚举，定义了 Slimefun 中所有可用的辐射强度等级喵~
 * 等级越高，辐射对玩家造成的效果越严重，累计速度越快喵~
 * 每次 {@link RadiationTask} 执行时会根据等级对应的 exposureModifier 累加辐射值喵~
 *
 * @author TheBusyBiscuit
 *
 * @see Radioactive
 *
 */
public enum Radioactivity {

    /**
     * 低辐射等级喵~
     * 辐射值增量为 1，积累缓慢，玩家有较多时间做出反应喵~
     */
    // 低辐射：颜色黄色，中文名"低"，每次 RadiationTask tick 给玩家增加 1 点辐射量喵~
    LOW(ChatColor.YELLOW, "低", 1),

    /**
     * 中辐射等级，属于默认参考辐射强度喵~
     * 辐射值增量为 2，属于中等威胁程度喵~
     */
    // 中辐射：颜色黄色，中文名"中"，每次 RadiationTask tick 给玩家增加 2 点辐射量喵~
    MODERATE(ChatColor.YELLOW, "中", 2),

    /**
     * 高辐射等级喵~
     * 辐射值增量为 3，若 {@link Player} 不迅速采取防护将面临死亡风险喵~
     */
    // 高辐射：颜色金色，中文名"高"，每次 RadiationTask tick 给玩家增加 3 点辐射量喵~
    HIGH(ChatColor.GOLD, "高", 3),

    /**
     * 极高辐射等级，对 {@link Player} 来说极为危险喵~
     * 辐射值增量为 5，玩家应当非常谨慎对待喵~
     */
    // 极高辐射：颜色红色，中文名"极高"，每次 RadiationTask tick 给玩家增加 5 点辐射量喵~
    VERY_HIGH(ChatColor.RED, "极高", 5),

    /**
     * 致死辐射等级，是最高辐射强度喵~
     * 辐射值增量为 10，{@link Player} 几乎没有任何自救机会，接触后几乎必然死亡喵~
     */
    // 致死辐射：颜色深红色，中文名"致死"，每次 RadiationTask tick 给玩家增加 10 点辐射量喵~
    VERY_DEADLY(ChatColor.DARK_RED, "致死", 10);

    // 辐射等级对应的聊天颜色，用于在物品 lore 中以不同颜色区分危险程度喵~
    private final ChatColor color;
    // 辐射等级的中文显示名称，如"低"、"中"、"高"，展示给玩家看喵~
    private final String displayName;
    // 每次 RadiationTask 执行时施加到玩家身上的辐射量增量，数值越大辐射积累越快喵~
    private final int exposureModifier;

    /**
     * 枚举构造方法，为每个辐射等级常量赋予颜色、中文名和辐射量增量喵~
     * 由枚举常量声明时自动调用喵~
     *
     * @param color            聊天颜色，用于 lore 中的颜色显示喵~
     * @param displayName      中文显示名称喵~
     * @param exposureModifier 每次 RadiationTask 执行时施加的辐射量增量喵~
     */
    @ParametersAreNonnullByDefault
    Radioactivity(ChatColor color, String displayName, int exposureModifier) {
        // 保存此辐射等级对应的聊天颜色，lore 生成时使用喵~
        this.color = color;
        // 保存此辐射等级的中文名称，例如"低"、"极高"等喵~
        this.displayName = displayName;
        // 保存辐射量增量，RadiationTask 每次 tick 会把此值累加到玩家辐射值上喵~
        this.exposureModifier = exposureModifier;
    }

    /**
     * 获取此辐射等级每次 {@link RadiationTask} 执行时施加的辐射量增量喵~
     * 数值越大，玩家辐射积累越快，越容易死亡喵~
     *
     * @return 每次任务执行时应用的辐射量增量数值喵~
     */
    public int getExposureModifier() {
        // 直接返回构造时设置的辐射量增量字段值喵~
        return exposureModifier;
    }

    /**
     * 获取此辐射等级的物品 lore 描述文本喵~
     * 返回带有辐射符号（☢）、等级颜色和中文名的格式化字符串，
     * 用于在物品提示栏中向玩家展示辐射危险程度喵~
     *
     * @return 格式化后的辐射 lore 字符串，包含颜色代码喵~
     */
    public @Nonnull String getLore() {
        // 拼接 lore：绿色☢符号(☢) + 灰色"辐射等级:"标签 + 对应颜色 + 中文等级名称喵~
        return ChatColor.GREEN + "☢" + ChatColor.GRAY + " 辐射等级: " + color + displayName;
    }

    /**
     * 获取此辐射等级对应的药水效果等级数值喵~
     * 基于枚举常量的声明顺序（ordinal），LOW 对应等级 1，依次递增喵~
     * 用于设置施加给玩家的辐射药水效果强度喵~
     *
     * @return 辐射药水效果的等级数值（从 1 开始）喵~
     */
    public int getRadiationLevel() {
        // ordinal() 返回枚举常量从0开始的声明顺序索引，加1转为从1开始的药水等级喵~
        return ordinal() + 1;
    }
}
