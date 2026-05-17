package io.github.thebusybiscuit.slimefun4.core.attributes;

import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.core.services.holograms.HologramsService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.blocks.HologramProjector;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

/**
 * This {@link ItemAttribute} manages holograms.
 *
 * @author TheBusyBiscuit
 *
 * @see HologramProjector
 * @see HologramsService
 *
 */
public interface HologramOwner extends ItemAttribute {

    /**
     * This will update the hologram text for the given {@link Block}.
     *
     * @param b
     *            The {@link Block} to which the hologram belongs
     *
     * @param text
     *            The nametag for the hologram
     */
    default void updateHologram(@Nonnull Block b, @Nonnull String text) {
        Location loc = b.getLocation().add(getHologramOffset(b));
        Slimefun.getHologramsService().setHologramLabel(loc, ChatColors.color(text));
    }

    default void updateHologram(@Nonnull Block b, @Nonnull String text, Supplier<Boolean> abort) {
        if (Bukkit.isPrimaryThread()) {
            if (abort.get()) {
                return;
            }
            updateHologram(b, text);
            return;
        }

        Slimefun.runSync(() -> {
            if (abort.get()) {
                return;
            }
            updateHologram(b, text);
        });
    }

    /**
     * This will remove the hologram for the given {@link Block}.
     *
     * @param b
     *            The {@link Block} to which the hologram blocks
     */
    default void removeHologram(@Nonnull Block b) {
        Location loc = b.getLocation().add(getHologramOffset(b));
        Slimefun.getHologramsService().removeHologram(loc);
    }

    /**
     * Updates a multi-line hologram for the given {@link Block}.
     * Each line is displayed as a separate {@link org.bukkit.entity.ArmorStand}.
     *
     * @param b
     *            The {@link Block} to which the hologram belongs
     * @param lines
     *            The text lines to display
     */
    default void updateMultiLineHologram(@Nonnull Block b, @Nonnull String... lines) {
        Location loc = b.getLocation().add(getHologramOffset(b));
        Slimefun.getHologramsService().setMultiLineHologram(loc, lines);
    }

    /**
     * Updates a multi-line hologram for the given {@link Block} with async safety.
     *
     * @param b
     *            The {@link Block} to which the hologram belongs
     * @param abort
     *            A {@link Supplier} that returns whether to abort the update
     * @param lines
     *            The text lines to display
     */
    default void updateMultiLineHologram(@Nonnull Block b, @Nonnull Supplier<Boolean> abort, @Nonnull String... lines) {
        if (Bukkit.isPrimaryThread()) {
            if (abort.get()) return;
            updateMultiLineHologram(b, lines);
            return;
        }

        Slimefun.runSync(() -> {
            if (abort.get()) return;
            updateMultiLineHologram(b, lines);
        });
    }

    /**
     * Removes a multi-line hologram for the given {@link Block}.
     *
     * @param b
     *            The {@link Block} to which the hologram belongs
     */
    default void removeMultiLineHologram(@Nonnull Block b) {
        Location loc = b.getLocation().add(getHologramOffset(b));
        Slimefun.getHologramsService().removeMultiLineHologram(loc);
    }

    /**
     * This returns the offset of the hologram as a {@link Vector}.
     * This offset is applied to {@link Block#getLocation()} when spawning
     * the hologram.
     *
     * @param block
     *            The {@link Block} which serves as the origin point
     *
     * @return The hologram offset
     */
    @Nonnull
    default Vector getHologramOffset(@Nonnull Block block) {
        return Slimefun.getHologramsService().getDefaultOffset();
    }
}
