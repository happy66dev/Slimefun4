package io.github.thebusybiscuit.slimefun4.core.guide.options;

import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuide;
import io.github.thebusybiscuit.slimefun4.core.guide.SlimefunGuideMode;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.SlimefunUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

// 指南模式选项类，实现SlimefunGuideOption接口，控制玩家指南的显示模式（普通/作弊）喵~
class GuideModeOption implements SlimefunGuideOption<SlimefunGuideMode> {

    @Nonnull
    @Override
    public SlimefunAddon getAddon() {
        // 返回Slimefun主插件实例作为此选项的所属附加包喵~
        return Slimefun.instance();
    }

    @Nonnull
    @Override
    public NamespacedKey getKey() {
        // 返回此选项的唯一命名空间键，用于在指南物品NBT中存储/读取模式值喵~
        return new NamespacedKey(Slimefun.instance(), "guide_mode");
    }

    /**
     * 生成指南模式切换选项的展示物品，仅对有作弊权限的玩家可见喵~
     * 输入：玩家对象p、当前手持的指南ItemStack
     * 输出：Optional包装的展示物品，无权限时返回Optional.empty()
     * 边界条件：玩家无权限时直接返回空，防止普通玩家看到作弊选项喵~
     */
    @Nonnull
    @Override
    public Optional<ItemStack> getDisplayItem(Player p, ItemStack guide) {
        // 喵~防御：玩家没有slimefun.cheat.items权限时不显示此选项，防止普通玩家误操作喵~
        if (!p.hasPermission("slimefun.cheat.items")) {
            // Only Players with the appropriate permission can access the cheat sheet
            return Optional.empty();
        }

        // 获取当前玩家已选择的指南模式，包装在Optional中喵~
        Optional<SlimefunGuideMode> current = getSelectedOption(p, guide);

        // 喵~防御：确保当前模式存在才继续构建展示物品，避免空值异常喵~
        if (current.isPresent()) {
            // 取出当前已选中的指南模式枚举值喵~
            SlimefunGuideMode selectedMode = current.get();
            // 创建一个空气物品作为初始容器，稍后根据模式替换材质喵~
            ItemStack item = new ItemStack(Material.AIR);

            // 根据当前模式决定展示物品的材质：普通模式用箱子，作弊模式用命令方块喵~
            if (selectedMode == SlimefunGuideMode.SURVIVAL_MODE) {
                // 普通生存模式使用箱子图标，视觉上代表普通物品存储喵~
                item.setType(Material.CHEST);
            } else {
                // 作弊模式使用命令方块图标，视觉上代表管理员指令能力喵~
                item.setType(Material.COMMAND_BLOCK);
            }

            // 获取物品元数据对象，用于设置显示名称和Lore描述喵~
            ItemMeta meta = item.getItemMeta();
            // 设置物品显示名称，格式为灰色前缀+当前模式的本地化名称喵~
            meta.setDisplayName(ChatColor.GRAY + "Slimefun 指南样式: " + ChatColor.YELLOW + selectedMode.getDisplayName());
            // 创建Lore描述列表，用于展示可切换的模式选项喵~
            List<String> lore = new ArrayList<>();
            // 添加空行作为视觉分隔喵~
            lore.add("");
            // 当前是普通模式时用绿色高亮，否则灰色表示未选中喵~
            lore.add((selectedMode == SlimefunGuideMode.SURVIVAL_MODE ? ChatColor.GREEN : ChatColor.GRAY) + "普通模式");
            // 当前是作弊模式时用绿色高亮，否则灰色表示未选中喵~
            lore.add((selectedMode == SlimefunGuideMode.CHEAT_MODE ? ChatColor.GREEN : ChatColor.GRAY) + "作弊模式");

            // 添加空行作为视觉分隔喵~
            lore.add("");
            // 添加操作提示，告知玩家单击可切换指南样式喵~
            lore.add(ChatColor.GRAY + "⇨ " + ChatColor.YELLOW + "单击修改指南样式");
            // 将构建好的Lore列表设置到物品元数据喵~
            meta.setLore(lore);
            // 将元数据应用回物品，使显示名称和Lore生效喵~
            item.setItemMeta(meta);

            // 返回包含展示物品的Optional喵~
            return Optional.of(item);
        }

        // 喵~防御：当前模式不存在时返回空Optional，避免展示异常物品喵~
        return Optional.empty();
    }

    /**
     * 处理玩家点击指南模式选项的逻辑：切换到下一个模式并刷新设置界面喵~
     * 输入：玩家p、当前指南ItemStack
     * 边界条件：若当前模式不存在则跳过切换，仍会重新打开设置页面喵~
     */
    @Override
    public void onClick(@Nonnull Player p, @Nonnull ItemStack guide) {
        // 获取当前选中的指南模式喵~
        Optional<SlimefunGuideMode> current = getSelectedOption(p, guide);

        // 喵~防御：只有当前模式存在时才执行切换，避免对空Optional调用get()喵~
        if (current.isPresent()) {
            // 根据当前模式和玩家权限计算下一个应切换到的模式喵~
            SlimefunGuideMode next = getNextMode(p, current.get());
            // 将计算出的新模式保存到指南物品中喵~
            setSelectedOption(p, guide, next);
        }

        // 切换完成后重新打开设置界面，让玩家看到更新后的状态喵~
        SlimefunGuideSettings.openSettings(p, guide);
    }

    /**
     * 根据玩家权限和当前模式计算下一个指南模式喵~
     * 输入：玩家p（用于权限检查）、当前模式mode
     * 输出：切换后应使用的SlimefunGuideMode枚举值
     * 边界条件：无作弊权限的玩家强制返回普通模式，防止权限绕过喵~
     */
    @Nonnull
    private SlimefunGuideMode getNextMode(@Nonnull Player p, @Nonnull SlimefunGuideMode mode) {
        // 只有拥有slimefun.cheat.items权限的玩家才能切换到作弊模式喵~
        if (p.hasPermission("slimefun.cheat.items")) {
            // 当前是普通模式则切换到作弊模式喵~
            if (mode == SlimefunGuideMode.SURVIVAL_MODE) {
                return SlimefunGuideMode.CHEAT_MODE;
            } else {
                // 当前是作弊模式则切换回普通模式喵~
                return SlimefunGuideMode.SURVIVAL_MODE;
            }
        } else {
            // 喵~防御：玩家无权限时强制返回普通模式，防止通过其他途径切换到作弊模式喵~
            return SlimefunGuideMode.SURVIVAL_MODE;
        }
    }

    /**
     * 读取当前指南物品对应的模式，通过比对物品外观来判断喵~
     * 输入：玩家p、指南ItemStack
     * 输出：Optional包装的当前指南模式，此实现始终返回非空Optional
     * 边界条件：若物品与作弊模式指南不匹配则默认返回普通模式喵~
     */
    @Nonnull
    @Override
    public Optional<SlimefunGuideMode> getSelectedOption(@Nonnull Player p, @Nonnull ItemStack guide) {
        // 通过工具方法比较当前指南物品是否与作弊模式指南物品相似（忽略数量，比较外观）喵~
        if (SlimefunUtils.isItemSimilar(guide, SlimefunGuide.getItem(SlimefunGuideMode.CHEAT_MODE), true, false)) {
            // 物品匹配作弊模式指南则返回作弊模式喵~
            return Optional.of(SlimefunGuideMode.CHEAT_MODE);
        } else {
            // 喵~防御：物品不匹配任何特殊模式时默认返回普通模式，确保始终有合法值喵~
            return Optional.of(SlimefunGuideMode.SURVIVAL_MODE);
        }
    }

    @Override
    @ParametersAreNonnullByDefault
    public void setSelectedOption(Player p, ItemStack guide, SlimefunGuideMode value) {
        // 将目标模式对应的指南物品元数据复制到当前指南，实现外观切换以持久化模式选择喵~
        guide.setItemMeta(SlimefunGuide.getItem(value).getItemMeta());
    }
}
