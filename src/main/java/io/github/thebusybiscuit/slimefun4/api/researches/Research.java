package io.github.thebusybiscuit.slimefun4.api.researches;

import city.norain.slimefun4.VaultIntegration;
import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.user.SkillsUser;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerPreResearchEvent;
import io.github.thebusybiscuit.slimefun4.api.events.ResearchUnlockEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemState;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.player.PlayerProfile;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideImplementation;
import io.github.thebusybiscuit.slimefun4.core.services.localization.Language;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.setup.ResearchSetup;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Represents a research, which is bound to one
 * {@link SlimefunItem} or more and requires XP levels/In-game economy to unlock said item(s).
 *
 * @author TheBusyBiscuit
 * @see ResearchSetup
 * @see ResearchUnlockEvent
 */
public class Research implements Keyed {

    private final NamespacedKey key;
    private final int id;
    private final String name;
    private boolean enabled = true;
    private int levelCost;
    private double moneyCost;
    private List<SlimefunItem> needUnlockedItems = new ArrayList<>();
    private int farmingLevelNeed;
    // axe use
    private int foragingLevelNeed;
    private int miningLevelNeed;
    private int fishingLevelNeed;
    // shovel use
    private int excavationLevelNeed;
    // bow use
    private int archeryLevelNeed;
    private int defenseLevelNeed;
    private int fightingLevelNeed;
    // 敏捷
    private int agilityLevelNeed;
    private int enchantingLevelNeed;
    // 药水
    private int alchemyLevelNeed;

    public int getMiningLevelNeed() {
        return miningLevelNeed;
    }

    public void setMiningLevelNeed(int miningLevelNeed) {
        this.miningLevelNeed = miningLevelNeed;
    }

    public int getFarmingLevelNeed() {
        return farmingLevelNeed;
    }

    public int getFightingLevelNeed() {
        return fightingLevelNeed;
    }

    public void setFightingLevelNeed(int fightingLevelNeed) {
        this.fightingLevelNeed = fightingLevelNeed;
    }

    public int getAlchemyLevelNeed() {
        return alchemyLevelNeed;
    }

    public void setAlchemyLevelNeed(int alchemyLevelNeed) {
        this.alchemyLevelNeed = alchemyLevelNeed;
    }

    public int getEnchantingLevelNeed() {
        return enchantingLevelNeed;
    }

    public void setEnchantingLevelNeed(int enchantingLevelNeed) {
        this.enchantingLevelNeed = enchantingLevelNeed;
    }

    public int getAgilityLevelNeed() {
        return agilityLevelNeed;
    }

    public void setAgilityLevelNeed(int agilityLevelNeed) {
        this.agilityLevelNeed = agilityLevelNeed;
    }

    public int getFishingLevelNeed() {
        return fishingLevelNeed;
    }

    public void setFishingLevelNeed(int fishingLevelNeed) {
        this.fishingLevelNeed = fishingLevelNeed;
    }

    public int getDefenseLevelNeed() {
        return defenseLevelNeed;
    }

    public void setDefenseLevelNeed(int defenseLevelNeed) {
        this.defenseLevelNeed = defenseLevelNeed;
    }

    public int getForagingLevelNeed() {
        return foragingLevelNeed;
    }

    public void setForagingLevelNeed(int foragingLevelNeed) {
        this.foragingLevelNeed = foragingLevelNeed;
    }

    public int getArcheryLevelNeed() {
        return archeryLevelNeed;
    }

    public void setArcheryLevelNeed(int archeryLevelNeed) {
        this.archeryLevelNeed = archeryLevelNeed;
    }

    public int getExcavationLevelNeed() {
        return excavationLevelNeed;
    }

    public void setExcavationLevelNeed(int excavationLevelNeed) {
        this.excavationLevelNeed = excavationLevelNeed;
    }

    public void setFarmingLevelNeed(int farmingLevelNeed) {
        this.farmingLevelNeed = farmingLevelNeed;
    }

    private final List<SlimefunItem> items = new LinkedList<>();

    /**
     * The constructor for a {@link this}.
     *
     * Create a new research, then bind this research to the Slimefun items you want by calling
     * {@link #addItems(SlimefunItem...)}. Once you're finished, call {@link #register()}
     * to register it.
     *
     * @param key
     *            A unique identifier for this {@link Research}
     * @param id
     *            old way of identifying researches
     * @param defaultName
     *            The displayed name of this {@link Research}
     * @param levelCost
     *            The Cost in XP levels to unlock this {@link Research}
     * @param moneyCost
     *            The Cost in economy to unlock this {@link Research}
     *
     */
    public Research(@Nonnull NamespacedKey key, int id, @Nonnull String defaultName, int levelCost, double moneyCost) {
        Validate.notNull(key, "A NamespacedKey must be provided");
        Validate.notNull(defaultName, "A default name must be specified");

        this.key = key;
        this.id = id;
        this.name = defaultName;
        this.levelCost = levelCost;
        this.moneyCost = moneyCost;
    }

    /**
     * The constructor for a {@link Research}.
     *
     * Create a new research, then bind this research to the Slimefun items you want by calling
     * {@link #addItems(SlimefunItem...)}. Once you're finished, call {@link #register()}
     * to register it.
     *
     * @param key
     *            A unique identifier for this {@link Research}
     * @param id
     *            old way of identifying researches
     * @param defaultName
     *            The displayed name of this {@link Research}
     * @param defaultCost
     *            The Cost in XP levels to unlock this {@link Research}
     *
     */
    public Research(@Nonnull NamespacedKey key, int id, @Nonnull String defaultName, int defaultCost) {
        Validate.notNull(key, "A NamespacedKey must be provided");
        Validate.notNull(defaultName, "A default name must be specified");

        this.key = key;
        this.id = id;
        this.name = defaultName;
        this.levelCost = defaultCost;
        // By default, we use a fixed rate to convert currency cost from level directly
        this.moneyCost = 0;
    }

    @Override
    public @Nonnull NamespacedKey getKey() {
        return key;
    }

    /**
     * This method returns whether this {@link Research} is enabled.
     * {@code false} can mean that this particular {@link Research} was disabled or that
     * researches altogether have been disabled.
     *
     * @return Whether this {@link Research} is enabled or not
     */
    public void addNeedUnlockedItems(SlimefunItem item) {
        needUnlockedItems.add(item);
    }

    public void clearNeedUnlockedItems() {
        needUnlockedItems.clear();
    }

    public boolean isEnabled() {
        return Slimefun.getConfigManager().isResearchingEnabled() && enabled;
    }

    /**
     * Gets the ID of this {@link Research}.
     * This is the old way of identifying Researches, use a {@link NamespacedKey} in the future.
     *
     * @deprecated Numeric Ids for Researches are deprecated, use {@link #getKey()} for identification instead.
     *
     * @return The ID of this {@link Research}
     */
    @Deprecated
    public int getID() {
        return id;
    }

    public List<SlimefunItem> getNeedUnlockedItems() {
        return needUnlockedItems;
    }

    /**
     * This method gives you a localized name for this {@link Research}.
     * The name is automatically taken from the currently selected {@link Language} of
     * the specified {@link Player}.
     *
     * @param p
     *            The {@link Player} to translate this name for.
     *
     * @return The localized Name of this {@link Research}.
     */
    public @Nonnull String getName(@Nonnull Player p) {
        String localized = Slimefun.getLocalization().getResearchName(p, key);
        return localized != null ? localized : name;
    }

    /**
     * Retrieve the name of this {@link Research} without any localization nor coloring.
     *
     * @return The unlocalized, decolorized name for this {@link Research}
     */
    public @Nonnull String getUnlocalizedName() {
        return ChatColor.stripColor(name);
    }

    /**
     * Gets the cost in XP levels to unlock this {@link Research}.
     * Deprecated, use {@link Research#getLevelCost} instead.
     *
     * @return The cost in XP levels for this {@link Research}
     */
    @Deprecated
    public int getCost() {
        return levelCost;
    }

    /**
     * Gets the cost in XP levels to unlock this {@link Research}.
     *
     * @return The cost in XP levels for this {@link Research}
     */
    public int getLevelCost() {
        return levelCost;
    }

    /**
     * Sets the cost in XP levels to unlock this {@link Research}.
     * Deprecated, use {@link Research#setLevelCost(int)} instead.
     *
     * @param cost The cost in XP levels
     */
    @Deprecated
    public void setCost(int cost) {
        if (levelCost < 0) {
            throw new IllegalArgumentException("Research cost must be zero or greater!");
        }

        levelCost = cost;
    }

    /**
     * Sets the cost in XP levels to unlock this {@link Research}.
     *
     * @param levelCost The cost in XP levels
     */
    public void setLevelCost(int levelCost) {
        if (levelCost < 0) {
            throw new IllegalArgumentException("Research cost must be zero or greater!");
        }

        this.levelCost = levelCost;
    }

    /**
     * Bind the specified {@link SlimefunItem SlimefunItems} to this {@link Research}.
     *
     * @param items
     *            Instances of {@link SlimefunItem} to bind to this {@link Research}
     */
    public void addItems(SlimefunItem... items) {
        for (SlimefunItem item : items) {
            if (item != null) {
                item.setResearch(this);
            }
        }
    }

    /**
     * Bind the specified ItemStacks to this {@link Research}.
     *
     * @param items
     *            Instances of {@link ItemStack} to bind to this {@link Research}
     *
     * @return The current instance of {@link Research}
     */
    @Nonnull
    public Research addItems(ItemStack... items) {
        for (ItemStack item : items) {
            SlimefunItem sfItem = SlimefunItem.getByItem(item);

            if (sfItem != null) {
                sfItem.setResearch(this);
            }
        }

        return this;
    }

    /**
     * Lists every {@link SlimefunItem} that is bound to this {@link Research}.
     *
     * @return The Slimefun items bound to this {@link Research}.
     */
    @Nonnull
    public List<SlimefunItem> getAffectedItems() {
        return items;
    }

    /**
     * This method checks whether there is at least one enabled {@link SlimefunItem}
     * included in this {@link Research}.
     *
     * @return whether there is at least one enabled {@link SlimefunItem}
     * included in this {@link Research}.
     */
    public boolean hasEnabledItems() {
        for (SlimefunItem item : items) {
            if (item.getState() == ItemState.ENABLED) {
                return true;
            }
        }
        return false;
    }

    /**
     * Handle what to do when a {@link Player} clicks on an un-researched item in
     * a {@link SlimefunGuideImplementation}.
     *
     * @param guide
     *            The {@link SlimefunGuideImplementation} used.
     * @param player
     *            The {@link Player} who clicked on the item.
     * @param profile
     *            The {@link PlayerProfile} of that {@link Player}.
     * @param sfItem
     *            The {@link SlimefunItem} on which the {@link Player} clicked.
     * @param itemGroup
     *            The {@link ItemGroup} where the {@link Player} was.
     * @param page
     *            The page number of where the {@link Player} was in the {@link ItemGroup};
     *
     */
    @ParametersAreNonnullByDefault
    public void unlockFromGuide(
            SlimefunGuideImplementation guide,
            Player player,
            PlayerProfile profile,
            SlimefunItem sfItem,
            ItemGroup itemGroup,
            int page) {

        if (!Slimefun.getRegistry().getCurrentlyResearchingPlayers().contains(player.getUniqueId())) {
            if (profile.hasUnlocked(this)) {
                guide.openItemGroup(profile, itemGroup, page);
            } else {
                PlayerPreResearchEvent event = new PlayerPreResearchEvent(player, this, sfItem);
                Bukkit.getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    if (this.canUnlock(player)) {
                        guide.unlockItem(player, sfItem, pl -> guide.openItemGroup(profile, itemGroup, page));
                    }
                }
            }
        }
    }

    /**
     * Checks if the {@link Player} can unlock this {@link Research}.
     * <p>
     * 已魔改支持 Vault
     *
     * @param p The {@link Player} to check
     * @return Whether that {@link Player} can unlock this {@link Research}
     */
    public boolean canUnlock(@Nonnull Player p) {
        if (!isEnabled()) {
            return true;
        }

        boolean canUnlock;

        canUnlock = VaultIntegration.getPlayerBalance(p) >= moneyCost && p.getLevel() >= levelCost;

        boolean creativeResearch = p.getGameMode() == GameMode.CREATIVE
                && Slimefun.getConfigManager().isFreeCreativeResearchingEnabled();

        Optional<PlayerProfile> profileOptional = PlayerProfile.find(p);
        boolean hasUnlockNeed = true;
        if (!needUnlockedItems.isEmpty())
            for (SlimefunItem item : needUnlockedItems) {
                if (profileOptional.isPresent()
                        && !profileOptional.get().hasUnlocked(item.getResearch())
                        && !item.isDisabled()) {
                    hasUnlockNeed = false;
                    break;
                }
            }

        AuraSkillsApi skillApi = AuraSkillsApi.get();
        SkillsUser playerSkill = skillApi.getUser(p.getUniqueId());

        if (playerSkill.getSkillLevel(Skills.ARCHERY) < this.getArcheryLevelNeed()
                || playerSkill.getSkillLevel(Skills.FARMING) < this.getFarmingLevelNeed()
                || playerSkill.getSkillLevel(Skills.FIGHTING) < this.getFightingLevelNeed()
                || playerSkill.getSkillLevel(Skills.FISHING) < this.getFishingLevelNeed()
                || playerSkill.getSkillLevel(Skills.FORAGING) < this.getForagingLevelNeed()
                || playerSkill.getSkillLevel(Skills.MINING) < this.getMiningLevelNeed()
                || playerSkill.getSkillLevel(Skills.AGILITY) < this.getAgilityLevelNeed()
                || playerSkill.getSkillLevel(Skills.DEFENSE) < this.getDefenseLevelNeed()
                || playerSkill.getSkillLevel(Skills.EXCAVATION) < this.getExcavationLevelNeed()
                || playerSkill.getSkillLevel(Skills.ALCHEMY) < this.getAlchemyLevelNeed()
                || playerSkill.getSkillLevel(Skills.ENCHANTING) < this.getEnchantingLevelNeed()) {
            Slimefun.getLocalization().sendMessage(p, "messages.not-enough-skill", true);
            return false;
        }

        if (profileOptional.isPresent() && !hasUnlockNeed) {
            Slimefun.getLocalization().sendMessage(p, "messages.not-unlock-need", true);
            return false;
        }

        if (!(creativeResearch || canUnlock)) {
            Slimefun.getLocalization().sendMessage(p, "messages.not-enough-xp", true);
        }
        return creativeResearch || canUnlock;
    }

    /**
     * This unlocks this {@link Research} for the given {@link Player} without any form of callback.
     *
     * @param p
     *            The {@link Player} who should unlock this {@link Research}
     * @param instant
     *            Whether to unlock it instantly
     */
    public void unlock(@Nonnull Player p, boolean instant) {
        unlock(p, instant, null);
    }

    /**
     * Unlocks this {@link Research} for the specified {@link Player}.
     *
     * @param p
     *            The {@link Player} for which to unlock this {@link Research}
     * @param isInstant
     *            Whether to unlock this {@link Research} instantly
     * @param callback
     *            A callback which will be run when the {@link Research} animation completed
     */
    public void unlock(@Nonnull Player p, boolean isInstant, @Nullable Consumer<Player> callback) {
        PlayerProfile.get(p, new PlayerResearchTask(this, isInstant, callback));
    }

    /**
     * Registers this {@link Research}.
     */
    public void register() {
        Slimefun.getResearchCfg().setDefaultValue("enable-researching", true);
        String path = key.getNamespace() + '.' + key.getKey();

        if (Slimefun.getResearchCfg().contains(path + ".enabled")
                && !Slimefun.getResearchCfg().getBoolean(path + ".enabled")) {
            for (SlimefunItem item : new ArrayList<>(items)) {
                if (item != null) {
                    item.setResearch(null);
                }
            }

            enabled = false;
            return;
        }

        Slimefun.getResearchCfg().setDefaultValue(path + ".levelCost", getLevelCost());
        Slimefun.getResearchCfg().setDefaultValue(path + ".moneyCost", getMoneyCost());
        Slimefun.getResearchCfg().setDefaultValue(path + ".enabled", true);
        Slimefun.getResearchCfg().setDefaultValue(path + ".need-unlocked-items", new ArrayList<String>());
        Slimefun.getResearchCfg().setDefaultValue(path + ".farmingLevelNeed", 0);
        Slimefun.getResearchCfg().setDefaultValue(path + ".foragingLevelNeed", 0);
        Slimefun.getResearchCfg().setDefaultValue(path + ".miningLevelNeed", 0);
        Slimefun.getResearchCfg().setDefaultValue(path + ".fishingLevelNeed", 0);
        Slimefun.getResearchCfg().setDefaultValue(path + ".excavationLevelNeed", 0);
        Slimefun.getResearchCfg().setDefaultValue(path + ".archeryLevelNeed", 0);
        Slimefun.getResearchCfg().setDefaultValue(path + ".defenseLevelNeed", 0);
        Slimefun.getResearchCfg().setDefaultValue(path + ".fightingLevelNeed", 0);
        Slimefun.getResearchCfg().setDefaultValue(path + ".agilityLevelNeed", 0);
        Slimefun.getResearchCfg().setDefaultValue(path + ".enchantingLevelNeed", 0);
        Slimefun.getResearchCfg().setDefaultValue(path + ".alchemyLevelNeed", 0);

        setLevelCost(Slimefun.getResearchCfg().getInt(path + ".levelCost"));

        this.setMiningLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".miningLevelNeed"));
        this.setAgilityLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".agilityLevelNeed"));
        this.setAlchemyLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".alchemyLevelNeed"));
        this.setArcheryLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".archeryLevelNeed"));
        this.setDefenseLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".defenseLevelNeed"));
        this.setEnchantingLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".enchantingLevelNeed"));
        this.setFarmingLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".farmingLevelNeed"));
        this.setFishingLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".fishingLevelNeed"));
        this.setForagingLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".foragingLevelNeed"));
        this.setExcavationLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".excavationLevelNeed"));
        this.setFightingLevelNeed(
                Slimefun.getResearchCfg().getInt(key.getNamespace() + '.' + key.getKey() + ".fightingLevelNeed"));

        setMoneyCost(Slimefun.getResearchCfg().getInt(path + ".moneyCost"));
        enabled = true;

        Slimefun.getRegistry().getResearches().add(this);
    }

    /**
     * Unregisters this {@link Research}.
     */
    public void disable() {
        enabled = false;
        for (SlimefunItem item : new ArrayList<>(items)) {
            if (item != null) {
                item.setResearch(null);
            }
        }
        Slimefun.getRegistry().getResearches().remove(this);
    }

    /**
     * Attempts to get a {@link Research} with the given {@link NamespacedKey}.
     *
     * @param key the {@link NamespacedKey} of the {@link Research} you are looking for
     * @return An {@link Optional} with or without the found {@link Research}
     */
    @Nonnull
    public static Optional<Research> getResearch(@Nullable NamespacedKey key) {
        if (key == null) {
            return Optional.empty();
        }

        for (Research research : Slimefun.getRegistry().getResearches()) {
            if (research.getKey().equals(key)) {
                return Optional.of(research);
            }
        }

        return Optional.empty();
    }

    @Deprecated
    public static Optional<Research> getResearchByID(@Nonnull Integer oldID) {
        if (oldID == null) {
            return Optional.empty();
        }

        return Slimefun.getRegistry().getResearches().parallelStream()
                .filter(r -> r.id == oldID)
                .findFirst();
    }

    @Override
    public String toString() {
        return "Research (" + getKey() + ')';
    }

    public double getMoneyCost() {
        return moneyCost;
    }

    public void setMoneyCost(double currencyCost) {
        this.moneyCost = currencyCost;
    }
}
