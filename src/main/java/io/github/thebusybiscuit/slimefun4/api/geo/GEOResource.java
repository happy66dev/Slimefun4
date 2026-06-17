package io.github.thebusybiscuit.slimefun4.api.geo;

import io.github.thebusybiscuit.slimefun4.api.events.GEOResourceGenerationEvent;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.geo.GEOMiner;
import io.github.thebusybiscuit.slimefun4.implementation.items.geo.GEOScanner;
import javax.annotation.Nonnull;
import org.bukkit.Keyed;
import org.bukkit.World.Environment;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * GEOResource 代表一种虚拟地质资源，类似于世界生成的矿物，但不会真实出现在世界方块里喵~
 * 这类资源只存在于内存中，必须通过 GEOMiner（地质采矿机）等设备才能获取喵~
 *
 * 玩家可以使用 GEOScanner（地质扫描仪）检测某区块内的资源储量喵~
 *
 * @author TheBusyBiscuit
 *
 * @see ResourceManager
 * @see GEOMiner
 * @see GEOScanner
 * @see GEOResourceGenerationEvent
 *
 */
public interface GEOResource extends Keyed {

    /**
     * 根据所在维度环境和生物群系，返回该资源在区块内的默认储量喵~
     * 不同生物群系（沙漠/丛林等）和维度（主世界/下界/末地）会有不同的资源储量喵~
     *
     * @param environment
     *            当前区域所处的维度环境（NORMAL主世界 / NETHER下界 / THE_END末地）喵~
     * @param biome
     *            当前区域所处的生物群系（如平原、沙漠等）喵~
     *
     * @return 在给定生物群系的区块中，该资源的默认储量数值喵~
     */
    int getDefaultSupply(@Nonnull Environment environment, @Nonnull Biome biome);

    /**
     * 返回资源实际储量相对于默认储量的最大随机偏差值（只取正数）喵~
     * 用于给资源储量添加随机性，让每个区块的资源数量不完全一样喵~
     *
     * @return 储量的最大偏差/波动范围喵~
     */
    int getMaxDeviation();

    /**
     * 返回该资源的名称（例如 "Oil" 石油），用于内部标识和默认显示喵~
     *
     * @return 资源的名称字符串喵~
     */
    @Nonnull
    String getName();

    /**
     * 返回该资源对应的 ItemStack 物品对象，用于在 GEO Scanner 扫描结果中展示图标喵~
     * 如果该资源支持被 GEO Miner 开采，这个 ItemStack 也将作为采矿机的产出物品喵~
     *
     * @return 代表该资源的 ItemStack 物品喵~
     */
    @Nonnull
    ItemStack getItem();

    /**
     * 返回该资源是否可以被 GEO Miner（地质采矿机）开采喵~
     * 如果返回 true，该资源会被自动添加进采矿机的产出列表喵~
     *
     * @return 可被 GEO Miner 开采则返回 true，否则返回 false 喵~
     */
    boolean isObtainableFromGEOMiner();

    /**
     * 将当前 GEO Resource 注册到 GPS 网络的资源管理器中，使其在游戏中生效喵~
     */
    default void register() {
        // 通过 GPS 网络获取资源管理器，将当前资源注册进去让游戏能识别和使用喵~
        Slimefun.getGPSNetwork().getResourceManager().register(this);
    }

    /**
     * 根据玩家选择的语言，返回该资源的本地化名称喵~
     * 先从本地化文件中查找对应翻译，找不到则回退到默认英文名称喵~
     *
     * 整体思路：
     *   输入：玩家对象 p，用来确定其所使用的语言设置喵~
     *   输出：该资源在玩家语言下的本地化名称，找不到翻译时返回默认名称喵~
     *   边界条件：本地化字符串不存在（返回null）时，自动回退到 getName() 的默认值喵~
     *
     * @param p
     *            需要获取本地化名称的玩家对象喵~
     * @return 该资源在玩家所用语言下的本地化名称喵~
     */
    @Nonnull
    default String getName(@Nonnull Player p) {
        // 拼接本地化键名路径（格式: resources.命名空间.资源键），从本地化服务中查找对应翻译喵~
        String name = Slimefun.getLocalization()
                .getResourceString(p, "resources." + getKey().getNamespace() + "." + getKey().getKey());
        // 喵~防御：name为null说明没有找到本地化翻译，回退使用默认名称避免返回空值喵~
        return name == null ? getName() : name;
    }
}
