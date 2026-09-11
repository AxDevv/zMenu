package fr.maxlego08.menu.hooks.packetevents;

import com.github.retrooper.packetevents.protocol.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Immutable packet views published by the server thread and read by Netty. */
final class PlayerInventoryProjection {
    private Object preparingOwner;
    private boolean preparingHidden;
    private int preparingSize;
    private final Map<Integer, ItemStack> preparingItems = new HashMap<>();
    private volatile View view;
    private View previousView;
    private volatile int windowId;

    void prepare(Object owner, boolean hidden, int size) {
        previousView = view;
        preparingOwner = owner;
        preparingHidden = hidden;
        preparingSize = size;
        preparingItems.clear();
    }

    void put(Object owner, int slot, ItemStack item) {
        if (owner == preparingOwner) {
            preparingItems.put(slot, item.copy());
            view = new View(owner, preparingHidden, preparingSize, Map.copyOf(preparingItems));
        } else {
            View current = view;
            if (current != null && current.owner() == owner) {
                Map<Integer, ItemStack> updated = new HashMap<>(current.items());
                updated.put(slot, item.copy());
                view = new View(owner, current.hidden(), current.topSize(), Map.copyOf(updated));
            }
        }
    }

    void putPrepared(int slot, ItemStack item) {
        if (preparingOwner != null) put(preparingOwner, slot, item);
    }

    View view() {
        return view;
    }

    void abort(Object owner, Object actualOwner) {
        if (preparingOwner != owner) return;
        view = previousView != null && previousView.owner() == actualOwner ? previousView : null;
        preparingOwner = null;
        preparingItems.clear();
        previousView = null;
    }

    void opened(Object owner) {
        if (preparingOwner == owner) previousView = null;
    }

    boolean close(Object owner) {
        if (preparingOwner == owner) {
            preparingOwner = null;
            preparingItems.clear();
        }
        View current = view;
        if (current == null || current.owner() != owner) return false;
        view = null;
        return true;
    }

    void windowId(int value) {
        windowId = value;
    }

    int windowId() {
        return windowId;
    }

    record View(Object owner, boolean hidden, int topSize, Map<Integer, ItemStack> items) {
        ItemStack item(int slot) {
            ItemStack projected = items.get(slot);
            if (projected != null) return projected.copy();
            return hidden && (slot >= 0 && slot < 36 || slot == 40) ? ItemStack.EMPTY : null;
        }
    }

    static int playerSlot(int window, int rawSlot, int topSize) {
        if (window == -2) return rawSlot;
        if (window == 0) {
            if (rawSlot >= 9 && rawSlot < 36) return rawSlot;
            if (rawSlot >= 36 && rawSlot < 45) return rawSlot - 36;
            return rawSlot == 45 ? 40 : -1;
        }
        if (window < 0) return -1;
        int bottom = rawSlot - topSize;
        if (bottom >= 0 && bottom < 27) return bottom + 9;
        return bottom >= 27 && bottom < 36 ? bottom - 27 : -1;
    }
}
