package io.github.thebusybiscuit.slimefun4.core.attributes;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.WitherProofBlock;
import javax.annotation.Nonnull;
import org.bukkit.block.Block;
import org.bukkit.entity.Wither;
import org.bukkit.event.entity.EntityChangeBlockEvent;

/**
 * This Interface, when attached to a class that inherits from {@link SlimefunItem}, marks
 * the Item as "Wither-Proof".
 * Wither-Proof blocks cannot be destroyed by a {@link Wither}.
 *
 * 凋零防护接口喵~ 凡是实现了此接口的 SlimefunItem 所对应的方块，
 * 将无法被凋零Boss破坏，用于保护特殊方块免受凋零攻击喵~
 *
 * @author TheBusyBiscuit
 *
 * @see WitherProofBlock
 *
 */
public interface WitherProof extends ItemAttribute {

    /**
     * This method is called when a {@link Wither} tried to attack the given {@link Block}.
     * You can use this method to play particles or even damage the {@link Wither}.
     * This method is only called from {@link WitherProof#onAttackEvent(EntityChangeBlockEvent)}
     *
     * 当凋零Boss尝试攻击某个方块时，此回调方法会被触发喵~
     * 子类可以在这里播放粒子特效、对凋零造成反弹伤害等自定义逻辑喵~
     * 注意：此方法只会由 onAttackEvent 在内部调用，不直接监听事件喵~
     *
     * @param block
     *            The {@link Block} which was attacked. 被凋零攻击的目标方块喵~
     * @param wither
     *            The {@link Wither} who attacked. 发起攻击的凋零Boss实体喵~
     */
    // 抽象方法：由各实现类定义凋零攻击该方块时的具体响应行为喵~
    void onAttack(@Nonnull Block block, @Nonnull Wither wither);

    /**
     * This method is called when a {@link Wither} tried to attack the block.
     * You can use this method to handle the {@link EntityChangeBlockEvent}.
     *
     * 凋零攻击方块事件的默认处理逻辑喵~
     * 整体思路：监听 EntityChangeBlockEvent（实体改变方块状态的事件），
     * 若攻击者是凋零Boss则取消事件以阻止方块被破坏，
     * 再调用 onAttack 执行子类自定义的响应（如粒子效果、反弹伤害）喵~
     * 输入：EntityChangeBlockEvent 方块变化事件对象
     * 边界条件：若触发实体不是 Wither 类型则不做任何处理，防止误拦截其他实体的行为喵~
     *
     * @param event
     *            The {@link EntityChangeBlockEvent} which was involved. 触发方块变化的事件喵~
     */
    default void onAttackEvent(EntityChangeBlockEvent event) {
        // 喵~防御：用 instanceof 模式匹配判断触发实体是否为凋零Boss，非凋零则直接跳过避免误处理喵~
        if (event.getEntity() instanceof Wither wither) {
            // 取消原始的方块变化事件，阻止凋零Boss将此防护方块摧毁喵~
            event.setCancelled(true);
            // 调用子类实现的 onAttack 方法，执行自定义的凋零攻击响应逻辑（如粒子、反伤）喵~
            onAttack(event.getBlock(), wither);
        }
    }
}
