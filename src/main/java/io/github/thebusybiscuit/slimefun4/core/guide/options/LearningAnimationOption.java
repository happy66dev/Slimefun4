package io.github.thebusybiscuit.slimefun4.core.guide.options;

import io.github.bakedlibs.dough.data.persistent.PersistentDataAPI;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.core.config.SlimefunConfigManager;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * {@link LearningAnimationOption} represents a setting in the Slimefun guide book.
 * It allows users to disable/enable the "learning animation",
 * the information in chat when doing a Slimefun research.
 *
 * @author martinbrom
 */
// \u5B66\u4E60\u52A8\u753B\u9009\u9879\u7C7B\uFF0C\u5B9E\u73B0 SlimefunGuideOption
// \u63A5\u53E3\uFF0C\u63A7\u5236\u73A9\u5BB6\u89E3\u9501\u7814\u7A76\u65F6\u662F\u5426\u5728\u804A\u5929\u6846\u64AD\u653E\u52A8\u753B\u6548\u679C\u55B5~
class LearningAnimationOption implements SlimefunGuideOption<Boolean> {

    @Nonnull
    @Override
    // \u8FD4\u56DE\u6B64\u9009\u9879\u5F52\u5C5E\u7684 SlimefunAddon\uFF08\u5373 Slimefun
    // \u4E3B\u63D2\u4EF6\u5B9E\u4F8B\uFF09\uFF0C\u7528\u4E8E\u6807\u8BC6\u8BE5\u914D\u7F6E\u9879\u7684\u6CE8\u518C\u6765\u6E90\u55B5~
    public SlimefunAddon getAddon() {
        return Slimefun
                .instance(); // \u83B7\u53D6 Slimefun \u5355\u4F8B\u4F5C\u4E3A\u5F52\u5C5E\u9644\u52A0\u5305\u55B5~
    }

    @Nonnull
    @Override
    // \u8FD4\u56DE\u6B64\u9009\u9879\u5728\u73A9\u5BB6 PersistentData
    // \u4E2D\u8BFB\u5199\u65F6\u4F7F\u7528\u7684\u552F\u4E00
    // NamespacedKey\uFF0C\u9632\u6B62\u4E0E\u5176\u4ED6\u952E\u540D\u51B2\u7A81\u55B5~
    public NamespacedKey getKey() {
        // \u4EE5 Slimefun \u547D\u540D\u7A7A\u95F4\u548C\u56FA\u5B9A\u5B57\u7B26\u4E32 "research_learning_animation"
        // \u6784\u9020\u552F\u4E00\u5B58\u50A8 key \u55B5~
        return new NamespacedKey(Slimefun.instance(), "research_learning_animation");
    }

    @Nonnull
    @Override
    /*
     * \u83B7\u53D6\u5728\u6307\u5357\u8BBE\u7F6E\u754C\u9762\u5C55\u793A\u7ED9\u73A9\u5BB6\u7684\u56FE\u6807 ItemStack \u55B5~
     * \u6574\u4F53\u601D\u8DEF\uFF1A
     *   1. \u5148\u68C0\u67E5\u7BA1\u7406\u5458\u662F\u5426\u5168\u5C40\u7981\u7528\u4E86\u7814\u7A76\u529F\u80FD\u6216\u5B66\u4E60\u52A8\u753B\uFF0C\u82E5\u7981\u7528\u5219\u4E0D\u5C55\u793A\u6B64\u9009\u9879\u55B5~
     *   2. \u5426\u5219\u8BFB\u53D6\u73A9\u5BB6\u5F53\u524D\u7684\u52A8\u753B\u5F00\u5173\u72B6\u6001\uFF0C\u6784\u5EFA\u5BF9\u5E94\u56FE\u6807\u4E0E\u591A\u884C\u8BF4\u660E\u6587\u5B57\u55B5~
     *   3. \u52A8\u753B\u5F00\u542F\u65F6\u56FE\u6807\u4E3A MAP\uFF0C\u5173\u95ED\u65F6\u4E3A PAPER\uFF0C\u6587\u6848\u5747\u6765\u81EA\u672C\u5730\u5316\u914D\u7F6E\u55B5~
     * \u8F93\u5165\uFF1A\u73A9\u5BB6\u5BF9\u8C61 p\u3001\u73A9\u5BB6\u624B\u6301\u7684\u6307\u5357 ItemStack guide
     * \u8F93\u51FA\uFF1AOptional<ItemStack>\uFF0C\u7BA1\u7406\u5458\u5168\u5C40\u7981\u7528\u65F6\u8FD4\u56DE empty()\uFF0C\u5426\u5219\u8FD4\u56DE\u5C01\u88C5\u597D\u7684\u5C55\u793A\u56FE\u6807\u55B5~
     * \u8FB9\u754C\u6761\u4EF6\uFF1A\u7814\u7A76\u7CFB\u7EDF\u672A\u542F\u7528\u6216\u52A8\u753B\u88AB\u5168\u5C40\u7981\u7528\u65F6\u76F4\u63A5\u8FD4\u56DE\u7A7A\uFF0C\u4E0D\u6E32\u67D3\u8BBE\u7F6E\u6309\u94AE\u55B5~
     */
    public Optional<ItemStack> getDisplayItem(@Nonnull Player p, @Nonnull ItemStack guide) {
        // \u83B7\u53D6 Slimefun
        // \u5168\u5C40\u914D\u7F6E\u7BA1\u7406\u5668\uFF0C\u7528\u4E8E\u67E5\u8BE2\u7814\u7A76\u548C\u5B66\u4E60\u52A8\u753B\u7684\u670D\u52A1\u7AEF\u5168\u5C40\u5F00\u5173\u72B6\u6001\u55B5~
        SlimefunConfigManager cfgManager = Slimefun.getConfigManager();

        // \u55B5~\u9632\u5FA1\uFF1A\u82E5\u7BA1\u7406\u5458\u5728 config.yml
        // \u4E2D\u5173\u95ED\u4E86\u7814\u7A76\u529F\u80FD\u6216\u5F3A\u5236\u7981\u7528\u4E86\u5B66\u4E60\u52A8\u753B\uFF0C\u5219\u6B64\u9009\u9879\u4E0D\u5BF9\u73A9\u5BB6\u5C55\u793A\uFF0C\u907F\u514D\u51FA\u73B0\u65E0\u6548\u914D\u7F6E\u9879\u55B5~
        if (!cfgManager.isResearchingEnabled() || cfgManager.isLearningAnimationDisabled()) {
            return Optional.empty(); // \u8FD4\u56DE empty
            // \u8868\u793A\u4E0D\u5728\u8BBE\u7F6E\u754C\u9762\u6E32\u67D3\u6B64\u6309\u94AE\u55B5~
        } else {
            // \u8BFB\u53D6\u73A9\u5BB6\u5F53\u524D\u5BF9"\u5B66\u4E60\u52A8\u753B"\u7684\u4E2A\u4EBA\u8BBE\u7F6E\uFF0C\u82E5\u4ECE\u672A\u8BBE\u7F6E\u8FC7\u5219\u9ED8\u8BA4\u4E3A true\uFF08\u52A8\u753B\u5F00\u542F\uFF09\u55B5~
            boolean enabled = getSelectedOption(p, guide).orElse(true);
            // \u6839\u636E\u5F00\u5173\u72B6\u6001\u62FC\u63A5\u672C\u5730\u5316 key \u540E\u7F00\uFF0C"enabled" \u6216
            // "disabled"\uFF0C\u5BF9\u5E94\u4E0D\u540C\u8BF4\u660E\u6587\u6848\u55B5~
            String optionState = enabled ? "enabled" : "disabled";
            // \u4ECE\u672C\u5730\u5316\u914D\u7F6E\u4E2D\u83B7\u53D6\u8BE5\u72B6\u6001\u4E0B\u56FE\u6807 lore
            // \u7684\u591A\u884C\u6587\u672C\u5217\u8868\uFF0C\u5411\u73A9\u5BB6\u89E3\u91CA\u6B64\u9009\u9879\u7684\u4F5C\u7528\u55B5~
            List<String> lore = Slimefun.getLocalization()
                    .getMessages(p, "guide.options.learning-animation." + optionState + ".text");
            // \u5728 lore
            // \u672B\u5C3E\u6DFB\u52A0\u4E00\u884C\u7A7A\u884C\uFF0C\u89C6\u89C9\u4E0A\u5206\u9694\u8BF4\u660E\u6587\u5B57\u4E0E\u4E0B\u65B9\u70B9\u51FB\u63D0\u793A\u55B5~
            lore.add("");
            // \u62FC\u63A5\u5E26 Unicode
            // \u53F3\u7BAD\u5934\u524D\u7F00\u7684\u70B9\u51FB\u63D0\u793A\u6587\u5B57\uFF0C\u544A\u77E5\u73A9\u5BB6\u70B9\u51FB\u8BE5\u56FE\u6807\u4F1A\u5207\u6362\u52A8\u753B\u5F00\u5173\u72B6\u6001\u55B5~
            lore.add("&7\u21E8 "
                    + Slimefun.getLocalization()
                            .getMessage(p, "guide.options.learning-animation." + optionState + ".click"));

            // \u6839\u636E\u52A8\u753B\u72B6\u6001\u9009\u62E9\u56FE\u6807\u6750\u8D28\uFF1A\u5F00\u542F\u7528
            // MAP\uFF08\u56FE\u6807\u66F4\u4E30\u5BCC\uFF09\uFF0C\u5173\u95ED\u7528
            // PAPER\uFF08\u6734\u7D20\uFF09\uFF0C\u9644\u4E0A lore \u6784\u5EFA\u5B8C\u6574\u56FE\u6807\u55B5~
            ItemStack item = new CustomItemStack(enabled ? Material.MAP : Material.PAPER, lore);
            return Optional.of(item); // \u5C06\u56FE\u6807\u5305\u88C5\u8FDB Optional
            // \u8FD4\u56DE\uFF0C\u4F9B\u8BBE\u7F6E\u754C\u9762\u6E32\u67D3\u55B5~
        }
    }

    @Override
    /*
     * \u73A9\u5BB6\u5728\u6307\u5357\u8BBE\u7F6E\u754C\u9762\u70B9\u51FB"\u5B66\u4E60\u52A8\u753B"\u56FE\u6807\u65F6\u89E6\u53D1\u6B64\u65B9\u6CD5\u55B5~
     * \u6574\u4F53\u601D\u8DEF\uFF1A\u8BFB\u53D6\u5F53\u524D\u5F00\u5173\u72B6\u6001\u53D6\u53CD\u540E\u4FDD\u5B58\uFF0C\u518D\u5237\u65B0\u8BBE\u7F6E\u754C\u9762\u8BA9\u73A9\u5BB6\u770B\u5230\u6700\u65B0\u72B6\u6001\u55B5~
     */
    public void onClick(@Nonnull Player p, @Nonnull ItemStack guide) {
        // \u5C06\u5F53\u524D\u52A8\u753B\u5F00\u5173\u72B6\u6001\u53D6\u53CD\uFF08true\u2192false \u6216
        // false\u2192true\uFF09\uFF0C\u5E76\u7ACB\u5373\u6301\u4E45\u5316\u4FDD\u5B58\u65B0\u72B6\u6001\u55B5~
        setSelectedOption(p, guide, !getSelectedOption(p, guide).orElse(true));
        // \u91CD\u65B0\u6253\u5F00\u6307\u5357\u8BBE\u7F6E\u754C\u9762\uFF0C\u5237\u65B0\u663E\u793A\u5207\u6362\u540E\u7684\u56FE\u6807\u4E0E\u6587\u6848\uFF0C\u8BA9\u73A9\u5BB6\u770B\u5230\u6700\u65B0\u6548\u679C\u55B5~
        SlimefunGuideSettings.openSettings(p, guide);
    }

    @Override
    /*
     * \u4ECE\u73A9\u5BB6\u7684 PersistentData \u4E2D\u8BFB\u53D6"\u5B66\u4E60\u52A8\u753B"\u9009\u9879\u7684\u5F53\u524D\u5E03\u5C14\u503C\u55B5~
     * \u6574\u4F53\u601D\u8DEF\uFF1A\u52A8\u753B\u72B6\u6001\u4EE5 byte \u5F62\u5F0F\u5B58\u50A8\uFF081=\u5F00\u542F\uFF0C0=\u5173\u95ED\uFF09\uFF0C
     *          \u82E5\u73A9\u5BB6\u5C1A\u672A\u5B58\u50A8\u8FC7\uFF08\u9996\u6B21\u4F7F\u7528\uFF09\u5219\u9ED8\u8BA4\u4E3A true\uFF08\u52A8\u753B\u5F00\u542F\uFF09\u55B5~
     * \u8F93\u51FA\uFF1AOptional<Boolean>\uFF0C\u59CB\u7EC8\u975E empty\uFF1Btrue \u8868\u793A\u52A8\u753B\u5F00\u542F\uFF0Cfalse \u8868\u793A\u52A8\u753B\u5173\u95ED\u55B5~
     */
    public Optional<Boolean> getSelectedOption(@Nonnull Player p, @Nonnull ItemStack guide) {
        // \u83B7\u53D6\u6B64\u914D\u7F6E\u9879\u7684 NamespacedKey\uFF0C\u7528\u4E8E\u5728\u73A9\u5BB6 PersistentData
        // \u4E2D\u5B9A\u4F4D\u5B58\u50A8\u7684\u503C\u55B5~
        NamespacedKey key = getKey();
        // \u55B5~\u9632\u5FA1\uFF1A\u82E5\u73A9\u5BB6 PersistentData \u4E2D\u4E0D\u5305\u542B\u6B64
        // key\uFF08\u4ECE\u672A\u8BBE\u7F6E\u8FC7\uFF09\uFF0C\u5219\u9ED8\u8BA4\u5F00\u542F\uFF08true\uFF09\uFF1B
        //         \u5426\u5219\u8BFB\u53D6 byte \u503C\uFF0C\u7B49\u4E8E 1
        // \u4E3A\u5F00\u542F\uFF0C\u5176\u4ED6\u503C\uFF080\uFF09\u4E3A\u5173\u95ED\u55B5~
        boolean value = !PersistentDataAPI.hasByte(p, key) || PersistentDataAPI.getByte(p, key) == (byte) 1;
        return Optional.of(value); // \u5305\u88C5\u8FDB Optional
        // \u8FD4\u56DE\uFF0C\u4FDD\u6301\u4E0E\u63A5\u53E3\u8FD4\u56DE\u7C7B\u578B\u4E00\u81F4\u55B5~
    }

    @Override
    /*
     * \u5C06"\u5B66\u4E60\u52A8\u753B"\u9009\u9879\u7684\u65B0\u5E03\u5C14\u503C\u5199\u5165\u73A9\u5BB6\u7684 PersistentData \u6301\u4E45\u5316\u5B58\u50A8\u55B5~
     * \u6574\u4F53\u601D\u8DEF\uFF1A\u7528 byte 1 \u4EE3\u8868\u5F00\u542F\uFF0Cbyte 0 \u4EE3\u8868\u5173\u95ED\uFF0C\u7ED1\u5B9A\u5230\u73A9\u5BB6\u5B9E\u4F53 NBT\uFF0C\u91CD\u542F\u670D\u52A1\u5668\u540E\u4F9D\u7136\u4FDD\u7559\u55B5~
     */
    public void setSelectedOption(@Nonnull Player p, @Nonnull ItemStack guide, @Nonnull Boolean value) {
        // \u5C06 Boolean \u8F6C\u6362\u4E3A byte\uFF08true\u21921\uFF0Cfalse\u21920\uFF09\uFF0C\u5B58\u5165\u73A9\u5BB6
        // PersistentData \u5B9E\u73B0\u8DE8\u4F1A\u8BDD\u6301\u4E45\u4FDD\u5B58\u55B5~
        PersistentDataAPI.setByte(p, getKey(), (byte) (value.booleanValue() ? 1 : 0));
    }
}
