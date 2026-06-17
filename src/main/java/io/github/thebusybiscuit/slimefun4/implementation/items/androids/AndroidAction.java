package io.github.thebusybiscuit.slimefun4.implementation.items.androids;

import city.norain.slimefun4.api.menu.UniversalMenu;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/**
 * 机械机器人的动作接口喵~
 *
 * 整体思路：这是一个函数式接口，定义了机械机器人每次执行某个动作时的回调规范喵~
 * 输入：执行动作的机器人实例、当前所在方块、机器人内部存储背包、机器人朝向的方块面喵~
 * 输出：无返回值，直接对游戏世界或背包产生副作用喵~
 * 边界条件：实现类需自行处理 android/b/inventory/face 为 null 的情况喵~
 */
@FunctionalInterface
interface AndroidAction {

    /**
     * 执行机械机器人的一次具体动作喵~
     *
     * @param android   正在执行动作的可编程机械机器人实例，提供机器人的配置与状态喵~
     * @param b         机器人当前所在的方块，用于确定操作位置喵~
     * @param inventory 机器人的内部存储背包（UniversalMenu），用于读写机器人携带的物品喵~
     * @param face      机器人当前朝向的方块面（如 NORTH/SOUTH/UP/DOWN），决定操作的目标方向喵~
     */
    void perform(ProgrammableAndroid android, Block b, UniversalMenu inventory, BlockFace face);
}
