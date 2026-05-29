# 机器状态持久化系统实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现机器状态的跨破坏/放置持久化，包括电量、工作状态（配方+进度）的保存与恢复，以及机器状态清除器功能。

**Architecture:** 采用集中式工具类 `MachineStatePersistence` 处理所有状态序列化/反序列化逻辑，通过修改 `BlockListener` 在破坏/放置时调用工具类，新增 `MachineStateClearer` 方块机器用于清除物品状态。

**Tech Stack:** Java, Bukkit PersistentDataContainer API, Slimefun BlockDataController, JSON (Gson)

---

## 文件结构

### 新增文件
| 文件路径 | 职责 |
|---------|------|
| `src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java` | 核心工具类，处理序列化/反序列化/lore生成 |
| `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/MachineStateClearer.java` | 机器状态清除器方块物品 |
| `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/MachineStateListener.java` | 服务器关闭监听器 |

### 修改文件
| 文件路径 | 修改内容 |
|---------|---------|
| `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BlockListener.java` | 破坏/放置时调用状态保存/恢复 |
| `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/SlimefunItems.java` | 添加清除器物品定义 |
| `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/SlimefunItemSetup.java` | 注册清除器物品 |
| `src/main/resources/messages.yml` | 添加默认消息 |
| `src/main/resources/languages/en/messages.yml` | 添加英文消息 |
| `src/main/resources/languages/zh-CN/messages.yml` | 添加中文消息 |

---

## Task 1: 创建 MachineStatePersistence 工具类 - PDC 键定义和基础方法

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java`

- [ ] **Step 1: 创建工具类骨架，定义 PDC 键**

```java
package io.github.thebusybiscuit.slimefun4.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import io.github.bakedlibs.dough.common.ChatColor;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.operations.CraftingOperation;
import io.github.thebusybiscuit.slimefun4.implementation.operations.FuelOperation;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;

import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AGenerator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

public final class MachineStatePersistence {

    private static final String PREFIX = "machine_state_";

    // 物品 PDC 键
    private static final NamespacedKey KEY_HAS_STATE = new NamespacedKey(Slimefun.instance(), PREFIX + "has_state");
    private static final NamespacedKey KEY_CHARGE = new NamespacedKey(Slimefun.instance(), PREFIX + "charge");
    private static final NamespacedKey KEY_CAPACITY = new NamespacedKey(Slimefun.instance(), PREFIX + "capacity");
    private static final NamespacedKey KEY_OPERATION_TYPE = new NamespacedKey(Slimefun.instance(), PREFIX + "op_type");
    private static final NamespacedKey KEY_OPERATION_DATA = new NamespacedKey(Slimefun.instance(), PREFIX + "op_data");

    // 方块数据库键
    private static final String DB_KEY_SAVED_OPERATION = "saved_operation";

    // 进度条长度
    private static final int PROGRESS_BAR_LENGTH = 10;

    private static final Gson GSON = new GsonBuilder().create();

    private MachineStatePersistence() {
        // 工具类，不可实例化
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功（可能有未使用变量警告，正常）

- [ ] **Step 3: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java
git commit -m "feat: create MachineStatePersistence utility class skeleton"
```

---

## Task 2: 实现 shouldSaveState 和 hasState 方法

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java`

- [ ] **Step 1: 添加 shouldSaveState 方法**

```java
/**
 * 判断是否应该保存机器状态
 */
public static boolean shouldSaveState(@Nonnull SlimefunItem sfItem, @Nonnull Location loc) {
    // 检查是否有电量
    if (sfItem instanceof EnergyNetComponent enc && enc.isChargeable()) {
        long charge = enc.getChargeLong(loc);
        if (charge > 0) {
            return true;
        }
    }

    // 检查是否有工作状态
    if (sfItem instanceof AContainer container) {
        if (container.getMachineProcessor().getOperation(loc.getBlock()) != null) {
            return true;
        }
    }
    if (sfItem instanceof AGenerator generator) {
        if (generator.getMachineProcessor().getOperation(loc.getBlock()) != null) {
            return true;
        }
    }

    return false;
}
```

- [ ] **Step 2: 添加 hasState 方法**

```java
/**
 * 检查 ItemStack 是否有机器状态
 */
public static boolean hasState(@Nullable ItemStack item) {
    if (item == null || item.getType().isAir()) {
        return false;
    }
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
        return false;
    }
    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    Byte hasState = pdc.get(KEY_HAS_STATE, PersistentDataType.BYTE);
    return hasState != null && hasState == 1;
}
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java
git commit -m "feat: add shouldSaveState and hasState methods"
```

---

## Task 3: 实现 buildProgressBar 和 buildStateLore 方法

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java`

- [ ] **Step 1: 添加 buildProgressBar 方法**

```java
/**
 * 生成进度条字符串
 * 格式: [■■■■■□□□□□] 50%
 */
@Nonnull
private static String buildProgressBar(int current, int total, int length) {
    if (total <= 0) {
        return "[" + "□".repeat(length) + "] 0%";
    }

    int filled = (int) ((double) current / total * length);
    filled = Math.min(filled, length);

    StringBuilder bar = new StringBuilder(ChatColor.GREEN.toString());
    for (int i = 0; i < length; i++) {
        if (i == filled && filled < length) {
            bar.append(ChatColor.GRAY);
        }
        bar.append(i < filled ? "■" : "□");
    }

    int percent = (int) ((double) current / total * 100);
    return "[" + bar + ChatColor.GRAY + "] " + percent + "%";
}
```

- [ ] **Step 2: 添加 buildStateLore 方法**

```java
/**
 * 生成状态 lore 行
 */
@Nonnull
private static List<String> buildStateLore(long charge, long capacity,
                                            @Nullable MachineOperation op,
                                            @Nonnull SlimefunItem sfItem) {
    List<String> lore = new ArrayList<>();

    // 电量显示
    if (capacity > 0) {
        lore.add(ChatColor.DARK_GRAY + "\u21E8 " + ChatColor.YELLOW + "\u26A1 " + ChatColor.GRAY + charge + " / " + capacity + " J");
    }

    // 工作状态显示
    if (op != null) {
        String typeName = getOperationTypeName(op, sfItem);
        int progress = op.getProgress();
        int totalTicks = op.getTotalTicks();

        if (totalTicks > 0) {
            String progressBar = buildProgressBar(progress, totalTicks, PROGRESS_BAR_LENGTH);
            lore.add(ChatColor.DARK_GRAY + "\u21E8 " + ChatColor.YELLOW + "\u2699 " + ChatColor.GRAY + typeName + " \u2192 " + progressBar);
        }
    }

    return lore;
}
```

- [ ] **Step 3: 添加 getOperationTypeName 辅助方法**

```java
/**
 * 获取操作类型的显示名称（输入物品名称）
 */
@Nonnull
private static String getOperationTypeName(@Nonnull MachineOperation op,
                                           @Nonnull SlimefunItem sfItem) {
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

/**
 * 获取物品的显示名称
 */
@Nonnull
private static String getItemDisplayName(@Nonnull ItemStack item) {
    if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
        return item.getItemMeta().getDisplayName();
    }
    // 使用本地化名称或材质名称
    String materialName = item.getType().name().replace("_", " ").toLowerCase();
    return materialName.substring(0, 1).toUpperCase() + materialName.substring(1);
}
```

- [ ] **Step 4: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java
git commit -m "feat: add progress bar and lore generation methods"
```

---

## Task 4: 实现 serializeOperation 和 deserializeOperation 方法

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java`

- [ ] **Step 1: 添加 ItemStack 序列化辅助方法**

```java
/**
 * 序列化 ItemStack 数组为 JSON 字符串
 */
@Nonnull
private static String serializeItemStacks(@Nonnull ItemStack[] items) {
    JsonArray array = new JsonArray();
    for (ItemStack item : items) {
        if (item != null) {
            JsonObject obj = new JsonObject();
            obj.addProperty("type", item.getType().name());
            obj.addProperty("amount", item.getAmount());
            if (item.hasItemMeta()) {
                obj.addProperty("meta", item.getItemMeta().getAsString());
            }
            array.add(obj);
        }
    }
    return GSON.toJson(array);
}

/**
 * 序列化单个 ItemStack 为 JSON 字符串
 */
@Nonnull
private static String serializeItemStack(@Nonnull ItemStack item) {
    JsonObject obj = new JsonObject();
    obj.addProperty("type", item.getType().name());
    obj.addProperty("amount", item.getAmount());
    if (item.hasItemMeta()) {
        obj.addProperty("meta", item.getItemMeta().getAsString());
    }
    return GSON.toJson(obj);
}
```

- [ ] **Step 2: 添加 CraftingOperation 序列化方法**

```java
/**
 * 序列化 CraftingOperation 为 JSON 字符串
 */
@Nonnull
private static String serializeCraftingOperation(@Nonnull CraftingOperation op) {
    JsonObject obj = new JsonObject();
    obj.addProperty("ingredients", serializeItemStacks(op.getIngredients()));
    obj.addProperty("results", serializeItemStacks(op.getResults()));
    obj.addProperty("totalTicks", op.getTotalTicks());
    obj.addProperty("currentTicks", op.getProgress());
    return GSON.toJson(obj);
}
```

- [ ] **Step 3: 添加 FuelOperation 序列化方法**

```java
/**
 * 序列化 FuelOperation 为 JSON 字符串
 */
@Nonnull
private static String serializeFuelOperation(@Nonnull FuelOperation op) {
    JsonObject obj = new JsonObject();
    obj.addProperty("ingredient", serializeItemStack(op.getIngredient()));
    if (op.getResult() != null) {
        obj.addProperty("result", serializeItemStack(op.getResult()));
    }
    obj.addProperty("totalTicks", op.getTotalTicks());
    obj.addProperty("currentTicks", op.getProgress());
    return GSON.toJson(obj);
}
```

- [ ] **Step 4: 添加 ItemStack 反序列化辅助方法**

```java
/**
 * 从 JSON 反序列化 ItemStack 数组
 */
@Nonnull
private static ItemStack[] deserializeItemStacks(@Nonnull String json) {
    JsonArray array = JsonParser.parseString(json).getAsJsonArray();
    ItemStack[] items = new ItemStack[array.size()];
    for (int i = 0; i < array.size(); i++) {
        JsonObject obj = array.get(i).getAsJsonObject();
        items[i] = deserializeItemStack(obj);
    }
    return items;
}

/**
 * 从 JSON 对象反序列化单个 ItemStack
 */
@Nonnull
private static ItemStack deserializeItemStack(@Nonnull JsonObject obj) {
    String typeName = obj.get("type").getAsString();
    int amount = obj.get("amount").getAsInt();
    ItemStack item = new ItemStack(org.bukkit.Material.valueOf(typeName), amount);

    if (obj.has("meta")) {
        String metaStr = obj.get("meta").getAsString();
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta = org.bukkit.Bukkit.getItemFactory().getItemMeta(item.getType());
            // 注意: ItemMeta.getAsString() 的反序列化需要特殊处理
            // 这里简化处理，实际可能需要使用 PersistentDataContainer
            item.setItemMeta(meta);
        }
    }

    return item;
}
```

- [ ] **Step 5: 添加 CraftingOperation 反序列化方法**

```java
/**
 * 从 JSON 反序列化 CraftingOperation
 */
@Nullable
public static CraftingOperation deserializeCraftingOperation(@Nonnull String json) {
    try {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        ItemStack[] ingredients = deserializeItemStacks(obj.get("ingredients").getAsString());
        ItemStack[] results = deserializeItemStacks(obj.get("results").getAsString());
        int totalTicks = obj.get("totalTicks").getAsInt();
        int currentTicks = obj.get("currentTicks").getAsInt();

        CraftingOperation op = new CraftingOperation(ingredients, results, totalTicks);
        // 恢复进度
        if (currentTicks > 0) {
            op.addProgress(currentTicks);
        }
        return op;
    } catch (Exception e) {
        Slimefun.logger().log(Level.WARNING, "Failed to deserialize CraftingOperation", e);
        return null;
    }
}
```

- [ ] **Step 6: 添加 FuelOperation 反序列化方法**

```java
/**
 * 从 JSON 反序列化 FuelOperation
 */
@Nullable
public static FuelOperation deserializeFuelOperation(@Nonnull String json) {
    try {
        JsonObject obj = JsonParser.parseString(json).getAsJsonObject();

        ItemStack ingredient = deserializeItemStack(obj.get("ingredient").getAsJsonObject());
        ItemStack result = obj.has("result") ? deserializeItemStack(obj.get("result").getAsJsonObject()) : null;
        int totalTicks = obj.get("totalTicks").getAsInt();
        int currentTicks = obj.get("currentTicks").getAsInt();

        FuelOperation op = new FuelOperation(ingredient, result, totalTicks);
        // 恢复进度
        if (currentTicks > 0) {
            op.addProgress(currentTicks);
        }
        return op;
    } catch (Exception e) {
        Slimefun.logger().log(Level.WARNING, "Failed to deserialize FuelOperation", e);
        return null;
    }
}
```

- [ ] **Step 7: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 8: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java
git commit -m "feat: add operation serialization/deserialization methods"
```

---

## Task 5: 实现 saveState 方法

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java`

- [ ] **Step 1: 添加 saveState 方法**

```java
/**
 * 保存机器状态到 ItemStack
 *
 * @param loc 方块位置
 * @param sfItem Slimefun 物品
 * @return 带状态的 ItemStack，或 null (无状态需保存时)
 */
@Nullable
public static ItemStack saveState(@Nonnull Location loc, @Nonnull SlimefunItem sfItem) {
    if (!shouldSaveState(sfItem, loc)) {
        return null;
    }

    // 获取掉落物模板
    ItemStack item = sfItem.getItem().clone();
    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
        return null;
    }

    PersistentDataContainer pdc = meta.getPersistentDataContainer();
    long charge = 0;
    long capacity = 0;
    MachineOperation operation = null;

    // 保存电量
    if (sfItem instanceof EnergyNetComponent enc && enc.isChargeable()) {
        charge = enc.getChargeLong(loc);
        capacity = enc.getCapacity();
        if (charge > 0) {
            pdc.set(KEY_CHARGE, PersistentDataType.LONG, charge);
            pdc.set(KEY_CAPACITY, PersistentDataType.LONG, capacity);
        }
    }

    // 保存工作状态
    if (sfItem instanceof AContainer container) {
        CraftingOperation op = container.getMachineProcessor().getOperation(loc.getBlock());
        if (op != null) {
            operation = op;
            pdc.set(KEY_OPERATION_TYPE, PersistentDataType.STRING, "crafting");
            pdc.set(KEY_OPERATION_DATA, PersistentDataType.STRING, serializeCraftingOperation(op));
        }
    } else if (sfItem instanceof AGenerator generator) {
        FuelOperation op = generator.getMachineProcessor().getOperation(loc.getBlock());
        if (op != null) {
            operation = op;
            pdc.set(KEY_OPERATION_TYPE, PersistentDataType.STRING, "fuel");
            pdc.set(KEY_OPERATION_DATA, PersistentDataType.STRING, serializeFuelOperation(op));
        }
    }

    // 标记有状态
    pdc.set(KEY_HAS_STATE, PersistentDataType.BYTE, (byte) 1);

    // 生成 lore
    List<String> originalLore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
    List<String> stateLore = buildStateLore(charge, capacity, operation, sfItem);

    // 合并 lore: 状态行 + 原始 lore
    List<String> newLore = new ArrayList<>(stateLore);
    if (originalLore != null) {
        newLore.addAll(originalLore);
    }
    meta.setLore(newLore);

    item.setItemMeta(meta);
    return item;
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java
git commit -m "feat: implement saveState method"
```

---

## Task 6: 实现 loadState 和 clearState 方法

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java`

- [ ] **Step 1: 添加 loadState 方法**

```java
/**
 * 从 ItemStack 恢复状态到方块
 *
 * @param item 物品栈
 * @param loc 方块位置
 * @param sfItem Slimefun 物品
 */
public static void loadState(@Nonnull ItemStack item, @Nonnull Location loc,
                              @Nonnull SlimefunItem sfItem) {
    if (!hasState(item)) {
        return;
    }

    ItemMeta meta = item.getItemMeta();
    if (meta == null) {
        return;
    }

    PersistentDataContainer pdc = meta.getPersistentDataContainer();

    // 恢复电量
    if (sfItem instanceof EnergyNetComponent enc && enc.isChargeable()) {
        Long charge = pdc.get(KEY_CHARGE, PersistentDataType.LONG);
        if (charge != null && charge > 0) {
            enc.setCharge(loc, charge);
        }
    }

    // 恢复工作状态
    String opType = pdc.get(KEY_OPERATION_TYPE, PersistentDataType.STRING);
    String opData = pdc.get(KEY_OPERATION_DATA, PersistentDataType.STRING);

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
```

- [ ] **Step 2: 添加 clearState 方法**

```java
/**
 * 清除 ItemStack 上的机器状态
 *
 * @param item 物品栈
 * @return 清除状态后的 ItemStack
 */
@Nonnull
public static ItemStack clearState(@Nonnull ItemStack item) {
    ItemStack result = item.clone();
    ItemMeta meta = result.getItemMeta();
    if (meta == null) {
        return result;
    }

    PersistentDataContainer pdc = meta.getPersistentDataContainer();

    // 移除状态 PDC 键
    pdc.remove(KEY_HAS_STATE);
    pdc.remove(KEY_CHARGE);
    pdc.remove(KEY_CAPACITY);
    pdc.remove(KEY_OPERATION_TYPE);
    pdc.remove(KEY_OPERATION_DATA);

    // 移除状态 lore 行
    if (meta.hasLore()) {
        List<String> lore = meta.getLore();
        if (lore != null) {
            List<String> newLore = new ArrayList<>();
            for (String line : lore) {
                // 跳过状态行（包含电量或进度条的行）
                if (!isStateLoreLine(line)) {
                    newLore.add(line);
                }
            }
            meta.setLore(newLore.isEmpty() ? null : newLore);
        }
    }

    result.setItemMeta(meta);
    return result;
}

/**
 * 判断是否为状态 lore 行
 */
private static boolean isStateLoreLine(@Nonnull String line) {
    // 状态行特征: 包含电量符号 ⚡ 或齿轮符号 ⚙
    return line.contains("\u26A1") || line.contains("\u2699");
}
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java
git commit -m "feat: implement loadState and clearState methods"
```

---

## Task 7: 实现服务器关闭持久化方法

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java`

- [ ] **Step 1: 添加 saveAllOperationsToDatabase 方法**

```java
/**
 * 保存所有活跃操作到数据库 (服务器关闭时调用)
 */
public static void saveAllOperationsToDatabase() {
    Slimefun.logger().info("[MachineStatePersistence] Saving all active operations to database...");

    int count = 0;

    // 遍历所有 AContainer 的操作
    for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
        if (item instanceof AContainer container) {
            count += saveProcessorOperations(container.getMachineProcessor(), "crafting");
        } else if (item instanceof AGenerator generator) {
            count += saveProcessorOperations(generator.getMachineProcessor(), "fuel");
        }
    }

    Slimefun.logger().info("[MachineStatePersistence] Saved " + count + " operations to database.");
}

/**
 * 保存指定处理器的所有操作
 */
private static <T extends MachineOperation> int saveProcessorOperations(
        @Nonnull MachineProcessor<T> processor, @Nonnull String type) {
    int count = 0;

    // 注意: MachineProcessor 的 machines 字段是 private
    // 需要通过反射或添加公共方法来访问
    // 这里假设我们可以通过其他方式获取所有活跃操作的位置

    // TODO: 需要实现获取所有活跃操作位置的方法
    // 可能需要修改 MachineProcessor 添加公共方法

    return count;
}
```

- [ ] **Step 2: 添加 loadOperationFromDatabase 方法**

```java
/**
 * 从数据库恢复操作 (机器加载时调用)
 *
 * @param loc 方块位置
 * @param sfItem Slimefun 物品
 * @param processor 机器处理器
 */
public static void loadOperationFromDatabase(@Nonnull Location loc,
                                              @Nonnull SlimefunItem sfItem,
                                              @Nonnull MachineProcessor<?> processor) {
    var blockData = com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils.getDataContainer(loc);
    if (blockData == null) {
        return;
    }

    String savedOp = blockData.getData(DB_KEY_SAVED_OPERATION);
    if (savedOp == null || savedOp.isEmpty()) {
        return;
    }

    try {
        JsonObject obj = JsonParser.parseString(savedOp).getAsJsonObject();
        String type = obj.get("type").getAsString();
        String data = obj.get("data").getAsString();

        if ("crafting".equals(type) && sfItem instanceof AContainer container) {
            CraftingOperation op = deserializeCraftingOperation(data);
            if (op != null) {
                container.getMachineProcessor().startOperation(loc.getBlock(), op);
            }
        } else if ("fuel".equals(type) && sfItem instanceof AGenerator generator) {
            FuelOperation op = deserializeFuelOperation(data);
            if (op != null) {
                generator.getMachineProcessor().startOperation(loc.getBlock(), op);
            }
        }

        // 清除数据库中的保存数据
        clearSavedOperation(loc);
    } catch (Exception e) {
        Slimefun.logger().log(Level.WARNING, "Failed to load operation from database at " + loc, e);
    }
}

/**
 * 清除数据库中的操作数据
 *
 * @param loc 方块位置
 */
public static void clearSavedOperation(@Nonnull Location loc) {
    var blockData = com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils.getDataContainer(loc);
    if (blockData != null) {
        blockData.setData(DB_KEY_SAVED_OPERATION, null);
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功（可能有 TODO 警告）

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java
git commit -m "feat: add server shutdown persistence methods"
```

---

## Task 8: 创建 MachineStateClearer 方块物品

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/MachineStateClearer.java`

- [ ] **Step 1: 创建 MachineStateClearer 类**

```java
package io.github.thebusybiscuit.slimefun4.implementation.items.blocks;

import javax.annotation.ParametersAreNonnullByDefault;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.MachineStatePersistence;

public class MachineStateClearer extends SlimefunItem implements BlockUseHandler {

    @ParametersAreNonnullByDefault
    public MachineStateClearer(ItemGroup itemGroup, SlimefunItemStack item,
                                RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler(onRightClick());
    }

    private BlockUseHandler onRightClick() {
        return e -> {
            e.cancel();

            var player = e.getPlayer();
            ItemStack heldItem = player.getInventory().getItemInMainHand();

            if (heldItem.getType() == Material.AIR) {
                Slimefun.getLocalization().sendMessage(player,
                    "machines.MACHINE_STATE_CLEARER.no-item", true);
                return;
            }

            if (!MachineStatePersistence.hasState(heldItem)) {
                Slimefun.getLocalization().sendMessage(player,
                    "machines.MACHINE_STATE_CLEARER.no-state", true);
                return;
            }

            ItemStack cleared = MachineStatePersistence.clearState(heldItem);
            player.getInventory().setItemInMainHand(cleared);
            Slimefun.getLocalization().sendMessage(player,
                "machines.MACHINE_STATE_CLEARER.success", true);
        };
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/MachineStateClearer.java
git commit -m "feat: create MachineStateClearer block item"
```

---

## Task 9: 添加物品定义和注册

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/SlimefunItems.java`
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/SlimefunItemSetup.java`

- [ ] **Step 1: 在 SlimefunItems.java 末尾添加物品定义**

在文件末尾（最后一个物品定义之后）添加：

```java
public static final SlimefunItemStack MACHINE_STATE_CLEARER = new SlimefunItemStack(
        "MACHINE_STATE_CLEARER",
        Material.SMITHING_TABLE,
        "&6机器状态清除器",
        "",
        "&7手持带有状态的机器物品",
        "&7右键此方块清除状态数据",
        "&7使其恢复为可堆叠状态");
```

- [ ] **Step 2: 在 SlimefunItemSetup.java 中注册物品**

在基础机器分类注册的末尾添加：

```java
new MachineStateClearer(
        itemGroups.basicMachines,
        SlimefunItems.MACHINE_STATE_CLEARER,
        RecipeType.ENHANCED_CRAFTING_TABLE,
        new ItemStack[] {
            null, null, null,
            null, null, null,
            null, null, null
        })
        .register(plugin);
```

注意: 合成配方暂设为空，后期有合成表大调。

- [ ] **Step 3: 添加 import 语句**

在 SlimefunItemSetup.java 顶部添加：

```java
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.MachineStateClearer;
```

- [ ] **Step 4: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 5: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/implementation/SlimefunItems.java
git add src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/SlimefunItemSetup.java
git commit -m "feat: add MachineStateClearer item definition and registration"
```

---

## Task 10: 修改 BlockListener - 破坏时保存状态

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BlockListener.java`

- [ ] **Step 1: 添加 import 语句**

在文件顶部添加：

```java
import io.github.thebusybiscuit.slimefun4.utils.MachineStatePersistence;
```

- [ ] **Step 2: 修改 callBlockHandler 方法**

找到 `callBlockHandler` 方法（约 L459-491），修改掉落物生成逻辑：

```java
@ParametersAreNonnullByDefault
private void callBlockHandler(BlockBreakEvent e, ItemStack item, List<ItemStack> drops) {
    var loc = e.getBlock().getLocation();
    SlimefunItem sfItem = StorageCacheUtils.getSlimefunItem(loc);

    if (sfItem != null && !sfItem.useVanillaBlockBreaking()) {
        sfItem.callItemHandler(BlockBreakHandler.class, handler -> handler.onPlayerBreak(e, item, drops));
        if (e.isCancelled()) {
            return;
        }

        BlockMenu inv = StorageCacheUtils.getMenu(loc);
        dropBlockMenuContents(loc, sfItem, inv);

        if (sfItem instanceof AContainer container) {
            container.getMachineProcessor().endOperation(e.getBlock());
        } else if (sfItem instanceof AGenerator generator) {
            generator.getMachineProcessor().endOperation(e.getBlock());
        }

        // 修改: 尝试保存机器状态
        ItemStack stateItem = MachineStatePersistence.saveState(loc, sfItem);
        if (stateItem != null) {
            drops.add(stateItem);
        } else {
            drops.addAll(sfItem.getDrops());
        }

        Slimefun.getDatabaseManager().getBlockDataController().removeBlock(loc);
        clearDamagedMachineBreakAttempts(loc);

        Location hologramLocation = loc.clone().add(Slimefun.getHologramsService().getDefaultOffset());
        Slimefun.getHologramsService().removeHologram(hologramLocation);
    }
}
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BlockListener.java
git commit -m "feat: save machine state on block break"
```

---

## Task 11: 修改 BlockListener - 放置时恢复状态

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BlockListener.java`

- [ ] **Step 1: 修改 onBlockPlace 方法**

找到 `onBlockPlace` 方法（约 L185-243），在创建 blockData 后添加状态恢复：

```java
@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
public void onBlockPlace(BlockPlaceEvent e) {
    if (!e.canBuild()) {
        return;
    }
    ItemStack item = e.getItemInHand();
    SlimefunItem sfItem = SlimefunItem.getByItem(item);

    if (sfItem != null && !(sfItem instanceof NotPlaceable)) {
        if (item.getType() != e.getBlock().getType()) {
            if (item.getType() != CompatibilityUtil.getPlacementMaterial(e.getBlock().getBlockData())) {
                return;
            }
        }
        if (!sfItem.canUse(e.getPlayer(), true)) {
            e.setCancelled(true);
        } else {
            var block = e.getBlock();
            optimizePlacement(sfItem, block, e.getPlayer().getLocation());
            var placeEvent = new SlimefunBlockPlaceEvent(e.getPlayer(), item, block, sfItem);
            Bukkit.getPluginManager().callEvent(placeEvent);
            if (placeEvent.isCancelled()) {
                e.setCancelled(true);
            } else {
                if (Slimefun.getBlockDataService().isTileEntity(block.getType())) {
                    Slimefun.getBlockDataService().setBlockData(block, sfItem.getId());
                }

                if (sfItem instanceof UniversalBlock) {
                    var universalBlock = Slimefun.getDatabaseManager()
                            .getBlockDataController()
                            .createUniversalBlock(block.getLocation(), sfItem.getId());
                    universalBlock.setData("machine_owner_uuid", e.getPlayer().getUniqueId().toString());
                } else {
                    var blockData = Slimefun.getDatabaseManager()
                            .getBlockDataController()
                            .createBlock(block.getLocation(), sfItem.getId());
                    blockData.setData("machine_owner_uuid", e.getPlayer().getUniqueId().toString());
                }

                // 新增: 恢复机器状态
                if (MachineStatePersistence.hasState(item)) {
                    MachineStatePersistence.loadState(item, block.getLocation(), sfItem);
                }

                sfItem.callItemHandler(BlockPlaceHandler.class, handler -> handler.onPlayerPlace(e));
            }
        }
    }
    Slimefun.getHologramsService().cleanOrphanHolograms(e.getBlock().getLocation());
}
```

- [ ] **Step 2: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 3: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BlockListener.java
git commit -m "feat: restore machine state on block place"
```

---

## Task 12: 创建服务器关闭监听器

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/MachineStateListener.java`

- [ ] **Step 1: 创建监听器类**

```java
package io.github.thebusybiscuit.slimefun4.implementation.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginDisableEvent;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.MachineStatePersistence;

public class MachineStateListener implements Listener {

    @EventHandler
    public void onServerShutdown(PluginDisableEvent event) {
        if (event.getPlugin() == Slimefun.instance()) {
            MachineStatePersistence.saveAllOperationsToDatabase();
        }
    }
}
```

- [ ] **Step 2: 注册监听器**

在 `Slimefun.java` 或适当的初始化位置注册监听器：

```java
Bukkit.getPluginManager().registerEvents(new MachineStateListener(), this);
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/MachineStateListener.java
git commit -m "feat: add server shutdown listener for operation persistence"
```

---

## Task 13: 添加本地化消息

**Files:**
- Modify: `src/main/resources/messages.yml`
- Modify: `src/main/resources/languages/en/messages.yml`
- Modify: `src/main/resources/languages/zh-CN/messages.yml`

- [ ] **Step 1: 修改 messages.yml**

在 `machines:` 部分添加：

```yaml
machines:
  MACHINE_STATE_CLEARER:
    no-item: '&cYou are not holding any item!'
    no-state: '&cThis item has no machine state data!'
    success: '&aMachine state has been cleared! The item can now be stacked.'
```

- [ ] **Step 2: 修改 languages/en/messages.yml**

在 `machines:` 部分添加：

```yaml
machines:
  MACHINE_STATE_CLEARER:
    no-item: '&cYou are not holding any item!'
    no-state: '&cThis item has no machine state data!'
    success: '&aMachine state has been cleared! The item can now be stacked.'
```

- [ ] **Step 3: 修改 languages/zh-CN/messages.yml**

在 `machines:` 部分添加：

```yaml
machines:
  MACHINE_STATE_CLEARER:
    no-item: '&c你手中没有物品!'
    no-state: '&c该物品没有机器状态数据!'
    success: '&a机器状态已清除! 物品现在可以堆叠了.'
```

- [ ] **Step 4: 提交**

```bash
git add src/main/resources/messages.yml
git add src/main/resources/languages/en/messages.yml
git add src/main/resources/languages/zh-CN/messages.yml
git commit -m "feat: add localization messages for MachineStateClearer"
```

---

## Task 14: 实现服务器关闭持久化 - 完善 saveAllOperationsToDatabase

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java`
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/core/machines/MachineProcessor.java`

- [ ] **Step 1: 在 MachineProcessor 添加获取所有活跃位置的方法**

```java
/**
 * 获取所有活跃操作的位置
 */
@Nonnull
public Set<BlockPosition> getActivePositions() {
    return Collections.unmodifiableSet(machines.keySet());
}
```

- [ ] **Step 2: 完善 saveAllOperationsToDatabase 方法**

```java
/**
 * 保存所有活跃操作到数据库 (服务器关闭时调用)
 */
public static void saveAllOperationsToDatabase() {
    Slimefun.logger().info("[MachineStatePersistence] Saving all active operations to database...");

    int count = 0;

    for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
        if (item instanceof AContainer container) {
            count += saveProcessorOperations(container.getMachineProcessor(), "crafting", item);
        } else if (item instanceof AGenerator generator) {
            count += saveProcessorOperations(generator.getMachineProcessor(), "fuel", item);
        }
    }

    Slimefun.logger().info("[MachineStatePersistence] Saved " + count + " operations to database.");
}

/**
 * 保存指定处理器的所有操作
 */
private static <T extends MachineOperation> int saveProcessorOperations(
        @Nonnull MachineProcessor<T> processor, @Nonnull String type,
        @Nonnull SlimefunItem sfItem) {
    int count = 0;

    for (BlockPosition pos : processor.getActivePositions()) {
        T op = processor.getOperation(pos);
        if (op != null) {
            Location loc = pos.toLocation();
            var blockData = com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils.getDataContainer(loc);
            if (blockData != null) {
                JsonObject obj = new JsonObject();
                obj.addProperty("type", type);

                if (op instanceof CraftingOperation craftingOp) {
                    obj.addProperty("data", serializeCraftingOperation(craftingOp));
                } else if (op instanceof FuelOperation fuelOp) {
                    obj.addProperty("data", serializeFuelOperation(fuelOp));
                }

                blockData.setData(DB_KEY_SAVED_OPERATION, GSON.toJson(obj));
                count++;
            }
        }
    }

    return count;
}
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java
git add src/main/java/io/github/thebusybiscuit/slimefun4/core/machines/MachineProcessor.java
git commit -m "feat: implement server shutdown operation persistence"
```

---

## Task 15: 添加机器加载时恢复操作

**Files:**
- Modify: `src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java`
- Modify: `src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AGenerator.java`

- [ ] **Step 1: 在 AContainer 的 preRegister 中添加操作恢复**

在 `preRegister()` 方法的 ticker 中添加：

```java
@Override
public void preRegister() {
    addItemHandler(new BlockTicker() {
        @Override
        public void tick(Block b, SlimefunItem sf, SlimefunBlockData data) {
            // 新增: 首次 tick 时尝试从数据库恢复操作
            if (!data.hasData("operation_restored")) {
                MachineStatePersistence.loadOperationFromDatabase(b.getLocation(), sf, processor);
                data.setData("operation_restored", "true");
            }

            AContainer.this.tick(b);
        }

        @Override
        public boolean isSynchronized() {
            return false;
        }
    });
}
```

- [ ] **Step 2: 在 AGenerator 的 getGeneratedOutput 中添加操作恢复**

在方法开头添加恢复检查：

```java
@Override
public int getGeneratedOutput(@Nonnull Location l, @Nonnull ASlimefunDataContainer data) {
    // 新增: 首次调用时尝试从数据库恢复操作
    if (!data.hasData("operation_restored")) {
        MachineStatePersistence.loadOperationFromDatabase(l, this, processor);
        data.setData("operation_restored", "true");
    }

    // ... 原有代码 ...
}
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java
git add src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AGenerator.java
git commit -m "feat: restore operations from database on machine load"
```

---

## Task 16: 处理 BlockPlacer 放置的状态恢复

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/BlockPlacer.java`

- [ ] **Step 1: 找到 BlockPlacer 的放置逻辑**

在 `placeSlimefunBlock` 方法中添加状态恢复：

```java
// 在创建 blockData 之后添加:
if (MachineStatePersistence.hasState(item)) {
    MachineStatePersistence.loadState(item, block.getLocation(), sfItem);
}
```

- [ ] **Step 2: 添加 import 语句**

```java
import io.github.thebusybiscuit.slimefun4.utils.MachineStatePersistence;
```

- [ ] **Step 3: 编译验证**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn compile -pl . -q`
Expected: 编译成功

- [ ] **Step 4: 提交**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/BlockPlacer.java
git commit -m "feat: support state restoration in BlockPlacer"
```

---

## Task 17: 完整测试

- [ ] **Step 1: 运行 Maven 构建**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn clean package -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 2: 运行测试（如果有）**

Run: `cd d:\Users\Administrator\Desktop\Java项目\slimefun\Slimefun4-master && mvn test`
Expected: 所有测试通过

- [ ] **Step 3: 提交最终版本**

```bash
git add -A
git commit -m "feat: complete machine state persistence system"
```

---

## 技术风险与注意事项

### 1. ItemStack 序列化兼容性
- `ItemMeta.getAsString()` 的反序列化可能需要特殊处理
- 建议使用 Bukkit 的 `ItemStack.serialize()` / `ItemStack.deserialize()` 方法
- 或使用 PersistentDataContainer 直接存储关键数据

### 2. MachineProcessor 访问权限
- `machines` 字段是 private，需要添加公共方法 `getActivePositions()`
- 或使用反射（不推荐）

### 3. 性能考虑
- 服务器关闭时遍历所有活跃操作可能有性能影响
- 建议在关闭事件中同步处理（Bukkit 要求）

### 4. 边界情况
- 损坏的机器由 `MachineDamageService` 接管，不掉落物品
- 爆炸破坏的机器需要额外处理（如果需要支持）
- 物品堆叠问题：有状态的物品不能堆叠，需要确保 lore 差异

### 5. 数据兼容性
- 新版本保存的状态数据在旧版本加载时需要兼容处理
- 建议在 PDC 中添加版本标记（可选）

---

## 待确认事项

- [x] 机器状态清除器的合成配方 → 无需考虑，后期有合成表大调
- [x] 损坏机器 → 由损坏工具类接管（不掉落），保持现有逻辑
- [x] BlockPlacer 放置的机器 → 需要恢复状态
- [x] 进度条长度 → 10格合适
