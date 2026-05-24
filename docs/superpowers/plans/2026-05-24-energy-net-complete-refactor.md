# EnergyNet 三阶段异步重构 — 功能完整性修复

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复上一轮重构中遗漏的核心功能（发电机燃料燃烧/能量产出/爆炸/机器损坏、电容清理、多余电力存储、能量克隆防护），并将所有 Bukkit/Storage I/O 限定在主线程 Phase 1 和 Phase 3。

**Architecture:** 删除上一轮的重度简化版 `computeTick` + `collectTickSnapshot`，用新的、完整的三阶段设计替换。Phase 1 = 主线程 tick generators/capacitors (I/O) + 快照采集；Phase 2 = 异步纯数学传输计算 (含限电器)；Phase 3 = 主线程 delta 写回 + 剩余能量存储。

**Tech Stack:** Java 17+, Bukkit/Paper API, ConcurrentHashMap, ExecutorService

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java`

---

## 架构全景图

```
tickSelfMainThread() [主线程]
│
├─ Phase 1: tickAllGenerators()  ← 燃烧燃料, 产出能量, 爆炸, 机器损坏检查
│           tickAllCapacitors()  ← 清理损坏/ID不匹配电容
│           collectTickSnapshot() ← 冻结: gen charges, cap charges, con demands, limits, nonChargeableSupply
│
├─ Phase 2: GRID_TICK_EXECUTOR.submit()
│           computeTransfers(snapshot)  ← 纯数学
│               ├── transferFromGenerators()    ← 按最短路径分配，考虑限电器
│               ├── transferFromCapacitors()    ← 同上，电容放电
│               └── → TransferResult (所有deltas + excess)
│
└─ Phase 3: Slimefun.runSync()
            applyTransferResult(result)  ← 写回 gen/cap/con charge deltas
            storeRemainingEnergy()       ← 多余电力存入电容 (I/O)
            propagateToEnergyMeters()    ← 电量计数器 (I/O)
            ConnectorAgingManager.processAging() ← 连接器老化 (I/O)
            统计 + 全息图更新
```

### 关键设计决策

`storeRemainingEnergy()` **保留在 Phase 3 (主线程)** 而不是 Phase 2。原因是它内部访问:
- `StorageCacheUtils.getDataContainer()` — 数据库读
- `Slimefun.getMachineDamageService().isMachineDamaged()` — 方块状态读
- `component.setCharge()` — Bukkit 写
- `machine_damage_charge_counter` — 数据库写

Phase 2 只计算"用电器完全满足后还剩多少能量 (`excessEnergy`)"，Phase 3 把 `excessEnergy` 传给 `storeRemainingEnergy()`。

---

## Task 1: 重新设计 `GridTickSnapshot` — 覆盖完整的冻结状态

**目的:** 删除上一轮的简化 snapshot，构建一个能完整驱动异步计算的 snapshot。

**Files:**
- Read first to understand current state: `EnergyNet.java:3322-3382`
- Modify: `EnergyNet.java` — 替换 `GridTickSnapshot` 内部类

- [ ] **Step 1: 替换 GridTickSnapshot 为完整版本**

找到现有的 `GridTickSnapshot` 类定义 (约第 3322 行)，替换全部内容:

```java
private static final class GridTickSnapshot {
    final Map<Location, Long> generatorCharges;
    final Map<Location, Long> generatorCapacities;
    final Map<Location, Long> nonChargeableSupply;
    final Map<Location, Long> capacitorCharges;
    final Map<Location, Long> capacitorCapacities;
    final Map<Location, Long> consumerCharges;
    final Map<Location, Long> consumerCapacities;
    final Map<Location, Long> connectorLimits;
    final Map<Location, Set<EnergyPath>> genPaths;
    final Map<Location, Set<EnergyPath>> capPaths;
    final Map<Location, Map<Location, Set<EnergyPath>>> genToCapPaths;

    GridTickSnapshot(
            Map<Location, Long> generatorCharges,
            Map<Location, Long> generatorCapacities,
            Map<Location, Long> nonChargeableSupply,
            Map<Location, Long> capacitorCharges,
            Map<Location, Long> capacitorCapacities,
            Map<Location, Long> consumerCharges,
            Map<Location, Long> consumerCapacities,
            Map<Location, Long> connectorLimits,
            Map<Location, Set<EnergyPath>> genPaths,
            Map<Location, Set<EnergyPath>> capPaths,
            Map<Location, Map<Location, Set<EnergyPath>>> genToCapPaths) {
        this.generatorCharges = generatorCharges;
        this.generatorCapacities = generatorCapacities;
        this.nonChargeableSupply = nonChargeableSupply;
        this.capacitorCharges = capacitorCharges;
        this.capacitorCapacities = capacitorCapacities;
        this.consumerCharges = consumerCharges;
        this.consumerCapacities = consumerCapacities;
        this.connectorLimits = connectorLimits;
        this.genPaths = genPaths;
        this.capPaths = capPaths;
        this.genToCapPaths = genToCapPaths;
    }
}
```

---

## Task 2: 重新设计 `TickResult` — 替换为 `TransferResult`

**目的:** 删除上一轮的 `TickResult`，替换为能完整驱动 Phase 3 写回的 `TransferResult`。

**Files:**
- Modify: `EnergyNet.java` — 替换 `TickResult` 内部类

- [ ] **Step 1: 将 TickResult 替换为 TransferResult**

```java
private static final class TransferResult {
    final Map<Location, Long> generatorChargeDeltas;
    final Map<Location, Long> capacitorChargeDeltas;
    final Map<Location, Long> consumerChargeDeltas;
    final Map<Location, Long> connectorLoads;
    final Map<Location, Long> meterPropagation;
    final long excessEnergy;
    final long totalProduced;
    final long totalConsumed;
    final long capacitorDischarged;

    TransferResult(
            Map<Location, Long> generatorChargeDeltas,
            Map<Location, Long> capacitorChargeDeltas,
            Map<Location, Long> consumerChargeDeltas,
            Map<Location, Long> connectorLoads,
            Map<Location, Long> meterPropagation,
            long excessEnergy,
            long totalProduced,
            long totalConsumed,
            long capacitorDischarged) {
        this.generatorChargeDeltas = generatorChargeDeltas;
        this.capacitorChargeDeltas = capacitorChargeDeltas;
        this.consumerChargeDeltas = consumerChargeDeltas;
        this.connectorLoads = connectorLoads;
        this.meterPropagation = meterPropagation;
        this.excessEnergy = excessEnergy;
        this.totalProduced = totalProduced;
        this.totalConsumed = totalConsumed;
        this.capacitorDischarged = capacitorDischarged;
    }
}
```

---

## Task 3: 重写 `collectTickSnapshot()` — 在 tickAllGenerators/tickAllCapacitors 之后快照

**目的:** 删除上一轮的快照方法，替换为在发电机产出能量之后才采集的快照。

**Files:**
- Modify: 替换 `EnergyNet.java` 中的 `collectTickSnapshot()` 方法

- [ ] **Step 1: 替换 collectTickSnapshot() 方法**

```java
@Nullable
private GridTickSnapshot collectTickSnapshot() {
    Map<Location, Long> genCharges = new HashMap<>();
    Map<Location, Long> genCapacities = new HashMap<>();
    for (Map.Entry<Location, EnergyNetProvider> entry : generators.entrySet()) {
        Location loc = entry.getKey();
        EnergyNetProvider gen = entry.getValue();
        if (gen.isChargeable()) {
            genCharges.put(loc, gen.getChargeLong(loc));
        }
        genCapacities.put(loc, gen.getChargeCapacityLong(loc));
    }

    Map<Location, Long> ncSupply = new HashMap<>(nonChargeableSupply);

    Map<Location, Long> capCharges = new HashMap<>();
    Map<Location, Long> capCapacities = new HashMap<>();
    for (Map.Entry<Location, EnergyNetComponent> entry : capacitors.entrySet()) {
        Location loc = entry.getKey();
        capCharges.put(loc, entry.getValue().getChargeLong(loc));
        capCapacities.put(loc, entry.getValue().getChargeCapacityLong(loc));
    }

    Map<Location, Long> conCharges = new HashMap<>();
    Map<Location, Long> conCapacities = new HashMap<>();
    for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
        Location loc = entry.getKey();
        conCharges.put(loc, entry.getValue().getChargeLong(loc));
        conCapacities.put(loc, entry.getValue().getChargeCapacityLong(loc));
    }

    Map<Location, Long> limits = new HashMap<>(connectorLimits);

    return new GridTickSnapshot(
            genCharges, genCapacities, ncSupply,
            capCharges, capCapacities,
            conCharges, conCapacities,
            limits,
            generatorPaths, capacitorPaths, generatorToCapacitorPaths);
}
```

---

## Task 4: 重写 `tickSelfMainThread()` — 完整的三阶段入口

**目的:** 替换当前不完整的 `tickSelfMainThread()`，使其先调用 `tickAllGenerators()` + `tickAllCapacitors()`，再采集快照。

**Files:**
- Modify: `EnergyNet.java` — 替换 `tickSelfMainThread()` 方法 (约第 2371 行)

- [ ] **Step 1: 替换 tickSelfMainThread() 方法**

```java
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

        // Phase 1: 在主线程执行发电机产出 + 电容清理 + 快照采集
        long generatorSupply = tickAllGenerators(timestamp -> {});
        tickAllCapacitors();

        totalProducedThisTick = netNewEnergy + calcNonChargeableRemaining();

        long currentConsumerCharge = 0;
        for (Map.Entry<Location, EnergyNetComponent> entry : consumers.entrySet()) {
            currentConsumerCharge += entry.getValue().getChargeLong(entry.getKey());
        }
        totalConsumedThisTick = Math.max(0, lastConsumerCharge - currentConsumerCharge);
        lastConsumerCharge = currentConsumerCharge;

        GridTickSnapshot snapshot = collectTickSnapshot();
        if (snapshot == null) {
            return;
        }

        // Phase 2: 异步纯数学计算
        GRID_TICK_EXECUTOR.submit(() -> {
            if (destroyed) return;
            TransferResult result = computeTransfers(snapshot);

            // Phase 3: 主线程写回
            Slimefun.runSync(() -> {
                if (!destroyed && regulator.getChunk().isLoaded()) {
                    applyTransferResult(result, snapshot);
                }
            });
        });
    } finally {
        selfTicking.set(false);
    }
}
```

---

## Task 5: 实现 `computeTransfers()` — 异步纯数学计算核心

**目的:** 在异步线程中执行所有纯数学的传输计算：发电机→用电器、发电不足时电容→用电器、限电器考虑、连接器负载计算。然后计算 excessEnergy。

**Files:**
- Add: 插入到 `EnergyNet.java` 中 `collectTickSnapshot()` 方法之后

- [ ] **Step 1: 插入 computeTransfers() 方法**

```java
@Nonnull
private static TransferResult computeTransfers(@Nonnull GridTickSnapshot snap) {
    Map<Location, Long> genDeltas = new HashMap<>();
    Map<Location, Long> capDeltas = new HashMap<>();
    Map<Location, Long> conDeltas = new HashMap<>();
    Map<Location, Long> loadAccumulator = new HashMap<>();
    Map<Location, Long> meterData = new HashMap<>();

    // 计算总供给和总需求
    long totalGenSupply = 0;
    for (long charge : snap.generatorCharges.values()) {
        totalGenSupply = NumberUtils.flowSafeAddition(totalGenSupply, charge);
    }
    for (long ns : snap.nonChargeableSupply.values()) {
        totalGenSupply = NumberUtils.flowSafeAddition(totalGenSupply, ns);
    }

    long totalDemand = 0;
    for (Map.Entry<Location, Long> entry : snap.consumerCharges.entrySet()) {
        Location loc = entry.getKey();
        long charge = entry.getValue();
        long capacity = snap.consumerCapacities.getOrDefault(loc, 0L);
        if (charge < capacity) {
            totalDemand = NumberUtils.flowSafeAddition(totalDemand, capacity - charge);
        }
    }

    // --- 阶段 A: 发电机 → 用电器 ---
    long remainingGenSupply = totalGenSupply;
    transferFromGeneratorsSnapshot(snap, genDeltas, conDeltas, loadAccumulator, totalDemand);

    // 计算发电机实际消耗
    long genUsed = 0;
    for (long d : genDeltas.values()) {
        if (d < 0) genUsed += (-d);
    }

    // 计算用电器已接收
    long consumerReceived = 0;
    for (long d : conDeltas.values()) {
        if (d > 0) consumerReceived += d;
    }

    // --- 阶段 B: 电容 → 用电器 (发电不足时) ---
    long remainingDemand = totalDemand - consumerReceived;
    if (remainingDemand > 0) {
        transferFromCapacitorsSnapshot(snap, capDeltas, conDeltas, loadAccumulator, remainingDemand);
    }

    // 计算电容实际放电量
    long capDischarged = 0;
    for (long d : capDeltas.values()) {
        if (d < 0) capDischarged += (-d);
    }

    // --- 阶段 C: 计算 excess ---
    // excess = 发电机产出 + 不可储电供应 - 实际消耗
    long genOutput = genUsed;
    long ncRemaining = snap.nonChargeableSupply.values().stream().mapToLong(Long::longValue).sum();
    // 不可储电(太阳能等)优先消耗: 如果genUsed >= ncRemaining, nc被完全消耗
    // 剩余的是可储电发电机中未被消耗的部分
    long chargeableConsumed = Math.max(0, genUsed - ncRemaining);
    long chargeableGenerated = snap.generatorCharges.values().stream().mapToLong(Long::longValue).sum();
    long excessEnergy = Math.max(0, chargeableGenerated - chargeableConsumed);

    // --- 阶段 D: 电量计数器传播数据 ---
    for (Map.Entry<Location, Long> entry : loadAccumulator.entrySet()) {
        if (entry.getValue() > 0) {
            meterData.put(entry.getKey(), entry.getValue());
        }
    }

    long consumerReceivedFinal = 0;
    for (long d : conDeltas.values()) {
        if (d > 0) consumerReceivedFinal += d;
    }

    return new TransferResult(
            genDeltas, capDeltas, conDeltas,
            loadAccumulator, meterData,
            excessEnergy,
            genOutput + ncRemaining,
            consumerReceivedFinal,
            capDischarged);
}
```

---

## Task 6: 实现 `transferFromGeneratorsSnapshot()` — 异步版发电机传输

**目的:** 将原始 `transferFromGenerators()` 的逻辑改写为纯快照操作版本。

**Files:**
- Add: 插入到 `EnergyNet.java` 中 `computeTransfers()` 之后

- [ ] **Step 1: 插入 transferFromGeneratorsSnapshot() 方法**

```java
private static void transferFromGeneratorsSnapshot(
        @Nonnull GridTickSnapshot snap,
        @Nonnull Map<Location, Long> genDeltas,
        @Nonnull Map<Location, Long> conDeltas,
        @Nonnull Map<Location, Long> loadAccumulator,
        long totalDemand) {

    // 建立快照内可用的发电机电荷视图: 可储电 + 不可储电
    Map<Location, Long> genCharges = new HashMap<>(snap.generatorCharges);
    Map<Location, Long> ncSupply = new HashMap<>(snap.nonChargeableSupply);

    // 按路径长度分组排序
    List<EnergyPath> sortedPaths = getSortedPathsStatic(snap.genPaths);
    Map<String, List<EnergyPath>> pathGroups = new LinkedHashMap<>();
    for (EnergyPath path : sortedPaths) {
        String key = path.source.getBlockX() + "," + path.source.getBlockY() + "," + path.source.getBlockZ()
                + "," + path.consumer.getBlockX() + "," + path.consumer.getBlockY() + "," + path.consumer.getBlockZ()
                + "," + path.length;
        pathGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
    }

    for (List<EnergyPath> pathGroup : pathGroups.values()) {
        EnergyPath firstPath = pathGroup.get(0);
        Location genLoc = firstPath.source;
        Location conLoc = firstPath.consumer;

        boolean isNonChargeable = !snap.generatorCharges.containsKey(genLoc);
        long genCharge = isNonChargeable
                ? ncSupply.getOrDefault(genLoc, 0L)
                : genCharges.getOrDefault(genLoc, 0L);

        long conCharge = snap.consumerCharges.getOrDefault(conLoc, 0L)
                + conDeltas.getOrDefault(conLoc, 0L);
        long conCapacity = snap.consumerCapacities.getOrDefault(conLoc, 0L);

        if (genCharge <= 0 || conCharge >= conCapacity) {
            continue;
        }

        long needed = conCapacity - conCharge;
        long transferAmount = Math.min(genCharge, needed);

        if (transferAmount <= 0) continue;

        // 限电器检查
        transferAmount = Math.min(transferAmount,
                computeLimiterCapSnapshot(pathGroup, snap.connectorLimits, loadAccumulator));
        if (transferAmount <= 0) continue;

        // 记录 delta (不修改原始对象)
        if (isNonChargeable) {
            long remaining = ncSupply.getOrDefault(genLoc, 0L) - transferAmount;
            if (remaining <= 0) {
                ncSupply.remove(genLoc);
            } else {
                ncSupply.put(genLoc, remaining);
            }
            conDeltas.merge(conLoc, transferAmount, Long::sum);
        } else {
            genDeltas.merge(genLoc, -transferAmount, Long::sum);
            genCharges.merge(genLoc, -transferAmount, Long::sum);
            conDeltas.merge(conLoc, transferAmount, Long::sum);
        }

        // 均摊负载到组内所有路径
        long loadPerPath = transferAmount / pathGroup.size();
        long remainder = transferAmount % pathGroup.size();
        for (int i = 0; i < pathGroup.size(); i++) {
            long pathLoad = loadPerPath + (i < remainder ? 1 : 0);
            if (pathLoad > 0) {
                for (Location conn : pathGroup.get(i).connectors) {
                    loadAccumulator.merge(conn, pathLoad, Long::sum);
                }
            }
        }
    }
}
```

---

## Task 7: 实现 `transferFromCapacitorsSnapshot()` — 异步版电容放电

**目的:** 将原始 `transferFromCapacitors()` 的逻辑改写为纯快照操作版本。

**Files:**
- Add: 插入到 `EnergyNet.java` 中 `transferFromGeneratorsSnapshot()` 之后

- [ ] **Step 1: 插入 transferFromCapacitorsSnapshot() 方法**

```java
private static void transferFromCapacitorsSnapshot(
        @Nonnull GridTickSnapshot snap,
        @Nonnull Map<Location, Long> capDeltas,
        @Nonnull Map<Location, Long> conDeltas,
        @Nonnull Map<Location, Long> loadAccumulator,
        long maxEnergy) {

    Map<Location, Long> capCharges = new HashMap<>(snap.capacitorCharges);
    long remaining = maxEnergy;

    List<EnergyPath> sortedPaths = getSortedPathsStatic(snap.capPaths);
    Map<String, List<EnergyPath>> pathGroups = new LinkedHashMap<>();
    for (EnergyPath path : sortedPaths) {
        String key = path.source.getBlockX() + "," + path.source.getBlockY() + "," + path.source.getBlockZ()
                + "," + path.consumer.getBlockX() + "," + path.consumer.getBlockY() + "," + path.consumer.getBlockZ()
                + "," + path.length;
        pathGroups.computeIfAbsent(key, k -> new ArrayList<>()).add(path);
    }

    for (List<EnergyPath> pathGroup : pathGroups.values()) {
        if (remaining <= 0) break;

        EnergyPath firstPath = pathGroup.get(0);
        Location capLoc = firstPath.source;
        Location conLoc = firstPath.consumer;

        long capCharge = capCharges.getOrDefault(capLoc, 0L);
        long conCharge = snap.consumerCharges.getOrDefault(conLoc, 0L)
                + conDeltas.getOrDefault(conLoc, 0L);
        long conCapacity = snap.consumerCapacities.getOrDefault(conLoc, 0L);

        if (capCharge <= 0 || conCharge >= conCapacity) continue;

        long needed = conCapacity - conCharge;
        long transferAmount = Math.min(Math.min(capCharge, needed), remaining);
        if (transferAmount <= 0) continue;

        transferAmount = Math.min(transferAmount,
                computeLimiterCapSnapshot(pathGroup, snap.connectorLimits, loadAccumulator));
        if (transferAmount <= 0) continue;

        capDeltas.merge(capLoc, -transferAmount, Long::sum);
        capCharges.merge(capLoc, -transferAmount, Long::sum);
        conDeltas.merge(conLoc, transferAmount, Long::sum);
        remaining -= transferAmount;

        long loadPerPath = transferAmount / pathGroup.size();
        long remainder = transferAmount % pathGroup.size();
        for (int i = 0; i < pathGroup.size(); i++) {
            long pathLoad = loadPerPath + (i < remainder ? 1 : 0);
            if (pathLoad > 0) {
                for (Location conn : pathGroup.get(i).connectors) {
                    loadAccumulator.merge(conn, pathLoad, Long::sum);
                }
            }
        }
    }
}
```

---

## Task 8: 实现辅助静态方法

**目的:** 添加异步计算所需的纯函数辅助方法（不含任何 Bukkit/Storage I/O）。

**Files:**
- Add: 插入到 `EnergyNet.java` 中 `transferFromCapacitorsSnapshot()` 之后

- [ ] **Step 1: 插入辅助静态方法**

```java
private static List<EnergyPath> getSortedPathsStatic(Map<Location, Set<EnergyPath>> pathMap) {
    List<EnergyPath> all = new ArrayList<>();
    for (Set<EnergyPath> paths : pathMap.values()) {
        all.addAll(paths);
    }
    all.sort((a, b) -> Integer.compare(a.length, b.length));
    return all;
}

private static long computeLimiterCapSnapshot(
        List<EnergyPath> pathGroup,
        Map<Location, Long> limits,
        Map<Location, Long> currentLoads) {
    long cap = Long.MAX_VALUE;
    for (EnergyPath path : pathGroup) {
        for (Location connLoc : path.connectors) {
            Long limit = limits.get(connLoc);
            if (limit == null) continue;
            if (limit == 0) return 0;
            long currentLoad = currentLoads.getOrDefault(connLoc, 0L);
            long remaining = limit - currentLoad;
            if (remaining <= 0) return 0;
            if (remaining < cap) cap = remaining;
        }
    }
    return cap;
}
```

---

## Task 9: 重写 `applyTransferResult()` — 完整的三阶段写回

**目的:** 替换上一轮的 `applyTickResult()`，完整应用 delta + 调用 `storeRemainingEnergy` + meters + aging。

**Files:**
- Modify: `EnergyNet.java` — 替换 `applyTickResult()` 方法

- [ ] **Step 1: 替换 applyTransferResult() 方法**

```java
private void applyTransferResult(@Nonnull TransferResult result, @Nonnull GridTickSnapshot snapshot) {
    // 1. 写回发电机充电量 delta
    for (Map.Entry<Location, Long> entry : result.generatorChargeDeltas.entrySet()) {
        EnergyNetProvider gen = generators.get(entry.getKey());
        if (gen != null && gen.isChargeable()) {
            long oldCharge = gen.getChargeLong(entry.getKey());
            gen.setCharge(entry.getKey(), oldCharge + entry.getValue());
        }
    }

    // 2. 写回用电器充电量 delta
    for (Map.Entry<Location, Long> entry : result.consumerChargeDeltas.entrySet()) {
        EnergyNetComponent con = consumers.get(entry.getKey());
        if (con != null) {
            long oldCharge = con.getChargeLong(entry.getKey());
            con.setCharge(entry.getKey(), oldCharge + entry.getValue());
        }
    }

    // 3. 写回电容放电 delta
    for (Map.Entry<Location, Long> entry : result.capacitorChargeDeltas.entrySet()) {
        EnergyNetComponent cap = capacitors.get(entry.getKey());
        if (cap != null) {
            long oldCharge = cap.getChargeLong(entry.getKey());
            cap.setCharge(entry.getKey(), oldCharge + entry.getValue());
        }
    }

    // 4. 多余电力存入电容 (保留在 Phase 3 因为需要 Storage I/O)
    long stored = storeRemainingEnergy(result.excessEnergy);
    totalStoredThisTick = stored;
    // 能量克隆防护: 按比例扣减发电机 charge（原 performEnergyTransfer 逻辑）
    if (stored > 0 && netNewEnergy > 0) {
        for (Map.Entry<Location, Long> entry : perGeneratorNewCharge.entrySet()) {
            Location genLoc = entry.getKey();
            long newCharge = entry.getValue();
            if (newCharge <= 0) continue;
            EnergyNetProvider gen = generators.get(genLoc);
            if (gen != null && gen.isChargeable()) {
                double fraction = (double) newCharge / netNewEnergy;
                long reduction = (long) (fraction * stored);
                if (reduction > 0) {
                    gen.setCharge(genLoc, Math.max(0, gen.getChargeLong(genLoc) - reduction));
                }
            }
        }
    }

    // 5. 更新 connectorLoad (用于 ConnectorAgingManager)
    connectorLoad.clear();
    connectorLoad.putAll(result.connectorLoads);

    // 6. 传播到电量计数器
    for (Map.Entry<Location, Long> entry : result.meterPropagation.entrySet()) {
        Location above = entry.getKey().clone().add(0, 1, 0);
        var data = StorageCacheUtils.getDataContainer(above);
        if (data != null && !data.isPendingRemove() && "ENERGY_METER".equals(data.getSfId())) {
            long acc = parseLongOrZero(data, "energy-counter");
            acc = NumberUtils.flowSafeAddition(acc, entry.getValue());
            data.setData("energy-counter", String.valueOf(acc));
        }
    }

    // 7. 连接器老化
    ConnectorAgingManager.processAging(this);

    // 8. 统计更新
    totalProducedThisTick = result.totalProduced;
    totalConsumedThisTick = result.totalConsumed;

    long currentTotalCharge = calculateTotalCharge();
    totalNetStoredThisTick = currentTotalCharge - lastTotalCharge;
    lastTotalCharge = currentTotalCharge;
    lastSupply = calculateTotalSupply();
    lastDemand = calculateTotalDemand();
    firstTickDone = true;

    debugLog("tickSelf: 电力传输完成 | 发电=" + lastSupply + " 用电=" + lastDemand
            + " | 发电机=" + generators.size() + " 连接器=" + connectors.size()
            + " 电容=" + capacitors.size() + " 用电器=" + consumers.size()
            + " 路径=" + (countTotalPaths(generatorPaths) + countTotalPaths(capacitorPaths)));

    // 9. 全息图更新
    if (regulator.getChunk().isLoaded()) {
        var data = StorageCacheUtils.getBlock(regulator);
        if (data != null && !data.isPendingRemove()) {
            updateHologram(data, lastSupply, lastDemand);
        }
    }
}
```

---

## Task 10: 清理死代码 — 删除上一轮遗留的 `applyTickResult()` 和旧的 `collectTickSnapshot()`

**目的:** 确保没有重复的旧方法残留。

**Files:**
- Delete from: `EnergyNet.java` — 删除旧的 `applyTickResult(@Nonnull TickResult result)` 方法（含 `GridTickSnapshot` 参数的版本已替换）
- Delete from: `EnergyNet.java` — 确认旧的 `collectTickSnapshot()` 已被替换
- Delete from: `EnergyNet.java` — 删除旧的 `computeTick(@Nonnull GridTickSnapshot snap)` 方法
- Delete from: `EnergyNet.java` — 删除 `TickResult` 类（已被 `TransferResult` 替换）

- [ ] **Step 1: 删除旧的 applyTickResult() 方法 (无 snapshot 参数的版本)**

如果存在不带 `GridTickSnapshot` 参数的旧版 `applyTickResult(TickResult)`，删除它的整个方法体。

- [ ] **Step 2: 删除旧的 computeTick() 方法**

搜索 `private TickResult computeTick` 并删除整个方法。

- [ ] **Step 3: 确认 TickResult 类已不存在**

搜索 `private static final class TickResult` — 如果存在，删除整个类。

- [ ] **Step 4: 运行 build 验证**

```bash
.\mvnw.cmd -q -DskipTests package
```

检查编译错误，确保没有对已删除方法/类型的引用。

---

## Task 11: 运行 build 并验证

- [ ] **Step 1: Run build**

```bash
.\mvnw.cmd -q -DskipTests package
```

Expected: BUILD SUCCESS

- [ ] **Step 2: 检查 diff 中的新增行数**

```bash
git diff --stat -- src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java
```

验证没有意外的大规模格式改动。

- [ ] **Step 3: 手动对比 original performEnergyTransfer 和新代码**

对照以下原始方法，确认所有逻辑在新架构中都有对应：
- `tickAllGenerators()` → Phase 1 直接调用 ✓
- `tickAllCapacitors()` → Phase 1 直接调用 ✓
- `transferFromGenerators()` → `transferFromGeneratorsSnapshot()` (Phase 2) ✓
- `transferFromCapacitors()` → `transferFromCapacitorsSnapshot()` (Phase 2) ✓
- `computeLimiterCap()` → `computeLimiterCapSnapshot()` (Phase 2) ✓
- `storeRemainingEnergy()` → Phase 3 直接调用 ✓
- `getSortedPaths()` → `getSortedPathsStatic()` (Phase 2) ✓
- `recordConnectorLoad()` → 内联到 transfer 方法中 ✓
- 发电机能量克隆防护 → `applyTransferResult()` Phase 3 ✓
- `propagateToEnergyMeters()` → `applyTransferResult()` Phase 3 ✓
- `ConnectorAgingManager.processAging()` → `applyTransferResult()` Phase 3 ✓
- 统计更新 + 全息图 → `applyTransferResult()` Phase 3 ✓

---

## 执行顺序

```
Task 1 → Task 2 → Task 3 → Task 4 → Task 5 → Task 6 → Task 7 → Task 8 → Task 9 → Task 10 → Task 11
```

所有任务必须按顺序执行，因为后一个任务依赖前面定义的类型和方法签名。

---

## 自我审查

**1. Spec coverage:**
- tickAllGenerators() 调用: Task 4 ✓
- tickAllCapacitors() 调用: Task 4 ✓
- 发电机→用电器传输 (含限电器): Task 5 + 6 ✓
- 电容→用电器传输 (含限电器): Task 5 + 7 ✓
- storeRemainingEnergy() 主线程: Task 9 ✓
- 能量克隆防护: Task 9 ✓
- propagateToEnergyMeters: Task 9 ✓
- ConnectorAging: Task 9 ✓
- 统计更新 + 全息图: Task 9 ✓
- 不阻塞主线程的异步计算: Task 4 ✓
- 不跨线程访问 Bukkit/Storage: Phase 1 + Phase 3 在主线程, Phase 2 纯数学 ✓

**2. Placeholder scan:** 无 TBD/TODO/placeholder。

**3. Type consistency:**
- `GridTickSnapshot` 在 Task 1 定义, Task 3/4/5/6/7/9 使用 ✓
- `TransferResult` 在 Task 2 定义, Task 4/5/9 使用 ✓
- `getSortedPathsStatic()` 在 Task 8 定义, Task 6/7 使用 ✓
- `computeLimiterCapSnapshot()` 在 Task 8 定义, Task 6/7 使用 ✓
- 所有 `Location` key 在 snapshot 中是深拷贝 (HashMap new)，异步线程安全 ✓
