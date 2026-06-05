package io.github.thebusybiscuit.slimefun4.core.machines;

import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedParticle;
import javax.annotation.Nonnull;
import org.bukkit.Particle;
import org.bukkit.Sound;

public enum MachineFeedbackType implements MachineFeedback {
    SMELTING(Particle.FLAME, Sound.BLOCK_FURNACE_FIRE_CRACKLE, ParticleOffset.ANY),
    SMELTING_INFERNAL(Particle.SOUL_FIRE_FLAME, Sound.ENTITY_WITHER_AMBIENT, ParticleOffset.TOP),
    SMELTING_BLAZING(VersionedParticle.LAVA, Sound.BLOCK_LAVA_POP, ParticleOffset.TOP),
    SMELTING_VOLCANIC(VersionedParticle.LARGE_SMOKE, Sound.ENTITY_GENERIC_EXPLODE, ParticleOffset.ANY),

    GRINDING(VersionedParticle.SMOKE, Sound.BLOCK_GRINDSTONE_USE, ParticleOffset.ANY),
    GRINDING_CLOCKWORK(VersionedParticle.ENCHANTED_HIT, Sound.BLOCK_ANVIL_USE, ParticleOffset.SIDE),
    GRINDING_HEAVY(VersionedParticle.SMOKE, Sound.BLOCK_ANVIL_LAND, ParticleOffset.ANY),

    ENCHANTING(VersionedParticle.ENCHANT, Sound.BLOCK_ENCHANTMENT_TABLE_USE, ParticleOffset.HEAD_ONLY),
    ENCHANTING_ARCANE(Particle.PORTAL, Sound.BLOCK_BEACON_AMBIENT, ParticleOffset.HEAD_ONLY),
    ENCHANTING_DARK(Particle.SOUL, Sound.ENTITY_WITHER_SPAWN, ParticleOffset.HEAD_ONLY),

    COOKING(VersionedParticle.SMOKE, Sound.BLOCK_BREWING_STAND_BREW, ParticleOffset.TOP),
    COOKING_SIZZLING(Particle.CAMPFIRE_COSY_SMOKE, Sound.BLOCK_CAMPFIRE_CRACKLE, ParticleOffset.TOP),
    COOKING_STEAMING(Particle.CLOUD, Sound.ENTITY_GENERIC_SPLASH, ParticleOffset.TOP),

    MECHANICAL(Particle.COMPOSTER, Sound.BLOCK_PISTON_CONTRACT, ParticleOffset.SIDE),
    MECHANICAL_STEAM(Particle.CLOUD, Sound.BLOCK_LAVA_EXTINGUISH, ParticleOffset.ANY),
    MECHANICAL_CLOCKWORK(VersionedParticle.ENCHANTED_HIT, Sound.BLOCK_NOTE_BLOCK_HAT, ParticleOffset.SIDE),
    MECHANICAL_HEAVY(VersionedParticle.LARGE_SMOKE, Sound.BLOCK_ANVIL_LAND, ParticleOffset.ANY),

    FLUID(VersionedParticle.DRIP_WATER, Sound.ENTITY_GENERIC_SPLASH, ParticleOffset.BOTTOM),
    FLUID_LAVA(VersionedParticle.DRIP_LAVA, Sound.BLOCK_LAVA_POP, ParticleOffset.BOTTOM),
    FLUID_BUBBLING(VersionedParticle.WATER_BUBBLE, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, ParticleOffset.BOTTOM),

    ELECTRIC(VersionedParticle.FIREWORK, Sound.BLOCK_PISTON_EXTEND, ParticleOffset.ANY),
    ELECTRIC_ARC(VersionedParticle.ENCHANT, Sound.BLOCK_BEACON_AMBIENT, ParticleOffset.ANY),
    ELECTRIC_STATIC(Particle.COMPOSTER, Sound.BLOCK_WOOL_PLACE, ParticleOffset.SIDE),

    CRYOGENIC(Particle.CLOUD, Sound.BLOCK_GLASS_BREAK, ParticleOffset.TOP),
    CRYOGENIC_FREEZING(VersionedParticle.DRIP_WATER, Sound.ENTITY_SNOWBALL_THROW, ParticleOffset.TOP),

    RADIANT(Particle.END_ROD, Sound.BLOCK_BEACON_POWER_SELECT, ParticleOffset.HEAD_ONLY),
    RADIANT_GLOWING(VersionedParticle.ENCHANT, Sound.BLOCK_NOTE_BLOCK_HARP, ParticleOffset.HEAD_ONLY);

    private final Particle defaultParticle;
    private final Sound defaultSound;
    private final ParticleOffset particleOffset;

    MachineFeedbackType(
            @Nonnull Particle defaultParticle, @Nonnull Sound defaultSound, @Nonnull ParticleOffset particleOffset) {
        this.defaultParticle = defaultParticle;
        this.defaultSound = defaultSound;
        this.particleOffset = particleOffset;
    }

    @Nonnull
    @Override
    public Particle getDefaultParticle() {
        return defaultParticle;
    }

    @Nonnull
    @Override
    public Sound getDefaultSound() {
        return defaultSound;
    }

    @Nonnull
    @Override
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
