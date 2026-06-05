package io.github.thebusybiscuit.slimefun4.implementation.operations;

import city.norain.slimefun4.utils.LocalizationUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineFuel;
import org.apache.commons.lang.Validate;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * This {@link MachineOperation} represents the process of burning fuel.
 *
 * @author TheBusyBiscuit
 *
 */
public class FuelOperation implements MachineOperation {

    public static final String TYPE_ID = "fuel";

    private final ItemStack ingredient;
    private final ItemStack result;

    private final int totalTicks;
    private int currentTicks = 0;

    public FuelOperation(@Nonnull MachineFuel recipe) {
        this(recipe.getInput(), recipe.getOutput(), recipe.getTicks());
    }

    public FuelOperation(@Nonnull ItemStack ingredient, @Nullable ItemStack result, int totalTicks) {
        Validate.notNull(ingredient, "The Ingredient cannot be null");
        Validate.isTrue(totalTicks > 0, "The amount of total ticks must be a positive integer");

        this.ingredient = ingredient;
        this.result = result;
        this.totalTicks = totalTicks;
    }

    @Override
    public void addProgress(int num) {
        Validate.isTrue(num > 0, "Progress must be positive.");
        currentTicks += num;
    }

    @Nonnull
    public ItemStack getIngredient() {
        return ingredient;
    }

    @Nullable public ItemStack getResult() {
        return result;
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
        sb.append(DataUtils.serializeItemStack(ingredient)).append("|");

        sb.append(result != null ? DataUtils.serializeItemStack(result) : "");

        return sb.toString();
    }

    @Override
    @Nullable public String getDisplayName() {
        return getItemDisplayName(ingredient);
    }

    @Nullable public static FuelOperation deserialize(@Nonnull String data) {
        try {
            String[] parts = data.split("\\|", 5);
            int offset = 0;
            if (parts.length > 0 && TYPE_ID.equals(parts[0])) {
                offset = 1;
            }

            if (parts.length < 3 + offset) {
                return null;
            }

            int totalTicks = Integer.parseInt(parts[offset]);
            int currentTicks = Integer.parseInt(parts[1 + offset]);

            ItemStack ingredient = DataUtils.deserializeItemStack(parts[2 + offset]);
            if (ingredient == null) {
                return null;
            }

            ItemStack result = parts.length > 3 + offset ? DataUtils.deserializeItemStack(parts[3 + offset]) : null;

            FuelOperation op = new FuelOperation(ingredient, result, totalTicks);
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
