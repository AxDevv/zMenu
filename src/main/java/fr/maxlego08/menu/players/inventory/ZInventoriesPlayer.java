package fr.maxlego08.menu.players.inventory;

import fr.maxlego08.menu.ZMenuPlugin;
import fr.maxlego08.menu.api.players.inventory.InventoriesPlayer;
import fr.maxlego08.menu.api.players.inventory.InventoryPlayer;
import fr.maxlego08.menu.api.storage.dto.InventoryDTO;
import fr.maxlego08.menu.zcore.logger.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.BiConsumer;

public class ZInventoriesPlayer implements InventoriesPlayer {

    private final Map<UUID, InventoryPlayer> inventories = new HashMap<>();
    private final ZMenuPlugin plugin;
    private long lastSave;

    public ZInventoriesPlayer(ZMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void storeInventory(@NonNull Player player) {
        if (hasSavedInventory(player.getUniqueId())) {
            log("STORE permanent skipped because snapshot already exists player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " existingSlots=" + snapshotSlots(player.getUniqueId()));
            return;
        }

        ZInventoryPlayer inventoryPlayer = new ZInventoryPlayer(this.plugin);
        log("STORE permanent start player=" + player.getName()
            + " uuid=" + player.getUniqueId());
        inventoryPlayer.storeInventory(player);
        inventories.put(player.getUniqueId(), inventoryPlayer);
        log("STORE permanent saved player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " slots=" + slots(inventoryPlayer));

        this.plugin.getStorageManager().storeInventory(player.getUniqueId(), inventoryPlayer);
        log("STORE permanent persisted player=" + player.getName()
            + " uuid=" + player.getUniqueId());
    }

    @Override
    public void storeInventoryTemporary(@NonNull Player player) {
        if (hasSavedInventory(player.getUniqueId())) {
            log("STORE temporary skipped because snapshot already exists player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " existingSlots=" + snapshotSlots(player.getUniqueId()));
            return;
        }

        ZInventoryPlayer inventoryPlayer = new ZInventoryPlayer(this.plugin);
        log("STORE temporary start player=" + player.getName()
            + " uuid=" + player.getUniqueId());
        inventoryPlayer.storeInventory(player, true);
        inventories.put(player.getUniqueId(), inventoryPlayer);
        log("STORE temporary saved player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " slots=" + slots(inventoryPlayer));
    }



    private void restoreInventory(Player player, String actionName, BiConsumer<InventoryPlayer, Player> restoreAction) {
        Optional<InventoryPlayer> optional = this.getPlayerInventory(player.getUniqueId());
        if (optional.isPresent()) {
            InventoryPlayer inventoryPlayer = optional.get();
            log("RESTORE " + actionName + " start player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " slots=" + slots(inventoryPlayer)
                + " permanent=" + inventoryPlayer.isPermanent());
            restoreAction.accept(inventoryPlayer, player);
            inventories.remove(player.getUniqueId());
            log("RESTORE " + actionName + " removed snapshot player=" + player.getName()
                + " uuid=" + player.getUniqueId());
            this.plugin.getStorageManager().removeInventory(player.getUniqueId());
            log("RESTORE " + actionName + " removed persisted snapshot player=" + player.getName()
                + " uuid=" + player.getUniqueId());
        } else {
            log("RESTORE " + actionName + " skipped because no snapshot exists player=" + player.getName()
                + " uuid=" + player.getUniqueId());
        }
    }

    @Override
    public void giveInventory(@NonNull Player player) {
        restoreInventory(player, "give", InventoryPlayer::giveInventory);
    }

    @Override
    public void forceGiveInventory(@NonNull Player player) {
        restoreInventory(player, "forceGive", InventoryPlayer::forceGiveInventory);
    }

    @Override
    public boolean hasSavedInventory(@NonNull UUID uniqueId) {
        return inventories.containsKey(uniqueId);
    }

    @Override
    public @NonNull Optional<InventoryPlayer> getPlayerInventory(@NonNull UUID uniqueId) {
        return Optional.ofNullable(inventories.getOrDefault(uniqueId, null));
    }

    @Override
    public @NonNull List<ItemStack> getInventory(@NonNull UUID uniqueId) {
        Optional<InventoryPlayer> optional = this.getPlayerInventory(uniqueId);
        if (optional.isPresent()) {
            InventoryPlayer inventoryPlayer = optional.get();
            return inventoryPlayer.getItemStacks();
        }
        return Collections.emptyList();
    }

    @Override
    public void clearInventorie(@NonNull UUID uniqueId) {
        log("CLEAR snapshot uuid=" + uniqueId
            + " existed=" + inventories.containsKey(uniqueId)
            + " slots=" + snapshotSlots(uniqueId));
        inventories.remove(uniqueId);
        this.plugin.getStorageManager().removeInventory(uniqueId);
        log("CLEAR snapshot persisted removal uuid=" + uniqueId);
    }

    @EventHandler
    public void onDisconnect(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Optional<InventoryPlayer> playerInventory = this.getPlayerInventory(player.getUniqueId());
        log("QUIT player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " hasSnapshot=" + playerInventory.isPresent()
            + " slots=" + snapshotSlots(player.getUniqueId()));
        if (playerInventory.isPresent()) {
            if (playerInventory.get().isPermanent())
                this.forceGiveInventory(player);
            else
                this.clearInventorie(player.getUniqueId());
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Optional<InventoryPlayer> playerInventory = this.getPlayerInventory(player.getUniqueId());
        log("JOIN player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " hasSnapshot=" + playerInventory.isPresent()
            + " slots=" + snapshotSlots(player.getUniqueId()));
        if (playerInventory.isPresent()) {
            if (playerInventory.get().isPermanent())
                this.giveInventory(player);
        }
    }

    @Override
    public void loadInventories() {
        Map<UUID, ZInventoryPlayer> loadedInventories = new HashMap<>();
        for (InventoryDTO inventory : this.plugin.getStorageManager().loadInventories()) {
            var inventoryPlayer = new ZInventoryPlayer(this.plugin);
            String[] serializedItems = inventory.inventory().split(";");
            for (String serializedItem : serializedItems) {
                String[] parts = serializedItem.split(":");
                if (parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty()) {
                    try {
                        int slot = Integer.parseInt(parts[0]);
                        inventoryPlayer.getItems().put(slot, parts[1]);
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            loadedInventories.put(inventory.player_id(), inventoryPlayer);
            log("LOAD persisted snapshot uuid=" + inventory.player_id()
                + " slots=" + slots(inventoryPlayer));
        }
        this.inventories.putAll(loadedInventories);
        log("LOAD persisted snapshots total=" + loadedInventories.size());
    }

    @Override
    public void restoreAllInventories() {
        log("RESTORE_ALL start snapshots=" + inventories.size());
        new HashMap<>(inventories).forEach((uuid, inventoryPlayer) -> {
            Player player = Bukkit.getPlayer(uuid);
            log("RESTORE_ALL entry uuid=" + uuid
                + " online=" + (player != null && player.isOnline())
                + " slots=" + slots(inventoryPlayer));
            if (player != null && player.isOnline()) {
                inventoryPlayer.forceGiveInventory(player);
            }
            inventories.remove(uuid);
        });
        log("RESTORE_ALL end snapshots=" + inventories.size());
    }

    private void log(String message) {
        Logger.info("[PlayerInvDebug] " + message, Logger.LogType.WARNING);
    }

    private String snapshotSlots(UUID uniqueId) {
        return this.getPlayerInventory(uniqueId)
            .map(this::slots)
            .orElse("[]");
    }

    private String slots(InventoryPlayer inventoryPlayer) {
        return new TreeSet<>(inventoryPlayer.getItems().keySet()).toString();
    }
}
