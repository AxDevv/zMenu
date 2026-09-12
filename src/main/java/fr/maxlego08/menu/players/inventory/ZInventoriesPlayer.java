package fr.maxlego08.menu.players.inventory;

import fr.maxlego08.menu.ZMenuPlugin;
import fr.maxlego08.menu.api.players.inventory.InventoriesPlayer;
import fr.maxlego08.menu.api.players.inventory.InventoryPlayer;
import fr.maxlego08.menu.api.storage.dto.InventoryDTO;
import fr.maxlego08.menu.common.utils.nms.ItemStackUtils;
import fr.maxlego08.menu.inventory.zinv.ZInventory;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.util.*;
import java.util.function.BiConsumer;

public class ZInventoriesPlayer implements InventoriesPlayer {

    private final Map<UUID, InventoryPlayer> inventories = new HashMap<>();
    private final Map<UUID, InventoryPlayer> quarantinedInventories = new HashMap<>();
    private final ZMenuPlugin plugin;
    private long lastSave;

    public ZInventoriesPlayer(ZMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void storeInventory(@NonNull Player player) {
        if (this.hasSavedInventory(player.getUniqueId())) {
            // Something is wrong
            // Logger.info("The plugin tries to save an inventory while the player already has an inventory saved!");
            return;
        }

        ZInventoryPlayer inventoryPlayer = new ZInventoryPlayer(this.plugin);
        inventoryPlayer.storeInventory(player);
        this.inventories.put(player.getUniqueId(), inventoryPlayer);
    }

    @Override
    public void storeInventoryTemporary(@NonNull Player player) {
        if (this.hasSavedInventory(player.getUniqueId())) {
            return;
        }

        ZInventoryPlayer inventoryPlayer = new ZInventoryPlayer(this.plugin);
        inventoryPlayer.storeInventory(player, true);
        this.inventories.put(player.getUniqueId(), inventoryPlayer);
    }

    @Override
    public void storeInventoryTemporaryOrClear(@NotNull Player player) {
        Optional<InventoryPlayer> playerInventory = this.getPlayerInventory(player.getUniqueId());
        if (playerInventory.isPresent()) {
            playerInventory.get().clearInventory(player);
        } else {
            storeInventoryTemporary(player);
        }
    }

    private void restoreInventory(Player player, BiConsumer<InventoryPlayer, Player> restoreAction) {
        Optional<InventoryPlayer> optional = this.getPlayerInventory(player.getUniqueId());
        if (optional.isPresent()) {
            InventoryPlayer inventoryPlayer = optional.get();
            restoreAction.accept(inventoryPlayer, player);
            this.inventories.remove(player.getUniqueId());
            this.plugin.getStorageManager().removeInventory(player.getUniqueId());
        }
    }

    @Override
    public void giveInventory(@NonNull Player player) {
        this.restoreInventory(player, InventoryPlayer::giveInventory);
    }

    @Override
    public void forceGiveInventory(@NonNull Player player) {
        if (ZInventory.restorePlayerInventoryBeforeExternalSave(this.plugin, player, "external-force-restore")) {
            return;
        }
        this.forceGiveInventoryDirect(player);
    }

    @Override
    public void forceGiveInventoryDirect(@NonNull Player player) {
        this.restoreInventory(player, InventoryPlayer::forceGiveInventory);
    }

    @Override
    public boolean hasSavedInventory(@NonNull UUID uniqueId) {
        return this.inventories.containsKey(uniqueId);
    }

    @Override
    public @NonNull Optional<InventoryPlayer> getPlayerInventory(@NonNull UUID uniqueId) {
        return Optional.ofNullable(this.inventories.getOrDefault(uniqueId, null));
    }

    @Override
    public @NonNull Optional<InventoryPlayer> getQuarantinedInventory(@NonNull UUID uniqueId) {
        return Optional.ofNullable(this.quarantinedInventories.get(uniqueId));
    }

    @Override
    public int quarantinedInventoryCount() {
        return this.quarantinedInventories.size();
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
        this.inventories.remove(uniqueId);
        this.plugin.getStorageManager().removeInventory(uniqueId);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDisconnect(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (ZInventory.restorePlayerInventoryBeforeExternalSave(this.plugin, player, "player-quit")) {
            return;
        }
        Optional<InventoryPlayer> playerInventory = this.getPlayerInventory(player.getUniqueId());
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
        if (playerInventory.isPresent()) {
            if (playerInventory.get().isPermanent()) {
                ZInventory.restorePlayerInventoryBeforeExternalSave(this.plugin, player, "player-join");
            }
        }
    }

    @Override
    public void loadInventories() {
        this.quarantinedInventories.clear();
        Map<UUID, List<InventoryDTO>> recoveries = new HashMap<>();
        for (InventoryDTO inventory : this.plugin.getStorageManager().loadInventories()) {
            if (inventory == null || inventory.player_id() == null || inventory.inventory() == null) {
                this.plugin.getLogger().severe("Ignored malformed zMenu inventory recovery row");
                continue;
            }
            recoveries.computeIfAbsent(inventory.player_id(), ignored -> new ArrayList<>()).add(inventory);
        }

        Map<UUID, ZInventoryPlayer> loadedInventories = new HashMap<>();
        recoveries.forEach((uuid, rows) -> {
            long distinctPayloads = rows.stream().map(InventoryDTO::inventory).distinct().count();
            if (distinctPayloads > 1) {
                this.plugin.getLogger().severe("Quarantined conflicting zMenu inventory recovery rows for "
                    + uuid + " count=" + rows.size());
                return;
            }
            this.decodeRecovery(uuid, rows.getFirst().inventory()).ifPresent(inventoryPlayer ->
                loadedInventories.put(uuid, inventoryPlayer));
        });
        this.quarantinedInventories.putAll(loadedInventories);
        if (!loadedInventories.isEmpty()) {
            this.plugin.getLogger().severe("Quarantined " + loadedInventories.size()
                + " legacy zMenu inventory recoveries; automatic restoration is disabled because "
                + "legacy rows have no server/session fence. Back up and reconcile them explicitly.");
        }
    }

    private Optional<ZInventoryPlayer> decodeRecovery(UUID uuid, String payload) {
        ZInventoryPlayer inventoryPlayer = new ZInventoryPlayer(this.plugin);
        if (payload.isEmpty()) {
            return Optional.of(inventoryPlayer);
        }

        try {
            for (String serializedItem : payload.split(";")) {
                String[] parts = serializedItem.split(":", 2);
                if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                    throw new IllegalArgumentException("invalid entry");
                }
                int slot = Integer.parseInt(parts[0]);
                if ((slot < 0 || slot >= 36) && slot != 40) {
                    throw new IllegalArgumentException("invalid slot");
                }
                if (inventoryPlayer.getItems().putIfAbsent(slot, parts[1]) != null) {
                    throw new IllegalArgumentException("duplicate slot");
                }
                if (ItemStackUtils.deserializeItemStack(parts[1]) == null) {
                    throw new IllegalArgumentException("invalid item");
                }
            }
            return Optional.of(inventoryPlayer);
        } catch (RuntimeException error) {
            this.plugin.getLogger().severe("Quarantined invalid zMenu inventory recovery row for " + uuid
                + ": " + error.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void restoreAllInventories() {
        new HashMap<>(this.inventories).forEach((uuid, inventoryPlayer) -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (!ZInventory.restorePlayerInventoryBeforeExternalSave(this.plugin, player, "plugin-disable")) {
                    this.forceGiveInventory(player);
                }
            } else {
                this.inventories.remove(uuid);
            }
        });
    }
}
