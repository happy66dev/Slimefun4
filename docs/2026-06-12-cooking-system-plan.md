# ExoticGardenComplex Cooking System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 ExoticGardenComplex 插件添加完整的烹饪系统，包含灶台、砧板、刀具、锅铲等交互式物品，支持 AI 生成菜肴名称与属性。

**Architecture:** 以 `CookingModule` 作为入口统一初始化；灶台 `StoveBlock` 持有静态状态 Map 并由 `StoveTickTask` 每 2 tick 推进烹饪进度；砧板 `CuttingBoardBlock` + 工具物品负责食材预处理；`DishGenerator` 通过 OpenAI-compatible API 异步生成最终菜肴。

**Tech Stack:** Java 16, Maven, Spigot API 1.19.2, Slimefun4 (happy-SNAPSHOT), BukkitRunnable, HttpURLConnection, YamlConfiguration

---

## File Structure

| 文件 | 职责 |
|------|------|
| `cooking/state/FoodState.java` | 枚举：WHOLE/SLICED/DICED/SAUCE |
| `cooking/state/ActiveFace.java` | 枚举：FRONT/BACK |
| `cooking/state/CharLevel.java` | 枚举：NONE/LIGHT/HEAVY/SEVERE，含 fromSeconds |
| `cooking/state/FuelEntry.java` | POJO：燃料条目 |
| `cooking/state/IngredientSlot.java` | POJO：食材槽位 |
| `cooking/state/SeasoningEntry.java` | POJO：调料条目 |
| `cooking/state/StoveState.java` | POJO：灶台完整状态 |
| `cooking/config/YamlConfigLoader.java` | 泛型抽象基类，统一 YAML 解析 |
| `cooking/config/FuelConfig.java` | 从 fuels.yml 加载燃料数据 |
| `cooking/config/IngredientConfig.java` | 从 ingredients.yml 加载食材数据 |
| `cooking/config/SeasoningConfig.java` | 从 seasonings.yml 加载调料数据 |
| `cooking/calculator/DonenessCalculator.java` | 成熟度计算策略接口 |
| `cooking/calculator/StandardDonenessCalculator.java` | 标准成熟度计算实现 |
| `cooking/interaction/StoveInteractionHandler.java` | 灶台交互责任链接口 |
| `cooking/interaction/SpatulaInteractionHandler.java` | 锅铲交互 handler |
| `cooking/interaction/BowlInteractionHandler.java` | 碗取出成品 handler |
| `cooking/interaction/FuelInteractionHandler.java` | 燃料添加 handler |
| `cooking/interaction/SeasoningInteractionHandler.java` | 辅料添加 handler |
| `cooking/interaction/IngredientInteractionHandler.java` | 主菜添加 handler |
| `cooking/interaction/ClearFuelInteractionHandler.java` | 清除燃料 handler |
| `cooking/task/StoveTickTask.java` | BukkitRunnable，每 2 tick 推进所有灶台 |
| `cooking/hologram/StoveHologram.java` | 拼装全息文本字符串 |
| `cooking/ai/DishGenerator.java` | OpenAI-compatible API 异步调用，返回 DishResult |
| `cooking/block/StoveBlock.java` | 灶台 SlimefunItem，右键分发 + 取出成品 |
| `cooking/block/CuttingBoardBlock.java` | 砧板 SlimefunItem，食材展示与预处理 |
| `cooking/item/KnifeItem.java` | 刀具 SlimefunItem，切割食材状态 |
| `cooking/item/SpatulaItem.java` | 锅铲 SlimefunItem，制酱 + 灶台翻面 |
| `cooking/CookingModule.java` | 统一初始化入口 |
| `resources/fuels.yml` | 燃料配置示例 |
| `resources/ingredients.yml` | 食材配置示例 |
| `resources/seasonings.yml` | 调料配置示例 |
| `ExoticGarden.java` | 修改：onEnable 末尾调用 CookingModule.initialize |
| `resources/config.yml` | 修改：新增 cooking 段落 |

---

## Task 1: 枚举类（FoodState / ActiveFace / CharLevel）

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/FoodState.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/ActiveFace.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/CharLevel.java`

- [ ] **Step 1: 创建 FoodState 枚举**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.state;

public enum FoodState {
    WHOLE, SLICED, DICED, SAUCE
}
```

- [ ] **Step 2: 创建 ActiveFace 枚举**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.state;

public enum ActiveFace {
    FRONT, BACK
}
```

- [ ] **Step 3: 创建 CharLevel 枚举（含 fromSeconds 方法）**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.state;

public enum CharLevel {
    NONE, LIGHT, HEAVY, SEVERE;

    public static CharLevel fromSeconds(double s) {
        if (s < 10) return NONE;
        if (s < 20) return LIGHT;
        if (s < 40) return HEAVY;
        return SEVERE;
    }
}
```

- [ ] **Step 4: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS，无错误

- [ ] **Step 5: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/FoodState.java src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/ActiveFace.java src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/CharLevel.java; git commit -m "feat(cooking): add FoodState, ActiveFace, CharLevel enums"
```

---

## Task 2: 状态 POJO（FuelEntry / IngredientSlot / SeasoningEntry / StoveState）

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/FuelEntry.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/IngredientSlot.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/SeasoningEntry.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/StoveState.java`

- [ ] **Step 1: 创建 FuelEntry**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.state;

public class FuelEntry {
    public String fuelId;
    public double ticksRemaining;

    public FuelEntry(String fuelId, double ticksRemaining) {
        this.fuelId = fuelId;
        this.ticksRemaining = ticksRemaining;
    }
}
```

- [ ] **Step 2: 创建 IngredientSlot**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.state;

public class IngredientSlot {
    public String ingredientId;
    public FoodState state;
    public double frontDoneness;
    public double backDoneness;
    public ActiveFace currentFace;
    public double charSeconds;

    public IngredientSlot(String ingredientId, FoodState state, double frontDoneness,
                          double backDoneness, ActiveFace currentFace, double charSeconds) {
        this.ingredientId = ingredientId;
        this.state = state;
        this.frontDoneness = frontDoneness;
        this.backDoneness = backDoneness;
        this.currentFace = currentFace;
        this.charSeconds = charSeconds;
    }
}
```

- [ ] **Step 3: 创建 SeasoningEntry**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.state;

public class SeasoningEntry {
    public String seasoningId;
    public double progress;

    public SeasoningEntry(String seasoningId, double progress) {
        this.seasoningId = seasoningId;
        this.progress = progress;
    }
}
```

- [ ] **Step 4: 创建 StoveState**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.state;

import java.util.ArrayList;
import java.util.List;

public class StoveState {
    public double currentTemp;
    public List<FuelEntry> fuels;
    public IngredientSlot[] slots;
    public List<SeasoningEntry> seasonings;
    public int spatulaBoostTicksLeft;
    public boolean pendingFuelClear;

    public StoveState() {
        this.currentTemp = 30.0;
        this.fuels = new ArrayList<>();
        this.slots = new IngredientSlot[4];
        this.seasonings = new ArrayList<>();
        this.spatulaBoostTicksLeft = 0;
        this.pendingFuelClear = false;
    }
}
```

- [ ] **Step 5: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 6: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/FuelEntry.java src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/IngredientSlot.java src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/SeasoningEntry.java src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/state/StoveState.java; git commit -m "feat(cooking): add state POJOs FuelEntry, IngredientSlot, SeasoningEntry, StoveState"
```

---

## Task 3: 配置加载（YamlConfigLoader + FuelConfig / IngredientConfig / SeasoningConfig + YAML 文件）

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/config/YamlConfigLoader.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/config/FuelConfig.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/config/IngredientConfig.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/config/SeasoningConfig.java`
- Create: `src/main/resources/fuels.yml`
- Create: `src/main/resources/ingredients.yml`
- Create: `src/main/resources/seasonings.yml`

- [ ] **Step 0: 创建 YamlConfigLoader 泛型抽象基类**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public abstract class YamlConfigLoader<T> {

    protected final Logger logger;

    protected YamlConfigLoader(Logger logger) {
        this.logger = logger;
    }

    public Map<String, T> loadAll(File file, String rootKey) {
        if (!file.exists()) {
            logger.warning("[Cooking] Config file not found: " + file.getName());
            return Collections.emptyMap();
        }
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = rootKey.isEmpty() ? cfg
            : cfg.getConfigurationSection(rootKey);
        if (section == null) {
            logger.warning("[Cooking] Missing root section in " + file.getName());
            return Collections.emptyMap();
        }
        Map<String, T> result = new HashMap<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection s = section.getConfigurationSection(key);
            if (s == null) continue;
            try {
                result.put(key, parseEntry(key, s));
            } catch (Exception e) {
                logger.warning("[Cooking] Failed to parse entry '" + key + "' in " + file.getName() + ": " + e.getMessage());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    protected abstract T parseEntry(String key, ConfigurationSection section);
}
```

- [ ] **Step 1: 创建 FuelConfig（继承 YamlConfigLoader）**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

public class FuelConfig extends YamlConfigLoader<FuelConfig.FuelData> {

    public static class FuelData {
        public final double tempGain;
        public final double durationSeconds;
        public final double heatRate;
        public final String effect;
        public final String byproduct;

        public FuelData(double tempGain, double durationSeconds, double heatRate,
                        String effect, String byproduct) {
            this.tempGain = tempGain;
            this.durationSeconds = durationSeconds;
            this.heatRate = heatRate;
            this.effect = effect;
            this.byproduct = byproduct;
        }
    }

    public FuelConfig(Logger logger) {
        super(logger);
    }

    @Override
    protected FuelData parseEntry(String key, ConfigurationSection s) {
        return new FuelData(
            s.getDouble("temp_gain"),
            s.getDouble("duration_seconds"),
            s.getDouble("heat_rate"),
            s.getString("effect", ""),
            s.getString("byproduct", null)
        );
    }
}
```

- [ ] **Step 2: 创建 IngredientConfig**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.logging.Logger;

public class IngredientConfig extends YamlConfigLoader<IngredientConfig.IngredientData> {

    public static class SauceCreation {
        public final int clicksRequired;

        public SauceCreation(int clicksRequired) {
            this.clicksRequired = clicksRequired;
        }
    }

    public static class IngredientData {
        public final double minTemp;
        public final double maxTemp;
        public final double optimalTempMin;
        public final double optimalTempMax;
        public final double baseCookTimeSeconds;
        public final boolean flipRequired;
        public final List<String> states;
        public final SauceCreation sauceCreation;
        public final String calculatorType;

        public IngredientData(double minTemp, double maxTemp, double optimalTempMin,
                              double optimalTempMax, double baseCookTimeSeconds,
                              boolean flipRequired, List<String> states,
                              SauceCreation sauceCreation, String calculatorType) {
            this.minTemp = minTemp;
            this.maxTemp = maxTemp;
            this.optimalTempMin = optimalTempMin;
            this.optimalTempMax = optimalTempMax;
            this.baseCookTimeSeconds = baseCookTimeSeconds;
            this.flipRequired = flipRequired;
            this.states = states;
            this.sauceCreation = sauceCreation;
            this.calculatorType = calculatorType;
        }
    }

    public IngredientConfig(Logger logger) {
        super(logger);
    }

    @Override
    protected IngredientData parseEntry(String key, ConfigurationSection s) {
        SauceCreation sauce = null;
        if (s.contains("sauce_creation")) {
            sauce = new SauceCreation(s.getInt("sauce_creation.clicks_required", 3));
        }
        return new IngredientData(
            s.getDouble("min_temp"),
            s.getDouble("max_temp"),
            s.getDouble("optimal_temp_min"),
            s.getDouble("optimal_temp_max"),
            s.getDouble("base_cook_time_seconds"),
            s.getBoolean("flip_required", false),
            s.getStringList("states"),
            sauce,
            s.getString("calculator_type", "standard")
        );
    }
}
```

- [ ] **Step 3: 创建 SeasoningConfig**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.config;

import org.bukkit.configuration.ConfigurationSection;

import java.util.logging.Logger;

public class SeasoningConfig extends YamlConfigLoader<SeasoningConfig.SeasoningData> {

    public static class SeasoningData {
        public final String displayName;
        public final boolean hasDoneness;
        public final double minTemp;
        public final double optimalTemp;
        public final double baseTimeSeconds;

        public SeasoningData(String displayName, boolean hasDoneness, double minTemp,
                             double optimalTemp, double baseTimeSeconds) {
            this.displayName = displayName;
            this.hasDoneness = hasDoneness;
            this.minTemp = minTemp;
            this.optimalTemp = optimalTemp;
            this.baseTimeSeconds = baseTimeSeconds;
        }
    }

    public SeasoningConfig(Logger logger) {
        super(logger);
    }

    @Override
    protected SeasoningData parseEntry(String key, ConfigurationSection s) {
        return new SeasoningData(
            s.getString("display_name", key),
            s.getBoolean("has_doneness", false),
            s.getDouble("min_temp", 0),
            s.getDouble("optimal_temp", 100),
            s.getDouble("base_time_seconds", 30)
        );
    }
}
```

- [ ] **Step 4: 创建 fuels.yml**

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

- [ ] **Step 5: 创建 ingredients.yml**

```yaml
BEEF:
  type: MAIN
  states: [WHOLE, SLICED, DICED]
  calculator_type: standard
  min_temp: 60
  max_temp: 240
  optimal_temp_min: 180
  optimal_temp_max: 210
  base_cook_time_seconds: 45
  flip_required: true
TOMATO:
  type: SAUCE_BASE
  states: [WHOLE, SAUCE]
  calculator_type: standard
  sauce_creation:
    clicks_required: 3
  min_temp: 40
  max_temp: 100
  optimal_temp_min: 70
  optimal_temp_max: 90
  base_cook_time_seconds: 30
  flip_required: false
```

- [ ] **Step 6: 创建 seasonings.yml**

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

- [ ] **Step 7: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 8: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/config/ src/main/resources/fuels.yml src/main/resources/ingredients.yml src/main/resources/seasonings.yml; git commit -m "feat(cooking): add YamlConfigLoader base class and config loaders with YAML examples"
```

---

## Task 3.5: DonenessCalculator 策略接口 + StandardDonenessCalculator

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/calculator/DonenessCalculator.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/calculator/StandardDonenessCalculator.java`

- [ ] **Step 1: 创建 DonenessCalculator 接口**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.calculator;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;

public interface DonenessCalculator {
    double calculate(double currentTemp, IngredientConfig.IngredientData config,
                     double deltaTime, boolean hasSpatulaBoost);
}
```

- [ ] **Step 2: 创建 StandardDonenessCalculator 实现**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.calculator;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;

public class StandardDonenessCalculator implements DonenessCalculator {

    @Override
    public double calculate(double currentTemp, IngredientConfig.IngredientData config,
                           double deltaTime, boolean hasSpatulaBoost) {
        if (currentTemp < config.minTemp) return 0;
        
        double coefficient;
        if (currentTemp <= config.optimalTempMax) {
            coefficient = 1.0;
        } else if (currentTemp < config.maxTemp) {
            double range = config.maxTemp - config.optimalTempMax;
            double over = currentTemp - config.optimalTempMax;
            coefficient = 1.0 - 0.5 * (over / range);
        } else {
            coefficient = 1.5;
        }
        
        double boost = hasSpatulaBoost ? 2.0 : 1.0;
        return (1.0 / config.baseCookTimeSeconds) * deltaTime * coefficient * boost;
    }
}
```

- [ ] **Step 3: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 4: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/calculator/; git commit -m "feat(cooking): add DonenessCalculator strategy interface and StandardDonenessCalculator"
```

---

## Task 3.7: StoveInteractionHandler 责任链接口（骨架）

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/StoveInteractionHandler.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/SpatulaInteractionHandler.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/BowlInteractionHandler.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/FuelInteractionHandler.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/SeasoningInteractionHandler.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/IngredientInteractionHandler.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/ClearFuelInteractionHandler.java`

- [ ] **Step 1: 创建 StoveInteractionHandler 接口**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface StoveInteractionHandler {
    boolean handle(Player player, ItemStack handItem, StoveState state, Location location);
}
```

- [ ] **Step 2: 创建所有 Handler 骨架（空实现，返回 false）**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class SpatulaInteractionHandler implements StoveInteractionHandler {
    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        return false;
    }
}
```

（同样结构创建 BowlInteractionHandler, FuelInteractionHandler, SeasoningInteractionHandler, IngredientInteractionHandler, ClearFuelInteractionHandler，均为空实现）

- [ ] **Step 3: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 4: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/; git commit -m "feat(cooking): add StoveInteractionHandler chain of responsibility skeleton"
```

---

## Task 4: StoveTickTask（全局烹饪 tick 逻辑）

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/task/StoveTickTask.java`

- [ ] **Step 1: 创建 StoveTickTask**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.task;

import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.DonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.StandardDonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Iterator;
import java.util.Map;

public class StoveTickTask extends BukkitRunnable {

    private final Map<String, FuelConfig.FuelData> fuels;
    private final Map<String, IngredientConfig.IngredientData> ingredients;
    private final Map<String, SeasoningConfig.SeasoningData> seasonings;
    private final Map<String, DonenessCalculator> calculators;
    private final DonenessCalculator defaultCalculator = new StandardDonenessCalculator();
    private int tickCounter = 0;

    public StoveTickTask(Map<String, FuelConfig.FuelData> fuels,
                         Map<String, IngredientConfig.IngredientData> ingredients,
                         Map<String, SeasoningConfig.SeasoningData> seasonings,
                         Map<String, DonenessCalculator> calculators) {
        this.fuels = fuels;
        this.ingredients = ingredients;
        this.seasonings = seasonings;
        this.calculators = calculators;
    }

    @Override
    public void run() {
        tickCounter += 2;
        for (Map.Entry<Location, StoveState> entry : StoveBlock.activeStoves.entrySet()) {
            StoveState state = entry.getValue();
            tickFuels(state);
            tickTemperature(state);
            if (state.spatulaBoostTicksLeft > 0) {
                state.spatulaBoostTicksLeft -= 2;
            }
            tickIngredients(state);
            tickSeasonings(state);
            if (tickCounter % 5 == 0) {
                io.github.thebusybiscuit.exoticgarden.cooking.hologram.StoveHologram
                    .update(entry.getKey(), state, fuels, ingredients, seasonings);
            }
        }
    }

    private void tickFuels(StoveState state) {
        Iterator<FuelEntry> it = state.fuels.iterator();
        while (it.hasNext()) {
            FuelEntry fuel = it.next();
            fuel.ticksRemaining -= 2;
            if (fuel.ticksRemaining <= 0) {
                FuelConfig.FuelData data = fuels.get(fuel.fuelId);
                if (data != null && data.byproduct != null && !data.byproduct.isEmpty()) {
                    dropByproduct(state, data.byproduct);
                }
                it.remove();
            }
        }
    }

    private void dropByproduct(StoveState state, String byproductId) {
    }

    private void tickTemperature(StoveState state) {
        if (!state.fuels.isEmpty()) {
            double totalHeatRate = 0;
            double maxTemp = 30;
            for (FuelEntry fe : state.fuels) {
                FuelConfig.FuelData data = fuels.get(fe.fuelId);
                if (data != null) {
                    totalHeatRate += data.heatRate;
                    maxTemp += data.tempGain;
                }
            }
            state.currentTemp = Math.min(state.currentTemp + totalHeatRate * 0.1, maxTemp);
        } else {
            double coolRate = Math.max((state.currentTemp + 20) * 0.01, 0.5) - 0.5;
            state.currentTemp = Math.max(state.currentTemp - coolRate, 30.0);
        }
    }

    private void tickIngredients(StoveState state) {
        for (IngredientSlot slot : state.slots) {
            if (slot == null) continue;
            IngredientConfig.IngredientData data = ingredients.get(slot.ingredientId);
            if (data == null) continue;

            DonenessCalculator calculator = calculators.getOrDefault(data.calculatorType, defaultCalculator);
            double increment = calculator.calculate(state.currentTemp, data, 0.1, state.spatulaBoostTicksLeft > 0);

            if (state.currentTemp >= data.maxTemp) {
                slot.charSeconds += 0.1;
            }

            if (slot.state == FoodState.WHOLE) {
                if (slot.currentFace == ActiveFace.FRONT) {
                    slot.frontDoneness = Math.min(slot.frontDoneness + increment, 1.0);
                    slot.backDoneness = Math.min(slot.backDoneness + increment * 0.3, 1.0);
                } else {
                    slot.backDoneness = Math.min(slot.backDoneness + increment, 1.0);
                }
            } else {
                slot.frontDoneness = Math.min(slot.frontDoneness + increment, 1.0);
            }
        }
    }

    private void tickSeasonings(StoveState state) {
        for (SeasoningEntry se : state.seasonings) {
            SeasoningConfig.SeasoningData data = seasonings.get(se.seasoningId);
            if (data == null || !data.hasDoneness) continue;
            if (state.currentTemp < data.minTemp) continue;
            double increment = (1.0 / data.baseTimeSeconds) * 0.1;
            se.progress = Math.min(se.progress + increment, 1.0);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

注意：此时 `StoveBlock` 和 `StoveHologram` 尚未创建，编译会报错。需先创建桩文件：

先在 `StoveBlock.java` 添加最小桩：
```java
package io.github.thebusybiscuit.exoticgarden.cooking.block;

import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StoveBlock {
    public static final Map<Location, StoveState> activeStoves = new ConcurrentHashMap<>();
}
```

先在 `StoveHologram.java` 添加最小桩：
```java
package io.github.thebusybiscuit.exoticgarden.cooking.hologram;

import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import java.util.Map;

public class StoveHologram {
    public static void update(Location loc, StoveState state,
                              Map<String, FuelConfig.FuelData> fuels,
                              Map<String, IngredientConfig.IngredientData> ingredients,
                              Map<String, SeasoningConfig.SeasoningData> seasonings) {
    }

    public static String buildLines(StoveState state,
                                    Map<String, FuelConfig.FuelData> fuels,
                                    Map<String, IngredientConfig.IngredientData> ingredients,
                                    Map<String, SeasoningConfig.SeasoningData> seasonings) {
        return "";
    }
}
```

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 3: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/task/StoveTickTask.java src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/block/StoveBlock.java src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/hologram/StoveHologram.java; git commit -m "feat(cooking): add StoveTickTask with stub StoveBlock and StoveHologram"
```

---

## Task 5: StoveHologram（全息文本拼装）

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/hologram/StoveHologram.java`

- [ ] **Step 1: 完整实现 StoveHologram**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.hologram;

import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
import org.bukkit.Location;

import java.util.Map;
import java.util.StringJoiner;

public class StoveHologram {

    public static void update(Location loc, StoveState state,
                              Map<String, FuelConfig.FuelData> fuels,
                              Map<String, IngredientConfig.IngredientData> ingredients,
                              Map<String, SeasoningConfig.SeasoningData> seasonings) {
    }

    public static String buildLines(StoveState state,
                                    Map<String, FuelConfig.FuelData> fuels,
                                    Map<String, IngredientConfig.IngredientData> ingredients,
                                    Map<String, SeasoningConfig.SeasoningData> seasonings) {
        StringBuilder sb = new StringBuilder();

        double maxTemp = 30;
        double totalRate = 0;
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData fd = fuels.get(fe.fuelId);
            if (fd != null) {
                if (fd.tempGain > maxTemp) maxTemp = fd.tempGain;
                totalRate += fd.heatRate;
            }
        }

        sb.append(String.format("§6[灶台] §e温度: §a%.0f°C §7/ §f%.0f°C\n",
            state.currentTemp, maxTemp));

        if (!state.fuels.isEmpty()) {
            FuelEntry first = state.fuels.get(0);
            FuelConfig.FuelData fd = fuels.get(first.fuelId);
            double secs = first.ticksRemaining / 20.0;
            sb.append(String.format("§b升温: +%.1f°C/s §7(%s %.0fs)\n",
                totalRate, first.fuelId, secs));
        } else {
            sb.append("§7无燃料\n");
        }

        for (int i = 0; i < state.slots.length; i++) {
            IngredientSlot slot = state.slots[i];
            if (slot == null) {
                sb.append(String.format("§e主菜%d: §7无\n", i + 1));
                continue;
            }
            CharLevel charLevel = CharLevel.fromSeconds(slot.charSeconds);
            if (charLevel == CharLevel.SEVERE || charLevel == CharLevel.HEAVY) {
                sb.append(String.format("§e主菜%d: §c%s §c⚠烧焦\n", i + 1, slot.ingredientId));
            } else if (slot.state == FoodState.WHOLE) {
                int front = (int) (slot.frontDoneness * 100);
                int back = (int) (slot.backDoneness * 100);
                sb.append(String.format("§e主菜%d: §a%s[完整] §6正面%d%% 背面%d%%\n",
                    i + 1, slot.ingredientId, front, back));
            } else {
                int pct = (int) (slot.frontDoneness * 100);
                sb.append(String.format("§e主菜%d: §a%s[%s] §e%d%%\n",
                    i + 1, slot.ingredientId, slot.state.name(), pct));
            }
        }

        if (!state.seasonings.isEmpty()) {
            StringJoiner sj = new StringJoiner(", ");
            for (SeasoningEntry se : state.seasonings) {
                SeasoningConfig.SeasoningData sd = seasonings.get(se.seasoningId);
                String name = sd != null ? sd.displayName : se.seasoningId;
                if (sd != null && sd.hasDoneness) {
                    sj.add(name + " " + (int) (se.progress * 100) + "%");
                } else {
                    sj.add(name);
                }
            }
            sb.append("§d辅料: ").append(sj);
        }

        return sb.toString().trim();
    }
}
```

- [ ] **Step 2: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 3: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/hologram/StoveHologram.java; git commit -m "feat(cooking): implement StoveHologram.buildLines"
```

---

## Task 6: DishGenerator（OpenAI 异步调用）

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/ai/DishGenerator.java`

- [ ] **Step 1: 创建 DishGenerator**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class DishGenerator {

    public static class IngredientInfo {
        public final String id;
        public final String state;
        public final double frontDoneness;
        public final double backDoneness;
        public final String charLevel;

        public IngredientInfo(String id, String state, double frontDoneness,
                              double backDoneness, String charLevel) {
            this.id = id;
            this.state = state;
            this.frontDoneness = frontDoneness;
            this.backDoneness = backDoneness;
            this.charLevel = charLevel;
        }
    }

    public static class SeasoningInfo {
        public final String id;
        public final Double progress;

        public SeasoningInfo(String id, Double progress) {
            this.id = id;
            this.progress = progress;
        }
    }

    public static class DishResult {
        public final String name;
        public final int hunger;
        public final double saturation;
        public final String quality;
        public final List<String> effects;
        public final String description;

        public DishResult(String name, int hunger, double saturation, String quality,
                          List<String> effects, String description) {
            this.name = name;
            this.hunger = hunger;
            this.saturation = saturation;
            this.quality = quality;
            this.effects = effects;
            this.description = description;
        }
    }

    public static CompletableFuture<DishResult> generate(
            List<IngredientInfo> ingredientInfos,
            List<SeasoningInfo> seasoningInfos,
            List<String> fuelEffects,
            String apiKey,
            String baseUrl,
            String model) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                Gson gson = new Gson();

                JsonObject userContent = new JsonObject();

                JsonArray ingArr = new JsonArray();
                for (IngredientInfo info : ingredientInfos) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", info.id);
                    obj.addProperty("state", info.state);
                    obj.addProperty("frontDoneness", info.frontDoneness);
                    obj.addProperty("backDoneness", info.backDoneness);
                    obj.addProperty("charLevel", info.charLevel);
                    ingArr.add(obj);
                }
                userContent.add("ingredients", ingArr);

                JsonArray seaArr = new JsonArray();
                for (SeasoningInfo si : seasoningInfos) {
                    JsonObject obj = new JsonObject();
                    obj.addProperty("id", si.id);
                    if (si.progress != null) {
                        obj.addProperty("progress", si.progress);
                    } else {
                        obj.add("progress", com.google.gson.JsonNull.INSTANCE);
                    }
                    seaArr.add(obj);
                }
                userContent.add("seasonings", seaArr);

                JsonArray fxArr = new JsonArray();
                for (String fx : fuelEffects) fxArr.add(fx);
                userContent.add("fuelEffects", fxArr);
                userContent.addProperty("language", "zh-CN");

                JsonObject sysMsg = new JsonObject();
                sysMsg.addProperty("role", "system");
                sysMsg.addProperty("content", "你是一个 Minecraft 烹饪游戏的菜肴生成器。根据食材和烹饪状态，用 JSON 格式返回菜肴信息。严格遵守格式，不输出任何其他内容。");

                JsonObject userMsg = new JsonObject();
                userMsg.addProperty("role", "user");
                userMsg.addProperty("content", gson.toJson(userContent));

                JsonArray messages = new JsonArray();
                messages.add(sysMsg);
                messages.add(userMsg);

                JsonObject body = new JsonObject();
                body.addProperty("model", model);
                body.add("messages", messages);
                body.addProperty("temperature", 0.7);

                URL url = new URL(baseUrl.endsWith("/") ? baseUrl + "chat/completions"
                    : baseUrl + "/chat/completions");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(gson.toJson(body).getBytes(StandardCharsets.UTF_8));
                }

                StringBuilder response = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = br.readLine()) != null) response.append(line);
                }

                JsonObject resp = JsonParser.parseString(response.toString()).getAsJsonObject();
                String content = resp.getAsJsonArray("choices")
                    .get(0).getAsJsonObject()
                    .getAsJsonObject("message")
                    .get("content").getAsString().trim();

                JsonObject dish = JsonParser.parseString(content).getAsJsonObject();
                String name = dish.get("name").getAsString();
                int hunger = dish.get("hunger").getAsInt();
                double saturation = dish.get("saturation").getAsDouble();
                String quality = dish.get("quality").getAsString();
                String description = dish.get("description").getAsString();

                List<String> effects = new java.util.ArrayList<>();
                if (dish.has("effects")) {
                    JsonArray efArr = dish.getAsJsonArray("effects");
                    for (int i = 0; i < efArr.size(); i++) {
                        effects.add(efArr.get(i).getAsString());
                    }
                }

                return new DishResult(name, hunger, saturation, quality, effects, description);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
```

- [ ] **Step 2: 检查 Gson 依赖**

查看 `pom.xml` 是否包含 Gson。Spigot API 本身通过 Bukkit 依赖了 Gson（`com.google.code.gson:gson`），因此 provided scope 下可用，无需额外添加依赖。

- [ ] **Step 3: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 4: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/ai/DishGenerator.java; git commit -m "feat(cooking): add DishGenerator with OpenAI-compatible API async call"
```

---

## Task 7: StoveBlock 责任链实现 + 各 Handler 填充（灶台交互）

**Files:**
- Modify: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/block/StoveBlock.java`
- Modify: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/SpatulaInteractionHandler.java`
- Modify: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/BowlInteractionHandler.java`
- Modify: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/FuelInteractionHandler.java`
- Modify: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/SeasoningInteractionHandler.java`
- Modify: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/IngredientInteractionHandler.java`
- Modify: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/ClearFuelInteractionHandler.java`

- [ ] **Step 1: 填充 SpatulaInteractionHandler**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.state.ActiveFace;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.exoticgarden.cooking.state.IngredientSlot;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class SpatulaInteractionHandler implements StoveInteractionHandler {

    private static final NamespacedKey KEY_ITEM_TYPE = new NamespacedKey("cooking", "item_type");

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() == Material.AIR || handItem.getItemMeta() == null) return false;
        PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
        if (!"SPATULA".equals(pdc.get(KEY_ITEM_TYPE, PersistentDataType.STRING))) return false;

        state.pendingFuelClear = false;
        boolean flipped = false;
        for (IngredientSlot slot : state.slots) {
            if (slot == null) continue;
            if (slot.state == FoodState.WHOLE && slot.frontDoneness >= 0.5
                    && slot.currentFace == ActiveFace.FRONT) {
                slot.currentFace = ActiveFace.BACK;
                flipped = true;
            }
        }
        state.spatulaBoostTicksLeft = Math.max(state.spatulaBoostTicksLeft, 200);
        if (flipped) player.sendMessage("§a已翻面！烹饪加速中...");
        return true;
    }
}
```

- [ ] **Step 2: 填充 BowlInteractionHandler（取出成品）**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.ai.DishGenerator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class BowlInteractionHandler implements StoveInteractionHandler {

    private static final NamespacedKey KEY_DISH_NAME = new NamespacedKey("cooking", "dish_name");
    private static final NamespacedKey KEY_DISH_HUNGER = new NamespacedKey("cooking", "dish_hunger");
    private static final NamespacedKey KEY_DISH_SATURATION = new NamespacedKey("cooking", "dish_saturation");
    private static final NamespacedKey KEY_DISH_QUALITY = new NamespacedKey("cooking", "dish_quality");
    private static final NamespacedKey KEY_DISH_DESCRIPTION = new NamespacedKey("cooking", "dish_description");

    private final JavaPlugin plugin;
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    private final Map<String, FuelConfig.FuelData> fuels;
    private final Map<String, IngredientConfig.IngredientData> ingredients;
    private final Map<String, SeasoningConfig.SeasoningData> seasonings;

    public BowlInteractionHandler(JavaPlugin plugin,
                                  Map<String, FuelConfig.FuelData> fuels,
                                  Map<String, IngredientConfig.IngredientData> ingredients,
                                  Map<String, SeasoningConfig.SeasoningData> seasonings,
                                  String apiKey, String baseUrl, String model) {
        this.plugin = plugin;
        this.fuels = fuels;
        this.ingredients = ingredients;
        this.seasonings = seasonings;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.model = model;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() != Material.BOWL) return false;
        if (handItem.getItemMeta() != null) {
            PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
            if (pdc.has(new NamespacedKey("slimefun", "slimefun_item"), PersistentDataType.STRING))
                return false;
        }

        state.pendingFuelClear = false;

        List<DishGenerator.IngredientInfo> ingInfos = new ArrayList<>();
        List<DishGenerator.SeasoningInfo> seaInfos = new ArrayList<>();
        List<String> fxList = new ArrayList<>();

        for (IngredientSlot slot : state.slots) {
            if (slot == null) continue;
            ingInfos.add(new DishGenerator.IngredientInfo(
                slot.ingredientId, slot.state.name(),
                slot.frontDoneness, slot.backDoneness,
                CharLevel.fromSeconds(slot.charSeconds).name()));
        }
        for (SeasoningEntry se : state.seasonings) {
            SeasoningConfig.SeasoningData sd = seasonings.get(se.seasoningId);
            seaInfos.add(new DishGenerator.SeasoningInfo(
                se.seasoningId,
                sd != null && sd.hasDoneness ? se.progress : null));
        }
        for (FuelEntry fe : state.fuels) {
            FuelConfig.FuelData fd = fuels.get(fe.fuelId);
            if (fd != null && fd.effect != null && !fd.effect.isEmpty()) fxList.add(fd.effect);
        }

        Arrays.fill(state.slots, null);
        state.seasonings.clear();

        player.sendMessage("§e烹饪中...");

        DishGenerator.generate(ingInfos, seaInfos, fxList, apiKey, baseUrl, model)
            .thenAccept(result -> plugin.getServer().getScheduler().runTask(plugin, () -> {
                ItemStack dish = buildDishItem(result);
                player.getInventory().addItem(dish);
                player.sendMessage("§a烹饪完成：" + result.name);
            }))
            .exceptionally(ex -> {
                plugin.getServer().getScheduler().runTask(plugin, () ->
                    player.sendMessage("§c烹饪失败，请稍后再试"));
                return null;
            });
        return true;
    }

    private ItemStack buildDishItem(DishGenerator.DishResult result) {
        ItemStack item = new ItemStack(Material.MUSHROOM_STEW, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(result.name);
        List<String> lore = new ArrayList<>();
        lore.add("§7品质: §a" + result.quality);
        lore.add("§7描述: " + result.description);
        lore.add("§7饥饿值: §e+" + result.hunger);
        meta.setLore(lore);
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_DISH_NAME, PersistentDataType.STRING, result.name);
        pdc.set(KEY_DISH_HUNGER, PersistentDataType.INTEGER, result.hunger);
        pdc.set(KEY_DISH_SATURATION, PersistentDataType.DOUBLE, result.saturation);
        pdc.set(KEY_DISH_QUALITY, PersistentDataType.STRING, result.quality);
        pdc.set(KEY_DISH_DESCRIPTION, PersistentDataType.STRING, result.description);
        item.setItemMeta(meta);
        return item;
    }
}
```

- [ ] **Step 3: 填充 FuelInteractionHandler**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FuelEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class FuelInteractionHandler implements StoveInteractionHandler {

    private static final NamespacedKey KEY_FUEL_ID = new NamespacedKey("cooking", "fuel_id");
    private final Map<String, FuelConfig.FuelData> fuels;

    public FuelInteractionHandler(Map<String, FuelConfig.FuelData> fuels) {
        this.fuels = fuels;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() == Material.AIR) return false;
        String fuelId = resolve(handItem);
        if (fuelId == null) return false;

        state.pendingFuelClear = false;
        if (state.fuels.size() >= 2) {
            player.sendMessage("§c燃料槽已满（最多2格）");
            return true;
        }
        FuelConfig.FuelData fd = fuels.get(fuelId);
        state.fuels.add(new FuelEntry(fuelId, fd.durationSeconds * 20));
        handItem.setAmount(handItem.getAmount() - 1);
        return true;
    }

    private String resolve(ItemStack item) {
        if (item.getItemMeta() != null) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String id = pdc.get(KEY_FUEL_ID, PersistentDataType.STRING);
            if (id != null && fuels.containsKey(id)) return id;
        }
        String matName = item.getType().name();
        if (fuels.containsKey(matName)) return matName;
        return null;
    }
}
```

- [ ] **Step 4: 填充 SeasoningInteractionHandler**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.SeasoningEntry;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class SeasoningInteractionHandler implements StoveInteractionHandler {

    private static final NamespacedKey KEY_SEASONING_ID = new NamespacedKey("cooking", "seasoning_id");
    private final Map<String, SeasoningConfig.SeasoningData> seasonings;

    public SeasoningInteractionHandler(Map<String, SeasoningConfig.SeasoningData> seasonings) {
        this.seasonings = seasonings;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() == Material.AIR) return false;
        String seasoningId = resolve(handItem);
        if (seasoningId == null) return false;

        state.pendingFuelClear = false;
        if (state.seasonings.size() >= 10) {
            player.sendMessage("§c调料槽已满（最多10种）");
            return true;
        }
        state.seasonings.add(new SeasoningEntry(seasoningId, 0));
        handItem.setAmount(handItem.getAmount() - 1);
        return true;
    }

    private String resolve(ItemStack item) {
        if (item.getItemMeta() != null) {
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String id = pdc.get(KEY_SEASONING_ID, PersistentDataType.STRING);
            if (id != null && seasonings.containsKey(id)) return id;
        }
        String matName = item.getType().name();
        if (seasonings.containsKey(matName)) return matName;
        return null;
    }
}
```

- [ ] **Step 5: 填充 IngredientInteractionHandler**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.ActiveFace;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.exoticgarden.cooking.state.IngredientSlot;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;

public class IngredientInteractionHandler implements StoveInteractionHandler {

    private static final NamespacedKey KEY_INGREDIENT_ID = new NamespacedKey("cooking", "ingredient_id");
    private static final NamespacedKey KEY_FOOD_STATE = new NamespacedKey("cooking", "food_state");
    private final Map<String, IngredientConfig.IngredientData> ingredients;

    public IngredientInteractionHandler(Map<String, IngredientConfig.IngredientData> ingredients) {
        this.ingredients = ingredients;
    }

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() == Material.AIR || handItem.getItemMeta() == null) return false;
        PersistentDataContainer pdc = handItem.getItemMeta().getPersistentDataContainer();
        String ingId = pdc.get(KEY_INGREDIENT_ID, PersistentDataType.STRING);
        if (ingId == null || !ingredients.containsKey(ingId)) return false;

        state.pendingFuelClear = false;
        int emptySlot = -1;
        for (int i = 0; i < state.slots.length; i++) {
            if (state.slots[i] == null) { emptySlot = i; break; }
        }
        if (emptySlot == -1) {
            player.sendMessage("§c食材槽已满（最多4格）");
            return true;
        }
        String rawState = pdc.get(KEY_FOOD_STATE, PersistentDataType.STRING);
        FoodState foodState = FoodState.WHOLE;
        if (rawState != null) {
            try { foodState = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
        }
        state.slots[emptySlot] = new IngredientSlot(ingId, foodState, 0, 0, ActiveFace.FRONT, 0);
        handItem.setAmount(handItem.getAmount() - 1);
        return true;
    }
}
```

- [ ] **Step 6: 填充 ClearFuelInteractionHandler**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.interaction;

import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ClearFuelInteractionHandler implements StoveInteractionHandler {

    @Override
    public boolean handle(Player player, ItemStack handItem, StoveState state, Location location) {
        if (handItem.getType() != Material.AIR || !player.isSneaking()) return false;

        if (state.pendingFuelClear) {
            state.fuels.clear();
            state.pendingFuelClear = false;
            player.sendMessage("§a已清除所有燃料");
        } else {
            state.pendingFuelClear = true;
            player.sendMessage("§e再次潜行右键确认清除燃料");
        }
        return true;
    }
}
```

- [ ] **Step 7: 重写 StoveBlock 使用责任链**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.block;

import io.github.thebusybiscuit.exoticgarden.cooking.interaction.StoveInteractionHandler;
import io.github.thebusybiscuit.exoticgarden.cooking.state.StoveState;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class StoveBlock extends SlimefunItem {

    public static final Map<Location, StoveState> activeStoves = new ConcurrentHashMap<>();

    private final List<StoveInteractionHandler> handlers;

    public StoveBlock(ItemGroup group, SlimefunItemStack item, RecipeType recipeType,
                      ItemStack[] recipe, List<StoveInteractionHandler> handlers) {
        super(group, item, recipeType, recipe);
        this.handlers = handlers;
        addItemHandler(buildUseHandler(), buildBreakHandler());
    }

    private BlockUseHandler buildUseHandler() {
        return (PlayerRightClickEvent e) -> {
            e.cancel();
            Player player = e.getPlayer();
            Location loc = e.getClickedBlock().get().getLocation();
            StoveState state = activeStoves.computeIfAbsent(loc, k -> new StoveState());
            ItemStack hand = player.getInventory().getItemInMainHand();

            boolean handled = false;
            for (StoveInteractionHandler handler : handlers) {
                if (handler.handle(player, hand, state, loc)) {
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                state.pendingFuelClear = false;
            }
        };
    }

    private BlockBreakHandler buildBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull org.bukkit.event.block.BlockBreakEvent e,
                                      @Nonnull Player player,
                                      @Nonnull List<ItemStack> drops) {
                activeStoves.remove(e.getBlock().getLocation());
            }
        };
    }
}
```

- [ ] **Step 8: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

注意：`BowlInteractionHandler` 依赖 `DishGenerator`（Task 6）、`StoveBlock` 不再持有 `fuels / ingredients / seasonings / plugin / apiKey` 等字段。

- [ ] **Step 9: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/block/StoveBlock.java src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/interaction/; git commit -m "feat(cooking): refactor StoveBlock to chain-of-responsibility, fill all interaction handlers"
```

---

## Task 8: CuttingBoardBlock（砧板方块）

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/block/CuttingBoardBlock.java`

- [ ] **Step 1: 创建 CuttingBoardBlock**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.block;

import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CuttingBoardBlock extends SlimefunItem {

    public static final Map<Location, Entity> boardDisplays = new ConcurrentHashMap<>();

    private static final NamespacedKey KEY_BOARD_ITEM = new NamespacedKey("cooking", "board_item");

    public CuttingBoardBlock(ItemGroup group, SlimefunItemStack item,
                             RecipeType recipeType, ItemStack[] recipe) {
        super(group, item, recipeType, recipe);
        addItemHandler(buildUseHandler(), buildBreakHandler());
    }

    private BlockUseHandler buildUseHandler() {
        return (PlayerRightClickEvent e) -> {
            e.cancel();
            Player player = e.getPlayer();
            Location loc = e.getClickedBlock().get().getLocation();
            Entity display = boardDisplays.get(loc);

            if (!player.isSneaking()) {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() == Material.AIR) return;

                if (display == null) {
                    display = spawnDisplay(loc, hand.clone());
                    boardDisplays.put(loc, display);
                    hand.setAmount(hand.getAmount() - 1);
                } else {
                    player.sendMessage("§c砧板上已有物品，请潜行右键取回");
                }
            } else {
                if (display != null) {
                    ItemStack stored = getStoredItem(display);
                    if (stored != null) {
                        player.getInventory().addItem(stored);
                    }
                    display.remove();
                    boardDisplays.remove(loc);
                }
            }
        };
    }

    private Entity spawnDisplay(Location loc, ItemStack item) {
        Location spawnLoc = loc.clone().add(0.5, 1.0, 0.5);
        ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        stand.setVisible(false);
        stand.setGravity(false);
        stand.setInvulnerable(true);
        stand.setSmall(true);
        stand.getEquipment().setHelmet(item);
        PersistentDataContainer pdc = stand.getPersistentDataContainer();
        pdc.set(KEY_BOARD_ITEM, PersistentDataType.STRING, "true");
        return stand;
    }

    private ItemStack getStoredItem(Entity display) {
        if (display instanceof ArmorStand stand) {
            return stand.getEquipment().getHelmet();
        }
        return null;
    }

    private BlockBreakHandler buildBreakHandler() {
        return new BlockBreakHandler(false, false) {
            @Override
            public void onPlayerBreak(@Nonnull org.bukkit.event.block.BlockBreakEvent e,
                                      @Nonnull Player player,
                                      @Nonnull List<ItemStack> drops) {
                Location loc = e.getBlock().getLocation();
                Entity display = boardDisplays.remove(loc);
                if (display != null) {
                    ItemStack stored = getStoredItem(display);
                    if (stored != null) {
                        loc.getWorld().dropItemNaturally(loc, stored);
                    }
                    display.remove();
                }
            }
        };
    }
}
```

- [ ] **Step 2: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 3: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/block/CuttingBoardBlock.java; git commit -m "feat(cooking): implement CuttingBoardBlock with ArmorStand display"
```

---

## Task 9: KnifeItem + SpatulaItem（工具物品）

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/item/KnifeItem.java`
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/item/SpatulaItem.java`

- [ ] **Step 1: 创建 KnifeItem**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.item;

import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public class KnifeItem extends SlimefunItem {

    private static final NamespacedKey KEY_FOOD_STATE = new NamespacedKey("cooking", "food_state");

    public KnifeItem(ItemGroup group, SlimefunItemStack item,
                     RecipeType recipeType, ItemStack[] recipe, JavaPlugin plugin) {
        super(group, item, recipeType, recipe);

        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onInteract(PlayerInteractAtEntityEvent event) {
                if (event.getHand() != EquipmentSlot.HAND) return;
                Entity target = event.getRightClicked();
                if (!CuttingBoardBlock.boardDisplays.containsValue(target)) return;

                Player player = event.getPlayer();
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getItemMeta() == null) return;
                PersistentDataContainer handPdc = hand.getItemMeta().getPersistentDataContainer();
                NamespacedKey typeKey = new NamespacedKey("cooking", "item_type");
                if (!"KNIFE".equals(handPdc.get(typeKey, PersistentDataType.STRING))) return;

                event.setCancelled(true);

                if (!(target instanceof ArmorStand stand)) return;
                ItemStack held = stand.getEquipment().getHelmet();
                if (held == null || held.getType().isAir()) return;

                if (player.isSneaking()) {
                    player.getInventory().addItem(held.clone());
                    stand.getEquipment().setHelmet(null);
                    Location boardLoc = findBoardLoc(target);
                    if (boardLoc != null) CuttingBoardBlock.boardDisplays.remove(boardLoc);
                    target.remove();
                    return;
                }

                org.bukkit.inventory.meta.ItemMeta heldMeta = held.getItemMeta();
                if (heldMeta == null) return;
                PersistentDataContainer heldPdc = heldMeta.getPersistentDataContainer();
                String rawState = heldPdc.get(KEY_FOOD_STATE, PersistentDataType.STRING);
                FoodState current = FoodState.WHOLE;
                if (rawState != null) {
                    try { current = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
                }
                FoodState next = advanceState(current);
                heldPdc.set(KEY_FOOD_STATE, PersistentDataType.STRING, next.name());
                held.setItemMeta(heldMeta);
                stand.getEquipment().setHelmet(held);
                player.sendMessage("§a食材状态: " + next.name());
            }
        }, plugin);
    }

    private FoodState advanceState(FoodState current) {
        return switch (current) {
            case WHOLE -> FoodState.SLICED;
            case SLICED -> FoodState.DICED;
            default -> current;
        };
    }

    private Location findBoardLoc(Entity entity) {
        for (Map.Entry<Location, Entity> entry : CuttingBoardBlock.boardDisplays.entrySet()) {
            if (entry.getValue().equals(entity)) return entry.getKey();
        }
        return null;
    }
}
```

- [ ] **Step 2: 修复 KnifeItem 中缺少的 import**

在 KnifeItem 顶部添加：
```java
import java.util.Map;
```

- [ ] **Step 3: 创建 SpatulaItem**

```java
package io.github.thebusybiscuit.exoticgarden.cooking.item;

import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.state.FoodState;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;

public class SpatulaItem extends SlimefunItem {

    private static final NamespacedKey KEY_FOOD_STATE = new NamespacedKey("cooking", "food_state");
    private static final NamespacedKey KEY_SPATULA_CLICKS = new NamespacedKey("cooking", "spatula_clicks");

    private final Map<String, IngredientConfig.IngredientData> ingredients;

    public SpatulaItem(ItemGroup group, SlimefunItemStack item,
                       RecipeType recipeType, ItemStack[] recipe,
                       JavaPlugin plugin,
                       Map<String, IngredientConfig.IngredientData> ingredients) {
        super(group, item, recipeType, recipe);
        this.ingredients = ingredients;

        plugin.getServer().getPluginManager().registerEvents(new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onInteract(PlayerInteractAtEntityEvent event) {
                if (event.getHand() != EquipmentSlot.HAND) return;
                Entity target = event.getRightClicked();
                if (!CuttingBoardBlock.boardDisplays.containsValue(target)) return;

                Player player = event.getPlayer();
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getItemMeta() == null) return;
                PersistentDataContainer handPdc = hand.getItemMeta().getPersistentDataContainer();
                NamespacedKey typeKey = new NamespacedKey("cooking", "item_type");
                if (!"SPATULA".equals(handPdc.get(typeKey, PersistentDataType.STRING))) return;

                event.setCancelled(true);

                if (!(target instanceof ArmorStand stand)) return;
                ItemStack held = stand.getEquipment().getHelmet();
                if (held == null || held.getType().isAir()) return;

                org.bukkit.inventory.meta.ItemMeta heldMeta = held.getItemMeta();
                if (heldMeta == null) return;
                PersistentDataContainer heldPdc = heldMeta.getPersistentDataContainer();

                String rawState = heldPdc.get(KEY_FOOD_STATE, PersistentDataType.STRING);
                FoodState current = FoodState.WHOLE;
                if (rawState != null) {
                    try { current = FoodState.valueOf(rawState); } catch (IllegalArgumentException ignored) {}
                }
                if (current != FoodState.WHOLE) return;

                String ingId = heldPdc.get(new NamespacedKey("cooking", "ingredient_id"),
                    PersistentDataType.STRING);
                if (ingId == null) return;
                IngredientConfig.IngredientData data = ingredients.get(ingId);
                if (data == null || data.sauceCreation == null) return;

                int clicks = heldPdc.getOrDefault(KEY_SPATULA_CLICKS, PersistentDataType.INTEGER, 0) + 1;
                if (clicks >= data.sauceCreation.clicksRequired) {
                    heldPdc.set(KEY_FOOD_STATE, PersistentDataType.STRING, FoodState.SAUCE.name());
                    heldPdc.remove(KEY_SPATULA_CLICKS);
                    player.sendMessage("§a已制成酱料！");
                } else {
                    heldPdc.set(KEY_SPATULA_CLICKS, PersistentDataType.INTEGER, clicks);
                    player.sendMessage("§e搅拌中: " + clicks + "/" + data.sauceCreation.clicksRequired);
                }
                held.setItemMeta(heldMeta);
                stand.getEquipment().setHelmet(held);
            }
        }, plugin);
    }
}
```

- [ ] **Step 4: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 5: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/item/KnifeItem.java src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/item/SpatulaItem.java; git commit -m "feat(cooking): add KnifeItem and SpatulaItem with entity interact logic"
```

---

## Task 10: CookingModule + 修改 ExoticGarden.onEnable + 修改 config.yml

**Files:**
- Create: `src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/CookingModule.java`
- Modify: `src/main/java/io/github/thebusybiscuit/exoticgarden/ExoticGarden.java`
- Modify: `src/main/resources/config.yml`

- [ ] **Step 1: 创建 CookingModule**

```java
package io.github.thebusybiscuit.exoticgarden.cooking;

import io.github.thebusybiscuit.exoticgarden.ExoticGarden;
import io.github.thebusybiscuit.exoticgarden.cooking.block.CuttingBoardBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.block.StoveBlock;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.DonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.calculator.StandardDonenessCalculator;
import io.github.thebusybiscuit.exoticgarden.cooking.config.FuelConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.IngredientConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.config.SeasoningConfig;
import io.github.thebusybiscuit.exoticgarden.cooking.interaction.*;
import io.github.thebusybiscuit.exoticgarden.cooking.item.KnifeItem;
import io.github.thebusybiscuit.exoticgarden.cooking.item.SpatulaItem;
import io.github.thebusybiscuit.exoticgarden.cooking.task.StoveTickTask;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class CookingModule {

    public static void initialize(ExoticGarden plugin) {
        Logger logger = plugin.getLogger();

        FuelConfig fuelConfig = new FuelConfig(logger);
        IngredientConfig ingredientConfig = new IngredientConfig(logger);
        SeasoningConfig seasoningConfig = new SeasoningConfig(logger);

        Map<String, FuelConfig.FuelData> fuels = fuelConfig.loadAll(
            new File(plugin.getDataFolder(), "fuels.yml"), "");
        Map<String, IngredientConfig.IngredientData> ingredients = ingredientConfig.loadAll(
            new File(plugin.getDataFolder(), "ingredients.yml"), "");
        Map<String, SeasoningConfig.SeasoningData> seasonings = seasoningConfig.loadAll(
            new File(plugin.getDataFolder(), "seasonings.yml"), "");

        saveResourceIfMissing(plugin, "fuels.yml");
        saveResourceIfMissing(plugin, "ingredients.yml");
        saveResourceIfMissing(plugin, "seasonings.yml");

        String apiKey = plugin.getConfig().getString("cooking.ai.api-key", "");
        String baseUrl = plugin.getConfig().getString("cooking.ai.base-url", "https://api.openai.com/v1");
        String model = plugin.getConfig().getString("cooking.ai.model", "gpt-4o-mini");

        Map<String, DonenessCalculator> calculators = new HashMap<>();
        calculators.put("standard", new StandardDonenessCalculator());

        List<StoveInteractionHandler> stoveHandlers = List.of(
            new SpatulaInteractionHandler(),
            new BowlInteractionHandler(plugin, fuels, ingredients, seasonings, apiKey, baseUrl, model),
            new FuelInteractionHandler(fuels),
            new SeasoningInteractionHandler(seasonings),
            new IngredientInteractionHandler(ingredients),
            new ClearFuelInteractionHandler()
        );

        ItemGroup cookingGroup = new ItemGroup(
            new NamespacedKey(plugin, "cooking"),
            new CustomItemStack(Material.CAMPFIRE, "&6烹饪系统")
        );

        SlimefunItemStack stoveStack = new SlimefunItemStack(
            "EG_COOKING_STOVE",
            Material.BLAST_FURNACE,
            "&6烹饪灶台",
            "&7放置燃料和食材进行烹饪",
            "&7右键交互以操作"
        );
        StoveBlock stove = new StoveBlock(
            cookingGroup, stoveStack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                new ItemStack(Material.COBBLESTONE), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COBBLESTONE),
                new ItemStack(Material.IRON_INGOT), new ItemStack(Material.CAMPFIRE), new ItemStack(Material.IRON_INGOT),
                new ItemStack(Material.COBBLESTONE), new ItemStack(Material.IRON_INGOT), new ItemStack(Material.COBBLESTONE)
            },
            stoveHandlers
        );
        stove.register(plugin);

        SlimefunItemStack boardStack = new SlimefunItemStack(
            "EG_CUTTING_BOARD",
            Material.OAK_SLAB,
            "&e砧板",
            "&7放置食材，使用刀具切割",
            "&7潜行右键取回物品"
        );
        CuttingBoardBlock board = new CuttingBoardBlock(
            cookingGroup, boardStack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                null, null, null,
                new ItemStack(Material.OAK_SLAB), new ItemStack(Material.OAK_SLAB), new ItemStack(Material.OAK_SLAB),
                null, null, null
            }
        );
        board.register(plugin);

        SlimefunItemStack knifeStack = new SlimefunItemStack(
            "EG_COOKING_KNIFE",
            Material.IRON_SWORD,
            "&f烹饪刀",
            "&7右键砧板上的食材进行切割",
            "&7潜行右键取回食材"
        );
        ItemMeta knifeMeta = knifeStack.getItemMeta();
        if (knifeMeta != null) {
            knifeMeta.getPersistentDataContainer()
                .set(new NamespacedKey("cooking", "item_type"),
                     PersistentDataType.STRING, "KNIFE");
            knifeStack.setItemMeta(knifeMeta);
        }
        KnifeItem knife = new KnifeItem(
            cookingGroup, knifeStack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.STICK), null
            },
            plugin
        );
        knife.register(plugin);

        SlimefunItemStack spatulaStack = new SlimefunItemStack(
            "EG_COOKING_SPATULA",
            Material.IRON_SHOVEL,
            "&b烹饪锅铲",
            "&7右键灶台翻面，加速烹饪",
            "&7右键砧板搅拌制酱"
        );
        ItemMeta spatulaMeta = spatulaStack.getItemMeta();
        if (spatulaMeta != null) {
            spatulaMeta.getPersistentDataContainer()
                .set(new NamespacedKey("cooking", "item_type"),
                     PersistentDataType.STRING, "SPATULA");
            spatulaStack.setItemMeta(spatulaMeta);
        }
        SpatulaItem spatula = new SpatulaItem(
            cookingGroup, spatulaStack, RecipeType.ENHANCED_CRAFTING_TABLE,
            new ItemStack[] {
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.IRON_INGOT), null,
                null, new ItemStack(Material.STICK), null
            },
            plugin, ingredients
        );
        spatula.register(plugin);

        new StoveTickTask(fuels, ingredients, seasonings, calculators)
            .runTaskTimer(plugin, 2L, 2L);
    }

    private static void saveResourceIfMissing(ExoticGarden plugin, String name) {
        File file = new File(plugin.getDataFolder(), name);
        if (file.exists()) return;
        try (InputStream in = plugin.getResource(name)) {
            if (in != null) Files.copy(in, file.toPath());
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save resource: " + name);
        }
    }
}
```

- [ ] **Step 2: 修改 ExoticGarden.onEnable，在末尾（cfg.save() 之后、调度器之前）插入**

在 [ExoticGarden.java](file:///d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex\src\main\java\io\github\thebusybiscuit\exoticgarden\ExoticGarden.java) 中找到 `cfg.save();` 所在位置（约第272行），在其后、`getServer().getScheduler()...` 之前添加：

```java
import io.github.thebusybiscuit.exoticgarden.cooking.CookingModule;
```

（添加到文件顶部 import 区域）

在 `cfg.save();` 之后插入：
```java
        CookingModule.initialize(this);
```

- [ ] **Step 3: 修改 config.yml，新增 cooking 段落**

在 [config.yml](file:///d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex\src\main\resources\config.yml) 末尾追加：

```yaml
cooking:
  ai:
    api-key: "your-api-key-here"
    base-url: "https://api.openai.com/v1"
    model: "gpt-4o-mini"
```

- [ ] **Step 4: 编译验证**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; mvn compile -q
```

预期：BUILD SUCCESS

- [ ] **Step 5: Commit**

```powershell
cd "d:\Users\Administrator\Desktop\Java项目\slimefun\ExoticGardenComplex"; git add src/main/java/io/github/thebusybiscuit/exoticgarden/cooking/CookingModule.java src/main/java/io/github/thebusybiscuit/exoticgarden/ExoticGarden.java src/main/resources/config.yml; git commit -m "feat(cooking): add CookingModule, wire into ExoticGarden.onEnable, add cooking config"
```

---

## 自检清单

| 检查项 | 状态 |
|--------|------|
| FoodState.WHOLE/SLICED/DICED/SAUCE 覆盖 | ✅ |
| CharLevel.fromSeconds 阈值 <10/<20/<40/≥40 | ✅ |
| StoveState 最多2燃料/4食材/10调料 | ✅ |
| StoveTickTask 执行顺序（燃料→温度→spatulaBoost→食材→调料→全息） | ✅ |
| 成熟度系数逻辑（<minTemp→0, ≤optMax→1.0, <maxTemp→线性, ≥maxTemp→1.5+charSeconds） | ✅ |
| WHOLE+FRONT → frontDoneness+increment, backDoneness+increment×0.3 | ✅ |
| WHOLE+BACK → backDoneness+increment | ✅ |
| 非WHOLE → frontDoneness+increment | ✅ |
| 右键分发优先级（责任链: Spatula→Bowl→Fuel→Seasoning→Ingredient→ClearFuel） | ✅ |
| pendingFuelClear 二次确认逻辑，任何非空手潜行操作重置 | ✅ |
| 取出成品：清空slots+seasonings（燃料不变），异步AI，切回主线程给物品 | ✅ |
| CuttingBoardBlock ArmorStand 放置在上方 0.5+0.5=1.0 格（加0.5到中心+1.0高度） | ✅ |
| KnifeItem 潜行右键取回，普通右键 WHOLE→SLICED→DICED | ✅ |
| SpatulaItem 仅处理 WHOLE，sauce_creation 存在时累计 spatula_clicks | ✅ |
| PDC Key 命名空间一致（均为 `cooking`） | ✅ |
| DishGenerator prompt 结构与规格一致 | ✅ |
| 菜肴 ItemStack Material.MUSHROOM_STEW，amount=1 | ✅ |
| 全息格式覆盖：SEVERE/HEAVY烧焦、WHOLE双面%、非WHOLE单%、无食材 | ✅ |
| CookingModule 注册顺序：ItemGroup→StoveBlock→CuttingBoardBlock→KnifeItem→SpatulaItem→Task | ✅ |
| Config 实例化调用：new FuelConfig(logger).loadAll(file, "") | ✅ |
| calculators Map 构建 + 传入 StoveTickTask | ✅ |
| handlers List 构建 + 传入 StoveBlock | ✅ |
| StoveBlock.buildUseHandler 使用责任链遍历 handlers | ✅ |
| tickTemperature maxTemp 为累加值（非取最大值） | ✅ |
| 所有 PowerShell commit 命令使用分号`;`不使用`&&` | ✅ |

---

## 注意事项

1. **Gson 可用性**：Spigot API 1.19.2 通过传递依赖包含了 `com.google.gson:gson`，在 provided scope 下可直接 import，无需在 pom.xml 中添加。

2. **SlimefunItemStack PDC 写入**：已使用 null-safe 模式（`ItemMeta m = stack.getItemMeta(); if (m != null) { m.getPDC().set(...); stack.setItemMeta(m); }`），见 Step 1 CookingModule 代码。

3. **PlayerInteractAtEntityEvent 重复注册**：`KnifeItem` 和 `SpatulaItem` 各自在构造器内注册 Listener，若插件重载会重复注册。生产环境中建议将 Listener 抽为独立类，在 `CookingModule.initialize` 时只注册一次。

4. **全息显示**：`StoveHologram.update` 目前为空方法，实际部署时需结合服务器端全息库（如 DecentHolograms、FancyHolograms 等）或使用 ArmorStand/TextDisplay 实体实现文字显示。
