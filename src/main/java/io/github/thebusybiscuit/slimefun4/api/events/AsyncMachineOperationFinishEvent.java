package io.github.thebusybiscuit.slimefun4.api.events;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineProcessor;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/**
 * This {@link Event} is fired whenever an {@link MachineProcessor} has completed a {@link MachineOperation}.
 * 当 MachineProcessor 完成一次 MachineOperation（机器操作）时，此异步事件会被触发喵~
 *
 * @author poma123
 * @author TheBusyBiscuit
 *
 */
public class AsyncMachineOperationFinishEvent extends Event {

    // Bukkit 事件系统要求的静态处理器列表，所有监听此事件的监听器都注册在这里喵~
    private static final HandlerList handlers = new HandlerList();

    // 触发此事件的机器所在方块的坐标位置喵~
    private final BlockPosition position;
    // 负责管理机器操作流程的处理器实例，包含操作队列与状态信息喵~
    private final MachineProcessor<?> machineProcessor;
    // 本次已完成的具体机器操作对象，记录了操作的输入输出等细节喵~
    private final MachineOperation machineOperation;

    /**
     * 构造方法：创建一个机器操作完成的异步事件喵~
     * 整体思路：将方块坐标、处理器、操作三个关键信息存入事件对象，
     * 并通过判断当前线程是否为主线程来决定事件的异步标志喵~
     * 输入：方块坐标pos、机器处理器processor、完成的操作operation
     * 边界条件：若在主线程调用则isAsync为false；在异步线程调用则为true喵~
     */
    public <T extends MachineOperation> AsyncMachineOperationFinishEvent(
            BlockPosition pos, MachineProcessor<T> processor, T operation) {
        // 调用父类构造，传入"是否异步"标志：当前不是主线程则此事件标记为异步喵~
        super(!Bukkit.isPrimaryThread());

        // 保存机器所在方块的坐标，供监听器查询位置使用喵~
        this.position = pos;
        // 保存机器处理器实例，供监听器获取处理器状态使用喵~
        this.machineProcessor = processor;
        // 保存已完成的操作对象，供监听器读取操作结果使用喵~
        this.machineOperation = operation;
    }

    /**
     * This returns the {@link BlockPosition} of the machine.
     * 返回触发此事件的机器所在方块的坐标位置喵~
     *
     * @return The {@link BlockPosition} of the machine
     */
    @Nonnull
    public BlockPosition getPosition() {
        // 直接返回存储的方块坐标，不会为null（由@Nonnull保证）喵~
        return position;
    }

    /**
     * The {@link MachineProcessor} instance of the machine.
     * 返回与此事件关联的机器处理器实例喵~
     *
     * @return The {@link MachineProcessor} instance of the machine
     */
    // 喵~防御：返回值标注@Nullable，调用方需自行判断null以避免空指针异常喵~
    @Nullable public MachineProcessor<?> getProcessor() {
        // 返回存储的机器处理器实例喵~
        return machineProcessor;
    }

    /**
     * This returns the used {@link MachineOperation} in the process.
     * 返回本次已完成的机器操作对象喵~
     *
     * @return The {@link MachineOperation} of the process
     */
    // 喵~防御：返回值标注@Nullable，调用方需自行判断null以避免空指针异常喵~
    @Nullable public MachineOperation getOperation() {
        // 返回存储的已完成操作对象喵~
        return machineOperation;
    }

    // Bukkit 规范要求提供静态的 getHandlerList() 方法，供事件总线注册和分发监听器使用喵~
    @Nonnull
    public static HandlerList getHandlerList() {
        // 返回全局共享的处理器列表喵~
        return handlers;
    }

    // 重写父类方法，返回此事件对应的处理器列表，Bukkit 内部通过此方法分发事件喵~
    @Nonnull
    @Override
    public HandlerList getHandlers() {
        // 委托给静态方法，保证所有实例共享同一个处理器列表喵~
        return getHandlerList();
    }
}
