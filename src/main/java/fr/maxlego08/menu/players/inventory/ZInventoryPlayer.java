package fr.maxlego08.menu.players.inventory;

import fr.maxlego08.menu.ZMenuPlugin;
import fr.maxlego08.menu.api.players.inventory.InventoryPlayer;
import fr.maxlego08.menu.api.utils.ClearInvType;
import fr.maxlego08.menu.common.utils.nms.ItemStackUtils;
import fr.maxlego08.menu.common.utils.nms.NMSUtils;
import fr.maxlego08.menu.zcore.logger.Logger;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public class ZInventoryPlayer implements InventoryPlayer {
    private static final int MAX_INVENTORY_SIZE = 36;
    private static final int OFF_HAND_SLOT = 40;

    private final Map<Integer, String> items = new HashMap<>();
    private final ZMenuPlugin plugin;
    private boolean temporary = false;

    public ZInventoryPlayer(ZMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void storeInventory(@NonNull Player player) {
        storeInventory(player, false);
    }

    public void storeInventory(@NonNull Player player, boolean temporary) {
        this.temporary = temporary;
        PlayerInventory playerInventory = player.getInventory();
        ItemStack[] content = playerInventory.getContents();
        log("SNAPSHOT store start player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " temporary=" + temporary
            + " contentLength=" + content.length);
        for (int slot = 0; slot != MAX_INVENTORY_SIZE; slot++) {
            clear(slot, playerInventory, content,!temporary, player);
        }
        if (!NMSUtils.isOneHand()) {
            clear(OFF_HAND_SLOT, playerInventory, content,!temporary, player);
        }
        log("SNAPSHOT store end player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " temporary=" + temporary
            + " savedSlots=" + new TreeSet<>(items.keySet()));
    }

    private void clear(int slot, PlayerInventory playerInventory, ItemStack[] content, boolean removeItem, Player player) {
        ItemStack itemStack = content[slot];
        if (itemStack != null) {
            items.put(slot, ItemStackUtils.serializeItemStack(itemStack));
        }
        ClearInvType clearInvType = removeItem ? ClearInvType.DEFAULT : ClearInvType.PACKET_EVENT;
        log("SNAPSHOT clear slot player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " slot=" + slot
            + " before=" + itemDescription(itemStack)
            + " stored=" + (itemStack != null)
            + " removeItem=" + removeItem
            + " clearInvType=" + clearInvType);
        clearInvType.getRemoveItem().accept(player, slot, playerInventory);
        log("SNAPSHOT clear slot done player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " slot=" + slot
            + " after=" + itemDescription(playerInventory.getItem(slot))
            + " clearInvType=" + clearInvType);
    }

    @Override
    public void giveInventory(@NonNull Player player) {
        PlayerInventory playerInventory = player.getInventory();
        log("RESTORE giveInventory start player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " savedSlots=" + new TreeSet<>(items.keySet()));
        items.forEach((slot, encodedItemStack) -> {
            ItemStack before = playerInventory.getItem(slot);
            ItemStack restored = ItemStackUtils.deserializeItemStack(encodedItemStack);
            log("RESTORE give slot player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " slot=" + slot
                + " before=" + itemDescription(before)
                + " restored=" + itemDescription(restored));
            playerInventory.setItem(slot, restored);
            log("RESTORE give slot done player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " slot=" + slot
                + " after=" + itemDescription(playerInventory.getItem(slot)));
        });
        log("RESTORE giveInventory end player=" + player.getName()
            + " uuid=" + player.getUniqueId());
    }

    @Override
    public void forceGiveInventory(@NonNull Player player) {
        PlayerInventory playerInventory = player.getInventory();
        log("RESTORE forceGiveInventory start player=" + player.getName()
            + " uuid=" + player.getUniqueId()
            + " savedSlots=" + new TreeSet<>(items.keySet()));
        for (int slot = 0; slot != MAX_INVENTORY_SIZE; slot++) {
            forceGiveSlot(player, playerInventory, slot);
        }
        if (!NMSUtils.isOneHand()) {
            forceGiveSlot(player, playerInventory, OFF_HAND_SLOT);
        }
        log("RESTORE forceGiveInventory end player=" + player.getName()
            + " uuid=" + player.getUniqueId());
    }

    private void forceGiveSlot(Player player, PlayerInventory playerInventory, int slot) {
        if (items.containsKey(slot)) {
            ItemStack before = playerInventory.getItem(slot);
            ItemStack restored = ItemStackUtils.deserializeItemStack(items.get(slot));
            log("RESTORE force slot player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " slot=" + slot
                + " before=" + itemDescription(before)
                + " restored=" + itemDescription(restored));
            playerInventory.setItem(slot, restored);
            log("RESTORE force slot done player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " slot=" + slot
                + " after=" + itemDescription(playerInventory.getItem(slot)));
            return;
        }
        ItemStack itemStack = playerInventory.getItem(slot);
        if (itemStack != null && this.plugin.getDupeManager().isDupeItem(itemStack)) {
            log("RESTORE force clearing dupe placeholder player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " slot=" + slot
                + " before=" + itemDescription(itemStack));
            playerInventory.setItem(slot, null);
        } else {
            log("RESTORE force slot no saved item player=" + player.getName()
                + " uuid=" + player.getUniqueId()
                + " slot=" + slot
                + " current=" + itemDescription(itemStack));
        }
    }

    @Override
    public void setItems(@NonNull Map<Integer, ItemStack> items) {
        Map<Integer, String> encodedItems = new HashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            encodedItems.put(entry.getKey(), ItemStackUtils.serializeItemStack(entry.getValue()));
        }
        setItemsFromEncode(encodedItems);
    }

    @Override
    public void setItemsFromEncode(@NonNull Map<Integer, String> items) {
        this.items.clear();
        this.items.putAll(items);
        log("SNAPSHOT setItemsFromEncode slots=" + new TreeSet<>(this.items.keySet()));
    }

    @Override
    public void setItems(@NonNull List<ItemStack> items) {
        this.items.clear();
        for (int slot = 0; slot != Math.min(items.size(), MAX_INVENTORY_SIZE); slot++) {
            ItemStack itemStack = items.get(slot);
            if (itemStack != null) {
                this.items.put(slot, ItemStackUtils.serializeItemStack(itemStack));
            }
        }
        log("SNAPSHOT setItems list slots=" + new TreeSet<>(this.items.keySet()));
    }

    @Override
    public @NonNull String toInventoryString() {
        StringBuilder builder = new StringBuilder();
        this.items.forEach((slot, itemStack) -> builder.append(slot).append(":").append(itemStack).append(";"));
        String result = builder.toString();
        return result.isEmpty() ? result : result.substring(0, result.length() - 1);
    }

    @Override
    public @NonNull List<ItemStack> getItemStacks() {
        List<ItemStack> deserialized = new ArrayList<>(this.items.size());
        for (String encoded : this.items.values()) {
            deserialized.add(ItemStackUtils.deserializeItemStack(encoded));
        }
        return deserialized;
    }

    @Override
    public @NonNull Map<Integer, String> getItems() {
        return this.items;
    }

    @Override
    public boolean isPermanent() {
        return !this.temporary;
    }

    private void log(String message) {
        Logger.info("[PlayerInvDebug] " + message, Logger.LogType.WARNING);
    }

    private String itemDescription(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "AIR";
        }
        return itemStack.getType().name() + "x" + itemStack.getAmount();
    }
}
