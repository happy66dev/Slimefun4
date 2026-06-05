# 机器反馈 API 扩展 — 设计文档 (Design Spec)

> 版本: 1.0 | 日期: 2025-12-01 | 状态: Draft

## 1. 概述

将 `MachineFeedbackType` 枚举重构为 `MachineFeedback` 接口，提供插件扩展 API，同时新增 21 个内置样式。

## 2. 新增文件

```
src/main/java/io/github/thebusybiscuit/slimefun4/
├── core/machines/
│   ├── MachineFeedback.java          (新增: 接口)
│   └── MachineFeedbackRegistry.java  (新增: 注册表)
```

## 3. 修改文件

| 文件 | 修改 |
|------|------|
| `MachineFeedbackType.java` | 改为 implements MachineFeedback，新增 21 个枚举值 |
| `MachineFeedbackService.java` | 参数类型从 MachineFeedbackType 改为 MachineFeedback |
| `AContainer.java` | feedbackType 改为 MachineFeedback 类型 |
| `AGenerator.java` | feedbackType 改为 MachineFeedback 类型 |
| `Slimefun.java` | 初始化 MachineFeedbackRegistry |

## 4. 27 种内置样式

见讨论记录，分为 SMELTING(4) / GRINDING(3) / ENCHANTING(3) / COOKING(3) / MECHANICAL(4) / FLUID(3) / ELECTRIC(3) / CRYOGENIC(2) / RADIANT(2)。

## 5. MachineFeedback 接口

```java
public interface MachineFeedback {
    Particle getDefaultParticle();
    Sound getDefaultSound();
    ParticleOffset getParticleOffset();
    default void onMachineStart(Block b) {}
    default void onMachineTick(Block b, MachineOperation op) {}
    default void onMachineStop(Block b) {}
}
```

## 6. MachineFeedbackRegistry

```java
// 静态注册表，拓展插件在 onEnable 中调用 register()
// 启动时自动注册 MachineFeedbackType 所有值
MachineFeedbackRegistry.register(String key, MachineFeedback feedback);
MachineFeedbackRegistry.get(String key);
```

## 7. 实现顺序

1. 创建 MachineFeedback 接口
2. 创建 MachineFeedbackRegistry
3. 重写 MachineFeedbackType（27 值）
4. 更新 MachineFeedbackService、AContainer、AGenerator
5. 更新 Slimefun.java 初始化
6. 编译测试

## 8. 内置机器反馈映射优化

为现有发电机和用电器按机器语义细化反馈样式，避免所有机器只使用少量通用样式。

| 机器 | 反馈样式 | 语义 |
|------|----------|------|
| ElectricFurnace | `SMELTING` | 常规电炉熔炼 |
| ElectricSmeltery | `SMELTING_BLAZING` | 高温熔炼 |
| ElectricOreGrinder | `GRINDING` | 常规研磨 |
| ElectricDustWasher | `FLUID_BUBBLING` | 洗矿液体冒泡 |
| ElectricIngotPulverizer | `GRINDING_HEAVY` | 重型粉碎 |
| ElectricIngotFactory | `SMELTING` | 金属加工熔炼 |
| ElectrifiedCrucible | `FLUID_LAVA` | 熔岩产出 |
| HeatedPressureChamber | `MECHANICAL_STEAM` | 加热加压与蒸汽 |
| Freezer | `CRYOGENIC_FREEZING` | 冷冻 |
| FoodComposter | `MECHANICAL` | 堆肥搅拌 |
| FoodFabricator | `COOKING` | 食物加工 |
| AutoDrier | `COOKING_STEAMING` | 烘干与蒸发 |
| AutoBrewer | `COOKING` | 酿造 |
| ElectricGoldPan | `FLUID_BUBBLING` | 淘洗 |
| Refinery | `FLUID` | 流体精炼 |
| OilPump | `FLUID` | 抽取流体 |
| ChargingBench | `ELECTRIC_ARC` | 充电电弧 |
| AutoAnvil | `MECHANICAL_HEAVY` | 重型锻造 |
| ElectricPress | `MECHANICAL_HEAVY` | 压制成型 |
| CarbonPress | `MECHANICAL_HEAVY` | 高压碳压缩 |
| ProduceCollector | `MECHANICAL_CLOCKWORK` | 自动收集机构 |
| AutoEnchanter | `ENCHANTING_ARCANE` | 注入附魔 |
| AutoDisenchanter | `ENCHANTING_DARK` | 移除附魔 |
| BookBinder | `ENCHANTING` | 书本附魔处理 |
| CoalGenerator | `SMELTING` | 常规燃烧发电 |
| CombustionGenerator | `SMELTING_BLAZING` | 燃油高热燃烧 |
| LavaGenerator | `FLUID_LAVA` | 熔岩发电 |
| MagnesiumGenerator | `RADIANT` | 镁盐高亮反应 |
| BioGenerator | `COOKING` | 生物质处理 |
