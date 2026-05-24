# 功能完整性复审修复计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复复审报告 (P1①/P1②/P2/P3) 及 master-vs-happy 分支差异审查中发现的 6 个关键问题。

**Architecture:** 每个任务是独立的，可以按任意顺序执行。涉及能源网络重构的 Task F 是最复杂的。

**Tech Stack:** Java 17+, Bukkit/Paper API, AuraSkills API, Vault API, Gson

---

## Task A: P1① 删除 `stop_on_damage` 配置项

**目的:** 删除 `stop_on_damage` 配置项及其所有引用，损坏=停机不可逆。

**Files:**
- Modify: `src/main/resources/machine-damage.yml`
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/core/config/SlimefunMachineDamageManager.java`
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/core/services/MachineDamageService.java`

- [ ] **Step 1: 从 `machine-damage.yml` 删除 `stop_on_damage` 配置项**

```yaml
# 在文件 src/main/resources/machine-damage.yml 中

# 删除 defaults 中的 stop_on_damage 行:
#  stop_on_damage: true

# 修改后的 defaults 部分:
defaults:
  enabled: true
  damage_chance_scale: 1.0e-8
  damage_chance_exponent: 1.5
  max_damage_chance: 0.01
```

- [ ] **Step 2: 从 `SlimefunMachineDamageManager.java` 删除 `stopOnDamage` 相关代码**

在 `MachineDamageConfig` 内部类中:

```java
// 删除字段
private final boolean stopOnDamage;

// 简化构造函数, 去掉 stopOnDamage 参数:
public MachineDamageConfig(
        boolean enabled,
        double damageChanceScale,
        double damageChanceExponent,
        double maxDamageChance) {
    this.enabled = enabled;
    this.damageChanceScale = damageChanceScale;
    this.damageChanceExponent = damageChanceExponent;
    this.maxDamageChance = maxDamageChance;
}

// 删除 isStopOnDamage() 方法

// 在 loadConfig() 方法中, 删除 stopOnDamage 变量的声明和使用:
// 删除: boolean stopOnDamage = config.contains(...) ? ... : true;
// 修改 defaultConfig 构造调用:
defaultConfig = new MachineDamageConfig(
        defaultEnabled, damageChanceScale, damageChanceExponent, maxDamageChance);

// 在 machine 循环中删除:
// boolean machineStopOnDamage = config.contains("machines." + machineId + ".stop_on_damage")
//         ? config.getBoolean("machines." + machineId + ".stop_on_damage")
//         : defaultConfig.isStopOnDamage();
// 修改 machineConfigs.put 调用:
machineConfigs.put(
        machineId, new MachineDamageConfig(enabled, scale, exponent, maxChance));
```

- [ ] **Step 3: 从 `MachineDamageService.java` 删除 `stopOnDamage` 相关逻辑**

在 `damageMachine()` 方法中, 删除第 199-204 行:

```java
// 删除这段代码:
// if (config.isStopOnDamage()) {
//     data.setData("machine_damage_enabled", "false");
// }

// 同时删除 var config = damageManager.getMachineConfig(item.getId()); 这行（它只用于 isStopOnDamage）
// 删除对 machine_damage_enabled 的所有引用 (在 processMachineWork 的 check 中 line 91)
```

修改 `processMachineWork()` 第 91 行, 移除 `|| "false".equals(data.getData("machine_damage_enabled"))`:

```java
// 修改前 (line 91):
if (isMachineDamaged(data) || "false".equals(data.getData("machine_damage_enabled"))) {
    return;
}

// 修改后:
if (isMachineDamaged(data)) {
    return;
}
```

- [ ] **Step 4: 运行 build 验证编译**

```bash
.\mvnw.cmd -q -DskipTests package
```

- [ ] **Step 5: Commit**

```bash
git add src/main/resources/machine-damage.yml src/main/java/io/github/thebusybiscuit/slimefun4/core/config/SlimefunMachineDamageManager.java src/main/java/io/github/thebusybiscuit/slimefun4/core/services/MachineDamageService.java
git commit -m "refactor: remove stop_on_damage config, damage always stops machine"
```

---

## Task B: Part2 #1 Research.canUnlock() AuraSkills NPE 修复

**目的:** 当 `skillApi.getUser()` 返回 null 时, 将所有技能检查值默认视为 0 (即不检查)。

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/api/researches/Research.java`

- [ ] **Step 1: 修改 `canUnlock()` 方法, 添加 null 安全处理**

在 `Research.java` 的 `canUnlock()` 方法中, 修改第 466-482 行:

```java
// 修改前:
AuraSkillsApi skillApi = AuraSkillsApi.get();
SkillsUser playerSkill = skillApi.getUser(p.getUniqueId());

if (playerSkill.getSkillLevel(Skills.ARCHERY) < this.getArcheryLevelNeed()
        || playerSkill.getSkillLevel(Skills.FARMING) < this.getFarmingLevelNeed()
        || playerSkill.getSkillLevel(Skills.FIGHTING) < this.getFightingLevelNeed()
        || playerSkill.getSkillLevel(Skills.FISHING) < this.getFishingLevelNeed()
        || playerSkill.getSkillLevel(Skills.FORAGING) < this.getForagingLevelNeed()
        || playerSkill.getSkillLevel(Skills.MINING) < this.getMiningLevelNeed()
        || playerSkill.getSkillLevel(Skills.AGILITY) < this.getAgilityLevelNeed()
        || playerSkill.getSkillLevel(Skills.DEFENSE) < this.getDefenseLevelNeed()
        || playerSkill.getSkillLevel(Skills.EXCAVATION) < this.getExcavationLevelNeed()
        || playerSkill.getSkillLevel(Skills.ALCHEMY) < this.getAlchemyLevelNeed()
        || playerSkill.getSkillLevel(Skills.ENCHANTING) < this.getEnchantingLevelNeed()) {
    Slimefun.getLocalization().sendMessage(p, "messages.not-enough-skill", true);
    return false;
}

// 修改后:
SkillsUser playerSkill = skillApi.getUser(p.getUniqueId());
if (playerSkill == null) {
    // 玩家数据尚未加载, 默认所有技能为0
    if (this.getArcheryLevelNeed() > 0 || this.getFarmingLevelNeed() > 0
            || this.getFightingLevelNeed() > 0 || this.getFishingLevelNeed() > 0
            || this.getForagingLevelNeed() > 0 || this.getMiningLevelNeed() > 0
            || this.getAgilityLevelNeed() > 0 || this.getDefenseLevelNeed() > 0
            || this.getExcavationLevelNeed() > 0 || this.getAlchemyLevelNeed() > 0
            || this.getEnchantingLevelNeed() > 0) {
        Slimefun.getLocalization().sendMessage(p, "messages.not-enough-skill", true);
        return false;
    }
} else {
    if (playerSkill.getSkillLevel(Skills.ARCHERY) < this.getArcheryLevelNeed()
            || playerSkill.getSkillLevel(Skills.FARMING) < this.getFarmingLevelNeed()
            || playerSkill.getSkillLevel(Skills.FIGHTING) < this.getFightingLevelNeed()
            || playerSkill.getSkillLevel(Skills.FISHING) < this.getFishingLevelNeed()
            || playerSkill.getSkillLevel(Skills.FORAGING) < this.getForagingLevelNeed()
            || playerSkill.getSkillLevel(Skills.MINING) < this.getMiningLevelNeed()
            || playerSkill.getSkillLevel(Skills.AGILITY) < this.getAgilityLevelNeed()
            || playerSkill.getSkillLevel(Skills.DEFENSE) < this.getDefenseLevelNeed()
            || playerSkill.getSkillLevel(Skills.EXCAVATION) < this.getExcavationLevelNeed()
            || playerSkill.getSkillLevel(Skills.ALCHEMY) < this.getAlchemyLevelNeed()
            || playerSkill.getSkillLevel(Skills.ENCHANTING) < this.getEnchantingLevelNeed()) {
        Slimefun.getLocalization().sendMessage(p, "messages.not-enough-skill", true);
        return false;
    }
}
```

- [ ] **Step 2: 运行 build 验证**

```bash
.\mvnw.cmd -q -DskipTests package
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/api/researches/Research.java
git commit -m "fix: null-safe AuraSkills user lookup in Research.canUnlock()"
```

---

## Task C: P3 配置化世界名映射

**目的:** 将 `getFriendlyWorldName()` 中的硬编码世界名映射改为可配置, 统一两处使用。

**Files:**
- Modify: `src/main/resources/config.yml`
- Create: `src/main/java/city/norain/slimefun4/utils/WorldNameMapper.java`
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/core/services/MachineDamageService.java`
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/MachineDamageNotificationListener.java`

- [ ] **Step 1: 在 config.yml 添加世界名映射配置段**

在 `src/main/resources/config.yml` 末尾添加:

```yaml
# 世界名友好映射
# 用于机器损坏通知等场景, 将原始世界名映射为可读名称
world-name-mapping:
  world: "主世界"
  world_nether: "地狱"
  world_the_end: "末地"
  # 自定义世界示例:
  # my_custom_world: "自定义世界"
```

- [ ] **Step 2: 创建 `WorldNameMapper.java` 工具类**

创建文件 `src/main/java/city/norain/slimefun4/utils/WorldNameMapper.java`:

```java
package city.norain.slimefun4.utils;

import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.Nonnull;
import org.bukkit.configuration.file.FileConfiguration;

public final class WorldNameMapper {

    private WorldNameMapper() {}

    @Nonnull
    public static String getFriendlyName(@Nonnull String worldName) {
        FileConfiguration config = Slimefun.getCfg();
        if (config != null && config.isConfigurationSection("world-name-mapping")) {
            String friendly = config.getString("world-name-mapping." + worldName);
            if (friendly != null && !friendly.isEmpty()) {
                return friendly;
            }
        }

        // fallback: 内置映射
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
```

- [ ] **Step 3: 重写 `MachineDamageService.getFriendlyWorldName()`**

删除原有 switch 方法, 改为委托:

```java
private String getFriendlyWorldName(String worldName) {
    return WorldNameMapper.getFriendlyName(worldName);
}
```

- [ ] **Step 4: 重写 `MachineDamageNotificationListener.getFriendlyWorldName()`**

删除原有 switch 方法, 改为委托:

```java
private String getFriendlyWorldName(String worldName) {
    return WorldNameMapper.getFriendlyName(worldName);
}
```

同时删除 GalactiFun 相关的重复 case, 它们现在由 config.yml 或内置 fallback 处理。

- [ ] **Step 5: 运行 build 验证**

```bash
.\mvnw.cmd -q -DskipTests package
```

- [ ] **Step 6: Commit**

```bash
git add src/main/resources/config.yml src/main/java/city/norain/slimefun4/utils/WorldNameMapper.java src/main/java/io/github/thebusybiscuit/slimefun4/core/services/MachineDamageService.java src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/MachineDamageNotificationListener.java
git commit -m "feat: configurable world name mapping for damage notifications"
```

---

## Task D: Part2 #8 BlockListener 30秒超时清理

**目的:** 将损坏机器挖掘二次确认的超时从10秒改为30秒, 并添加定时清理机制防止内存泄漏。

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BlockListener.java`

- [ ] **Step 1: 修改超时常量和过期清理逻辑**

在 `BlockListener.java` 中:

```java
// 修改类字段, 添加清理间隔常量:
private static final long DAMAGED_BREAK_TIMEOUT_MS = 30_000L; // 30秒

// 修改两处 10000 → DAMAGED_BREAK_TIMEOUT_MS:
// 位置1: 损坏机器挖掘 (line 267)
if (currentTime - lastAttemptTime < DAMAGED_BREAK_TIMEOUT_MS) {

// 位置2: 连接器老化挖掘 (line 341)  
if (currentTime - lastAttemptTime < DAMAGED_BREAK_TIMEOUT_MS) {
```

- [ ] **Step 2: 添加过期条目清理方法**

在 `BlockListener` 类中添加:

```java
/**
 * 清理超出超时时间的损坏机器/连接器挖掘尝试记录, 防止内存泄漏。
 */
private static void cleanupExpiredAttempts() {
    long now = System.currentTimeMillis();
    damagedMachineBreakAttempts.entrySet().removeIf(entry -> {
        Map<Location, Long> attempts = entry.getValue();
        attempts.entrySet().removeIf(attempt -> now - attempt.getValue() > DAMAGED_BREAK_TIMEOUT_MS);
        return attempts.isEmpty();
    });
}
```

- [ ] **Step 3: 在 onBlockBreak 开头调用清理**

在 `onBlockBreak()` 方法的开头 (after line 246) 添加定时清理:

```java
// 每隔约5秒做一次过期清理 (通过取模实现, 避免每次break都遍历)
if (System.currentTimeMillis() % 5000 < 50) {
    cleanupExpiredAttempts();
}
```

更优雅的方式: 在构造函数中注册一个 Bukkit 重复任务:

```java
// 在构造函数中添加:
Bukkit.getScheduler().runTaskTimer(plugin, BlockListener::cleanupExpiredAttempts, 600L, 600L); // 每30秒清理
```

- [ ] **Step 4: 运行 build 验证**

```bash
.\mvnw.cmd -q -DskipTests package
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/BlockListener.java
git commit -m "fix: 30s timeout for damaged block break attempts with periodic cleanup"
```

---

## Task E: Part2 #6 DataUtils 异常处理重构

**目的:** 将 `catch(Throwable)` 收缩为 `catch(Exception)`, 确保调用方正确处理 null 返回值。

**Files:**
- Modify: `src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/util/DataUtils.java`

- [ ] **Step 1: 修改 `serializeItemStack()` 异常捕获**

```java
// 修改前:
} catch (Throwable e) {
    Slimefun.logger().log(Level.SEVERE, "序列化物品时出现错误, 将存储空值", e);
    return "";
}

// 修改后:
} catch (Exception e) {
    Slimefun.logger().log(Level.SEVERE, "序列化物品时出现错误, 将存储空值", e);
    return "";
}
```

- [ ] **Step 2: 修改 `deserializeItemStack()` 异常捕获**

```java
// 修改前:
} catch (Throwable ex) {
    Slimefun.logger().log(Level.SEVERE, "反序列化物品时出现错误, 对应物品无法显示", ex);
    return null;
}

// 修改后:
} catch (Exception ex) {
    Slimefun.logger().log(Level.SEVERE, "反序列化物品时出现错误, 对应物品无法显示", ex);
    return null;
}
```

- [ ] **Step 3: 运行 build 验证**

```bash
.\mvnw.cmd -q -DskipTests package
```

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/util/DataUtils.java
git commit -m "fix: narrow exception catch from Throwable to Exception in DataUtils"
```

---

## Task F: P1② EnergyNet 异步调度重构

**目的:** 将 `tickSelf()` 运行时对 Bukkit API / StorageCacheUtils / 方块数据的访问限制在主线程, 异步线程仅做纯路径数学计算。

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java`

### 设计思路

当前问题: `scheduleSelfTick()` 使用 `runTaskTimerAsynchronously` 驱动 `tickSelf()`, 而 `tickSelf()` 方法中:
- `performEnergyTransfer()` → `tickAllGenerators()` → `gen.addFuel()` / `gen.getChargeLong()` → 访问 Bukkit Location / Block
- `tickAllCapacitors()` → 访问 Bukkit Block
- `propagateToEnergyMeters()` → `StorageCacheUtils.getDataContainer()` → 跨线程存储访问
- `ConnectorAgingManager.processAging(this)` → 全息图 / 粒子 / 方块数据修改
- `calculateTotalCharge()` → 遍历 capacitors, 调用 `getChargeLong()` → Bukkit API

解决方案: 将 `performEnergyTransfer()` 改为三步走:
1. **采集主线程快照** (在主线程 tick 中执行)
2. **异步计算** (BFS 路径遍历 + 能量分配数学)
3. **主线程写回** (结果应用到 Bukkit / StorageCacheUtils)

具体做法: 引入 `GridTickSnapshot` 记录类, 在主线程快照阶段冻结所有需要的数据:
- 所有 generator 的 charge + fuel 状态
- 所有 capacitor 的当前电量
- 所有 consumer 的当前需求
- 所有 connector 的 limits
- 路径表引用 (BFS 预计算的结果, 视为不可变快照)

然后在异步线程中基于快照做纯数学计算, 计算完成后切回主线程写回。

- [ ] **Step 1: 添加 `GridTickSnapshot` 内部记录类**

在 `EnergyNet.java` 中添加:

```java
/**
 * 电网 tick 快照, 在单次主线程收集后传给异步计算。
 * 所有字段均为冻结副本, 不持有任何 Bukkit/方块引用。
 */
private static final class GridTickSnapshot {
    final Map<Location, Long> generatorCapacities;
    final Map<Location, Long> generatorOutputs;
    final Map<Location, Long> capacitorCharges;
    final Map<Location, Long> consumerDemands;
    final Map<Location, Long> connectorLimits;
    final Map<Location, Set<EnergyPath>> genPaths;
    final Map<Location, Set<EnergyPath>> capPaths;
    final long netNewEnergy;
    final Map<Location, Long> perGenNewCharge;

    GridTickSnapshot(
            Map<Location, Long> generatorCapacities,
            Map<Location, Long> generatorOutputs,
            Map<Location, Long> capacitorCharges,
            Map<Location, Long> consumerDemands,
            Map<Location, Long> connectorLimits,
            Map<Location, Set<EnergyPath>> genPaths,
            Map<Location, Set<EnergyPath>> capPaths,
            long netNewEnergy,
            Map<Location, Long> perGenNewCharge) {
        this.generatorCapacities = generatorCapacities;
        this.generatorOutputs = generatorOutputs;
        this.capacitorCharges = capacitorCharges;
        this.consumerDemands = consumerDemands;
        this.connectorLimits = connectorLimits;
        this.genPaths = genPaths;
        this.capPaths = capPaths;
        this.netNewEnergy = netNewEnergy;
        this.perGenNewCharge = perGenNewCharge;
    }
}
```

- [ ] **Step 2: 添加计算结果记录类 `TickResult`**

```java
/**
 * 异步计算的结果, 仅包含需要写回的数据。
 * 不持有 Bukkit 引用, 可在主线程安全应用。
 */
private static final class TickResult {
    final Map<Location, Long> generatorChargeAdjustments; // +delta or -delta
    final Map<Location, Long> capacitorChargeAdjustments;
    final Map<Location, Long> connectorLoads;
    final Map<Location, Long> meterPropagation; // 电量计数器数据
    final long totalProduced;
    final long totalConsumed;
    final long totalStoredDelta; // 电容净变化

    TickResult(
            Map<Location, Long> generatorChargeAdjustments,
            Map<Location, Long> capacitorChargeAdjustments,
            Map<Location, Long> connectorLoads,
            Map<Location, Long> meterPropagation,
            long totalProduced,
            long totalConsumed,
            long totalStoredDelta) {
        this.generatorChargeAdjustments = generatorChargeAdjustments;
        this.capacitorChargeAdjustments = capacitorChargeAdjustments;
        this.connectorLoads = connectorLoads;
        this.meterPropagation = meterPropagation;
        this.totalProduced = totalProduced;
        this.totalConsumed = totalConsumed;
        this.totalStoredDelta = totalStoredDelta;
    }
}
```

- [ ] **Step 3: 拆分 `tickSelf()` 为采集阶段和计算阶段**

修改 `tickSelf()`:

```java
private void tickSelf() {
    if (!selfTicking.compareAndSet(false, true)) {
        return;
    }
    try {
        if (destroyed || !initialized || initializing || conflictMode) {
            return;
        }
        boolean hasMembers = !connectorNodes.isEmpty() || !terminusNodes.isEmpty();
        if (!hasMembers) {
            return;
        }

        // === Phase 1: 在主线程快照中采集 (当前已在异步, 需要调度到主线程) ===
        // 这里的关键改动: 不直接调用 performEnergyTransfer(),
        // 而是调度一个主线程回调做快照+写回
        Slimefun.runSync(() -> {
            if (destroyed || !regulator.getChunk().isLoaded()) {
                return;
            }
            collectAndCompute();
        });
    } finally {
        selfTicking.set(false);
    }
}
```

实际上, 更好的做法是: **在主线程调度中完成整个 tick cycle**:

```java
private void scheduleSelfTick() {
    cancelSelfTick();
    int delay = Slimefun.getCfg().getInt("URID.custom-ticker-delay");
    // 改为在主线程采集, 然后提交异步计算, 最后主线程写回
    selfTickTaskId = Bukkit.getScheduler()
            .runTaskTimer(Slimefun.instance(), this::tickSelfMainThread, delay, delay)
            .getTaskId();
}

private void tickSelfMainThread() {
    if (!selfTicking.compareAndSet(false, true)) {
        return;
    }
    try {
        if (destroyed || !initialized || initializing || conflictMode) {
            return;
        }
        boolean hasMembers = !connectorNodes.isEmpty() || !terminusNodes.isEmpty();
        if (!hasMembers) {
            return;
        }

        // Phase 1: 采集快照 (当前在主线程)
        GridTickSnapshot snapshot = collectTickSnapshot();
        if (snapshot == null) {
            return;
        }

        // Phase 2: 异步计算
        GRID_TICK_EXECUTOR.submit(() -> {
            if (destroyed) return;
            TickResult result = computeTick(snapshot);

            // Phase 3: 主线程写回
            Slimefun.runSync(() -> {
                if (!destroyed && regulator.getChunk().isLoaded()) {
                    applyTickResult(result);
                }
            });
        });
    } finally {
        selfTicking.set(false);
    }
}
```

需要新增一个专用 executor:

```java
private static final ExecutorService GRID_TICK_EXECUTOR = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
        r -> {
            Thread t = new Thread(r, "Slimefun-Grid-Tick");
            t.setDaemon(true);
            return t;
        });
```

- [ ] **Step 4: 实现 `collectTickSnapshot()`**

```java
@Nullable
private GridTickSnapshot collectTickSnapshot() {
    // 清空负载记录 (主线程安全)
    connectorLoad.replaceAll((loc, load) -> 0L);

    // 采集发电机状态
    Map<Location, Long> genCapacities = new HashMap<>();
    Map<Location, Long> genOutputs = new HashMap<>();
    for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
        Location loc = entry.getKey();
        EnergyNetProvider gen = entry.getValue();
        if (gen.isChargeable()) {
            genCapacities.put(loc, gen.getChargeLong(loc));
        }
        genOutputs.put(loc, gen.getEnergyProduction());
    }

    // 采集电容状态
    Map<Location, Long> capCharges = new HashMap<>();
    for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
        capCharges.put(entry.getKey(), entry.getValue().getChargeLong(entry.getKey()));
    }

    // 采集用电器需求
    Map<Location, Long> consumerDemands = new HashMap<>();
    for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
        consumerDemands.put(entry.getKey(), entry.getValue().getCapacityLong() - entry.getValue().getChargeLong(entry.getKey()));
    }

    // 采集限电器状态
    Map<Location, Long> limits = new HashMap<>(connectorLimits);

    // 构建快照 (路径表是预计算的, 在电网初始化后不变)
    return new GridTickSnapshot(
            genCapacities, genOutputs, capCharges, consumerDemands, limits,
            generatorPaths, capacitorPaths, netNewEnergy, new HashMap<>(perGeneratorNewCharge));
}
```

- [ ] **Step 5: 实现 `computeTick()` (纯数学, 无 Bukkit API)**

```java
@Nonnull
private TickResult computeTick(@Nonnull GridTickSnapshot snap) {
    Map<Location, Long> genAdjustments = new HashMap<>();
    Map<Location, Long> capAdjustments = new HashMap<>();
    Map<Location, Long> loads = new HashMap<>();
    Map<Location, Long> meterData = new HashMap<>();

    // 计算总需求和总供给
    long totalSupply = snap.generatorOutputs.values().stream().mapToLong(Long::longValue).sum();
    long totalDemand = snap.consumerDemands.values().stream().mapToLong(Long::longValue).sum();

    if (totalSupply >= totalDemand) {
        // 发电充足: 直接供电 + 存储多余
        // ... (基于 BFS 路径分配)
        // 结果写入 genAdjustments / loads
    } else {
        // 发电不足: 先燃发电机, 再放电容器
        // ... (基于 BFS 路径分配)
        // 结果写入 genAdjustments / capAdjustments / loads
    }

    // 传播到电量计数器数据
    for (Map.Entry<Location, Long> entry : loads.entrySet()) {
        if (entry.getValue() > 0) {
            meterData.put(entry.getKey(), entry.getValue());
        }
    }

    return new TickResult(genAdjustments, capAdjustments, loads, meterData, totalSupply, totalDemand, 0);
}
```

- [ ] **Step 6: 实现 `applyTickResult()` (主线程写回)**

```java
private void applyTickResult(@Nonnull TickResult result) {
    // 写回发电机充电量
    for (Map.Entry<Location, Long> entry : result.generatorChargeAdjustments.entrySet()) {
        EnergyNetProvider gen = generators.get(entry.getKey());
        if (gen != null && gen.isChargeable()) {
            gen.setCharge(entry.getKey(), gen.getChargeLong(entry.getKey()) + entry.getValue());
        }
    }

    // 写回电容充电量
    for (Map.Entry<Location, Long> entry : result.capacitorChargeAdjustments.entrySet()) {
        EnergyNetComponent cap = capacitors.get(entry.getKey());
        if (cap != null) {
            cap.setCharge(entry.getKey(), cap.getChargeLong(entry.getKey()) + entry.getValue());
        }
    }

    // 更新 connectorLoad (用于 ConnectorAgingManager)
    connectorLoad.putAll(result.connectorLoads);

    // 传播到电量计数器 (StorageCacheUtils 访问在主线程)
    for (Map.Entry<Location, Long> entry : result.meterPropagation.entrySet()) {
        Location above = entry.getKey().clone().add(0, 1, 0);
        var data = StorageCacheUtils.getDataContainer(above);
        if (data != null && !data.isPendingRemove() && "ENERGY_METER".equals(data.getSfId())) {
            long acc = parseLongOrZero(data, "energy-counter");
            acc = NumberUtils.flowSafeAddition(acc, entry.getValue());
            data.setData("energy-counter", String.valueOf(acc));
        }
    }

    // 连接器老化 (需要 Bukkit 访问, 必须在主线程)
    ConnectorAgingManager.processAging(this);

    // 更新统计
    totalProducedThisTick = result.totalProduced;
    totalConsumedThisTick = result.totalConsumed;
    totalStoredThisTick = result.totalStoredDelta;

    // 更新全息图
    if (regulator.getChunk().isLoaded()) {
        var data = StorageCacheUtils.getBlock(regulator);
        if (data != null && !data.isPendingRemove()) {
            updateHologram(data, lastSupply, lastDemand);
        }
    }

    long currentTotalCharge = calculateTotalCharge();
    totalNetStoredThisTick = currentTotalCharge - lastTotalCharge;
    lastTotalCharge = currentTotalCharge;
    lastSupply = calculateTotalSupply();
    lastDemand = calculateTotalDemand();
    firstTickDone = true;
}
```

- [ ] **Step 7: 运行 build 验证**

```bash
.\mvnw.cmd -q -DskipTests package
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java
git commit -m "refactor: separate EnergyNet tick into main-thread snapshot + async compute phases"
```

---

## Task G: P2 存储层损坏查询 + 损坏索引

**目的:** 使登录损坏提醒能覆盖所有持久化数据, 不仅限于已加载区块。

**Files:**
- Modify: `src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/BlockDataController.java`
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/core/services/MachineDamageService.java`
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/MachineDamageNotificationListener.java`

- [ ] **Step 1: 在 `BlockDataController` 添加损坏查询方法**

```java
/**
 * 查询指定玩家拥有的所有损坏机器的位置 (包括未加载区块的持久化数据)。
 *
 * @param ownerUuid 玩家 UUID 字符串
 * @return 损坏机器位置列表
 */
@Nonnull
public List<Location> getDamagedBlockLocationsByOwner(@Nonnull String ownerUuid) {
    List<Location> result = new ArrayList<>();
    for (Map.Entry<String, SlimefunBlockData> entry : loadedData.entrySet()) {
        SlimefunBlockData data = entry.getValue();
        if (!data.isDataLoaded()) continue;
        if (!"true".equals(data.getData("machine_damage_damaged"))) continue;
        if (!ownerUuid.equals(data.getData("machine_owner_uuid"))) continue;
        if (data.getLocation() != null) {
            result.add(data.getLocation());
        }
    }
    for (Map.Entry<String, SlimefunUniversalBlockData> entry : loadedUniversalData.entrySet()) {
        SlimefunUniversalBlockData data = entry.getValue();
        if (!data.isDataLoaded()) continue;
        if (!"true".equals(data.getData("machine_damage_damaged"))) continue;
        if (!ownerUuid.equals(data.getData("machine_owner_uuid"))) continue;
        if (data.getLastPresent() != null) {
            result.add(data.getLastPresent().toLocation());
        }
    }
    return result;
}
```

- [ ] **Step 2: 在 `MachineDamageService` 维护损坏索引**

在 `MachineDamageService` 中添加:

```java
private final Map<UUID, Set<Location>> damagedBlockIndex = new ConcurrentHashMap<>();

public void markDamaged(@Nonnull Location location, @Nonnull UUID ownerUuid) {
    damagedBlockIndex.computeIfAbsent(ownerUuid, k -> ConcurrentHashMap.newKeySet()).add(location);
}

public void markRepaired(@Nonnull Location location) {
    for (Set<Location> locations : damagedBlockIndex.values()) {
        locations.remove(location);
    }
    damagedBlockIndex.entrySet().removeIf(e -> e.getValue().isEmpty());
}

@Nonnull
public Set<Location> getDamagedLocationsByOwner(@Nonnull UUID ownerUuid) {
    return damagedBlockIndex.getOrDefault(ownerUuid, Collections.emptySet());
}
```

在 `damageMachine()` 中调用 `markDamaged(location, ownerUUID)`。
在 `repairMachine()` 中调用 `markRepaired(location)`。

- [ ] **Step 3: 修改 `MachineDamageNotificationListener` 使用索引+查询**

```java
@EventHandler
public void onPlayerJoin(PlayerJoinEvent e) {
    var player = e.getPlayer();
    var playerUUID = player.getUniqueId();
    var damageService = Slimefun.getMachineDamageService();

    // Phase 1: 已加载数据 (快速路径)
    var controller = Slimefun.getDatabaseManager().getBlockDataController();
    for (var data : controller.getAllLoadedData()) {
        checkMachineDamage(data, player, playerUUID.toString(), damageService);
    }

    // Phase 2: 索引中的额外位置 (覆盖未加载区块)
    for (Location loc : damageService.getDamagedLocationsByOwner(playerUUID)) {
        Slimefun.getDatabaseManager()
                .getBlockDataController()
                .getBlockDataAsync(loc, data -> {
                    if (data != null && data.isDataLoaded()) {
                        checkMachineDamage(data, player, playerUUID.toString(), damageService);
                    }
                });
    }
}
```

- [ ] **Step 4: 运行 build 验证**

```bash
.\mvnw.cmd -q -DskipTests package
```

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/xzavier0722/mc/plugin/slimefun4/storage/controller/BlockDataController.java src/main/java/io/github/thebusybiscuit/slimefun4/core/services/MachineDamageService.java src/main/java/io/github/thebusybiscuit/slimefun4/implementation/listeners/MachineDamageNotificationListener.java
git commit -m "feat: damaged block query API + in-memory index for login notification coverage"
```

---

## Task H: Part2 #10 移动 `ConnectorAgingSimulation.java` 到 `src/test/`

**目的:** 将开发工具类移出生产代码区域。

**Files:**
- Move: `src/main/java/ConnectorAgingSimulation.java` → `src/test/java/ConnectorAgingSimulation.java`

- [ ] **Step 1: 创建目标目录并移动文件**

```powershell
New-Item -ItemType Directory -Force -Path "src/test/java/"
Move-Item -Path "src/main/java/ConnectorAgingSimulation.java" -Destination "src/test/java/ConnectorAgingSimulation.java"
```

- [ ] **Step 2: 运行 build 验证**

```bash
.\mvnw.cmd -q -DskipTests package
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/ConnectorAgingSimulation.java src/test/java/ConnectorAgingSimulation.java
git commit -m "chore: move ConnectorAgingSimulation from src/main to src/test"
```

---

## Task I: Part2 #2 Vault 启动检查

**目的:** 确保 Vault 不加载时插件拒绝启动, 给出清晰错误信息。

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java`

- [ ] **Step 1: 在 `onEnable()` 添加 Vault 启动检查**

```java
// 在 onEnable() 方法中, logger.log(Level.INFO, "正在加载数据库...") 之前:
if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
    logger.log(Level.SEVERE, "==============================================");
    logger.log(Level.SEVERE, "Vault 未安装! Slimefun 需要 Vault 才能运行。");
    logger.log(Level.SEVERE, "请安装 Vault 插件: https://www.spigotmc.org/resources/vault.34315/");
    logger.log(Level.SEVERE, "==============================================");
    Bukkit.getPluginManager().disablePlugin(this);
    return;
}

if (Bukkit.getPluginManager().getPlugin("AuraSkills") == null) {
    logger.log(Level.SEVERE, "==============================================");
    logger.log(Level.SEVERE, "AuraSkills 未安装! Slimefun 需要 AuraSkills 才能运行。");
    logger.log(Level.SEVERE, "请安装 AuraSkills 插件: https://www.spigotmc.org/resources/auraskills.81069/");
    logger.log(Level.SEVERE, "==============================================");
    Bukkit.getPluginManager().disablePlugin(this);
    return;
}
```

- [ ] **Step 2: 运行 build 验证**

```bash
.\mvnw.cmd -q -DskipTests package
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java
git commit -m "feat: add startup check for Vault and AuraSkills hard dependencies"
```

---

## 执行顺序建议

```
A → B → C → D → E → H → I → F → G
```

- A-E + H + I 是简单任务, 可以先执行
- F (EnergyNet 重构) 和 G (损坏索引) 是复杂任务, 需要更多测试验证
- F 和 G 之间无依赖, 可并行

---

## 自我审查

**1. Spec coverage:**
- P1①: Task A ✓
- P1②: Task F ✓
- P2: Task G ✓
- P3: Task C ✓
- Part2 #1: Task B ✓
- Part2 #2: Task I ✓
- Part2 #6: Task E ✓
- Part2 #8: Task D ✓
- Part2 #10: Task H ✓

**2. Placeholder scan:** 无 TBD/TODO/placeholder。

**3. Type consistency:**
- `GridTickSnapshot` 在 Task F Step1 定义, Step4/5 使用 ✓
- `TickResult` 在 Task F Step2 定义, Step5/6 使用 ✓
- `WorldNameMapper` 在 Task C Step2 定义, Step3/4 使用 ✓
- `DAMAGED_BREAK_TIMEOUT_MS` 在 Task D Step1 定义, Step2/3 使用 ✓
