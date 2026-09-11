package fr.maxlego08.menu.hooks.packetevents;

import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.manager.server.ServerManager;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PlayerInventoryProjectionTest {
    @BeforeAll
    static void initializePacketRegistry() {
        PacketEventsAPI<?> api = mock(PacketEventsAPI.class);
        ServerManager server = mock(ServerManager.class);
        when(server.getVersion()).thenReturn(ServerVersion.V_1_21_11);
        when(api.getServerManager()).thenReturn(server);
        when(api.getSettings()).thenReturn(new com.github.retrooper.packetevents.settings.PacketEventsSettings());
        when(api.getNettyManager()).thenReturn(new io.github.retrooper.packetevents.impl.netty.NettyManagerImpl());
        PacketEvents.setAPI(api);
    }

    @Test
    void nextMenuIsMaskedBeforePostOpenAndOldCloseCannotExposeItems() {
        var tracker = new PlayerInventoryProjection();
        Object first = new Object();
        Object second = new Object();
        tracker.prepare(first, true, 54);
        tracker.putPrepared(0, ItemStack.EMPTY);
        tracker.opened(first);
        tracker.prepare(second, true, 27);
        assertNotNull(tracker.view().item(15), "Previous menu stays masked during preparation");
        tracker.putPrepared(0, ItemStack.EMPTY);
        assertFalse(tracker.close(first));
        assertSame(second, tracker.view().owner());
        assertNotNull(tracker.view().item(35), "New container packet must already be masked");
        assertNotNull(tracker.view().item(40));
        assertNull(tracker.view().item(36), "Armor is not projected");
        assertTrue(tracker.close(second));
        assertNull(tracker.view(), "Close exposes current server items, never a saved snapshot");
    }

    @Test
    void deniedPreparationAndCancelledOpeningPreserveThePreviousMenu() {
        var tracker = new PlayerInventoryProjection();
        Object first = new Object();
        Object second = new Object();
        tracker.prepare(first, true, 54);
        tracker.putPrepared(0, ItemStack.EMPTY);
        tracker.opened(first);
        var original = tracker.view();
        tracker.prepare(second, true, 27);
        assertSame(original, tracker.view(), "A failed requirement must not change the view");
        tracker.putPrepared(0, ItemStack.EMPTY);
        tracker.abort(second, first);
        assertSame(original, tracker.view());
    }

    @Test
    void visibleMenuOnlyProjectsItsButtonsAndStaleUpdatesCannotReplaceIt() {
        var tracker = new PlayerInventoryProjection();
        Object first = new Object();
        Object second = new Object();
        tracker.prepare(first, true, 54);
        tracker.putPrepared(0, ItemStack.EMPTY);
        tracker.prepare(second, false, 27);
        tracker.put(second, 8, ItemStack.EMPTY);
        tracker.put(first, 4, ItemStack.EMPTY);
        assertNull(tracker.view().item(4));
        assertNotNull(tracker.view().item(8));
        assertNull(tracker.view().item(40));
    }

    @Test
    void mapsChestPlayerInventoryHotbarOffhandAndCursorWithoutTouchingArmor() {
        assertEquals(9, PlayerInventoryProjection.playerSlot(3, 54, 54));
        assertEquals(35, PlayerInventoryProjection.playerSlot(3, 80, 54));
        assertEquals(0, PlayerInventoryProjection.playerSlot(3, 81, 54));
        assertEquals(8, PlayerInventoryProjection.playerSlot(3, 89, 54));
        assertEquals(-1, PlayerInventoryProjection.playerSlot(3, 53, 54));
        assertEquals(-1, PlayerInventoryProjection.playerSlot(3, 90, 54));
        assertEquals(9, PlayerInventoryProjection.playerSlot(3, 3, 3));
        assertEquals(0, PlayerInventoryProjection.playerSlot(0, 36, 0));
        assertEquals(40, PlayerInventoryProjection.playerSlot(0, 45, 0));
        assertEquals(-1, PlayerInventoryProjection.playerSlot(0, 8, 0));
        assertEquals(-1, PlayerInventoryProjection.playerSlot(-1, -1, 54));
        assertEquals(0, PlayerInventoryProjection.playerSlot(-2, 0, 54));
    }

    @Test
    void actualContainerAndSlotPacketsNeverExposeRealItemsBetweenMenus() {
        var uuid = java.util.UUID.randomUUID();
        var tracker = new PlayerInventoryProjection();
        var listener = new PlayerInventoryProjectionListener(java.util.Map.of(uuid, tracker));
        Object first = new Object();
        Object second = new Object();
        tracker.prepare(first, true, 54);
        tracker.putPrepared(0, ItemStack.EMPTY);
        tracker.prepare(second, true, 27);
        tracker.putPrepared(0, ItemStack.EMPTY);
        assertFalse(tracker.close(first));
        var real = ItemStack.builder().type(com.github.retrooper.packetevents.protocol.item.type.ItemTypes.DIAMOND).amount(7).build();
        var button = ItemStack.builder().type(com.github.retrooper.packetevents.protocol.item.type.ItemTypes.PAPER).build();
        tracker.put(second, 0, button);
        var opened = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow(4, 2,
            net.kyori.adventure.text.Component.text("Next menu"));
        apply(listener, uuid, com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.OPEN_WINDOW, opened);
        var contents = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems(
            4, 8, java.util.Collections.nCopies(63, real), real);
        var masked = (com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems)
            apply(listener, uuid, com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.WINDOW_ITEMS, contents);
        assertEquals(real, masked.getItems().get(0), "Top inventory is untouched");
        assertTrue(masked.getItems().get(27).isEmpty());
        assertEquals(button, masked.getItems().get(54), "Hotbar button survives the initial contents packet");
        assertEquals(real, masked.getCarriedItem().orElseThrow(), "Cursor is not hidden");
        assertEquals(real, contents.getItems().get(27), "Source packet is not mutated");
        var slot = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot(4, 8, 28, real);
        var hiddenSlot = (com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot)
            apply(listener, uuid, com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.SET_SLOT, slot);
        assertTrue(hiddenSlot.getItem().isEmpty());
        var update = new com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory(15, real);
        var hiddenUpdate = (com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory)
            apply(listener, uuid, com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.SET_PLAYER_INVENTORY, update);
        assertTrue(hiddenUpdate.getStack().isEmpty());
        tracker.close(second);
        assertSame(update, apply(listener, uuid,
            com.github.retrooper.packetevents.protocol.packettype.PacketType.Play.Server.SET_PLAYER_INVENTORY, update));
    }

    private com.github.retrooper.packetevents.wrapper.PacketWrapper<?> apply(
        PlayerInventoryProjectionListener listener, java.util.UUID uuid,
        com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon type,
        com.github.retrooper.packetevents.wrapper.PacketWrapper<?> packet
    ) {
        var user = mock(com.github.retrooper.packetevents.protocol.player.User.class);
        when(user.getUUID()).thenReturn(uuid);
        when(user.getClientVersion()).thenReturn(com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_21_11);
        var event = mock(com.github.retrooper.packetevents.event.PacketSendEvent.class);
        when(event.getUser()).thenReturn(user);
        when(event.getServerVersion()).thenReturn(ServerVersion.V_1_21_11);
        when(event.getPacketType()).thenReturn(type);
        var latest = new java.util.concurrent.atomic.AtomicReference<com.github.retrooper.packetevents.wrapper.PacketWrapper<?>>(packet);
        when(event.getLastUsedWrapper()).thenAnswer(ignored -> latest.get());
        doAnswer(call -> { latest.set(call.getArgument(0)); return null; }).when(event).setLastUsedWrapper(any());
        listener.onPacketSend(event);
        return latest.get();
    }
}
