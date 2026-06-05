package io.github.thebusybiscuit.slimefun4.core.machines;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class MachineFeedbackRegistry {

    private static final Map<String, MachineFeedback> registry = new HashMap<>();

    static {
        for (MachineFeedbackType type : MachineFeedbackType.values()) {
            register(type.name().toLowerCase(), type);
        }
    }

    private MachineFeedbackRegistry() {}

    public static void register(@Nonnull String key, @Nonnull MachineFeedback feedback) {
        registry.put(key.toLowerCase(), feedback);
    }

    @Nullable public static MachineFeedback get(@Nonnull String key) {
        return registry.get(key.toLowerCase());
    }

    @Nonnull
    public static Map<String, MachineFeedback> getAll() {
        return new HashMap<>(registry);
    }
}
