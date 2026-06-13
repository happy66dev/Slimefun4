package io.github.thebusybiscuit.slimefun4.core.guide;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Method;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Reflection-based bridge to SlimefunCustomGuide (SCG / SlimefunWeaver).
 * All calls gracefully degrade when SCG is not installed.
 */
public final class ScgBridge {

    private static boolean checked;
    private static boolean available;
    private static Class<?> apiClass;
    private static Method isCustomGuideMode;
    private static Method openOrRestore;
    private static Method pushNestedDetail;
    private static Method isInExternalView;
    private static Method navigateBackItem;
    private static Method suppressPush;
    private static Method clearSuppressPush;

    private ScgBridge() {}

    private static void ensureLoaded() {
        if (checked) return;
        checked = true;
        try {
            apiClass = Class.forName("cn.rmc.slimefunweaver.api.SlimefunWeaverAPI");
            isCustomGuideMode = apiClass.getMethod("isCustomGuideMode", ItemStack.class);
            openOrRestore = apiClass.getMethod("openOrRestore", Player.class, ItemStack.class);
            pushNestedDetail = apiClass.getMethod("pushNestedDetail", Player.class, String.class);
            isInExternalView = apiClass.getMethod("isInExternalView", Player.class);
            navigateBackItem = apiClass.getMethod("navigateBackItem", Player.class);
            suppressPush = apiClass.getMethod("suppressPush", Player.class);
            clearSuppressPush = apiClass.getMethod("clearSuppressPush", Player.class);
            available = true;
        } catch (Exception e) {
            available = false;
        }
    }

    public static boolean isScgEnabled() {
        ensureLoaded();
        if (!available) return false;
        return Bukkit.getPluginManager().isPluginEnabled("SlimefunWeaver");
    }

    public static boolean isCustomGuideMode(@Nonnull ItemStack guide) {
        ensureLoaded();
        if (!available) return false;
        try {
            return (boolean) isCustomGuideMode.invoke(null, guide);
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "SCG bridge: isCustomGuideMode failed", e);
            return false;
        }
    }

    public static void openOrRestore(@Nonnull Player p, @Nonnull ItemStack guide) {
        ensureLoaded();
        if (!available) return;
        try {
            openOrRestore.invoke(null, p, guide);
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "SCG bridge: openOrRestore failed", e);
        }
    }

    public static void pushNestedDetail(@Nonnull Player p, @Nonnull String itemId) {
        ensureLoaded();
        if (!available) return;
        try {
            pushNestedDetail.invoke(null, p, itemId);
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "SCG bridge: pushNestedDetail failed", e);
        }
    }

    public static boolean isInExternalView(@Nonnull Player p) {
        ensureLoaded();
        if (!available) return false;
        try {
            return (boolean) isInExternalView.invoke(null, p);
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "SCG bridge: isInExternalView failed", e);
            return false;
        }
    }

    @Nullable public static String navigateBackItem(@Nonnull Player p) {
        ensureLoaded();
        if (!available) return null;
        try {
            return (String) navigateBackItem.invoke(null, p);
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "SCG bridge: navigateBackItem failed", e);
            return null;
        }
    }

    public static void suppressPush(@Nonnull Player p) {
        ensureLoaded();
        if (!available) return;
        try {
            suppressPush.invoke(null, p);
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "SCG bridge: suppressPush failed", e);
        }
    }

    public static void clearSuppressPush(@Nonnull Player p) {
        ensureLoaded();
        if (!available) return;
        try {
            clearSuppressPush.invoke(null, p);
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "SCG bridge: clearSuppressPush failed", e);
        }
    }
}
