package io.github.thebusybiscuit.slimefun4.core.handlers;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.bakedlibs.dough.collections.LoopIterator;
import io.github.thebusybiscuit.slimefun4.api.MinecraftVersion;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.RainbowBlock;
import io.github.thebusybiscuit.slimefun4.utils.ColoredMaterial;
import java.util.Arrays;
import java.util.List;
import javax.annotation.Nonnull;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.apache.commons.lang.Validate;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.GlassPane;

/**
 * 彩虹方块专属的 tick 处理器喵~
 * 这是一个只给彩虹方块使用的 {@link BlockTicker}，
 * 每次 tick 时，它会通过 {@link LoopIterator} 循环获取下一个 {@link Material}（方块材质），
 * 并把方块设置成那个新材质，从而实现彩虹变色的效果喵~
 *
 * @author TheBusyBiscuit
 *
 * @see RainbowBlock
 *
 */
public class RainbowTickHandler extends BlockTicker {

    // 循环迭代器，存放彩虹方块所有候选材质，每次 uniqueTick 时向前推进一个喵~
    private final LoopIterator<Material> iterator;
    // 标记候选材质列表中是否含有玻璃板类型，用于运行时快速判断，避免每次都重新检查喵~
    private final boolean glassPanes;
    // 当前这一轮 tick 应该设置的方块材质喵~
    private Material material;

    /*
     * 整体思路：
     * 构造函数接收一组材质列表，做以下初始化工作：
     *   1. 用 Validate 检查列表中不含 null 元素（防御性校验）
     *   2. 检查列表不能为空，否则彩虹无从循环
     *   3. 预先判断列表里是否包含玻璃板材质，结果存入 glassPanes 字段
     *   4. 用列表创建 LoopIterator（循环迭代器），并取出第一个材质作为初始值
     * 输入：材质列表（不能含 null，不能为空）
     * 输出：初始化完成的 RainbowTickHandler 对象
     * 边界条件：空列表或含 null 元素时直接抛出异常喵~
     */
    public RainbowTickHandler(@Nonnull List<Material> materials) {
        // 喵~防御：校验列表中不能有 null 元素，有则立即抛出异常，防止后续 iterator 崩溃喵
        Validate.noNullElements(materials, "A RainbowTicker cannot have a Material that is null!");

        // 喵~防御：材质列表为空时抛出异常，空列表无法循环变色，必须至少有一个材质喵
        if (materials.isEmpty()) {
            throw new IllegalArgumentException("A RainbowTicker must have at least one Material associated with it!");
        }

        // 预先检测列表中是否包含玻璃板材质，结果缓存到字段，避免每 tick 都重复检测喵~
        glassPanes = containsGlassPanes(materials);
        // 用材质列表创建循环迭代器，每次调用 next() 都会按顺序返回下一个材质，到末尾后自动回头喵~
        iterator = new LoopIterator<>(materials);
        // 取出迭代器的第一个材质作为初始显示材质喵~
        material = iterator.next();
    }

    // 接收可变参数形式的材质，内部转成 List 再调用主构造器，方便调用方直接传多个材质喵~
    public RainbowTickHandler(@Nonnull Material... materials) {
        this(Arrays.asList(materials));
    }

    // 接收 ColoredMaterial 枚举（如彩色混凝土系列），自动展开成材质列表再调用主构造器喵~
    public RainbowTickHandler(@Nonnull ColoredMaterial material) {
        this(material.asList());
    }

    /**
     * 检测材质列表中是否包含玻璃板类型材质喵~
     *
     * 整体思路：
     *   在启动时预先检查一次，把结果缓存到 glassPanes 字段，
     *   这样运行时每次 tick 就不必再重复创建 BlockData 来判断，节省性能喵~
     *   注意：单元测试环境下 BlockData 不可用，直接返回 false 跳过检测喵~
     *
     * 输入：材质列表 materials
     * 输出：列表中是否存在任意一个玻璃板材质（true/false）
     * 边界条件：单元测试模式下直接返回 false 喵~
     *
     * @param materials
     *            需要检查的材质列表
     *
     * @return 列表中是否含有玻璃板材质
     */
    private boolean containsGlassPanes(@Nonnull List<Material> materials) {
        // 喵~防御：单元测试环境下 BlockData 无法使用，直接返回 false 跳过检测，避免测试崩溃喵
        if (Slimefun.getMinecraftVersion() == MinecraftVersion.UNIT_TEST) {
            // BlockData is not available to us during Unit Tests :/
            return false;
        }

        // 主人注意：这里对每个材质都创建一次虚拟 BlockData，仅在服务端启动时执行一次，性能影响极小喵~
        for (Material type : materials) {
            /*
            此处创建的 BlockData 是纯虚拟对象，仅在启动时执行一次，
            不会影响实际运行性能；反而因提前加载数据，
            避免了后续每次 tick 时对其他材质的重量级判断调用喵~
            */
            // 创建当前材质的虚拟 BlockData，检查它是否是玻璃板类型喵~
            if (type.createBlockData() instanceof GlassPane) {
                // 找到玻璃板材质，立即返回 true，后续 tick 时会走玻璃板专用逻辑喵~
                return true;
            }
        }

        // 遍历完所有材质都没找到玻璃板，返回 false 喵~
        return false;
    }

    /*
     * 整体思路：
     * tick 方法在每个 Slimefun tick 周期被调用，负责把彩虹方块的实际材质更新成当前 material 字段的值喵~
     * 分两种情况处理：
     *   1. 如果是玻璃板类型：需要保留玻璃板的连接状态（哪些面连接了相邻方块）和防水状态，
     *      再切换材质，防止切换后玻璃板连接断开或防水状态丢失喵~
     *   2. 普通方块：直接用 setType 切换材质即可喵~
     * 输入：方块对象 b、Slimefun 物品 item、方块数据 data
     * 输出：无返回值，直接修改游戏中方块的材质喵~
     * 边界条件：方块已被破坏（变成空气）时直接 return，防止出现方块复制 bug 喵~
     */
    @Override
    public void tick(Block b, SlimefunItem item, SlimefunBlockData data) {
        // 喵~防御：方块已经被破坏变成空气了，此时再 setType 会产生方块复制 bug，直接跳过喵
        if (b.getType().isAir()) {
            /*
            方块已被破坏，此时再设置材质会导致方块复制的 bug，
            直接返回不做任何操作喵~
            */
            return;
        }

        // 如果候选材质中包含玻璃板类型，走玻璃板专用的更新逻辑喵~
        if (glassPanes) {
            // 获取当前方块的 BlockData（包含连接状态、防水状态等附加属性）喵~
            BlockData blockData = b.getBlockData();

            // 判断当前方块确实是玻璃板类型，才需要保留其连接状态喵~
            if (blockData instanceof GlassPane previousData) {
                /*
                 * 创建新材质的 BlockData，并在 lambda 回调中把旧玻璃板的属性复制过去：
                 *   - 防水状态（isWaterlogged）：方块是否被水淹没喵~
                 *   - 六面连接状态（face）：玻璃板与相邻方块的连接关系喵~
                 * 这样切换颜色后，玻璃板的外观连接不会突然断开，视觉上保持一致喵~
                 */
                BlockData block = material.createBlockData(bd -> {
                    // 检查新 BlockData 是否也是玻璃板类型，才能安全地复制属性喵~
                    if (bd instanceof GlassPane nextData) {
                        // 把旧玻璃板的防水状态复制到新玻璃板，保持水淹效果一致喵~
                        nextData.setWaterlogged(previousData.isWaterlogged());

                        // 遍历旧玻璃板所有允许连接的方向，把每个方向的连接状态复制过去喵~
                        for (BlockFace face : previousData.getAllowedFaces()) {
                            // 把旧玻璃板在该方向上是否有连接的状态复制到新玻璃板喵~
                            nextData.setFace(face, previousData.hasFace(face));
                        }
                    }
                });

                // 将保留了连接属性的新 BlockData 应用到方块，第二个参数 false 表示不触发物理更新（避免不必要的连锁反应）喵~
                b.setBlockData(block, false);
                return;
            }
        }

        // 非玻璃板的普通方块，直接设置新材质；第二个参数 false 表示不触发物理更新喵~
        b.setType(material, false);
    }

    @Override
    public void uniqueTick() {
        // uniqueTick 是所有同类彩虹方块共用的一次性 tick，在这里推进迭代器到下一个材质，
        // 所有彩虹方块下一帧都会变成同一种新颜色，保持同步喵~
        material = iterator.next();
    }

    @Override
    public boolean isSynchronized() {
        // 返回 true 表示 tick 必须在主线程（同步）执行，因为 setType/setBlockData 是非线程安全的 Bukkit API 喵~
        return true;
    }
}
