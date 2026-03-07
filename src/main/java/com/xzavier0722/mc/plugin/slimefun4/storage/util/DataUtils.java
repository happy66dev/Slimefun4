package com.xzavier0722.mc.plugin.slimefun4.storage.util;

import city.norain.slimefun4.utils.StringUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.StorageType;
import io.github.thebusybiscuit.slimefun4.core.debug.Debug;
import io.github.thebusybiscuit.slimefun4.core.debug.TestCase;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.logging.Level;
import javax.annotation.Nullable;
import org.bukkit.inventory.ItemStack;

public class DataUtils {
    /**
     * 使用 ItemStack 的 serialize() 方法将物品序列化为 Base64 字符串，用于数据库存储.
     *
     * @param itemStack 要序列化的 {@link ItemStack}
     * @return 序列化后的 Base64 字符串
     */
    public static String serializeItemStack(ItemStack itemStack) {
        Debug.log(TestCase.BACKPACK, "Serializing itemstack: " + itemStack);

        if (itemStack == null) {
            return "";
        }

        try {
            // 使用 Bukkit 的内置序列化方法
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            org.bukkit.util.io.BukkitObjectOutputStream bukkitOutputStream =
                    new org.bukkit.util.io.BukkitObjectOutputStream(outputStream);
            bukkitOutputStream.writeObject(itemStack);
            bukkitOutputStream.close();
            byte[] bytes = outputStream.toByteArray();
            var itemStr = Base64.getEncoder().encodeToString(bytes);

            if (!Slimefun.getConfigManager().isBypassItemLengthCheck()
                    && Slimefun.getDatabaseManager().getBlockDataStorageType() == StorageType.MYSQL
                    && itemStr.length() > 65535) {

                throw new IllegalArgumentException("检测到过大物品, 请联系物品对应插件开发者解决: " + StringUtil.itemStackToString(itemStack)
                        + ", size = " + itemStr.length());
            }

            return itemStr;
        } catch (Throwable e) {
            Slimefun.logger().log(Level.SEVERE, "序列化物品时出现错误, 将存储空值", e);
            return "";
        }
    }

    /**
     * 使用 Bukkit 的内置反序列化方法将 Base64 字符串反序列化为物品对象.
     *
     * @param base64Str 要反序列化的 Base64 字符串
     * @return 反序列化后的 {@link ItemStack} 对象
     */
    @Nullable public static ItemStack deserializeItemStack(String base64Str) {
        if (base64Str == null || base64Str.isEmpty() || base64Str.isBlank()) {
            return null;
        }

        Debug.log(TestCase.BACKPACK, "Deserializing itemstack: " + base64Str);

        try {
            // 解码 Base64 字符串
            byte[] bytes = Base64.getDecoder().decode(base64Str);
            // 使用 Bukkit 的内置反序列化方法
            ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes);
            org.bukkit.util.io.BukkitObjectInputStream bukkitInputStream =
                    new org.bukkit.util.io.BukkitObjectInputStream(inputStream);
            var result = (ItemStack) bukkitInputStream.readObject();
            bukkitInputStream.close();

            Debug.log(TestCase.BACKPACK, "Deserialized itemstack: " + result);

            if (result == null || result.getType().isAir()) {
                Slimefun.logger().log(Level.SEVERE, "反序列化数据库中的物品失败! 对应物品无法显示.");
                return null;
            }

            return result;
        } catch (Throwable ex) {
            Slimefun.logger().log(Level.SEVERE, "反序列化物品时出现错误, 对应物品无法显示", ex);
            return null;
        }
    }

    public static String blockDataBase64(String text) {
        return Slimefun.getDatabaseManager().isBlockDataBase64Enabled() ? base64Encode(text) : text;
    }

    public static String blockDataDebase64(String base64Str) {
        return Slimefun.getDatabaseManager().isBlockDataBase64Enabled() ? base64Decode(base64Str) : base64Str;
    }

    public static String profileDataBase64(String text) {
        return Slimefun.getDatabaseManager().isProfileDataBase64Enabled() ? base64Encode(text) : text;
    }

    public static String profileDataDebase64(String base64Str) {
        return Slimefun.getDatabaseManager().isProfileDataBase64Enabled() ? base64Decode(base64Str) : base64Str;
    }

    public static String base64Encode(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    public static String base64Decode(String base64Str) {
        return new String(Base64.getDecoder().decode(base64Str), StandardCharsets.UTF_8);
    }
}
