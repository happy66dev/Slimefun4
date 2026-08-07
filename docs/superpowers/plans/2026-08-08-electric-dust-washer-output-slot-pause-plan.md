# 电动洗矿机产物槽满暂停实现计划

## 目标

在电动洗矿机启动新加工前，先确认输出槽 `24、25` 至少有一个空槽喵~两个输出槽都被占用时直接暂停，不消耗输入，也不创建新的加工操作喵~

## 修改文件

- `src/main/java/io/github/thebusybiscuit/slimefun4/implementation/items/electric/machines/ElectricDustWasher.java`

## 实现步骤

1. 在 `findNextRecipe(BlockMenu menu)` 的配方遍历之前增加输出槽空位检查喵~
2. 复用现有的 `hasplusSlot(BlockMenu menu)` 方法；返回 `false` 时立即返回 `null` 喵~
3. 保留现有各类配方的 `menu.fits(...)` 检查，继续防御特殊物品无法实际插入输出槽的情况喵~
4. 不修改 `AContainer`、普通洗矿机或其他电动机器喵~
5. 为新增判断补充中文注释，说明两个输出槽都被占用时暂停的业务原因喵~

## 验证步骤

1. 检查代码差异，确认只改动电动洗矿机逻辑和对应注释喵~
2. 执行针对项目的编译或测试命令，优先运行 `mvn package -DskipTests` 喵~
3. 构建完成后执行仓库要求的 `copy_jars.bat` 喵~
4. 检查 Git 状态并提交实现改动喵~

## 预期结果

- 两个输出槽都非空时，电动洗矿机保持空闲，不消耗输入喵~
- 任意输出槽为空时，机器继续按原有逻辑选择随机产物并加工喵~
- 已经开始的加工不被本次修改中断喵~
- 普通洗矿机和其他电动机器行为不变喵~
