package io.github.thebusybiscuit.slimefun4.utils;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.DataUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineProcessor;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.implementation.operations.FuelOperation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AGenerator;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class MachineStatePersistence {

    private static final String PREFIX = "machine_state_";
    private static final String DB_KEY_SAVED_OPERATION = "saved_operation";
    private static final int PROGRESS_BAR_LENGTH = 10;
    private static final Set<String> DATABASE_RESTORE_CHECKS = ConcurrentHashMap.newKeySet();

    private static volatile NamespacedKey keyHasState;
    private static volatile NamespacedKey keyCharge;
    private static volatile NamespacedKey keyCapacity;
    private static volatile NamespacedKey keyOperationType;
    private static volatile NamespacedKey keyOperationData;
    private static volatile NamespacedKey keyStateLoreLines;

    private static NamespacedKey getKeyHasState() {
        if (keyHasState == null) {
            synchronized (MachineStatePersistence.class) {
                if (keyHasState == null) {
                    keyHasState = new NamespacedKey(Slimefun.instance(), PREFIX + "has_state");
                }
            }
        }
        return keyHasState;
    }

    private static NamespacedKey getKeyCharge() {
        if (keyCharge == null) {
            synchronized (MachineStatePersistence.class) {
                if (keyCharge == null) {
                    keyCharge = new NamespacedKey(Slimefun.instance(), PREFIX + "charge");
                }
            }
        }
        return keyCharge;
    }

    private static NamespacedKey getKeyCapacity() {
        if (keyCapacity == null) {
            synchronized (MachineStatePersistence.class) {
                if (keyCapacity == null) {
                    keyCapacity = new NamespacedKey(Slimefun.instance(), PREFIX + "capacity");
                }
            }
        }
        return keyCapacity;
    }

    private static NamespacedKey getKeyOperationType() {
        if (keyOperationType == null) {
            synchronized (MachineStatePersistence.class) {
                if (keyOperationType == null) {
                    keyOperationType = new NamespacedKey(Slimefun.instance(), PREFIX + "op_type");
                }
            }
        }
        return keyOperationType;
    }

    private static NamespacedKey getKeyOperationData() {
        if (keyOperationData == null) {
            synchronized (MachineStatePersistence.class) {
                if (keyOperationData == null) {
                    keyOperationData = new NamespacedKey(Slimefun.instance(), PREFIX + "op_data");
                }
            }
        }
        return keyOperationData;
    }

    private static NamespacedKey getKeyStateLoreLines() {
        if (keyStateLoreLines == null) {
            synchronized (MachineStatePersistence.class) {
                if (keyStateLoreLines == null) {
                    keyStateLoreLines = new NamespacedKey(Slimefun.instance(), PREFIX + "state_lore_lines");
                }
            }
        }
        return keyStateLoreLines;
    }

    private MachineStatePersistence() {}

    public static boolean shouldSaveState(@Nonnull SlimefunItem sfItem, @Nonnull Location loc) {
        if (getStoredCharge(sfItem, loc) > 0) {
            return true;
        }
        return getActiveOperation(sfItem, loc) != null;
    }

    private static long getStoredCharge(@Nonnull SlimefunItem sfItem, @Nonnull Location loc) {
        if (!(sfItem instanceof EnergyNetComponent enc) || !enc.isChargeable()) {
            return 0;
        }

        ASlimefunDataContainer data = StorageCacheUtils.getDataContainer(loc);
        if (data != null && data.isDataLoaded()) {
            return enc.getChargeLong(loc, data);
        }

        return enc.getChargeLong(loc);
    }

    @Nullable private static MachineOperation getActiveOperation(@Nonnull SlimefunItem sfItem, @Nonnull Location loc) {
        if (sfItem instanceof AContainer container) {
            return container.getMachineProcessor().getOperation(loc.getBlock());
        }
        if (sfItem instanceof AGenerator generator) {
            return generator.getMachineProcessor().getOperation(loc.getBlock());
        }
        return null;
    }

    public static boolean hasState(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Byte hasState = pdc.get(getKeyHasState(), PersistentDataType.BYTE);
        return hasState != null && hasState == 1;
    }

    @Nonnull
    private static String buildProgressBar(int current, int total, int length) {
        if (total <= 0) {
            return "[" + "\u25A1".repeat(length) + "] 0%";
        }

        int clampedCurrent = Math.max(0, Math.min(current, total));
        int filled = clampedCurrent * length / total;

        StringBuilder bar = new StringBuilder(ChatColor.GREEN.toString());
        for (int i = 0; i < length; i++) {
            if (i == filled && filled < length) {
                bar.append(ChatColor.GRAY);
            }
            bar.append(i < filled ? "\u25A0" : "\u25A1");
        }

        int percent = clampedCurrent * 100 / total;
        return "[" + bar + ChatColor.GRAY + "] " + percent + "%";
    }

    @Nonnull
    private static List<String> buildStateLore(
            long charge, long capacity, @Nullable MachineOperation op, @Nonnull SlimefunItem sfItem) {
        List<String> lore = new ArrayList<>();

        if (capacity > 0) {
            lore.add(ChatColor.DARK_GRAY + "\u21E8 " + ChatColor.YELLOW + "\u26A1 " + ChatColor.GRAY + charge + " / "
                    + capacity + " J");
        }

        if (op != null) {
            String typeName = getOperationTypeName(op, sfItem);
            int progress = op.getProgress();
            int totalTicks = op.getTotalTicks();

            if (totalTicks > 0) {
                String progressBar = buildProgressBar(progress, totalTicks, PROGRESS_BAR_LENGTH);
                lore.add(ChatColor.DARK_GRAY + "\u21E8 " + ChatColor.YELLOW + "\u2699 " + ChatColor.GRAY + typeName
                        + " \u2192 " + progressBar);
            }
        }

        return lore;
    }

    @Nonnull
    private static String getOperationTypeName(@Nonnull MachineOperation op, @Nonnull SlimefunItem sfItem) {
        if (op instanceof CraftingOperation craftingOp) {
            ItemStack[] ingredients = craftingOp.getIngredients();
            if (ingredients != null && ingredients.length > 0 && ingredients[0] != null) {
                return getItemDisplayName(ingredients[0]);
            }
        } else if (op instanceof FuelOperation fuelOp) {
            ItemStack ingredient = fuelOp.getIngredient();
            if (ingredient != null) {
                return getItemDisplayName(ingredient);
            }
        }
        return "???";
    }

    @Nonnull
    private static String getItemDisplayName(@Nonnull ItemStack item) {
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        if (meta != null && meta.hasDisplayName()) {
            return meta.getDisplayName();
        }
        String materialName = item.getType().name().replace("_", " ").toLowerCase();
        return materialName.substring(0, 1).toUpperCase() + materialName.substring(1);
    }

    @Nullable public static ItemStack saveState(@Nonnull Location loc, @Nonnull SlimefunItem sfItem) {
        ItemStack item = sfItem.getItem().clone();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        long charge = getStoredCharge(sfItem, loc);
        long capacity = 0;
        MachineOperation operation = getActiveOperation(sfItem, loc);

        if (charge <= 0 && operation == null) {
            return null;
        }

        if (sfItem instanceof EnergyNetComponent enc && enc.isChargeable()) {
            capacity = enc.getCapacity();
            if (charge > 0) {
                pdc.set(getKeyCharge(), PersistentDataType.LONG, charge);
                pdc.set(getKeyCapacity(), PersistentDataType.LONG, capacity);
            }
        }

        if (operation instanceof CraftingOperation craftingOp) {
            pdc.set(getKeyOperationType(), PersistentDataType.STRING, "crafting");
            pdc.set(getKeyOperationData(), PersistentDataType.STRING, serializeCraftingOperation(craftingOp));
        } else if (operation instanceof FuelOperation fuelOp) {
            pdc.set(getKeyOperationType(), PersistentDataType.STRING, "fuel");
            pdc.set(getKeyOperationData(), PersistentDataType.STRING, serializeFuelOperation(fuelOp));
        }

        pdc.set(getKeyHasState(), PersistentDataType.BYTE, (byte) 1);

        List<String> stateLore = buildStateLore(charge, capacity, operation, sfItem);
        pdc.set(getKeyStateLoreLines(), PersistentDataType.INTEGER, stateLore.size());

        List<String> newLore = new ArrayList<>(stateLore);
        if (meta.hasLore()) {
            List<String> originalLore = meta.getLore();
            if (originalLore != null) {
                newLore.addAll(originalLore);
            }
        }
        meta.setLore(newLore);

        item.setItemMeta(meta);
        return item;
    }

    public static void loadState(@Nonnull ItemStack item, @Nonnull Location loc, @Nonnull SlimefunItem sfItem) {
        if (!hasState(item)) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (sfItem instanceof EnergyNetComponent enc && enc.isChargeable()) {
            Long charge = pdc.get(getKeyCharge(), PersistentDataType.LONG);
            if (charge != null && charge > 0) {
                enc.setCharge(loc, charge);
            }
        }

        String opType = pdc.get(getKeyOperationType(), PersistentDataType.STRING);
        String opData = pdc.get(getKeyOperationData(), PersistentDataType.STRING);

        if (opType != null && opData != null) {
            if ("crafting".equals(opType) && sfItem instanceof AContainer container) {
                CraftingOperation op = deserializeCraftingOperation(opData);
                if (op != null) {
                    container.getMachineProcessor().startOperation(loc.getBlock(), op);
                }
            } else if ("fuel".equals(opType) && sfItem instanceof AGenerator generator) {
                FuelOperation op = deserializeFuelOperation(opData);
                if (op != null) {
                    generator.getMachineProcessor().startOperation(loc.getBlock(), op);
                }
            }
        }
    }

    @Nonnull
    public static ItemStack clearState(@Nonnull ItemStack item) {
        ItemStack result = item.clone();
        ItemMeta meta = result.getItemMeta();
        if (meta == null) {
            return result;
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        Integer storedCount = pdc.get(getKeyStateLoreLines(), PersistentDataType.INTEGER);
        int stateLoreLines = storedCount != null ? storedCount : 0;

        pdc.remove(getKeyHasState());
        pdc.remove(getKeyCharge());
        pdc.remove(getKeyCapacity());
        pdc.remove(getKeyOperationType());
        pdc.remove(getKeyOperationData());
        pdc.remove(getKeyStateLoreLines());

        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                int removeCount = Math.min(stateLoreLines, lore.size());
                List<String> newLore = new ArrayList<>(lore.subList(removeCount, lore.size()));
                meta.setLore(newLore.isEmpty() ? null : newLore);
            }
        }

        result.setItemMeta(meta);
        return result;
    }

    @Nonnull
    private static String serializeCraftingOperation(@Nonnull CraftingOperation op) {
        StringBuilder sb = new StringBuilder();
        sb.append("crafting|");
        sb.append(op.getTotalTicks()).append("|");
        sb.append(op.getProgress()).append("|");

        ItemStack[] ingredients = op.getIngredients();
        sb.append(ingredients.length).append("|");
        for (ItemStack ingredient : ingredients) {
            sb.append(DataUtils.serializeItemStack(ingredient)).append(";");
        }
        sb.append("|");

        ItemStack[] results = op.getResults();
        sb.append(results.length).append("|");
        for (ItemStack result : results) {
            sb.append(DataUtils.serializeItemStack(result)).append(";");
        }

        return sb.toString();
    }

    @Nonnull
    private static String serializeFuelOperation(@Nonnull FuelOperation op) {
        StringBuilder sb = new StringBuilder();
        sb.append("fuel|");
        sb.append(op.getTotalTicks()).append("|");
        sb.append(op.getProgress()).append("|");
        sb.append(DataUtils.serializeItemStack(op.getIngredient())).append("|");

        ItemStack result = op.getResult();
        sb.append(result != null ? DataUtils.serializeItemStack(result) : "");

        return sb.toString();
    }

    @Nullable public static CraftingOperation deserializeCraftingOperation(@Nonnull String data) {
        try {
            String[] parts = data.split("\\|", 7);
            if (parts.length < 6 || !"crafting".equals(parts[0])) {
                return null;
            }

            int totalTicks = Integer.parseInt(parts[1]);
            int currentTicks = Integer.parseInt(parts[2]);

            int ingredientCount = Integer.parseInt(parts[3]);
            if (ingredientCount < 0 || ingredientCount > 64) {
                return null;
            }
            String[] ingredientData = parts[4].split(";", -1);
            ItemStack[] ingredients = new ItemStack[ingredientCount];
            for (int i = 0; i < ingredientCount && i < ingredientData.length; i++) {
                ingredients[i] = DataUtils.deserializeItemStack(ingredientData[i]);
            }

            int resultCount = Integer.parseInt(parts[5]);
            if (resultCount < 0 || resultCount > 64) {
                return null;
            }
            String[] resultData = parts.length > 6 ? parts[6].split(";", -1) : new String[0];
            ItemStack[] results = new ItemStack[resultCount];
            for (int i = 0; i < resultCount && i < resultData.length; i++) {
                results[i] = DataUtils.deserializeItemStack(resultData[i]);
            }

            CraftingOperation op = new CraftingOperation(ingredients, results, totalTicks);
            if (currentTicks > 0) {
                op.addProgress(currentTicks);
            }
            return op;
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "Failed to deserialize CraftingOperation", e);
            return null;
        }
    }

    @Nullable public static FuelOperation deserializeFuelOperation(@Nonnull String data) {
        try {
            String[] parts = data.split("\\|", 5);
            if (parts.length < 4 || !"fuel".equals(parts[0])) {
                return null;
            }

            int totalTicks = Integer.parseInt(parts[1]);
            int currentTicks = Integer.parseInt(parts[2]);

            ItemStack ingredient = DataUtils.deserializeItemStack(parts[3]);
            if (ingredient == null) {
                return null;
            }

            ItemStack result = parts.length > 4 ? DataUtils.deserializeItemStack(parts[4]) : null;

            FuelOperation op = new FuelOperation(ingredient, result, totalTicks);
            if (currentTicks > 0) {
                op.addProgress(currentTicks);
            }
            return op;
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "Failed to deserialize FuelOperation", e);
            return null;
        }
    }

    public static void saveAllOperationsToDatabase() {
        Slimefun.logger().info("[MachineStatePersistence] Saving all active operations to database...");

        int count = 0;

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (item instanceof AContainer container) {
                count += saveProcessorOperations(container.getMachineProcessor(), "crafting");
            } else if (item instanceof AGenerator generator) {
                count += saveProcessorOperations(generator.getMachineProcessor(), "fuel");
            }
        }

        Slimefun.logger().info("[MachineStatePersistence] Saved " + count + " operations to database.");
    }

    private static <T extends MachineOperation> int saveProcessorOperations(
            @Nonnull MachineProcessor<T> processor, @Nonnull String type) {
        int count = 0;

        for (var entry : processor.getActiveOperations().entrySet()) {
            Location loc = entry.getKey().toLocation();
            T op = entry.getValue();
            if (op != null) {
                var blockData = StorageCacheUtils.getDataContainer(loc);
                if (blockData != null) {
                    String serialized;
                    if (op instanceof CraftingOperation craftingOp) {
                        serialized = serializeCraftingOperation(craftingOp);
                    } else if (op instanceof FuelOperation fuelOp) {
                        serialized = serializeFuelOperation(fuelOp);
                    } else {
                        continue;
                    }

                    blockData.setData(DB_KEY_SAVED_OPERATION, type + "|" + serialized);
                    count++;
                }
            }
        }

        return count;
    }

    public static void loadOperationFromDatabase(
            @Nonnull Location loc, @Nonnull SlimefunItem sfItem, @Nonnull MachineProcessor<?> processor) {
        var blockData = StorageCacheUtils.getDataContainer(loc);
        if (blockData == null || !blockData.isDataLoaded()) {
            return;
        }

        String savedOp = blockData.getData(DB_KEY_SAVED_OPERATION);
        if (savedOp == null || savedOp.isEmpty()) {
            return;
        }

        if (processor.getOperation(loc) != null) {
            clearSavedOperation(loc);
            return;
        }

        try {
            int separatorIndex = savedOp.indexOf('|');
            if (separatorIndex <= 0) {
                clearSavedOperation(loc);
                return;
            }

            String type = savedOp.substring(0, separatorIndex);
            String opData = savedOp.substring(separatorIndex + 1);

            boolean restored = false;
            if ("crafting".equals(type) && sfItem instanceof AContainer container) {
                CraftingOperation op = deserializeCraftingOperation(opData);
                if (op != null) {
                    container.getMachineProcessor().startOperation(loc.getBlock(), op);
                    restored = true;
                }
            } else if ("fuel".equals(type) && sfItem instanceof AGenerator generator) {
                FuelOperation op = deserializeFuelOperation(opData);
                if (op != null) {
                    generator.getMachineProcessor().startOperation(loc.getBlock(), op);
                    restored = true;
                }
            }

            clearSavedOperation(loc);

            if (restored) {
                Slimefun.logger().log(Level.FINE, "[MachineStatePersistence] Restored operation at " + loc);
            }
        } catch (Exception e) {
            Slimefun.logger().log(Level.WARNING, "Failed to load operation from database at " + loc, e);
            clearSavedOperation(loc);
        }
    }

    public static void loadOperationFromDatabaseOnce(
            @Nonnull Location loc, @Nonnull SlimefunItem sfItem, @Nonnull MachineProcessor<?> processor) {
        String key = getLocationKey(loc);
        if (!DATABASE_RESTORE_CHECKS.add(key)) {
            return;
        }

        try {
            loadOperationFromDatabase(loc, sfItem, processor);
        } finally {
            DATABASE_RESTORE_CHECKS.remove(key);
        }
    }

    @Nonnull
    private static String getLocationKey(@Nonnull Location loc) {
        String world = loc.getWorld() == null ? "null" : loc.getWorld().getUID().toString();
        return world + ':' + loc.getBlockX() + ':' + loc.getBlockY() + ':' + loc.getBlockZ();
    }

    public static void clearRestoreChecks() {
        DATABASE_RESTORE_CHECKS.clear();
    }

    public static void clearSavedOperation(@Nonnull Location loc) {
        var blockData = StorageCacheUtils.getDataContainer(loc);
        if (blockData != null) {
            blockData.removeData(DB_KEY_SAVED_OPERATION);
        }
    }
}
