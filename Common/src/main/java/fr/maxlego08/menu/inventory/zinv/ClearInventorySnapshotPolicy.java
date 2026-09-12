package fr.maxlego08.menu.inventory.zinv;

import fr.maxlego08.menu.api.utils.ClearInvType;

final class ClearInventorySnapshotPolicy {

    private ClearInventorySnapshotPolicy() {
    }

    static boolean usesSavedInventory(boolean clearInventory, ClearInvType clearInvType) {
        return clearInventory && clearInvType == ClearInvType.DEFAULT;
    }
}
