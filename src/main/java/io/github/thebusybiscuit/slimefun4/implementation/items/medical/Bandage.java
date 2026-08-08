package io.github.thebusybiscuit.slimefun4.implementation.items.medical;

import city.norain.slimefun4.compatibillty.CompatibilityUtil;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedPotionEffectType;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * 破布与绷带的统一医疗用品实现喵~
 */
public class Bandage extends MedicalSupply<ItemUseHandler> {

    // 瞬时治疗效果等级，零级恢复两点、一级恢复四点喵~
    private final int healingLevel;

    /**
     * 创建具有指定读条时长的破布或绷带喵~
     */
    @ParametersAreNonnullByDefault
    public Bandage(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            ItemStack recipeOutput,
            int healingLevel,
            long useDurationTicks,
            MedicalSupplyUseManager useManager) {
        // 初始化物品基础定义，瞬时治疗由完成回调执行喵~
        super(itemGroup, 0, useDurationTicks, item, recipeType, recipe, recipeOutput, useManager);
        // 保存破布或绷带对应的瞬时治疗等级喵~
        this.healingLevel = healingLevel;
    }

    /**
     * 返回统一的医疗用品右键读条处理器喵~
     */
    @Override
    public @Nonnull ItemUseHandler getItemHandler() {
        // 使用管理器负责创建会话、打断、消费和冷却喵~
        return createUseHandler();
    }

    /**
     * 保留满血且未着火时不使用破布或绷带的旧规则喵~
     */
    @Override
    public boolean canStartUse(@Nonnull Player player) {
        // 读取玩家最大生命值属性喵~
        var maxHealthAttribute = player.getAttribute(CompatibilityUtil.getMaxHealth());
        // 喵~防御：缺少最大生命值属性时拒绝使用，避免错误判断喵~
        if (maxHealthAttribute == null) {
            return false;
        }

        // 受伤或着火才允许开始读条喵~
        return player.getFireTicks() > 0 || player.getHealth() < maxHealthAttribute.getValue();
    }

    /**
     * 执行破布或绷带原有的成功效果喵~
     */
    @Override
    public void applySuccessfulUse(@Nonnull Player player) {
        // 播放踩踏白色羊毛的原有使用音效喵~
        player.getWorld().playEffect(player.getLocation(), Effect.STEP_SOUND, Material.WHITE_WOOL);
        // 施加原有瞬时治疗等级喵~
        player.addPotionEffect(new PotionEffect(VersionedPotionEffectType.INSTANT_HEALTH, 1, healingLevel));
        // 破布和绷带成功使用后熄灭玩家身上的火焰喵~
        player.setFireTicks(0);
    }
}
