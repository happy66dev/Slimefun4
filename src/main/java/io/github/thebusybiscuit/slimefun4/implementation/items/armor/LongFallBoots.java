package io.github.thebusybiscuit.slimefun4.implementation.items.armor;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.services.sounds.SoundEffect;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

/**
 * {@link LongFallBoots} are a pair of boots which negate fall damage.
 * Nameworthy examples of this are Slime Boots and Bee Boots.
 * <p>
 * <i>Yes, you just found a Portal reference :P</i>
 *
 * @author TheBusyBiscuit
 *
 */
// 长落靴类，继承 SlimefunArmorPiece，是所有能抵消摔落伤害的靴子的基类喵~
// 典型例子有黏液靴和蜜蜂靴，穿上后落地时播放特定音效并消除摔落伤害喵~
public class LongFallBoots extends SlimefunArmorPiece {

    // 落地时播放的音效，由构造函数传入，final 表示一旦赋值不可更改喵~
    private final SoundEffect soundEffect;

    /**
     * @deprecated In RC-35, marked for removal in RC-36
     */
    /*
     * 旧版构造函数（已废弃）：不传入音效参数，自动使用默认的黏液靴落地音效喵~
     * 整体思路：调用新版构造函数并传入默认音效 SLIME_BOOTS_FALL_SOUND 喵~
     * 输入：物品组、物品栈、合成类型、合成配方数组、药水效果数组
     * 输出：一个使用默认落地音效的 LongFallBoots 实例喵~
     * 边界条件：所有参数均标注 @ParametersAreNonnullByDefault，禁止传入 null 喵~
     */
    @Deprecated
    @ParametersAreNonnullByDefault
    public LongFallBoots(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            PotionEffect[] effects) {
        // 将默认音效 SLIME_BOOTS_FALL_SOUND 作为音效参数，委托给完整构造函数处理喵~
        this(itemGroup, item, recipeType, recipe, effects, SoundEffect.SLIME_BOOTS_FALL_SOUND);
    }

    /*
     * 完整构造函数：允许自定义落地音效，创建一个长落靴实例喵~
     * 整体思路：调用父类 SlimefunArmorPiece 构造函数完成基础护甲注册，再保存自定义音效喵~
     * 输入：物品组、物品栈、合成类型、合成配方数组、药水效果数组、落地音效
     * 输出：一个携带自定义音效的 LongFallBoots 实例喵~
     * 边界条件：所有参数均标注 @ParametersAreNonnullByDefault，禁止传入 null 喵~
     */
    @ParametersAreNonnullByDefault
    public LongFallBoots(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            PotionEffect[] effects,
            SoundEffect soundEffect) {
        // 调用父类构造函数，完成物品组、物品栈、合成类型、配方、药水效果的基础初始化喵~
        super(itemGroup, item, recipeType, recipe, effects);

        // 将传入的自定义落地音效保存到成员变量，供 getSoundEffect() 方法返回喵~
        this.soundEffect = soundEffect;
    }

    /**
     * This returns the {@link SoundEffect} that is played upon landing with these boots.
     * 返回穿着这双靴子落地时播放的音效对象喵~
     *
     * @return The {@link SoundEffect} played when landing
     */
    // 获取落地音效的方法，供落地事件处理器调用，用于播放对应音效喵~
    @Nonnull
    public SoundEffect getSoundEffect() {
        // 直接返回构造时保存的音效字段喵~
        return soundEffect;
    }
}
