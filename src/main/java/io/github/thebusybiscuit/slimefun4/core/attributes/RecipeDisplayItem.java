package io.github.thebusybiscuit.slimefun4.core.attributes;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.geo.GEOMiner;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.GoldPan;
import java.util.List;
import javax.annotation.Nonnull;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AGenerator;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * \u914D\u65B9\u5C55\u793A\u63A5\u53E3\uFF1A\u5B9E\u73B0\u6B64\u63A5\u53E3\u7684 {@link SlimefunItem} \u5B50\u7C7B\u53EF\u5728 {@link SlimefunGuide} \u6307\u5357\u754C\u9762\u4E2D
 * \u989D\u5916\u5C55\u793A\u4E00\u7EC4\u5173\u8054\u7269\u54C1\u5217\u8868\uFF0C\u7528\u4E8E\u8868\u8FBE\u673A\u5668\u7684\u5904\u7406\u914D\u65B9\u6216\u53EF\u4EA7\u51FA\u8D44\u6E90\u55B5~
 *
 * This interface, when attache to a {@link SlimefunItem} class will make additional items
 * appear in the {@link SlimefunGuide}.
 * These additional items can be used represent recipes or resources that are associated
 * with this {@link SlimefunItem}.
 *
 * You can find a few examples below.
 *
 * @author TheBusyBiscuit
 *
 * @see GoldPan
 * @see GEOMiner
 * @see AGenerator
 *
 */
public interface RecipeDisplayItem extends ItemAttribute {

    /**
     * \u83B7\u53D6\u9700\u8981\u5728 {@link SlimefunGuide} \u4E2D\u5C55\u793A\u7684\u914D\u65B9\u7269\u54C1\u5217\u8868\u55B5~
     * \u7269\u54C1\u4F1A\u4ECE\u4E0A\u5230\u4E0B\u4F9D\u6B21\u586B\u5165\u5C55\u793A\u683C\uFF0C\u5982\u9700\u8868\u8FBE\u914D\u65B9\u5173\u7CFB\u8BF7\u5148\u653E\u8F93\u5165 {@link ItemStack} \u518D\u653E\u8F93\u51FA\u55B5~
     *
     * This is the list of items to display alongside this {@link SlimefunItem}.
     * Note that these items will be filled in from top to bottom first.
     * So if you want it to express a recipe, add your input {@link ItemStack}
     * and then your output {@link ItemStack}.
     *
     * @return The recipes to display in the {@link SlimefunGuide}
     */
    @Nonnull
    List<ItemStack> getDisplayRecipes();

    /**
     * \u83B7\u53D6\u6307\u5357\u4E2D\u914D\u65B9\u533A\u57DF\u6807\u7B7E\u7684\u672C\u5730\u5316\u8DEF\u5F84\u55B5~
     * \u9ED8\u8BA4\u6307\u5411\u8BED\u8A00\u6587\u4EF6\u4E2D\u7684\u673A\u5668\u914D\u65B9\u901A\u7528\u6807\u7B7E "guide.tooltips.recipes.machine"\uFF0C\u5B50\u7C7B\u53EF\u91CD\u5199\u4EE5\u81EA\u5B9A\u4E49\u55B5~
     *
     * Gets the local path for the label in the guide.
     *
     * @return The local path for the label
     */
    @Nonnull
    default String getLabelLocalPath() {
        // \u8FD4\u56DE\u914D\u65B9\u533A\u57DF\u6807\u7B7E\u7684\u9ED8\u8BA4\u672C\u5730\u5316\u952E\uFF0C\u5B50\u7C7B\u91CD\u5199\u6B64\u65B9\u6CD5\u53EF\u81EA\u5B9A\u4E49\u6307\u5357\u4E2D\u663E\u793A\u7684\u6807\u7B7E\u6587\u5B57\u55B5~
        return "guide.tooltips.recipes.machine";
    }

    // \u4E3A\u6307\u5B9A\u73A9\u5BB6\u751F\u6210\u914D\u65B9\u533A\u57DF\u7684\u5206\u9694\u6807\u9898\u6587\u672C\uFF0C\u5E26\u7070\u8272\u4E0B\u7BAD\u5934\u88C5\u9970\u548C\u672C\u5730\u5316\u6807\u7B7E\uFF0C\u5C55\u793A\u5728\u914D\u65B9\u5217\u8868\u4E0A\u65B9\u55B5~
    @Nonnull
    default String getRecipeSectionLabel(@Nonnull Player p) {
        // \u62FC\u63A5\u7070\u8272\u989C\u8272\u4EE3\u7801\u3001\u4E0B\u7BAD\u5934Unicode\u7B26\u53F7\u4E0E\u73A9\u5BB6\u5F53\u524D\u8BED\u8A00\u7684\u672C\u5730\u5316\u6807\u7B7E\uFF0C\u6784\u6210\u914D\u65B9\u533A\u57DF\u6807\u9898\u55B5~
        return "&7\u21E9 " + Slimefun.getLocalization().getMessage(p, getLabelLocalPath()) + " \u21E9";
    }
}
