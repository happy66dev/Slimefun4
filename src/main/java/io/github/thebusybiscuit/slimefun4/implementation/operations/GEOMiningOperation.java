package io.github.thebusybiscuit.slimefun4.implementation.operations;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.api.geo.GEOResource;
import io.github.thebusybiscuit.slimefun4.api.geo.ResourceManager;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.geo.GEOMiner;
import java.util.OptionalInt;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.NamespacedKey;

/**
 * This {@link MachineOperation} represents a {@link GEOMiner}
 * mining a {@link GEOResource}.
 *
 * @author iTwins
 *
 * @see GEOMiner
 */
public class GEOMiningOperation extends MiningOperation {

    public static final String TYPE_ID = "geo_mining";

    private final GEOResource resource;

    public GEOMiningOperation(@Nonnull GEOResource resource, int totalTicks) {
        super(resource.getItem().clone(), totalTicks);
        this.resource = resource;
    }

    @Nonnull
    public GEOResource getResource() {
        return resource;
    }

    /**
     * This returns the {@link GEOResource} back to the chunk
     * when the {@link GEOMiningOperation} gets cancelled
     */
    @Override
    public void onCancel(@Nonnull BlockPosition position) {
        ResourceManager resourceManager = Slimefun.getGPSNetwork().getResourceManager();
        OptionalInt supplies =
                resourceManager.getSupplies(resource, position.getWorld(), position.getChunkX(), position.getChunkZ());
        supplies.ifPresent(s -> resourceManager.setSupplies(
                resource, position.getWorld(), position.getChunkX(), position.getChunkZ(), s + 1));
    }

    @Override
    @Nonnull
    public String getOperationTypeId() {
        return TYPE_ID;
    }

    @Override
    @Nonnull
    public String serialize() {
        return getProgress() + "|" + getTotalTicks() + "|" + resource.getKey().toString();
    }

    @Override
    @Nullable public String getDisplayName() {
        return getItemDisplayName(getResult());
    }

    @Nullable public static GEOMiningOperation deserialize(@Nonnull String data) {
        try {
            String[] parts = data.split("\\|", 3);
            if (parts.length < 3) {
                return null;
            }

            int currentTicks = Integer.parseInt(parts[0]);
            int totalTicks = Integer.parseInt(parts[1]);

            NamespacedKey key = NamespacedKey.fromString(parts[2]);
            if (key == null) {
                return null;
            }

            GEOResource resource =
                    Slimefun.getRegistry().getGEOResources().get(key).orElse(null);
            if (resource == null) {
                return null;
            }

            GEOMiningOperation op = new GEOMiningOperation(resource, totalTicks);
            if (currentTicks > 0) {
                op.addProgress(currentTicks);
            }
            return op;
        } catch (Exception e) {
            return null;
        }
    }
}
