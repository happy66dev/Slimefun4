package io.github.thebusybiscuit.slimefun4.implementation.items.cargo;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.core.attributes.rotations.NotRotatable;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.cargo.CargoNet;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.Optional;
import java.util.logging.Level;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.apache.commons.lang.Validate;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CargoManager extends SlimefunItem implements HologramOwner, NotRotatable, EnergyNetComponent {

    private static final long BASE_CAPACITY = 128;
    private static final long CAPACITY_PER_INPUT = 4;
    private static final long MAX_CAPACITY = 10000;

    @ParametersAreNonnullByDefault
    public CargoManager(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);

        addItemHandler(onBreak());
    }

    @Nonnull
    private BlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {

            @Override
            public void onBlockBreak(@Nonnull Block b) {
                removeHologram(b);
            }
        };
    }

    @Override
    @Nonnull
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    @Override
    public int getCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public long getChargeCapacityLong(@Nonnull Location l) {
        var data = StorageCacheUtils.getBlock(l);
        int inputCount = 0;
        if (data != null) {
            String countStr = data.getData("cargo-input-count");
            if (countStr != null) {
                try {
                    inputCount = Integer.parseInt(countStr);
                } catch (NumberFormatException e) {
                    inputCount = 0;
                }
            }
        }
        return Math.min(MAX_CAPACITY, BASE_CAPACITY + CAPACITY_PER_INPUT * inputCount);
    }

    @Override
    public void setCharge(@Nonnull Location l, long charge) {
        Validate.notNull(l, "Location was null!");
        Validate.isTrue(charge >= 0, "You can only set a charge of zero or more!");

        try {
            long capacity = getChargeCapacityLong(l);

            if (capacity > 0) {
                charge = NumberUtils.clamp(0, charge, capacity);

                if (charge != getChargeLong(l)) {
                    var blockData = StorageCacheUtils.getDataContainer(l);

                    if (blockData == null || blockData.isPendingRemove()) {
                        return;
                    }

                    if (!blockData.isDataLoaded()) {
                        StorageCacheUtils.requestLoad(blockData);
                        return;
                    }

                    blockData.setData("energy-charge", String.valueOf(charge));
                }
            }
        } catch (Exception | LinkageError x) {
            Slimefun.logger()
                    .log(
                            Level.SEVERE,
                            x,
                            () -> "Exception while trying to set the energy-charge for \""
                                    + getId()
                                    + "\" at "
                                    + l.getBlockX()
                                    + ", "
                                    + l.getBlockY()
                                    + ", "
                                    + l.getBlockZ());
        }
    }

    @Override
    public void preRegister() {
        addItemHandler(
                new BlockTicker() {

                    @Override
                    public void tick(Block b, SlimefunItem item, SlimefunBlockData data) {
                        CargoNet.getNetworkFromLocationOrCreate(b.getLocation()).tick(b, data);
                    }

                    @Override
                    public boolean isSynchronized() {
                        return false;
                    }
                },
                new BlockUseHandler() {

                    @Override
                    public void onRightClick(PlayerRightClickEvent e) {
                        Optional<Block> block = e.getClickedBlock();

                        if (block.isPresent()) {
                            Player p = e.getPlayer();
                            Block b = block.get();

                            var blockData = StorageCacheUtils.getBlock(b.getLocation());
                            if (blockData.getData("visualizer") == null) {
                                blockData.setData("visualizer", "disabled");
                                p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c货运网络可视化: " + "&4\u2718"));
                            } else {
                                blockData.removeData("visualizer");
                                p.sendMessage(ChatColor.translateAlternateColorCodes('&', "&c货运网络可视化: " + "&2\u2714"));
                            }
                        }
                    }
                });
    }
}
