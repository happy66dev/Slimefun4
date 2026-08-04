package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedbackType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.OreWasher;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link ElectricDustWasher} serves as an electrical {@link OreWasher}.
 *
 * @author TheBusyBiscuit
 *
 * @see OreWasher
 *
 */
public class ElectricDustWasher extends AContainer {

    private final OreWasher oreWasher = SlimefunItems.ORE_WASHER.getItem(OreWasher.class);
    private final boolean legacyMode;

    @ParametersAreNonnullByDefault
    public ElectricDustWasher(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        this.feedbackType = MachineFeedbackType.FLUID_BUBBLING;

        legacyMode = Slimefun.getCfg().getBoolean("options.legacy-dust-washer");
    }

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.GOLDEN_SHOVEL);
    }

    @Override
    protected MachineRecipe findNextRecipe(BlockMenu menu) {
        for (int slot : getInputSlots()) {
            ItemStack input = menu.getItemInSlot(slot);
            MachineRecipe recipe = null;
            if (SlimefunUtils.isItemSimilar(input, SlimefunItems.SIFTED_ORE, true, false)) {
                if (!legacyMode && !hasFreeSlot(menu)) {
                    return null;
                }

                recipe = new MachineRecipe(4 / getSpeed(), new ItemStack[] {SlimefunItems.SIFTED_ORE}, new ItemStack[] {
                    oreWasher.getRandomDust()
                });

                if (!legacyMode || menu.fits(recipe.getOutput()[0], getOutputSlots())) {
                    menu.consumeItem(slot);
                    return recipe;
                }
            } else if (SlimefunUtils.isItemSimilar(input, SlimefunItems.PULVERIZED_ORE, true)) {
                recipe = new MachineRecipe(
                        4 / getSpeed(),
                        new ItemStack[] {SlimefunItems.PULVERIZED_ORE},
                        new ItemStack[] {SlimefunItems.PURE_ORE_CLUSTER});
            } else if (SlimefunUtils.isItemSimilar(input, new ItemStack(Material.SAND), true)) {
                recipe = new MachineRecipe(
                        4 / getSpeed(),
                        new ItemStack[] {new ItemStack(Material.SAND)},
                        new ItemStack[] {SlimefunItems.SALT});
            } else if (oreWasher != null) {
                // 查询普通洗矿机共享的附属精炼配方，实现电动洗矿机一致产物。
                ItemStack refineryOutput = oreWasher.getRegisteredRefineryOutput(input);
                // 喵~防御：没有匹配附属配方时继续检查其他默认分支。
                if (refineryOutput != null) {
                    // 创建随机精炼产物对应的电动洗矿机配方。
                    recipe = new MachineRecipe(
                            4 / getSpeed(), new ItemStack[] {input.clone()}, new ItemStack[] {refineryOutput});
                }
            }
            if (recipe != null && menu.fits(recipe.getOutput()[0], getOutputSlots())) {
                menu.consumeItem(slot);
                return recipe;
            }
        }

        return null;
    }

    private boolean hasFreeSlot(BlockMenu menu) {
        for (int slot : getOutputSlots()) {
            ItemStack item = menu.getItemInSlot(slot);

            if (item == null || item.getType() == Material.AIR) {
                return true;
            }
        }

        return false;
    }

    @Override
    public @Nonnull String getMachineIdentifier() {
        return "ELECTRIC_DUST_WASHER";
    }
}
