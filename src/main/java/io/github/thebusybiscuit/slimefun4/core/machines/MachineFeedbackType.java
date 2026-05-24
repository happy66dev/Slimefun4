package io.github.thebusybiscuit.slimefun4.core.machines;

import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedParticle;
import javax.annotation.Nonnull;
import org.bukkit.Particle;
import org.bukkit.Sound;

public enum MachineFeedbackType {
    SMELTING(ParticleOffset.ANY),
    GRINDING(ParticleOffset.ANY),
    ENCHANTING(ParticleOffset.HEAD_ONLY),
    COOKING(ParticleOffset.TOP),
    MECHANICAL(ParticleOffset.SIDE),
    FLUID(ParticleOffset.BOTTOM);

    private final ParticleOffset particleOffset;

    MachineFeedbackType(@Nonnull ParticleOffset particleOffset) {
        this.particleOffset = particleOffset;
    }

    @Nonnull
    public Particle getDefaultParticle() {
        switch (this) {
            case SMELTING:
                return Particle.FLAME;
            case GRINDING:
                return VersionedParticle.SMOKE;
            case ENCHANTING:
                return VersionedParticle.ENCHANT;
            case COOKING:
                return VersionedParticle.SMOKE;
            case MECHANICAL:
                return Particle.COMPOSTER;
            case FLUID:
                return VersionedParticle.DRIP_WATER;
            default:
                return Particle.FLAME;
        }
    }

    @Nonnull
    public Sound getDefaultSound() {
        switch (this) {
            case SMELTING:
                return Sound.BLOCK_FURNACE_FIRE_CRACKLE;
            case GRINDING:
                return Sound.BLOCK_GRINDSTONE_USE;
            case ENCHANTING:
                return Sound.BLOCK_ENCHANTMENT_TABLE_USE;
            case COOKING:
                return Sound.BLOCK_BREWING_STAND_BREW;
            case MECHANICAL:
                return Sound.BLOCK_PISTON_CONTRACT;
            case FLUID:
                return Sound.ENTITY_GENERIC_SPLASH;
            default:
                return Sound.BLOCK_PISTON_CONTRACT;
        }
    }

    @Nonnull
    public ParticleOffset getParticleOffset() {
        return particleOffset;
    }

    public enum ParticleOffset {
        ANY,
        TOP,
        SIDE,
        BOTTOM,
        HEAD_ONLY
    }
}
