# 机器状态持久化系统设计文档

**日期**: 2026-05-30
**状态**: 待审核
**范围**: 机器破坏/放置时的状态保存与恢复

---

## 1. 概述

### 1.1 目标

实现机器状态的跨破坏/放置持久化，包括：
- 破坏机器时将电量、工作状态（配方+进度）保存到掉落物的 NBT 中
- 放置机器时自动从 NBT 恢复状态
- 服务器关闭时将内存中的工作状态持久化到数据库
- 机器加载时从数据库恢复工作状态
- 提供机器状态清除器用于清除物品状态使其可堆叠

### 1.2 适用范围

| 机器类型 | 保存内容 | Lore 显示 |
|---------|---------|----------|
| 用电器 (AContainer) | 电量 + 工作状态 | 存电量 + 输入物→[进度条]百分比 |
| 发电机 (AGenerator) | 电量 + 工作状态 | 存电量 + 燃料→[进度条]百分比 |
| 电容 (Capacitor) | 电量 | 存电量 |
| 连接器 | ❌ 不包含 | ❌ |

### 1.3 条件判断

- **写入状态条件**：机器有电量 > 0 **或** 有活跃的工作状态
- **不写入条件**：没电且无工作状态 → 使用原版掉落逻辑

---

## 2. 架构设计

### 2.1 方案选择

采用**方案 A：集中式工具类**，创建 `MachineStatePersistence` 工具类集中处理所有状态序列化/反序列化逻辑。

**理由**：
- 符合现有项目模式（如 `ChargeUtils`、`SlimefunUtils`）
- 集中管理，易于维护
- 不需要修改抽象类层级

### 2.2 数据流

```
[机器破坏时]
    |
    +-- BlockListener.onBlockBreak()
            |
            +-- MachineStatePersistence.saveState(location, sfItem)
                    |
                    +-- 读取 blockData: energy-charge, CraftingOperation
                    +-- 判断: 有电量 OR 有工作状态?
                    |       |
                    |       +-- YES → 序列化到 ItemStack PDC + 添加 lore
                    |       +-- NO  → 返回 null (使用原版掉落)
                    |
                    +-- 用返回的 ItemStack 替换 sfItem.getDrops() 的结果

[机器放置时]
    |
    +-- BlockListener.onBlockPlace()
            |
            +-- MachineStatePersistence.loadState(item, location, sfItem)
                    |
                    +-- 检查 ItemStack PDC 是否有状态数据
                    +-- YES → 恢复 energy-charge, 恢复 CraftingOperation
                    +-- NO  → 跳过 (使用默认值)

[服务器关闭]
    |
    +-- 监听 PluginDisableEvent
            |
            +-- MachineStatePersistence.saveAllOperationsToDatabase()
                    |
                    +-- 遍历所有 MachineProcessor 中的活跃操作
                    +-- 序列化 CraftingOperation → JSON
                    +-- 写入 blockData: "saved_operation"

[机器加载 (区块加载)]
    |
    +-- MachineStatePersistence.loadOperationFromDatabase()
            |
            +-- 检查 blockData 是否有 "saved_operation"
            +-- YES → 反序列化 → 注册到 MachineProcessor → 清除数据库条目
            +-- NO  → 跳过

[机器状态清除器]
    |
    +-- 玩家右键方块
            |
            +-- 检查手持物品是否有状态
            +-- YES → 清除 PDC 状态键 + 移除 lore 状态行
            +-- NO  → 提示无状态
```

---

## 3. 详细设计

### 3.1 MachineStatePersistence 工具类

**文件位置**: `src/main/java/io/github/thebusybiscuit/slimefun4/utils/MachineStatePersistence.java`

#### 3.1.1 PDC 键定义

```java
// 物品 PDC 键 (存储在 ItemStack 上)
private static final String PREFIX = "machine_state_";
private static final NamespacedKey KEY_CHARGE = new NamespacedKey(Slimefun.instance(), PREFIX + "charge");
private static final NamespacedKey KEY_OPERATION = new NamespacedKey(Slimefun.instance(), PREFIX + "operation");
private static final NamespacedKey KEY_HAS_STATE = new NamespacedKey(Slimefun.instance(), PREFIX + "has_state");

// 方块数据库键 (存储在 SlimefunBlockData 中)
private static final String DB_KEY_SAVED_OPERATION = "saved_operation";
```

#### 3.1.2 核心方法

```java
public class MachineStatePersistence {

    /**
     * 保存机器状态到 ItemStack
     * @param loc 方块位置
     * @param sfItem Slimefun 物品
     * @return 带状态的 ItemStack，或 null (无状态需保存时)
     */
    public static ItemStack saveState(Location loc, SlimefunItem sfItem);

    /**
     * 从 ItemStack 恢复状态到方块
     * @param item 物品栈
     * @param loc 方块位置
     * @param sfItem Slimefun 物品
     */
    public static void loadState(ItemStack item, Location loc, SlimefunItem sfItem);

    /**
     * 清除 ItemStack 上的状态
     * @param item 物品栈
     * @return 清除状态后的 ItemStack
     */
    public static ItemStack clearState(ItemStack item);

    /**
     * 检查 ItemStack 是否有状态
     * @param item 物品栈
     * @return 是否有状态
     */
    public static boolean hasState(ItemStack item);

    /**
     * 保存所有活跃操作到数据库 (服务器关闭时调用)
     */
    public static void saveAllOperationsToDatabase();

    /**
     * 从数据库恢复操作 (机器加载时调用)
     * @param loc 方块位置
     * @param sfItem Slimefun 物品
     * @param processor 机器处理器
     */
    public static void loadOperationFromDatabase(Location loc, SlimefunItem sfItem,
                                                  MachineProcessor<?> processor);

    /**
     * 清除数据库中的操作数据
     * @param loc 方块位置
     */
    public static void clearSavedOperation(Location loc);

    /**
     * 判断是否应该保存状态
     * @param sfItem Slimefun 物品
     * @param loc 方块位置
     * @return 是否应该保存
     */
    private static boolean shouldSaveState(SlimefunItem sfItem, Location loc);

    /**
     * 生成状态 lore 行
     * @param charge 当前电量
     * @param capacity 最大电量
     * @param op 加工操作 (可为 null)
     * @param sfItem Slimefun 物品
     * @return lore 行列表
     */
    private static List<String> buildStateLore(long charge, long capacity,
                                                CraftingOperation op, SlimefunItem sfItem);

    /**
     * 生成进度条
     * @param current 当前进度
     * @param total 总进度
     * @param length 进度条长度 (格数)
     * @return 进度条字符串
     */
    private static String buildProgressBar(int current, int total, int length);

    /**
     * 序列化 CraftingOperation 为 JSON
     * @param op 加工操作
     * @return JSON 字符串
     */
    private static String serializeOperation(CraftingOperation op);

    /**
     * 从 JSON 反序列化 CraftingOperation
     * @param json JSON 字符串
     * @return CraftingOperation 实例
     */
    private static CraftingOperation deserializeOperation(String json);
}
```

#### 3.1.3 CraftingOperation 序列化格式

```json
{
    "ingredients": [
        {"type": "IRON_INGOT", "amount": 1},
        ...
    ],
    "results": [
        {"type": "IRON_PLATE", "amount": 1},
        ...
    ],
    "totalTicks": 200,
    "currentTicks": 40
}
```

使用 Bukkit 的 `ItemStack.serialize()` 方法序列化物品。

### 3.2 BlockListener 修改

**文件位置**: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BlockListener.java`

#### 3.2.1 破坏流程修改

在 `callBlockHandler()` 方法中，修改掉落物生成逻辑：

```java
// 原代码 (约 L480):
drops.addAll(sfItem.getDrops());

// 修改为:
if (shouldSaveState(sfItem, loc)) {
    ItemStack stateItem = MachineStatePersistence.saveState(loc, sfItem);
    if (stateItem != null) {
        drops.add(stateItem);
    } else {
        drops.addAll(sfItem.getDrops());
    }
} else {
    drops.addAll(sfItem.getDrops());
}
```

同样需要修改 `onBlockBreak()` 中的损坏机器处理逻辑。

#### 3.2.2 放置流程修改

在 `onBlockPlace()` 方法中，创建 blockData 后添加状态恢复：

```java
// 在 createBlock/createUniversalBlock 之后添加:
if (MachineStatePersistence.hasState(item)) {
    MachineStatePersistence.loadState(item, block.getLocation(), sfItem);
}
```

### 3.3 服务器关闭监听器

**文件位置**: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/MachineStateListener.java` (新增)

或在现有监听器中添加：

```java
@EventHandler
public void onServerShutdown(PluginDisableEvent event) {
    if (event.getPlugin() == Slimefun.instance()) {
        MachineStatePersistence.saveAllOperationsToDatabase();
    }
}
```

### 3.4 机器加载时恢复

在机器 ticker 初始化时（`BlockDataController` 或相关位置），检查并恢复保存的操作：

```java
// 在 AContainer 或 AGenerator 的 ticker 初始化处:
MachineStatePersistence.loadOperationFromDatabase(loc, sfItem, getMachineProcessor());
```

### 3.5 机器状态清除器

#### 3.5.1 物品定义

**文件位置**: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/SlimefunItems.java`

```java
public static final SlimefunItemStack MACHINE_STATE_CLEARER = new SlimefunItemStack(
    "MACHINE_STATE_CLEARER",
    Material.SMITHING_TABLE,
    "&6机器状态清除器",
    "",
    "&7手持带有状态的机器物品",
    "&7右键此方块清除状态数据",
    "&7使其恢复为可堆叠状态"
);
```

#### 3.5.2 行为实现

**文件位置**: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/blocks/MachineStateClearer.java` (新增)

```java
public class MachineStateClearer extends SlimefunItem implements BlockUseHandler {

    public MachineStateClearer(ItemGroup itemGroup, SlimefunItemStack item,
                                RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler(onRightClick());
    }

    private BlockUseHandler onRightClick() {
        return e -> {
            e.cancel();  // 取消放置事件

            Player player = e.getPlayer();
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

    @Override
    public BlockUseHandler getItemHandler() {
        return onRightClick();
    }
}
```

#### 3.5.3 注册

**文件位置**: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/setup/SlimefunItemSetup.java`

在基础机器分类末尾注册：

```java
new MachineStateClearer(
    itemGroups.basicMachines,
    SlimefunItems.MACHINE_STATE_CLEARER,
    RecipeType.ENHANCED_CRAFTING_TABLE,
    new ItemStack[]{
        SlimefunItems.PLATE, SlimefunItems.PLATE, SlimefunItems.PLATE,
        SlimefunItems.REDSTONE_ALLOY, SlimefunItems.ELECTRIC_MOTOR, SlimefunItems.REDSTONE_ALLOY,
        SlimefunItems.PLATE, SlimefunItems.PLATE, SlimefunItems.PLATE
    }
).register(plugin);
```

### 3.6 本地化消息

#### 3.6.1 物品名称

硬编码在 `SlimefunItems.java` 中（中文）：
- `&6机器状态清除器`

#### 3.6.2 消息键

添加到 `messages.yml` 文件：

**英文** (`src/main/resources/languages/en/messages.yml`):
```yaml
machines:
  MACHINE_STATE_CLEARER:
    no-item: '&cYou are not holding any item!'
    no-state: '&cThis item has no machine state data!'
    success: '&aMachine state has been cleared! The item can now be stacked.'
```

**简体中文** (`src/main/resources/languages/zh-CN/messages.yml`):
```yaml
machines:
  MACHINE_STATE_CLEARER:
    no-item: '&c你手中没有物品!'
    no-state: '&c该物品没有机器状态数据!'
    success: '&a机器状态已清除! 物品现在可以堆叠了.'
```

---

## 4. Lore 格式规范

### 4.1 用电器 (AContainer)

```
§8⇨ §e⚡ §7100 / 500 J
§8⇨ §e⚙ §7铁锭 → [■■■■■□□□□□] 50%
```

### 4.2 发电机 (AGenerator)

```
§8⇨ §e⚡ §764 / 128 J
§8⇨ §e⚙ §7煤炭 → [■■□□□□□□□□] 20%
```

### 4.3 电容 (Capacitor)

```
§8⇨ §e⚡ §71000 / 5000 J
```

### 4.4 进度条格式

```java
// 10 格进度条
// ■ = 已完成 (绿色 §a)
// □ = 未完成 (灰色 §7)
// 格式: [■■■■■□□□□□] 50%

private static String buildProgressBar(int current, int total, int length) {
    int filled = (int) ((double) current / total * length);
    StringBuilder bar = new StringBuilder("§a");
    for (int i = 0; i < length; i++) {
        if (i == filled) bar.append("§7");
        bar.append(i < filled ? "■" : "□");
    }
    return "[" + bar + "§7] " + (current * 100 / total) + "%";
}
```

---

## 5. 条件判断逻辑

### 5.1 何时写入状态

```java
private static boolean shouldSaveState(SlimefunItem sfItem, Location loc) {
    // 1. 检查是否有电量
    if (sfItem instanceof EnergyNetComponent enc && enc.isChargeable()) {
        long charge = enc.getChargeLong(loc);
        if (charge > 0) return true;
    }

    // 2. 检查是否有工作状态
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

    return false;  // 没电且无工作状态 → 原版掉落
}
```

### 5.2 何时恢复状态

- 放置时总是检查，有数据就恢复
- 数据库加载时总是检查，有数据就恢复
- 恢复后清除数据库条目（防止重复加载）

---

## 6. 文件修改清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `utils/MachineStatePersistence.java` | **新增** | 核心工具类 |
| `items/blocks/MachineStateClearer.java` | **新增** | 机器状态清除器 |
| `listeners/MachineStateListener.java` | **新增** | 服务器关闭监听器 (或添加到现有监听器) |
| `listeners/BlockListener.java` | **修改** | 破坏/放置时调用工具类 |
| `SlimefunItems.java` | **修改** | 添加清除器物品定义 |
| `SlimefunItemSetup.java` | **修改** | 注册清除器物品 |
| `languages/zh-CN/messages.yml` | **修改** | 添加中文消息 |
| `languages/en/messages.yml` | **修改** | 添加英文消息 |
| `messages.yml` | **修改** | 添加默认消息 |

---

## 7. 技术风险与注意事项

### 7.1 CraftingOperation 序列化

- 需要确保 `CraftingOperation` 的所有字段都可以序列化
- `ItemStack.serialize()` 返回 `Map<String, Object>`，需要转换为 JSON 字符串存储
- 反序列化时需要处理版本兼容性问题

### 7.2 性能考虑

- 服务器关闭时遍历所有活跃操作可能有性能影响
- 建议使用异步方式处理（如果 Bukkit 允许在关闭事件中异步）

### 7.3 边界情况

- 损坏的机器目前不掉落物品，需要修改逻辑使其掉落带状态的物品
- 爆炸破坏的机器也需要处理
- 自动放置器 (BlockPlacer) 放置的机器是否需要恢复状态？

### 7.4 数据兼容性

- 新版本保存的状态数据在旧版本加载时需要兼容处理
- 建议在 PDC 中添加版本标记

---

## 8. 测试计划

### 8.1 单元测试

- `MachineStatePersistence` 的序列化/反序列化方法
- 进度条生成方法
- 条件判断方法

### 8.2 集成测试

- 用电器破坏/放置测试
- 发电机破坏/放置测试
- 电容破坏/放置测试
- 服务器关闭/启动测试
- 机器状态清除器测试
- 边界情况测试（无状态、满电量、满进度等）

---

## 9. 待确认事项

- [x] 机器状态清除器的合成配方 → 无需考虑，后期有合成表大调
- [x] 损坏机器 → 由损坏工具类接管（不掉落），保持现有逻辑
- [x] BlockPlacer 放置的机器 → 需要恢复状态
- [x] 进度条长度 → 10格合适
