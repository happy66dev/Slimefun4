package io.github.thebusybiscuit.slimefun4.implementation.items;

// 导入物品组类，用于将此物品归入Slimefun指南的某个分类喵
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
// 导入物品状态枚举，VanillaItem禁用时会变为VANILLA状态喵
import io.github.thebusybiscuit.slimefun4.api.items.ItemState;
// 导入Slimefun物品核心基类，VanillaItem继承自它喵
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
// 导入合成类型类，定义此物品通过什么方式合成喵
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
// 导入注解：标记此构造器的所有参数都不允许为null喵
import javax.annotation.ParametersAreNonnullByDefault;
// 导入Bukkit物品栈类，表示游戏中的具体物品喵
import org.bukkit.inventory.ItemStack;

/**
 * 代表一个被Slimefun"接管"的原版物品（例如鞘翅 {@code ELYTRA}）喵~
 * <p>
 * {@link VanillaItem} 使用未经修改的原版 {@link ItemStack}（没有自定义显示名或lore）喵~
 * 当 {@link VanillaItem} 被禁用时，其 {@link ItemState} 会变为 {@code State.VANILLA}，
 * 这样在配方中会自动用原版等价物替代它喵~
 *
 * {@link VanillaItem} 也默认允许在工作台中使用喵~
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunItem
 *
 */
// 原版物品类，继承SlimefunItem，专门用来表示Slimefun接管的原版物品喵
public class VanillaItem extends SlimefunItem {

    /**
     * 用给定参数创建一个新的 {@link VanillaItem} 实例喵~
     *
     * 整体思路：
     *   - 直接调用父类 SlimefunItem 的构造器，把物品组、物品栈、ID、合成类型、配方全部传递进去喵~
     *   - 然后把 useableInWorkbench 标志设为 true，让这个物品可以在工作台中使用喵~
     * 输入：物品组、物品栈、ID字符串、合成类型、配方数组（均不允许为null，由@ParametersAreNonnullByDefault保证喵）
     * 输出：构造完成的VanillaItem对象喵~
     * 边界条件：所有参数由注解强制非null，调用方须保证合法喵~
     *
     * @param itemGroup
     *            将此 {@link VanillaItem} 绑定到的 {@link ItemGroup}（物品分组）喵
     * @param item
     *            与此 {@link VanillaItem} 对应的原版物品栈喵
     * @param id
     *            此 {@link VanillaItem} 的唯一字符串ID喵
     * @param recipeType
     *            获取此 {@link VanillaItem} 所用的合成类型喵
     * @param recipe
     *            获取此 {@link VanillaItem} 所用的合成配方材料数组喵
     */
    // 标记此构造器的所有参数均不允许传入null，防止空指针异常喵
    @ParametersAreNonnullByDefault
    public VanillaItem(ItemGroup itemGroup, ItemStack item, String id, RecipeType recipeType, ItemStack[] recipe) {
        // 调用父类SlimefunItem的构造器，完成物品组、物品栈、ID、合成类型、配方的初始化喵
        super(itemGroup, item, id, recipeType, recipe);

        // 将"可在工作台使用"标志设为true，让VanillaItem不受Slimefun的工作台限制喵
        useableInWorkbench = true;
    }
}
