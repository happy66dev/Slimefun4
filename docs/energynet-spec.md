# EnergyNet 能源电网技术实现文档

> 最后更新：2026-05-24 | 版本：v2.6（三阶段异步重构 + 功能完整性修复）

---

## 目录

1. [架构概览](#1-架构概览)
2. [组件类型与术语](#2-组件类型与术语)
3. [电网生命周期](#3-电网生命周期)
4. [网络发现（成员收集）](#4-网络发现成员收集)
5. [路径预计算（路由）](#5-路径预计算路由)
6. [电力传输流程](#6-电力传输流程)
7. [连接规则与距离度量](#7-连接规则与距离度量)
8. [显示与指令](#8-显示与指令)
9. [调试系统](#9-调试系统)
10. [诊断指南](#10-诊断指南)
11. [已知限制与未来改进](#11-已知限制与未来改进)

---

## 1. 架构概览

EnergyNet 继承自 Slimefun 的 `Network` 抽象类，负责管理一个能源调节器所辖的电网。

```
EnergyNet (extends Network)
├── 成员映射
│   ├── generators  : Map<Location, EnergyNetProvider>
│   ├── capacitors  : Map<Location, EnergyNetComponent>
│   ├── consumers   : Map<Location, EnergyNetComponent>
│   └── connectors  : Map<Location, EnergyNetComponent>
├── 路径数据
│   ├── generatorPaths        : Map<Location, Set<EnergyPath>>  (发电机→用电器)
│   ├── capacitorPaths        : Map<Location, Set<EnergyPath>>  (电容→用电器)
│   └── generatorToCapacitorPaths : Map<Location, Map<Location, EnergyPath>> (发电机→电容，仅显示用)
├── 状态标志
│   ├── initializing / initialized / pendingInit
│   ├── abortRequested / destroyed / conflictMode
│   └── netNewEnergy / perGeneratorNewCharge
├── 连接器负载
│   └── connectorLoad : Map<Location, Long>
├── 调试计数器
│   └── bfsDebugLogCount : AtomicInteger (每次初始化重置)
└── 常量
    ├── RANGE = 6          (调节器覆盖半径)
    ├── MAX_BFS_NODES = 100_000  (BFS节点上限防OOM)
    ├── DEBUG = false      (完整调试日志开关)
    ├── DEBUG_PATHS = false (BFS路由日志开关)
    └── MAX_BFS_DEBUG_LOG = 200  (每条BFS细节日志最多打印次数)
```

---

## 2. 组件类型与术语

| 术语 | EnergyNetComponentType | NetworkComponent | 说明 |
|------|:---:|:---:|------|
| 发电机 | `GENERATOR` | `TERMINUS` | 发电设备。分可储电（煤机）和不可储电（太阳能） |
| 用电器 | `CONSUMER` | `TERMINUS` | 耗电设备，**不作为 BFS 发送方**（无出边） |
| 货运管理器 | `CONSUMER` | `REGULATOR` | 特殊用电器，货运网络调节器。可变容量 `128+4×输入节点数` J，每个tick消耗电力 |
| 电容 | `CAPACITOR` | `CONNECTOR` | 储能设备。电容→电容仅6方向相邻，电容↔连接器/调节器6方向相邻 |
| 连接器 | `CONNECTOR` | `CONNECTOR` | 明文连接器。有 `range` 属性控制轴向覆盖距离 |
| 短路连接器 | `CONNECTOR` | `CONNECTOR` | `SHORT_CIRCUIT_ENERGY_CONNECTOR`，基于 `EnergyConnector`，range=1，当前为占位版本 |
| 调节器 | *（不实现接口）* | `REGULATOR` | **特殊连接器**，RANGE=6（轴向）。可作为路径跳点 |

> **特殊规则**：能源调节器不实现 `EnergyNetComponent`，但被当作特殊连接器处理——BFS 可以通过它路由，且每过一个调节器计1跳。

---

## 3. 电网生命周期

```
       ┌─────────────┐
       │  未初始化    │
       └──────┬──────┘
              │ tick() 检测到 !initialized
              ▼
       ┌─────────────┐
       │ pendingInit  │ → 提交到 GRID_EXECUTOR（单线程池）
       └──────┬──────┘
              │
              ▼
       ┌─────────────┐
       │ initializing │
       └──────┬──────┘
              │
    ┌─────────┼─────────┐
    │         │         │
    ▼         ▼         ▼
 冲突     中断/销毁   成功
 conflictMode  │     initialized=true
              │
              ▼
       ┌─────────────┐
       │ 已就绪      │ ← 每个 tick 执行 performEnergyTransfer()
       └─────────────┘
```

**触发重新初始化**：
- 任何电网内机器被放置/拆除 → `markDirty()` → 下次 `tick()` 检测到 `!initialized`
- 电网冲突 → `wakeUpConflictNets()` → `abortAllInitializing()`

**异步初始化线程**：使用 `GRID_EXECUTOR`（单线程 `ExecutorService`），避免阻塞主线程。线程名为 `Slimefun-Grid-Init`，关闭时有 5 秒优雅等待。

---

## 4. 网络发现（成员收集）

### 4.1 `collectNetworkMembers()` [L1337](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L1337)

**阶段1：调节器6轴向扩展**（RANGE=6格）

```java
int[][] axes = {{1,0,0}, {-1,0,0}, {0,1,0}, {0,-1,0}, {0,0,1}, {0,0,-1}};
for each axis:
    for i = 1..RANGE:
        检查 regulator + axis * i 位置
        如果是 CONNECTOR/CAPACITOR → 加入 BFS 队列
        如果是 GENERATOR/CONSUMER → 仅标记 visited
        如果是 ENERGY_REGULATOR（不实现 EnergyNetComponent，需显式 id 检查）→ 冲突检测
```

> **注意**：`EnergyRegulator` 不实现 `EnergyNetComponent` 接口，因此 `getComponent()` 返回 null，**必须通过 `querySlimefunItemFromDb()` 查到后显式检查 id.equals("ENERGY_REGULATOR")**。如果忽略此检查，两个调节器直接堆叠（无连接器）时将无法检测冲突。

**阶段2：BFS 队列扩展**

| 遇到节点 | 处理函数 | 搜索方式 |
|---------|---------|---------|
| CONNECTOR | `processConnector()` | 6轴向 × range 格 |
| CAPACITOR | `processCapacitor()` | 6方向（仅相邻1格） |

**阶段3：分类到映射**

遍历所有 `visited` 位置，按类型分入 `generators`/`consumers`/`capacitors`/`connectors`。

### 4.2 `processConnector()` [L1518](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L1518)
- 6轴向 × range 格线性扫描
- 每步检查：缓存 `getComponent()` → DB查询 `querySlimefunItemFromDb()`
- 发现 CONNECTOR/CAPACITOR 加入队列继续扩展
- 发现调节器（冲突检测，也通过 `dbItem.getId().equals("ENERGY_REGULATOR")` 显式判断）→ `conflictMode`

### 4.3 `processCapacitor()` [L1616](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L1616)
- 仅6方向相邻（曼哈顿距离=1）
- 目标必须是 CAPACITOR 或 CONNECTOR
- CONNECTOR 需通过 `checkRangeValidation()` 验证

---

## 5. 路径预计算（路由）

### 5.1 `precomputePaths()` [L922](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L922)

按顺序计算三类路径：
1. **发电机→用电器**：每个发电机调用 `findShortestPathsFromSource()`
2. **电容→用电器**：每个电容调用 `findShortestPathsFromSource()`
3. **发电机→电容**：每个发电机调用 `findShortestPathsToCapacitors()`（仅用于显示）

### 5.2 `findShortestPathsFromSource()` [L981](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L981)

**核心BFS算法**：
- `List<BFSNode>` + head指针（替代Queue，支持父节点回溯）
- 遇到 CONSUMER → 记录路径（最短距离优先，等长路径保留多条）
- **消费者允许多条路径**：消费者不参与 `bfsVisited` 去重，多路径在连接器汇聚后仍能分别到达用电器
- 遇到 CONNECTOR → 剪枝（跳过已找到更短路径的连接器）
- 遇到 `regulator`（组件为null） → 调用 `addRegulatorNeighbors()`
- 遇到 CAPACITOR → `getNeighbors(CAPACITOR)` 获取邻居（包括电容桥接）

**跳数计算**：
- 进入 CONNECTOR → `newLength++`
- 进入 regulator → `newLength++`
- 进入 GENERATOR/CONSUMER/CAPACITOR → 不变

**路径提取** `extractConnectorsFromPath()` [L1497](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L1497)：
沿着父节点链回溯（从消费者→源端方向），提取所有 CONNECTOR、CAPACITOR（桥接）和 regulator 节点，然后 `Collections.reverse()` 为源端→消费者方向。
支持 `excludeTarget` 参数，用于电容充电时排除目标电容自身。

### 5.3 `getNeighbors()` [L1184](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L1184)

| 类型 | 邻居来源 |
|------|---------|
| GENERATOR | 调节器 + 短路连接器（**不允许直达LongRangeConnector**，需先经短路连接器） |
| CONSUMER | **无出边**（不能作为发送方） |
| CAPACITOR | 相邻电容(曼哈顿=1) + 调节器(26邻居) + 短路连接器(26邻居，**排除LongRangeConnector**) |
| CONNECTOR(普通) | 其他连接器 + 发电机 + 调节器 + 用电器 + 电容（**全部使用轴向范围检查** `isWithinRangeAxial`） |
| CONNECTOR(长途) | **仅6个轴向上最近的连接器**（扫描每个轴向，遇到第一个连接器即停，遇任意非空方块也停） |

---

### 5.4 `findShortestPathsToCapacitors()` [L1209](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L1209)

**电容充电路径搜索**：
- 与 `findShortestPathsFromSource` 类似，但终点是电容器
- **支持电容桥接**：遇到电容后不 `continue`，继续搜索相邻电容
- **桥接跳数**：电容→电容的跳步增加1跳
- **跳数提取**：`extractConnectorsFromPath` 会提取桥接电容（排除目标电容自身）

```
示例：发电机→连接器A→电容Bridge→电容Target
路径：connectors=[连接器A, 电容Bridge], length=2
显示：本机 → (连接器A) → (电容Bridge) → 电容Target (跳数: 2)
```

---

## 6. 电力传输流程 (v2.6 — 三阶段异步架构)

### 6.0 架构概览

```
tickSelfMainThread() [主线程]
├── Phase 1: tickAllGenerators() → 发电机产电
│           tickAllCapacitors() → 清理损坏电容
│           collectTickSnapshot() → 冻结状态 (11字段)
├── Phase 2: GRID_TICK_EXECUTOR [异步]
│           computeTransfers(snapshot)
│               ├── transferFromGeneratorsSnapshot() → 最短路径 + 限电器
│               ├── transferFromCapacitorsSnapshot() → 电容放电 + 限电器
│               └── → TransferResult (deltas + excess)
└── Phase 3: Slimefun.runSync [主线程]
            applyTransferResult(result, snapshot)
                ├── gen/cap/con charge delta 写回
                ├── storeRemainingEnergy() → 多余电力存电容
                ├── 能量克隆防护 (perGeneratorNewCharge 比例扣减)
                ├── propagateToEnergyMeters() → 电量计数器
                ├── ConnectorAgingManager.processAging() → 连接器老化
                └── 统计 + 全息图
```

### 6.1 关键设计决策

- `storeRemainingEnergy()` 保留在 Phase 3 (主线程)：它内部访问 StorageCacheUtils、MachineDamageService、component.setCharge()
- Phase 2 只计算"用电器满足后还剩多少能量 (excessEnergy)"
- 路径映射使用不可变视图 (`Collections.unmodifiableMap`)，避免每 tick 深拷贝开销
- 旧 `performEnergyTransfer()` / `transferFromGenerators()` / `transferFromCapacitors()` 已移除，逻辑迁移至三阶段架构

### 6.2 `performEnergyTransfer()` [已移除 — 逻辑迁移至三阶段]

旧版流程 (已移除):
```
tickAllGenerators() → 发电机产电

tickAllCapacitors() → 排除损坏电容

calculateTotalDemand() → 所有用电器缺口总和

if generatorSupply >= totalDemand:
    transferFromGenerators(totalDemand)     → 发电机供电给用电器
    totalConsumedThisTick = totalDemand     ← 记录本tick总消耗
    excess = totalNew - usedFromNew         → 剩余电力
    stored = storeRemainingEnergy(excess)   → 存入电容（返回实际存储量）
    按比例扣减发电机（只扣 stored 的量）      → 避免能量克隆
else:
    supplyLeft = transferFromGenerators(generatorSupply)  → 发电机供应
    remainingDemand = totalDemand - (genSupply - supplyLeft) → 正确计算剩余需求
    consumedFromGenerators = genSupply - supplyLeft
    if remainingDemand > 0:
        capLeft = transferFromCapacitors(remainingDemand)
        consumedFromCapacitors = remainingDemand - capLeft
    totalConsumedThisTick = consumedFromGenerators + consumedFromCapacitors  ← 记录本tick总消耗
```

### 6.2 `storeRemainingEnergy()` [L419](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L419)

- 返回 `long`：实际存入电容的总焦耳数
- **路径优先**：使用 `generatorToCapacitorPaths` 构建电容优先级排序表
- 按路径长度排序（短路径优先），无路径的电容排最后
- 遍历排序后的电容列表，按序填充直到 `remainingEnergy` 耗尽或电容全满
- 同时累加机器损坏计数器（每1%容量触发一次报废检查）

### 6.3 `transferFromGenerators()` [L1720](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L1720)
- 路径按长度排序（短路径优先）
- 按(源, 目标, 长度)分组
- 可储电发电机：`generator.setCharge(genCharge - transferAmount)`
- 不可储电（太阳能）：从 `nonChargeableSupply` 中扣除
- 记录连接器负载（均摊到组内路径）

### 6.4 `transferFromCapacitors()` [L1812](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L1812)
- 与 `transferFromGenerators` 逻辑相同
- 从 `capacitor.setCharge()` 扣减

---

## 7. 连接规则与距离度量

### 7.1 距离函数

| 函数 | 公式 | 用途 |
|------|------|------|
| `isAdjacent(loc1, loc2)` | `\|dx\|+\|dy\|+\|dz\| == 1` | **曼哈顿距离=1**（6方向：前后上下左右） |
| `isWithinRange(src, tgt, range)` | `\|dx\|≤range && \|dy\|≤range && \|dz\|≤range` | **切比雪夫距离**（26邻居含对角线） |
| `isWithinRangeAxial(src, tgt, range)` | (两个轴差=0, 第三个轴差≤range且>0) | **轴向距离**（6轴向，与processConnector一致） |
| `getAxialDistance(loc1, loc2)` | `\|dx\|+\|dy\|+\|dz\|` | **曼哈顿距离**（调试显示用） |

> **注意**：连接器**和调节器**的范围检查始终使用 `isWithinRangeAxial`（轴向），与 `processConnector` 和调节器6轴向搜索保持一致，确保BFS路径计算和成员收集阶段行为一致。
>
> **长途连接器（LongRangeConnector）特殊规则**：
> - `LongRangeConnector` 的 range=128，但 `getMaxConnectorRange()` 排除它，不参与调节器/发电机搜索范围计算
> - 当普通连接器作为发送方且目标为长途连接器时，正向验证使用长途连接器的 range(128)（这意味着普通连接器在范围内时能接入长途网络，但反向传输被下一规则阻止）
> - 长途连接器在 `processConnector()` 中扫描每个轴向，找到第一个 CONNECTOR 后 `break`（只连最近）
> - 长途连接器在 `getNeighbors(CONNECTOR)` 中扫描6轴向，找到每个方向上最近的连接器，**双向校验**：目标连接器必须能用**自身的范围**沿轴向覆盖长途连接器，否则不建立邻居；普通连接器侧对长途连接器也附加 `isWithinRangeAxial(长途, 自身, 自身范围)` 校验。两侧等价（`distance ≤ target.range`），实现**完全隔离**。
> - 长途连接器有老化机制，使用碳金能源连接器的配置（512/2000/8000, 碳金修复）

> **短路连接器（SHORT_CIRCUIT_ENERGY_CONNECTOR）占位规则**：
> - 使用 `EnergyConnector` 注册，`range=1`，只按普通连接器规则参与 6 轴向相邻/覆盖检查，不走 `LongRangeConnector` 的最近连接器隔离逻辑。
> - 物品材质为 `LIGHT_GRAY_CONCRETE`，lore 标记为范围 1，用途是极近距离连接能源网络。
> - 当前配方为 9 个淡灰色混凝土合成 1 个短路连接器，属于占位配方，后续可按正式玩法重新设计。
> - 研究为 `short_circuit_connectors`，研究 id `284`，英文名 `Short-Circuit Connections`，中文名 `短路连接`。
> - 老化参数暂与黑钻/长途连接器一致：512/2000/8000 J/t，预期寿命 27,648,000 tick，修复物品配置为碳金。

### 7.2 `validateConnection()` [L876](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L876)

| 发送方→接收方 | 正向验证 | 反向验证 |
|--------------|---------|---------|
| CAPACITOR↔CAPACITOR | `isAdjacent`（直接返回） | 不需要 |
| CONNECTOR→普通CONNECTOR | `isWithinRangeAxial(发送方, 接收方, 发送方.range)` | 不需要 |
| CONNECTOR→长途CONNECTOR | `isWithinRangeAxial(发送方, 接收方, 长途.range=128)` | 不需要 |
| GENERATOR→CONNECTOR | `true`（发电机不验证） | `isWithinRangeAxial(conn, gen, conn.range)` |
| CAPACITOR→CONNECTOR | `isAdjacent(cap, conn)`（6方向相邻） | `isWithinRangeAxial(conn, cap, conn.range)` |
| CONNECTOR↔CONNECTOR | 默认已在轴向范围内 | 不需要 |

---

## 8. 显示与指令

### 8.1 调节器悬浮字（多行 ArmorStand 方案）
- 使用 `HologramsService.setMultiLineHologram()` 实现，每行一个独立的 ArmorStand
- 三行 ArmorStand 沿 Y 轴以 0.3 间距垂直堆叠
- 初始化中：`"&e初始化电网中 N%"`（单行）
- 初始化完成但未首次tick：`"&e初始化完成，等待首个tick数据..."`（单行，首个tick成功后才切多行）
- 已就绪（首行）：`"+N J ⚡"` 或 `"-N J ⚡"`（电力净差额）
- 已就绪（次行）：`"&a产出 +N J &7| &c消耗 -N J"`（本tick产出/消耗）
- 已就绪（第三行）：`"&e储能 N &7/ &eN &7J"`（当前总存电量 / 最大可存容量）
- 冲突时自动切换回单行：`removeMultiLineHologram() + updateHologram()`
- 冲突：`"&c电网冲突：*"`
- 空闲：`"&7电网已就绪，等待接入设备"`

### 8.2 `/sf grid` 指令 — `getComponentInfo()` [L2071](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L2071)

| 查看目标 | 显示内容 |
|---------|---------|
| 发电机 | 到达用电器的路由（跳数排序）+ 电容充电路由 |
| 用电器 | 来自发电机/电容的到达路由 |
| 连接器 | 范围 + 本刻负载 |
| 电容 | 到达用电器的路由 + 相邻电容桥接 |
| 调节器 | 完整电网概览（成员数 + 路径总数） |

### 8.3 路线组显示格式

```
▼ 发电机路由 (出发)
  → 用电器坐标 (跳数: 2) ×3条路线
    路线 1: (连接器1)→(连接器2)
    路线 2: (连接器3)→(连接器4)
    路线 3: (连接器1)→(连接器5)

▼ 用电器路由 (到达)
  来自发电机:
  ← 发电机坐标 (跳数: 2) ×2条路线
    路线 1: (连接器1)→(连接器2)
    路线 2: (连接器3)→(连接器4)
  来自电容:
  ← [电容] 电容坐标 (跳数: 1)
```

- 同（源, 目标, 跳数）的多条路径标记 `×N条路线`
- >1条时逐条展开显示各路线连接器链

### 8.4 万用表 (Multimeter) — 物品 + 管理员指令

万用表是玩家可合成的电网检测工具。同时提供管理员指令 `/sf multimeter` 作为调试替代。

#### 8.4.1 物品Lore及使用方式

**物品Lore：**
```
右键 - 查看电网设备详细信息
潜行+右键 - 切换路线粒子/连接器负载

粒子颜色说明:
● 白色 = 多目标共享路段
● 淡蓝 = 电容充电路线
● 橙●粉 = 用电器供电路线
```

**使用方式：**

| 操作 | 效果 |
|------|------|
| **右键** 机器 | 显示电网设备信息文本（类型、电量/容量、电网信息、路径详情） |
| **潜行+右键** 发电机/用电器/电容 | **切换**路径显示：路径粒子(15s) + 连接器负载全息 + 端点储能全息(每秒实时刷新) |
| **潜行+右键** 连接器 | 显示连接器刻负载全息(15s，再次使用重置计时器) |
| 再次潜行+右键同一路径起始机器 | **关闭**路径显示 |

**激活颜色提示：** 开启路径显示时，物品会额外发送一行颜色说明消息：
```
● 白色=共享段 ● 淡蓝=电容 ● 橙●粉=用电器
```

#### 8.4.2 管理指令 `/sf multimeter`

| 语法 | 效果 |
|------|------|
| `/sf multimeter` | 看向机器，显示电网设备信息 |
| `/sf multimeter -p` | 看向机器，显示信息 + 切换路径显示/连接器负载 |

需要权限 `slimefun.command.multimeter`（`plugin.yml` 当前默认 `true`）

#### 8.4.3 粒子颜色方案

粒子颜色根据**目的地类型**赋予语义含义：

| 段类型 | 颜色 | 说明 |
|--------|------|------|
| 共享段 | `⬜ 白色` | 多条路径共用的线段 |
| 电容充电路段 | `🟦 蓝色/淡蓝/深蓝/紫蓝` | 目的地为电容的路线，多个电容时按顺序分配蓝色系变体 |
| 用电器供电路段 | `🟥🟧🟨🟪 红/橙/黄/粉` | 目的地为用电器的路线，多个用电器时按顺序分配暖色系变体 |
| 粒子类型 | `DUST` (彩色) | 使用 `Particle.DustOptions` 控制颜色和大小(1.5F) |

**分配逻辑 (`assignConsumerColors`)：** 遍历所有路径，判断 `consumer` 是否属于 `net.getCapacitors()`；是则从 `CAPACITOR_COLORS[]`（蓝色系4色）分配，否则从 `CONSUMER_COLORS[]`（暖色系4色）分配。

#### 8.4.4 显示管理机制 — `MultimeterDisplayManager`

| 组件 | 类 | 说明 |
|------|---|------|
| 路径显示 | `PathDisplay` (内部类) | 粒子闪烁(每秒刷新) + 负载全息(实时更新) + **端点储能全息(每秒实时刷新)**，15s自动清除 |
| 连接器负载 | `ConnectorDisplay` (内部类) | 仅刻负载全息+倒计时，15s清除，再点重置计时器 |
| 生命周期 | `PlayerQuitEvent` | 玩家退出自动清理所有显示 |
| 范围控制 | `hasNearbyPlayer()` | 粒子仅在有玩家在32格内时生成（全息不受此限制） |

#### 8.4.5 显示结束时的清理

- 15s倒计时到 0 时：粒子任务取消 + 全息删除 + 从显示映射中移除
- 玩家退出服务器：通过 `PlayerQuitEvent` 清理该玩家的所有显示
- 服务器重启：全息服务在内存中，重启后自然清除

#### 8.4.6 合成配方

```
铜锭  -   铜锭
 -  红石合金  -
 -   6K金  -
```
增强工作台合成

研究ID：`176`，名称：`Power Measurement`（能量测量），等级：10

### 8.5 连接器老化/损坏机制

#### 8.5.1 核心机制

每个连接器拥有浮点耐久（0.0 ~ 1.0，初始 1.0），重启不丢失（通过 `blockData.setData()` 持久化到数据库）。

每 Slimefun Tick（默认每 10 server tick，即 2 次/秒）如果连接器参与了能量传输，根据负载计算老化概率，判定成功则扣除 0.01% 耐久。

耐久降至 0% 时：标记为损坏 → 触发 `EnergyNet.markDirty()` 断开连接 → 显示 "§c连接器损坏" 全息。

#### 8.5.2 各连接器参数

| 连接器 | 甜点功率 | 最大功率 | 峰值功率 | 预期寿命(tick) | 预期总传输(J) | 修复物品 |
|-------|:--------:|:--------:|:--------:|:-------------:|:------------:|---------|
| 简易能源连接器 | 12 J/t | 40 J/t | 75 J/t | 72,000 | ~436K J | 从合成表随机 |
| 大功率能源连接器 | 36 J/t | 100 J/t | 200 J/t | 144,000 | ~2.62M J | 从合成表随机 |
| 能源连接器 | 24 J/t | 72 J/t | 160 J/t | 576,000 | ~6.98M J | 从合成表随机 |
| 镶金能源连接器 | 100 J/t | 300 J/t | 500 J/t | 3,456,000 | ~174.6M J | 从合成表随机 |
| 强化能源连接器 | 300 J/t | 750 J/t | 1,200 J/t | 6,912,000 | ~1.05B J | 从合成表随机 |
| 黑钻能源连接器 | 512 J/t | 2,000 J/t | 8,000 J/t | 27,648,000 | ~7.15B J | 从合成表随机 |
| 长途连接器 | 512 J/t | 2,000 J/t | 8,000 J/t | 27,648,000 | ~7.15B J | 从合成表随机 |
| 短路连接器 | 512 J/t | 2,000 J/t | 8,000 J/t | 27,648,000 | ~7.15B J | 淡灰色混凝土占位配方 |

> 预期总传输 = sweetPower × 有效寿命（含老化加速数值积分），在 sweet 功率下持续运行时的理论值。

#### 8.5.3 老化概率公式

```
totalProb = baseProb × loadFactor × ageFactor
```

- **baseProb** = 10000 / expectedLifetime（每 tick 在 maxPower+全新 下的命中概率）
- **loadFactor**：三段有理函数，完全连续：
  - **0 ≤ P ≤ sweetPower**: `x = P/sweet`, `lf = x / (0.9 + 0.1x)`
    - P=0 → lf=0（不老化）；P=sweet → lf=1.0
  - **sweetPower < P ≤ maxPower**: `x = (P-sweet)/(max-sweet)×3 + 1`, `lf = x / (1.2 - 0.2x)`
    - P=sweet(x=1) → lf=1.0；P=max(x=4) → lf=10.0
  - **maxPower < P ≤ peakPower**: `x = (P-max)/(peak-max)×4 + 4`, `lf = x / (0.79 - 0.0975x)`
    - P=max(x=4) → lf=10.0；P=peak(x=8) → lf=800.0
  - **P > peakPower**: 不走概率，走直接过载扣减
- **ageFactor**：分段线性插值，10段：
  - 1.0~0.95 → 1.0；0.95~0.9 → 1.1→1.0；...
  - ...0.2~0.0 → 5.0→4.4

#### 8.5.4 关键点 loadFactor 值

| P | loadFactor | 预期寿命(刻)与sweet处比值 | 预期传输量与sweet处比值 |
|---|-----------|------------------------|---------------------|
| 0 | 0 | ∞(不老化) | 0 |
| sweetPower | **1.0** | 1x (基准) | **1x (峰值)** |
| midway(sweet~max) | ~3.4 | 0.29x | 0.43x |
| maxPower | **10.0** | 0.1x | 0.33x |
| midway(max~peak) | ~145 | 0.0069x | 0.012x |
| peakPower | **800.0** | 0.00125x | 0.00083x |

**T(P) = P/loadFactor 严格单调递减**（从 sweet 到 peak 持续下降）。

#### 8.5.5 过载惩罚

当 load > peakPower 时：
- 不经过概率判定，直接扣除 `0.5% × (load/peakPower)` 耐久/tick
- 产生烟雾 + 橙色电火花粒子
- 连续过载超过 `当前耐久 × 60秒` 强制归零（动态阈值，例如满耐久允许 60s，半耐久只允许 30s）
- 过载计数器在负载恢复正常时自动归零；修复完成时也归零

#### 8.5.6 耐久状态等级

| 耐久范围 | 状态 | 显示颜色 | 表现 |
|---------|------|---------|------|
| >80% | 健康 | 🟢 &a | 满性能 |
| 50%~80% | 正常 | 🟡 &e | 正常 |
| 30%~50% | 磨损 | 🟠 &6 | 性能下降 |
| 15%~30% | 老化 | 🔴 &c | 明显衰减 |
| 0%~15% | 预计故障 | ⚫ &4 | 濒临损坏 |
| 0% | 已损坏 | ❌ &c | 断开连接 |

#### 8.5.7 右键修复机制

右键连接器时显示耐久的修复信息。修复材料从合成配方中随机抽取，当前实现允许重复抽到同一种材料，逐一提交：

- 已损坏(0%)：需要提交 **4 个材料**
- 耐久 ≤33%：需要提交 **3 个材料**
- 耐久 ≤66%：需要提交 **2 个材料**
- 耐久 <100%：需要提交 **1 个材料**

手持匹配的物品右键点击即可逐个提交，进度实时显示。

#### 8.5.8 过载连续时间（耐久动态阈值）

```java
int maxTicks = (int) (newDura * 60.0f * 20 / TICK_DELAY);
```

当前耐久对应的允许过载时长：

| 当前耐久 | 允许过载时长 |
|---------|-------------|
| 100% | 60 秒 |
| 80% | 48 秒 |
| 50% | 30 秒 |
| 25% | 15 秒 |
| 10% | 6 秒 |

#### 8.5.9 显示集成

- **连接器右键**：仅在需要修复时显示修复材料列表和当前进度；连接状态、范围、耐久百分比和剩余吞吐由万用表显示
- **万用表**：点击连接器显示 `耐久: XX.X% (状态)` + `剩余吞吐: X.X J`
- **损坏全息**：红色 "§c连接器损坏"

#### 8.5.10 实现类

- `ConnectorAgingManager` — 核心管理类（`core/networks/energy/`），含配置注册、概率计算、数据存储、修复逻辑
- `EnergyNet.performEnergyTransfer()` 末尾调用 `ConnectorAgingManager.processAging(this)`
- `EnergyConnector.BlockUseHandler` / `LongRangeConnector.BlockUseHandler` 显示耐久信息并处理右键修复

#### 8.5.11 预计算预期寿命

在 `ConnectorConfig` 构造函数中一次性数值积分（`computeExpectedLifetimeJoules`）计算含老化加速的总预期传输量，用于 `getRemainingJoules()` 和后续 lore 显示。

---

## 9. 调试系统

### 9.1 日志级别控制

| 常量 | 默认值 | 作用 | 建议场景 |
|------|:------:|------|---------|
| `DEBUG` | `false` | 完整调试日志（含初始化进度、能量传输细节） | 调试能量分配/扣减异常 |
| `DEBUG_PATHS` | `false` | BFS路由日志（路径数、跳数、成员数量） | 默认关闭，需要排查路径时临时开启 |
| `MAX_BFS_DEBUG_LOG` | `200` | 每条BFS细节日志最多打印次数 | 防刷屏，需要时调大 |

所有 `DEBUG_PATHS = true` 的日志输出到控制台格式为 `[EnergyNet-DEBUG]`，同时向 OP / 有 `slimefun.debug` 权限的在线玩家发送彩色消息；默认值当前为 `false`。

### 9.2 三个日志函数

| 函数 | 条件 | 说明 |
|------|------|------|
| `debugLog(msg)` | `DEBUG=true` | 完整调试信息，**不计数** |
| `debugPathLog(msg)` | `DEBUG_PATHS=true` | BFS路由信息，**不计数** |
| `debugPathLogLimited(msg)` | `DEBUG_PATHS=true` + 计数<MAX | **计数版**，每条日志仅打印前N次 |

`debugPathLogLimited` 内部使用 `AtomicInteger bfsDebugLogCount` 做计数器，每次 `precomputePaths()` 开始时自动重置（依靠新的电网初始化）。

### 9.3 BFS 关键日志点（用于追踪路径问题）

以下日志点分布在BFS循环中，专门用于定位"消费者从未被BFS处理"类问题：

**日志点A — BFS主循环消费者检查**（约L1058-1077）：
```
BFS步骤: 检查消费者 current=worlds (x,y,z) consumers含=true 是源=false
BFS步骤: 找到消费者 worlds (x,y,z) length=N
```
- `consumers含=false` → 该位置不在 `consumers` 映射中，不会被记录为用电设备
- `是源=true` → 源与消费者同一位置（不合理，需要检查成员收集阶段）

**日志点B — 邻居迭代消费者追踪**（约L1126-1155）：
```
BFS邻居: 消费者=worlds (x,y,z) 当前=worlds (x',y',z') bfsVisited.contains=true/false
BFS邻居: 消费者已被访问，跳过! loc=worlds (x,y,z)
BFS邻居: 消费者通过bfsVisited.add，即将加入nodes! loc=worlds (x,y,z)
BFS邻居: 消费者已加入nodes! loc=worlds (x,y,z) nodes.size=N head=H
```
- `bfsVisited.contains=true` → 消费者之前已被其他路径访问，**不会再次入队**
- `已被访问，跳过` → 同前，消费者从 `getNeighbors()` 返回但已在visited中
- `已加入nodes` → 消费者成功进入BFS队列。`nodes.size > head` 意味着后续会被处理

**日志点C — 调节器邻居消费者扩张**（约L1281-1295）：
```
addRegulatorNeighbors: 消费者=worlds (x,y,z) inRange=true/false regRange=6 距离=D added=true/false
```
- `inRange=false` → 调节器到该用电器的距离超过6格，不会通过调节器路由
- `added=false` 且 `inRange=true` → 消费者之前已被标记visited（过早访问）
- `added=true` → 消费者通过调节器加入BFS队列

**日志点D — 调节器处理**（约L1106-1113）：
```
BFS: 处理调节器 length=N
```

### 9.4 BFS完成摘要

每次 `findShortestPathsFromSource()` 结束时打印（约L1141-1158）：
```
BFS: 源=worlds (x,y,z) 类型=GENERATOR 找到路径数=N 路径: ... (L=跳数)
```
- `找到路径数=0` → 源无法到达任何用电器
- 路径列表包含每条路径的目标和跳数

### 9.5 使用流程

要排查BFS路径问题，推荐步骤：

1. **临时开启 `DEBUG_PATHS=true`**（默认关闭）
2. **重现问题**（放置/拆除机器触发电网重新初始化）
3. **查看控制台日志**，搜索关键词：
   - 先看 `precomputePaths:` 行 — 确认sources/consumers数量
   - 看 `BFS: 源=... 找到路径数=N` — 确认源是否找到路径
   - 如果路径数=0，看 `BFS步骤: 检查消费者` 行 — 确认消费者是否被BFS处理到
   - 如果从未出现消费者行，看 `BFS邻居: 消费者` 行 — 追踪消费者入队路径
4. **如需完整能量传输细节**，临时开启 `DEBUG=true`（注意日志量大）

---

## 10. 诊断指南

### 10.1 常见问题速查表

| 症状 | 可能原因 | 排查日志关键词 | 修复方向 |
|------|---------|--------------|---------|
| 用电器不工作，电网显示`+N J ⚡`（供>需） | 用电器未被识别为成员 | `consumers含=false` | 检查用电器是否在连接器/调节器范围内 |
| 用电器不工作，电容有电但不用 | 电容→用电器路径未找到 | `电容路径计算完成, capacitorPaths=0` | 检查电容是否通过连接器与用电器连通 |
| 发电机能找到用电器但没电过去 | 能量传输阶段问题 | 开启`DEBUG=true`看`transferFromGenerators` | 检查发电机isChargeable和getCharge |
| 电网卡在初始化 | 成员收集阶段死循环或超慢 | `初始化电网中 N%` 进度不动 | `MAX_BFS_NODES` 限制或 `abortRequested` |
| 所有设备显示电网冲突 `&c电网冲突：*` | 两个调节器范围重叠 | `conflictMode` | 调整调节器间距 >12格 |
| `BFS: 源=... 找到路径数=0` | 源无法连通任何用电器 | `BFS邻居: 消费者=... added=true/false` | 检查连接器链条是否完整 |
| `BFS邻居: 消费者已被访问，跳过` | 消费者在到达当前节点前已被其他路径发现 | 看谁的出边先到达该消费者 | 正常行为，多条路径竞争 |
| 初始化正常但能量传输微乎其微 | 可储电发电机电荷同步问题 | `perGeneratorNewCharge` | 参考发电机的setCharge调用栈 |

### 10.2 成员收集阶段异常

如果电网未正确发现某台机器：
- 调节器6轴向扩展（RANGE=6）是机器进入电网的第一道关卡
- 机器必须首先在调节器6格轴向范围内，或者在某连接器/电容的范围内
- `collectNetworkMembers:` 开头的日志显示搜索起点
- 如果机器确实在范围内但仍未被发现，检查 `StorageCacheUtils` 的DB缓存状态

### 10.3 BFS路径全零排查

如果 `BFS: 源=... 找到路径数=0`，按以下链条排查：

```
① getNeighbors() 返回的邻居是否包含消费者？
   → 看日志: getNeighbors(CONNECTOR): 检查用电器 ... 在范围内=true getComponent=CONSUMER validateConnection结果=true
   → 如果不在范围: 检查连接器range和距离
   → 如果validateConnection=false: 检查验证规则

② 消费者是否通过bfsVisited.add()？
   → 看日志: BFS邻居: 消费者通过bfsVisited.add，即将加入nodes!
   → 如果"已被访问，跳过": 之前的节点已经访问过该消费者

③ 消费者加入nodes后是否被BFS主循环处理？
   → 看日志: BFS步骤: 检查消费者 current=consumerLoc consumers含=...
   → 如果nodes有消费者但head已超过其索引: BFS在消费者之前提前退出
   → 检查 MAX_BFS_NODES 限制和 abortRequested

④ 如果以上全通过，看路径结束摘要
   → shortestPaths.size() 应该 >0
```

### 10.4 链路完全排查（从发电机到用电器）

1. **发电机侧**：确认发电机在 `generators` 映射中
   - 看日志 `precomputePaths: sources=N consumers=M`
2. **连接器链路**：确认连接器之间互相连通
   - 看日志 `getNeighbors(CONNECTOR): 检查连接器 ... 结果=true`
3. **用电器侧**：确认用电器在连接器范围内
   - 看日志 `getNeighbors(CONNECTOR): 检查用电器 ... 在范围内=true`
4. **BFS完整路径**：确认整条链路被BFS发现
   - 看摘要 `BFS: 源=... 找到路径数=N`

---

## 11. 货运网络电力集成

### 11.1 概述

货运管理器（CargoManager）现已接入能源电网，作为电网的 CONSUMER（用电器）运行。货运网络每次执行物品传输前需消耗电力，电力不足时传输任务被跳过。

### 11.2 可变容量

货运管理器具有**位置感知的可变容量**，通过 `EnergyNetComponent.getChargeCapacityLong(Location)` 接口支持：

- **基础容量**：128 J（出厂默认）
- **每输入节点加成**：+4 J/个
- **上限**：10,000 J
- **公式**：`capacity = min(10000, 128 + 4 × inputNodeCount)`

输入节点计数通过 `CargoNet.onClassificationChange()` 自动追踪，在方块数据中以 `cargo-input-count` 持久化。

> **容量变化无需触发电网重新初始化**，EnergyNet 在每次计算需求时调用 `getChargeCapacityLong(loc)` 获取最新值。

### 11.3 电力消耗计算

每次 Slimefun Tick，货运网络在执行物品传输前计算所需电力：

```
powerNeeded = 16(信道) × 6J + inputNodeCount × 2J
```

- **16 × 6J = 96J**：固定部分，16个信道每个信道的传输尝试开销
- **inputNodeCount × 2J**：每个输入节点尝试传输时的开销
- 例如 4 个输入节点：`96 + 4×2 = 104J`

### 11.4 执行流程

```
CargoNet.tick()
├── super.tick() → discoverStep()
├── 检查是否已连接
├── mapInputNodes() / mapOutputNodes()
├── calculatePowerNeeded(inputCount)
├── readCharge() → 从 regulator (货运管理器) 读取 energy-charge
│
├── if (charge < powerNeeded):
│   └── 更新悬浮字 "&c电力不足: 需要 N J, 当前 M J"
│   └── return (跳过本次传输)
│
├── deductCharge(powerNeeded) → 扣除电力
│
└── 继续原有传输逻辑 (CargoNetworkTask)
```

### 11.5 电力不足时的行为

- 货运管理器悬浮字显示当前电力不足信息
- 物品传输被跳过（不执行 CargoNetworkTask）
- 电力恢复（有足够存电）后自动恢复正常传输
- **不接入机器损坏机制**

### 11.6 实现相关文件

| 文件 | 改动 |
|------|------|
| `EnergyNetComponent.java` | 新增 `getChargeCapacityLong(Location)` 默认方法 |
| `EnergyNet.java` | 4处 `getCapacityLong()` → `getChargeCapacityLong(loc)` |
| `CargoManager.java` | 实现 `EnergyNetComponent`，`CONSUMER` 类型，可变容量，覆写 `setCharge()` |
| `CargoNet.java` | 新增 `calculatePowerNeeded()`、`readCharge()`、`deductCharge()`、`updateCargoManagerInputCount()` |

---

## 12. 已知限制与未来改进

| 类别 | 限制 | 说明 |
|------|------|------|
| 路径算法 | BFS 不反查 CONSUMER→controller 链路 | 用电器不产生出边，仅作为路径终点 |
| 能量守恒 | 扣减使用 double fraction | 小电量时可能产生舍入误差（<1J） |
| 电容桥接 | `processCapacitor` 仅6方向 | 主动搜索时保守；BFS路径计算中已支持桥接跳数计数 |
| 并发 | 异步初始化的 BFS 在后台线程 | 对 block 的读操作为 `StorageCacheUtils` 缓存，写操作仅在主线程 tick |
| 跨世界 | 支持通过 `getDistance` 计算 | 显示时 `formatLocation` 包含世界名 |
| 日志量 | `DEBUG_PATHS=true` 时BFS日志较多 | `MAX_BFS_DEBUG_LOG` 限制每条日志打印次数，不影响关键路径摘要 |

---

### 电量计数器（EnergyMeter）
- 物品ID: `ENERGY_METER`
- 材质: `Material.DAYLIGHT_DETECTOR`
- 功能：统计经过连接器的累计电量（负载）
- 位置：连接器正上方一格
- 数据存储：使用 `SlimefunBlockData` 持久化（`energy-counter` key），重启不丢失
- 全息显示：自动单位转换（K/M/B/T/Q），带闪烁抑制（displayCache 仅变化时更新）
- 右键：显示精确的电量数值
- Shift+右键（仅放置者）：清零计数器
- 放置者记录：`energy-meter-owner` key 记录 UUID
- 无挖掘保护（任何玩家都可挖掘）
- 实现原理：
  1. `EnergyNet.tick()` 中 `performEnergyTransfer()` 后调用 `propagateToEnergyMeters()`
  2. 遍历所有连接器负载，检查正上方是否为 ENERGY_METER
  3. 使用 `NumberUtils.flowSafeAddition(long, long)` 安全加法避免溢出
  4. 结果通过 `data.setData()` 写回持久化存储
- 独立 tick（BlockTicker）：每秒更新全息显示

### markDirty 主线程短路

[EnergyNet:L2469](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L2469)
`markDirty()` 中的 `runSync` 已优化为**主线程短路**：
```java
if (Bukkit.isPrimaryThread()) {
    hologramCleanup.run();  // 已在主线程，直接执行
} else {
    Slimefun.runSync(hologramCleanup);
}
```
当从 `onMachinePlaced`（BlockPlaceEvent → 主线程）调用时，省去一次 runSync 调度。

---

## 附录A：关键方法调用图

```
tick()
├── initializeNetworkAsync()   (异步，仅首次)
│   ├── clearNetworkData()
│   ├── collectNetworkMembers()
│   │   ├── processConnector()
│   │   └── processCapacitor()
│   ├── precomputePaths()
│   └── 初始化完成 → scheduleSelfTick() → 更新全息(等待首个tick)
└── [显示层] 全息刷新（无电力逻辑）← 电力逻辑由 tickSelfMainThread() 接管

tickSelfMainThread() (BukkitScheduler 主线程自调度，三阶段异步)
├── Phase 1 [主线程]:
│   ├── tickAllGenerators()       → 记录 totalProducedThisTick
│   ├── tickAllCapacitors()
│   └── collectTickSnapshot()     → 冻结状态快照
├── Phase 2 [GRID_TICK_EXECUTOR 异步]:
│   └── computeTransfers(snapshot) → 纯数学计算 → TransferResult
└── Phase 3 [runSync 主线程]:
    └── applyTransferResult(result)
        ├── 写回 gen/cap/con charge deltas
        ├── storeRemainingEnergy() → 多余电力存电容
        ├── reduceStoredChargeableEnergy() → 能量克隆防护
        ├── propagateToEnergyMeters() → 电量计数器
        ├── ConnectorAgingManager.processAging() → 连接器老化
        ├── 统计计算 (totalConsumedThisTick, lastSupply, lastDemand)
        └── 全息刷新 (regulator区块加载 → 更新 / 未加载 → 跳过)

MultimeterDisplayManager (独立于tick)
├── PathDisplay.start()
│   ├── spawnParticles()        (每秒刷新粒子)
│   ├── createHolograms()       (初始全息创建)
│   └── updateHolograms()       (每秒刷新负载)
│   └── 15s超时 → cancel() → removeHolograms()
├── ConnectorDisplay.start()
│   ├── updateHologram()        (每秒刷新)
│   └── 15s超时 → cancel()
└── onPlayerQuit()
    └── cleanupPlayer()         (清理所有显示)
```

---

## 附录B：自定义 BFS 数据结构

### BFSNode [L1303](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L1303)

```java
class BFSNode {
    Location location;
    int parentIndex; // nodes列表中的父节点索引，-1=根节点
    int length;      // 到源节点的跳数（连接器+调节器数量）
}
```

### EnergyPath [L2361](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L2361) — `public` 类

```java
public static class EnergyPath {
    Location source;          // 发电机或电容 → getSource()
    Location consumer;        // 用电器（或电容）→ getConsumer()
    List<Location> connectors; // 路径上的连接器+调节器 → getConnectors()
    int length;               // = connectors.size() → getLength()
}
```

万用表和 `/sf multimeter` 指令通过 `EnergyNet.getGeneratorPaths()`、`getCapacitorPaths()`、`getGeneratorToCapacitorPaths()` 公开 getter 获取路径数据。

---

## 12. 自调度架构 (Self-Tick) — v2.6 三阶段异步

### 12.1 动机

电网的 `tick()` 原本由 BlockTicker 驱动，BlockTicker 依赖 `chunk.isLoaded()`。LongRangeConnector（范围128格）超远跨区电网在调节器区块卸载时完全停摆。同时，原方案使用 `runTaskTimerAsynchronously` 在异步线程直接访问 Bukkit/Storage API，存在线程安全问题。

### 12.2 设计

```
旧架构：BlockTicker → chunk.isLoaded()? → tick() → 电力逻辑+显示
中间架构：BukkitScheduler → runTaskTimerAsynchronously → tickSelf() (异步+不安全)
新架构：BukkitScheduler → runTaskTimer (主线程)
         → tickSelfMainThread()
            ├── Phase 1 [主线程]: tickAllGenerators + tickAllCapacitors + collectTickSnapshot
            ├── Phase 2 [异步]:   GRID_TICK_EXECUTOR → computeTransfers(snapshot)
            └── Phase 3 [主线程]: runSync → applyTransferResult(result, snapshot)
```

- `scheduleSelfTick()` 使用 `runTaskTimer` (主线程调度)，不再使用 `runTaskTimerAsynchronously`
- `tickSelfMainThread()` 以 `AtomicBoolean` 防重入
- Phase 1 在主线程执行 I/O，Phase 2 异步纯数学，Phase 3 主线程写回
- 启动时机：`initializeNetworkAsync()` 成功后调用 `scheduleSelfTick()`
- 停止时机：`markDirty(regulator)` → `cancelSelfTick()`
- 自愈机制：`initializeNetworkAsync.finally` 中自动重试

### 12.3 markDirty 直接提交

`markDirty` 不再只"标记脏了等 tick"，而是在设置 `initialized=false` 后直接调用 `GRID_EXECUTOR.submit(this::initializeNetworkAsync)`。这样即使调节器区块未加载，网络也能立即开始重新初始化。

### 12.4 全息刷新

`applyTransferResult()` (Phase 3) 中通过 `regulator.getChunk().isLoaded()` 判断：
- 加载 → `updateHologram(data, lastSupply, lastDemand)`
- 未加载 → 跳过（不报错，不影响电力逻辑）

BlockTicker 的 `tick()` 作为显示层的补充，在区块加载时额外刷新全息。

---

## 13. 限电器 (CurrentLimiter)

### 13.1 动机

在复杂电网中，管理员可对特定连接器设置流量上限，防止某条线路承载过多电力。限电器放在连接器正上方（与电量计数器互斥），限制该连接器每 tick 的最大通过电量。

### 13.2 设计

```
连接器 y+1 位置:
  电量计数器 (ENERGY_METER) ↔ 限电器 (CURRENT_LIMITER)  二选一
```

- 存储键 `current-limit` (String long)，持久化到数据库
- `connectorLimits` (ConcurrentHashMap) 电网级数据，由 BFS 初始化填充，运行中可实时修改
- `computeLimiterCap(pathGroup)` 在传输前检查路径上所有连接器的剩余额度
- 注入点：`transferFromGenerators` / `transferFromCapacitors`

### 13.3 交互

| 交互 | 行为 |
|------|------|
| 右键（放置者） | 提示在聊天栏输入整数值 (J/t) |
| 输入 0 | 连接器禁用 |
| 输入正整数 | 限制通过量 |
| 输入非法值 | 拒绝 |
| Shift+右键 | 快捷设为无限制 |
| 非放置者右键 | 仅查看当前限制值 |

### 13.4 限流逻辑

`transferAmount = min(transferAmount, computeLimiterCap(pathGroup))`

- 仅对当前 tick 有效，`connectorLoad` 每 tick 清零
- 超出限额的电留在源端不传输
- 无备用路径时用电器收不到足额电力

### 13.5 生命周期

| 事件 | BFS 重扫？ |
|------|:---:|
| 初次初始化 | ✅ |
| 限电器值变更 | ❌ 实时 |
| 拆限电器 | ❌ 实时 (BlockBreakHandler) |
| 服务器重启 | ✅ 从 DB 恢复 |
