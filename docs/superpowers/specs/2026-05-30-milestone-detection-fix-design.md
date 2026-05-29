# 里程碑音效检测逻辑修复 — 设计文档

> 版本: 1.0 | 日期: 2026-05-30 | 状态: Approved

## 1. 问题

`MachineFeedbackService.onMachineTick()` 中里程碑触发条件:

```java
if (prevPercent < milestone && currPercent >= milestone && !milestones.contains(milestone))
```

存在两个缺陷:

| 场景 | 现象 | 原因 |
|------|------|------|
| 进度大跳跃 (25%→75%) | 三个声音同时播放 | prevPercent < 25/50/75 全满足 |
| 进度精确跳过 (46→49→52) | milestone 50 丢失 | prevPercent(51) 已 >= 50, 条件不满足 |

大跳跃场景在标准 `AContainer`（`addProgress(1)`）中不会发生，但自定义 `MachineOperation` 或批量推进进度时会触发。

## 2. 需求

1. **大跳跃只播放一次**: 进度从 25% 跳到 75% 时，只播放最高里程碑 (75%) 的声音
2. **精确跳过仍播放**: 进度 46→49→52 跳过 50% 时，仍触发 50% 的声音
3. **每个里程碑最多触发一次**: 依赖 `milestones.contains()` 防重

## 3. 方案

将触发逻辑从 "prevPercent < milestone && currPercent >= milestone" 改为:

1. 遍历所有里程碑，找出 `currPercent >= milestone && !milestones.contains(milestone)` 的最高者
2. 标记该里程碑及以下所有未标记里程碑为已触发
3. 仅播放最高里程碑的声音（一次）

```java
int highestNewMilestone = -1;
for (int milestone : new int[] {25, 50, 75}) {
    if (currPercent >= milestone && !milestones.contains(milestone)) {
        highestNewMilestone = milestone;
    }
}
if (highestNewMilestone >= 0) {
    for (int milestone : new int[] {25, 50, 75}) {
        if (milestone <= highestNewMilestone) {
            milestones.add(milestone);
        }
    }
    // play sound once
}
```

## 4. 验证

| 场景 | prev% | curr% | 触发 | 声音 |
|------|-------|-------|------|------|
| 正常 24→25 | 24 | 25 | {25} | 1次 ✅ |
| 大跳跃 24→75 | 24 | 75 | {25,50,75} | 1次(75%) ✅ |
| 跳过 49→52 | 49 | 52 | {50} | 1次(50%) ✅ |
| 55→60 (50已触发) | 55 | 60 | 无 | 0次 ✅ |
| 新操作 0→25 (重置后) | 0 | 25 | {25} | 1次 ✅ |

## 5. 修改范围

仅修改 `MachineFeedbackService.onMachineTick()` 方法内的里程碑检测逻辑，约 15 行。
