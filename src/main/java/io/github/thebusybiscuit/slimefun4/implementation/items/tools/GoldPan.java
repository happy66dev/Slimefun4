package io.github.thebusybiscuit.slimefun4.implementation.items.tools;

import io.github.bakedlibs.dough.collections.RandomizedSet;
import io.github.bakedlibs.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSpawnReason;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.EntityInteractHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectricGoldPan;
import io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks.AutomatedPanningMachine;
import io.github.thebusybiscuit.slimefun4.implementation.settings.GoldPanDrop;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.inventory.ItemStack;

/**
 * A {@link GoldPan} is a {@link SlimefunItem} which allows you to obtain various
 * resources from Gravel.
 *
 * @author TheBusyBiscuit
 * @author svr333
 * @author JustAHuman
 *
 * @see NetherGoldPan
 * @see AutomatedPanningMachine
 * @see ElectricGoldPan
 */
public class GoldPan extends SimpleSlimefunItem<ItemUseHandler> implements RecipeDisplayItem {

    private final RandomizedSet<ItemStack> randomizer = new RandomizedSet<>();
    private final Set<Material> inputMaterials = new HashSet<>(List.of(Material.GRAVEL));
    private final Set<GoldPanDrop> drops = new HashSet<>();

    @ParametersAreNonnullByDefault
    public GoldPan(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        drops.addAll(getGoldPanDrops());
        addItemSetting(drops.toArray(new GoldPanDrop[0]));
        addItemHandler(onEntityInteract());
    }

    /**
     * @deprecated since RC-36
     *
     * Use {@link GoldPan#getInputMaterials()} instead.
     */
    @Deprecated(since = "RC-36")
    public Material getInputMaterial() {
        return Material.GRAVEL;
    }

    /**
     * This method returns the target {@link Material Materials} for this {@link GoldPan}.
     *
     * @return The {@link Set} of {@link Material Materials} this {@link GoldPan} can be used on.
     */
    public @Nonnull Set<Material> getInputMaterials() {
        return Collections.unmodifiableSet(inputMaterials);
    }

    /**
     * This method returns the target {@link GoldPanDrop GoldPanDrops} for this {@link GoldPan}.
     *
     * @return The {@link Set} of {@link GoldPanDrop GoldPanDrops} this {@link GoldPan} can drop.
     */
    protected @Nonnull Set<GoldPanDrop> getGoldPanDrops() {
        Set<GoldPanDrop> settings = new HashSet<>();

        settings.add(new GoldPanDrop(this, "chance.FLINT", 40, new ItemStack(Material.FLINT)));
        settings.add(new GoldPanDrop(this, "chance.CLAY", 20, new ItemStack(Material.CLAY_BALL)));
        settings.add(new GoldPanDrop(this, "chance.SIFTED_ORE", 25, SlimefunItems.SIFTED_ORE));
        settings.add(new GoldPanDrop(this, "chance.IRON_NUGGET", 10, new ItemStack(Material.IRON_NUGGET)));
        settings.add(new GoldPanDrop(this, "chance.GOLD_NUGGET", 7, new ItemStack(Material.GOLD_NUGGET)));
        settings.add(new GoldPanDrop(this, "chance.REDSTONE", 3, new ItemStack(Material.REDSTONE)));

        return settings;
    }

    @Override
    public void postRegister() {
        super.postRegister();
        updateRandomizer();
    }

    /**
     * <strong>Do not call this method directly</strong>.
     * <p>
     * This method is for internal purposes only.
     * It will update and re-calculate all weights in our {@link RandomizedSet}.
     */
    public void updateRandomizer() {
        randomizer.clear();

        for (GoldPanDrop setting : drops) {
            if (setting.getValue() > 0) {
                randomizer.add(setting.getOutput(), setting.getValue());
            }
        }
    }

    /**
     * 此方法为附属插件设置一个运行时淘金掉落条目。
     *
     * 同一键会覆盖已有的运行时条目，避免插件重复初始化时累积相同产物。
     * AIR 代表消耗输入但不产生物品的空结果，不会显示在指南配方中。
     *
     * @param key 运行时条目的唯一键。
     * @param output 随机抽取到的物品或 AIR 空结果。
     * @param weight 相对随机权重，必须为非负整数。
     */
    @ParametersAreNonnullByDefault
    public void setRuntimeDrop(String key, ItemStack output, int weight) {
        // 喵~防御：空白键无法稳定替换运行时条目，拒绝创建不可定位的掉落配置喵~
        if (key == null || key.isBlank()) {
            // 抛出明确异常，帮助附属插件修正非法运行时条目键喵~
            throw new IllegalArgumentException("The runtime Gold Pan drop key cannot be blank");
        }
        // 喵~防御：空输出会破坏随机池和机器结果处理，拒绝注册喵~
        if (output == null) {
            // 抛出明确异常，防止空物品进入随机池喵~
            throw new IllegalArgumentException("The runtime Gold Pan drop output cannot be null");
        }
        // 喵~防御：负权重不具备概率含义，拒绝生成非法随机池喵~
        if (weight < 0) {
            // 抛出明确异常，提示调用者使用零或正整数权重喵~
            throw new IllegalArgumentException("The runtime Gold Pan drop weight cannot be negative");
        }

        // 移除同键旧条目，使重复调用保持幂等而不会叠加概率喵~
        drops.removeIf(drop -> drop.getKey().equals(key));
        // 保存独立副本，防止调用者后续修改 ItemStack 改变随机掉落喵~
        drops.add(new GoldPanDrop(this, key, weight, output.clone()));
        // 立即重建随机池，让运行时覆盖无需等待下一次核心重载喵~
        updateRandomizer();
    }

    public @Nonnull ItemStack getRandomOutput() {
        ItemStack item = randomizer.getRandom();

        // Fixes #2804
        return item != null ? item : new ItemStack(Material.AIR);
    }

    @Nonnull
    @Override
    public String getLabelLocalPath() {
        return "guide.tooltips.recipes.gold-pan";
    }

    @Nonnull
    @Override
    public ItemUseHandler getItemHandler() {
        return e -> {
            Optional<Block> block = e.getClickedBlock();

            if (block.isPresent()) {
                Block b = block.get();

                // Check the clicked block type and for protections
                if (isValidInputMaterial(b.getType())
                        && Slimefun.getProtectionManager()
                                .hasPermission(e.getPlayer(), b.getLocation(), Interaction.BREAK_BLOCK)) {
                    ItemStack output = getRandomOutput();

                    b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, b.getType());
                    b.setType(Material.AIR);

                    // Make sure that the randomly selected item is not air
                    if (output.getType() != Material.AIR) {
                        SlimefunUtils.spawnItem(
                                b.getLocation(), output.clone(), ItemSpawnReason.GOLD_PAN_USE, true, e.getPlayer());
                    }
                }
            }

            e.cancel();
        };
    }

    /**
     * This method cancels {@link EntityInteractHandler} to prevent interacting {@link GoldPan}
     * with entities.
     *
     * @return the {@link EntityInteractHandler} of this {@link SlimefunItem}
     */
    @Nonnull
    public EntityInteractHandler onEntityInteract() {
        return (e, item, offHand) -> {
            if (!(e.getRightClicked() instanceof ItemFrame)) {
                e.setCancelled(true);
            }
        };
    }

    @Nonnull
    @Override
    public List<ItemStack> getDisplayRecipes() {
        List<ItemStack> recipes = new ArrayList<>();

        for (GoldPanDrop drop : drops) {
            if (drop.getValue() <= 0 || drop.getOutput().getType().isAir()) {
                continue;
            }

            for (Material material : getInputMaterials()) {
                recipes.add(new ItemStack(material));
                recipes.add(drop.getOutput());
            }
        }

        return recipes;
    }

    /**
     * This returns whether the {@link GoldPan} accepts the {@link ItemStack} as an input
     *
     * @param itemStack
     *            The {@link ItemStack} to check
     *
     * @return If the {@link ItemStack} is valid
     */
    public boolean isValidInput(@Nullable ItemStack itemStack) {
        if (itemStack == null) {
            return false;
        }

        Material material = itemStack.getType();
        return isValidInputMaterial(material)
                && SlimefunUtils.isItemSimilar(itemStack, new ItemStack(material), true, false);
    }

    /**
     * This returns whether the {@link GoldPan} accepts the {@link Material} as an input
     *
     * @param material
     *            The {@link Material} to check
     *
     * @return If the {@link Material} is valid
     */
    public boolean isValidInputMaterial(@Nonnull Material material) {
        return getInputMaterials().contains(material);
    }
}
