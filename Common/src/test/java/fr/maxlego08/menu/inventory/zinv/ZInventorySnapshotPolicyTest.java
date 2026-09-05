package fr.maxlego08.menu.inventory.zinv;

import fr.maxlego08.menu.api.utils.ClearInvType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZInventorySnapshotPolicyTest {

    @Test
    void onlyStoredClearInventoriesUseSavedInventoryState() {
        assertTrue(ClearInventorySnapshotPolicy.usesSavedInventory(true, ClearInvType.DEFAULT));
        assertFalse(ClearInventorySnapshotPolicy.usesSavedInventory(true, ClearInvType.PACKET_EVENT));
        assertFalse(ClearInventorySnapshotPolicy.usesSavedInventory(false, ClearInvType.DEFAULT));
    }
}
