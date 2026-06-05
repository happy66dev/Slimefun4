package io.github.thebusybiscuit.slimefun4.core.machines;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineProcessHolder;
import javax.annotation.Nullable;

/**
 * This represents a {@link MachineOperation} which is handled
 * by a {@link MachineProcessor}.
 *
 * @author TheBusyBiscuit
 *
 * @see MachineProcessor
 * @see MachineProcessHolder
 *
 */
public interface MachineOperation {

    /**
     * This method adds the given amount of ticks to the progress.
     *
     * @param ticks
     *            The amount of ticks to add to the progress
     */
    void addProgress(int ticks);

    /**
     * This returns the amount of progress that has been made.
     * It's basically the amount of elapsed ticks since the {@link MachineOperation}
     * has started.
     *
     * @return The amount of elapsed ticks
     */
    int getProgress();

    /**
     * This returns the amount of total ticks this {@link MachineOperation} takes to complete.
     *
     * @return The amount of total ticks required.
     */
    int getTotalTicks();

    /**
     * This returns the amount of remaining ticks until the {@link MachineOperation}
     * finishes.
     *
     * @return The amount of remaining ticks.
     */
    default int getRemainingTicks() {
        return getTotalTicks() - getProgress();
    }

    /**
     * This returns whether this {@link MachineOperation} has finished.
     *
     * @return Whether this has finished or not.
     */
    default boolean isFinished() {
        return getRemainingTicks() <= 0;
    }

    /**
     * This method is called when a {@link MachineOperation} is interrupted before finishing.
     * Implement to specify behaviour that should happen in this case.
     */
    default void onCancel(BlockPosition position) {}

    /**
     * Returns the unique type identifier for this {@link MachineOperation}.
     * Used by {@link OperationSerializers} to route deserialization to the correct handler.
     *
     * @return The type identifier string, or null if this operation does not support persistence
     */
    @Nullable default String getOperationTypeId() {
        return null;
    }

    /**
     * Serializes this {@link MachineOperation} to a string for persistence.
     * The serialized form will be passed to the registered deserializer in
     * {@link OperationSerializers} when loading.
     *
     * @return The serialized string, or null if this operation does not support persistence
     */
    @Nullable default String serialize() {
        return null;
    }

    /**
     * Returns a human-readable display name for this {@link MachineOperation},
     * used in item lore when the machine is broken with an active operation.
     *
     * @return The display name, or null to omit from lore
     */
    @Nullable default String getDisplayName() {
        return null;
    }
}
