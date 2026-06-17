package io.github.thebusybiscuit.slimefun4.implementation.items;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.inventory.ItemStack;

/**
 * 这是一个为自定义Slimefun物品快速上手的抽象基类喵~
 * 它让你只需绑定一个 {@link ItemHandler} 就能给 {@link SlimefunItem} 赋予基本行为喵~
 *
 * 例如可以使用 {@link ItemUseHandler} 为物品添加右键点击功能喵~
 *
 * 整体思路：
 *   继承 SlimefunItem，子类通过泛型参数 T 指定要绑定的 ItemHandler 类型喵~
 *   在注册前（preRegister）自动调用子类实现的 getItemHandler() 并添加到物品喵~
 *   子类只需实现 getItemHandler() 返回具体的处理器实例即可喵~
 *
 * @author TheBusyBiscuit
 *
 * @see ItemHandler
 * @see ItemUseHandler
 * @see SlimefunItem
 *
 * @param <T>
 *            绑定到此 {@link SlimefunItem} 的 {@link ItemHandler} 类型喵~
 */
public abstract class SimpleSlimefunItem<T extends ItemHandler> extends SlimefunItem {

    /**
     * 基础构造方法：不指定自定义合成产出，直接使用默认产出喵~
     * 将物品注册到指定物品组，使用给定的合成类型和配方喵~
     */
    @ParametersAreNonnullByDefault
    protected SimpleSlimefunItem(
            ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        // 调用父类 SlimefunItem 的构造方法，初始化物品组、物品栈、合成类型和配方数组喵~
        super(itemGroup, item, recipeType, recipe);
    }

    /**
     * 扩展构造方法：支持指定自定义合成产出 recipeOutput，
     * 当合成结果和物品本身不同时使用此构造方法喵~
     * recipeOutput 允许为 null，为 null 时表示使用默认产出喵~
     */
    @ParametersAreNonnullByDefault
    protected SimpleSlimefunItem(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            @Nullable ItemStack recipeOutput) {
        // 调用父类构造方法，同时传入自定义合成产出物品(可为null)喵~
        super(itemGroup, item, recipeType, recipe, recipeOutput);
    }

    /**
     * 注册前回调，在此物品被注册到Slimefun系统之前自动执行喵~
     * 负责将子类提供的 ItemHandler 绑定到本物品，使其具备对应的交互能力喵~
     */
    @Override
    public void preRegister() {
        // 获取子类实现的行为处理器，并将其添加到物品的处理器列表喵~
        addItemHandler(getItemHandler());
    }

    /**
     * 抽象方法：由子类实现，返回要绑定到此物品的 {@link ItemHandler} 喵~
     * 例如可返回 ItemUseHandler 实现右键交互，或其他自定义处理器喵~
     *
     * @return 要绑定到此 {@link SlimefunItem} 的 {@link ItemHandler} 实例，不可为null喵~
     */
    public abstract @Nonnull T getItemHandler();
}
