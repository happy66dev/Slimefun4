package io.github.thebusybiscuit.slimefun4.implementation.items.altar;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.inventory.ItemStack;

/**
 * 古代祭坛合成配方类喵~
 *
 * 整体思路：
 *   古代祭坛的合成配方由一个 3x3 的输入格子组成，编号 0~8（从左上到右下排列）。
 *   其中，格子 4（正中间）是催化剂（catalyst），周围 8 格按顺时针顺序重新排列后
 *   存入 input 列表，方便后续按顺序逐一检测玩家放置的物品是否符合配方要求。
 *
 *   输入格子原始编号布局（3x3 九宫格）：
 *     0 | 1 | 2
 *     3 | 4 | 5
 *     6 | 7 | 8
 *
 *   顺时针重排后 input 列表顺序：0 → 1 → 2 → 5 → 8 → 7 → 6 → 3
 *   催化剂固定为 input[4]（中心格）喵~
 *
 * 边界条件：
 *   - 传入的 input 列表长度必须 >= 9，否则 get(8) 会抛出 IndexOutOfBoundsException 喵~
 *   - output 不能为 null，否则产出物为空会导致祭坛无法正常合成喵~
 */
public class AltarRecipe {

    // 催化剂物品（对应 3x3 配方中心格，即原始 input 列表第 4 位）喵~
    private final ItemStack catalyst;

    // 按顺时针顺序排列的周围 8 格输入物品列表喵~
    private final List<ItemStack> input;

    // 合成完成后的产出物品喵~
    private final ItemStack output;

    /**
     * 构造一条祭坛配方，将 3x3 输入格子中的物品分离出催化剂并按顺时针顺序重排周围 8 格喵~
     *
     * 输入：
     *   input  - 长度为 9 的 ItemStack 列表，对应 3x3 配方格子（编号 0~8，从左上至右下）喵~
     *   output - 合成完成后产出的 ItemStack 喵~
     *
     * 输出：无（构造方法，初始化实例字段）喵~
     *
     * 边界条件：
     *   - input 列表长度须 >= 9，否则 get() 越界喵~
     *   - output 应不为 null，调用方需保证喵~
     */
    public AltarRecipe(List<ItemStack> input, ItemStack output) {
        // 取出中心格（index 4）作为催化剂，催化剂不放入 input 列表，单独保存喵~
        this.catalyst = input.get(4);

        // 初始化存放顺时针顺序 8 格输入物品的列表，初始容量默认 10 喵~
        this.input = new ArrayList<>();

        // 按顺时针顺序添加上方一行三格：左上(0)、正上(1)、右上(2)喵~
        this.input.add(input.get(0)); // 添加左上角格子 [0] 的物品喵~
        this.input.add(input.get(1)); // 添加正上方格子 [1] 的物品喵~
        this.input.add(input.get(2)); // 添加右上角格子 [2] 的物品喵~

        // 继续顺时针，添加右侧和下方：右中(5)、右下(8)喵~
        this.input.add(input.get(5)); // 添加右中格子 [5] 的物品喵~

        // 继续顺时针，添加下方一行从右到左：右下(8)、正下(7)、左下(6)喵~
        this.input.add(input.get(8)); // 添加右下角格子 [8] 的物品喵~
        this.input.add(input.get(7)); // 添加正下方格子 [7] 的物品喵~
        this.input.add(input.get(6)); // 添加左下角格子 [6] 的物品喵~

        // 最后顺时针补完左侧：左中(3)喵~
        this.input.add(input.get(3)); // 添加左中格子 [3] 的物品喵~

        // 保存合成产出物品喵~
        this.output = output;
    }

    /**
     * 获取该配方的催化剂物品（祭坛中心格放置的物品）喵~
     *
     * @return 催化剂 ItemStack 喵~
     */
    public ItemStack getCatalyst() {
        // 返回中心格催化剂物品喵~
        return this.catalyst;
    }

    /**
     * 获取该配方的产出物品（合成完成后给予玩家的物品）喵~
     *
     * @return 产出物 ItemStack 喵~
     */
    public ItemStack getOutput() {
        // 返回合成产出物品喵~
        return this.output;
    }

    /**
     * 获取按顺时针顺序排列的 8 个输入物品列表（不含催化剂）喵~
     *
     * @return 顺时针排列的输入物品列表，长度固定为 8 喵~
     */
    public List<ItemStack> getInput() {
        // 返回顺时针顺序排列的 8 格输入物品列表喵~
        return this.input;
    }

    /**
     * 判断两个 AltarRecipe 是否完全相同喵~
     *
     * 整体思路：
     *   先检查传入的 obj 是否是 AltarRecipe 类型，若是则逐一比对催化剂、输入列表和产出物
     *   三个字段是否全部相等，全部相等才认为是同一配方喵~
     *
     * 输入：obj - 待比较的对象喵~
     * 输出：true 表示两个配方完全相同，false 表示不同或类型不匹配喵~
     */
    @Override
    public boolean equals(Object obj) {
        // 使用 Java 16+ 的模式匹配 instanceof，同时完成类型检查和类型转换喵~
        if (obj instanceof AltarRecipe ar) {
            // 依次比较催化剂、输入物品列表、产出物是否完全一致，全部匹配才返回 true 喵~
            return ar.getCatalyst().equals(getCatalyst())
                    && ar.getInput().equals(getInput())
                    && ar.getOutput().equals(getOutput());
        } else {
            // 传入对象不是 AltarRecipe 类型，直接返回 false 喵~
            return false;
        }
    }
}
