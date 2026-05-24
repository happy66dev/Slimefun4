package io.github.thebusybiscuit.slimefun4.core.services;

import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineFeedbackType;
import io.github.thebusybiscuit.slimefun4.core.machines.MachineOperation;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
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

    private final Slimefun plugin;

    private final Set<BlockPosition> activeBlockStates = new HashSet<>();
    private final Map<BlockPosition, Set<Integer>> firedMilestones = new HashMap<>();
    private final Map<BlockPosition, Integer> particleTickCounters = new HashMap<>();

    public MachineFeedbackService(@Nonnull Slimefun plugin) {
        this.plugin = plugin;
    }

    public void onMachineStart(
            @Nonnull Block block, @Nullable MachineFeedbackType type, @Nonnull MachineOperation operation) {
        if (type == null) {
            return;
        }

        BlockPosition pos = new BlockPosition(block);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (hasLitProperty(block.getType())) {
                activeBlockStates.add(pos);
                BlockData data = block.getBlockData();
                if (data instanceof Lightable lightable) {
                    lightable.setLit(true);
                    block.setBlockData(data);
                }
            }
        });

        firedMilestones.put(pos, new HashSet<>());
        particleTickCounters.put(pos, 0);

        spawnParticles(block, type);
    }

    public void onMachineTick(
            @Nonnull Block block, @Nullable MachineFeedbackType type, @Nonnull MachineOperation operation) {
        if (type == null) {
            return;
        }

        BlockPosition pos = new BlockPosition(block);

        int counter = particleTickCounters.getOrDefault(pos, 0);
        counter++;
        particleTickCounters.put(pos, counter);

        if (counter % 2 == 0) {
            spawnParticles(block, type);
        }

        int totalTicks = operation.getTotalTicks();
        if (totalTicks <= 0) {
            return;
        }

        int prevPercent = (operation.getProgress() - 1) * 100 / totalTicks;
        int currPercent = operation.getProgress() * 100 / totalTicks;

        Set<Integer> milestones = firedMilestones.computeIfAbsent(pos, k -> new HashSet<>());
        for (int milestone : new int[] {25, 50, 75}) {
            if (prevPercent < milestone && currPercent >= milestone && !milestones.contains(milestone)) {
                milestones.add(milestone);
                Sound sound = type.getDefaultSound();
                World world = block.getWorld();
                Location loc = block.getLocation();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    world.playSound(loc, sound, SoundCategory.BLOCKS, 1.0f, 1.0f);
                });
            }
        }
    }

    public void onMachineStop(@Nonnull Block block, @Nullable MachineFeedbackType type) {
        if (type == null) {
            return;
        }

        BlockPosition pos = new BlockPosition(block);

        if (activeBlockStates.remove(pos)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                BlockData data = block.getBlockData();
                if (data instanceof Lightable lightable) {
                    lightable.setLit(false);
                    block.setBlockData(data);
                }
            });
        }

        firedMilestones.remove(pos);
        particleTickCounters.remove(pos);
    }

    private void spawnParticles(@Nonnull Block block, @Nonnull MachineFeedbackType type) {
        Particle particle = type.getDefaultParticle();
        World world = block.getWorld();
        double bx = block.getX();
        double by = block.getY();
        double bz = block.getZ();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        double[] skullOffsets = null;
        double cx = 0, cy = 0, cz = 0, sx = 0, sy = 0, sz = 0, speed = 0;

        for (int i = 0; i < 4; i++) {
            if (skullOffsets == null) {
                skullOffsets = new double[16];
            }
            int base = i * 4;
            skullOffsets[base] = bx + 0.5 + rnd.nextDouble(-0.25, 0.25);
            skullOffsets[base + 1] = by + 0.85 + rnd.nextDouble(0, 0.3);
            skullOffsets[base + 2] = bz + 0.5 + rnd.nextDouble(-0.25, 0.25);
            skullOffsets[base + 3] = rnd.nextDouble(0.02);
        }

        cx = bx + 0.5;
        cy = by + 1.05;
        cz = bz + 0.5;
        sx = bx + 0.5 + (rnd.nextBoolean() ? 0.6 : -0.6);
        sz = bz + 0.5 + (rnd.nextBoolean() ? 0.6 : -0.6);
        sy = by + 0.5;
        speed = rnd.nextDouble(0.02);

        final double[] fSkullOffsets = skullOffsets;
        final double fCx = cx, fCy = cy, fCz = cz, fSx = sx, fSy = sy, fSz = sz, fSpeed = speed;

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
            } else {
                world.spawnParticle(particle, fCx, fCy, fCz, 2, 0.15, 0.05, 0.15, fSpeed);
                world.spawnParticle(particle, fSx, fSy, fSz, 1, 0, fSpeed, 0, fSpeed);
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
    }
}
