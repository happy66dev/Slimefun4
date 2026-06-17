// 声明此文件所属的包路径，位于旋转属性相关接口的包下喵~
package io.github.thebusybiscuit.slimefun4.core.attributes.rotations;

// 引入 SlimefunItem 基类，用于接口文档注释中关联物品类型喵~
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
// 引入 ItemAttribute 接口，本接口作为物品属性接口的子接口喵~
import io.github.thebusybiscuit.slimefun4.core.attributes.ItemAttribute;
// 引入 BlockFace 枚举，用于表示方块的朝向（南/北/东/西等）喵~
import org.bukkit.block.BlockFace;

/**
 * Implement this interface for any {@link SlimefunItem} to prevent
 * that {@link SlimefunItem} from being rotated to
 * {@link BlockFace}.NORTH_EAST
 * {@link BlockFace}.NORTH_WEST
 * {@link BlockFace}.SOUTH_EAST
 * {@link BlockFace}.SOUTH_WEST
 *
 * @author Ddggdd135
 *
 */
// 定义"禁止对角旋转"接口：实现此接口的 SlimefunItem 只能朝南/北/东/西四个正方向旋转，不能朝东北/西北/东南/西南对角方向旋转喵~
public interface NotDiagonallyRotatable extends ItemAttribute {
    /*
     * 根据玩家放置时的角度，将连续角度值映射到最近的正方向（南/北/东/西）喵~
     * 整体思路：将 -180 到 180 度的圆周均分为四个 90 度扇区，每个扇区对应一个基本方向喵~
     * 输入：angle - 玩家放置时相对于南方的旋转角度，范围为 [-180, 180] 度喵~
     * 输出：距离 angle 最近的 BlockFace（SOUTH/WEST/NORTH/EAST 四选一）喵~
     * 边界条件：angle 必须在 [-180, 180] 范围内，超出范围会抛出 IllegalArgumentException 喵~
     */
    default BlockFace getRotation(double angle) {
        // 角度在 (-45, 45] 范围内，说明玩家面朝南方放置，返回 SOUTH 喵~
        if (-45 < angle && angle <= 45) return BlockFace.SOUTH;
        // 角度在 (45, 135] 范围内，说明玩家偏向西方放置，返回 WEST 喵~
        else if (45 < angle && angle <= 135) return BlockFace.WEST;
        // 角度绝对值在 [135, 180] 范围内（即极左或极右），说明玩家面朝北方放置，返回 NORTH 喵~
        else if (135 <= Math.abs(angle) && Math.abs(angle) <= 180) return BlockFace.NORTH;
        // 角度在 (-135, -45] 范围内，说明玩家偏向东方放置，返回 EAST 喵~
        else if (-135 < angle && angle <= -45) return BlockFace.EAST;
        // 喵~防御：angle 不在合法范围 [-180, 180] 内时抛出异常，防止非法角度导致无返回值的逻辑漏洞喵~
        throw new IllegalArgumentException("angle must be number from -180 to 180");
    }
}
