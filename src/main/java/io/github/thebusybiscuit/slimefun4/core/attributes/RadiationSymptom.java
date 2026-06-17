package io.github.thebusybiscuit.slimefun4.core.attributes;

import com.google.common.base.Preconditions;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.utils.RadiationUtils;
import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedPotionEffectType;
import javax.annotation.Nonnull;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * 辐射症状枚举，定义了不同辐射暴露等级下玩家会受到的药水效果喵~
 * 当玩家的辐射暴露值达到各症状的最低阈值(minExposure)时，对应的药水效果会被施加给玩家喵~
 *
 * An enum of potential radiation symptoms.
 * A symptom will be applied when the minExposure
 * threshold is reached on the {@link Player}'s
 * exposure level.
 * When the {@link Player} gets above the minExposure threshold
 * the {@link PotionEffect} will be applied.
 *
 * @author Semisol
 *
 * @see RadiationUtils
 */
public enum RadiationSymptom {
    // 辐射暴露值达到10时触发，施加缓慢效果(等级3)，最轻微的辐射症状喵~
    SLOW(10, VersionedPotionEffectType.SLOWNESS, 3),
    // 辐射暴露值达到25时触发，施加低等级凋零效果(等级0)，轻度辐射中毒喵~
    WITHER_LOW(25, PotionEffectType.WITHER, 0),
    // 辐射暴露值达到50时触发，施加失明效果(等级4)，中度辐射伤害喵~
    BLINDNESS(50, PotionEffectType.BLINDNESS, 4),
    // 辐射暴露值达到75时触发，施加高等级凋零效果(等级3)，重度辐射中毒喵~
    WITHER_HIGH(75, PotionEffectType.WITHER, 3),
    // 辐射暴露值达到100时触发，施加超高等级瞬间伤害效果(等级49)，致命辐射剂量喵~
    IMMINENT_DEATH(100, VersionedPotionEffectType.INSTANT_DAMAGE, 49);

    // 触发此症状所需的最低辐射暴露值，超过此值症状才会被施加给玩家喵~
    private final int minExposure;
    // 达到阈值时施加给玩家的药水效果对象，持续时长由配置项决定喵~
    private final PotionEffect potionEffect;

    /*
     * 构造函数：初始化辐射症状的触发阈值和对应药水效果喵~
     * 输入：minExposure=最低触发暴露值, type=药水效果类型, level=效果等级
     * 药水效果持续时长 = (配置项 radiation-update-interval 的值 * 20 + 20) tick喵~
     * 边界条件：type不可为null，minExposure必须>0，level必须>=0喵~
     */
    RadiationSymptom(int minExposure, @Nonnull PotionEffectType type, int level) {
        // 喵~防御：type为null时立即抛出异常，防止构造出无效症状导致后续空指针崩溃喵
        Preconditions.checkNotNull(type, "The effect type cannot be null");
        // 喵~防御：minExposure必须大于0，否则症状在无辐射时也会触发，逻辑错误喵
        Preconditions.checkArgument(minExposure > 0, "The minimum exposure must be greater than 0.");
        // 喵~防御：效果等级必须非负，负数等级在Bukkit中行为未定义喵
        Preconditions.checkArgument(level >= 0, "The status effect level must be non-negative.");

        // 保存此症状的最低辐射暴露触发阈值喵~
        this.minExposure = minExposure;
        // 读取配置中的辐射更新间隔，计算效果持续tick数并构建药水效果对象喵~
        this.potionEffect = new PotionEffect(
                type, Slimefun.getCfg().getOrSetDefault("options.radiation-update-interval", 1) * 20 + 20, level);
    }

    /**
     * 将此辐射症状对应的药水效果施加给指定玩家喵~
     *
     * This method applies the symptom to a player.
     *
     * @param p
     *            The player
     */
    public void apply(@Nonnull Player p) {
        // 喵~防御：玩家对象为null时立即抛出异常，防止addPotionEffect空指针崩溃喵
        Preconditions.checkNotNull(p, "The player cannot be null");
        // 将预先构建好的药水效果对象施加给玩家喵~
        p.addPotionEffect(potionEffect);
    }

    /**
     * 根据当前辐射暴露值判断此症状是否应该被触发喵~
     *
     * This method returns if this symptom
     * should be applied.
     *
     * @param exposure
     *            Exposure level
     *
     * @return If the symptom should be applied
     */
    public boolean shouldApply(int exposure) {
        // 当暴露值大于等于此症状的最低阈值时返回true，表示需要对玩家施加该效果喵~
        return exposure >= minExposure;
    }
}
