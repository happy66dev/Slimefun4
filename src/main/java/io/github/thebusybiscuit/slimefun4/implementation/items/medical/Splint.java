package io.github.thebusybiscuit.slimefun4.implementation.items.medical;

import city.norain.slimefun4.compatibillty.CompatibilityUtil;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedPotionEffectType;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * 夹板医疗用品，提供固定治疗量且不会灭火喵~
 */
public class Splint extends MedicalSupply<ItemUseHandler> {

    /**
     * 创建七秒读条的夹板喵~
     */
    @ParametersAreNonnullByDefault
    public Splint(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            ItemStack recipeOutput,
            MedicalSupplyUseManager useManager) {
        // 初始化夹板的治疗量、读条时长和配方喵~
        super(itemGroup, 2, 140L, item, recipeType, recipe, recipeOutput, useManager);
    }

    /**
     * 返回统一医疗读条处理器喵~
     */
    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        // 右键时由管理器处理读条和最终消费喵~
        return createUseHandler();
    }

    /**
     * 保留夹板满血且未着火时不启动的旧规则喵~
     */
    @Override
    public boolean canStartUse(@Nonnull Player player) {
        // 读取玩家最大生命值属性喵~
        var maxHealthAttribute = player.getAttribute(CompatibilityUtil.getMaxHealth());
        // 喵~防御：缺少最大生命值属性时拒绝使用，避免错误治疗判断喵~
        if (maxHealthAttribute == null) {
            return false;
        }

        // 受伤或着火时允许开始，否则保持旧逻辑直接拒绝喵~
        return player.getFireTicks() > 0 || player.getHealth() < maxHealthAttribute.getValue();
    }

    /**
     * 执行夹板原有成功效果喵~
     */
    @Override
    public void applySuccessfulUse(@Nonnull Player player) {
        // 播放夹板消费音效喵~
        io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect.SPLINT_CONSUME_SOUND.playFor(player);
        // 添加等级零的瞬时治疗，恢复两点生命值喵~
        player.addPotionEffect(new PotionEffect(VersionedPotionEffectType.INSTANT_HEALTH, 1, 0));
    }
}
