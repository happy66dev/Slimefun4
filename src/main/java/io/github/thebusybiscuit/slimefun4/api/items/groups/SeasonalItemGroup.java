package io.github.thebusybiscuit.slimefun4.api.items.groups;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import java.time.LocalDate;
import java.time.Month;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.apache.commons.lang.Validate;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 季节性物品组，只在绑定的特定月份才会在Slimefun指南中展示给玩家喵~
 * Represents a {@link ItemGroup} that is only displayed in the Guide during
 * a specified {@link Month}.
 *
 * @author TheBusyBiscuit
 *
 * @see ItemGroup
 * @see LockedItemGroup
 */
public class SeasonalItemGroup extends ItemGroup {

    // 该物品组只在此月份对玩家可见，其他月份自动隐藏喵~
    private final Month month;

    /**
     * 季节性物品组的构造方法，将物品组与指定月份绑定喵~
     * 整体思路：调用父类完成基本初始化，再校验并保存月份字段，
     * 后续 isAccessible 方法会用这个月份字段决定是否展示给玩家喵~
     * 输入：key=唯一命名空间键，month=绑定月份(1月~12月)，tier=物品组层级，item=图标物品喵~
     * 边界条件：month 不能为 null，否则立即抛出异常喵~
     *
     * The constructor for a {@link SeasonalItemGroup}.
     *
     * @param key
     *            The {@link NamespacedKey} that is used to identify this {@link ItemGroup}
     * @param month
     *            The month when the {@link ItemGroup} should be displayed (from 1 = January ; to 12 = December)
     * @param tier
     *            The tier of this {@link ItemGroup}
     * @param item
     *            The display item for this {@link ItemGroup}
     */
    @ParametersAreNonnullByDefault
    public SeasonalItemGroup(NamespacedKey key, Month month, int tier, ItemStack item) {
        // 调用父类构造方法，完成命名空间键、图标物品和层级的基础初始化喵~
        super(key, item, tier);
        // 喵~防御：month 为 null 时立即抛出异常，防止后续月份比较产生空指针崩溃喵
        Validate.notNull(month, "The Month cannot be null");

        // 将绑定的月份保存到字段，供 isAccessible 做可见性判断喵~
        this.month = month;
    }

    /**
     * 获取该季节性物品组绑定的显示月份喵~
     * This method returns the {@link Month} in which this {@link SeasonalItemGroup} will appear.
     *
     * @return the {@link Month} in which this {@link SeasonalItemGroup} appears
     */
    public @Nonnull Month getMonth() {
        // 直接返回绑定的月份枚举值喵~
        return month;
    }

    /**
     * 判断该季节性物品组对指定玩家是否可访问喵~
     * 整体思路：先比较系统当前月份与绑定月份，不匹配直接返回 false 隐藏物品组；
     * 匹配时再交由父类判断玩家是否满足其他访问条件（如解锁状态等）喵~
     * 输入：p = 待判断的玩家对象
     * 输出：true = 玩家可见并可访问，false = 对该玩家隐藏喵~
     */
    @Override
    public boolean isAccessible(@Nonnull Player p) {
        // 获取系统当前月份，与绑定月份比较，判断是否处于对应季节喵~
        if (month != LocalDate.now().getMonth()) {
            // 当前月份不匹配，直接返回 false，对玩家隐藏该季节性物品组喵~
            return false;
        }

        // 月份匹配，继续交由父类判断玩家权限等其他访问条件喵~
        return super.isAccessible(p);
    }
}
