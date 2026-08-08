package io.github.thebusybiscuit.slimefun4.implementation.items.medical;

import city.norain.slimefun4.compatibillty.CompatibilityUtil;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedPotionEffectType;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

/**
 * 医疗用品的统一基类，提供读条入口、治疗和负面效果清理喵~
 *
 * @param <T> 医疗用品使用的 Slimefun 处理器类型喵~
 */
public abstract class MedicalSupply<T extends ItemHandler> extends SimpleSlimefunItem<T> {

    // 使用成功后可清除的固定负面药水效果集合喵~
    private final Set<PotionEffectType> curedEffects = new HashSet<>();
    // 医疗用品直接恢复的生命值，单位：生命值点数喵~
    private final int healAmount;
    // 医疗用品读条时长，单位：tick喵~
    private final long useDurationTicks;
    // 统一管理读条、打断与共用冷却的管理器喵~
    private final MedicalSupplyUseManager useManager;

    /**
     * 创建医疗用品基础数据喵~
     *
     * @param itemGroup 医疗用品所属物品组喵~
     * @param healAmount 医疗用品直接治疗量，单位：生命值点数喵~
     * @param useDurationTicks 使用前摇时长，单位：tick喵~
     * @param item 医疗用品展示物品喵~
     * @param recipeType 医疗用品配方类型喵~
     * @param recipe 医疗用品配方内容喵~
     * @param useManager 统一医疗用品使用管理器喵~
     */
    @ParametersAreNonnullByDefault
    protected MedicalSupply(
            ItemGroup itemGroup,
            int healAmount,
            long useDurationTicks,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            ItemStack recipeOutput,
            MedicalSupplyUseManager useManager) {
        // 初始化 Slimefun 物品基础定义并保留配方产出喵~
        super(itemGroup, item, recipeType, recipe, recipeOutput);

        // 喵~防御：治疗量不能为负数，避免错误配置造成反向伤害喵~
        if (healAmount < 0) {
            throw new IllegalArgumentException("healAmount cannot be negative");
        }

        // 喵~防御：读条时长必须为正数，避免零时长绕过使用机制喵~
        if (useDurationTicks < 1L) {
            throw new IllegalArgumentException("useDurationTicks must be positive");
        }

        // 喵~防御：管理器不能为空，否则物品右键无法安全创建会话喵~
        if (useManager == null) {
            throw new IllegalArgumentException("useManager cannot be null");
        }

        // 保存医疗用品治疗量喵~
        this.healAmount = healAmount;
        // 保存医疗用品读条时长喵~
        this.useDurationTicks = useDurationTicks;
        // 保存统一医疗用品使用管理器喵~
        this.useManager = useManager;

        // 将中毒纳入医疗用品可清除的负面效果喵~
        curedEffects.add(PotionEffectType.POISON);
        // 将凋零纳入医疗用品可清除的负面效果喵~
        curedEffects.add(PotionEffectType.WITHER);
        // 将缓慢纳入医疗用品可清除的负面效果喵~
        curedEffects.add(VersionedPotionEffectType.SLOWNESS);
        // 将挖掘疲劳纳入医疗用品可清除的负面效果喵~
        curedEffects.add(VersionedPotionEffectType.MINING_FATIGUE);
        // 将虚弱纳入医疗用品可清除的负面效果喵~
        curedEffects.add(PotionEffectType.WEAKNESS);
        // 将反胃纳入医疗用品可清除的负面效果喵~
        curedEffects.add(VersionedPotionEffectType.NAUSEA);
        // 将失明纳入医疗用品可清除的负面效果喵~
        curedEffects.add(PotionEffectType.BLINDNESS);
        // 将不祥之兆纳入医疗用品可清除的负面效果喵~
        curedEffects.add(PotionEffectType.BAD_OMEN);
    }

    /**
     * 返回此医疗用品读条时长喵~
     *
     * @return 使用前摇时长，单位：tick喵~
     */
    public long getUseDurationTicks() {
        // 返回不可变的读条时长数值喵~
        return useDurationTicks;
    }

    /**
     * 返回本医疗用品的统一使用管理器喵~
     *
     * @return 管理读条、打断和冷却的管理器喵~
     */
    @Nonnull
    protected MedicalSupplyUseManager getUseManager() {
        // 返回构造时注入的唯一管理器喵~
        return useManager;
    }

    /**
     * 判断玩家当前状态是否允许开始此用品的读条喵~
     *
     * @param player 需要检查的玩家喵~
     * @return 可以开始时返回 true喵~
     */
    public boolean canStartUse(@Nonnull Player player) {
        // 默认允许使用，使维他命和药物可在满血时清除异常状态喵~
        return true;
    }

    /**
     * 在管理器确认消费成功后执行物品原有效果喵~
     *
     * @param player 成功完成读条的玩家喵~
     */
    public abstract void applySuccessfulUse(@Nonnull Player player);

    /**
     * 判断成功消费后是否需要返还一个空玻璃瓶喵~
     *
     * @return 需要返还空玻璃瓶时返回 true喵~
     */
    public boolean returnsGlassBottle() {
        // 默认不返还容器，只有药物覆盖此行为喵~
        return false;
    }

    /**
     * 创建统一右键读条处理器喵~
     *
     * @return 转发到使用管理器的 ItemUseHandler喵~
     */
    @Nonnull
    protected ItemUseHandler createUseHandler() {
        // 右键时只创建读条会话，成功效果由管理器在完成后调用喵~
        return (PlayerRightClickEvent event) -> useManager.startUse(event, this);
    }

    /**
     * 返回此医疗用品可清除的 PotionEffect 集合喵~
     *
     * @return 不可修改的负面效果集合喵~
     */
    @Nonnull
    public Set<PotionEffectType> getCuredEffects() {
        // 返回不可变视图，防止调用方修改医疗用品规则喵~
        return Collections.unmodifiableSet(curedEffects);
    }

    /**
     * 清除实体身上的医疗用品固定负面效果喵~
     *
     * @param entity 需要清除效果的生物实体喵~
     */
    public void clearNegativeEffects(@Nonnull LivingEntity entity) {
        // 喵~防御：空实体没有可清除的效果，避免空指针喵~
        if (entity == null) {
            return;
        }

        // 遍历所有可清除的负面效果类型喵~
        for (PotionEffectType effect : curedEffects) {
            // 实体拥有该效果时才调用移除，避免无意义操作喵~
            if (entity.hasPotionEffect(effect)) {
                // 移除当前负面效果喵~
                entity.removePotionEffect(effect);
            }
        }
    }

    /**
     * 按构造参数恢复实体生命值且不超过最大生命值喵~
     *
     * @param entity 需要治疗的生物实体喵~
     */
    public void heal(@Nonnull LivingEntity entity) {
        // 喵~防御：空实体不能读取生命值或属性喵~
        if (entity == null || entity.getAttribute(CompatibilityUtil.getMaxHealth()) == null) {
            return;
        }

        // 计算治疗后的目标生命值喵~
        double healedHealth = entity.getHealth() + healAmount;
        // 读取兼容不同版本的最大生命值属性喵~
        double maximumHealth =
                entity.getAttribute(CompatibilityUtil.getMaxHealth()).getValue();
        // 将生命值限制为最大生命值，避免 Bukkit 抛出越界异常喵~
        entity.setHealth(Math.min(healedHealth, maximumHealth));
    }
}
