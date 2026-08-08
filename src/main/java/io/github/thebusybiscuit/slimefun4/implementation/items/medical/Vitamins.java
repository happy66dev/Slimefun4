package io.github.thebusybiscuit.slimefun4.implementation.items.medical;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import io.github.thebusybiscuit.slimefun4.utils.RadiationUtils;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 维他命医疗用品，成功后治疗、灭火并清除异常状态喵~
 */
public class Vitamins extends MedicalSupply<ItemUseHandler> {

    /**
     * 创建三秒读条的维他命喵~
     */
    @ParametersAreNonnullByDefault
    public Vitamins(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            MedicalSupplyUseManager useManager) {
        // 初始化八点治疗量和三秒读条时长喵~
        super(itemGroup, 8, 60L, item, recipeType, recipe, null, useManager);
    }

    /**
     * 返回统一医疗用品读条处理器喵~
     */
    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        // 右键时交由管理器管理会话和冷却喵~
        return createUseHandler();
    }

    /**
     * 执行维他命原有的成功医疗效果喵~
     */
    @Override
    public void applySuccessfulUse(@Nonnull Player player) {
        // 播放维他命消费音效喵~
        SoundEffect.VITAMINS_CONSUME_SOUND.playFor(player);
        // 熄灭玩家当前的燃烧状态喵~
        player.setFireTicks(0);
        // 清除医疗用品定义的固定负面药水效果喵~
        clearNegativeEffects(player);
        // 清除 Slimefun 辐射暴露状态喵~
        RadiationUtils.clearExposure(player);
        // 恢复最多八点生命值且不超过最大生命值喵~
        heal(player);
    }
}
