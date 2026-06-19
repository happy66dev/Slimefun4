package io.github.thebusybiscuit.slimefun4.core.machines;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.implementation.operations.FuelOperation;
import io.github.thebusybiscuit.slimefun4.implementation.operations.GEOMiningOperation;
import io.github.thebusybiscuit.slimefun4.implementation.operations.MiningOperation;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * A registry for {@link MachineOperation} serializers and deserializers.
 * <p>
 * Built-in types ({@link CraftingOperation}, {@link FuelOperation}, {@link MiningOperation},
 * {@link GEOMiningOperation}) are registered automatically.
 * <p>
 * Addon developers can register custom operation types via {@link #register(String, Function)}.
 *
 * @see MachineOperation#getOperationTypeId()
 * @see MachineOperation#serialize()
 */
public final class OperationSerializers {

    private static final Map<String, Function<String, MachineOperation>> DESERIALIZERS = new ConcurrentHashMap<>();

    static {
        register(CraftingOperation.TYPE_ID, CraftingOperation::deserialize);
        register(FuelOperation.TYPE_ID, FuelOperation::deserialize);
        register(MiningOperation.TYPE_ID, MiningOperation::deserialize);
        register(GEOMiningOperation.TYPE_ID, GEOMiningOperation::deserialize);
    }

    private OperationSerializers() {}

    /**
     * Registers a deserializer for a custom {@link MachineOperation} type.
     * <p>
     * The deserializer function receives the serialized string (from {@link MachineOperation#serialize()})
     * and should return a fully initialized {@link MachineOperation} instance, or null on failure.
     *
     * @param typeId
     *            The unique type identifier (must match {@link MachineOperation#getOperationTypeId()})
     * @param deserializer
     *            A function that reconstructs the operation from its serialized form
     */
    public static void register(@Nonnull String typeId, @Nonnull Function<String, MachineOperation> deserializer) {
        DESERIALIZERS.put(typeId, deserializer);
    }

    /**
     * Deserializes a {@link MachineOperation} from its type identifier and serialized data.
     *
     * @param typeId
     *            The type identifier returned by {@link MachineOperation#getOperationTypeId()}
     * @param data
     *            The serialized string returned by {@link MachineOperation#serialize()}
     *
     * @return The deserialized {@link MachineOperation}, or null if the type is unknown or deserialization fails
     */
    @Nullable public static MachineOperation deserialize(@Nonnull String typeId, @Nonnull String data) {
        Function<String, MachineOperation> deserializer = DESERIALIZERS.get(typeId);
        if (deserializer == null) {
            Slimefun.logger().log(Level.WARNING, "No deserializer registered for operation type: {0}", typeId);
            return null;
        }

        try {
            return deserializer.apply(data);
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "Failed to deserialize operation of type: " + typeId, e);
            return null;
        }
    }

    /**
     * Checks whether a deserializer is registered for the given type identifier.
     *
     * @param typeId
     *            The type identifier to check
     *
     * @return true if a deserializer is registered for this type
     */
    public static boolean isRegistered(@Nonnull String typeId) {
        return DESERIALIZERS.containsKey(typeId);
    }
}
