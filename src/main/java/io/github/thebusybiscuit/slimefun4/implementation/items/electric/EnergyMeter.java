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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
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

    private final Map<BlockPosition, String> displayCache = new ConcurrentHashMap<>();

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
                if (!EnergyAccessoryPlacement.validate(
                        e, "ENERGY_METER", "&c只能在连接器正上方放置", "&c该位置已有另一种附件，电量计数器与限电器只能二选一")) {
                    return;
                }
                var data = StorageCacheUtils.getBlock(e.getBlock().getLocation());
                if (data != null) {
                    data.setData(OWNER_KEY, e.getPlayer().getUniqueId().toString());
                }
            }
        });
    }

    private void tickMeter(@Nonnull Block b, @Nonnull SlimefunBlockData data) {
        if (data.isPendingRemove()) return;

        long accumulated = parseLongOrZero(data, COUNTER_KEY);
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
        var data = StorageCacheUtils.getBlock(b.getLocation());
        if (data == null || data.isPendingRemove()) {
            return;
        }

        if (p.isSneaking()) {
            String ownerStr = data.getData(OWNER_KEY);
            if (ownerStr != null && !ownerStr.isEmpty()) {
                UUID ownerUuid = parseUuidOrNull(data, ownerStr);
                if (p.getUniqueId().equals(ownerUuid)) {
                    data.setData(COUNTER_KEY, "0");
                    displayCache.remove(new BlockPosition(b.getLocation()));
                    p.sendMessage(ChatColors.color("&a电量计数器已清零"));
                    return;
                }
            }
        }

        long accumulated = parseLongOrZero(data, COUNTER_KEY);
        p.sendMessage(ChatColors.color("&6\u26A1 &e累计通过电量: &f" + accumulated + " &7J"));
    }

    private static long parseLongOrZero(@Nonnull SlimefunBlockData data, @Nonnull String key) {
        String value = data.getData(key);
        if (value == null || value.isEmpty()) {
            return 0L;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            data.removeData(key);
            return 0L;
        }
    }

    private static UUID parseUuidOrNull(@Nonnull SlimefunBlockData data, @Nonnull String ownerStr) {
        try {
            return UUID.fromString(ownerStr);
        } catch (IllegalArgumentException e) {
            data.removeData(OWNER_KEY);
            return null;
        }
    }
}
