package fr.maxlego08.menu.hooks.packetevents;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerOpenWindow;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerWindowItems;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;

final class PlayerInventoryProjectionListener extends PacketListenerAbstract {
    private final Map<UUID, PlayerInventoryProjection> players;

    PlayerInventoryProjectionListener(Map<UUID, PlayerInventoryProjection> players) {
        super(PacketListenerPriority.HIGHEST);
        this.players = players;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        var type = event.getPacketType();
        if (type != PacketType.Play.Server.OPEN_WINDOW && type != PacketType.Play.Server.WINDOW_ITEMS
            && type != PacketType.Play.Server.SET_SLOT && type != PacketType.Play.Server.SET_PLAYER_INVENTORY) return;
        UUID uuid = event.getUser().getUUID();
        if (uuid == null || event.isCancelled()) return;
        PlayerInventoryProjection tracker = players.get(uuid);
        if (tracker == null) return;
        if (type == PacketType.Play.Server.OPEN_WINDOW) {
            tracker.windowId(new WrapperPlayServerOpenWindow(event).getContainerId());
            return;
        }
        var view = tracker.view();
        if (view == null) return;
        if (type == PacketType.Play.Server.WINDOW_ITEMS) {
            var packet = new WrapperPlayServerWindowItems(event);
            int window = packet.getWindowId();
            if (window != 0 && window != tracker.windowId()) return;
            var items = new ArrayList<>(packet.getItems());
            int topSize = window == 0 ? 0 : items.size() - 36;
            for (int raw = 0; raw < items.size(); raw++) {
                var item = view.item(PlayerInventoryProjection.playerSlot(window, raw, topSize));
                if (item != null) items.set(raw, item);
            }
            packet.setItems(items);
            event.markForReEncode(true);
        } else if (type == PacketType.Play.Server.SET_SLOT) {
            var packet = new WrapperPlayServerSetSlot(event);
            int window = packet.getWindowId();
            if (window > 0 && window != tracker.windowId()) return;
            var item = view.item(PlayerInventoryProjection.playerSlot(window, packet.getSlot(), view.topSize()));
            if (item != null) {
                packet.setItem(item);
                event.markForReEncode(true);
            }
        } else if (type == PacketType.Play.Server.SET_PLAYER_INVENTORY) {
            var packet = new WrapperPlayServerSetPlayerInventory(event);
            var item = view.item(packet.getSlot());
            if (item != null) {
                packet.setStack(item);
                event.markForReEncode(true);
            }
        }
    }
}
