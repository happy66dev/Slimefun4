# EnergyNet 能源电网技术实现文档

> 最后更新：2026-05-10 | 版本：v2.2（新增长途连接器）

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
    ├── DEBUG_PATHS = true (BFS路由日志开关)
    └── MAX_BFS_DEBUG_LOG = 200  (每条BFS细节日志最多打印次数)
```

---

## 2. 组件类型与术语

| 术语 | EnergyNetComponentType | NetworkComponent | 说明 |
|------|:---:|:---:|------|
| 发电机 | `GENERATOR` | `TERMINUS` | 发电设备。分可储电（煤机）和不可储电（太阳能） |
| 用电器 | `CONSUMER` | `TERMINUS` | 耗电设备，**不作为 BFS 发送方**（无出边） |
| 电容 | `CAPACITOR` | `CONNECTOR` | 储能设备。电容→电容仅6方向相邻，电容↔连接器/调节器6方向相邻 |
| 连接器 | `CONNECTOR` | `CONNECTOR` | 明文连接器。有 `range` 属性控制轴向覆盖距离 |
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
```

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
- 发现调节器（冲突检测）→ `conflictMode`

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
| GENERATOR | 调节器 + 所有连接器（`validateConnection`） |
| CONSUMER | **无出边**（不能作为发送方） |
| CAPACITOR | 相邻电容(曼哈顿=1) + 调节器(26邻居) + 连接器(26邻居+`validateConnection`) |
| CONNECTOR(普通) | 其他连接器 + 发电机 + 调节器 + 用电器 + 电容（**全部使用轴向范围检查** `isWithinRangeAxial`） |
| CONNECTOR(长途) | **仅其他连接器**（不连接发电机、调节器、用电器、电容） |

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

## 6. 电力传输流程

### 6.1 `performEnergyTransfer()` [L1667](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L1667)

```
tickAllGenerators() → 发电机产电，追踪 netNewEnergy + perGeneratorNewCharge
tickAllCapacitors() → 排除损坏电容

calculateTotalDemand() → 所有用电器缺口总和

if generatorSupply >= totalDemand:
    transferFromGenerators(totalDemand)     → 发电机供电给用电器
    excess = totalNew - usedFromNew         → 剩余电力
    stored = storeRemainingEnergy(excess)   → 存入电容（返回实际存储量）
    按比例扣减发电机（只扣 stored 的量）      → 避免能量克隆
else:
    supplyLeft = transferFromGenerators(generatorSupply)  → 发电机供应
    remainingDemand = totalDemand - (genSupply - supplyLeft) → 正确计算剩余需求
    transferFromCapacitors(remainingDemand) → 电容补足
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
> - 当普通连接器作为发送方且目标为长途连接器时，正向验证使用长途连接器的 range(128)
> - 长途连接器在 `processConnector()` 中仅将 CONNECTOR 类型加入 BFS 队列
> - 长途连接器在 `getNeighbors(CONNECTOR)` 中仅生成到其他连接器的边

### 7.2 `validateConnection()` [L876](file:///d:/Users/Administrator/Desktop/Java项目/slimefun/Slimefun4-master/src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java#L876)

| 发送方→接收方 | 正向验证 | 反向验证 |
|--------------|---------|---------|
| CAPACITOR↔CAPACITOR | `isAdjacent`（直接返回） | 不需要 |
| CONNECTOR→普通CONNECTOR | `isWithinRangeAxial(发送方, 接收方, 发送方.range)` | 不需要 |
| CONNECTOR→长途CONNECTOR | `isWithinRangeAxial(发送方, 接收方, 长途.range=128)` | 不需要 |
| GENERATOR→CONNECTOR | `true`（发电机不验证） | `isWithinRangeAxial(conn, gen, conn.range)` |
| CAPACITOR→CONNECTOR | `isWithinRange(cap, conn, 1)` (26邻居) | `isWithinRangeAxial(conn, cap, conn.range)` |
| CONNECTOR↔CONNECTOR | 默认已在轴向范围内 | 不需要 |

---

## 8. 显示与指令

### 8.1 调节器悬浮字
- 初始化中：`"&e初始化电网中 N%"`
- 已就绪：`"+N J ⚡"` 或 `"-N J ⚡"`（供需差）
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

需要权限 `slimefun.command.multimeter`（默认仅 OP）

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

每 tick 如果连接器参与了能量传输，根据负载计算老化概率，判定成功则扣除 0.01% 耐久。

耐久降至 0% 时：标记为损坏 → 触发 `EnergyNet.markDirty()` 断开连接 → 显示 "§c连接器损坏" 全息。

#### 8.5.2 各连接器参数

| 连接器 | 甜点功率 | 最大功率 | 峰值功率 | 期望寿命(tick) | 总吞吐容量(J) | 修复物品 |
|-------|:--------:|:--------:|:--------:|:-------------:|:------------:|---------|
| 简易能源连接器 | 12 J/t | 40 J/t | 75 J/t | 72,000 | 2.88M J | 红石 |
| 大功率能源连接器 | 36 J/t | 100 J/t | 200 J/t | 144,000 | 14.40M J | 地狱砖 |
| 能源连接器 | 24 J/t | 72 J/t | 160 J/t | 576,000 | 41.47M J | 碳 |
| 镶金能源连接器 | 100 J/t | 300 J/t | 500 J/t | 3,456,000 | 1.04B J | 金锭 |
| 强化能源连接器 | 300 J/t | 750 J/t | 1,200 J/t | 6,912,000 | 5.18B J | 强化合金锭 |
| 黑钻能源连接器 | 512 J/t | 2,000 J/t | 8,000 J/t | 27,648,000 | 55.30B J | 黑金刚石 |

#### 8.5.3 老化概率公式

```
totalProb = baseProb × loadFactor × ageFactor
```

- **baseProb** = (100 / 0.01) / expectedLifetime（由期望寿命自动校准）
- **loadFactor**：高次幂分段函数（指数 exp=4）：
  - load ≤ sweetPower：线性 `0.2 × load/sweetPower`
  - sweet < load ≤ max：`0.2 + 0.8 × r^4`
  - max < load ≤ peak：`1 + 9 × r^4`
  - load > peak：强制过载惩罚
- **ageFactor** = `1 + (1 - durability) × 4.0`（低耐久加速老化）

#### 8.5.4 过载惩罚

当 load > peakPower 时：
- 不经过概率判定，强制扣除 `0.5% × (load/peakPower)` 耐久
- 产生烟雾 + 橙色电火花粒子
- 连续过载 10 秒直接损坏

#### 8.5.5 耐久状态等级

| 耐久范围 | 状态 | 显示颜色 | 表现 |
|---------|------|---------|------|
| >80% | 健康 | 🟢 &a | 满性能 |
| 50%~80% | 正常 | 🟡 &e | 正常 |
| 30%~50% | 磨损 | 🟠 &6 | 性能下降 |
| 15%~30% | 老化 | 🔴 &c | 明显衰减 |
| 0%~15% | 预计故障 | ⚫ &4 | 濒临损坏 |
| 0% | 已损坏 | ❌ &c | 断开连接 |

#### 8.5.6 右键修复机制

右键连接器时显示耐久的修复信息：
- 耐久 >66%：消耗 1 个修复物品修复到 100%
- 耐久 >33%：消耗 2 个修复物品
- 耐久 >0%：消耗 3 个修复物品
- 已损坏(0%)：消耗 4 个修复物品

修复物品为各连接器配方的核心材料（红石/地狱砖/碳/金锭/强化合金锭/黑金刚石）。

#### 8.5.7 显示集成

- **连接器 Lore**：甜点/最大/峰值功率 + 总传输容量
- **万用表**：点击连接器显示 `耐久: XX.X% (状态)` + `剩余吞吐: X.X J`
- **路径悬浮字**：负载后增加状态文字，如 `负载: 50 J 健康`
- **拆除**：耐久 >95% 正常掉落，≤95% 参考损坏机器拆除机制

#### 8.5.8 实现类

- `ConnectorAgingManager` — 核心管理类（`core/networks/energy/`），含配置注册、概率计算、数据存储、修复逻辑
- `EnergyNet.performEnergyTransfer()` 末尾调用 `ConnectorAgingManager.processAging(this)`
- `EnergyConnector.BlockUseHandler` 显示耐久信息并处理右键修复

---

## 9. 调试系统

### 9.1 日志级别控制

| 常量 | 默认值 | 作用 | 建议场景 |
|------|:------:|------|---------|
| `DEBUG` | `false` | 完整调试日志（含初始化进度、能量传输细节） | 调试能量分配/扣减异常 |
| `DEBUG_PATHS` | `true` | BFS路由日志（路径数、跳数、成员数量） | 默认开启，排查路径问题 |
| `MAX_BFS_DEBUG_LOG` | `200` | 每条BFS细节日志最多打印次数 | 防刷屏，需要时调大 |

所有 `DEBUG_PATHS = true` 的日志输出到控制台格式为 `[EnergyNet-DEBUG]`，同时向 OP / 有 `slimefun.debug` 权限的在线玩家发送彩色消息。

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

1. **确保 `DEBUG_PATHS=true`**（默认已开启）
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

## 11. 已知限制与未来改进

| 类别 | 限制 | 说明 |
|------|------|------|
| 路径算法 | BFS 不反查 CONSUMER→controller 链路 | 用电器不产生出边，仅作为路径终点 |
| 能量守恒 | 扣减使用 double fraction | 小电量时可能产生舍入误差（<1J） |
| 电容桥接 | `processCapacitor` 仅6方向 | 主动搜索时保守；BFS路径计算中已支持桥接跳数计数 |
| 并发 | 异步初始化的 BFS 在后台线程 | 对 block 的读操作为 `StorageCacheUtils` 缓存，写操作仅在主线程 tick |
| 跨世界 | 支持通过 `getDistance` 计算 | 显示时 `formatLocation` 包含世界名 |
| 日志量 | `DEBUG_PATHS=true` 时BFS日志较多 | `MAX_BFS_DEBUG_LOG` 限制每条日志打印次数，不影响关键路径摘要 |

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
│   │   ├── findShortestPathsFromSource()     (发电机/电容 → 用电器)
│   │   │   ├── getNeighbors()
│   │   │   ├── addRegulatorNeighbors()
│   │   │   └── extractConnectorsFromPath()
│   │   └── findShortestPathsToCapacitors()   (发电机 → 电容，仅显示)
│   └── calculateTotalSupply/Demand()
└── performEnergyTransfer()
    ├── tickAllGenerators()
    ├── tickAllCapacitors()
    ├── calculateTotalDemand()
    ├── transferFromGenerators()
    ├── storeRemainingEnergy()
    └── transferFromCapacitors()

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
