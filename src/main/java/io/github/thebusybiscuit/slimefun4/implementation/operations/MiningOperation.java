package io.github.thebusybiscuit.slimefun4.implementation.operations;

import city.norain.slimefun4.utils.LocalizationUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.lang.Validate;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * This {@link MachineOperation} represents an operation
 * with no inputs, only a result.
 *
 * @author TheBusyBiscuit
 *
 */
public class MiningOperation implements MachineOperation {

    public static final String TYPE_ID = "mining";

    private final ItemStack result;

    private final int totalTicks;
    private int currentTicks = 0;

    public MiningOperation(@Nonnull ItemStack result, int totalTicks) {
        Validate.notNull(result, "The result cannot be null");
        Validate.isTrue(
                totalTicks >= 0,
                "The amount of total ticks must be a positive integer or zero, received: " + totalTicks);

        this.result = result;
        this.totalTicks = totalTicks;
    }

    @Override
    public void addProgress(int num) {
        Validate.isTrue(num > 0, "Progress must be positive.");
        currentTicks += num;
    }

    @Nonnull
    public ItemStack getResult() {
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
        return totalTicks + "|" + currentTicks + "|" + DataUtils.serializeItemStack(result);
    }

    @Override
    @Nullable public String getDisplayName() {
        return getItemDisplayName(result);
    }

    @Nullable public static MiningOperation deserialize(@Nonnull String data) {
        try {
            String[] parts = data.split("\\|", 3);
            if (parts.length < 3) {
                return null;
            }

            int totalTicks = Integer.parseInt(parts[0]);
            int currentTicks = Integer.parseInt(parts[1]);

            ItemStack result = DataUtils.deserializeItemStack(parts[2]);
            if (result == null) {
                return null;
            }

            MiningOperation op = new MiningOperation(result, totalTicks);
            if (currentTicks > 0) {
                op.addProgress(currentTicks);
            }
            return op;
        } catch (Exception e) {
            return null;
        }
    }

    @Nonnull
    static String getItemDisplayName(@Nonnull ItemStack item) {
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
