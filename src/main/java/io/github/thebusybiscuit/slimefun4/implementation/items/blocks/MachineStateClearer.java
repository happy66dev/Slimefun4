package io.github.thebusybiscuit.slimefun4.implementation.items.blocks;

import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.SimpleSlimefunItem;
import io.github.thebusybiscuit.slimefun4.utils.MachineStatePersistence;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class MachineStateClearer extends SimpleSlimefunItem<BlockUseHandler> {

    @ParametersAreNonnullByDefault
    public MachineStateClearer(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    @Nonnull
    public BlockUseHandler getItemHandler() {
        return e -> {
            e.cancel();

            Player player = e.getPlayer();
            ItemStack heldItem = player.getInventory().getItemInMainHand();

            if (heldItem.getType() == Material.AIR) {
                Slimefun.getLocalization().sendMessage(player, "machines.MACHINE_STATE_CLEARER.no-item", true);
                return;
            }

            if (!MachineStatePersistence.hasState(heldItem)) {
                Slimefun.getLocalization().sendMessage(player, "machines.MACHINE_STATE_CLEARER.no-state", true);
                return;
            }

            ItemStack cleared = MachineStatePersistence.clearState(heldItem);
            player.getInventory().setItemInMainHand(cleared);
            Slimefun.getLocalization().sendMessage(player, "machines.MACHINE_STATE_CLEARER.success", true);
        };
    }
}
