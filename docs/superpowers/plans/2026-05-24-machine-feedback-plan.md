# 机器工作反馈系统 — 实施计划 (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Slimefun4 所有 AContainer 用电器和主动型 AGenerator 发电机添加统一的工作反馈（BlockState 变化 + 粒子效果 + 音效里程碑）

**Architecture:** 新建 `MachineFeedbackType` 枚举 + `ParticleOffset` 枚举定义预设反馈，新建 `MachineFeedbackService` 服务类统一调度。在 `AContainer.tick()` / `AGenerator.getGeneratedOutput()` 中插入 3 处钩子。各机器子类构造函数声明 `feedbackType`。所有 Bukkit API 调用包裹在 `Bukkit.runTask()` 中以确保异步线程安全。

**Tech Stack:** Java 17+, Bukkit/Spigot API, Slimefun4 项目结构, Maven

**Spec:** [2026-05-24-machine-feedback-design.md](../specs/2026-05-24-machine-feedback-design.md)

---

## 文件结构

| 操作 | 文件路径 |
|------|----------|
| **Create** | `src/main/java/io/github/thebusybiscuit/slimefun4/core/machines/MachineFeedbackType.java` |
| **Modify** | `src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java` |
| **Modify** | `src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AGenerator.java` |
| **Modify** | `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java` |
| **Modify** | 26 个机器子类（24 AContainer + 5 AGenerator，Solar 除外） |

**MachineFeedbackService** 内嵌为静态内部类放在 `Slimefun.java` 的 services 子包中，但因为该服务是全新的不需要与 Registry 交互，我们采取和 `MachineDamageService` 相同的模式：作为独立 service 类放在 `core/services/` 下，实例字段和静态 getter 放在 `Slimefun.java` 中。

**修正：** 为保持代码简洁且遵循现有模式，`MachineFeedbackService` 放在 `core/services/` 下，由 `Slimefun` 直接持有实例。

---

## Task 1: 创建 `MachineFeedbackType` 枚举和 `ParticleOffset` 内部枚举

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/slimefun4/core/machines/MachineFeedbackType.java`

- [ ] **Step 1: 写入完整枚举文件**

```java
package io.github.thebusybiscuit.slimefun4.core.machines;

import javax.annotation.Nonnull;
import org.bukkit.Particle;
import org.bukkit.Sound;

public enum MachineFeedbackType {

    SMELTING(Particle.FLAME, Sound.BLOCK_FURNACE_FIRE_CRACKLE, ParticleOffset.ANY),
    GRINDING(Particle.SMOKE, Sound.BLOCK_GRINDSTONE_USE, ParticleOffset.ANY),
    ENCHANTING(Particle.ENCHANT, Sound.BLOCK_ENCHANTMENT_TABLE_USE, ParticleOffset.HEAD_ONLY),
    COOKING(Particle.SMOKE, Sound.BLOCK_BREWING_STAND_BREW, ParticleOffset.TOP),
    MECHANICAL(Particle.COMPOSTER, Sound.BLOCK_PISTON_CONTRACT, ParticleOffset.SIDE),
    FLUID(Particle.DRIP_WATER, Sound.ENTITY_GENERIC_SPLASH, ParticleOffset.BOTTOM);

    private final Particle defaultParticle;
    private final Sound defaultSound;
    private final ParticleOffset particleOffset;

    MachineFeedbackType(@Nonnull Particle defaultParticle, @Nonnull Sound defaultSound, @Nonnull ParticleOffset particleOffset) {
        this.defaultParticle = defaultParticle;
        this.defaultSound = defaultSound;
        this.particleOffset = particleOffset;
    }

    @Nonnull
    public Particle getDefaultParticle() {
        return defaultParticle;
    }

    @Nonnull
    public Sound getDefaultSound() {
        return defaultSound;
    }

    @Nonnull
    public ParticleOffset getParticleOffset() {
        return particleOffset;
    }

    public enum ParticleOffset {
        ANY,
        TOP,
        SIDE,
        BOTTOM,
        HEAD_ONLY
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -pl . -q
```

Expected: BUILD SUCCESS

---

## Task 2: 创建 `MachineFeedbackService`

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/slimefun4/core/services/MachineFeedbackService.java`

- [ ] **Step 1: 确认 `BlockPosition` 类是否存在**

```bash
# Check if BlockPosition is already available
```

`BlockPosition` 在 `io.github.thebusybiscuit.slimefun4.utils.BlockPosition` 中已经存在，无需创建。

- [ ] **Step 2: 写入 MachineFeedbackService.java**

```java
package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedbackType;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.BlockPosition;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;

public class MachineFeedbackService {

    private final Slimefun plugin;

    private final Set<BlockPosition> activeBlockStates = new HashSet<>();
    private final Map<BlockPosition, Set<Integer>> firedMilestones = new HashMap<>();
    private final Map<BlockPosition, Integer> particleTickCounters = new HashMap<>();

    public MachineFeedbackService(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
    }

    public void onMachineStart(@Nonnull Block block, @Nullable MachineFeedbackType type,
                               @Nonnull MachineOperation operation) {
        if (type == null) {
            return;
        }

        BlockPosition pos = new BlockPosition(block);

        if (hasLitProperty(block.getType())) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                BlockData data = block.getBlockData();
                if (data instanceof Lightable lightable) {
                    lightable.setLit(true);
                    block.setBlockData(data);
                }
            });
            activeBlockStates.add(pos);
        }

        firedMilestones.put(pos, new HashSet<>());
        particleTickCounters.put(pos, 0);

        spawnParticles(block, type);
    }

    public void onMachineTick(@Nonnull Block block, @Nullable MachineFeedbackType type,
                              @Nonnull MachineOperation operation) {
        if (type == null) {
            return;
        }

        BlockPosition pos = new BlockPosition(block);

        int counter = particleTickCounters.getOrDefault(pos, 0);
        counter++;
        particleTickCounters.put(pos, counter);

        if (counter % 2 == 0) {
            spawnParticles(block, type);
        }

        int prevPercent = (operation.getProgress() - 1) * 100 / operation.getTotalTicks();
        int currPercent = operation.getProgress() * 100 / operation.getTotalTicks();

        Set<Integer> milestones = firedMilestones.computeIfAbsent(pos, k -> new HashSet<>());
        for (int milestone : new int[]{25, 50, 75}) {
            if (prevPercent < milestone && currPercent >= milestone && !milestones.contains(milestone)) {
                block.getWorld().playSound(block.getLocation(), type.getDefaultSound(), SoundCategory.BLOCKS, 1.0f, 1.0f);
                milestones.add(milestone);
            }
        }
    }

    public void onMachineStop(@Nonnull Block block, @Nullable MachineFeedbackType type) {
        if (type == null) {
            return;
        }

        BlockPosition pos = new BlockPosition(block);

        if (activeBlockStates.contains(pos)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                BlockData data = block.getBlockData();
                if (data instanceof Lightable lightable) {
                    lightable.setLit(false);
                    block.setBlockData(data);
                }
            });
            activeBlockStates.remove(pos);
        }

        firedMilestones.remove(pos);
        particleTickCounters.remove(pos);
    }

    private void spawnParticles(@Nonnull Block block, @Nonnull MachineFeedbackType type) {
        Location loc = block.getLocation();
        Particle particle = type.getDefaultParticle();
        MachineFeedbackType.ParticleOffset offset = type.getParticleOffset();
        Material mat = block.getType();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        if (isSkull(mat)) {
            for (int i = 0; i < 4; i++) {
                double x = loc.getX() + 0.5 + rnd.nextDouble(-0.25, 0.25);
                double y = loc.getY() + 0.85 + rnd.nextDouble(0, 0.3);
                double z = loc.getZ() + 0.5 + rnd.nextDouble(-0.25, 0.25);
                loc.getWorld().spawnParticle(particle, x, y, z, 1, 0, 0.02, 0, 0.02);
            }
        } else if (offset == MachineFeedbackType.ParticleOffset.ANY) {
            loc.getWorld().spawnParticle(particle, loc.getX() + 0.5, loc.getY() + 1.05, loc.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.02);
            double sx = loc.getX() + 0.5 + (rnd.nextBoolean() ? 0.6 : -0.6);
            double sz = loc.getZ() + 0.5 + (rnd.nextBoolean() ? 0.6 : -0.6);
            loc.getWorld().spawnParticle(particle, sx, loc.getY() + 0.5, sz, 1, 0, 0.02, 0, 0.02);
        } else {
            loc.getWorld().spawnParticle(particle, loc.getX() + 0.5, loc.getY() + 1.05, loc.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0.03);
        }
    }

    private boolean hasLitProperty(@Nonnull Material mat) {
        return mat == Material.FURNACE || mat == Material.BLAST_FURNACE || mat == Material.SMOKER;
    }

    private boolean isSkull(@Nonnull Material mat) {
        return mat == Material.PLAYER_HEAD || mat == Material.PLAYER_WALL_HEAD
                || mat == Material.SKELETON_SKULL || mat == Material.SKELETON_WALL_SKULL
                || mat == Material.WITHER_SKELETON_SKULL || mat == Material.WITHER_SKELETON_WALL_SKULL
                || mat == Material.ZOMBIE_HEAD || mat == Material.ZOMBIE_WALL_HEAD
                || mat == Material.CREEPER_HEAD || mat == Material.CREEPER_WALL_HEAD;
    }

    public void cleanup() {
        activeBlockStates.clear();
        firedMilestones.clear();
        particleTickCounters.clear();
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -pl . -q
```

Expected: BUILD SUCCESS

---

## Task 3: 在 Slimefun 主类中注册 MachineFeedbackService

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/Slimefun.java`

- [ ] **Step 1: 添加 import**

在文件顶部 import 区域（约 line 35 附近，`MachineDamageService` import 之后）添加：

```java
import io.github.thebusybiscuit.slimefun4.core.services.MachineFeedbackService;
```

- [ ] **Step 2: 添加实例字段**

在 `MachineDamageService machineDamageService;` 之后（约 line 230）添加：

```java
private MachineFeedbackService machineFeedbackService;
```

- [ ] **Step 3: 初始化服务**

在 `machineDamageService.start();` 之后（约 line 358）添加：

```java
machineFeedbackService = new MachineFeedbackService(this);
```

- [ ] **Step 4: 在 onDisable 中清理**

找到 `machineDamageService.stop()` 所在位置（约 line 518），在其后添加：

```java
if (machineFeedbackService != null) {
    machineFeedbackService.cleanup();
}
```

- [ ] **Step 5: 添加静态访问方法**

在 `getMachineDamageService()` 方法之后（约 line 1026）添加：

```java
/**
 * 获取 {@link MachineFeedbackService} 实例。
 *
 * @return {@link MachineFeedbackService} 实例
 */
public static @Nonnull MachineFeedbackService getMachineFeedbackService() {
    validateInstance();
    return instance.machineFeedbackService;
}
```

- [ ] **Step 6: 验证编译**

```bash
mvn compile -pl . -q
```

Expected: BUILD SUCCESS

---

## Task 4: 修改 AContainer 添加 feedbackType 字段和钩子

**Files:**
- Modify: `src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java`

- [ ] **Step 1: 添加 import**

在文件顶部 import 区域添加：

```java
import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedbackType;
import javax.annotation.Nullable;
```

- [ ] **Step 2: 添加 feedbackType 字段**

在 `private int processingSpeed = -1;` 之后（约 line 73）添加：

```java
protected @Nullable MachineFeedbackType feedbackType = null;
```

- [ ] **Step 3: 添加 getter**

在适当位置添加：

```java
@Nullable
public MachineFeedbackType getMachineFeedbackType() {
    return feedbackType;
}
```

- [ ] **Step 4: 修改 tick() 方法 — 插入三处钩子**

将 [AContainer.java L371-L410](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java#L371-L410) 修改为：

```java
protected void tick(Block b) {
    var data = StorageCacheUtils.getDataContainer(b.getLocation());
    if (data != null && Slimefun.getMachineDamageService().isMachineDamaged(data)) {
        return;
    }

    BlockMenu inv = StorageCacheUtils.getMenu(b.getLocation());
    CraftingOperation currentOperation = processor.getOperation(b);

    if (currentOperation != null) {
        if (takeCharge(b.getLocation())) {
            Slimefun.getMachineDamageService().processMachineWork(b.getLocation(), this);

            if (!currentOperation.isFinished()) {
                processor.updateProgressBar(inv, 22, currentOperation);
                currentOperation.addProgress(1);
                Slimefun.getMachineFeedbackService().onMachineTick(b, feedbackType, currentOperation);
            } else {
                inv.replaceExistingItem(22, new CustomItemStack(Material.BLACK_STAINED_GLASS_PANE, " "));

                for (ItemStack output : currentOperation.getResults()) {
                    inv.pushItem(output.clone(), getOutputSlots());
                }

                processor.endOperation(b);
                Slimefun.getMachineFeedbackService().onMachineStop(b, feedbackType);
            }
        }
    } else {
        MachineRecipe next = findNextRecipe(inv);

        if (next != null) {
            currentOperation = new CraftingOperation(next);
            processor.startOperation(b, currentOperation);

            processor.updateProgressBar(inv, 22, currentOperation);
            Slimefun.getMachineFeedbackService().onMachineStart(b, feedbackType, currentOperation);
        }
    }
}
```

- [ ] **Step 5: 验证编译**

```bash
mvn compile -pl . -q
```

Expected: BUILD SUCCESS

---

## Task 5: 修改 AGenerator 添加 feedbackType 字段和钩子

**Files:**
- Modify: `src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AGenerator.java`

- [ ] **Step 1: 添加 import**

```java
import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedbackType;
import javax.annotation.Nullable;
```

- [ ] **Step 2: 添加字段**

在类体合适位置添加：

```java
protected @Nullable MachineFeedbackType feedbackType = null;
```

- [ ] **Step 3: 完整读取 AGenerator 以确定精确修改位置**

先读取 `AGenerator.java` 的 `getGeneratedOutput()` 方法完整内容，然后按照以下模式插入钩子：

- `operation.addProgress(1);` 之后 → `Slimefun.getMachineFeedbackService().onMachineTick(l.getBlock(), feedbackType, operation);`
- `processor.endOperation(l);` 之后 → `Slimefun.getMachineFeedbackService().onMachineStop(l.getBlock(), feedbackType);`
- `processor.startOperation(l, new FuelOperation(fuel));` 之后 → `Slimefun.getMachineFeedbackService().onMachineStart(l.getBlock(), feedbackType, /* 新 operation */);`

**注意：** AGenerator 使用 `Location l` 参数而非 `Block b`，需要转换 `l.getBlock()`。

- [ ] **Step 4: 验证编译**

```bash
mvn compile -pl . -q
```

Expected: BUILD SUCCESS

---

## Task 6: 修改所有用电器子类（24 种 AContainer）

**Files (逐一修改):**
每个文件在构造函数末尾添加 `this.feedbackType = MachineFeedbackType.XXX;`

| 文件路径 | feedbackType | import 需添加 |
|----------|:---:|---|
| `.../electric/machines/ElectricFurnace.java` | SMELTING | `MachineFeedbackType` |
| `.../electric/machines/ElectricSmeltery.java` | SMELTING | `MachineFeedbackType` |
| `.../electric/machines/HeatedPressureChamber.java` | SMELTING | `MachineFeedbackType` |
| `.../electric/machines/ElectricOreGrinder.java` | GRINDING | `MachineFeedbackType` |
| `.../electric/machines/ElectricDustWasher.java` | GRINDING | `MachineFeedbackType` |
| `.../electric/machines/ElectricIngotPulverizer.java` | GRINDING | `MachineFeedbackType` |
| `.../electric/machines/ElectricIngotFactory.java` | GRINDING | `MachineFeedbackType` |
| `.../electric/machines/ElectrifiedCrucible.java` | GRINDING | `MachineFeedbackType` |
| `.../electric/machines/enchanting/AutoEnchanter.java` | ENCHANTING | `MachineFeedbackType` |
| `.../electric/machines/enchanting/AutoDisenchanter.java` | ENCHANTING | `MachineFeedbackType` |
| `.../electric/machines/enchanting/BookBinder.java` | ENCHANTING | `MachineFeedbackType` |
| `.../electric/machines/FoodFabricator.java` | COOKING | `MachineFeedbackType` |
| `.../electric/machines/Freezer.java` | COOKING | `MachineFeedbackType` |
| `.../electric/machines/FoodComposter.java` | COOKING | `MachineFeedbackType` |
| `.../electric/machines/AutoBrewer.java` | COOKING | `MachineFeedbackType` |
| `.../electric/machines/AutoDrier.java` | COOKING | `MachineFeedbackType` |
| `.../electric/machines/ElectricPress.java` | MECHANICAL | `MachineFeedbackType` |
| `.../electric/machines/CarbonPress.java` | MECHANICAL | `MachineFeedbackType` |
| `.../electric/machines/AutoAnvil.java` | MECHANICAL | `MachineFeedbackType` |
| `.../electric/machines/entities/ProduceCollector.java` | MECHANICAL | `MachineFeedbackType` |
| `.../electric/machines/Refinery.java` | FLUID | `MachineFeedbackType` |
| `.../electric/machines/ElectricGoldPan.java` | FLUID | `MachineFeedbackType` |
| `.../geo/OilPump.java` | FLUID | `MachineFeedbackType` |
| `.../electric/machines/ChargingBench.java` | MECHANICAL | `MachineFeedbackType` |

- [ ] **Step 1-N: 逐文件修改**

每个文件需要：
1. 添加 import: `import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedbackType;`
2. 在构造函数末尾（`super(...)` 调用之后）添加: `this.feedbackType = MachineFeedbackType.XXX;`

示例 (ElectricFurnace.java):

```java
import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedbackType;

// ... class body

public ElectricFurnace(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
    super(itemGroup, item, recipeType, recipe);
    this.feedbackType = MachineFeedbackType.SMELTING;
}
```

**特别注意 AutoAnvil:** 构造函数签名为 `(ItemGroup, int repairFactor, SlimefunItemStack, RecipeType, ItemStack[])`，需按实际参数调用 `super`。

**特别注意 AbstractEnchantmentMachine:** `AutoEnchanter` 和 `AutoDisenchanter` 通过 `AbstractEnchantmentMachine` 间接继承 `AContainer`。确认 `AbstractEnchantmentMachine` 的构造函数签名一致后，在子类构造函数中设置即可。

- [ ] **完成检查: 验证编译**

```bash
mvn compile -pl . -q
```

Expected: BUILD SUCCESS

---

## Task 7: 修改所有发电机子类（5 种 AGenerator）

**Files (逐一修改):**

| 文件路径 | feedbackType |
|----------|:---:|
| `.../electric/generators/CoalGenerator.java` | SMELTING |
| `.../electric/generators/LavaGenerator.java` | SMELTING |
| `.../electric/generators/CombustionGenerator.java` | SMELTING |
| `.../electric/generators/MagnesiumGenerator.java` | SMELTING |
| `.../electric/generators/BioGenerator.java` | COOKING |

**不修改:** `SolarGenerator.java`（保持 feedbackType = null）

- [ ] **Step 1-N: 逐文件修改**

与 Task 6 相同模式，每个文件添加 import 和构造函数中的 `this.feedbackType = MachineFeedbackType.XXX;`

- [ ] **完成检查: 验证编译**

```bash
mvn compile -pl . -q
```

Expected: BUILD SUCCESS

---

## Task 8: 最终编译验证

- [ ] **Step 1: 完整编译**

```bash
mvn clean package -pl . -q -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 确认 jar 包生成**

```bash
dir target\*.jar
```

Expected: 列出生成的 jar 文件

---

## 边界情况自检清单

| 情况 | 是否覆盖 |
|------|:--:|
| `feedbackType == null` → 跳过所有反馈 | ✅ Service 方法入口检查 |
| 机器损坏 → 不触发反馈 | ✅ tick() 中 return 在钩子之前 |
| 电力不足 → 不触发反馈 | ✅ takeCharge() 返回 false 不进分支 |
| SolarGenerator 继承 SlimefunItem 非 AGenerator → 无反馈 | ✅ 不修改 SolarGenerator |
| 头颅方块粒子生成在正确位置 | ✅ isSkull() 判断 + 独立 offset |
| BlockState 在线程安全的主线程执行 | ✅ Bukkit.runTask() |
| 服务端 onDisable 清理 | ✅ cleanup() 调用 |
| Audio 不堆叠 | ✅ firedMilestones Set 防重复 |
| playSound/spawnParticle 异步安全 | ✅ 所有 Bukkit API 包裹在 Bukkit.runTask() 中 |
| block.getType() 异步安全 | ✅ isSkull/hasLitProperty 判断移入 runTask 内 |
| AGenerator 主线程安全 | ✅ EnergyNet.tickAllGenerators() 由 runTaskTimer 调度到主线程 |
