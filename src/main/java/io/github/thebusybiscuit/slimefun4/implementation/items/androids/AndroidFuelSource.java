package io.github.thebusybiscuit.slimefun4.implementation.items.androids;

import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.utils.HeadTexture;
import javax.annotation.Nonnull;
import org.bukkit.inventory.ItemStack;

/**
 * 这个枚举列举了 {@link ProgrammableAndroid}（可编程机器人）所有可能的燃料来源类型喵~
 * 不同类型的机器人只能接受对应类型的燃料才能运作喵~
 *
 * @author TheBusyBiscuit
 *
 */
public enum AndroidFuelSource {

    /**
     * 固态燃料类型的机器人，使用木材、煤炭等固态材料作为燃料喵~
     * 例如原木、煤炭、木板等可燃固体物品喵~
     */
    SOLID("", "&f这类机器人需要固态燃料", "&f例如煤, 原木等..."),

    /**
     * 液态燃料类型的机器人，使用燃油、原油、岩浆等液态物质作为燃料喵~
     * 例如岩浆桶、原油桶、燃油桶等液态容器物品喵~
     */
    LIQUID("", "&f这类机器人需要液态燃料", "&f例如岩浆, 原油, 燃油等..."),

    /**
     * 核燃料类型的机器人，使用放射性材料如铀、镎等作为燃料喵~
     * 这类机器人能量强大但燃料来源特殊，属于高科技机器人喵~
     */
    NUCLEAR("", "&f这类机器人需要放射性燃料", "&f例如铀, 镎或钚铀混合氧化物核燃料");

    // 存储该燃料类型机器人在 GUI 中显示的 lore 文本行数组喵~
    private final String[] lore;

    /**
     * 枚举构造器，接收可变数量的 lore 字符串，用于描述该燃料类型的显示信息喵~
     * 整体思路：每个枚举常量在创建时传入若干行 lore 文本，保存到字段中供 getItem() 使用喵~
     * 输入：@Nonnull String... lore — 可变参数，不可为 null，代表 GUI 物品的描述行喵~
     * 输出：无（构造器）喵~
     *
     * @param lore 用于描述燃料类型的文字数组，显示在 GUI 物品的 lore 区域喵~
     */
    AndroidFuelSource(@Nonnull String... lore) {
        // 将传入的 lore 文本数组保存到字段，供后续 getItem() 方法拼装 ItemStack 使用喵~
        this.lore = lore;
    }

    /**
     * 返回该燃料类型对应的展示用 {@link ItemStack}，用于在机器人 GUI 的燃料槽中显示喵~
     * 整体思路：以发电机头颅纹理为图标，加上固定标题"燃料输入槽"和当前枚举的 lore 说明文字，
     * 组合成一个 CustomItemStack 返回给调用方在 GUI 中渲染喵~
     * 输入：无喵~
     * 输出：带有发电机图标、标题和 lore 的 ItemStack 展示物品喵~
     *
     * @return 用于展示在燃料槽的 {@link ItemStack} 对象喵~
     */
    @Nonnull
    public ItemStack getItem() {
        // 用发电机头颅纹理、固定标题"燃料输入槽"以及枚举自带的 lore 描述，构造一个 GUI 展示物品喵~
        return new CustomItemStack(HeadTexture.GENERATOR.getAsItemStack(), "&8⇩ &c燃料输入槽 &8⇩", lore);
    }
}
