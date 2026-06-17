package io.github.thebusybiscuit.slimefun4.implementation.guide;

import city.norain.slimefun4.VaultIntegration;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import io.github.bakedlibs.dough.chat.ChatInput;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.bakedlibs.dough.items.ItemUtils;
import io.github.bakedlibs.dough.recipes.MinecraftRecipe;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.groups.FlexItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.groups.LockedItemGroup;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeEntry;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.api.researches.Research;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.thebusybiscuit.slimefun4.core.guide.GuideHistory;
import io.github.thebusybiscuit.slimefun4.core.guide.ScgBridge;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlock;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.tasks.AsyncRecipeChoiceTask;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedItemFlag;
import io.github.thebusybiscuit.slimefun4.utils.itemstack.SlimefunGuideItem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu.MenuClickHandler;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.RecipeChoice.MaterialChoice;

/**
 * The {@link SurvivalSlimefunGuide} is the standard version of our {@link SlimefunGuide}.
 * It uses an {@link Inventory} to display {@link SlimefunGuide} contents.
 *
 * @author TheBusyBiscuit
 * @see SlimefunGuide
 * @see SlimefunGuideImplementation
 * @see CheatSheetSlimefunGuide
 *
 */
// 生存模式指南的核心实现类，负责渲染所有指南GUI界面喵~
public class SurvivalSlimefunGuide implements SlimefunGuideImplementation {

    // 主菜单最多显示的物品组数量上限，超出则分页显示喵~
    private static final int MAX_ITEM_GROUPS = 36;

    // 合成配方槽位的GUI编号映射，对应3x3合成格在箱子界面中的槽位喵~
    private final int[] recipeSlots = {3, 4, 5, 12, 13, 14, 21, 22, 23};
    // 指南物品本体的ItemStack，用于给玩家手持展示喵~
    private final ItemStack item;

    // 默认构造方法，创建生存模式指南物品喵~
    public SurvivalSlimefunGuide() {
        // 使用SlimefunGuideItem创建带指南数据的物品，显示名为"Slimefun 指南 (箱子界面)"喵~
        item = new SlimefunGuideItem(this, "&aSlimefun 指南 &7(箱子界面)");
    }

    // 已废弃的兼容性构造方法，提供向后兼容入口（内部不再使用两个布尔参数）喵~
    // 已废弃的兼容性构造方法，忽略两个布尔参数直接创建默认指南物品喵~
    @Deprecated
    public SurvivalSlimefunGuide(boolean v1, boolean v2) {
        // 与默认构造方法行为一致，保持向后兼容性喵~
        item = new SlimefunGuideItem(this, "&aSlimefun 指南 &7(箱子界面)");
    }

    @Override
    // 返回当前指南运行的模式，生存模式为SURVIVAL_MODE喵~
    public @Nonnull SlimefunGuideMode getMode() {
        return SlimefunGuideMode.SURVIVAL_MODE;
    }

    @Override
    // 返回代表指南的物品ItemStack，用于识别和展示指南喵~
    public @Nonnull ItemStack getItem() {
        return item;
    }

    // 判断当前是否处于生存模式（非作弊模式），生存模式下有权限和研究限制喵~
    protected final boolean isSurvivalMode() {
        return getMode() != SlimefunGuideMode.CHEAT_MODE;
    }

    /**
     * Returns a {@link List} of visible {@link ItemGroup} instances that the {@link SlimefunGuide} would display.
     *
     * @param p       The {@link Player} who opened his {@link SlimefunGuide}
     * @param profile The {@link PlayerProfile} of the {@link Player}
     * @return a {@link List} of visible {@link ItemGroup} instances
     */
    /*
     * 整体思路：遍历注册表中所有物品组，根据当前指南模式和玩家状态过滤出可见的物品组列表喵~
     * 输入：玩家对象p，玩家档案profile
     * 输出：对该玩家可见的ItemGroup列表
     * 边界条件：FlexItemGroup需要额外调用isVisible判断；普通组只判断isHidden；任何异常都捕获并记录日志不影响其他组喵~
     */
    // 获取当前玩家在指南中能看到的所有物品组列表喵~
    protected @Nonnull List<ItemGroup> getVisibleItemGroups(@Nonnull Player p, @Nonnull PlayerProfile profile) {
        // 使用LinkedList存储可见物品组，适合动态增删操作喵~
        List<ItemGroup> groups = new LinkedList<>();

        // 遍历所有已注册的物品组，逐一判断是否对玩家可见喵~
        for (ItemGroup group : Slimefun.getRegistry().getAllItemGroups()) {
            try {
                // 喵~防御：FlexItemGroup有自定义可见逻辑，需单独调用isVisible判断避免错误显示喵~
                if (group instanceof FlexItemGroup flexItemGroup) {
                    // FlexItemGroup需结合当前指南模式判断是否对玩家可见喵~
                    if (flexItemGroup.isVisible(p, profile, getMode())) {
                        groups.add(group);
                    }
                } else if (!group.isHidden(p)) {
                    // 普通物品组只要对玩家不隐藏就加入可见列表喵~
                    groups.add(group);
                }
            } catch (Exception | LinkageError x) {
                // 喵~防御：捕获任意异常和类加载错误，避免一个坏插件附加包导致整个指南崩溃喵~
                SlimefunAddon addon = group.getAddon();

                if (addon != null) {
                    // 如果物品组有所属附加包，用附加包自己的日志器记录错误喵~
                    addon.getLogger().log(Level.SEVERE, x, () -> "Could not display item group: " + group);
                } else {
                    // 没有附加包时使用Slimefun全局日志器记录错误喵~
                    Slimefun.logger().log(Level.SEVERE, x, () -> "Could not display item group: " + group);
                }
            }
        }

        return groups;
    }

    /*
     * 整体思路：打开指南主菜单，显示所有对玩家可见的物品组图标，支持翻页喵~
     * 输入：玩家档案profile，当前要显示的页码page（从1开始）
     * 输出：无返回值，直接为玩家打开GUI菜单
     * 边界条件：玩家为null时立即返回；生存模式下会清空历史并记录当前页；物品组超过36个时分页喵~
     */
    @Override
    public void openMainMenu(PlayerProfile profile, int page) {
        // 从档案中获取在线玩家对象喵~
        Player p = profile.getPlayer();

        // 喵~防御：玩家离线时p为null，直接返回避免后续空指针崩溃喵~
        if (p == null) {
            return;
        }

        // 生存模式下需要记录导航历史，清空历史并设置当前主菜单页码喵~
        if (isSurvivalMode()) {
            GuideHistory history = profile.getGuideHistory();
            // 清空之前的浏览历史，主菜单是导航起点喵~
            history.clear();
            // 记录主菜单当前所在页码，用于"返回主菜单"功能喵~
            history.setMainMenuPage(page);
        }

        // 创建指南箱子界面菜单喵~
        ChestMenu menu = create(p);
        // 获取当前玩家可见的所有物品组列表喵~
        List<ItemGroup> itemGroups = getVisibleItemGroups(p, profile);

        // 物品组从槽位9开始填充（0-8是顶部导航栏）喵~
        int index = 9;
        // 在菜单顶部绘制搜索/设置等导航按钮喵~
        createHeader(p, profile, menu);

        // 计算当前页第一个要显示的物品组下标（减1是因为循环开始前会先自增）喵~
        int target = (MAX_ITEM_GROUPS * (page - 1)) - 1;

        // 主人注意：此while循环遍历当前页需要显示的物品组，物品组数量超大时性能可接受喵~
        // 循环条件：还有物品组未显示 且 当前页槽位还未填满喵~
        while (target < (itemGroups.size() - 1) && index < MAX_ITEM_GROUPS + 9) {
            // 移动到下一个要显示的物品组下标喵~
            target++;

            // 取出当前下标的物品组喵~
            ItemGroup group = itemGroups.get(target);
            // 在菜单指定槽位绘制物品组图标喵~
            showItemGroup(menu, p, profile, group, index);

            // 槽位下标后移一格喵~
            index++;
        }

        // 计算总页数：若当前页已是最后一页则总页数=当前页，否则按物品组总数除以每页最大数喵~
        int pages = target == itemGroups.size() - 1 ? page : (itemGroups.size() - 1) / MAX_ITEM_GROUPS + 1;

        // 在槽位46添加上一页按钮喵~
        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            // 计算上一页页码喵~
            int next = page - 1;

            // 确保页码合法（大于0且与当前页不同）才跳转喵~
            if (next != page && next > 0) {
                openMainMenu(profile, next);
            }

            return false;
        });

        // 在槽位52添加下一页按钮喵~
        menu.addItem(52, ChestMenuUtils.getNextButton(p, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            // 计算下一页页码喵~
            int next = page + 1;

            // 确保页码合法（不超过总页数且与当前页不同）才跳转喵~
            if (next != page && next <= pages) {
                openMainMenu(profile, next);
            }

            return false;
        });

        // 打开菜单给玩家喵~
        menu.open(p);
    }

    /*
     * 整体思路：在主菜单的指定槽位显示一个物品组图标，已解锁的组可点击进入，未解锁的显示锁定提示喵~
     * 输入：menu菜单、p玩家、profile档案、group物品组、index槽位下标
     * 输出：无返回值，直接修改menu的槽位内容喵~
     * 边界条件：LockedItemGroup需要检查是否满足前置条件，不满足则显示屏障图标+前置提示喵~
     */
    private void showItemGroup(ChestMenu menu, Player p, PlayerProfile profile, ItemGroup group, int index) {
        // 判断：非锁定组 或 创意模式 或 玩家已解锁前置 → 正常显示图标可点击喵~
        if (!(group instanceof LockedItemGroup)
                || !isSurvivalMode()
                || ((LockedItemGroup) group).hasUnlocked(p, profile)) {
            // 在菜单槽位放置物品组图标喵~
            menu.addItem(index, group.getItem(p));
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                // 点击后打开该物品组第一页喵~
                openItemGroup(profile, group, 1);
                return false;
            });
        } else {
            // 玩家未满足前置条件，构建锁定提示lore喵~
            List<String> lore = new ArrayList<>();
            // 添加空行分隔喵~
            lore.add("");

            // 从本地化配置读取"locked-itemgroup"提示文本逐行添加喵~
            for (String line : Slimefun.getLocalization().getMessages(p, "guide.locked-itemgroup")) {
                lore.add(ChatColor.WHITE + line);
            }

            // 再添加空行分隔喵~
            lore.add("");

            // 列出所有前置物品组名称，让玩家知道需要先解锁哪些组喵~
            for (ItemGroup parent : ((LockedItemGroup) group).getParents()) {
                lore.add(parent.getItem(p).getItemMeta().getDisplayName());
            }

            // 用屏障方块图标显示锁定状态，标题带锁定文字和物品组名称喵~
            menu.addItem(
                    index,
                    new CustomItemStack(
                            Material.BARRIER,
                            "&4"
                                    + Slimefun.getLocalization().getMessage(p, "guide.locked")
                                    + " &7- &f"
                                    + group.getItem(p).getItemMeta().getDisplayName(),
                            lore.toArray(new String[0])));
            // 锁定状态的物品组点击无效，使用空点击处理器喵~
            menu.addMenuClickHandler(index, ChestMenuUtils.getEmptyClickHandler());
        }
    }

    /*
     * 整体思路：打开指定物品组的内容页，显示组内所有未禁用的Slimefun物品，支持翻页喵~
     * 输入：玩家档案profile，要打开的物品组itemGroup，当前页码page（从1开始）
     * 输出：无返回值，直接为玩家打开GUI菜单
     * 边界条件：玩家null时返回；FlexItemGroup有自己的open逻辑；生存模式需记录浏览历史；禁用的物品跳过不显示喵~
     */
    @Override
    @ParametersAreNonnullByDefault
    public void openItemGroup(PlayerProfile profile, ItemGroup itemGroup, int page) {
        // 从档案中获取在线玩家对象喵~
        Player p = profile.getPlayer();

        // 喵~防御：玩家离线时p为null，直接返回避免后续空指针崩溃喵~
        if (p == null) {
            return;
        }

        // 喵~防御：FlexItemGroup有自定义的open方法，直接委托给它处理而不走普通逻辑喵~
        if (itemGroup instanceof FlexItemGroup flexItemGroup) {
            // FlexItemGroup自己控制打开界面的方式喵~
            flexItemGroup.open(p, profile, getMode());
            return;
        }

        // 生存模式下将当前物品组和页码记录到浏览历史，方便"返回"功能喵~
        if (isSurvivalMode()) {
            profile.getGuideHistory().add(itemGroup, page);
        }

        // 创建指南箱子界面菜单喵~
        ChestMenu menu = create(p);
        // 绘制顶部导航栏（搜索/设置等按钮）喵~
        createHeader(p, profile, menu);

        // 在槽位1添加返回上一页按钮喵~
        addBackButton(menu, 1, p, profile);

        // 根据物品组物品总数和每页最大数计算总页数喵~
        int pages = (itemGroup.getItems().size() - 1) / MAX_ITEM_GROUPS + 1;

        // 在槽位46添加上一页按钮喵~
        menu.addItem(46, ChestMenuUtils.getPreviousButton(p, page, pages));
        menu.addMenuClickHandler(46, (pl, slot, item, action) -> {
            // 计算上一页页码喵~
            int next = page - 1;

            // 页码合法才跳转喵~
            if (next != page && next > 0) {
                openItemGroup(profile, itemGroup, next);
            }

            return false;
        });

        // 在槽位52添加下一页按钮喵~
        menu.addItem(52, ChestMenuUtils.getNextButton(p, page, pages));
        menu.addMenuClickHandler(52, (pl, slot, item, action) -> {
            // 计算下一页页码喵~
            int next = page + 1;

            // 页码合法才跳转喵~
            if (next != page && next <= pages) {
                openItemGroup(profile, itemGroup, next);
            }

            return false;
        });

        // 物品从槽位9开始填充（0-8是顶部导航栏）喵~
        int index = 9;
        // 当前页第一个物品在物品组完整列表中的下标喵~
        int itemGroupIndex = MAX_ITEM_GROUPS * (page - 1);

        // 主人注意：循环最多执行MAX_ITEM_GROUPS次，当物品组物品量很大时也只处理当前页36个，性能可控喵~
        for (int i = 0; i < MAX_ITEM_GROUPS; i++) {
            // 计算当前循环对应的物品在物品组列表中的实际下标喵~
            int target = itemGroupIndex + i;

            // 喵~防御：当前下标超出物品组物品总数时退出循环，防止越界喵~
            if (target >= itemGroup.getItems().size()) {
                break;
            }

            // 取出当前下标的Slimefun物品喵~
            SlimefunItem sfitem = itemGroup.getItems().get(target);

            // 只显示未在当前世界禁用的物品，禁用的物品跳过不占槽位喵~
            if (!sfitem.isDisabledIn(p.getWorld())) {
                // 在菜单指定槽位显示该Slimefun物品并绑定点击事件喵~
                displaySlimefunItem(menu, itemGroup, p, profile, sfitem, page, index);
                // 槽位后移一格准备放下一个物品喵~
                index++;
            }
        }

        // 打开菜单给玩家喵~
        menu.open(p);
    }

    /*
     * 整体思路：在物品组界面的指定槽位显示单个Slimefun物品，根据权限/研究解锁状态显示不同的图标和提示喵~
     * 输入：menu菜单、itemGroup所属物品组、p玩家、profile档案、sfitem目标物品、page当前页、index槽位
     * 输出：无返回值，直接修改menu槽位内容喵~
     * 边界条件：
     *   1. 无权限 → 显示"无权限"图标+提示lore
     *   2. 生存模式且有研究且未解锁 → 显示锁定图标+解锁所需条件（经验/金币/技能等级/前置物品）
     *   3. 已解锁/创意模式 → 正常显示物品，点击进入详情页或直接给予物品喵~
     */
    private void displaySlimefunItem(
            ChestMenu menu,
            ItemGroup itemGroup,
            Player p,
            PlayerProfile profile,
            SlimefunItem sfitem,
            int page,
            int index) {
        // 获取该物品绑定的研究对象，若为null说明无需研究直接可用喵~
        Research research = sfitem.getResearch();

        // 生存模式下检查玩家是否有权限使用该物品喵~
        if (isSurvivalMode() && !hasPermission(p, sfitem)) {
            // 无权限时获取提示lore文本喵~
            List<String> message = Slimefun.getPermissionsService().getLore(sfitem);
            // 用"无权限"图标替代原物品图标，显示权限提示喵~
            menu.addItem(
                    index,
                    new CustomItemStack(
                            ChestMenuUtils.getNoPermissionItem(),
                            sfitem.getItemName(),
                            message.toArray(new String[0])));
            // 无权限状态点击无效喵~
            menu.addMenuClickHandler(index, ChestMenuUtils.getEmptyClickHandler());
        } else if (isSurvivalMode() && research != null && !profile.hasUnlocked(research)) {
            // 生存模式、该物品需要研究、且玩家尚未解锁 → 显示锁定状态和解锁所需条件喵~
            List<String> lore = new ArrayList<>();

            // 从AuraSkills API获取当前玩家的技能数据喵~
            AuraSkillsApi skillApi = AuraSkillsApi.get();
            // 获取玩家的技能用户对象，用于读取各技能等级喵~
            SkillsUser playerSkill = skillApi.getUser(p.getUniqueId());

            // 存放技能等级需求的lore行列表喵~
            List<String> skillLore = new ArrayList<>();
            // 标记该研究是否有技能等级要求喵~
            boolean hasSkill = false;

            // 喵~防御：playerSkill为null说明AuraSkills未加载或数据不可用喵~
            if (playerSkill == null) {
                // 如果研究有任意技能等级要求但AuraSkills不可用，提示玩家喵~
                if (research.getArcheryLevelNeed() > 0
                        || research.getFarmingLevelNeed() > 0
                        || research.getFightingLevelNeed() > 0
                        || research.getFishingLevelNeed() > 0
                        || research.getForagingLevelNeed() > 0
                        || research.getMiningLevelNeed() > 0
                        || research.getAgilityLevelNeed() > 0
                        || research.getDefenseLevelNeed() > 0
                        || research.getExcavationLevelNeed() > 0
                        || research.getAlchemyLevelNeed() > 0
                        || research.getEnchantingLevelNeed() > 0) {
                    // 告知玩家AuraSkills服务不可用喵~
                    skillLore.add("&cAuraSkills 未加载或数据不可用");
                    hasSkill = true;
                }
            } else {
                // 检查弓箭技能等级要求，>1表示有需求（1是默认无要求值）喵~
                if (research.getArcheryLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家弓箭等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.ARCHERY) >= research.getArcheryLevelNeed()) {
                        skillLore.add("&b弓箭手 &e" + research.getArcheryLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b弓箭手 &e" + research.getArcheryLevelNeed() + "级&c×");
                    }
                }
                // 检查战斗技能等级要求喵~
                if (research.getFightingLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家战士等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.FIGHTING) >= research.getFightingLevelNeed()) {
                        skillLore.add("&b战士 &e" + research.getFightingLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b战士 &e" + research.getFightingLevelNeed() + "级&c×");
                    }
                }
                // 检查防御技能等级要求喵~
                if (research.getDefenseLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家防御等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.DEFENSE) >= research.getDefenseLevelNeed()) {
                        skillLore.add("&b防御 &e" + research.getDefenseLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b防御 &e" + research.getDefenseLevelNeed() + "级&c×");
                    }
                }
                // 检查农业技能等级要求喵~
                if (research.getFarmingLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家草药学等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.FARMING) >= research.getFarmingLevelNeed()) {
                        skillLore.add("&b草药学 &e" + research.getFarmingLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b草药学 &e" + research.getFarmingLevelNeed() + "级&c×");
                    }
                }
                // 检查伐木技能等级要求喵~
                if (research.getForagingLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家伐树等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.FORAGING) >= research.getForagingLevelNeed()) {
                        skillLore.add("&b伐树 &e" + research.getForagingLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b伐树 &e" + research.getForagingLevelNeed() + "级&c×");
                    }
                }
                // 检查采矿技能等级要求喵~
                if (research.getMiningLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家采掘等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.MINING) >= research.getMiningLevelNeed()) {
                        skillLore.add("&b采掘 &e" + research.getMiningLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b采掘 &e" + research.getMiningLevelNeed() + "级&c×");
                    }
                }
                // 检查钓鱼技能等级要求喵~
                if (research.getFishingLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家钓鱼等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.FISHING) >= research.getFishingLevelNeed()) {
                        skillLore.add("&b钓鱼 &e" + research.getFishingLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b钓鱼 &e" + research.getFishingLevelNeed() + "级&c×");
                    }
                }
                // 检查挖掘技能等级要求喵~
                if (research.getExcavationLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家挖掘等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.EXCAVATION) >= research.getExcavationLevelNeed()) {
                        skillLore.add("&b挖掘 &e" + research.getExcavationLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b挖掘 &e" + research.getExcavationLevelNeed() + "级&c×");
                    }
                }
                // 检查敏捷技能等级要求喵~
                if (research.getAgilityLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家敏捷等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.AGILITY) >= research.getAgilityLevelNeed()) {
                        skillLore.add("&b敏捷 &e" + research.getAgilityLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b敏捷 &e" + research.getAgilityLevelNeed() + "级&c×");
                    }
                }
                // 检查炼金技能等级要求喵~
                if (research.getAlchemyLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家炼金术等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.ALCHEMY) >= research.getAlchemyLevelNeed()) {
                        skillLore.add("&b炼金术 &e" + research.getAlchemyLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b炼金术 &e" + research.getAlchemyLevelNeed() + "级&c×");
                    }
                }
                // 检查附魔技能等级要求喵~
                if (research.getEnchantingLevelNeed() > 1) {
                    hasSkill = true;
                    // 玩家附魔等级达标时显示绿色勾，否则红色叉喵~
                    if (playerSkill.getSkillLevel(Skills.ENCHANTING) >= research.getEnchantingLevelNeed()) {
                        skillLore.add("&b附魔 &e" + research.getEnchantingLevelNeed() + "级&a√");
                    } else {
                        skillLore.add("&b附魔 &e" + research.getEnchantingLevelNeed() + "级&c×");
                    }
                }
            }
            // 如果研究需要游戏币，检查玩家余额是否足够并显示达标/不达标符号喵~
            if (research.getMoneyCost() > 0) {
                // 通过VaultIntegration获取玩家余额并与所需费用对比喵~
                if (VaultIntegration.getPlayerBalance(p) >= research.getMoneyCost()) {
                    // 余额充足，显示绿色勾喵~
                    lore.add("&e" + String.format("%.2f", research.getMoneyCost()) + " 游戏币&a√");
                } else {
                    // 余额不足，显示红色叉喵~
                    lore.add("&e" + String.format("%.2f", research.getMoneyCost()) + " 游戏币&c×");
                }
            }
            // 如果研究需要经验等级，检查玩家等级是否足够喵~
            if (research.getLevelCost() > 0) {
                // 玩家当前等级与所需等级对比喵~
                if (p.getLevel() >= research.getLevelCost()) {
                    // 等级足够，显示绿色勾喵~
                    lore.add("&a" + research.getLevelCost() + " 级经验&a√");
                } else {
                    // 等级不足，显示红色叉喵~
                    lore.add("&a" + research.getLevelCost() + " 级经验&c×");
                }
            }

            // 标志变量：玩家是否已解锁该研究所依赖的所有前置物品的研究喵~
            boolean doesPlayerUnLockedNeed = true;
            // 喵~防御：先判断research不为null再遍历前置物品，避免空指针喵~
            if (sfitem.getResearch() != null)
                // 遍历研究的所有前置解锁物品，检查玩家是否都已解锁喵~
                for (SlimefunItem item : sfitem.getResearch().getNeedUnlockedItems()) {
                    // 如果某个前置物品的研究玩家还未解锁且该物品未被禁用，则标记为未满足前置条件喵~
                    if (item.getResearch() != null && !profile.hasUnlocked(item.getResearch()) && !item.isDisabled()) {
                        doesPlayerUnLockedNeed = false;
                        break;
                    }
                }

            // 判断：研究存在 且 (没有前置物品要求 或 玩家已满足所有前置解锁)喵~
            if (sfitem.getResearch() != null
                    && (sfitem.getResearch().getNeedUnlockedItems().isEmpty() || doesPlayerUnLockedNeed)) {
                // 没有技能等级要求的分支：只显示基础费用lore喵~
                if (!hasSkill) {
                    // 构建展示在指南中的物品锁定提示lore列表喵~
                    ArrayList<String> RealLore = new ArrayList<>();
                    // 显示物品的本地化名称喵~
                    RealLore.add("&f" + ItemUtils.getItemName(sfitem.getItem()));
                    // 显示物品的唯一ID喵~
                    RealLore.add("&7" + sfitem.getId());
                    // 显示"已锁定"标题喵~
                    RealLore.add("&4&l" + Slimefun.getLocalization().getMessage(p, "guide.locked"));
                    // 空行分隔喵~
                    RealLore.add("");
                    // 提示玩家可以单击解锁喵~
                    RealLore.add("&a> 单击解锁");
                    // 空行分隔喵~
                    RealLore.add("");
                    // 提示所需条件小标题喵~
                    RealLore.add("&7需要 &b");
                    // 将费用/等级要求逐行加入喵~
                    RealLore.addAll(lore);
                    // 用无权限图标显示该物品，带完整lore喵~
                    menu.addItem(
                            index,
                            new CustomItemStack(new CustomItemStack(ChestMenuUtils.getNoPermissionItem(), RealLore)));
                } else {
                    // 有技能等级要求的分支：在基础费用lore后追加技能要求喵~
                    ArrayList<String> RealLore = new ArrayList<>();
                    // 显示物品名喵~
                    RealLore.add("&f" + ItemUtils.getItemName(sfitem.getItem()));
                    // 显示物品ID喵~
                    RealLore.add("&7" + sfitem.getId());
                    // 显示锁定标题喵~
                    RealLore.add("&4&l" + Slimefun.getLocalization().getMessage(p, "guide.locked"));
                    RealLore.add("");
                    RealLore.add("&a> 单击解锁");
                    RealLore.add("");
                    RealLore.add("&7需要 &b");
                    // 添加货币/等级等基础费用喵~
                    RealLore.addAll(lore);
                    // 追加技能等级要求小标题喵~
                    RealLore.add("&7需要技能等级:");
                    // 追加各技能达标状态喵~
                    RealLore.addAll(skillLore);
                    // 用无权限图标展示喵~
                    menu.addItem(
                            index,
                            new CustomItemStack(new CustomItemStack(ChestMenuUtils.getNoPermissionItem(), RealLore)));
                }
            } else {
                // 玩家还有前置物品未解锁，需要显示前置物品列表喵~
                // 用StringBuilder构建前置物品名称列表字符串喵~
                StringBuilder sb = new StringBuilder();
                sb.append("&c[");
                // 遍历所有前置物品，找出玩家尚未解锁的并加入列表喵~
                for (SlimefunItem item : sfitem.getResearch().getNeedUnlockedItems()) {
                    if (isSurvivalMode()
                            && item.getResearch() != null
                            && !item.isDisabled()
                            && !profile.hasUnlocked(item.getResearch())
                            && !item.getItemName().isEmpty()) {
                        // 将未解锁的前置物品名追加到列表喵~
                        sb.append(item.getItemName());
                        sb.append("&7,");
                    }
                }
                // 删除末尾多余的分隔符"&7,"（长度3）喵~
                sb.delete(sb.length() - 3, sb.length());
                sb.append("&c]");
                // 最终的前置物品名称字符串喵~
                String loreNeedUnlock = sb.toString();
                // 没有技能等级要求，只显示前置物品提示喵~
                if (!hasSkill) {
                    ArrayList<String> RealLore = new ArrayList<>();
                    RealLore.add("&f" + ItemUtils.getItemName(sfitem.getItem()));
                    RealLore.add("&7" + sfitem.getId());
                    RealLore.add("&4&l" + Slimefun.getLocalization().getMessage(p, "guide.locked"));
                    RealLore.add("");
                    RealLore.add("&a> 单击解锁");
                    RealLore.add("");
                    RealLore.add("&7需要 &b");
                    RealLore.addAll(lore);
                    // 追加前置解锁提示喵~
                    RealLore.add("&7在解锁这个物品前 你需要解锁下列物品:");
                    // 显示未解锁的前置物品名列表喵~
                    RealLore.add("&c" + loreNeedUnlock);
                    menu.addItem(
                            index,
                            new CustomItemStack(new CustomItemStack(ChestMenuUtils.getNoPermissionItem(), RealLore)));
                } else {
                    // 既有前置物品要求，又有技能等级要求，全部追加喵~
                    ArrayList<String> RealLore = new ArrayList<>();
                    RealLore.add("&f" + ItemUtils.getItemName(sfitem.getItem()));
                    RealLore.add("&7" + sfitem.getId());
                    RealLore.add("&4&l" + Slimefun.getLocalization().getMessage(p, "guide.locked"));
                    RealLore.add("");
                    RealLore.add("&a> 单击解锁");
                    RealLore.add("");
                    RealLore.add("&7需要 &b");
                    RealLore.addAll(lore);
                    RealLore.add("&7在解锁这个物品前 你需要解锁下列物品:");
                    RealLore.add("&c" + loreNeedUnlock);
                    // 追加技能等级要求喵~
                    RealLore.add("&7需要技能等级:");
                    RealLore.addAll(skillLore);
                    menu.addItem(
                            index,
                            new CustomItemStack(new CustomItemStack(ChestMenuUtils.getNoPermissionItem(), RealLore)));
                }
            }
            // 点击锁定物品时触发研究解锁流程喵~
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                // 调用研究的指南解锁方法，由Research对象处理经验消耗和状态更新喵~
                research.unlockFromGuide(this, p, profile, sfitem, itemGroup, page);
                return false;
            });
        } else {
            // 物品已解锁或不需要研究，直接在菜单槽位显示物品本体喵~
            menu.addItem(index, sfitem.getItem());
            menu.addMenuClickHandler(index, (pl, slot, item, action) -> {
                try {
                    // 生存模式下点击跳转到物品详情页喵~
                    if (isSurvivalMode()) {
                        displayItem(profile, sfitem, true);
                    } else if (pl.hasPermission("slimefun.cheat.items")) {
                        // 作弊模式且有权限：直接给玩家物品喵~
                        if (sfitem instanceof MultiBlockMachine) {
                            // 多方块机器无法作弊给予，发送提示消息喵~
                            Slimefun.getLocalization().sendMessage(pl, "guide.cheat.no-multiblocks");
                        } else {
                            // 克隆物品避免修改原始注册物品喵~
                            ItemStack clonedItem = sfitem.getItem().clone();

                            // shift点击时给予最大堆叠数量喵~
                            if (action.isShiftClicked()) {
                                clonedItem.setAmount(clonedItem.getMaxStackSize());
                            }

                            // 将物品直接放入玩家背包喵~
                            pl.getInventory().addItem(clonedItem);
                        }
                    } else {
                        /*
                         * Fixes #3548 - If for whatever reason,
                         * an unpermitted players gets access to this guide,
                         * this will be our last line of defense to prevent any exploit.
                         */
                        // 最后防线：无权限玩家不应能进入此分支，发送无权限提示喵~
                        Slimefun.getLocalization().sendMessage(pl, "messages.no-permission", true);
                    }
                } catch (Exception | LinkageError x) {
                    // 喵~防御：捕获所有运行时异常和类加载错误，打印错误消息避免玩家看到崩溃喵~
                    printErrorMessage(pl, sfitem, x);
                }

                return false;
            });
        }
    }

    /*
     * 整体思路：打开搜索结果页面，遍历所有已启用的Slimefun物品，过滤出名称包含搜索词的物品并展示喵~
     * 输入：玩家档案profile、搜索输入字符串input、是否加入历史记录addToHistory
     * 输出：无返回值，直接为玩家打开搜索结果GUI
     * 边界条件：玩家为null时立即返回；搜索结果最多显示35个（槽位9-43）；隐藏物品不参与搜索喵~
     */
    @Override
    @ParametersAreNonnullByDefault
    public void openSearch(PlayerProfile profile, String input, boolean addToHistory) {
        // 获取在线玩家对象喵~
        Player p = profile.getPlayer();

        // 喵~防御：玩家离线时p为null，直接返回避免后续空指针崩溃喵~
        if (p == null) {
            return;
        }

        // 创建搜索结果菜单，标题中包含经过截断处理的搜索关键词喵~
        ChestMenu menu = new ChestMenu(Slimefun.getLocalization()
                .getMessage(p, "guide.search.inventory")
                .replace("%item%", ChatUtils.crop(ChatColor.WHITE, input)));
        // 将输入转为小写且去除颜色代码，用于大小写不敏感的模糊匹配喵~
        String searchTerm = ChatColor.stripColor(input.toLowerCase(Locale.ROOT));

        // 若需要记录历史，将搜索词加入浏览历史便于"返回"功能喵~
        if (addToHistory) {
            profile.getGuideHistory().add(searchTerm);
        }

        // 禁止点击空格子喵~
        menu.setEmptySlotsClickable(false);
        // 绘制顶部导航栏喵~
        createHeader(p, profile, menu);
        // 在槽位1添加返回按钮喵~
        addBackButton(menu, 1, p, profile);

        // 物品结果从槽位9开始填充喵~
        int index = 9;
        // 主人注意：此处遍历所有已启用Slimefun物品，物品数量非常多时会有轻微性能开销，但通常可接受喵~
        // 查找匹配物品并填充进菜单喵~
        for (SlimefunItem slimefunItem : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            // 搜索结果最多显示到槽位43（共35个），超出则停止喵~
            if (index == 44) {
                break;
            }

            // 过滤：不隐藏 + 物品组可访问 + 名称匹配搜索词 三个条件都满足才显示喵~
            if (!slimefunItem.isHidden()
                    && isItemGroupAccessible(p, slimefunItem)
                    && isSearchFilterApplicable(slimefunItem, searchTerm)) {
                // 构造显示用物品：在原物品基础上修改lore，加上所属物品组名称提示喵~
                ItemStack itemstack = new CustomItemStack(slimefunItem.getItem(), meta -> {
                    // 获取物品所属分类用于显示在lore中喵~
                    ItemGroup itemGroup = slimefunItem.getItemGroup();
                    // 设置lore：空行 + 所属物品组名称喵~
                    meta.setLore(Arrays.asList(
                            "", ChatColor.DARK_GRAY + "\u21E8 " + ChatColor.WHITE + itemGroup.getDisplayName(p)));
                    // 隐藏多余的附魔、属性等原版tooltip避免杂乱显示喵~
                    meta.addItemFlags(
                            ItemFlag.HIDE_ATTRIBUTES,
                            ItemFlag.HIDE_ENCHANTS,
                            VersionedItemFlag.HIDE_ADDITIONAL_TOOLTIP);
                });

                // 将构造好的物品放入菜单槽位喵~
                menu.addItem(index, itemstack);
                menu.addMenuClickHandler(index, (pl, slot, itm, action) -> {
                    try {
                        // 作弊模式下直接给予物品喵~
                        if (!isSurvivalMode()) {
                            pl.getInventory().addItem(slimefunItem.getItem().clone());
                        } else {
                            // 生存模式下跳转到物品详情页喵~
                            displayItem(profile, slimefunItem, true);
                        }
                    } catch (Exception | LinkageError x) {
                        // 喵~防御：捕获所有异常，避免因某个物品异常导致玩家菜单崩溃喵~
                        printErrorMessage(pl, slimefunItem, x);
                    }

                    return false;
                });

                // 槽位后移喵~
                index++;
            }
        }

        // 打开搜索结果菜单给玩家喵~
        menu.open(p);
    }

    // 判断玩家是否有权访问指定Slimefun物品所属的物品组喵~
    // 若配置允许在搜索中显示隐藏组，或物品组本身对玩家可访问，则返回true喵~
    @ParametersAreNonnullByDefault
    private boolean isItemGroupAccessible(Player p, SlimefunItem slimefunItem) {
        // 配置允许搜索显示隐藏组 或 物品组对该玩家可访问，两种情况都算合法喵~
        return Slimefun.getConfigManager().isShowHiddenItemGroupsInSearch()
                || slimefunItem.getItemGroup().isAccessible(p);
    }

    // 判断指定Slimefun物品的名称是否包含搜索关键词，实现大小写不敏感的模糊匹配喵~
    @ParametersAreNonnullByDefault
    private boolean isSearchFilterApplicable(SlimefunItem slimefunItem, String searchTerm) {
        // 去除颜色代码并转小写，统一大小写后进行字符串匹配喵~
        String itemName = ChatColor.stripColor(slimefunItem.getItemName()).toLowerCase(Locale.ROOT);
        // 喵~防御：物品名为空时直接返回false，避免匹配空字符串造成误判喵~
        return !itemName.isEmpty() && (itemName.equals(searchTerm) || itemName.contains(searchTerm));
    }

    /*
     * 整体思路：根据ItemStack显示对应的合成配方详情页喵~
     * 若是Slimefun物品则委托给SlimefunItem重载；若是原版物品则查询原版配方并展示喵~
     * 输入：档案profile、物品itemStack、配方下标index（多配方时用于翻页）、是否加入历史addToHistory
     * 输出：无返回值，直接打开配方详情GUI
     * 边界条件：p/item为null或AIR时立即返回；物品没有原版配方时返回；配置禁止显示原版配方时返回喵~
     */
    @Override
    @ParametersAreNonnullByDefault
    public void displayItem(PlayerProfile profile, ItemStack item, int index, boolean addToHistory) {
        // 获取在线玩家对象喵~
        Player p = profile.getPlayer();

        // 喵~防御：玩家离线、物品为null或为空气时，均无法展示配方，直接返回喵~
        if (p == null || item == null || item.getType() == Material.AIR) {
            return;
        }

        // 尝试将物品匹配为Slimefun物品喵~
        SlimefunItem sfItem = SlimefunItem.getByItem(item);

        // 喵~防御：若是Slimefun物品，委托给专门的Slimefun物品展示方法处理喵~
        if (sfItem != null) {
            displayItem(profile, sfItem, addToHistory);
            return;
        }

        // 配置禁止显示原版配方时直接跳过喵~
        if (!Slimefun.getConfigManager().isShowVanillaRecipes()) {
            return;
        }

        // 查询原版合成配方喵~
        Recipe[] recipes = Slimefun.getMinecraftRecipeService().getRecipesFor(item);

        // 喵~防御：没有任何可用配方时不展示配方页喵~
        if (recipes.length == 0) {
            return;
        }

        // 展示原版Minecraft配方详情页喵~
        showMinecraftRecipe(recipes, index, item, profile, p, addToHistory);
    }

    /*
     * 整体思路：将原版配方数组中的指定下标配方渲染到GUI中，支持多配方翻页喵~
     * 输入：recipes所有配方、index当前显示哪条、item产出物、profile/p玩家信息、addToHistory是否记录历史
     * 输出：无返回值，直接打开配方详情GUI给玩家
     * 边界条件：配方无法被Dough识别时显示屏障占位；多配方时添加翻页按钮喵~
     */
    private void showMinecraftRecipe(
            Recipe[] recipes, int index, ItemStack item, PlayerProfile profile, Player p, boolean addToHistory) {
        // 取出当前要展示的那条配方喵~
        Recipe recipe = recipes[index];

        // 初始化9格配方材料数组喵~
        ItemStack[] recipeItems = new ItemStack[9];
        // 默认配方类型为NULL（无机器）喵~
        RecipeType recipeType = RecipeType.NULL;
        // 配方产出物初始为null喵~
        ItemStack result = null;

        // 尝试将Bukkit配方转为Dough封装的MinecraftRecipe以便读取配方形状喵~
        Optional<MinecraftRecipe<? super Recipe>> optional = MinecraftRecipe.of(recipe);
        // 创建异步材料选择任务，用于在GUI中轮播多材料选项喵~
        AsyncRecipeChoiceTask task = new AsyncRecipeChoiceTask();

        if (optional.isPresent()) {
            // 成功识别配方：填充配方材料格并获取配方类型和产出物喵~
            showRecipeChoices(recipe, recipeItems, task);

            // 从Dough的MinecraftRecipe对象构建RecipeType喵~
            recipeType = new RecipeType(optional.get());
            // 获取配方最终产出物喵~
            result = recipe.getResult();
        } else {
            // 无法识别的配方：中央格显示红色屏障提示无法展示喵~
            recipeItems = new ItemStack[] {
                null,
                null,
                null,
                null,
                new CustomItemStack(Material.BARRIER, "&4We are somehow unable to show you this Recipe :/"),
                null,
                null,
                null,
                null
            };
        }

        // 创建新的箱子界面菜单喵~
        ChestMenu menu = create(p);

        // 需要记录历史时，将当前物品+配方下标压入导航栈喵~
        if (addToHistory) {
            profile.getGuideHistory().add(item, index);
        }

        // 将配方材料、类型、产出物渲染到GUI中喵~
        displayItem(menu, profile, p, item, result, recipeType, recipeItems, task);

        // 当有多条配方时，在GUI底部显示翻页按钮喵~
        if (recipes.length > 1) {
            // 先将27-35槽位填充背景方块喵~
            for (int i = 27; i < 36; i++) {
                menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
            }

            // 槽位28放上一条配方按钮喵~
            menu.addItem(
                    28, ChestMenuUtils.getPreviousButton(p, index + 1, recipes.length), (pl, slot, action, stack) -> {
                        // 喵~防御：确保不低于第0条才能翻回上一条配方喵~
                        if (index > 0) {
                            showMinecraftRecipe(recipes, index - 1, item, profile, p, true);
                        }
                        return false;
                    });

            // 槽位34放下一条配方按钮喵~
            menu.addItem(34, ChestMenuUtils.getNextButton(p, index + 1, recipes.length), (pl, slot, action, stack) -> {
                // 喵~防御：确保不超出配方数组末尾才能翻到下一条喵~
                if (index < recipes.length - 1) {
                    showMinecraftRecipe(recipes, index + 1, item, profile, p, true);
                }
                return false;
            });
        }

        // 将菜单展示给玩家喵~
        menu.open(p);

        // 如果有需要异步轮播的材料选项，启动轮播任务喵~
        if (!task.isEmpty()) {
            task.start(menu.toInventory());
        }
    }

    /*
     * 整体思路：将原版配方的材料选项(RecipeChoice)解析成可展示的ItemStack并注册轮播任务喵~
     * 输入：泛型配方T、待填充的9格材料数组recipeItems、异步轮播任务task
     * 输出：无返回值，直接修改recipeItems数组并向task注册多材料轮播喵~
     * 边界条件：只有1格材料时放在中央槽位[4]；多格材料时逐一填充；每格多种材料时注册轮播喵~
     */
    private <T extends Recipe> void showRecipeChoices(T recipe, ItemStack[] recipeItems, AsyncRecipeChoiceTask task) {
        // 获取配方形状，返回每个格子对应的RecipeChoice数组喵~
        RecipeChoice[] choices = Slimefun.getMinecraftRecipeService().getRecipeShape(recipe);

        // 单格配方（如无形状配方）：只用中央格[4]展示喵~
        if (choices.length == 1 && choices[0] instanceof MaterialChoice materialChoice) {
            // 取材料选项的第一个材料填入中央格喵~
            recipeItems[4] = new ItemStack(materialChoice.getChoices().get(0));

            // 如果该格有多种可用材料，注册轮播任务以循环展示喵~
            if (materialChoice.getChoices().size() > 1) {
                task.add(recipeSlots[4], materialChoice);
            }
        } else {
            // 多格配方：遍历每个格子的材料选项喵~
            for (int i = 0; i < choices.length; i++) {
                if (choices[i] instanceof MaterialChoice materialChoice) {
                    // 用材料选项的第一个材料作为默认展示喵~
                    recipeItems[i] = new ItemStack(materialChoice.getChoices().get(0));

                    // 该格有多种材料时注册轮播任务喵~
                    if (materialChoice.getChoices().size() > 1) {
                        task.add(recipeSlots[i], materialChoice);
                    }
                }
            }
        }
    }

    /*
     * 整体思路：显示Slimefun物品的合成配方详情页，支持多配方分页喵~
     * 此重载默认从第0页(第一条配方)开始展示喵~
     * 输入：profile玩家档案、item目标Slimefun物品、addToHistory是否记录到浏览历史
     * 输出：无返回值，直接打开GUI给玩家
     * 边界条件：委托给带recipePage参数的重载方法处理喵~
     */
    @Override
    @ParametersAreNonnullByDefault
    // 默认从第一条配方(下标0)开始展示物品配方详情页喵~
    public void displayItem(PlayerProfile profile, SlimefunItem item, boolean addToHistory) {
        // 委托给带recipePage参数的重载，默认展示第0页配方喵~
        displayItem(profile, item, addToHistory, 0);
    }

    /*
     * 整体思路：展示Slimefun物品的完整配方详情页，包含wiki按钮、配方分页、RecipeDisplayItem附加配方列表喵~
     * 输入：profile玩家档案、item目标Slimefun物品、addToHistory是否加入历史、recipePage当前配方页下标
     * 输出：无返回值，直接为玩家打开GUI
     * 边界条件：玩家离线返回；有wiki页时显示知识书按钮；多配方时显示翻页按钮；
     *           recipePage自动夹紧到[0, totalPages-1]喵~
     */
    @ParametersAreNonnullByDefault
    public void displayItem(PlayerProfile profile, SlimefunItem item, boolean addToHistory, int recipePage) {
        // 获取在线玩家对象喵~
        Player p = profile.getPlayer();

        // 喵~防御：玩家离线时p为null，无法打开GUI直接返回喵~
        if (p == null) {
            return;
        }

        // 需要加入历史时，向SCG桥接层推入当前物品ID的嵌套详情记录喵~
        if (addToHistory) {
            ScgBridge.pushNestedDetail(p, item.getId());
        }

        // 创建新的箱子界面菜单喵~
        ChestMenu menu = create(p);
        // 获取物品绑定的wiki页面URL（Optional包装）喵~
        Optional<String> wiki = item.getWikipage();

        // 如果物品绑定了wiki链接，在槽位8显示知识书图标供玩家点击查看喵~
        if (wiki.isPresent()) {
            menu.addItem(
                    8,
                    new CustomItemStack(
                            Material.KNOWLEDGE_BOOK,
                            ChatColor.WHITE + Slimefun.getLocalization().getMessage(p, "guide.tooltips.wiki"),
                            "",
                            ChatColor.GRAY
                                    + "\u21E8 "
                                    + ChatColor.GREEN
                                    + Slimefun.getLocalization().getMessage(p, "guide.tooltips.open-itemgroup")));
            // 点击知识书按钮：关闭当前界面并向玩家发送wiki链接喵~
            menu.addMenuClickHandler(8, (pl, slot, itemstack, action) -> {
                // 先关闭当前界面避免GUI卡住喵~
                pl.closeInventory();
                // 通过聊天发送可点击的wiki网址给玩家喵~
                ChatUtils.sendURL(pl, wiki.get());
                return false;
            });
        }

        // 创建异步材料选择轮播任务，用于在配方格中循环展示多种可用材料喵~
        AsyncRecipeChoiceTask task = new AsyncRecipeChoiceTask();

        // 加入历史记录时，将当前Slimefun物品推入玩家的浏览导航栈喵~
        if (addToHistory) {
            // 将物品压入导航历史，供"返回上一页"功能使用喵~
            profile.getGuideHistory().add(item);
        }

        // 构建完整配方列表：主配方 + 所有额外附加配方喵~
        // 构建完整配方列表：第一条是主配方，后续追加附加配方喵~
        List<RecipeEntry> allRecipes = new ArrayList<>();
        // 将主配方（合成类型+材料+产出物）加入列表喵~
        allRecipes.add(new RecipeEntry(item.getRecipeType(), item.getRecipe(), item.getRecipeOutput()));
        // 追加物品注册的所有额外附加配方喵~
        allRecipes.addAll(item.getAdditionalRecipes());

        // 总页数等于所有配方的数量喵~
        int totalPages = allRecipes.size();
        // 将请求的页码夹紧到合法范围[0, totalPages-1]，防止越界喵~
        int currentPage = Math.max(0, Math.min(recipePage, totalPages - 1));
        // 取出当前页对应的配方条目喵~
        RecipeEntry current = allRecipes.get(currentPage);

        // 获取当前配方的产出物喵~
        ItemStack result = current.getRecipeOutput();
        // 获取当前配方的合成类型（如工作台/熔炉/古代祭坛等）喵~
        RecipeType recipeType = current.getRecipeType();
        // 获取当前配方的9格原料数组喵~
        ItemStack[] recipe = current.getRecipe();

        // 调用底层渲染方法，将配方材料/类型/产出渲染到GUI中喵~
        displayItem(menu, profile, p, item, result, recipeType, recipe, task);

        // 当物品有多条配方时，显示配方翻页按钮喵~
        // 有多条配方时，显示配方翻页按钮和当前页码指示器喵~
        if (totalPages > 1) {
            // 上一条配方按钮的槽位号喵~
            int prevSlot = 11;
            // 下一条配方按钮的槽位号喵~
            int nextSlot = 15;

            // 在槽位9显示当前配方页码指示器（如"配方 1 / 3"）喵~
            // 在槽位9放置纸张图标作为配方页码指示器，显示"配方 x / y"喵~
            menu.replaceExistingItem(
                    9,
                    new CustomItemStack(
                            Material.PAPER, ChatColor.WHITE + "配方 " + (currentPage + 1) + " / " + totalPages));
            // 页码指示器点击无效，绑定空点击处理器喵~
            menu.addMenuClickHandler(9, ChestMenuUtils.getEmptyClickHandler());

            // 当前不是第一页时渲染上一页按钮喵~
            // 当前不是第一页时，才渲染上一页按钮喵~
            if (currentPage > 0) {
                // 在上一页槽位放置翻页按钮图标喵~
                menu.replaceExistingItem(prevSlot, ChestMenuUtils.getPreviousButton(p, currentPage + 1, totalPages));
                // 绑定点击事件：跳转到上一条配方喵~
                menu.addMenuClickHandler(prevSlot, (pl, slot, itemstack, action) -> {
                    // 切换到上一条配方，不重复加入历史记录喵~
                    displayItem(profile, item, false, currentPage - 1);
                    return false;
                });
            }

            // 当前不是最后一页时渲染下一页按钮喵~
            // 当前不是最后一页时，才渲染下一页按钮喵~
            if (currentPage < totalPages - 1) {
                // 在下一页槽位放置翻页按钮图标喵~
                menu.replaceExistingItem(nextSlot, ChestMenuUtils.getNextButton(p, currentPage + 1, totalPages));
                // 绑定点击事件：跳转到下一条配方喵~
                menu.addMenuClickHandler(nextSlot, (pl, slot, itemstack, action) -> {
                    // 切换到下一条配方，不重复加入历史记录喵~
                    displayItem(profile, item, false, currentPage + 1);
                    return false;
                });
            }
        }

        // 如果物品实现了RecipeDisplayItem接口，额外渲染下方的关联配方展示区域喵~
        if (item instanceof RecipeDisplayItem recipeDisplayItem) {
            // 从第0页开始展示附加配方列表喵~
            displayRecipes(p, profile, menu, recipeDisplayItem, 0);
        }

        // 将渲染好的菜单展示给玩家喵~
        menu.open(p);

        // 如果有需要异步轮播的材料选项，启动后台轮播任务喵~
        if (!task.isEmpty()) {
            // 将背包Inventory传给轮播任务，任务会定期刷新对应槽位的显示物品喵~
            task.start(menu.toInventory());
        }
    }

    /*
     * 整体思路：底层GUI渲染方法，将配方材料/类型/产出物填充到箱子界面的对应槽位喵~
     * 输入：menu菜单、profile档案、p玩家、item来源物品(SlimefunItem或ItemStack)、output产出物、
     *        recipeType合成类型、recipe9格原料数组、task异步轮播任务
     * 输出：无返回值，直接修改menu槽位内容
     * 边界条件：多方块机器的材料可能匹配Tag通配，需注册轮播；点击原料时跳转到该原料的配方详情页喵~
     */
    private void displayItem(
            ChestMenu menu,
            PlayerProfile profile,
            Player p,
            Object item,
            ItemStack output,
            RecipeType recipeType,
            ItemStack[] recipe,
            AsyncRecipeChoiceTask task) {
        // 在槽位0添加返回按钮喵~
        addBackButton(menu, 0, p, profile);

        // 定义配方格点击处理器：点击任意配方材料格跳转到该材料的配方详情页喵~
        // 喵~防御：屏障物品是"无法展示"的占位符，点击不跳转喵~
        MenuClickHandler clickHandler = (pl, slot, itemstack, action) -> {
            try {
                // 喵~防御：物品不为null且不是屏障占位符才允许跳转喵~
                if (itemstack != null && itemstack.getType() != Material.BARRIER) {
                    // 跳转到该材料物品的配方详情，下标0即第一条配方，加入历史记录喵~
                    displayItem(profile, itemstack, 0, true);
                }
            } catch (Exception | LinkageError x) {
                // 喵~防御：捕获所有异常，避免某个物品导致整个配方页崩溃喵~
                printErrorMessage(pl, x);
            }
            return false;
        };

        // 判断来源物品是否为Slimefun物品，影响材料格的显示逻辑（锁定状态等）喵~
        boolean isSlimefunRecipe = item instanceof SlimefunItem;

        // 遍历9个配方材料格，依次获取展示用物品并放入对应槽位喵~
        // 主人注意：此循环固定9次，性能无忧喵~
        for (int i = 0; i < 9; i++) {
            // 根据是否为Slimefun配方决定材料的展示形式（锁定提示/直接显示）喵~
            ItemStack recipeItem = getDisplayItem(p, isSlimefunRecipe, recipe[i]);
            // 将材料物品放入配方槽位，并绑定点击跳转处理器喵~
            menu.addItem(recipeSlots[i], recipeItem, clickHandler);

            // 如果来源是多方块机器且材料非空，检查是否需要注册Tag轮播任务喵~
            if (recipeItem != null && item instanceof MultiBlockMachine) {
                // 遍历所有支持的Tag（如木头类/栅栏类等），判断该材料是否属于某个Tag喵~
                for (Tag<Material> tag : MultiBlock.getSupportedTags()) {
                    // 材料匹配到Tag时，注册Tag轮播任务让该格循环展示所有匹配材料喵~
                    if (tag.isTagged(recipeItem.getType())) {
                        // 将槽位和Tag注册到轮播任务，找到第一个匹配就break喵~
                        task.add(recipeSlots[i], tag);
                        break;
                    }
                }
            }
        }

        // 在槽位10展示合成类型图标（如工作台/古代祭坛等），点击无效喵~
        menu.addItem(10, recipeType.getItem(p), ChestMenuUtils.getEmptyClickHandler());
        // 在槽位16展示配方产出物，点击无效喵~
        menu.addItem(16, output, ChestMenuUtils.getEmptyClickHandler());
    }

    @ParametersAreNonnullByDefault
    /*
     * 整体思路：渲染指南菜单的顶部导航栏（0-8槽）和底部装饰栏（45-53槽）喵~
     * 包含背景格、设置按钮（槽位1）、搜索按钮（槽位7）喵~
     * 输入：p玩家、profile档案、menu要渲染的菜单
     * 输出：无返回值，直接修改menu槽位内容
     * 边界条件：三个参数均不能为null，否则抛出IllegalArgumentException喵~
     */
    public void createHeader(Player p, PlayerProfile profile, ChestMenu menu) {
        // 喵~防御：校验参数不为null，防止空指针导致GUI渲染崩溃喵~
        Validate.notNull(p, "The Player cannot be null!");
        Validate.notNull(profile, "The Profile cannot be null!");
        Validate.notNull(menu, "The Inventory cannot be null!");

        // 将顶部导航栏0-8槽位填充背景方块，防止玩家点击到空白格喵~
        for (int i = 0; i < 9; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }

        // Settings Panel - 在槽位1添加设置按钮供玩家打开指南设置面板喵~
        // 在槽位1放置设置按钮图标喵~
        menu.addItem(1, ChestMenuUtils.getMenuButton(p));
        // 点击槽位1的设置按钮：用玩家手持物品打开指南设置界面喵~
        menu.addMenuClickHandler(1, (pl, slot, item, action) -> {
            // 打开指南个人设置界面（语言/显示偏好等）喵~
            SlimefunGuideSettings.openSettings(pl, pl.getInventory().getItemInMainHand());
            return false;
        });

        // Search feature! - 在槽位7添加搜索按钮喵~
        // 在槽位7放置搜索按钮图标喵~
        menu.addItem(7, ChestMenuUtils.getSearchButton(p));
        // 点击槽位7的搜索按钮：关闭界面并开始等待玩家在聊天框输入搜索词喵~
        menu.addMenuClickHandler(7, (pl, slot, item, action) -> {
            // 先关闭当前界面，然后进入聊天输入等待状态喵~
            pl.closeInventory();

            // 向玩家发送提示消息，告知其在聊天框输入搜索关键词喵~
            Slimefun.getLocalization().sendMessage(pl, "guide.search.message");
            // 注册聊天输入监听，玩家下一条聊天消息会作为搜索词触发搜索喵~
            ChatInput.waitForPlayer(
                    Slimefun.instance(),
                    pl,
                    msg -> SlimefunGuide.openSearch(profile, msg, getMode(), isSurvivalMode()));

            return false;
        });

        // 将底部装饰栏45-53槽位填充背景方块，防止玩家点击到空白格喵~
        for (int i = 45; i < 54; i++) {
            menu.addItem(i, ChestMenuUtils.getBackground(), ChestMenuUtils.getEmptyClickHandler());
        }
    }

    /*
     * 整体思路：在指定槽位添加返回按钮，根据历史记录和当前是否在SCG外部视图决定按钮行为喵~
     * 生存模式且有历史记录时：左键=返回上一页，Shift+左键=返回主菜单或SCG
     * 否则：直接返回主菜单或SCG喵~
     * 输入：menu菜单、slot槽位号、p玩家、profile档案
     * 输出：无返回值，直接修改menu槽位内容喵~
     */
    private void addBackButton(ChestMenu menu, int slot, Player p, PlayerProfile profile) {
        // 获取玩家的指南浏览历史对象喵~
        GuideHistory history = profile.getGuideHistory();
        // 检查玩家当前是否处于SCG(自定义指南)的外部视图模式喵~
        boolean extView = ScgBridge.isInExternalView(p);
        // 根据是否在SCG外部视图，设置Shift点击的提示文本喵~
        String shiftLore = extView ? "&fShift + 左键: &7返回SCG分类" : "&fShift + 左键: &7返回主菜单";

        // 生存模式且历史记录超过1条时，显示"返回上一页"的完整返回按钮喵~
        if (isSurvivalMode() && history.size() > 1) {
            // 构建带"左键返回"和"Shift+左键"双功能提示的返回按钮图标喵~
            menu.addItem(slot, new CustomItemStack(ChestMenuUtils.getBackButton(p, "", "&f左键: &7返回上一页", shiftLore)));

            // 绑定返回按钮点击逻辑喵~
            menu.addMenuClickHandler(slot, (pl, s, is, action) -> {
                // 在SCG外部视图时，按键行为不同喵~
                if (ScgBridge.isInExternalView(pl)) {
                    // Shift点击：完全退出SCG视图，打开原版指南主界面喵~
                    if (action.isShiftClicked()) {
                        SlimefunGuide.openGuide(pl, pl.getInventory().getItemInMainHand());
                        // 普通点击：在SCG视图内向后导航到上一个物品喵~
                    } else {
                        handleExternalBack(pl, profile);
                    }
                    // 非SCG视图的Shift点击：返回主菜单喵~
                } else if (action.isShiftClicked()) {
                    // 跳转到主菜单上次记录的页码喵~
                    openMainMenu(profile, profile.getGuideHistory().getMainMenuPage());
                } else {
                    // 普通点击：在指南内回退到上一条浏览历史喵~
                    history.goBack(this);
                }
                return false;
            });

        } else {
            // 历史记录不足或非生存模式时，显示"返回指南"的单功能返回按钮喵~
            menu.addItem(
                    slot,
                    new CustomItemStack(ChestMenuUtils.getBackButton(
                            p,
                            "",
                            // 读取本地化的"返回指南"文字作为按钮提示喵~
                            ChatColor.GRAY + Slimefun.getLocalization().getMessage(p, "guide.back.guide"),
                            shiftLore)));
            // 绑定点击逻辑：无历史记录时只能返回主菜单或SCG入口喵~
            menu.addMenuClickHandler(slot, (pl, s, is, action) -> {
                if (ScgBridge.isInExternalView(pl)) {
                    if (action.isShiftClicked()) {
                        SlimefunGuide.openGuide(pl, pl.getInventory().getItemInMainHand());
                    } else {
                        handleExternalBack(pl, profile);
                    }
                    // 非SCG视图时直接跳转到主菜单记录的页码喵~
                } else {
                    openMainMenu(profile, profile.getGuideHistory().getMainMenuPage());
                }
                return false;
            });
        }
    }

    /*
     * 整体思路：处理SCG外部视图中的"返回"操作喵~
     * 从SCG导航栈弹出上一个物品ID，找到对应Slimefun物品并展示，若栈空则回到指南首页喵~
     * 输入：pl玩家、profile档案
     * 输出：无返回值，直接打开对应GUI
     * 边界条件：弹出的ID找不到对应Slimefun物品时不展示；栈空时打开指南入口界面喵~
     */
    private void handleExternalBack(Player pl, PlayerProfile profile) {
        // 从SCG导航栈弹出上一个物品的ID字符串喵~
        String prevItemId = ScgBridge.navigateBackItem(pl);
        // 喵~防御：prevItemId不为null才能执行回退，为null说明已到达历史起点喵~
        if (prevItemId != null) {
            // 根据ID查找对应的Slimefun物品注册对象喵~
            SlimefunItem sfItem = SlimefunItem.getById(prevItemId);
            // 抑制下次displayItem时自动向SCG栈push，避免回退操作被当成新导航记录喵~
            ScgBridge.suppressPush(pl);
            try {
                // 喵~防御：物品可能已被卸载或禁用，为null时跳过展示喵~
                if (sfItem != null) {
                    // 展示找到的Slimefun物品配方详情页，addToHistory=true以维护Slimefun内部历史喵~
                    displayItem(profile, sfItem, true);
                }
                // finally块确保无论是否异常都清除push抑制标记，避免后续导航异常喵~
            } finally {
                // 清除SCG的push抑制标记，恢复正常导航记录行为喵~
                ScgBridge.clearSuppressPush(pl);
            }
        } else {
            // 导航栈为空时直接用玩家手持指南物品重新打开指南入口界面喵~
            SlimefunGuide.openGuide(pl, pl.getInventory().getItemInMainHand());
        }
    }

    @ParametersAreNonnullByDefault
    /*
     * 整体思路：根据玩家是否已解锁该物品，返回合适的展示用ItemStack喵~
     * Slimefun配方中的原料若玩家未解锁则显示屏障图标+锁定提示；非Slimefun配方直接返回原物品喵~
     * 输入：p玩家、isSlimefunRecipe是否为Slimefun配方、item原料ItemStack
     * 输出：展示用ItemStack（可能是原物品或屏障占位）喵~
     * 边界条件：item为null时直接返回null（由调用方保证传入非null）喵~
     */
    private static @Nonnull ItemStack getDisplayItem(Player p, boolean isSlimefunRecipe, ItemStack item) {
        // 只有Slimefun配方才需要检查解锁状态，原版配方直接展示原物品喵~
        if (isSlimefunRecipe) {
            // 尝试将原料ItemStack匹配为Slimefun物品喵~
            SlimefunItem slimefunItem = SlimefunItem.getByItem(item);

            // 喵~防御：不是Slimefun物品时直接返回原始ItemStack，无需检查解锁喵~
            if (slimefunItem == null) {
                return item;
            }

            // 根据权限状态决定lore提示文字：有权限显示"需要解锁"，无权限显示"无权限"喵~
            String lore = hasPermission(p, slimefunItem)
                    ? "&f需要在 " + slimefunItem.getItemGroup().getDisplayName(p) + " 中解锁"
                    : "&f无权限";

            // 玩家可以使用该物品（已解锁且有权限）时直接展示原始物品喵~
            if (slimefunItem.canUse(p, false)) {
                return item;
            } else {
                // 玩家未解锁时返回屏障图标，带锁定提示和操作提示喵~
                return new CustomItemStack(
                        Material.BARRIER,
                        ItemUtils.getItemName(item),
                        "&4&l" + Slimefun.getLocalization().getMessage(p, "guide.locked"),
                        "",
                        lore);
            }
            // 非Slimefun配方（原版配方）直接返回原始物品喵~
        } else {
            return item;
        }
    }

    @ParametersAreNonnullByDefault
    /*
     * 整体思路：在GUI的36-53槽渲染RecipeDisplayItem的附加配方列表，支持分页喵~
     * 偶数索引(0,2,4...)放在输入区36-44，奇数索引(1,3,5...)放在输出区45-53喵~
     * 输入：p玩家、profile档案、menu菜单、sfItem实现了RecipeDisplayItem的物品、page当前页（0起）
     * 输出：无返回值，直接修改menu槽位
     * 边界条件：配方列表为空时不渲染；第0页时初始化标题栏；每页最多18个物品（9输入+9输出）喵~
     */
    private void displayRecipes(Player p, PlayerProfile profile, ChestMenu menu, RecipeDisplayItem sfItem, int page) {
        // 获取RecipeDisplayItem提供的展示配方列表（输入+输出交替排列）喵~
        List<ItemStack> recipes = sfItem.getDisplayRecipes();

        // 喵~防御：只有配方列表非空时才渲染附加配方区域喵~
        if (!recipes.isEmpty()) {
            // 在槽位53放null清空占位，为配方翻页按钮腾出空间喵~
            menu.addItem(53, null);

            // 第0页时初始化27-35槽位的标题栏，显示配方区域的标签文字喵~
            if (page == 0) {
                // 用背景物品+配方区标签文字填充标题栏，点击无效喵~
                for (int i = 27; i < 36; i++) {
                    menu.replaceExistingItem(
                            i, new CustomItemStack(ChestMenuUtils.getBackground(), sfItem.getRecipeSectionLabel(p)));
                    menu.addMenuClickHandler(i, ChestMenuUtils.getEmptyClickHandler());
                }
            }

            // 计算总页数：每页18个（9输入+9输出），向上取整喵~
            int pages = (recipes.size() - 1) / 18 + 1;

            // 在槽位28放置上一页按钮图标喵~
            menu.replaceExistingItem(28, ChestMenuUtils.getPreviousButton(p, page + 1, pages));
            // 绑定上一页点击事件：不是第0页才允许回退喵~
            menu.addMenuClickHandler(28, (pl, slot, itemstack, action) -> {
                // 喵~防御：页码大于0才翻页，防止越界喵~
                if (page > 0) {
                    // 跳转到上一页并播放翻页音效喵~
                    displayRecipes(pl, profile, menu, sfItem, page - 1);
                    SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(pl);
                }

                return false;
            });

            // 在槽位34放置下一页按钮图标喵~
            menu.replaceExistingItem(34, ChestMenuUtils.getNextButton(p, page + 1, pages));
            // 绑定下一页点击事件：还有未展示的配方才允许翻页喵~
            menu.addMenuClickHandler(34, (pl, slot, itemstack, action) -> {
                // 喵~防御：剩余配方总数大于当前已展示数量才翻页喵~
                if (recipes.size() > (18 * (page + 1))) {
                    // 跳转到下一页并播放翻页音效喵~
                    displayRecipes(pl, profile, menu, sfItem, page + 1);
                    SoundEffect.GUIDE_BUTTON_CLICK_SOUND.playFor(pl);
                }

                return false;
            });

            // 输入物品（偶数下标）从槽位36开始填充喵~
            int inputs = 36;
            // 输出物品（奇数下标）从槽位45开始填充喵~
            int outputs = 45;

            // 主人注意：每页固定遍历18次（输入输出交替），性能可控喵~
            for (int i = 0; i < 18; i++) {
                int slot;

                // 偶数下标对应输入物品，放在36-44区域喵~
                if (i % 2 == 0) {
                    slot = inputs;
                    inputs++;
                    // 奇数下标对应输出物品，放在45-53区域喵~
                } else {
                    slot = outputs;
                    outputs++;
                }

                // 将第i条配方的物品放入对应槽位喵~
                addDisplayRecipe(menu, profile, recipes, slot, i, page);
            }
        }
    }

    /*
     * 整体思路：将配方列表中第(i + page*18)条物品放入指定槽位，超出范围则清空该槽位喵~
     * 输入：menu菜单、profile档案、recipes配方列表、slot目标槽位、i当前组内下标、page当前页
     * 输出：无返回值，直接修改menu槽位
     * 边界条件：下标越界时清空槽位+绑定空处理器；clone防止修改原始配方数据；displayItem可能为null喵~
     */
    private void addDisplayRecipe(
            ChestMenu menu, PlayerProfile profile, List<ItemStack> recipes, int slot, int i, int page) {
        // 喵~防御：当前下标在配方列表范围内才取物品，否则走else清空槽位喵~
        if ((i + (page * 18)) < recipes.size()) {
            // 根据页码和组内下标计算在完整配方列表中的实际位置喵~
            ItemStack displayItem = recipes.get(i + (page * 18));

            /*
             * We want to clone this item to avoid corrupting the original
             * but we wanna make sure no stupid addon creator sneaked some nulls in here
             */
            // 喵~防御：clone物品避免修改原始配方数据，但先检查是否为null喵~
            if (displayItem != null) {
                // clone确保对展示物品的任何修改不影响注册的配方原数据喵~
                displayItem = displayItem.clone();
            }

            // 将物品放入指定槽位喵~
            menu.replaceExistingItem(slot, displayItem);

            // 只有第0页才绑定点击跳转事件（翻页后的物品点击不触发配方跳转）喵~
            if (page == 0) {
                // 点击该配方物品时跳转到其配方详情页喵~
                menu.addMenuClickHandler(slot, (pl, s, itemstack, action) -> {
                    // 跳转到被点击物品的配方详情页，从第0条配方开始，加入历史记录喵~
                    displayItem(profile, itemstack, 0, true);
                    return false;
                });
            }
            // 下标超出范围时清空该槽位并绑定空处理器，避免残留旧数据喵~
        } else {
            menu.replaceExistingItem(slot, null);
            menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
        }
    }

    @ParametersAreNonnullByDefault
    // 检查玩家对指定Slimefun物品的使用权限，委托给权限服务判断喵~
    // 封装成独立方法便于统一调用，避免重复引用permissionsService喵~
    private static boolean hasPermission(Player p, SlimefunItem item) {
        // 调用权限服务检查玩家是否有该物品的使用权限喵~
        return Slimefun.getPermissionsService().hasPermission(p, item);
    }

    // 创建一个新的指南箱子界面菜单喵~
    // 标题读取本地化配置，同时设置空格不可点击并绑定打开音效喵~
    private @Nonnull ChestMenu create(@Nonnull Player p) {
        // 创建标题为本地化指南标题的箱子界面菜单喵~
        ChestMenu menu = new ChestMenu(Slimefun.getLocalization().getMessage(p, "guide.title.main"));

        // 禁止点击空槽位，防止玩家误操作移动背包物品喵~
        menu.setEmptySlotsClickable(false);
        // 玩家打开菜单时播放指南翻页音效喵~
        menu.addMenuOpeningHandler(SoundEffect.GUIDE_BUTTON_CLICK_SOUND::playFor);
        return menu;
    }

    @ParametersAreNonnullByDefault
    // 向玩家发送简洁的内部错误提示，同时在服务器后台记录完整异常堆栈喵~
    // 用于指南操作中发生未知异常但无法确定是哪个物品引起时喵~
    private void printErrorMessage(Player p, Throwable x) {
        // 向玩家发送红色错误提示消息，告知联系管理员喵~
        p.sendMessage(ChatColor.DARK_RED + "服务器发生了一个内部错误. 请联系管理员处理.");
        // 在服务器后台以SEVERE级别记录完整异常信息，便于管理员排查喵~
        Slimefun.logger().log(Level.SEVERE, "在打开指南书里的 Slimefun 物品时发生了意外!", x);
    }

    @ParametersAreNonnullByDefault
    // 向玩家发送英文内部错误提示，同时通过物品的error方法记录更详细的上下文信息喵~
    // 用于明确知道是哪个Slimefun物品触发了异常时调用喵~
    private void printErrorMessage(Player p, SlimefunItem item, Throwable x) {
        // 向玩家发送英文错误提示（告知检查控制台）喵~
        p.sendMessage(ChatColor.DARK_RED
                + "An internal server error has occurred. Please inform an admin, check the console for"
                + " further info.");
        // 通过物品自身的error方法记录异常，同时附带物品在指南中被查看的上下文说明喵~
        item.error(
                "This item has caused an error message to be thrown while viewing it in the Slimefun" + " guide.", x);
    }
}
