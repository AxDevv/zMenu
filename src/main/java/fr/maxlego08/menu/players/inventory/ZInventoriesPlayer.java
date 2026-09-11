package fr.maxlego08.menu.players.inventory;

import fr.maxlego08.menu.ZMenuPlugin;
import fr.maxlego08.menu.api.players.inventory.InventoriesPlayer;
import fr.maxlego08.menu.api.players.inventory.InventoryPlayer;
import fr.maxlego08.menu.api.utils.ClearInvType;
import fr.maxlego08.menu.common.utils.nms.NMSUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Keeps the upstream service API while SaoWorld inventories remain owned by Core. */
public class ZInventoriesPlayer implements InventoriesPlayer {
    private final ZMenuPlugin plugin;

    public ZInventoriesPlayer(ZMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void storeInventory(Player player) {
        throw new UnsupportedOperationException("Physical inventory storage is disabled on SaoWorld");
    }

    @Override
    public void storeInventoryTemporary(Player player) {
        var inventory = player.getInventory();
        var remove = ClearInvType.PACKET_EVENT.getRemoveItem();
        for (int slot = 0; slot < 36; slot++) remove.accept(player, slot, inventory);
        if (!NMSUtils.isOneHand()) remove.accept(player, 40, inventory);
    }

    @Override
    public void storeInventoryTemporaryOrClear(Player player) {
        storeInventoryTemporary(player);
    }

    @Override
    public void giveInventory(Player player) {
        player.updateInventory();
    }

    @Override
    public void forceGiveInventory(Player player) {
        giveInventory(player);
    }

    @Override
    public boolean hasSavedInventory(UUID uniqueId) {
        return false;
    }

    @Override
    public Optional<InventoryPlayer> getPlayerInventory(UUID uniqueId) {
        return Optional.empty();
    }

    @Override
    public List<ItemStack> getInventory(UUID uniqueId) {
        return List.of();
    }

    @Override
    public void clearInventorie(UUID uniqueId) {
        // No snapshot exists. Never delete legacy recovery rows.
    }

    @Override
    public void loadInventories() {
        plugin.getLogger().info("SaoWorld packet-only inventories enabled; legacy recovery rows are preserved and never restored automatically");
    }

    @Override
    public void restoreAllInventories() {
        // Live items have never left the player inventory.
    }
}
