package io.github.thebusybiscuit.slimefun4.api.recipes;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a single recipe entry for a {@link SlimefunItem}.
 * Each entry contains a {@link RecipeType}, a 3x3 recipe grid, and an output item.
 * This allows a single {@link SlimefunItem} to have multiple crafting recipes.
 */
public class RecipeEntry {

    private final RecipeType recipeType;
    private final ItemStack[] recipe;
    private final ItemStack recipeOutput;

    @ParametersAreNonnullByDefault
    public RecipeEntry(RecipeType recipeType, ItemStack[] recipe, ItemStack recipeOutput) {
        this.recipeType = recipeType;
        this.recipe = recipe;
        this.recipeOutput = recipeOutput;
    }

    @Nonnull
    public RecipeType getRecipeType() {
        return recipeType;
    }

    @Nonnull
    public ItemStack[] getRecipe() {
        return recipe;
    }

    @Nonnull
    public ItemStack getRecipeOutput() {
        return recipeOutput.clone();
    }
}
