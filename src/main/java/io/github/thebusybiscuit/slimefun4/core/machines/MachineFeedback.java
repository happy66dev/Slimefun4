package io.github.thebusybiscuit.slimefun4.core.machines;

import javax.annotation.Nonnull;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;

public interface MachineFeedback {

    @Nonnull
    Particle getDefaultParticle();

    @Nonnull
    Sound getDefaultSound();

    @Nonnull
    MachineFeedbackType.ParticleOffset getParticleOffset();

    default void onMachineStart(@Nonnull Block block) {}

    default void onMachineTick(@Nonnull Block block, @Nonnull MachineOperation operation) {}

    default void onMachineStop(@Nonnull Block block) {}
}
