package io.github.thebusybiscuit.slimefun4.api.recipes;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.lang.Validate;
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
        Validate.notNull(recipeType, "The RecipeType must not be null!");
        Validate.notNull(recipe, "The recipe must not be null!");
        Validate.notNull(recipeOutput, "The output must not be null!");

        if (recipe.length != 9) {
            throw new IllegalArgumentException("Recipes must be of length 9, got " + recipe.length);
        }

        this.recipeType = recipeType;
        this.recipe = java.util.Arrays.stream(recipe)
                .map(s -> s == null ? null : s.clone())
                .toArray(ItemStack[]::new);
        this.recipeOutput = recipeOutput.clone();
    }

    @Nonnull
    public RecipeType getRecipeType() {
        return recipeType;
    }

    @Nonnull
    public ItemStack[] getRecipe() {
        return java.util.Arrays.stream(recipe)
                .map(s -> s == null ? null : s.clone())
                .toArray(ItemStack[]::new);
    }

    @Nonnull
    public ItemStack getRecipeOutput() {
        return recipeOutput.clone();
    }
}
