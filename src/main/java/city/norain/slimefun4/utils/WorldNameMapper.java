package city.norain.slimefun4.utils;

import io.github.bakedlibs.dough.config.Config;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;

public final class WorldNameMapper {

    private WorldNameMapper() {}

    @Nonnull
    public static String getFriendlyName(@Nonnull String worldName) {
        Config config = Slimefun.getCfg();
        if (config != null && config.contains("world-name-mapping")) {
            String friendly = config.getString("world-name-mapping." + worldName);
            if (friendly != null && !friendly.isEmpty()) {
                return friendly;
            }
        }

        switch (worldName.toLowerCase()) {
            case "world":
                return "主世界";
            case "world_nether":
                return "地狱";
            case "world_the_end":
                return "末地";
            default:
                return worldName;
        }
    }
}
