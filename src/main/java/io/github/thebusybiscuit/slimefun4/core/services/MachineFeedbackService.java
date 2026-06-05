package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedback;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedbackType;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Lightable;

public class MachineFeedbackService {

    private static final int[] MILESTONES = {25, 50, 75};

    private final Slimefun plugin;

    private final Set<BlockPosition> activeBlockStates = ConcurrentHashMap.newKeySet();
    private final Map<BlockPosition, Set<Integer>> firedMilestones = new ConcurrentHashMap<>();
    private final Map<BlockPosition, Integer> particleTickCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<BlockPosition, BlockPosition> positionCache = new ConcurrentHashMap<>();

    public MachineFeedbackService(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
    }

    @Nonnull
    private BlockPosition getPosition(@Nonnull Block block) {
        return positionCache.computeIfAbsent(new BlockPosition(block), k -> k);
    }

    public void onMachineStart(
            @Nonnull Block block, @Nullable MachineFeedback type, @Nonnull MachineOperation operation) {
        if (type == null) {
            return;
        }

        BlockPosition pos = getPosition(block);
        Bukkit.getScheduler().runTask(plugin, () -> {
            type.onMachineStart(block);
            if (hasLitProperty(block.getType())) {
                BlockData data = block.getBlockData();
                if (data instanceof Lightable lightable) {
                    lightable.setLit(true);
                    block.setBlockData(data);
                    activeBlockStates.add(pos);
                }
            }
        });

        firedMilestones.put(pos, ConcurrentHashMap.newKeySet());
        particleTickCounters.put(pos, 0);

        spawnParticles(block, type);
    }

    public void onMachineTick(
            @Nonnull Block block, @Nullable MachineFeedback type, @Nonnull MachineOperation operation) {
        if (type == null) {
            return;
        }

        BlockPosition pos = getPosition(block);

        int counter = particleTickCounters.getOrDefault(pos, 0);
        counter++;
        particleTickCounters.put(pos, counter);

        if (counter % 2 == 0) {
            spawnParticles(block, type);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            type.onMachineTick(block, operation);
        });

        int totalTicks = operation.getTotalTicks();
        if (totalTicks <= 0) {
            return;
        }

        int currPercent = (int) ((long) operation.getProgress() * 100 / totalTicks);

        Set<Integer> milestones = firedMilestones.computeIfAbsent(pos, k -> ConcurrentHashMap.newKeySet());
        int highestNewMilestone = -1;
        for (int milestone : MILESTONES) {
            if (currPercent >= milestone && !milestones.contains(milestone)) {
                highestNewMilestone = milestone;
            }
        }

        if (highestNewMilestone >= 0) {
            for (int milestone : MILESTONES) {
                if (milestone <= highestNewMilestone) {
                    milestones.add(milestone);
                }
            }

            Sound sound = type.getDefaultSound();
            World world = block.getWorld();
            Location loc = block.getLocation();
            Bukkit.getScheduler().runTask(plugin, () -> {
                world.playSound(loc, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
            });
        }
    }

    public void onMachineStop(@Nonnull Block block, @Nullable MachineFeedback type) {
        if (type == null) {
            return;
        }

        BlockPosition pos = getPosition(block);

        Bukkit.getScheduler().runTask(plugin, () -> {
            type.onMachineStop(block);
            if (activeBlockStates.remove(pos)) {
                BlockData data = block.getBlockData();
                if (data instanceof Lightable lightable) {
                    lightable.setLit(false);
                    block.setBlockData(data);
                }
            }
        });

        firedMilestones.remove(pos);
        particleTickCounters.remove(pos);
        positionCache.remove(pos);
    }

    private void spawnParticles(@Nonnull Block block, @Nonnull MachineFeedback type) {
        Particle particle = type.getDefaultParticle();
        if (particle == null) {
            return;
        }

        MachineFeedbackType.ParticleOffset offset = type.getParticleOffset();
        World world = block.getWorld();
        double bx = block.getX();
        double by = block.getY();
        double bz = block.getZ();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        double[] skullOffsets = new double[16];
        for (int i = 0; i < 4; i++) {
            int base = i * 4;
            skullOffsets[base] = bx + 0.5 + rnd.nextDouble(-0.25, 0.25);
            skullOffsets[base + 1] = by + 0.85 + rnd.nextDouble(0, 0.3);
            skullOffsets[base + 2] = bz + 0.5 + rnd.nextDouble(-0.25, 0.25);
            skullOffsets[base + 3] = rnd.nextDouble(0.02);
        }

        double cx = bx + 0.5;
        double cz = bz + 0.5;
        double sideX = bx + 0.5 + (rnd.nextBoolean() ? 0.6 : -0.6);
        double sideZ = bz + 0.5 + (rnd.nextBoolean() ? 0.6 : -0.6);
        double speed = rnd.nextDouble(0.02);

        final double[] fSkullOffsets = skullOffsets;
        final double fCx = cx, fCz = cz, fSideX = sideX, fSideZ = sideZ, fSpeed = speed;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (isSkull(block.getType())) {
                for (int i = 0; i < 4; i++) {
                    int base = i * 4;
                    world.spawnParticle(
                            particle,
                            fSkullOffsets[base],
                            fSkullOffsets[base + 1],
                            fSkullOffsets[base + 2],
                            1,
                            0,
                            fSkullOffsets[base + 3],
                            0,
                            fSkullOffsets[base + 3]);
                }
                return;
            }

            switch (offset) {
                case TOP -> {
                    world.spawnParticle(particle, fCx, by + 1.05, fCz, 2, 0.15, 0.05, 0.15, fSpeed);
                }
                case SIDE -> {
                    world.spawnParticle(particle, fSideX, by + 0.5, fSideZ, 1, 0, fSpeed, 0, fSpeed);
                }
                case BOTTOM -> {
                    world.spawnParticle(particle, fCx, by - 0.05, fCz, 2, 0.15, 0.05, 0.15, fSpeed);
                }
                case HEAD_ONLY -> {
                    world.spawnParticle(particle, fCx, by + 1.05, fCz, 1, 0.1, 0.1, 0.1, fSpeed);
                }
                default -> {
                    world.spawnParticle(particle, fCx, by + 1.05, fCz, 2, 0.15, 0.05, 0.15, fSpeed);
                    world.spawnParticle(particle, fSideX, by + 0.5, fSideZ, 1, 0, fSpeed, 0, fSpeed);
                }
            }
        });
    }

    private boolean hasLitProperty(@Nonnull Material mat) {
        return mat == Material.FURNACE || mat == Material.BLAST_FURNACE || mat == Material.SMOKER;
    }

    private boolean isSkull(@Nonnull Material mat) {
        return mat == Material.PLAYER_HEAD
                || mat == Material.PLAYER_WALL_HEAD
                || mat == Material.SKELETON_SKULL
                || mat == Material.SKELETON_WALL_SKULL
                || mat == Material.WITHER_SKELETON_SKULL
                || mat == Material.WITHER_SKELETON_WALL_SKULL
                || mat == Material.ZOMBIE_HEAD
                || mat == Material.ZOMBIE_WALL_HEAD
                || mat == Material.CREEPER_HEAD
                || mat == Material.CREEPER_WALL_HEAD;
    }

    public void cleanup() {
        activeBlockStates.clear();
        firedMilestones.clear();
        particleTickCounters.clear();
        positionCache.clear();
    }
}
