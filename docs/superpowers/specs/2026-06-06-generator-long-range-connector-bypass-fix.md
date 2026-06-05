# 发电机与电容路径规则修复（禁止直达长途连接器）

**日期**: 2026-06-06  
**范围**: EnergyNet 邻居规则 + 文档同步  
**目标**: 修复两实例路径异常，确保发电机必须先经短路连接器，再通过长途连接器拓扑扩展

---

## 问题实例

### 实例 A（原：无路线但仍传输且仅长途有负载）

拓扑：
```
LongRangeConnector → ShortConnector → 空气 → Regulator → Capacitor(或Consumer)
                      ShortConnector
                      Generator
```

- 期望：Generator 先到 ShortConnector，再到 LongRangeConnector，再经拓扑到 Capacitor/Consumer。
- 现象：`generatorToCapacitorPaths` 可能为空，但仍有“传输”和长途连接器负载记录（来自 storeRemainingEnergy 降级分支）。
- 根因：Generator 邻居规则允许直达 LongRangeConnector，导致 BFS 路径与用户期望拓扑不一致。

### 实例 B（原：有路线但不是期望值）

拓扑：
```
LongRangeConnector → ShortConnector → Capacitor(或Consumer) → Regulator
                      ShortConnector
                      Generator
```

- 期望路径：`Generator → ShortConnector → LongRangeConnector → ShortConnector → Capacitor`
- 现象：Generator 直达 LongRangeConnector，路径更短但不符合“必须先经短路连接器”的期望。

---

## 规则调整

### 代码修改

**文件**: `src/main/java/io/github/thebusybiscuit/slimefun4/core/networks/energy/EnergyNet.java`

#### 1. `getNeighbors()` 发电机/电容邻居排除 LongRangeConnector

- 原：Generator/Capacitor 邻居包含所有 `CONNECTOR`（包括 LongRangeConnector）
- 新：Generator/Capacitor 邻居排除 `LongRangeConnector`，只允许普通连接器和调节器

```java
EnergyNetComponent connectorComp = getComponent(connectorLoc);
if (connectorComp instanceof LongRangeConnector) {
    continue;
}
```

#### 2. `storeRemainingEnergy()` 移除降级分支

- 原：无 BFS 路径时，遍历所有连接器找"轴向能覆盖电容"的连接器记负载（随意走）
- 新：无 BFS 路径时跳过该电容，不充电、不记负载

```java
if (allCapPaths.isEmpty()) {
    debugLog("storeRemainingEnergy电容: 跳过 " + formatLocation(loc) + " (无BFS路径，不传输)");
    continue;
}
```

### 文档同步

| 文档 | 修改内容 |
|------|----------|
| `docs/energynet-spec.md` | GENERATOR 邻居表更新为“调节器 + 短路连接器（不允许直达LongRangeConnector）” |
| `docs/当前分支修改内容.md` | 新增“发电机路径规则修复 (2026-06-06)”行 + 构建状态追加说明 |
| `docs/superpowers/specs/2026-06-06-generator-long-range-connector-bypass-fix.md` | 本文件 |

---

## 期望行为

1. **Generator → LongRangeConnector**：禁止直达
2. **Generator → ShortConnector**：允许（受 `validateConnection` 轴向范围约束）
3. **Capacitor → LongRangeConnector**：禁止直达（仅允许邻接短路连接器）
4. **LongRangeConnector → ShortConnector**：允许（6轴扫描，最近连接器）
5. **ShortConnector → Capacitor/Consumer**：允许（邻接/范围约束）

---

## 验证方法

### 构建验证

```bash
mvn -q clean package -DskipTests
```

### 场景验证

1. **实例 A**
   - 放置拓扑
   - 预期：Generator 路径经过 ShortConnector → LongRangeConnector → ShortConnector → Capacitor
   - 预期：`generatorToCapacitorPaths` 非空，storeRemainingEnergy 使用预计算路径

2. **实例 B**
   - 放置拓扑
   - 预期：路径 `Generator → ShortConnector → LongRangeConnector → ShortConnector → Capacitor`
   - 预期：不存在 Generator 直达 LongRangeConnector 的路径

---

## 风险评估

| 风险 | 等级 | 说明 |
|------|------|------|
| 发电机无短路连接器邻居时无路径 | 中 | 若拓扑中只有长途连接器且无短路连接器，发电机将无法供电（符合新规则） |
| 既有路径全部失效 | 低 | 仅影响 Generator 邻居，Capacitor/Connector 路径不变 |
| 文档遗漏 | 低 | 已同步 spec、分支修改内容、本 spec 文件 |
