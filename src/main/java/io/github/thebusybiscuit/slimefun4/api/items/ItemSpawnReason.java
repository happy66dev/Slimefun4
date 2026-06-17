package io.github.thebusybiscuit.slimefun4.api.items;

import io.github.thebusybiscuit.slimefun4.api.events.SlimefunItemSpawnEvent;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.core.networks.cargo.CargoNet;
import io.github.thebusybiscuit.slimefun4.implementation.items.altar.AncientPedestal;
import io.github.thebusybiscuit.slimefun4.implementation.items.seasonal.ChristmasPresent;
import io.github.thebusybiscuit.slimefun4.implementation.items.seasonal.EasterEgg;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.GoldPan;
import io.github.thebusybiscuit.slimefun4.implementation.items.tools.PickaxeOfContainment;
import org.bukkit.block.Block;

/**
 * 这个枚举列出了所有可能触发物品生成（掉落/放置）的原因喵~
 * 每个枚举常量代表一种具体的业务场景，会在 SlimefunItemSpawnEvent 事件中被引用喵~
 *
 * @author TheBusyBiscuit
 *
 * @see SlimefunItemSpawnEvent
 *
 */
public enum ItemSpawnReason {

    /**
     * 物品被放置在 {@link AncientPedestal}（远古祭坛基座）上方时触发的生成原因喵~
     */
    ANCIENT_PEDESTAL_PLACE_ITEM,

    /**
     * 使用 {@link PickaxeOfContainment}（封印镐）破坏怪物刷怪笼时，
     * 刷怪笼以 ItemStack 形式掉落到地上的原因喵~
     */
    BROKEN_SPAWNER_DROP,

    /**
     * {@link CargoNet}（货运网络）物品溢出时，多余的 ItemStack 被强制掉落到世界中的原因喵~
     */
    CARGO_OVERFLOW,

    /**
     * {@link MultiBlockMachine}（多方块机器）输出槽溢出时，多余的 ItemStack 被强制掉落到地面的原因喵~
     */
    MULTIBLOCK_MACHINE_OVERFLOW,

    /**
     * 玩家打开 {@link ChristmasPresent}（圣诞礼物）后，礼物内容物以 ItemStack 形式掉落的原因喵~
     */
    CHRISTMAS_PRESENT_OPENED,

    /**
     * 玩家打开 {@link EasterEgg}（复活节彩蛋）后，内容物以 ItemStack 形式掉落的原因喵~
     */
    EASTER_EGG_OPENED,

    /**
     * 玩家使用 {@link GoldPan}（淘金盘）对某个 {@link Block}（方块）进行操作，
     * 该方块产生了掉落物时对应的生成原因喵~
     */
    GOLD_PAN_USE,

    /**
     * 其他未被上方枚举值覆盖到的杂项生成原因喵~
     */
    MISC;
}
