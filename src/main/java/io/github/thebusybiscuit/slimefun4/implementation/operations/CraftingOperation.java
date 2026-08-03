package io.github.thebusybiscuit.slimefun4.implementation.operations;

import city.norain.slimefun4.utils.LocalizationUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import org.apache.commons.lang.Validate;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * This {@link MachineOperation} represents a crafting process.
 *
 * @author TheBusyBiscuit
 *
 */
public class CraftingOperation implements MachineOperation {

    public static final String TYPE_ID = "crafting";

    private final ItemStack[] ingredients;
    private final ItemStack[] results;

    private final int totalTicks;
    private int currentTicks = 0;

    public CraftingOperation(@Nonnull MachineRecipe recipe) {
        this(recipe.getInput(), recipe.getOutput(), recipe.getTicks());
    }

    public CraftingOperation(@Nonnull ItemStack[] ingredients, @Nonnull ItemStack[] results, int totalTicks) {
        Validate.notEmpty(ingredients, "The Ingredients array cannot be empty or null");
        Validate.notEmpty(results, "The results array cannot be empty or null");
        Validate.isTrue(
                totalTicks >= 0,
                "The amount of total ticks must be a positive integer or zero, received: " + totalTicks);

        this.ingredients = ingredients;
        this.results = results;
        this.totalTicks = totalTicks;
    }

    @Override
    public void addProgress(int num) {
        Validate.isTrue(num > 0, "Progress must be positive.");
        currentTicks += num;
    }

    @Nonnull
    public ItemStack[] getIngredients() {
        return ingredients;
    }

    @Nonnull
    public ItemStack[] getResults() {
        return results;
    }

    @Override
    public int getProgress() {
        return currentTicks;
    }

    @Override
    public int getTotalTicks() {
        return totalTicks;
    }

    @Override
    @Nonnull
    public String getOperationTypeId() {
        return TYPE_ID;
    }

    @Override
    @Nonnull
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(totalTicks).append("|");
        sb.append(currentTicks).append("|");

        sb.append(ingredients.length).append("|");
        for (ItemStack ingredient : ingredients) {
            sb.append(DataUtils.serializeItemStack(ingredient)).append(";");
        }
        sb.append("|");

        sb.append(results.length).append("|");
        for (ItemStack result : results) {
            sb.append(DataUtils.serializeItemStack(result)).append(";");
        }

        return sb.toString();
    }

    @Override
    @Nullable public String getDisplayName() {
        if (ingredients.length > 0 && ingredients[0] != null) {
            return getItemDisplayName(ingredients[0]);
        }
        return null;
    }

    @Nullable public static CraftingOperation deserialize(@Nonnull String data) {
        try {
            String[] parts = data.split("\\|", 7);
            int offset = 0;
            if (parts.length > 0 && TYPE_ID.equals(parts[0])) {
                offset = 1;
            }

            if (parts.length < 5 + offset) {
                return null;
            }

            int totalTicks = Integer.parseInt(parts[offset]);
            int currentTicks = Integer.parseInt(parts[1 + offset]);
            if (totalTicks < 0 || currentTicks < 0 || currentTicks > totalTicks) {
                return null;
            }

            int ingredientCount = Integer.parseInt(parts[2 + offset]);
            if (ingredientCount < 0 || ingredientCount > 64) {
                return null;
            }
            String[] ingredientData = parts[3 + offset].split(";", -1);
            ItemStack[] ingredients = new ItemStack[ingredientCount];
            for (int i = 0; i < ingredientCount && i < ingredientData.length; i++) {
                ingredients[i] = DataUtils.deserializeItemStack(ingredientData[i]);
                if (ingredients[i] == null || ingredients[i].getType().isAir()) {
                    return null;
                }
            }
            if (ingredientData.length < ingredientCount) {
                return null;
            }

            int resultCount = Integer.parseInt(parts[4 + offset]);
            if (resultCount < 0 || resultCount > 64) {
                return null;
            }
            int resultDataIdx = 5 + offset;
            String[] resultData = parts.length > resultDataIdx ? parts[resultDataIdx].split(";", -1) : new String[0];
            ItemStack[] results = new ItemStack[resultCount];
            for (int i = 0; i < resultCount && i < resultData.length; i++) {
                results[i] = DataUtils.deserializeItemStack(resultData[i]);
                if (results[i] == null || results[i].getType().isAir()) {
                    return null;
                }
            }
            if (resultData.length < resultCount) {
                return null;
            }

            CraftingOperation op = new CraftingOperation(ingredients, results, totalTicks);
            if (currentTicks > 0) {
                op.addProgress(currentTicks);
            }
            return op;
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    private static String getItemDisplayName(@Nonnull ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }

        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        if (sfItem != null) {
            return sfItem.getItemName();
        }

        return LocalizationUtils.getItemName(item.getType());
    }
}
