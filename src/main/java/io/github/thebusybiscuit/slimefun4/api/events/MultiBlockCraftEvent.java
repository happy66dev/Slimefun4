package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;

/**
 * This {@link Event} is called when a {@link Player} crafts an item using a {@link MultiBlockMachine}.
 * Unlike the {@link MultiBlockInteractEvent}, this event only fires if an output to a craft is expected.
 * If this event is cancelled, ingredients will not be consumed and no output item results.
 *
 * @author char321
 * @author JustAHuman
 */
// 喵~多方块机器合成事件：玩家使用多方块机器合成物品时触发，只有预期有产出才会触发；取消事件后既不消耗材料也不产出物品喵~
public class MultiBlockCraftEvent extends PlayerEvent implements Cancellable {
    // Bukkit事件系统的全局处理器列表，用于注册和管理本事件的所有监听器喵
    private static final HandlerList handlers = new HandlerList();

    // 触发本次合成操作的多方块机器实例（如压力机、冶炼炉等）喵
    private final MultiBlockMachine machine;
    // 合成所需的输入物品数组，不可变引用但数组内容可被读取喵
    private final ItemStack[] input;
    // 合成产出的结果物品，可通过setOutput方法在事件处理中被替换喵
    private ItemStack output;
    // 事件取消标记，true表示本次合成将被阻止喵
    private boolean cancelled;

    /**
     * Creates a new {@link MultiBlockCraftEvent}.
     *
     * @param p The player that crafts using a multiblock
     * @param machine The multiblock machine used to craft
     * @param input The input items of the craft
     * @param output The resulting item of the craft
     */
    @ParametersAreNonnullByDefault
    public MultiBlockCraftEvent(Player p, MultiBlockMachine machine, ItemStack[] input, ItemStack output) {
        super(p); // 调用父类PlayerEvent构造函数，将玩家p绑定为事件触发者喵
        this.machine = machine; // 保存触发合成所用的多方块机器引用喵
        this.input = input; // 保存合成输入物品数组，后续监听器可读取配方材料喵
        this.output = output; // 保存合成预期的产出物品喵
    }

    /**
     * Creates a new {@link MultiBlockCraftEvent}.
     *
     * @param p The player that crafts using a multiblock
     * @param machine The multiblock machine used to craft
     * @param input The input item of the craft
     * @param output The resulting item of the craft
     */
    @ParametersAreNonnullByDefault
    public MultiBlockCraftEvent(Player p, MultiBlockMachine machine, ItemStack input, ItemStack output) {
        this(p, machine, new ItemStack[] {input}, output); // 喵~便捷构造函数：将单个输入物品包装成数组后委托给主构造函数，方便只有一个输入材料时使用喵
    }

    /**
     * Gets the machine that was used to craft.
     *
     * @return The {@link MultiBlockMachine} used to craft.
     */
    public @Nonnull MultiBlockMachine getMachine() {
        return machine; // 返回触发本次合成的多方块机器实例喵
    }

    /**
     * Gets the input of the craft.
     *
     * @return The {@link ItemStack ItemStack[]} input that is used in the craft.
     */
    public @Nonnull ItemStack[] getInput() {
        return input; // 返回合成输入物品数组，调用者可读取配方材料信息喵
    }

    /**
     * Gets the output of the craft.
     *
     * @return The {@link ItemStack} output that results from the craft.
     */
    public @Nonnull ItemStack getOutput() {
        return output; // 返回当前设定的合成产出物品喵
    }

    /**
     * Sets the output of the craft. Keep in mind that this overwrites any existing output.
     *
     * @param output
     *            The new item for the event to produce.
     *
     * @return The previous {@link ItemStack} output that was replaced.
     */
    public @Nullable ItemStack setOutput(@Nullable ItemStack output) {
        ItemStack oldOutput = this.output; // 喵~保存旧的产出物品引用，以便返回给调用者喵
        this.output = output; // 将产出物品替换为新值，其他监听器可以动态修改合成结果喵
        return oldOutput; // 返回被替换前的旧产出物品，允许调用者回滚喵
    }

    @Override
    public boolean isCancelled() {
        return cancelled; // 返回事件取消状态：true表示合成将被阻止喵
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel; // 设置事件取消标记，设为true可阻止本次合成（材料不消耗、物品不产出）喵
    }

    // 静态获取全局HandlerList，Bukkit事件系统据此管理所有监听器注册喵
    public static @Nonnull HandlerList getHandlerList() {
        return handlers; // 返回本事件类共用的静态HandlerList实例喵
    }

    @Override
    public @Nonnull HandlerList getHandlers() {
        return getHandlerList(); // 委托到静态方法getHandlerList()，符合Bukkit事件规范喵
    }
}
