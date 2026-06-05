package io.github.thebusybiscuit.slimefun4.utils;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.MachineProcessHolder;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineProcessor;
import io.github.thebusybiscuit.slimefun4.core.machines.OperationSerializers;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class MachineStatePersistence {

    private static final String PREFIX = "machine_state_";
    private static final String DB_KEY_SAVED_OPERATION = "saved_operation";
    private static final int PROGRESS_BAR_LENGTH = 10;
    private static final String MACHINE_DAMAGE_WORK_TICKS_KEY = "machine_damage_work_ticks";
    private static final String MACHINE_DAMAGE_CHANCE_KEY = "machine_damage_chance";

    private static volatile NamespacedKey keyHasState;
    private static volatile NamespacedKey keyCharge;
    private static volatile NamespacedKey keyCapacity;
    private static volatile NamespacedKey keyOperationType;
    private static volatile NamespacedKey keyOperationData;
    private static volatile NamespacedKey keyStateLoreLines;
    private static volatile NamespacedKey keyWorkTicks;
    private static volatile NamespacedKey keyDamageChance;

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

    private static NamespacedKey getKeyWorkTicks() {
        if (keyWorkTicks == null) {
            synchronized (MachineStatePersistence.class) {
                if (keyWorkTicks == null) {
                    keyWorkTicks = new NamespacedKey(Slimefun.instance(), PREFIX + "work_ticks");
                }
            }
        }
        return keyWorkTicks;
    }

    private static NamespacedKey getKeyDamageChance() {
        if (keyDamageChance == null) {
            synchronized (MachineStatePersistence.class) {
                if (keyDamageChance == null) {
                    keyDamageChance = new NamespacedKey(Slimefun.instance(), PREFIX + "damage_chance");
                }
            }
        }
        return keyDamageChance;
    }

    private MachineStatePersistence() {}

    @SuppressWarnings("unchecked")
    private static <T extends MachineOperation> void startOperationUnchecked(
            MachineProcessor<?> processor, Block block, MachineOperation op, SlimefunItem sfItem) {
        if (sfItem instanceof MachineProcessHolder<?> holder
                && holder.getMachineOperationClass().isInstance(op)) {
            ((MachineProcessor<T>) processor).startOperation(block, (T) op);
        }
    }

    public static boolean shouldSaveState(@Nonnull SlimefunItem sfItem, @Nonnull Location loc) {
        if (getStoredCharge(sfItem, loc) > 0) {
            return true;
        }
        if (isPersistableOperation(getActiveOperation(sfItem, loc))) {
            return true;
        }
        return getStoredWorkTicks(loc) > 0;
    }

    private static boolean isPersistableOperation(@Nullable MachineOperation operation) {
        return operation != null && operation.getOperationTypeId() != null && operation.serialize() != null;
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
        if (sfItem instanceof MachineProcessHolder<?> holder) {
            return holder.getMachineProcessor().getOperation(loc.getBlock());
        }
        return null;
    }

    private static long getStoredWorkTicks(@Nonnull Location loc) {
        ASlimefunDataContainer data = StorageCacheUtils.getDataContainer(loc);
        if (data == null) {
            return 0;
        }
        String value = data.getData(MACHINE_DAMAGE_WORK_TICKS_KEY);
        if (value != null) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    private static double getStoredDamageChance(@Nonnull Location loc) {
        ASlimefunDataContainer data = StorageCacheUtils.getDataContainer(loc);
        if (data == null) {
            return 0;
        }
        String value = data.getData(MACHINE_DAMAGE_CHANCE_KEY);
        if (value != null) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
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
    private static List<String> buildStateLore(long charge, long capacity, @Nullable MachineOperation op) {
        List<String> lore = new ArrayList<>();

        if (capacity > 0) {
            lore.add(ChatColor.DARK_GRAY + "\u21E8 " + ChatColor.YELLOW + "\u26A1 " + ChatColor.GRAY + charge + " / "
                    + capacity + " J");
        }

        if (op != null) {
            String typeName = op.getDisplayName();
            if (typeName == null) {
                typeName = "???";
            }
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
        String operationTypeId = operation == null ? null : operation.getOperationTypeId();
        String operationData = operation == null ? null : operation.serialize();
        boolean persistOperation = operationTypeId != null && operationData != null;
        long workTicks = getStoredWorkTicks(loc);
        double damageChance = getStoredDamageChance(loc);

        if (charge <= 0 && !persistOperation && workTicks <= 0) {
            return null;
        }

        if (sfItem instanceof EnergyNetComponent enc && enc.isChargeable()) {
            capacity = enc.getCapacity();
            if (charge > 0) {
                pdc.set(getKeyCharge(), PersistentDataType.LONG, charge);
                pdc.set(getKeyCapacity(), PersistentDataType.LONG, capacity);
            }
        }

        if (persistOperation) {
            pdc.set(getKeyOperationType(), PersistentDataType.STRING, operationTypeId);
            pdc.set(getKeyOperationData(), PersistentDataType.STRING, operationData);
        }

        if (workTicks > 0) {
            pdc.set(getKeyWorkTicks(), PersistentDataType.LONG, workTicks);
        }
        if (damageChance > 0) {
            pdc.set(getKeyDamageChance(), PersistentDataType.DOUBLE, damageChance);
        }

        pdc.set(getKeyHasState(), PersistentDataType.BYTE, (byte) 1);

        List<String> stateLore = buildStateLore(charge, capacity, persistOperation ? operation : null);
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
            if (sfItem instanceof MachineProcessHolder<?> holder) {
                MachineOperation op = OperationSerializers.deserialize(opType, opData);
                if (op != null) {
                    startOperationUnchecked(holder.getMachineProcessor(), loc.getBlock(), op, sfItem);
                }
            }
        }

        Long workTicks = pdc.get(getKeyWorkTicks(), PersistentDataType.LONG);
        if (workTicks != null && workTicks > 0) {
            var blockData = StorageCacheUtils.getDataContainer(loc);
            if (blockData != null) {
                blockData.setData(MACHINE_DAMAGE_WORK_TICKS_KEY, String.valueOf(workTicks));
            }
        }

        Double damageChance = pdc.get(getKeyDamageChance(), PersistentDataType.DOUBLE);
        if (damageChance != null && damageChance > 0) {
            var blockData = StorageCacheUtils.getDataContainer(loc);
            if (blockData != null) {
                blockData.setData(MACHINE_DAMAGE_CHANCE_KEY, String.valueOf(damageChance));
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
        pdc.remove(getKeyWorkTicks());
        pdc.remove(getKeyDamageChance());

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

    public static void saveAllOperationsToDatabase() {
        Slimefun.logger().info("[MachineStatePersistence] Saving all active operations to database...");

        int count = 0;

        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            if (item instanceof MachineProcessHolder<?> holder) {
                count += saveProcessorOperations(holder.getMachineProcessor());
            }
        }

        Slimefun.logger().info("[MachineStatePersistence] Saved " + count + " operations to database.");
    }

    private static <T extends MachineOperation> int saveProcessorOperations(@Nonnull MachineProcessor<T> processor) {
        int count = 0;

        for (var entry : processor.getActiveOperations().entrySet()) {
            Location loc = entry.getKey().toLocation();
            T op = entry.getValue();
            if (op != null) {
                String typeId = op.getOperationTypeId();
                String serialized = op.serialize();
                if (typeId == null || serialized == null) {
                    continue;
                }

                var blockData = StorageCacheUtils.getDataContainer(loc);
                if (blockData != null) {
                    blockData.setData(DB_KEY_SAVED_OPERATION, typeId + "|" + serialized);
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
            MachineOperation op = OperationSerializers.deserialize(type, opData);
            if (op != null) {
                startOperationUnchecked(processor, loc.getBlock(), op, sfItem);
                restored = true;
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

    public static void clearSavedOperation(@Nonnull Location loc) {
        var blockData = StorageCacheUtils.getDataContainer(loc);
        if (blockData != null) {
            blockData.removeData(DB_KEY_SAVED_OPERATION);
        }
    }
}
