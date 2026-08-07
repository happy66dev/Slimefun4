package io.github.thebusybiscuit.slimefun4.implementation.items.multiblocks;

import io.github.thebusybiscuit.slimefun4.api.events.MultiBlockCraftEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Dispenser;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link OreWasher} is a special {@link MultiBlockMachine} which allows you to
 * turn Sifted Ore into ore dusts.
 *
 * @author TheBusyBiscuit
 * @author Sfiguz7
 *
 */
public class OreWasher extends MultiBlockMachine {

    // @formatter:off
    // 筛矿产物池：14种等概率产物，getRandomDust()从中均匀随机取一个喵~
    // 后5种物品是 MoreOres 插件添加的，需在服务器启动后通过 setMoreOresDusts() 注入，
    // 未注入时退化到原版9种产物
    private ItemStack[] dusts = new ItemStack[] {
        SlimefunItems.IRON_DUST,
        SlimefunItems.GOLD_DUST,
        SlimefunItems.COPPER_DUST,
        SlimefunItems.TIN_DUST,
        SlimefunItems.ZINC_DUST,
        SlimefunItems.ALUMINUM_DUST,
        SlimefunItems.MAGNESIUM_DUST,
        SlimefunItems.LEAD_DUST,
        SlimefunItems.SILVER_DUST
    };
    // @formatter:on

    /**
     * 由 MoreOres 插件在启动后注入额外的筛矿产物，实现14种产物等概率产出喵~
     * 传入5种额外物品：钴粉、镍粉、石块、粗盐、杂矿粉
     */
    public void setMoreOresDusts(
            ItemStack cobaltDust, ItemStack nickelDust, ItemStack cobblestone, ItemStack salt, ItemStack impureOre) {
        // 创建新数组：原9种 + 传入的5种，共14种等概率产物
        this.dusts = new ItemStack[] {
            SlimefunItems.IRON_DUST,
            SlimefunItems.GOLD_DUST,
            SlimefunItems.COPPER_DUST,
            SlimefunItems.TIN_DUST,
            SlimefunItems.ZINC_DUST,
            SlimefunItems.ALUMINUM_DUST,
            SlimefunItems.MAGNESIUM_DUST,
            SlimefunItems.LEAD_DUST,
            SlimefunItems.SILVER_DUST,
            cobaltDust, // MoreOres 钴粉
            nickelDust, // MoreOres 镍粉
            cobblestone, // 原版石块
            salt, // SF 粗盐
            impureOre // MoreOres 杂矿粉
        };
    }

    // 保存核心旧版洗矿库存检查模式，保持原有配置兼容性喵~
    private final boolean legacyMode;

    // 记录是否允许核心默认的 SIFTED_ORE 洗矿流程，默认保持原版行为喵~
    private boolean siftedOreProcessingEnabled = true;

    /**
     * 设置核心筛矿物的默认洗矿流程是否启用喵~
     *
     * 附属插件可以在自身启用阶段关闭该流程，但不会影响附属插件注册的精炼配方喵~
     *
     * @param enabled 是否允许 SIFTED_ORE 进入核心默认洗矿逻辑。
     */
    public void setSiftedOreProcessingEnabled(boolean enabled) {
        // 保存附属插件请求的核心筛矿开关状态喵~
        this.siftedOreProcessingEnabled = enabled;
        // 关闭默认筛矿时同步移除指南和已缓存的核心筛矿配方喵~
        if (!enabled) {
            // 删除显示列表中的 SIFTED_ORE 配方对，避免指南继续展示核心筛矿喵~
            removeSiftedOreDisplayRecipes();
            // 删除已经进入机器配方列表的 SIFTED_ORE 配方，避免旧状态继续执行喵~
            recipes.removeIf(this::isSiftedOreRecipe);
        }
    }

    /**
     * 删除显示列表中的核心 SIFTED_ORE 配方对喵~
     */
    private void removeSiftedOreDisplayRecipes() {
        // 从后向前按输入输出成对删除，避免列表索引移动导致漏删喵~
        for (int recipeIndex = displayRecipes.size() - 2; recipeIndex >= 0; recipeIndex -= 2) {
            // 读取当前配方对的输入物品喵~
            ItemStack input = displayRecipes.get(recipeIndex);
            // 输入为核心筛矿时删除输入和对应输出喵~
            if (isSiftedOreDisplayItem(input)) {
                // 删除当前配方的输出物品喵~
                displayRecipes.remove(recipeIndex + 1);
                // 删除当前配方的输入物品喵~
                displayRecipes.remove(recipeIndex);
            }
        }
    }

    /**
     * 判断显示列表中的物品是否为核心筛矿物品喵~
     *
     * @param itemStack 待检查的显示物品。
     * @return 是 SIFTED_ORE 时返回 true。
     */
    private boolean isSiftedOreDisplayItem(ItemStack itemStack) {
        // 喵~防御：空物品或空气不可能是筛矿物品。
        if (itemStack == null || itemStack.getType().isAir()) return false;
        // 使用 Slimefun 相似物品判断，兼容带 ID 元数据的 ItemStack 喵~
        return SlimefunUtils.isItemSimilar(itemStack, SlimefunItems.SIFTED_ORE, true);
    }

    /**
     * 判断机器配方是否以核心筛矿物品为输入喵~
     *
     * @param recipePair 机器配方的输入输出数组。
     * @return 首个输入是 SIFTED_ORE 时返回 true。
     */
    private boolean isSiftedOreRecipe(ItemStack[] recipePair) {
        // 喵~防御：空配方或没有输入槽时直接跳过。
        if (recipePair == null || recipePair.length == 0) return false;
        // 检查第一个输入槽是否为 SIFTED_ORE 喵~
        return isSiftedOreDisplayItem(recipePair[0]);
    }

    /**
     * 屏蔽核心默认的 SIFTED_ORE 配方注册，同时保留其他洗矿配方喵~
     *
     * @param input 配方输入数组。
     * @param output 配方输出物品。
     */
    @Override
    public void addRecipe(ItemStack[] input, ItemStack output) {
        // 关闭核心筛矿时拒绝所有以 SIFTED_ORE 开头的默认配方喵~
        if (!siftedOreProcessingEnabled && isSiftedOreRecipe(input)) return;
        // 其他洗矿配方继续交给核心机器保存喵~
        super.addRecipe(input, output);
    }

    /**
     * 查询核心筛矿物的默认洗矿流程是否启用喵~
     *
     * @return 启用时返回 true，否则返回 false。
     */
    public boolean isSiftedOreProcessingEnabled() {
        // 返回当前核心默认筛矿流程状态喵~
        return siftedOreProcessingEnabled;
    }

    /**
     * 精炼配方条目：一种输入粉末对应一个加权产物列表，按概率随机选取喵~
     *
     * 每个 RefineryEntry 持有输入物品和带权重的产物候选数组。
     * 权重实现方式：候选数组里重复放相同物品，数组长度即总权重。
     */
    public static class RefineryEntry {
        // 输入粉末物品，用 SlimefunUtils.isItemSimilar 匹配
        public final ItemStack input;
        // 加权产物池：同一物品出现 N 次代表权重 N
        public final ItemStack[] weightedOutputs;

        public RefineryEntry(ItemStack input, ItemStack[] weightedOutputs) {
            this.input = input;
            this.weightedOutputs = weightedOutputs;
        }
    }

    // 由附属插件注入的精炼配方列表，onInteract 遍历此列表处理粉末输入
    private final List<RefineryEntry> refineryRecipes = new ArrayList<>();

    /**
     * 注册一条精炼配方供 OreWasher 的 onInteract 识别和处理喵~
     * 调用者需在服务器启动后（延迟1tick）调用，确保物品已注册。
     */
    public void registerRefineryRecipe(RefineryEntry entry) {
        refineryRecipes.add(entry);
    }

    // 根据输入物品随机返回附属插件注册的精炼产物，供普通和电动洗矿机共用喵~
    public @Nullable ItemStack getRegisteredRefineryOutput(ItemStack input) {
        // 喵~防御：输入为空时不匹配任何附属配方。
        if (input == null || input.getType().isAir()) return null;
        // 遍历全部附属精炼配方并查找相似输入。
        for (RefineryEntry entry : refineryRecipes) {
            // 喵~防御：配方条目或输出池无效时跳过，避免运行时异常。
            if (entry == null
                    || entry.input == null
                    || entry.weightedOutputs == null
                    || entry.weightedOutputs.length == 0) continue;
            // 仅对完全匹配的输入物品选择随机输出。
            if (!SlimefunUtils.isItemSimilar(input, entry.input, true)) continue;
            // 从加权输出池中均匀选择一个候选物品。
            int outputIndex = ThreadLocalRandom.current().nextInt(entry.weightedOutputs.length);
            // 复制输出物品，避免调用方修改注册表中的全局对象。
            ItemStack output = entry.weightedOutputs[outputIndex];
            return output == null ? null : output.clone();
        }
        // 没有匹配配方时返回空值，交由机器继续处理默认逻辑。
        return null;
    }

    @ParametersAreNonnullByDefault
    public OreWasher(ItemGroup itemGroup, SlimefunItemStack item) {
        // @formatter:off
        super(
                itemGroup,
                item,
                new ItemStack[] {
                    null, new ItemStack(Material.DISPENSER), null,
                    null, new ItemStack(Material.OAK_FENCE), null,
                    null, new ItemStack(Material.CAULDRON), null
                },
                BlockFace.SELF);
        // @formatter:on

        legacyMode = Slimefun.getCfg().getBoolean("options.legacy-ore-washer");
    }

    @Override
    protected void registerDefaultRecipes(List<ItemStack> recipes) {
        /*
         * Iron and Gold are displayed as Ore Crusher recipes, as that is their primary
         * way of obtaining them. But we also wanna display them here, so we just
         * add these two recipes manually
         */
        // 只有核心筛矿开关开启时，才把默认筛矿展示项加入指南喵~
        if (siftedOreProcessingEnabled) {
            // 添加筛矿到铁粉的核心指南展示项喵~
            recipes.add(SlimefunItems.SIFTED_ORE);
            recipes.add(SlimefunItems.IRON_DUST);
            // 添加筛矿到金粉的核心指南展示项喵~
            recipes.add(SlimefunItems.SIFTED_ORE);
            recipes.add(SlimefunItems.GOLD_DUST);
        }

        // 保留砂子到粗盐的核心指南展示项喵~
        recipes.add(new ItemStack(Material.SAND));
        recipes.add(SlimefunItems.SALT);
    }

    @Override
    public @Nonnull List<ItemStack> getDisplayRecipes() {
        return recipes.stream().map(items -> items[0]).toList();
    }

    @Override
    public void onInteract(Player p, Block b) {
        Block dispBlock = b.getRelative(BlockFace.UP);
        BlockState state = dispBlock.getState(false);

        if (state instanceof Dispenser disp) {
            Inventory inv = disp.getInventory();

            for (ItemStack input : inv.getContents()) {
                if (input != null) {
                    if (siftedOreProcessingEnabled
                            && SlimefunUtils.isItemSimilar(input, SlimefunItems.SIFTED_ORE, true)) {
                        ItemStack output = getRandomDust();
                        Inventory outputInv;

                        if (!legacyMode) {
                            /*
                             * This is a fancy way of checking if there is empty space in the inv
                             * by checking if an unobtainable item could fit in it.
                             * However, due to the way the method findValidOutputInv() functions,
                             * the dummyAdding will never actually be added to the real inventory,
                             * so it really doesn't matter what item the ItemStack is made by.
                             * SlimefunItems.DEBUG_FISH however, signals that it's not supposed
                             * to be given to the player.
                             */
                            ItemStack dummyAdding = SlimefunItems.DEBUG_FISH;
                            outputInv = findOutputInventory(dummyAdding, dispBlock, inv);
                        } else {
                            outputInv = findOutputInventory(output, dispBlock, inv);
                        }

                        MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, output);
                        if (event.isCancelled()) {
                            return;
                        }

                        removeItem(p, b, inv, outputInv, input, event.getOutput(), 1);

                        if (outputInv != null) {
                            outputInv.addItem(SlimefunItems.STONE_CHUNK);
                        }

                        return;
                    } else if (SlimefunUtils.isItemSimilar(input, new ItemStack(Material.SAND, 2), false)) {
                        ItemStack output = SlimefunItems.SALT;
                        Inventory outputInv = findOutputInventory(output, dispBlock, inv);

                        MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, output);
                        if (event.isCancelled()) {
                            return;
                        }

                        removeItem(p, b, inv, outputInv, input, event.getOutput(), 2);

                        return;
                    } else if (SlimefunUtils.isItemSimilar(input, SlimefunItems.SALT, true)) {
                        // 喵~粗盐(SF SALT) → 食盐(EG_FOOD_SALT)，EG插件注册后运行期动态查找喵
                        io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem egSaltItem =
                                io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.getById("EG_FOOD_SALT");
                        // 喵~防御：EG 未安装或物品未注册时提示材料未知喵
                        if (egSaltItem == null) {
                            Slimefun.getLocalization().sendMessage(p, "machines.unknown-material", true);
                            return;
                        }
                        ItemStack output = egSaltItem.getItem().clone();
                        Inventory outputInv = findOutputInventory(output, dispBlock, inv);

                        MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, output);
                        if (event.isCancelled()) {
                            return;
                        }

                        removeItem(p, b, inv, outputInv, input, event.getOutput(), 1);

                        return;
                    } else if (SlimefunUtils.isItemSimilar(input, SlimefunItems.PULVERIZED_ORE, true)) {
                        ItemStack output = SlimefunItems.PURE_ORE_CLUSTER;
                        Inventory outputInv = findOutputInventory(output, dispBlock, inv);
                        MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, output);

                        Bukkit.getPluginManager().callEvent(event);
                        if (event.isCancelled()) {
                            return;
                        }

                        removeItem(p, b, inv, outputInv, input, event.getOutput(), 1);

                        return;
                    } else {
                        // 查询附属插件注册的精炼配方（如 MoreOres 的粉末→精粉）
                        for (RefineryEntry entry : refineryRecipes) {
                            // 用 SF 的 isItemSimilar 匹配，确保 SF 物品 ID 一致性
                            if (!SlimefunUtils.isItemSimilar(input, entry.input, true)) continue;
                            // 从加权产物池中随机取一种（池中重复项越多概率越高）
                            int idx = ThreadLocalRandom.current().nextInt(entry.weightedOutputs.length);
                            ItemStack output = entry.weightedOutputs[idx].clone();
                            Inventory outputInv = findOutputInventory(output, dispBlock, inv);

                            MultiBlockCraftEvent event = new MultiBlockCraftEvent(p, this, input, output);
                            if (event.isCancelled()) {
                                return;
                            }
                            // 消耗1个输入粉末，产出1个随机精粉或副产物
                            removeItem(p, b, inv, outputInv, input, event.getOutput(), 1);
                            return;
                        }
                    }
                }
            }
            Slimefun.getLocalization().sendMessage(p, "machines.unknown-material", true);
        }
    }

    @ParametersAreNonnullByDefault
    private void removeItem(
            Player p,
            Block b,
            Inventory inputInv,
            @Nullable Inventory outputInv,
            ItemStack input,
            ItemStack output,
            int amount) {
        if (outputInv != null) {
            ItemStack removing = input.clone();
            removing.setAmount(amount);
            inputInv.removeItem(removing);
            outputInv.addItem(output.clone());

            b.getWorld().playEffect(b.getLocation(), Effect.STEP_SOUND, Material.WATER);
            SoundEffect.ORE_WASHER_WASH_SOUND.playAt(b);
        } else {
            Slimefun.getLocalization().sendMessage(p, "machines.full-inventory", true);
        }
    }

    /**
     * This returns a random dust item from Slimefun.
     *
     * @return A randomly picked dust item
     */
    public @Nonnull ItemStack getRandomDust() {
        int index = ThreadLocalRandom.current().nextInt(dusts.length);
        return dusts[index].clone();
    }
}
