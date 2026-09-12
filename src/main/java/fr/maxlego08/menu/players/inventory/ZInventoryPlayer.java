package fr.maxlego08.menu.players.inventory;

import fr.maxlego08.menu.ZMenuPlugin;
import fr.maxlego08.menu.api.players.inventory.InventoryPlayer;
import fr.maxlego08.menu.api.utils.ClearInvType;
import fr.maxlego08.menu.common.utils.nms.ItemStackUtils;
import fr.maxlego08.menu.common.utils.nms.NMSUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ZInventoryPlayer implements InventoryPlayer {
    private static final int STORAGE_SIZE = 36;
    private static final int OFF_HAND_SLOT = 40;
    private final Map<Integer, String> items = new HashMap<>();
    private final ZMenuPlugin plugin;
    private boolean temporary = false;

    public ZInventoryPlayer(ZMenuPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void storeInventory(@NonNull Player player) {
        this.storeInventory(player, false);
    }

    public void storeInventory(@NonNull Player player, boolean temporary) {
        this.temporary = temporary;
        PlayerInventory playerInventory = player.getInventory();
        ItemStack[] content = playerInventory.getContents();
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            this.capture(slot, content);
            if (temporary) {
                ClearInvType.PACKET_EVENT.getRemoveItem().accept(player, slot, playerInventory);
            }
        }
        if (!NMSUtils.isOneHand()) {
            this.capture(OFF_HAND_SLOT, content);
            if (temporary) {
                ClearInvType.PACKET_EVENT.getRemoveItem().accept(player, OFF_HAND_SLOT, playerInventory);
            }
        }
    }

    @Override
    public void clearInventory(@NonNull Player player) {
        ClearInvType clearInvType = ClearInvType.PACKET_EVENT;
        var removeItem = clearInvType.getRemoveItem();

        PlayerInventory playerInventory = player.getInventory();
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            removeItem.accept(player, slot, playerInventory);
        }
        if (!NMSUtils.isOneHand()) {
            removeItem.accept(player, OFF_HAND_SLOT, playerInventory);
        }
    }

    private void capture(int slot, ItemStack[] content) {
        ItemStack itemStack = content[slot];
        if (itemStack != null) {
            this.items.put(slot, ItemStackUtils.serializeItemStack(itemStack));
        }
    }

    @Override
    public void giveInventory(@NonNull Player player) {
        PlayerInventory playerInventory = player.getInventory();
        this.items.forEach((slot, encodedItemStack) -> playerInventory.setItem(slot, ItemStackUtils.deserializeItemStack(encodedItemStack)));
    }

    @Override
    public void forceGiveInventory(@NonNull Player player) {
        PlayerInventory playerInventory = player.getInventory();
        for (int slot = 0; slot < STORAGE_SIZE; slot++) {
            this.restoreSlot(playerInventory, slot);
        }
        if (!NMSUtils.isOneHand()) {
            this.restoreSlot(playerInventory, OFF_HAND_SLOT);
        }
    }

    private void restoreSlot(PlayerInventory playerInventory, int slot) {
        if (this.items.containsKey(slot)) {
            playerInventory.setItem(slot, ItemStackUtils.deserializeItemStack(this.items.get(slot)));
            return;
        }
        ItemStack itemStack = playerInventory.getItem(slot);
        if (itemStack != null && this.plugin.getDupeManager().isDupeItem(itemStack)) {
            playerInventory.setItem(slot, null);
        }
    }

    @Override
    public void setItems(@NonNull Map<Integer, ItemStack> items) {
        Map<Integer, String> encodedItems = new HashMap<>();
        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            encodedItems.put(entry.getKey(), ItemStackUtils.serializeItemStack(entry.getValue()));
        }
        this.setItemsFromEncode(encodedItems);
    }

    @Override
    public void setItemsFromEncode(@NonNull Map<Integer, String> items) {
        this.items.clear();
        this.items.putAll(items);
    }

    @Override
    public void setItems(@NonNull List<ItemStack> items) {
        this.items.clear();
        for (int slot = 0; slot < Math.min(items.size(), STORAGE_SIZE); slot++) {
            ItemStack itemStack = items.get(slot);
            if (itemStack != null) {
                this.items.put(slot, ItemStackUtils.serializeItemStack(itemStack));
            }
        }
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
}
