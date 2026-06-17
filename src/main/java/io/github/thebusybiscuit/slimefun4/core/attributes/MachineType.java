package io.github.thebusybiscuit.slimefun4.core.attributes;

import javax.annotation.Nonnull;

/**
 * 机器类型枚举，用于区分 Slimefun 中不同种类的功能方块喵~
 * 每个枚举常量对应一个中文后缀，拼接在机器名称末尾用于展示喵~
 * 例如：CAPACITOR → "电容"，GENERATOR → "发电机"喵~
 */
public enum MachineType {
    // 电容类机器，名称后缀为"电容"，用于储存能量的功能方块喵~
    CAPACITOR("电容"),
    // 发电机类机器，名称后缀为"发电机"，用于产生能量的功能方块喵~
    GENERATOR("发电机"),
    // 普通机器，名称后缀为"机器"，泛指消耗能量执行加工任务的功能方块喵~
    MACHINE("机器");

    // 该机器类型对应的中文后缀字符串，用于 toString() 返回给显示层喵~
    private final String suffix;

    /**
     * 枚举构造方法，将中文后缀绑定到当前枚举常量喵~
     * @param suffix 机器类型的中文后缀，不允许为 null 喵~
     */
    MachineType(@Nonnull String suffix) {
        // 将传入的中文后缀保存到成员变量，后续 toString() 时直接返回喵~
        this.suffix = suffix;
    }

    @Override
    // 返回该机器类型的中文后缀字符串，便于拼接到机器名称末尾展示喵~
    public String toString() {
        return suffix; // 直接返回构造时绑定的中文后缀，例如"发电机"喵~
    }
}
