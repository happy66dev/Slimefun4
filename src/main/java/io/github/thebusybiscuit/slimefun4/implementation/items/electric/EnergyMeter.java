package io.github.thebusybiscuit.slimefun4.implementation.items.electric;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;

public class EnergyMeter extends SlimefunItem implements HologramOwner {

    private static final String COUNTER_KEY = "energy-counter";
    private static final String OWNER_KEY = "energy-meter-owner";

    private final Map<BlockPosition, String> displayCache = new HashMap<>();

    @ParametersAreNonnullByDefault
    public EnergyMeter(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        this(itemGroup, item, recipeType, recipe, null);
    }

    @ParametersAreNonnullByDefault
    public EnergyMeter(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            @Nullable ItemStack recipeOutput) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);

        addItemHandler(new BlockTicker() {
            @Override
            public void tick(Block b, SlimefunItem sf, SlimefunBlockData data) {
                tickMeter(b, data);
            }

            @Override
            public boolean isSynchronized() {
                return false;
            }
        });

        addItemHandler((BlockUseHandler) this::onRightClick);

        addItemHandler(new BlockPlaceHandler(false) {
            @Override
            public void onPlayerPlace(BlockPlaceEvent e) {
                var data = StorageCacheUtils.getBlock(e.getBlock().getLocation());
                if (data != null) {
                    data.setData(OWNER_KEY, e.getPlayer().getUniqueId().toString());
                }
            }
        });
    }

    private void tickMeter(@Nonnull Block b, @Nonnull SlimefunBlockData data) {
        if (data.isPendingRemove()) return;

        String counterStr = data.getData(COUNTER_KEY);
        long accumulated = counterStr != null ? Long.parseLong(counterStr) : 0L;
        String display = NumberUtils.getCompactDouble(accumulated) + " J \u26A1";

        BlockPosition pos = new BlockPosition(b.getLocation());
        String cached = displayCache.get(pos);
        if (display.equals(cached)) return;
        displayCache.put(pos, display);

        updateHologram(b, display, data::isPendingRemove);
    }

    private void onRightClick(@Nonnull PlayerRightClickEvent e) {
        if (!e.getClickedBlock().isPresent()) return;
        Player p = e.getPlayer();
        Block b = e.getClickedBlock().get();

        if (p.isSneaking()) {
            String ownerStr = StorageCacheUtils.getBlock(b.getLocation()).getData(OWNER_KEY);
            if (ownerStr != null && !ownerStr.isEmpty()) {
                UUID ownerUuid = UUID.fromString(ownerStr);
                if (p.getUniqueId().equals(ownerUuid)) {
                    StorageCacheUtils.getBlock(b.getLocation()).setData(COUNTER_KEY, "0");
                    displayCache.remove(new BlockPosition(b.getLocation()));
                    p.sendMessage(ChatColors.color("&a电量计数器已清零"));
                    return;
                }
            }
        }

        String counterStr = StorageCacheUtils.getBlock(b.getLocation()).getData(COUNTER_KEY);
        long accumulated = counterStr != null ? Long.parseLong(counterStr) : 0L;
        p.sendMessage(ChatColors.color("&6\u26A1 &e累计通过电量: &f" + accumulated + " &7J"));
    }
}
