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
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNet;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.utils.NumberUtils;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class CurrentLimiter extends SlimefunItem implements HologramOwner {

    private static final String LIMIT_KEY = "current-limit";
    private static final String OWNER_KEY = "current-limiter-owner";
    private static final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private static volatile boolean listenerRegistered = false;

    private final Map<BlockPosition, String> displayCache = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public CurrentLimiter(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        this(itemGroup, item, recipeType, recipe, null);
    }

    @ParametersAreNonnullByDefault
    public CurrentLimiter(
            ItemGroup itemGroup,
            SlimefunItemStack item,
            RecipeType recipeType,
            ItemStack[] recipe,
            @Nullable ItemStack recipeOutput) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);

        addItemHandler(new BlockTicker() {
            @Override
            public void tick(Block b, SlimefunItem sf, SlimefunBlockData data) {
                tickLimiter(b, data);
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
                        e, "CURRENT_LIMITER", "&c只能在连接器正上方放置", "&c该位置已有另一种附件，电量计数器与限电器只能二选一")) {
                    return;
                }
                Location placed = e.getBlock().getLocation();
                var data = StorageCacheUtils.getBlock(placed);
                if (data != null) {
                    data.setData(OWNER_KEY, e.getPlayer().getUniqueId().toString());
                }
            }
        });

        addItemHandler(onBreak());

        ensureChatListener();
    }

    @Nonnull
    private BlockBreakHandler onBreak() {
        return new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(@Nonnull Block b) {
                Location connLoc = b.getLocation().clone().subtract(0, 1, 0);
                EnergyNet net = EnergyNet.getNetworkFromLocation(connLoc);
                if (net != null) {
                    net.removeConnectorLimit(connLoc);
                } else {
                    EnergyNet.onMachinePlaced(connLoc);
                }
                displayCache.remove(new BlockPosition(b.getLocation()));
                clearPendingInputs(b.getLocation());
                removeHologram(b);
            }
        };
    }

    private void tickLimiter(@Nonnull Block b, @Nonnull SlimefunBlockData data) {
        if (data.isPendingRemove()) return;

        Long limit = readLimitValue(data);
        String display;
        if (limit == null) {
            display = "&7\u26A1 &f\u221E J/t";
        } else if (limit == 0L) {
            display = "&4\u26A1 \u26D4 \u7981\u7528";
        } else {
            display = "&b\u26A1 &f\u2264 " + NumberUtils.getCompactDouble(limit) + " J/t";
        }

        BlockPosition pos = new BlockPosition(b.getLocation());
        String cached = displayCache.get(pos);
        if (display.equals(cached)) return;
        displayCache.put(pos, display);

        updateHologram(b, ChatColors.color(display), data::isPendingRemove);
    }

    private void onRightClick(@Nonnull PlayerRightClickEvent e) {
        if (!e.getClickedBlock().isPresent()) return;
        Player p = e.getPlayer();
        Block b = e.getClickedBlock().get();
        Location loc = b.getLocation();
        var data = StorageCacheUtils.getBlock(loc);
        if (data == null || data.isPendingRemove()) {
            return;
        }

        if (p.isSneaking()) {
            String ownerStr = data.getData(OWNER_KEY);
            if (ownerStr != null && !ownerStr.isEmpty()) {
                UUID ownerUuid = parseUuidOrNull(data, ownerStr);
                if (p.getUniqueId().equals(ownerUuid)) {
                    Location connLoc = loc.clone().subtract(0, 1, 0);
                    EnergyNet net = EnergyNet.getNetworkFromLocation(connLoc);
                    if (net != null) {
                        net.removeConnectorLimit(connLoc);
                    } else {
                        EnergyNet.onMachinePlaced(connLoc);
                    }
                    data.removeData(LIMIT_KEY);
                    displayCache.remove(new BlockPosition(loc));
                    p.sendMessage(ChatColors.color("&a限电器已设为无限制"));
                    return;
                }
            }
            sendLimitStatus(p, readLimitValue(data));
            return;
        }

        String ownerStr = data.getData(OWNER_KEY);
        if (ownerStr == null || ownerStr.isEmpty()) return;
        UUID ownerUuid = parseUuidOrNull(data, ownerStr);
        if (ownerUuid == null) {
            sendLimitStatus(p, readLimitValue(data));
            return;
        }
        if (!p.getUniqueId().equals(ownerUuid)) {
            sendLimitStatus(p, readLimitValue(data));
            return;
        }

        PendingInput input = new PendingInput(loc.clone(), System.currentTimeMillis());
        pendingInputs.put(p.getUniqueId(), input);
        Bukkit.getScheduler()
                .runTaskLater(
                        Slimefun.instance(),
                        () -> {
                            if (pendingInputs.remove(p.getUniqueId(), input)) {
                                p.sendMessage(ChatColors.color("&7限电器设置已超时，已取消修改"));
                            }
                        },
                        PendingInput.TIMEOUT_TICKS);
        p.sendMessage(ChatColors.color("&b\u26A1 &7请在聊天栏输入限制值 (J/t):"));
        p.sendMessage(ChatColors.color("&7输入 &c0 &7禁用连接器 | 输入正整数设置限额 | 输入 &a取消 &7或等待30秒退出"));
    }

    private static void sendLimitStatus(@Nonnull Player p, @Nullable Long limit) {
        if (limit == null) {
            p.sendMessage(ChatColors.color("&b\u26A1 &7当前限制: &f\u221E J/t &7(无限制)"));
        } else if (limit == 0L) {
            p.sendMessage(ChatColors.color("&4\u26A1 &7当前限制: &c\u26D4 \u7981\u7528"));
        } else {
            p.sendMessage(ChatColors.color("&b\u26A1 &7当前限制: &f\u2264 " + limit + " J/t"));
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

    @Nullable private static Long readLimitValue(@Nonnull SlimefunBlockData data) {
        String limitStr = data.getData(LIMIT_KEY);
        if (limitStr == null) {
            return null;
        }

        try {
            return Long.parseLong(limitStr);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void clearPendingInputs(@Nonnull Location loc) {
        pendingInputs.entrySet().removeIf(entry -> isSameBlock(entry.getValue().location, loc));
    }

    private static boolean isSameBlock(@Nonnull Location a, @Nonnull Location b) {
        return a.getWorld() != null
                && a.getWorld().equals(b.getWorld())
                && a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }

    private static void ensureChatListener() {
        if (!listenerRegistered) {
            synchronized (CurrentLimiter.class) {
                if (!listenerRegistered) {
                    Bukkit.getPluginManager().registerEvents(new ChatInputListener(), Slimefun.instance());
                    listenerRegistered = true;
                }
            }
        }
    }

    static class ChatInputListener implements Listener {
        @EventHandler
        public void onChat(AsyncPlayerChatEvent e) {
            Player p = e.getPlayer();
            PendingInput input = pendingInputs.remove(p.getUniqueId());
            if (input == null) return;

            e.setCancelled(true);
            if (input.isExpired()) {
                Bukkit.getScheduler()
                        .runTask(Slimefun.instance(), () -> p.sendMessage(ChatColors.color("&7限电器设置已超时，已取消修改")));
                return;
            }

            Location loc = input.location;
            String msg = e.getMessage().trim();

            if (msg.equalsIgnoreCase("取消") || msg.equalsIgnoreCase("cancel")) {
                Bukkit.getScheduler().runTask(Slimefun.instance(), () -> p.sendMessage(ChatColors.color("&7已取消设置")));
                return;
            }

            long value;
            try {
                value = Long.parseLong(msg);
            } catch (NumberFormatException ex) {
                Bukkit.getScheduler()
                        .runTask(Slimefun.instance(), () -> p.sendMessage(ChatColors.color("&c请输入有效的非负整数")));
                return;
            }

            if (value < 0) {
                Bukkit.getScheduler().runTask(Slimefun.instance(), () -> p.sendMessage(ChatColors.color("&c请输入非负整数")));
                return;
            }

            Bukkit.getScheduler().runTask(Slimefun.instance(), () -> applyInput(p, loc, value));
        }

        private void applyInput(@Nonnull Player p, @Nonnull Location loc, long value) {
            var data = StorageCacheUtils.getBlock(loc);
            if (data == null || data.isPendingRemove()) {
                p.sendMessage(ChatColors.color("&c限电器已被拆除"));
                return;
            }

            Location connLoc = loc.clone().subtract(0, 1, 0);
            EnergyNet net = EnergyNet.getNetworkFromLocation(connLoc);

            if (value == 0) {
                data.setData(LIMIT_KEY, "0");
                if (net != null) {
                    net.setConnectorLimit(connLoc, 0L);
                }
                p.sendMessage(ChatColors.color("&4\u26A1 \u26D4 &c连接器已禁用"));
            } else {
                data.setData(LIMIT_KEY, String.valueOf(value));
                if (net != null) {
                    net.setConnectorLimit(connLoc, value);
                } else {
                    EnergyNet.onMachinePlaced(connLoc);
                }
                p.sendMessage(ChatColors.color("&b\u26A1 &a限制已设为: &f\u2264 " + value + " J/t"));
            }
        }

        @EventHandler
        public void onQuit(PlayerQuitEvent e) {
            pendingInputs.remove(e.getPlayer().getUniqueId());
        }
    }

    private static final class PendingInput {
        private static final long TIMEOUT_MILLIS = 30_000L;
        private static final long TIMEOUT_TICKS = 20L * 30L;

        private final Location location;
        private final long createdAt;

        private PendingInput(@Nonnull Location location, long createdAt) {
            this.location = location;
            this.createdAt = createdAt;
        }

        private boolean isExpired() {
            return System.currentTimeMillis() - createdAt > TIMEOUT_MILLIS;
        }
    }
}
