package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.implementation.items.androids.AndroidInstance;
import io.github.thebusybiscuit.slimefun4.implementation.items.androids.FarmerAndroid;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.block.Block;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

/**
 * 该 {@link Event} 在 {@link FarmerAndroid} 收获 {@link Block} 之前被触发喵~
 * 如果此 {@link Event} 被取消，那么 {@link Block} 将不会被收获喵~
 * <p>
 * 即使方块不可收获，该事件仍然会触发喵~
 *
 * @author TheBusyBiscuit
 */
// 安卓农场事件：在农民安卓机器人收获方块时触发，支持取消操作喵~
public class AndroidFarmEvent extends Event implements Cancellable {

    // 静态事件处理器列表，用于Bukkit事件系统注册和分发事件喵~
    private static final HandlerList handlers = new HandlerList();

    private final Block block; // 被收获的目标方块喵~
    private final AndroidInstance android; // 触发此事件的安卓机器人实例喵~
    private final boolean isAdvanced; // 是否为高级耕作模式（影响收获行为和产出）喵~
    private ItemStack drop; // 收获掉落的物品栈，可为null表示无掉落喵~
    private boolean cancelled; // 事件取消标志，设为true则阻止方块收获喵~

    /**
     * 构造一个安卓农场事件喵~
     *
     * @param block      被收获的方块喵~
     * @param android    触发此事件的安卓机器人实例喵~
     * @param isAdvanced 是否为高级耕作操作喵~
     * @param drop       将要掉落的物品，可为null喵~
     */
    public AndroidFarmEvent(
            @Nonnull Block block, @Nonnull AndroidInstance android, boolean isAdvanced, @Nullable ItemStack drop) {
        this.block = block; // 记录被收获的目标方块喵~
        this.android = android; // 记录触发事件的安卓机器人喵~
        this.isAdvanced = isAdvanced; // 记录是否为高级耕作模式喵~
        this.drop = drop; // 记录收获掉落的物品（可能为null）喵~
    }

    /**
     * 获取被开采的方块喵~
     *
     * @return 被开采的方块喵~
     */
    @Nonnull
    public Block getBlock() {
        return block; // 返回被收获的方块实例喵~
    }

    /**
     * 获取收获掉落的物品，可能为null喵~
     *
     * @return 收获掉落的物品，如果没有掉落则返回null喵~
     */
    @Nullable public ItemStack getDrop() {
        return drop; // 返回收获掉落物品，可能为null喵~
    }

    /**
     * 判断此次事件是否由高级耕作操作触发喵~
     *
     * @return 是否为高级耕作模式喵~
     */
    public boolean isAdvanced() {
        return isAdvanced; // 返回高级耕作模式标志喵~
    }

    /**
     * 设置收获的 {@link ItemStack} 结果喵~
     *
     * @param drop 物品结果，可为null表示无掉落喵~
     */
    public void setDrop(@Nullable ItemStack drop) {
        this.drop = drop; // 更新收获掉落物品喵~
    }

    /**
     * 获取触发此事件的安卓机器人实例喵~
     *
     * @return 相关的安卓机器人实例喵~
     */
    @Nonnull
    public AndroidInstance getAndroid() {
        return android; // 返回触发事件的安卓机器人喵~
    }

    @Override
    public boolean isCancelled() {
        return cancelled; // 返回事件是否被取消，true表示阻止收获喵~
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel; // 设置事件取消状态，可阻止方块收获喵~
    }

    @Nonnull
    public static HandlerList getHandlerList() {
        return handlers; // 返回Bukkit事件系统的处理器列表（静态获取）喵~
    }

    @Nonnull
    @Override
    public HandlerList getHandlers() {
        return getHandlerList(); // 返回事件实例的处理器列表，委托给静态方法喵~
    }
}
