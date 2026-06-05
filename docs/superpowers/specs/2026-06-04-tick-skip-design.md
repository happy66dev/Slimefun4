# EnergyNet Tick 跳过机制设计

**日期**: 2026-06-04
**状态**: 已批准
**范围**: `EnergyNet.java` 单文件修改

## 背景

EnergyNet 使用三阶段 tick 架构（Phase 1 主线程采样 → Phase 2 异步计算 → Phase 3 主线程写回），由 `Bukkit.getScheduler().runTaskTimer(delay, delay)` 固定间隔驱动。

当前防重入机制：`AtomicBoolean selfTicking` CAS——如果上一轮未完成，新 tick 直接 return。但这意味着调度器仍然每 `delay` ticks 触发一次回调，虽然立即返回，但浪费了调度器槽位和方法调用开销。

**问题**：当电网规模大、计算耗时超过 1 tick interval 时，调度器会连续触发多次无效调用。

## 目标

当 Phase 1 + Phase 2 + Phase 3 总耗时超过 1 tick interval 时，自动跳过下一次 tick 调度，等待一个完整间隔后再执行。

## 方案选型

| 方案 | 描述 | 改动量 | 风险 |
|------|------|--------|------|
| **A: 冷却计数器** ✅ | AtomicInteger skipTicks，入口检查递减 | 最小 | 最低 |
| B: 单次重调度 | runTaskLater 替代 runTaskTimer | 中等 | 遗漏重调度风险 |
| C: 动态间隔 | 取消/重建定时器 | 最大 | 频繁取消重建 |

选择方案 A。

## 设计

### 1. 新增字段

```java
private final AtomicInteger skipTicks = new AtomicInteger(0);
private int tickInterval; // 保存调度间隔（ticks）
```

位置：`selfTicking` (L178) 附近。

### 2. scheduleSelfTick() 保存间隔

```java
private void scheduleSelfTick() {
    cancelSelfTick();
    int delay = DEBUG ? 40 : Slimefun.getCfg().getInt("URID.custom-ticker-delay");
    tickInterval = delay; // 新增
    selfTickTaskId = Bukkit.getScheduler()
            .runTaskTimer(Slimefun.instance(), this::tickSelfMainThread, delay, delay)
            .getTaskId();
}
```

### 3. tickSelfMainThread() 入口修改

在 `selfTicking` CAS 之前插入：

```java
if (skipTicks.get() > 0) {
    skipTicks.decrementAndGet();
    return;
}
```

在 Phase 2 的 `Slimefun.runSync` 回调（Phase 3）末尾，`selfTicking.set(false)` 之前：

```java
long elapsedNanos = System.nanoTime() - phaseStartNanos;
long tickIntervalNanos = tickInterval * 50_000_000L;
if (elapsedNanos > tickIntervalNanos) {
    skipTicks.set(1);
    debugLog("tick耗时 " + (elapsedNanos / 1_000_000) + "ms > "
        + (tickInterval * 50) + "ms, 跳过下一次tick");
}
```

`phaseStartNanos` 在 Phase 1 开始前记录：`long phaseStartNanos = System.nanoTime();`

### 4. 重置 skipTicks 的场景

- `cancelSelfTick()` 中：`skipTicks.set(0)`
- `initializeNetworkAsync()` 开始时：`skipTicks.set(0)`

### 时序

```
正常 (Phase 1+2+3 < interval):
  tick─P1─P2─P3──idle──idle──tick─P1─P2─P3──

超时 (Phase 1+2+3 > interval):
  tick─P1─P2───P3(skip=1)──skip──tick─P1─P2─P3──
              ↑ 超时设置     ↑ 跳过    ↑ 正常恢复
```

### 线程安全

- `skipTicks` 是 `AtomicInteger`，CAS 读写线程安全
- `skipTicks.get()` 和 `decrementAndGet()` 在主线程（tick 回调）
- `skipTicks.set(1)` 在 Phase 3 的 `runSync` 回调（也是主线程）
- `skipTicks.set(0)` 在 `cancelSelfTick()` 和 `initializeNetworkAsync()` 中（也是主线程或初始化线程）
- 所有写入都在主线程或初始化线程，无竞态条件

### 不改动的部分

- `scheduleSelfTick()` 的 `runTaskTimer(delay, delay)` 不变
- `selfTicking` CAS 防重入机制不变（作为安全网保留）
- `computeTransfers()` 纯函数不变
- `applyTransferResult()` 不变

## 验证标准

1. 小电网（计算 < interval）：tick 行为不变，skipTicks 始终为 0
2. 大电网（计算 > interval）：skipTicks 在超时后被设为 1，下一次 tick 被跳过，再下一次正常执行
3. 电网销毁/重建时 skipTicks 被重置为 0
4. debug 日志在超时时输出耗时信息
