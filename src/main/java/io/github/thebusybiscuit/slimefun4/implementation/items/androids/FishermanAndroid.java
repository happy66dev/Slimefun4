package io.github.thebusybiscuit.slimefun4.implementation.items.androids;

import city.norain.slimefun4.api.menu.UniversalMenu;
import io.github.bakedlibs.dough.collections.RandomizedSet;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/**
 * 钓鱼机器人（FishermanAndroid）喵~
 *
 * <p>整体思路：
 * 这是一种特化型可编程机器人，专门负责自动钓鱼喵~
 * 机器人放置后，每次 tick 会检查正下方方块是否为水，
 * 若是水则按「等级×10%」的概率从预设战利品池中随机产出一件物品并推入输出格喵~
 *
 * <p>战利品池权重说明：
 * - 鱼类（所有Tag.ITEMS_FISHES中的鱼）：权重25，是最常见的产出喵~
 * - 垃圾（骨头/线/墨囊等）：权重2~10，偶尔会捞到没用的东西喵~
 * - 稀有战利品（马鞍/命名牌/鹦鹉螺壳）：权重1，非常难得喵~
 *
 * <p>输入：安装了钓鱼程序的机器人方块，下方必须有水喵~
 * <p>输出：随机一件战利品放入机器人的输出槽喵~
 * <p>边界条件：下方不是水时不会产出任何物品喵~
 */
public class FishermanAndroid extends ProgrammableAndroid {

    // 钓鱼战利品随机池，存储所有可能钓到的物品及其对应的权重喵~
    private final RandomizedSet<ItemStack> fishingLoot = new RandomizedSet<>();

    /**
     * 构造方法：创建一个钓鱼机器人实例并初始化战利品池喵~
     *
     * <p>整体思路：
     * 调用父类构造完成基本注册后，将所有可钓物品按权重加入 fishingLoot 随机池。
     * 权重越高的物品被随机选中的概率越大喵~
     *
     * <p>输入：物品组、等级、物品栈、合成类型、合成配方喵~
     * <p>输出：初始化完成的 FishermanAndroid 实例喵~
     */
    @ParametersAreNonnullByDefault
    public FishermanAndroid(
            ItemGroup itemGroup, int tier, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        // 调用父类 ProgrammableAndroid 的构造方法，完成基础注册和等级设置喵~
        super(itemGroup, tier, item, recipeType, recipe);

        // ===== 鱼类战利品（权重25，最常钓到的物品类别）=====
        // 遍历游戏内所有属于 ITEMS_FISHES Tag 的鱼类材质，逐个加入战利品池喵~
        for (Material fish : Tag.ITEMS_FISHES.getValues()) {
            // 将每种鱼以权重25加入战利品池，权重越高被选中概率越大喵~
            fishingLoot.add(new ItemStack(fish), 25);
        }

        // ===== 垃圾类战利品（低权重，偶尔钓到的杂物）=====
        // 骨头：权重10，垃圾中最常见喵~
        fishingLoot.add(new ItemStack(Material.BONE), 10);
        // 线：权重10，和骨头同等概率喵~
        fishingLoot.add(new ItemStack(Material.STRING), 10);
        // 墨囊：权重8，偶尔钓到喵~
        fishingLoot.add(new ItemStack(Material.INK_SAC), 8);
        // 海带：权重6，在水里比较正常喵~
        fishingLoot.add(new ItemStack(Material.KELP), 6);
        // 木棍：权重5，钓到一根棍子喵~
        fishingLoot.add(new ItemStack(Material.STICK), 5);
        // 腐肉：权重3，钓到点恶心的东西喵~
        fishingLoot.add(new ItemStack(Material.ROTTEN_FLESH), 3);
        // 皮革：权重2，垃圾中最少见喵~
        fishingLoot.add(new ItemStack(Material.LEATHER), 2);
        // 竹子：权重3，可能水底长着竹子喵~
        fishingLoot.add(new ItemStack(Material.BAMBOO), 3);

        // ===== 稀有战利品（权重1，极低概率的惊喜）=====
        // 马鞍：权重1，超稀有战利品喵~
        fishingLoot.add(new ItemStack(Material.SADDLE), 1);
        // 命名牌：权重1，超稀有战利品喵~
        fishingLoot.add(new ItemStack(Material.NAME_TAG), 1);
        // 鹦鹉螺壳：权重1，超稀有战利品喵~
        fishingLoot.add(new ItemStack(Material.NAUTILUS_SHELL), 1);
    }

    /**
     * 返回此机器人的类型标识为「钓鱼机器人」喵~
     * 父类会根据此类型决定机器人能执行哪些程序指令喵~
     */
    @Override
    public AndroidType getAndroidType() {
        // 返回钓鱼机器人类型枚举值，告诉系统这台机器人专门用于钓鱼喵~
        return AndroidType.FISHERMAN;
    }

    /**
     * 执行一次钓鱼操作的核心方法喵~
     *
     * <p>整体思路：
     * 1. 获取机器人正下方的方块，判断是否为水喵~
     * 2. 若是水，播放钓鱼音效喵~
     * 3. 按「等级×10%」的概率尝试产出一件战利品喵~
     * 4. 产出时从战利品池随机取一件，克隆后推入输出槽喵~
     *
     * <p>输入：机器人所在方块 b，机器人的 UniversalMenu 菜单喵~
     * <p>输出：无返回值，副作用是向菜单输出槽推入一件战利品（或什么都不做）喵~
     * <p>边界条件：下方不是水则直接跳过，不播放音效也不产出物品喵~
     */
    @Override
    protected void fish(Block b, UniversalMenu menu) {
        // 获取机器人正下方的方块，用于判断是否在水上钓鱼喵~
        Block water = b.getRelative(BlockFace.DOWN);

        // 喵~防御：只有下方确实是水方块才执行钓鱼逻辑，防止在陆地上空转浪费资源喵~
        if (water.getType() == Material.WATER) {
            // 在水方块位置播放钓鱼音效，给玩家视听反馈喵~
            SoundEffect.FISHERMAN_ANDROID_FISHING_SOUND.playAt(water);

            // 根据机器人等级计算钓到物品的概率：等级1=10%，等级2=20%，以此类推喵~
            // ThreadLocalRandom.current().nextInt(100) 生成 [0,99] 的随机整数，小于阈值则成功喵~
            if (ThreadLocalRandom.current().nextInt(100) < 10 * getTier()) {
                // 从战利品随机池中按权重随机抽取一件物品喵~
                ItemStack drop = fishingLoot.getRandom();
                // 将物品克隆一份（避免修改池中的原始物品引用）推入机器人的输出槽喵~
                menu.pushItem(drop.clone(), getOutputSlots());
            }
        }
    }
}
