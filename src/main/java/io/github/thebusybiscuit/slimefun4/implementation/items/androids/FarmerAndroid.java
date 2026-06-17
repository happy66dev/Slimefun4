package io.github.thebusybiscuit.slimefun4.implementation.items.androids;

import city.norain.slimefun4.api.menu.UniversalMenu;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.events.AndroidFarmEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

/**
 * 农民机器人类，继承自 ProgrammableAndroid（可编程机器人）喵~
 *
 * 整体思路：
 *   这个机器人专门负责自动耕种任务。每次执行时，它会：
 *   1. 检查目标方块所有者的权限（不能越权收割别人的地）
 *   2. 判断目标方块是否是成熟的庄稼（Ageable 接口且 age >= maxAge）
 *   3. 触发 AndroidFarmEvent 事件，允许其他插件监听或取消此次收割
 *   4. 若事件未被取消，则将庄稼产出放入机器人存储槽，并将庄稼重置为幼苗状态（age = 0）
 *
 * 支持的庄稼类型：小麦、土豆、胡萝卜、甜菜根、可可豆、地狱疣、甜浆果丛
 * 边界条件：目标方块不在世界边界内时直接跳过；庄稼未成熟时不收割喵~
 */
// 农民机器人：FarmerAndroid 是专门执行农业耕种程序的机器人物品类喵~
public class FarmerAndroid extends ProgrammableAndroid {

    // 构造方法：接收物品组、等级、物品栈、合成类型、合成配方，传给父类初始化喵~
    @ParametersAreNonnullByDefault
    public FarmerAndroid(
            ItemGroup itemGroup, int tier, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        // 调用父类 ProgrammableAndroid 的构造方法完成注册初始化喵~
        super(itemGroup, tier, item, recipeType, recipe);
    }

    // 重写获取机器人类型的方法，根据等级返回普通农民或高级农民类型喵~
    @Override
    public AndroidType getAndroidType() {
        // 等级为1时是普通农民机器人，否则是高级农民机器人喵~
        return getTier() == 1 ? AndroidType.FARMER : AndroidType.ADVANCED_FARMER;
    }

    /**
     * 执行一次耕种操作的核心方法喵~
     *
     * 整体思路：
     *   1. 从方块数据中读取机器人所有者的 UUID，并获取对应的 OfflinePlayer
     *   2. 用保护管理器检查该玩家是否有权限破坏目标方块（防止越权）
     *   3. 检查目标方块是否在世界边界内（防止边界外操作报错）
     *   4. 用 instanceof 模式匹配判断方块是否是可成长作物，且已成熟
     *   5. 创建 AndroidInstance 代表本次执行的机器人实例，触发事件
     *   6. 事件未取消时，尝试将产出物放入存储槽，成功则播放音效并重置作物年龄
     *
     * 输入：b=机器人方块本身, menu=机器人存储GUI, block=正在处理的目标庄稼方块, isAdvanced=是否高级
     * 输出：无返回值，副作用是收割作物并放入存储槽喵~
     *
     * @param b         机器人方块的位置
     * @param menu      机器人的 GUI 存储菜单
     * @param block     当前正在检测/收割的目标方块
     * @param isAdvanced 是否是高级农民机器人
     */
    @Override
    protected void farm(Block b, UniversalMenu menu, Block block, boolean isAdvanced) {
        // 从方块的持久化存储中读取 "owner" 字段（UUID字符串），并转换为 OfflinePlayer 对象喵~
        OfflinePlayer owner = Bukkit.getOfflinePlayer(
                UUID.fromString(StorageCacheUtils.getUniversalBlockData(menu.getUuid(), b.getLocation(), "owner")));
        // 喵~防御：检查机器人所有者是否有权限破坏目标方块，无权限则直接返回，防止越权收割他人农田喵~
        if (!Slimefun.getProtectionManager().hasPermission(owner, block, Interaction.BREAK_BLOCK)) {
            return;
        }

        // 获取目标方块的材质类型（如 WHEAT、CARROTS 等），用于后续判断是哪种庄稼喵~
        Material blockType = block.getType();
        // 获取目标方块的方块数据（包含作物年龄等信息）喵~
        BlockData data = block.getBlockData();
        // 初始化产出物为 null，若庄稼成熟才会被赋值喵~
        ItemStack drop = null;

        // 喵~防御：检查目标方块是否在世界边界范围内，超出世界边界时直接跳过，防止操作越界方块引发异常喵~
        if (!block.getWorld().getWorldBorder().isInside(block.getLocation())) {
            return;
        }

        // 用 instanceof 模式匹配判断方块是否实现了 Ageable 接口（即是可成长的作物）
        // 同时检查当前年龄是否已达到最大年龄（即庄稼已完全成熟）喵~
        if (data instanceof Ageable ageable && ageable.getAge() >= ageable.getMaximumAge()) {
            // 庄稼已成熟，根据材质类型计算本次收割的产出物喵~
            drop = getDropFromCrop(blockType);
        }

        // 创建代表本次执行的机器人实例，封装机器人类型和方块位置信息喵~
        AndroidInstance instance = new AndroidInstance(this, b);

        // 创建并触发 AndroidFarmEvent 事件，允许其他插件监听或取消本次收割操作喵~
        AndroidFarmEvent event = new AndroidFarmEvent(block, instance, isAdvanced, drop);
        // 向服务器事件总线广播此事件，若被插件取消则后续逻辑不执行喵~
        Bukkit.getPluginManager().callEvent(event);

        // 喵~防御：检查事件是否被其他插件取消，若取消则不执行收割操作喵~
        if (!event.isCancelled()) {
            // 从事件对象中取回最终的产出物（插件可能在事件中修改了 drop）喵~
            drop = event.getDrop();

            // 喵~防御：drop 为 null 说明庄稼未成熟或无产出，不执行后续操作；
            // pushItem 返回 null 说明物品成功放入存储槽，若存储槽已满则不收割喵~
            if (drop != null && menu.pushItem(drop, getOutputSlots()) == null) {
                // 播放方块踩踏音效，模拟收割庄稼的声音喵~
                block.getWorld().playEffect(block.getLocation(), Effect.STEP_SOUND, blockType);

                // 再次用 instanceof 模式匹配确认方块数据是 Ageable 类型喵~
                if (data instanceof Ageable ageable) {
                    // 将作物年龄重置为 0（幼苗状态），实现可持续自动耕种喵~
                    ageable.setAge(0);
                    // 将修改后的方块数据写回方块，使游戏内方块外观同步更新喵~
                    block.setBlockData(data);
                }
            }
        }
    }

    /**
     * 根据庄稼材质类型计算收割产出物的方法喵~
     *
     * 整体思路：
     *   使用 switch 表达式根据作物类型返回对应的 ItemStack 产出物。
     *   每种作物的产出数量是随机的（模拟真实收割的随机性）：
     *     - 小麦：1~2 个
     *     - 其余作物（土豆/胡萝卜/甜菜根/可可豆/地狱疣/甜浆果）：1~3 个
     *   不支持的材质类型返回 null（例如普通草地、石头等非庄稼方块）喵~
     *
     * 输入：crop - 作物的 Material 枚举类型
     * 输出：对应的产出 ItemStack，若材质不是支持的庄稼类型则返回 null 喵~
     */
    private ItemStack getDropFromCrop(Material crop) {
        // 使用线程本地随机数生成器，避免多线程竞争同一 Random 实例，性能更好喵~
        Random random = ThreadLocalRandom.current();

        // 用 switch 表达式根据庄稼类型返回对应数量的产出物喵~
        return switch (crop) {
            // 小麦成熟后产出 1~2 个小麦（random.nextInt(2) 结果为 0 或 1，加 1 后为 1~2）喵~
            case WHEAT -> new ItemStack(Material.WHEAT, random.nextInt(2) + 1);
            // 马铃薯成熟后产出 1~3 个土豆（random.nextInt(3) 结果为 0~2，加 1 后为 1~3）喵~
            case POTATOES -> new ItemStack(Material.POTATO, random.nextInt(3) + 1);
            // 胡萝卜成熟后产出 1~3 个胡萝卜喵~
            case CARROTS -> new ItemStack(Material.CARROT, random.nextInt(3) + 1);
            // 甜菜根成熟后产出 1~3 个甜菜根喵~
            case BEETROOTS -> new ItemStack(Material.BEETROOT, random.nextInt(3) + 1);
            // 可可豆（通常长在丛林木头上）成熟后产出 1~3 个可可豆喵~
            case COCOA -> new ItemStack(Material.COCOA_BEANS, random.nextInt(3) + 1);
            // 地狱疣（下界合成材料）成熟后产出 1~3 个地狱疣喵~
            case NETHER_WART -> new ItemStack(Material.NETHER_WART, random.nextInt(3) + 1);
            // 甜浆果丛成熟后产出 1~3 个甜浆果喵~
            case SWEET_BERRY_BUSH -> new ItemStack(Material.SWEET_BERRIES, random.nextInt(3) + 1);
            // 喵~防御：其他不支持的方块类型返回 null，调用方会判断 null 不执行收割喵~
            default -> null;
        };
    }
}
