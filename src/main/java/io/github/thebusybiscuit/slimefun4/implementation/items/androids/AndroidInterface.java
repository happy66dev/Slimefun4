package io.github.thebusybiscuit.slimefun4.implementation.items.androids;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockDispenseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.VanillaInventoryDropHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.block.Dispenser;
import org.bukkit.inventory.ItemStack;

/**
 * {@link AndroidInterface} 是用于与 {@link ProgrammableAndroid}（可编程机器人）交互的物品栏接口喵~
 * 共有两种变体：燃料接口（fuel interface）和物品接口（item interface），分别负责向机器人传递燃料或物品喵~
 *
 * @author TheBusyBiscuit
 *
 * @see ProgrammableAndroid
 *
 */
public class AndroidInterface extends SimpleSlimefunItem<BlockDispenseHandler> {

    /**
     * 构造方法：创建一个 AndroidInterface 实例，并注册发射器物品掉落处理器喵~
     *
     * 整体思路：
     *   1. 调用父类构造方法，将物品注册到指定分组、配方类型和配方材料喵~
     *   2. 注册 VanillaInventoryDropHandler，当发射器（Dispenser）被破坏时，
     *      使用原版逻辑处理背包内物品的掉落，防止物品凭空消失喵~
     *
     * 输入参数：
     *   @param itemGroup   物品所属的 ItemGroup（Slimefun指南分组）喵~
     *   @param item        物品对应的 SlimefunItemStack（带有唯一ID和外观）喵~
     *   @param recipeType  合成类型，决定在哪台机器上合成喵~
     *   @param recipe      合成配方材料数组，长度为9对应3x3合成格喵~
     */
    @ParametersAreNonnullByDefault
    public AndroidInterface(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        // 调用父类 SimpleSlimefunItem 的构造方法，完成基本注册（分组、物品、配方类型、配方）喵~
        super(itemGroup, item, recipeType, recipe);

        // 注册 VanillaInventoryDropHandler：当作为 Dispenser（发射器方块）被破坏时，
        // 使用原版背包掉落逻辑，把内部物品正常掉落到世界中，避免物品丢失喵~
        addItemHandler(new VanillaInventoryDropHandler<>(Dispenser.class));
    }

    /**
     * 获取此物品绑定的 BlockDispenseHandler（方块发射处理器）喵~
     *
     * 整体思路：
     *   返回一个 Lambda 形式的 BlockDispenseHandler：
     *   当有物品被发射器发射（dispense）时，立即取消该发射事件，
     *   让 AndroidInterface 不会像普通发射器那样弹出物品喵~
     *   这样设计是为了让机器人接口只能通过机器人逻辑存取物品，
     *   而不允许玩家或红石电路直接从接口中发射物品喵~
     *
     * @return BlockDispenseHandler 实例，内容为取消所有发射事件喵~
     */
    @Override
    public BlockDispenseHandler getItemHandler() {
        // 返回一个 Lambda：接收发射事件e、发射器数据d、发射器方块block、对应机器machine，
        // 调用 e.setCancelled(true) 取消本次发射行为，阻止物品被弹出喵~
        return (e, d, block, machine) -> e.setCancelled(true);
    }
}
