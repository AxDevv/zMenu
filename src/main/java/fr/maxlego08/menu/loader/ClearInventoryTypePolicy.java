package fr.maxlego08.menu.loader;

import java.util.Locale;

final class ClearInventoryTypePolicy {

    private ClearInventoryTypePolicy() {
    }

    static String resolve(String configuredType, boolean clearInventory, boolean packetEventsEnabled) {
        String normalizedType = configuredType.toUpperCase(Locale.ROOT);
        if (clearInventory && packetEventsEnabled) {
            return "PACKET_EVENT";
        }
        if ("PACKET_EVENT".equals(normalizedType) && !packetEventsEnabled) {
            return "DEFAULT";
        }
        return normalizedType;
    }
}
