// 定义机器反馈接口，所有能产生视觉/听觉反馈效果的机器都需要实现这个接口喵~
package io.github.thebusybiscuit.slimefun4.core.machines;

import javax.annotation.Nonnull;
import org.bukkit.Particle; // Bukkit粒子效果枚举，用于在游戏中播放粒子动画喵~
import org.bukkit.Sound; // Bukkit音效枚举，用于在游戏中播放声音喵~
import org.bukkit.block.Block; // Bukkit方块对象，代表世界中一个具体的方块位置喵~

/**
 * 机器反馈接口喵~
 *
 * 整体思路：
 *   定义机器在运行时应当产生什么视觉粒子效果、音效，以及粒子生成的偏移位置喵~
 *   同时提供机器启动、每tick运行、停止三个生命周期回调，让实现类可以自定义行为喵~
 *
 * 输入：方块对象(Block)与当前机器操作(MachineOperation)喵~
 * 输出：无返回值，主要通过副作用(播放粒子/音效)体现喵~
 * 边界条件：方法参数均标注@Nonnull，调用时不应传入null喵~
 */
public interface MachineFeedback {

    /**
     * 获取机器默认粒子效果喵~
     * 返回值不能为null，由实现类保证喵~
     */
    @Nonnull
    Particle getDefaultParticle(); // 返回此机器运行时应播放的默认粒子类型喵~

    /**
     * 获取机器默认音效喵~
     * 返回值不能为null，由实现类保证喵~
     */
    @Nonnull
    Sound getDefaultSound(); // 返回此机器运行时应播放的默认音效喵~

    /**
     * 获取粒子生成的偏移量配置喵~
     * 返回值不能为null，由实现类保证喵~
     */
    @Nonnull
    MachineFeedbackType.ParticleOffset getParticleOffset(); // 返回粒子相对于机器方块位置的偏移参数，用于精准定位粒子生成点喵~

    // 机器刚启动时触发的回调方法，默认不做任何操作，实现类可按需重写喵~
    default void onMachineStart(@Nonnull Block block) {} // block为刚启动的机器方块，@Nonnull表示不能传null喵~

    // 机器每个游戏tick运行时触发的回调，默认不做任何操作，实现类可按需重写喵~
    default void onMachineTick(
            @Nonnull Block block, @Nonnull MachineOperation operation) {} // block为运行中的机器方块，operation为当前正在执行的操作喵~

    // 机器停止运行时触发的回调方法，默认不做任何操作，实现类可按需重写喵~
    default void onMachineStop(@Nonnull Block block) {} // block为刚停止的机器方块，@Nonnull表示不能传null喵~
}
