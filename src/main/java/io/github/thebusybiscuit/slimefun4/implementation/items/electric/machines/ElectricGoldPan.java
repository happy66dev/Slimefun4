package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedbackType;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.GoldPan;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.NetherGoldPan;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link ElectricGoldPan} is an electric machine based on the {@link GoldPan}.
 * It also serves as a {@link NetherGoldPan}.
 *
 * @author TheBusyBiscuit
 * @author svr333
 * @author JustAHuman
 *
 * @see GoldPan
 * @see NetherGoldPan
 */
public class ElectricGoldPan extends AContainer implements RecipeDisplayItem {

    private final ItemSetting<Boolean> overrideOutputLimit = new ItemSetting<>(this, "override-output-limit", false);

    private final GoldPan goldPan = SlimefunItems.GOLD_PAN.getItem(GoldPan.class);
    private final GoldPan netherGoldPan = SlimefunItems.NETHER_GOLD_PAN.getItem(GoldPan.class);

    @ParametersAreNonnullByDefault
    public ElectricGoldPan(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        this.feedbackType = MachineFeedbackType.FLUID_BUBBLING;
        addItemSetting(overrideOutputLimit);
    }

    /**
     * @deprecated since RC-36
     * Use {@link ElectricGoldPan#isOutputLimitOverridden()} instead.
     */
    @Deprecated(since = "RC-36")
    public boolean isOutputLimitOverriden() {
        return isOutputLimitOverridden();
    }

    /**
     * This returns whether the {@link ElectricGoldPan} will stop processing inputs
     * if both output slots contain items or if that default behavior should be
     * overridden and allow the {@link ElectricGoldPan} to continue processing inputs
     * even if both output slots are occupied. Note this option will allow players
     * to force specific outputs from the {@link ElectricGoldPan} but can be
     * necessary when a server has disabled cargo networks.
     *
     * @return If output limits are overridden
     */
    public boolean isOutputLimitOverridden() {
        return overrideOutputLimit.getValue();
    }

    @Override
    public @Nonnull List<ItemStack> getDisplayRecipes() {
        List<ItemStack> recipes = new ArrayList<>();

        recipes.addAll(goldPan.getDisplayRecipes());
        recipes.addAll(netherGoldPan.getDisplayRecipes());

        return recipes;
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.DIAMOND_SHOVEL);
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        // 遍历输入槽，优先寻找可由普通或下界淘金盘处理的物品喵~
        for (int slot : getInputSlots()) {
            // 读取当前输入槽物品，供后续识别淘金来源喵~
            ItemStack item = menu.getItemInSlot(slot);
            // 保存本轮随机结果对应的处理时长，零值表示当前物品不能淘金喵~
            int processingSeconds = 0;
            // 保存随机淘金产物，AIR 表示消耗输入但没有物品产出喵~
            ItemStack output = null;

            // 普通淘金盘可处理沙砾时抽取本轮随机产物喵~
            if (goldPan.isValidInput(item)) {
                // 获取普通淘金盘的随机结果喵~
                output = goldPan.getRandomOutput();
                // 普通沙砾淘金保持原有三秒处理时长喵~
                processingSeconds = 3 / getSpeed();
                // 下界淘金盘可处理输入时沿用其原有随机流程喵~
            } else if (netherGoldPan.isValidInput(item)) {
                // 获取下界淘金盘的随机结果喵~
                output = netherGoldPan.getRandomOutput();
                // 下界淘金保持原有四秒处理时长喵~
                processingSeconds = 4 / getSpeed();
            }

            // 喵~防御：非淘金输入或意外空结果不创建无效机器操作喵~
            if (output == null) {
                // 继续检查下一个输入槽喵~
                continue;
            }
            // 普通淘金盘抽到 AIR 时消耗沙砾并创建合法的无产出周期喵~
            if (output.getType().isAir() && goldPan.isValidInput(item)) {
                // 消耗当前输入槽中的一块沙砾，确保空结果与手持淘金盘语义一致喵~
                menu.consumeItem(slot);
                // 使用空输出数组表示已完成处理但不应向输出槽写入任何物品喵~
                return new MachineRecipe(processingSeconds, new ItemStack[] {item}, new ItemStack[0]);
            }
            // 喵~防御：下界淘金盘不应产生 AIR；若核心随机池为空则安全跳过喵~
            if (output.getType().isAir()) {
                // 继续检查其他输入槽，避免将 AIR 交给机器输出路径喵~
                continue;
            }
            // 输出槽受限且没有空槽时暂停普通产物处理，保留输入物品喵~
            if (!isOutputLimitOverridden() && !hasFreeSlot(menu)) {
                // 等待玩家或物流腾出输出槽喵~
                return null;
            }
            // 输出槽能够完整容纳本轮产物时才消耗输入并启动周期喵~
            if (menu.fits(output, getOutputSlots())) {
                // 消耗本轮参与淘金的一块输入物品喵~
                menu.consumeItem(slot);
                // 返回携带正常产物的原有机器配方结构喵~
                return new MachineRecipe(processingSeconds, new ItemStack[] {item}, new ItemStack[] {output});
            }
        }

        // 没有可启动的淘金操作时保持机器空闲喵~
        return null;
    }

    private boolean hasFreeSlot(@Nonnull BlockMenu menu) {
        for (int slot : getOutputSlots()) {
            if (menu.getItemInSlot(slot) == null) {
                return true;
            }
        }

        return false;
    }

    @Override
    public @Nonnull String getMachineIdentifier() {
        return "ELECTRIC_GOLD_PAN";
    }
}
