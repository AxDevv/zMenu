package fr.maxlego08.menu.inventory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VInventoryManagerTest {

    @Test
    void neverUncancelsAnotherPluginsProtection() {
        assertTrue(InventoryClickPolicy.shouldCancel(true, false));
        assertTrue(InventoryClickPolicy.shouldCancel(true, true));
        assertTrue(InventoryClickPolicy.shouldCancel(false, true));
        assertFalse(InventoryClickPolicy.shouldCancel(false, false));
    }
}
