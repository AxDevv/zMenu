package fr.maxlego08.menu.inventory;

final class InventoryClickPolicy {

    private InventoryClickPolicy() {
    }

    static boolean shouldCancel(boolean alreadyCancelled, boolean inventoryDisablesClick) {
        return alreadyCancelled || inventoryDisablesClick;
    }
}
