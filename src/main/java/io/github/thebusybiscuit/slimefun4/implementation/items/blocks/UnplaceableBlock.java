package io.github.thebusybiscuit.slimefun4.implementation.items.blocks;

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotPlaceable;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.inventory.ItemStack;

/**
 * This is a simple {@link SlimefunItem} implementation which implements the {@link NotPlaceable}
 * attribute and also cancels any {@link PlayerRightClickEvent}.
 * Therefore making this an {@link UnplaceableBlock}.
 *
 * @author TheBusyBiscuit
 *
 * @see NotPlaceable
 *
 */
public class UnplaceableBlock extends SimpleSlimefunItem<ItemUseHandler> implements NotPlaceable {

    private final boolean allowBlockInteraction;

    @ParametersAreNonnullByDefault
    public UnplaceableBlock(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        this(itemGroup, item, recipeType, recipe, null, true);
    }

    @ParametersAreNonnullByDefault
    public UnplaceableBlock(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            @Nullable ItemStack recipeOutput) {
        this(itemGroup, item, recipeType, recipe, recipeOutput, true);
    }

    @ParametersAreNonnullByDefault
    public UnplaceableBlock(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            @Nullable ItemStack recipeOutput,
            boolean allowBlockInteraction) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);
        this.allowBlockInteraction = allowBlockInteraction;
    }

    @Override
    public ItemUseHandler getItemHandler() {
        return event -> {
            if (allowBlockInteraction) {
                event.setUseItem(org.bukkit.event.Event.Result.DENY);
            } else {
                event.cancel();
            }
        };
    }
}
