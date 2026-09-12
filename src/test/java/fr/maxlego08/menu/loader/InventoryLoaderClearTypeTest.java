package fr.maxlego08.menu.loader;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InventoryLoaderClearTypeTest {

    @Test
    void physicalClearIsUpgradedToPacketProjectionWhenAvailable() {
        assertEquals(
            "PACKET_EVENT",
            ClearInventoryTypePolicy.resolve("DEFAULT", true, true)
        );
    }

    @Test
    void missingPacketEventsFallsBackToFailClosedPhysicalType() {
        assertEquals(
            "DEFAULT",
            ClearInventoryTypePolicy.resolve("PACKET_EVENT", true, false)
        );
    }

    @Test
    void nonClearingMenuKeepsItsConfiguredType() {
        assertEquals(
            "DEFAULT",
            ClearInventoryTypePolicy.resolve("DEFAULT", false, true)
        );
    }
}
