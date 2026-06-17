package io.github.thebusybiscuit.slimefun4.implementation.items.armor;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectionType;
import io.github.thebusybiscuit.slimefun4.core.attributes.ProtectiveArmor;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * 代表防辐射服(Hazmat Suit)套装中的一件护甲喵~
 * 这是 {@link ProtectiveArmor} 接口为数不多的实际应用之一喵~
 * 穿戴后可防御蜜蜂、辐射和能量过载伤害，且需要穿满整套才会生效喵~
 *
 * Represents 1 {@link SlimefunArmorPiece} of the Hazmat armor set.
 * One of the very few utilisations of {@link ProtectiveArmor}.
 *
 * @author Linox
 *
 * @see SlimefunArmorPiece
 * @see ProtectiveArmor
 *
 */
public class HazmatArmorPiece extends SlimefunArmorPiece implements ProtectiveArmor {

    // 用于唯一标识"防辐射服套装"的 NamespacedKey，在防护系统中区分不同套装喵~
    private final NamespacedKey namespacedKey;
    // 该护甲能防御的伤害类型数组，包含蜜蜂/辐射/能量过载三种防护喵~
    private final ProtectionType[] types;

    /*
     * 构造方法整体说明喵~
     * 功能：创建一件防辐射服护甲单件，初始化其防护类型和套装唯一标识喵~
     * 输入：itemGroup(物品所属分组)、item(物品外观/ID)、recipeType(合成方式)、
     *       recipe(合成配方材料数组)、effects(穿戴时施加的药水效果数组)喵~
     * 输出：构造完成的 HazmatArmorPiece 实例喵~
     * 边界条件：所有参数由 @ParametersAreNonnullByDefault 注解保证非null喵~
     */
    @ParametersAreNonnullByDefault
    public HazmatArmorPiece(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            PotionEffect[] effects) {
        // 调用父类 SlimefunArmorPiece 的构造器，注册物品组/外观/合成配方/药水效果喵~
        super(itemGroup, item, recipeType, recipe, effects);

        // 初始化防护类型数组：该护甲可防御蜜蜂蜇刺、辐射伤害和能量过载三种危害喵~
        types = new ProtectionType[] {ProtectionType.BEES, ProtectionType.RADIATION, ProtectionType.ENERGY_OVERLOAD};
        // 创建套装唯一标识 "hazmat_suit"，用于系统判断玩家是否穿戴了完整的防辐射服套装喵~
        namespacedKey = new NamespacedKey(Slimefun.instance(), "hazmat_suit");
    }

    /**
     * 返回此护甲能防御的所有伤害类型，防护系统会根据此列表决定是否抵消伤害喵~
     */
    @Override
    public ProtectionType[] getProtectionTypes() {
        // 返回包含蜜蜂/辐射/能量过载三种防护类型的数组喵~
        return types;
    }

    /**
     * 返回true表示必须穿满整套防辐射服才能触发防护效果，单件穿戴无效喵~
     */
    @Override
    public boolean isFullSetRequired() {
        // 防辐射服需要头盔+胸甲+护腿+靴子全部穿戴才能生效，返回true强制整套要求喵~
        return true;
    }

    /**
     * 返回该套装的唯一 NamespacedKey 标识，系统通过此Key来识别同一套装的不同部件喵~
     */
    @Override
    public NamespacedKey getArmorSetId() {
        // 返回 "slimefun:hazmat_suit" 这个唯一标识，让系统能将四件套关联为同一套装喵~
        return namespacedKey;
    }
}
