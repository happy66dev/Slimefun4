package io.github.thebusybiscuit.slimefun4.implementation.items.armor;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.gadgets.Jetpack;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.player.ParachuteTask;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.inventory.ItemStack;

/**
 * 降落伞物品类，是一种可以装备在胸甲槽位的 Slimefun 特殊物品喵~
 *
 * 玩家装备降落伞后，按住 Shift 键可以缓慢滑翔降落，避免摔伤喵~
 *
 * 这个类本身代码很少，实际的滑翔逻辑全都在 {@link ParachuteTask} 里处理喵~
 * （{@link ParachuteTask} 是每 tick 执行一次的任务，负责检测玩家状态并施加缓慢下落效果）
 *
 * The {@link Parachute} is a {@link SlimefunItem} that can be equipped as a chestplate.
 * It allows you slowly glide to the ground while holding shift.
 *
 * This class does not contain much code to see, check our the {@link ParachuteTask} class
 * for the actual logic behind this.
 *
 * @author TheBusyBiscuit
 *
 * @see ParachuteTask
 * @see Jetpack
 *
 */
public class Parachute extends SlimefunItem {

    /**
     * 降落伞物品的构造函数，将物品注册到指定物品组和合成配方中喵~
     *
     * 整体思路：
     *   - 直接调用父类 SlimefunItem 的构造函数完成注册，不需要额外初始化喵~
     *   - 所有参数非空（由 @ParametersAreNonnullByDefault 注解保证），传入 null 会在运行时抛出异常喵~
     *
     * @param itemGroup  物品所属的分类组（决定在 Slimefun 指南哪个分类页显示）喵
     * @param item       降落伞对应的 SlimefunItemStack（包含物品唯一ID、外观等信息）喵
     * @param recipeType 合成类型（决定用哪台机器或什么方式合成）喵
     * @param recipe     合成配方所需的材料数组（3x3 工作台对应9格）喵
     */
    @ParametersAreNonnullByDefault
    public Parachute(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        // 调用父类 SlimefunItem 的构造函数，完成物品注册、配方绑定等基础初始化工作喵~
        super(itemGroup, item, recipeType, recipe);
    }
}
