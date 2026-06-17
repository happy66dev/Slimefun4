package io.github.thebusybiscuit.slimefun4.implementation.items.androids;

import javax.annotation.Nonnull;

/**
 * This enum holds all the different types a {@link ProgrammableAndroid} can represent.
 * 这个枚举定义了所有可编程机械机器人(ProgrammableAndroid)的类型，
 * 每种类型对应不同的功能与能力喵~
 *
 * @author TheBusyBiscuit
 * @see ProgrammableAndroid
 */
public enum AndroidType {

    /**
     * This is the default type. This {@link ProgrammableAndroid} has no special abilities.
     * But it can move!
     * 默认类型，没有任何特殊能力，但是可以移动喵~
     */
    NONE,

    /**
     * This type represents a {@link MinerAndroid}, it can break blocks.
     * 矿工类型，对应 MinerAndroid，能够挖掘方块喵~
     */
    MINER,

    /**
     * This type stands for the {@link FarmerAndroid}, it can harvest crops.
     * 农夫类型，对应 FarmerAndroid，能够收割庄稼喵~
     */
    FARMER,

    /**
     * The AdvancedFarmerAndroid is an extension of the {@link FarmerAndroid},
     * it can also harvest plants from ExoticGarden.
     * 高级农夫类型，是 FarmerAndroid 的升级版，除了收割普通庄稼外，
     * 还能收割来自 ExoticGarden 插件的特殊植物喵~
     */
    ADVANCED_FARMER,

    /**
     * This type represents the {@link WoodcutterAndroid}, it can chop trees.
     * 伐木工类型，对应 WoodcutterAndroid，能够砍伐树木喵~
     */
    WOODCUTTER,

    /**
     * This type stands for the {@link ButcherAndroid}, it has the ability
     * to damage entities.
     * 战士类型，对应 ButcherAndroid（屠夫机器人），能够对实体造成伤害，用于战斗喵~
     */
    FIGHTER,

    /**
     * The {@link FishermanAndroid} can catch a fish and other materials.
     * 渔夫类型，对应 FishermanAndroid，能够钓鱼和获取其他钓鱼奖励物品喵~
     */
    FISHERMAN,

    /**
     * This type that can represent any other {@link AndroidType} that is not {@code FIGHTER}.
     * This is only used for internal purposes and has no actual implementation of {@link ProgrammableAndroid}
     * that is equivalent to this.
     * 非战士类型，是一个特殊的内部枚举值，代表"除 FIGHTER 以外的所有类型"，
     * 仅供内部逻辑判断使用，没有对应的实际 ProgrammableAndroid 实现喵~
     */
    NON_FIGHTER;

    /*
     * 整体思路：判断当前机器人实例的类型是否"属于"传入的 AndroidType 范围喵~
     * 输入：type —— 要判断的目标类型(不能为null)
     * 输出：boolean —— 当前类型是否符合 type 所代表的范围
     * 边界条件：
     *   - type == NONE      → 任何类型都匹配，因为 NONE 代表"无限制"
     *   - type == this      → 当前类型与 type 完全一致时匹配
     *   - type == NON_FIGHTER && this != FIGHTER → type是"非战士"范围，且当前类型确实不是 FIGHTER 时匹配
     * 例：MINER.isType(NON_FIGHTER) 返回 true，FIGHTER.isType(NON_FIGHTER) 返回 false 喵~
     */
    boolean isType(@Nonnull AndroidType type) {
        // 判断当前类型是否属于指定的 type 范围：
        // 1. type==NONE 表示无类型限制，所有机器人都满足喵~
        // 2. type==this 表示类型完全匹配喵~
        // 3. type==NON_FIGHTER 且当前不是 FIGHTER，表示属于"非战士"这个大范围喵~
        return type == NONE || type == this || (type == NON_FIGHTER && this != FIGHTER);
    }
}
