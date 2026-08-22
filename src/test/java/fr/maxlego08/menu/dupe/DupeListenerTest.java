package fr.maxlego08.menu.dupe;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DupeListenerTest {

    @Test
    void protectsEveryDupeTransferPath() {
        assertTrue(DupeListener.touchesDupeItem(ClickType.LEFT, false, true, false, false));
        assertTrue(DupeListener.touchesDupeItem(ClickType.NUMBER_KEY, true, false, false, false));
        assertTrue(DupeListener.touchesDupeItem(ClickType.NUMBER_KEY, false, false, true, false));
        assertTrue(DupeListener.touchesDupeItem(ClickType.SWAP_OFFHAND, true, false, false, false));
        assertTrue(DupeListener.touchesDupeItem(ClickType.SWAP_OFFHAND, false, false, false, true));
        assertFalse(DupeListener.touchesDupeItem(ClickType.LEFT, true, false, false, false));
        assertFalse(DupeListener.touchesDupeItem(ClickType.LEFT, false, false, true, true));
    }

    @Test
    void protectsEitherSideOfDirectHandSwap() {
        assertTrue(DupeListener.protectsHandSwap(true, false));
        assertTrue(DupeListener.protectsHandSwap(false, true));
        assertFalse(DupeListener.protectsHandSwap(false, false));
    }

    @Test
    void runsTransferGuardsAtHighestPriority() throws Exception {
        assertEquals(EventPriority.HIGHEST,
            eventHandler("onInventoryClick", InventoryClickEvent.class).priority());
        assertEquals(EventPriority.HIGHEST,
            eventHandler("onSwapHand", PlayerSwapHandItemsEvent.class).priority());
    }

    private EventHandler eventHandler(String name, Class<?> eventType) throws Exception {
        Method method = DupeListener.class.getDeclaredMethod(name, eventType);
        return method.getAnnotation(EventHandler.class);
    }
}
