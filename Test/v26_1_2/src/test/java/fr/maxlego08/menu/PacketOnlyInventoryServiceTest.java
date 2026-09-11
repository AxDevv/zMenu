package fr.maxlego08.menu;

import fr.maxlego08.menu.players.inventory.ZInventoriesPlayer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PacketOnlyInventoryServiceTest {
    @Test
    void closingAndExternalCaptureNeverRestoreOldItemsOrWriteStorage() {
        // A null plugin makes any access to its persistence services fail immediately.
        var inventories = new ZInventoriesPlayer(null);
        var player = mock(Player.class);
        var uuid = UUID.randomUUID();
        inventories.forceGiveInventory(player);
        inventories.giveInventory(player);
        inventories.clearInventorie(uuid);
        inventories.restoreAllInventories();
        assertFalse(inventories.hasSavedInventory(uuid));
        assertTrue(inventories.getPlayerInventory(uuid).isEmpty());
        assertTrue(inventories.getInventory(uuid).isEmpty());
        verify(player, times(2)).updateInventory();
        verifyNoMoreInteractions(player);
    }

    @Test
    void physicalStorageIsRejectedBeforeReadingOrChangingThePlayer() {
        var inventories = new ZInventoriesPlayer(null);
        var player = mock(Player.class);
        assertThrows(UnsupportedOperationException.class, () -> inventories.storeInventory(player));
        verifyNoInteractions(player);
    }
}
