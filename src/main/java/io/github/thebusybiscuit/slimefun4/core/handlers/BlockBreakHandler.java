package io.github.thebusybiscuit.slimefun4.core.handlers;

import io.github.thebusybiscuit.slimefun4.api.events.AndroidMineEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemHandler;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.androids.MinerAndroid;
import java.util.List;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

/**
 * The {@link BlockBreakHandler} is called when a {@link Block} is broken
 * which holds a {@link SlimefunItem}.
 * The {@link BlockBreakHandler} provides three methods for this, one for block breaking
 * caused by a {@link Player}, one for a {@link MinerAndroid} and one method for a {@link Block}
 * being destroyed by an explosion.
 *
 * @author TheBusyBiscuit
 *
 * @see BlockPlaceHandler
 *
 */
// 方块破坏处理器抽象类，所有Slimefun物品的方块被破坏时的行为都继承自这里喵~
public abstract class BlockBreakHandler implements ItemHandler {

    /**
     * Whether a {@link MinerAndroid} is allowed to break this block.
     */
    // 标记机械机器人(MinerAndroid)是否被允许挖掘这种方块喵~
    private final boolean allowAndroids;

    /**
     * Whether an explosion is allowed to destroy this block.
     */
    // 标记爆炸(TNT/苦力怕等)是否被允许摧毁这种方块喵~
    private final boolean allowExplosions;

    /**
     * This constructs a new {@link BlockBreakHandler}.
     *
     * @param allowAndroids
     *            Whether a {@link MinerAndroid} is allowed to break blocks of this type
     * @param allowExplosions
     *            Whether blocks of this type are allowed to be broken by explosions
     */
    /*
     * 构造方法：初始化方块破坏处理器喵~
     * 输入：allowAndroids - 机器人是否可挖；allowExplosions - 爆炸是否可摧毁
     * 这两个标志位会在后续 isAndroidAllowed / isExplosionAllowed 查询时直接返回喵~
     */
    protected BlockBreakHandler(boolean allowAndroids, boolean allowExplosions) {
        this.allowAndroids = allowAndroids; // 将机器人许可标志保存到实例字段喵~
        this.allowExplosions = allowExplosions; // 将爆炸许可标志保存到实例字段喵~
    }

    // 抽象方法：当玩家手动破坏持有Slimefun物品的方块时被调用，子类必须实现具体逻辑喵~
    @ParametersAreNonnullByDefault
    public abstract void onPlayerBreak(BlockBreakEvent e, ItemStack item, List<ItemStack> drops);

    // 当爆炸摧毁持有Slimefun物品的方块时被调用，默认什么都不做，子类可按需重写喵~
    @ParametersAreNonnullByDefault
    public void onExplode(Block b, List<ItemStack> drops) {
        // This can be overridden, if necessary
    }

    // 当机械机器人(MinerAndroid)挖掘持有Slimefun物品的方块时被调用，默认什么都不做，子类可按需重写喵~
    @ParametersAreNonnullByDefault
    public void onAndroidBreak(AndroidMineEvent e) {
        // This can be overridden, if necessary
    }

    /**
     * This returns whether an explosion is able to break the given {@link Block}.
     *
     * @param b
     *            The {@link Block}
     * @return Whether explosions can destroy this {@link Block}
     */
    // 判断爆炸是否允许摧毁指定方块，默认返回构造时传入的 allowExplosions 标志喵~
    public boolean isExplosionAllowed(@Nonnull Block b) {
        /*
         * By default our flag is returned, but you can override it
         * to be handled on a per-Block basis.
         * 默认直接返回类级别的 allowExplosions 标志，子类可重写以实现按方块实例单独判断喵~
         */
        return allowExplosions; // 返回构造时设定的爆炸许可标志喵~
    }

    /**
     * This returns whether a {@link MinerAndroid} is allowed to break
     * the given {@link Block}.
     *
     * @param b
     *            The {@link Block}
     *
     * @return Whether androids can break the given {@link Block}
     */
    // 判断机械机器人是否允许挖掘指定方块，默认返回构造时传入的 allowAndroids 标志喵~
    public boolean isAndroidAllowed(@Nonnull Block b) {
        /*
         * By default our flag is returned, but you can override it
         * to be handled on a per-Block basis.
         * 默认直接返回类级别的 allowAndroids 标志，子类可重写以实现按方块实例单独判断喵~
         */
        return allowAndroids; // 返回构造时设定的机器人许可标志喵~
    }

    // 返回此处理器的唯一标识类型，Slimefun用它确保同一物品只绑定一个同类型处理器喵~
    @Override
    public final Class<? extends ItemHandler> getIdentifier() {
        return BlockBreakHandler.class; // 以当前类的Class对象作为处理器唯一标识喵~
    }
}
