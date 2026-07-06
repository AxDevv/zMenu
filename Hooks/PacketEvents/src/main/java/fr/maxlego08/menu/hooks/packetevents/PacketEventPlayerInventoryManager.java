package fr.maxlego08.menu.hooks.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import fr.maxlego08.menu.api.InventoryListener;
import fr.maxlego08.menu.api.engine.BaseInventory;
import fr.maxlego08.menu.api.engine.ItemButton;
import fr.maxlego08.menu.api.players.inventory.InventoryPlayer;
import fr.maxlego08.menu.api.utils.ClearInvType;
import fr.maxlego08.menu.common.utils.nms.ItemStackUtils;
import fr.maxlego08.menu.zcore.logger.Logger;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class PacketEventPlayerInventoryManager implements InventoryListener {
    private final Map<UUID, Map<Integer,WrapperPlayServerSetPlayerInventory>> pendingInventoryUpdates = new HashMap<>();

    public PacketEventPlayerInventoryManager() {
        ClearInvType packetEvent = ClearInvType.PACKET_EVENT;
        packetEvent.setOnButtonClear((player, slot) -> {
            log("PACKET onButtonClear player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " slot=" + slot
                + " item=AIR");
            this.addItemInstantly(player, slot, new ItemStack(Material.AIR));
        });
        packetEvent.setOnInventoryClose(((inventoriesPlayer, player) -> {
            Optional<InventoryPlayer> playerInventory = inventoriesPlayer.getPlayerInventory(player.getUniqueId());
            log("PACKET onInventoryClose player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " hasSnapshot=" + playerInventory.isPresent()
                + " slots=" + playerInventory.map(value -> new java.util.TreeSet<>(value.getItems().keySet()).toString()).orElse("[]"));
            if (playerInventory.isPresent()) {
                Map<Integer, String> items = playerInventory.get().getItems();
                for (var entry : items.entrySet()) {
                    int slot = entry.getKey();
                    ItemStack itemStack = ItemStackUtils.deserializeItemStack(entry.getValue());
                    log("PACKET restore slot player=" + player.getName()
                        + " uuid=" + player.getUniqueId()
                        + " slot=" + slot
                        + " item=" + itemDescription(itemStack));
                    this.addItemInstantly(player, slot,itemStack);
                }
            }
            inventoriesPlayer.clearInventorie(player.getUniqueId());
            log("PACKET onInventoryClose cleared snapshot player=" + player.getName()
                + " uuid=" + player.getUniqueId());
        }));
        packetEvent.setRemoveItem((player, slot, playerInventory) -> {
            log("PACKET removeItem later player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " slot=" + slot
                + " item=AIR");
            this.addItemLater(player, slot, new ItemStack(Material.AIR));
        });
    }

    public boolean addItem(BaseInventory baseInventory, boolean inPlayerInventory, ItemButton itemButton, boolean enableAntiDupe){
        if (baseInventory.getClearInvType() == ClearInvType.PACKET_EVENT && inPlayerInventory && baseInventory.getPlayer() != null) {
            WrapperPlayServerSetPlayerInventory wrapperPlayServerSetPlayerInventory = new WrapperPlayServerSetPlayerInventory(itemButton.getSlot(), SpigotConversionUtil.fromBukkitItemStack(itemButton.getDisplayItem()));
            this.pendingInventoryUpdates.computeIfAbsent(baseInventory.getPlayer().getUniqueId(), k -> new HashMap<>()).put(itemButton.getSlot(), wrapperPlayServerSetPlayerInventory);
            log("PACKET queue player-inv button player=" + baseInventory.getPlayer().getName()
                + " uuid=" + baseInventory.getPlayer().getUniqueId()
                + " gui=" + baseInventory.getGuiName()
                + " slot=" + itemButton.getSlot()
                + " item=" + itemDescription(itemButton.getDisplayItem())
                + " pendingSlots=" + pendingSlots(baseInventory.getPlayer().getUniqueId()));
            return true;
        }
        return false;
    }

    public void onInventoryPreOpen(Player player, BaseInventory baseInventory, int page, Object... objects){
        log("PACKET preOpen clear pending player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " gui=" + baseInventory.getGuiName()
            + " page=" + page
            + " previousPendingSlots=" + pendingSlots(player.getUniqueId()));
        this.pendingInventoryUpdates.remove(player.getUniqueId());
    }

    public void onInventoryPostOpen(Player player, BaseInventory baseInventory){
        UUID playerUniqueId = player.getUniqueId();
        log("PACKET postOpen player=" + player.getName()
            + " uuid=" + playerUniqueId
            + " gui=" + baseInventory.getGuiName()
            + " pendingSlots=" + pendingSlots(playerUniqueId));
        if (this.pendingInventoryUpdates.containsKey(playerUniqueId)) {
            Map<Integer,WrapperPlayServerSetPlayerInventory> wrappers = this.pendingInventoryUpdates.get(playerUniqueId);
            for (WrapperPlayServerSetPlayerInventory wrapper : wrappers.values()) {
                log("PACKET send queued update player=" + player.getName()
                    + " uuid=" + playerUniqueId
                    + " gui=" + baseInventory.getGuiName()
                    + " wrapper=" + wrapper);
                PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapper);
            }
            this.pendingInventoryUpdates.remove(playerUniqueId);
            log("PACKET postOpen pending cleared player=" + player.getName()
                + " uuid=" + playerUniqueId);
        }
    }

    public void onButtonClick(Player player, ItemButton button){
        if (button.isInPlayerInventory() && button.getBaseInventory().getClearInvType() == ClearInvType.PACKET_EVENT) {
            log("PACKET button click resync player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " slot=" + button.getSlot()
                + " item=" + itemDescription(button.getDisplayItem()));
            this.addItemInstantly(player, button.getSlot(), button.getDisplayItem());
        }
    }

    public void addItemInstantly(@NotNull Player player, int slot, @NotNull ItemStack itemStack){
        log("PACKET send instant player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " slot=" + slot
            + " item=" + itemDescription(itemStack));
        WrapperPlayServerSetPlayerInventory wrapper = new WrapperPlayServerSetPlayerInventory(slot, itemStack.getType() == Material.AIR ? null : SpigotConversionUtil.fromBukkitItemStack(itemStack));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapper);
    }

    public void addItemLater(@NotNull Player player, int slot, @NotNull ItemStack itemStack){
        this.pendingInventoryUpdates.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(slot, new WrapperPlayServerSetPlayerInventory(slot, itemStack.getType() == Material.AIR ? null : SpigotConversionUtil.fromBukkitItemStack(itemStack)));
        log("PACKET queue later player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " slot=" + slot
            + " item=" + itemDescription(itemStack)
            + " pendingSlots=" + pendingSlots(player.getUniqueId()));
    }

    private void log(String message) {
        Logger.info("[PlayerInvDebug] " + message, Logger.LogType.WARNING);
    }

    private String pendingSlots(UUID playerUniqueId) {
        Map<Integer, WrapperPlayServerSetPlayerInventory> updates = pendingInventoryUpdates.get(playerUniqueId);
        return updates == null ? "[]" : new java.util.TreeSet<>(updates.keySet()).toString();
    }

    private String itemDescription(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "AIR";
        }
        return itemStack.getType().name() + "x" + itemStack.getAmount();
    }
}
