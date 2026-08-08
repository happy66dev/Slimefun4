package io.github.thebusybiscuit.slimefun4.implementation.items.medical;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.utils.RadiationUtils;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 药物医疗用品，读条完成后治疗、灭火并返还空玻璃瓶喵~
 */
public class Medicine extends MedicalSupply<ItemUseHandler> {

    /**
     * 创建三秒读条的药物喵~
     */
    @ParametersAreNonnullByDefault
    public Medicine(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            MedicalSupplyUseManager useManager) {
        // 初始化八点治疗量和三秒读条时长喵~
        super(itemGroup, 8, 60L, item, recipeType, recipe, null, useManager);
    }

    /**
     * 返回统一医疗用品读条处理器，替代原版消费事件链喵~
     */
    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        // 右键时交由管理器阻止原版饮用并创建读条喵~
        return createUseHandler();
    }

    /**
     * 执行药物原有的成功医疗效果喵~
     */
    @Override
    public void applySuccessfulUse(@Nonnull Player player) {
        // 熄灭玩家当前的燃烧状态喵~
        player.setFireTicks(0);
        // 清除医疗用品定义的固定负面药水效果喵~
        clearNegativeEffects(player);
        // 清除 Slimefun 辐射暴露状态喵~
        RadiationUtils.clearExposure(player);
        // 恢复最多八点生命值且不超过最大生命值喵~
        heal(player);
    }

    /**
     * 声明药物完成后应返还原版空玻璃瓶喵~
     */
    @Override
    public boolean returnsGlassBottle() {
        // 药物材质为药水，手动消费后需要模拟原版容器返还喵~
        return true;
    }
}
