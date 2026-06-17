package io.github.thebusybiscuit.slimefun4.core.attributes;

import javax.annotation.Nonnull;

/**
 * MachineTier 枚举定义了机器的等级分类喵~
 * 每个等级对应一个带Minecraft颜色代码的中文名称，用于在游戏中展示机器品质喵~
 * 等级从低到高依次为：基础 → 普通 → 中型 → 优秀 → 高级 → 终极喵~
 */
public enum MachineTier {
    // 基础等级，用黄色(&e)前缀标识，代表最低品质的机器喵~
    BASIC("&e基础"),
    // 普通等级，用金色(&6)前缀标识，比基础高一级喵~
    AVERAGE("&6普通"),
    // 中型等级，用亮绿色(&a)前缀标识，属于中等品质喵~
    MEDIUM("&a中型"),
    // 优秀等级，用深绿色(&2)前缀标识，品质较高喵~
    GOOD("&2优秀"),
    // 高级等级，用金色(&6)前缀标识，属于高端机器喵~
    ADVANCED("&6高级"),
    // 终极等级，用深红色(&4)前缀标识，代表最顶级的机器喵~
    END_GAME("&4终极");

    // 存储该等级对应的颜色代码+中文名称字符串，用于游戏内界面显示喵~
    private final String prefix;

    /**
     * 枚举构造方法，为每个机器等级绑定显示前缀喵~
     * 输入：prefix — 带Minecraft颜色代码的中文等级名称字符串喵~
     */
    MachineTier(@Nonnull String prefix) {
        // 将传入的颜色前缀字符串保存到实例字段中喵~
        this.prefix = prefix;
    }

    /**
     * 重写toString方法，让枚举值转字符串时直接返回带颜色的等级名称喵~
     * 输出：形如 "&e基础" 的颜色代码字符串，可直接用于游戏内聊天或界面渲染喵~
     */
    @Override
    public String toString() {
        // 返回该等级的颜色前缀字符串，供游戏界面渲染使用喵~
        return prefix;
    }
}
