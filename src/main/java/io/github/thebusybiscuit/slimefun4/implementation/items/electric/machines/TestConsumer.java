package io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ASlimefunDataContainer;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.blocks.BlockPosition;
import io.github.bakedlibs.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.HologramOwner;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.handlers.SimpleBlockBreakHandler;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.AContainer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public class TestConsumer extends AContainer implements HologramOwner {

    private static final String ENERGY_CONSUMPTION_KEY = "test-con-consumption";
    private static final int CAPACITY = 32000;

    private static final Map<UUID, PendingInput> pendingInputs = new ConcurrentHashMap<>();
    private static volatile boolean listenerRegistered = false;

    private final Map<BlockPosition, String> displayCache = new ConcurrentHashMap<>();

    @ParametersAreNonnullByDefault
    public TestConsumer(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        this.hidden = true;

        setCapacity(CAPACITY);
        setEnergyConsumption(1);
        setProcessingSpeed(1);

        ensureChatListener();
    }

    @Override
    public void preRegister() {
        super.preRegister();

        addItemHandler((BlockUseHandler) this::onRightClick);

        addItemHandler(new SimpleBlockBreakHandler() {
            @Override
            public void onBlockBreak(@Nonnull Block b) {
                removeHologram(b);
                displayCache.remove(new BlockPosition(b.getLocation()));
                clearPendingInputs(b.getLocation());
            }
        });
    }

    @Override
    protected void tick(Block b) {
        ASlimefunDataContainer data = StorageCacheUtils.getDataContainer(b.getLocation());
        if (data == null || data.isPendingRemove()) {
            return;
        }

        long consumption = readConsumptionValue(data);
        if (consumption > 0) {
            long charge = getChargeLong(b.getLocation());
            if (charge >= consumption) {
                removeCharge(b.getLocation(), consumption);
            }
        }

        if (data instanceof SlimefunBlockData blockData) {
            updateDisplay(b, blockData, consumption);
        }
    }

    private void updateDisplay(@Nonnull Block b, @Nonnull SlimefunBlockData data, long consumption) {
        if (data.isPendingRemove()) {
            return;
        }

        long charge = getChargeLong(b.getLocation());
        String display = "&c\u26A1 &f" + consumption + " J/t | " + charge + "/" + CAPACITY + " J";

        BlockPosition pos = new BlockPosition(b.getLocation());
        String cached = displayCache.get(pos);
        if (display.equals(cached)) {
            return;
        }
        displayCache.put(pos, display);

        updateHologram(b, ChatColors.color(display), data::isPendingRemove);
    }

    @Override
    public int getCapacity() {
        return CAPACITY;
    }

    @Override
    public void registerDefaultRecipes() {}

    @Override
    public ItemStack getProgressBar() {
        return new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
    }

    @Override
    public String getMachineIdentifier() {
        return "TEST_CONSUMER";
    }

    private void onRightClick(@Nonnull PlayerRightClickEvent e) {
        if (!e.getClickedBlock().isPresent()) {
            return;
        }
        Player p = e.getPlayer();
        Block b = e.getClickedBlock().get();
        Location loc = b.getLocation();
        var data = StorageCacheUtils.getBlock(loc);
        if (data == null || data.isPendingRemove()) {
            return;
        }

        e.cancel();

        if (p.isSneaking()) {
            sendStatus(p, readConsumptionValue(data));
            return;
        }

        PendingInput input = new PendingInput(loc.clone(), System.currentTimeMillis());
        pendingInputs.put(p.getUniqueId(), input);
        Bukkit.getScheduler()
                .runTaskLater(
                        Slimefun.instance(),
                        () -> {
                            if (pendingInputs.remove(p.getUniqueId(), input)) {
                                p.sendMessage(ChatColors.color("&7测试用电器设置已超时，已取消修改"));
                            }
                        },
                        PendingInput.TIMEOUT_TICKS);
        p.sendMessage(ChatColors.color("&c\u26A1 &7请在聊天栏输入每 tick 消耗值 (J/t):"));
        p.sendMessage(ChatColors.color("&7输入正整数设置消耗 | 输入 &a取消 &7或等待30秒退出"));
    }

    private static void sendStatus(@Nonnull Player p, long consumption) {
        p.sendMessage(ChatColors.color("&c\u26A1 &7当前消耗: &f" + consumption + " J/t"));
    }

    private static long readConsumptionValue(@Nonnull ASlimefunDataContainer data) {
        String val = data.getData(ENERGY_CONSUMPTION_KEY);
        if (val != null) {
            try {
                return Long.parseLong(val);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
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
            synchronized (TestConsumer.class) {
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
            if (input == null) {
                return;
            }

            e.setCancelled(true);
            if (input.isExpired()) {
                Bukkit.getScheduler()
                        .runTask(Slimefun.instance(), () -> p.sendMessage(ChatColors.color("&7测试用电器设置已超时，已取消修改")));
                return;
            }

            Location loc = input.location;
            String msg = e.getMessage().trim();

            if (msg.equalsIgnoreCase("\u53D6\u6D88") || msg.equalsIgnoreCase("cancel")) {
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
                p.sendMessage(ChatColors.color("&c测试用电器已被拆除"));
                return;
            }

            data.setData(ENERGY_CONSUMPTION_KEY, String.valueOf(value));
            p.sendMessage(ChatColors.color("&c\u26A1 &a消耗已设为: &f" + value + " J/t"));
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
