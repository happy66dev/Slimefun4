# 限电器均分分配算法设计

**日期**: 2026-06-05
**状态**: 已批准
**范围**: `allocatePathGroupWithLimits()` 方法重写

## 背景

当前限电器算法使用"饱和最受限路径优先"策略：找到 available 最小的路径，先分配给它直到饱和，然后处理剩余路径。这导致共享同一限电器的多条路径中，先被处理的路径获得全部容量，后被处理的路径获得 0。

**问题场景**：
```
Path1: A → C(30J) → D → G
Path2: A → C(30J) → F → G
```
当前：Path1=30J, Path2=0J（C 的容量被 Path1 独占）
期望：Path1=15J, Path2=15J（C 的容量均分）

## 目标

当多条路径共享同一限电器时，限电器的容量应在所有经过它的路径之间均分。如果某条路径因其他限电器而无法使用其全部份额，剩余容量重新分配给其他路径。

## 算法设计

### 核心算法

```
输入: pathGroup, limits, globalLoads, maxAmount
输出: totalAllocated

1. 统计每个限电器被多少条活跃路径经过: limiterPathCount
2. 计算每条路径的"均分份额":
   - 对路径上的每个限电器: share = (limit - globalUsed) / pathCount
   - 路径有效限额 = min(所有限电器的 share)
3. 快速路径: 如果所有路径的 share >= remaining / activeCount，直接均分，结束
4. 迭代:
   a. equalShare = remaining / activeCount
   b. 对每条活跃路径: alloc = min(share, equalShare)
   c. 如果 alloc < equalShare (路径被截断):
      - 分配 alloc，标记路径为不活跃
      - 更新 limiterPathCount (该限电器的路径数 -1)
      - 重新计算剩余路径的 share
      - 回到 4a
   d. 如果没有路径被截断: 直接均分，结束
5. 更新 globalLoads
```

### 场景验证

#### 场景 1：共享限电器 + 独立限电器
```
         B(50J) → D → G
A → 
         C(30J) → E → G
         C(30J) → F → G
```
- limiterPathCount: B=1, C=2
- Path1 share=50, Path2 share=15, Path3 share=15
- equalShare=33: Path2 截断(15), Path3 截断(15)
- 重新计算: Path1 share=50, equalShare=70 → Path1=50
- **结果**: 50+15+15=80 ✓

#### 场景 2：多层嵌套限电器
```
         C(20J) → D → G
A → B(10J) →
         E(30J) → F → G
```
- limiterPathCount: B=2, C=1, E=1
- Path1 share=min(5,20)=5, Path2 share=min(5,30)=5
- equalShare=50: Path1 截断(5), Path2 截断(5)
- **结果**: 5+5=10 ✓

#### 场景 3：三层嵌套 + 重分配
```
         C(60J) → D → G
A → B(10J) →
         E(30J) → F → G
```
- limiterPathCount: B=2, C=1, E=1
- Path1 share=min(5,60)=5, Path2 share=min(5,30)=5
- equalShare=50: Path1 截断(5), Path2 截断(5)
- 重新计算: B 路径数=1, Path2 share=min(10,30)=10
- **结果**: 5+10=15 ✓

#### 场景 4：用户原始例子
```
         B → E(50J) → G
A →
         C(30J) → D → G
         C(30J) → F → G
```
- limiterPathCount: E=1, C=2
- Path1 share=50, Path2 share=15, Path3 share=15
- equalShare=33: Path2 截断(15), Path3 截断(15)
- 重新计算: Path1 share=50, equalShare=70 → Path1=50
- **结果**: 50+15+15=80 ✓

## 实现方案

### 修改方法

`allocatePathGroupWithLimits()` — 重写核心分配逻辑

### 保留不变

- `computePathLimiterAvailable()` — 保留，用于计算单路径限电器余量
- `addToLocalLoads()` — 保留，用于局部负载追踪
- `recordConnectorLoadSnapshot()` — 保留，用于无限电器路径的负载记录
- `connectorLimits` 初始化逻辑 — 不变
- `groupPathsBySourceConsumerAndLength()` — 不变
- `transferFromGeneratorsSnapshot()` / `transferFromCapacitorsSnapshot()` — 不变

### 性能优化

- **快速路径**: 如果所有路径的 share >= equalShare，直接均分，无需迭代
- **迭代次数**: 最多 N 轮（N = 路径数），每轮至少一条路径饱和退出
- **提前退出**: 如果 remaining <= 0 或 activeCount <= 0，退出

## 验证标准

1. 共享限电器的路径均分容量
2. 多层嵌套限电器正确处理
3. 被截断路径的剩余容量正确重分配
4. 全局负载正确更新
5. 无限电器路径行为不变
6. 性能无显著下降
