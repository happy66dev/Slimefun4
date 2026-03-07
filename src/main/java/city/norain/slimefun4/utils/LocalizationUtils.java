package city.norain.slimefun4.utils;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Material;

public class LocalizationUtils {

    private static final Map<String, String> ITEM_LOCALIZATIONS = new HashMap<>();
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) {
            return;
        }

        try (InputStream inputStream = LocalizationUtils.class.getResourceAsStream("/zh_cn.json")) {
            if (inputStream != null) {
                JsonObject jsonObject = JsonParser.parseReader(
                                new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                        .getAsJsonObject();

                int count = 0;
                for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                    String key = entry.getKey();
                    if (key.startsWith("item.minecraft.")) {
                        ITEM_LOCALIZATIONS.put(key, entry.getValue().getAsString());
                        count++;
                    }
                }

                Slimefun.logger().info("Loaded " + count + " item localizations from zh_cn.json");
                // 测试红石粉的本地化
                if (ITEM_LOCALIZATIONS.containsKey("item.minecraft.redstone")) {
                    Slimefun.logger()
                            .info("Found redstone localization: " + ITEM_LOCALIZATIONS.get("item.minecraft.redstone"));
                } else {
                    Slimefun.logger().info("No redstone localization found");
                }
            } else {
                Slimefun.logger().warning("Could not find zh_cn.json file");
            }
        } catch (Exception e) {
            Slimefun.logger().severe("Error loading zh_cn.json:");
            e.printStackTrace();
        }

        initialized = true;
    }

    public static String getItemName(Material material) {
        if (!initialized) {
            initialize();
        }

        String key = "item.minecraft." + material.name().toLowerCase();
        String name = ITEM_LOCALIZATIONS.get(key);
        if (name != null) {
            Slimefun.logger().info("Found localization for " + key + ": " + name);
            return name;
        } else {
            Slimefun.logger().info("No localization found for " + key + ", using material name: " + material.name());
            return material.name();
        }
    }
}
