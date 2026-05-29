# 机器工作反馈系统 — 设计文档 (Design Spec)

> 版本: 1.0 | 日期: 2026-05-24 | 状态: Draft

## 1. 概述 (Overview)

### 1.1 目标

为 Slimefun4 的用电器 (`AContainer`) 和主动型发电机 (`AGenerator`) 添加统一的工作状态反馈系统，包含三类反馈：

| 类型 | 描述 |
|------|------|
| **视觉 — 方块状态** | 熔炉类方块设置 `lit=true/false` 燃烧动画 |
| **视觉 — 粒子** | 方块类型感知的粒子效果（完整方块/头颅不同偏移） |
| **听觉 — 音效** | 按操作进度里程碑 (25%/50%/75%) 播放音效 |

### 1.2 范围

| 包含 | 排除 |
|------|------|
| 所有 `AContainer` 子类用电器 (24种) | 太阳能发电机 (`SlimefunItem`, 非 `AGenerator`) |
| Coal/Lava/Combustion/Bio/Magnesium 等 5 种 `AGenerator` 发电机 | 电容 (`CAPACITOR`) — 非"工作"概念 |
| | 连接器 (`CONNECTOR`) — 非机器 |
| | 反应堆 (Reactor) — 已有独立反馈 |
| | FluidPump (`SimpleSlimefunItem`, 非 `AContainer`) |
| | 多方块机器 Compressor/Smeltery… (`MultiBlockMachine`) |
| | GrowthAccelerator / AutoCrafter (`SlimefunItem`, 非 `AContainer`) |
| | EntityAssembler Wither/IronGolem (`SimpleSlimefunItem`) |

### 1.3 不作配置开关

根据决策，不添加配置文件开关。反馈始终启用。

---

## 2. 架构设计

### 2.1 新增文件

```
src/main/java/io/github/thebusybiscuit/slimefun4/
├── core/
│   ├── machines/
│   │   └── MachineFeedbackType.java      (新增: 枚举)
│   └── services/
│       └── MachineFeedbackService.java    (新增: 服务)
```

### 2.2 修改文件

| 文件 | 修改内容 |
|------|----------|
| `me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java` | 新增 `feedbackType` 字段 + 3处钩子 |
| `me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AGenerator.java` | 新增 `feedbackType` 字段 + 3处钩子 |
| `Slimefun.java` | 初始化 `MachineFeedbackService` 并注册到 `SlimefunRegistry` |
| `SlimefunRegistry.java` | 新增 `getMachineFeedbackService()` 方法 |
| 各机器子类 | 构造函数中设置 `this.feedbackType`（共 24 种 AContainer + 5 种 AGenerator） |
| `.../electric/machines/enchanting/BookBinder.java` | `this.feedbackType = ENCHANTING` |
| `.../electric/machines/entities/ProduceCollector.java` | `this.feedbackType = MECHANICAL` |

### 2.3 架构图

```
┌─────────────────────────────────────────────────┐
│                  Timer                          │
│             (TickerTask 10tick/s)               │
│                                                 │
│  ┌──────────────┐      ┌──────────────────┐     │
│  │  AContainer   │      │   AGenerator     │     │
│  │  .tick(Block) │      │   .getGenerated  │     │
│  │               │      │    Output()      │     │
│  │  feedbackType ─┼──────┼── feedbackType   │     │
│  │  ┌───────────┐│      │  ┌─────────────┐ │     │
│  │  │onMachine  ││      │  │ onMachine   │ │     │
│  │  │Start()    ││      │  │ Start()     │ │     │
│  │  │onMachine  ││      │  │ onMachine   │ │     │
│  │  │Tick()     ││      │  │ Tick()      │ │     │
│  │  │onMachine  ││      │  │ onMachine   │ │     │
│  │  │Stop()     ││      │  │ Stop()      │ │     │
│  │  └─────┬─────┘│      │  └──────┬──────┘ │     │
│  └────────┼──────┘      └─────────┼────────┘     │
│           │                       │              │
│           ▼                       ▼              │
│  ┌──────────────────────────────────────┐        │
│  │    MachineFeedbackService (单例)      │        │
│  │                                      │        │
│  │  - onMachineStart()  设置BlockState  │        │
│  │  - onMachineTick()   粒子+音效       │        │
│  │  - onMachineStop()   清除BlockState  │        │
│  │                                      │        │
│  │  内部:                               │        │
│  │   activeBlockStates: Set<BlockPos>   │        │
│  │   firedMilestones:   Map<BP,Set<Int>>│        │
│  │   tickCounter:        Map<BP, Int>   │        │
│  └──────────────────────────────────────┘        │
└─────────────────────────────────────────────────┘
```

---

## 3. MachineFeedbackType 枚举

### 3.1 定义

```java
package io.github.thebusybiscuit.slimefun4.core.machines;

import org.bukkit.Particle;
import org.bukkit.Sound;

public enum MachineFeedbackType {

    SMELTING  (Particle.FLAME,      Sound.BLOCK_FURNACE_FIRE_CRACKLE,   ParticleOffset.ANY),
    GRINDING  (Particle.SMOKE,      Sound.BLOCK_GRINDSTONE_USE,         ParticleOffset.ANY),
    ENCHANTING(Particle.ENCHANTMENT_TABLE, Sound.BLOCK_ENCHANTMENT_TABLE_USE, ParticleOffset.HEAD_ONLY),
    COOKING   (Particle.SMOKE,      Sound.BLOCK_BREWING_STAND_BREW,    ParticleOffset.TOP),
    MECHANICAL(Particle.COMPOSTER,  Sound.BLOCK_PISTON_CONTRACT,       ParticleOffset.SIDE),
    FLUID     (Particle.DRIPPING_WATER, Sound.ENTITY_GENERIC_SPLASH,   ParticleOffset.BOTTOM);

    private final Particle defaultParticle;
    private final Sound defaultSound;
    private final ParticleOffset particleOffset;

    MachineFeedbackType(Particle particle, Sound sound, ParticleOffset offset) {
        this.defaultParticle = particle;
        this.defaultSound = sound;
        this.particleOffset = offset;
    }

    public Particle getDefaultParticle() { return defaultParticle; }
    public Sound getDefaultSound() { return defaultSound; }
    public ParticleOffset getParticleOffset() { return particleOffset; }
}
```

### 3.2 ParticleOffset 枚举

```java
enum ParticleOffset {
    ANY,         // 完整方块: 顶面中心 + 四个侧面随机
    TOP,         // 仅上方
    SIDE,        // 四个侧面随机
    BOTTOM,      // 底部
    HEAD_ONLY    // 头颅方块专用: 正上方偏移
}
```

### 3.3 机器类别分配表

#### 用电器 (AContainer)

| 机器类 | Material (典型) | feedbackType |
|--------|:---:|---|
| ElectricFurnace | FURNACE | SMELTING |
| ElectricSmeltery | FURNACE | SMELTING |
| HeatedPressureChamber | CONCRETE | SMELTING |
| ElectricOreGrinder | CONCRETE | GRINDING |
| ElectricDustWasher | CONCRETE | GRINDING |
| ElectricIngotPulverizer | CONCRETE | GRINDING |
| ElectricIngotFactory | CONCRETE | GRINDING |
| ElectrifiedCrucible | CONCRETE | GRINDING |
| AutoEnchanter | PLAYER_HEAD | ENCHANTING |
| AutoDisenchanter | PLAYER_HEAD | ENCHANTING |
| BookBinder | CONCRETE | ENCHANTING |
| FoodFabricator | CONCRETE | COOKING |
| Freezer | CONCRETE | COOKING |
| FoodComposter | CONCRETE | COOKING |
| AutoBrewer | BREWING_STAND | COOKING |
| AutoDrier | CONCRETE | COOKING |
| ElectricPress | CONCRETE | MECHANICAL |
| CarbonPress | CONCRETE | MECHANICAL |
| AutoAnvil | ANVIL | MECHANICAL |
| ProduceCollector | CONCRETE | MECHANICAL |
| Refinery | CONCRETE | FLUID |
| ElectricGoldPan | CONCRETE | FLUID |
| OilPump | CONCRETE | FLUID |
| ChargingBench | CONCRETE | MECHANICAL |

#### 发电机 (AGenerator)

| 机器类 | feedbackType | 备注 |
|--------|:---:|---|
| CoalGenerator | SMELTING | 燃烧煤炭 |
| LavaGenerator | SMELTING | 熔岩燃烧 |
| CombustionGenerator | SMELTING | 燃烧燃料 |
| BioGenerator | COOKING | 生物质 |
| MagnesiumGenerator | SMELTING | 燃烧镁 |
| SolarGenerator | null | 被动型，不反馈 |

---

## 4. MachineFeedbackService

### 4.1 API

```java
package io.github.thebusybiscuit.slimefun4.core.services;

public class MachineFeedbackService {

    public MachineFeedbackService(Slimefun plugin);

    /**
     * 机器开始工作时调用。
     * - 对有 lit 属性的方块设置 BlockState (lit=true)
     * - 生成首帧粒子
     */
    public void onMachineStart(@Nonnull Block block, @Nullable MachineFeedbackType type,
                               @Nonnull MachineOperation operation);

    /**
     * 机器每 tick 工作中调用。
     * - 粒子: 每 2 tick 生成一批
     * - 音效: 25%/50%/75% 里程碑各触发一次
     */
    public void onMachineTick(@Nonnull Block block, @Nullable MachineFeedbackType type,
                              @Nonnull MachineOperation operation);

    /**
     * 机器停止时调用（操作完成或取消）。
     * - 清除 BlockState (lit=false)
     * - 清理 firedMilestones 记录
     */
    public void onMachineStop(@Nonnull Block block, @Nullable MachineFeedbackType type);
}
```

### 4.2 内部状态

```java
// 已激活 BlockState 的方块集合 (用于停止时清理)
private final Set<BlockPosition> activeBlockStates;

// 已触发的音效里程碑 (BlockPosition -> {25, 50, 75})
private final Map<BlockPosition, Set<Integer>> firedMilestones;

// 粒子生成计数器 (每个方块独立)
private final Map<BlockPosition, Integer> particleTickCounters;
```

### 4.3 参数决策

| 参数 | 值 | 理由 |
|------|---|------|
| 粒子频率 | 每 2 tick 一批 | 机器 tick=10tick/s → 每秒 5 批粒子，视觉连贯 |
| 音效里程碑 | 25%, 50%, 75% | 三次音效均匀贯穿整个操作周期 |
| 音效播放 | `SoundCategory.BLOCKS`, volume=1.0, pitch=1.0 | 与 Minecraft 方块音效一致 |
| BlockState 变更 | 主线程 `runTask()` 同步执行 | BlockState 必须在主线程操作 |

---

## 5. 粒子系统

### 5.1 方块类型感知偏移

```
方块 Material 判定:
  ├─ PLAYER_HEAD / SKELETON_SKULL / WITHER_SKELETON_SKULL / ZOMBIE_HEAD / CREEPER_HEAD 等
  │    └─ 头颅方块(不完整): 粒子在 (0.5, 0.75, 0.5) 方块正上方生成
  │       particleCount = 5
  │
  ├─ 其他不完整方块 (栅栏 FENCE, 台阶 SLAB, 楼梯 STAIRS 等)
  │    └─ 粒子在方块中心上浮: (0.5, blockHeight + 0.2, 0.5)
  │       particleCount = 3
  │
  └─ 完整方块 (CONCRETE, TERRACOTTA, FURNACE, STONE 等)
       └─ 粒子在顶面+侧面随机:
          - 顶面: (0.5, 1.0, 0.5)
          - 侧面: 从四个方向随机选一 → N/S/E/W 中心点偏移
          particleCount = 3
```

### 5.2 粒子数量与分布

| 条件 | 每批数量 | 分布 |
|------|:---:|------|
| 完整方块 ANY | 3~5 | 1顶面 + 2~4侧面随机 |
| 头颅 HEAD_ONLY | 3~5 | 集中在头颅上方小范围球形 |
| 不完整方块 | 3 | 中心上浮 |
| 粒子速度 | 0.02~0.05 | 轻微浮动，自然感 |

### 5.3 各类别备用粒子

除了默认粒子，部分类别可生成辅助粒子增强效果：

| feedbackType | 默认粒子 | 辅助粒子 (概率%) |
|-------------|---|---|
| SMELTING | FLAME | SMOKE (30%) |
| GRINDING | SMOKE | CRIT (20%) |
| ENCHANTING | ENCHANTMENT_TABLE | PORTAL (10%) |
| COOKING | SMOKE | VILLAGER_HAPPY (20%) |
| MECHANICAL | COMPOSTER | SMOKE (30%) |
| FLUID | DRIPPING_WATER | DRIPPING_LAVA (15%) |

---

## 6. 音效系统

### 6.1 里程碑触发

```
操作进度(tick):  0% ──…── 25% ──…── 50% ──…── 75% ──…── 100%

音效触发:                      🔊25     🔊50     🔊75
触发后设标志:                  set[25]  set[50]  set[75]
```

**触发逻辑 (伪代码):**

```java
int currPercent = operation.getProgress() * 100 / operation.getTotalTicks();

// 找到当前 tick 新达到的最高里程碑
int highestNewMilestone = -1;
for (int milestone : new int[]{25, 50, 75}) {
    if (currPercent >= milestone && !firedMilestones.get(pos).contains(milestone)) {
        highestNewMilestone = milestone;
    }
}

// 标记该里程碑及以下所有未标记里程碑，仅播放一次声音
if (highestNewMilestone >= 0) {
    for (int milestone : new int[]{25, 50, 75}) {
        if (milestone <= highestNewMilestone) {
            firedMilestones.get(pos).add(milestone);
        }
    }
    block.getWorld().playSound(
        block.getLocation(), type.getDefaultSound(), SoundCategory.BLOCKS, 1.0f, 1.0f
    );
}
```

> 设计说明: 大跳跃（如 25%→75%）只播放最高里程碑声音；精确跳过（如 46→49→52 跳过 50%）仍能触发。

### 6.2 操作完成/停止清理

`onMachineStop()` 中从 `firedMilestones` 移除该方块的记录，确保下次操作重新触发。

---

## 7. BlockState 变化

### 7.1 支持的方块类型

| Material | 属性 | 工作时 | 停止时 |
|----------|------|--------|--------|
| `FURNACE` | `lit` | true (燃烧动画) | false |
| `BLAST_FURNACE` | `lit` | true (燃烧动画) | false |
| `SMOKER` | `lit` | true (燃烧动画) | false |

### 7.2 实现细节

```java
// onMachineStart() — 必须切换主线程执行 BlockState 操作
if (hasLitProperty(block.getType())) {
    Bukkit.getScheduler().runTask(plugin, () -> {
        BlockState bs = block.getState();
        // org.bukkit.block.data.Lightable 接口
        if (bs.getBlockData() instanceof Lightable lightable) {
            lightable.setLit(true);
            bs.setBlockData(lightable);
            bs.update(true);
        }
    });
    activeBlockStates.add(new BlockPosition(block));
}

// onMachineStop()
if (activeBlockStates.contains(pos)) {
    Bukkit.getScheduler().runTask(plugin, () -> {
        Block block = pos.toLocation().getBlock();
        BlockState bs = block.getState();
        if (bs.getBlockData() instanceof Lightable lightable) {
            lightable.setLit(false);
            bs.setBlockData(lightable);
            bs.update(true);
        }
    });
    activeBlockStates.remove(pos);
    firedMilestones.remove(pos);
    particleTickCounters.remove(pos);
}
```

### 7.3 Lightable 接口兼容

`org.bukkit.block.data.Lightable` 是 Bukkit API 标准接口，FURNACE / BLAST_FURNACE / SMOKER 均实现了此接口，无需版本兼容处理。

---

## 8. 集成修改 (AContainer / AGenerator)

### 8.1 AContainer 修改

在 `AContainer` 类中：

```java
// 新增字段
protected @Nullable MachineFeedbackType feedbackType = null;

// 新增 getter
public @Nullable MachineFeedbackType getMachineFeedbackType() {
    return feedbackType;
}
```

在 `tick(Block b)` 方法中 ([L371-L410](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AContainer.java#L371-L410))：

```java
// 钩子1: 新操作开始 (startOperation 之后)
processor.startOperation(b, currentOperation);
processor.updateProgressBar(inv, 22, currentOperation);
// >>> 新增
Slimefun.getMachineFeedbackService().onMachineStart(b, feedbackType, currentOperation);

// 钩子2: 每tick工作中 (addProgress 之后)
currentOperation.addProgress(1);
// >>> 新增
Slimefun.getMachineFeedbackService().onMachineTick(b, feedbackType, currentOperation);

// 钩子3: 操作完成 (endOperation 之后)
processor.endOperation(b);
// >>> 新增
Slimefun.getMachineFeedbackService().onMachineStop(b, feedbackType);
```

**注意**: `onMachineStop` 需要在 `endOperation` 之后调用，因为此时操作已被移除。`onMachineStop` 不再需要 operation 参数（仅用于清理状态）。

### 8.2 AGenerator 修改

同样新增 `feedbackType` 字段。在 `getGeneratedOutput()` 中 ([L153-L201](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/me/mrCookieSlime/Slimefun/Objects/SlimefunItem/abstractItems/AGenerator.java#L153-L201)) 插入相同钩子。

### 8.3 Slimefun / SlimefunRegistry 修改

```java
// SlimefunRegistry 中新增
private MachineFeedbackService machineFeedbackService;

public MachineFeedbackService getMachineFeedbackService() {
    return machineFeedbackService;
}

// Slimefun 构造函数中 (或 onEnable 等效处)
machineFeedbackService = new MachineFeedbackService(this);
registry.setMachineFeedbackService(machineFeedbackService);
```

### 8.4 机器子类修改示例

```java
// ElectricFurnace.java
public class ElectricFurnace extends AContainer {
    // ...
    public ElectricFurnace(Category category, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(category, item, recipeType, recipe);
        this.feedbackType = MachineFeedbackType.SMELTING;  // 新增
    }
}

// SolarGenerator.java
public class SolarGenerator extends AGenerator {
    // ...
    // feedbackType 保持 null (默认 null，即不反馈)
}
```

---

## 9. 边界情况处理

| 情况 | 处理 |
|------|------|
| `feedbackType == null` | 跳过所有反馈逻辑（静音机器） |
| 机器损坏 | `tick()` 中 return 在 feedback 钩子之前，自然跳过 |
| 电力不足 | AContainer 中 `takeCharge()` 返回 false → 不进工作分支，不触发反馈 |
| 世界卸载/方块移除 | `TickerTask` 中 `disableTicker` 移除 tick → 反馈自动停止 |
| 服务端重载 | Service 作为 Slimefun 实例生命周期的一部分重新初始化 |
| 多世界 | 反馈位置绑定到 World+Location，自然隔离 |

---

## 10. 实现顺序

1. 创建 `MachineFeedbackType` 枚举和 `ParticleOffset` 枚举
2. 创建 `MachineFeedbackService` 服务类
3. 在 `SlimefunRegistry` 中注册服务
4. 在 `Slimefun` 主类中初始化服务
5. 修改 `AContainer` 添加 `feedbackType` 字段和 3 处钩子
6. 修改 `AGenerator` 添加 `feedbackType` 字段和 3 处钩子
7. 逐一修改各机器子类构造函数设置 `feedbackType`（24 种 `AContainer` + 5 种 `AGenerator`）
8. 编译测试

---

## 11. 线程安全

### 11.1 背景

`AContainer.tick()` 由 `BlockTicker` 在异步线程调用（`isSynchronized() = false`），因此 `MachineFeedbackService` 的所有方法均被异步调用。`playSound()`、`spawnParticle()`、`setBlockData()` 等 Bukkit API **必须在主线程**执行，否则 Spigot `AsyncCatcher` 会拦截并报错。

`AGenerator.getGeneratedOutput()` 由 `EnergyNet.tickAllGenerators()` → `Bukkit.runTaskTimer()` 在主线程调用，无需特殊处理。

### 11.2 修复方案

所有 Bukkit API 调用均包裹在 `Bukkit.getScheduler().runTask(plugin, () -> {...})` 中：

| 操作 | 包裹位置 |
|------|----------|
| `world.playSound()` | `onMachineTick()` 内 `runTask` |
| `world.spawnParticle()` | `spawnParticles()` 内 `runTask` |
| `block.setBlockData()` / `block.getBlockData()` | `onMachineStart()` / `onMachineStop()` 内 `runTask` |
| `block.getType()` (Lit/Skull 判断) | `onMachineStart()` / `spawnParticles()` 内 `runTask` |

随机数生成在异步线程完成（压低主线程负担），仅最终 Bukkit API 调用切换到主线程。

---

## 12. 已知限制

- 方块状态变化对非 `Lightable` 材料无效（如混凝土方块），仅通过粒子+音效表达状态
- 粒子效果距离受 Minecraft 客户端渲染距离限制
- 大量机器同时工作时可能有粒子密度问题，但每 2 tick 一批的速率已做控制
