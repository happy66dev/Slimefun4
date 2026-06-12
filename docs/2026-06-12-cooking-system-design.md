# 烹饪系统（FarmerDelight 风格）— 设计规格

日期: 2026-06-12
状态: 设计完成
基础项目: ExoticGardenComplex

---

## 目标

在 ExoticGardenComplex 插件内新增一套完整的烹饪系统，核心特性：
- 基于篝火外观的灶台方块，无 GUI，全右键交互
- 温度 + 双面成熟度机制
- 砧板 + 刀 + 锅铲配套工具
- 取出成品时异步调用 OpenAI API 生成独特菜肴

---

## 一、模块结构

全部代码新增在 ExoticGardenComplex 内，新建 `cooking/` 子包，不创建新插件。

```
ExoticGardenComplex/src/main/java/
└── io/github/thebusybiscuit/exoticgarden/
    └── cooking/
        ├── CookingModule.java
        ├── block/
        │   ├── StoveBlock.java
        │   └── CuttingBoardBlock.java
        ├── item/
        │   ├── SpatulaItem.java
        │   └── KnifeItem.java
        ├── state/
        │   ├── StoveState.java
        │   ├── FuelEntry.java
        │   ├── IngredientSlot.java
        │   └── SeasoningEntry.java
        ├── config/
        │   ├── FuelConfig.java
        │   ├── IngredientConfig.java
        │   └── SeasoningConfig.java
        ├── task/
        │   └── StoveTickTask.java
        ├── ai/
        │   └── DishGenerator.java
        └── hologram/
            └── StoveHologram.java
```

`CookingModule.java` 在 `ExoticGarden.onEnable()` 中调用，负责：
- 加载三个 YAML 配置
- 注册所有物品到独立 ItemGroup（`NamespacedKey: "cooking"`）
- 启动 `StoveTickTask`（`scheduleSyncRepeatingTask` period=2）

---

## 二、配置文件

### config.yml（已有文件，新增段落）

```yaml
cooking:
  ai_api_key: "sk-xxxxxxxx"
  ai_model: "gpt-4o-mini"
  ai_base_url: "https://api.openai.com/v1"
  hologram_update_ticks: 5
  cook_tick_interval: 2
```

`ai_base_url` 可替换为代理地址，方便国内环境使用。

### fuels.yml（新增）

```yaml
OAK_LOG:
  temp_gain: 300
  duration_seconds: 80
  heat_rate: 6
  effect: WOOD_SMOKE
  byproduct: CHARCOAL
CHARCOAL:
  temp_gain: 400
  duration_seconds: 180
  heat_rate: 4
  effect: SMOKY
  byproduct: null
ICE:
  temp_gain: 0
  duration_seconds: 6
  heat_rate: -40
  effect: FREEZING
  byproduct: null
```

### ingredients.yml（新增）

```yaml
BEEF:
  type: MAIN
  states: [WHOLE, SLICED, DICED]
  min_temp: 60
  max_temp: 240
  optimal_temp_min: 180
  optimal_temp_max: 210
  base_cook_time_seconds: 45
  flip_required: true
TOMATO:
  type: SAUCE_BASE
  states: [WHOLE, SAUCE]
  sauce_creation:
    tool: SPATULA
    clicks_required: 3
  min_temp: 40
  max_temp: 100
  optimal_temp_min: 70
  optimal_temp_max: 90
  base_cook_time_seconds: 30
  flip_required: false
```

### seasonings.yml（新增）

```yaml
SALT:
  display_name: "盐"
  has_doneness: false
SUGAR:
  display_name: "白糖"
  has_doneness: false
RICE_WINE:
  display_name: "料酒"
  has_doneness: true
  min_temp: 50
  optimal_temp: 80
  base_time_seconds: 20
```

---

## 三、运行时状态结构

### StoveState

```
double currentTemp              // 当前温度（℃），最低 30
List<FuelEntry> fuels           // 最多 2 个燃料槽
IngredientSlot[4] slots         // 4 个主菜槽，null = 空
List<SeasoningEntry> seasonings // 最多 10 个辅料条目
int spatulaBoostTicksLeft       // 锅铲加速剩余 tick 数（200 tick = 10s）
boolean pendingFuelClear        // 等待二次确认清除燃料标志
```

### FuelEntry

```
String fuelId                   // fuels.yml 中的 key
double ticksRemaining           // 剩余燃烧 tick 数（duration_seconds × 20）
```

### IngredientSlot

```
String ingredientId             // ingredients.yml 中的 key
FoodState state                 // WHOLE / SLICED / DICED / SAUCE（枚举，可扩展）
double frontDoneness            // 0.0~1.0
double backDoneness             // 0.0~1.0，仅 WHOLE 状态有意义
ActiveFace currentFace          // 枚举：FRONT / BACK（决定当前加热的面）
double charSeconds              // 超出 max_temp 的累计秒数，取出时统一计算烧焦等级
```

### SeasoningEntry

```
String seasoningId
double progress                 // 0.0~1.0，has_doneness=false 时始终为 1.0
```

### 烧焦等级（取出时计算，传给 AI）

```
charSeconds < 10  → NONE
charSeconds < 20  → LIGHT
charSeconds < 40  → HEAVY
charSeconds >= 40 → SEVERE
```

---

## 四、StoveTickTask（全局，每 2 tick 执行）

每次执行 = 0.1 秒，遍历 `StoveBlock.activeStoves`（`Map<Location, StoveState>`）喵。

```
① 更新燃料
   对每个 FuelEntry：ticksRemaining -= 2
   若 ticksRemaining <= 0：移除，按配置掉落 byproduct 到灶台位置

② 计算温度增量
   有活跃燃料：
     targetTemp = Σ fuel.temp_gain
     heatRate   = Σ fuel.heat_rate
     currentTemp += heatRate × 0.1
     currentTemp = clamp(currentTemp, 30, targetTemp)
   无活跃燃料：
     coolRate = max((currentTemp + 20) × 0.01, 0.5) - 0.5
     currentTemp -= coolRate × 0.1
     currentTemp = max(currentTemp, 30)

③ 锅铲加速递减
   if spatulaBoostTicksLeft > 0: spatulaBoostTicksLeft -= 2

④ 更新每个非空 IngredientSlot
   查 IngredientConfig → 计算 increment（每 0.1s）：
     currentTemp < min_temp                    → 0
     min_temp ~ optimal_max                    → coefficient = 1.0
     optimal_max < currentTemp < max_temp      → coefficient 线性 1.0→0.5
     currentTemp >= max_temp                   → coefficient = 1.5，charSeconds += 0.1
   increment = (1.0 / base_cook_time) × 0.1 × coefficient
             × (spatulaBoostTicksLeft > 0 ? 2.0 : 1.0)

   WHOLE 状态：
     currentFace == FRONT → frontDoneness += increment，backDoneness += increment × 0.3
     currentFace == BACK  → backDoneness  += increment
   非 WHOLE 状态：frontDoneness += increment
   所有 doneness clamp 到 1.0

⑤ 更新 has_doneness=true 的 SeasoningEntry
   （同成熟度逻辑，参考 seasonings.yml 的 optimal_temp / base_time）

⑥ tickCounter 每 5 tick 更新一次全息（tickCounter % 5 == 0）
```

---

## 五、右键交互分发（StoveBlock BlockUseHandler）

`pendingFuelClear` 在任何非「空手潜行右键」时自动重置为 false。

```
优先级顺序：

1. 锅铲（NBT cooking:item_type = SPATULA）
   → 翻面：所有 currentFace==FRONT 且 frontDoneness>=0.5 的槽位切换为 BACK
   → 刷新 spatulaBoostTicksLeft = max(当前值, 200)

2. 碗（Material.BOWL，无 SF NBT）
   → 触发取出成品流程

3. 燃料（fuels.yml 中存在）
   → fuels.size() >= 2 → 拒绝提示
   → 否则消耗 1 个，添加 FuelEntry

4. 辅料（seasonings.yml 中存在）
   → seasonings.size() >= 10 → 拒绝提示
   → 否则消耗 1 个，添加 SeasoningEntry

5. 主菜原料（ingredients.yml 中存在，带 cooking:food_state NBT）
   → 4 槽全满 → 拒绝提示
   → 否则消耗 1 个，初始化 IngredientSlot 放入空槽

6. 刀（NBT cooking:item_type = KNIFE）→ 无操作

7. 空手 + 潜行
   → pendingFuelClear=false → 设为 true，聊天栏提示「再次潜行右键确认清除燃料」
   → pendingFuelClear=true  → 清除 fuels 列表（不返还物品），重置标志

8. 其他 → 无操作
```

---

## 六、取出成品流程

```
① 收集数据
   - 各槽位 charLevel（由 charSeconds 计算）
   - seasonings 列表（含 progress）
   - 活跃燃料的 effect 标签列表

② 清空 slots 数组 + seasonings 列表（燃料和温度保持不变）

③ 向玩家发送「烹饪中...」提示

④ BukkitScheduler.runTaskAsynchronously：
   a. 构造 prompt JSON
   b. HttpURLConnection POST → {ai_base_url}/chat/completions（JSON mode）
   c. 解析响应：name / hunger / saturation / quality / effects / description
   d. runTask（切回主线程）→ 生成菜肴 ItemStack，给玩家
   e. 失败 → 切回主线程发送「烹饪失败，请稍后再试」
```

### AI Prompt 结构

System prompt（固定，后续补充食材枚举等内容）：
```
你是一个 Minecraft 烹饪游戏的菜肴生成器。根据食材和烹饪状态，
用 JSON 格式返回菜肴信息。严格遵守格式，不输出任何其他内容。
```

User prompt（动态）：
```json
{
  "ingredients": [
    {"id": "BEEF", "state": "WHOLE", "frontDoneness": 0.95, "backDoneness": 0.88, "charLevel": "NONE"}
  ],
  "seasonings": [
    {"id": "SALT", "progress": null},
    {"id": "RICE_WINE", "progress": 0.72}
  ],
  "fuelEffects": ["SMOKY"],
  "language": "zh-CN"
}
```

期望返回格式：
```json
{
  "name": "烟熏番茄牛排",
  "hunger": 8,
  "saturation": 0.8,
  "quality": "GOOD",
  "effects": [],
  "description": "外焦里嫩的牛排，带着淡淡番茄酸香"
}
```

`quality` 枚举：`POOR / NORMAL / GOOD / EXCELLENT`

### 菜肴 ItemStack

- Material: `MUSHROOM_STEW`
- 信息写入 ItemMeta + PersistentDataContainer
- 不可堆叠（独立 NBT，amount=1）
- 消耗时通过 `ItemConsumptionHandler` 从 PDC 读取 hunger/saturation 恢复

---

## 七、砧板 + 刀 + 锅铲交互

### 砧板（CuttingBoardBlock）

- 放置时在上方生成不可见 `ItemDisplay` 实体（兼容回退到 ArmorStand）
- 实体位置绑定到 `Location`，存入 `static Map<Location, Entity> boardDisplays`
- 破坏时：若有物品则掉落，移除实体，从 Map 移除

```
BlockUseHandler：

手持物品 + 非潜行
  → 展示框已有物品 → 拒绝
  → 否则放入展示框，消耗玩家手持 1 个

空手 + 潜行
  → 有物品 → 取回给玩家，清空展示框
  → 否则无操作
```

### 刀（KnifeItem）

EntityInteractHandler，右键砧板展示框实体：

```
普通右键：
  取出物品，读取 cooking:food_state：
    WHOLE  → SLICED
    SLICED → DICED
    DICED  → DICED（已最细）
    SAUCE  → SAUCE
  写回 PDC，更新展示框物品

潜行+右键：
  取出物品还给玩家，清空展示框
```

### 锅铲酱汁制作（SpatulaItem）

EntityInteractHandler，右键砧板展示框实体：

```
取出物品，查 ingredients.yml 的 sauce_creation 配置
若存在且 state==WHOLE：
  读取砧板 PDC 的 cooking:spatula_clicks + 1
  若 >= clicks_required → FoodState 改为 SAUCE，重置计数，播放音效+粒子
  否则播放进度音效
spatula_clicks 存在砧板 PDC 上（防止多砧板互相干扰）
```

---

## 八、物品 NBT 约定（PDC Key）

| Key | 类型 | 说明 |
|---|---|---|
| `cooking:food_state` | String | `WHOLE / SLICED / DICED / SAUCE` |
| `cooking:ingredient_id` | String | ingredients.yml 中的 key |
| `cooking:item_type` | String | `SPATULA / KNIFE`（工具识别） |
| `cooking:dish_*` | 各类型 | 成品菜肴的 hunger/saturation/quality/description |
| `cooking:spatula_clicks` | Integer | 砧板上锅铲点击次数 |

---

## 九、全息显示（StoveHologram）

每 5 tick 调用一次，通过 `HologramOwner` 接口更新，内容示例：

```
§6[灶台] §e温度: §a156°C §7/ §f300°C
§b升温: +4°C/s §7(木炭 32s)  §c降温: 1.2°C/s
§7燃料2: 冰 5s
§e主菜1: §a牛肉[切块] §e87%
§e主菜2: §a胡萝卜[完整] §6正面45% 背面12%
§e主菜3: §c猪肉 §c⚠烧焦
§e主菜4: §7无
§d辅料: 盐, 料酒(70%), 白糖
```

---

## 十、数据存储与限制

- 所有灶台状态仅存在内存（`Map<Location, StoveState>`），服务器重启后丢失
- 不支持自动化输入/输出（管道、机器人）
- 灶台破坏时直接丢弃所有内部数据，不保留物品

---

## 十一、不包含

- 服务器重启后的状态持久化
- 灶台 GUI
- 自动化输入/输出支持
- 多语言 i18n（后续可扩展）
