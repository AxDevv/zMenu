package fr.maxlego08.menu.hooks.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import fr.maxlego08.menu.api.InventoryListener;
import fr.maxlego08.menu.api.MenuPlugin;
import fr.maxlego08.menu.api.engine.BaseInventory;
import fr.maxlego08.menu.api.engine.ItemButton;
import fr.maxlego08.menu.api.inventory.ContainerInventory;
import fr.maxlego08.menu.api.utils.ClearInvType;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PacketEventPlayerInventoryManager implements InventoryListener, Listener {
    private final MenuPlugin plugin;
    private final Map<UUID, PlayerInventoryProjection> players = new ConcurrentHashMap<>();
    private final PlayerInventoryProjectionListener packetListener = new PlayerInventoryProjectionListener(players);

    public PacketEventPlayerInventoryManager(MenuPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getOnlinePlayers().forEach(player ->
            players.put(player.getUniqueId(), new PlayerInventoryProjection()));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        PacketEvents.getAPI().getEventManager().registerListener(packetListener);
        ClearInvType.PACKET_EVENT.setOnButtonClear((player, slot) -> {
            // A replacement view is installed before the next container packet.
        });
        ClearInvType.PACKET_EVENT.setRemoveItem((player, slot, inventory) -> {
            var tracker = players.get(player.getUniqueId());
            if (tracker != null) tracker.putPrepared(slot, ItemStack.EMPTY);
        });
        ClearInvType.PACKET_EVENT.setOnInventoryClose((inventories, player) -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof BaseInventory)) {
                player.updateInventory();
            }
        });
    }

    @Override
    public void onInventoryPreOpen(Player player, BaseInventory inventory, int page, Object... objects) {
        var tracker = players.get(player.getUniqueId());
        if (tracker == null || objects.length == 0 || !(objects[0] instanceof ContainerInventory menu)) return;
        tracker.prepare(inventory, menu.clearInventory(), menu.size());
    }

    @Override
    public boolean addItem(BaseInventory inventory, boolean inPlayerInventory, ItemButton button, boolean antiDupe) {
        if (!inPlayerInventory || inventory.getClearInvType() != ClearInvType.PACKET_EVENT) return false;
        Player player = inventory.getPlayer();
        if (player == null) return true;
        var tracker = players.get(player.getUniqueId());
        if (tracker != null) {
            tracker.put(inventory, button.getSlot(), SpigotConversionUtil.fromBukkitItemStack(button.getDisplayItem()));
        }
        // Never inject a menu button into the physical player inventory.
        return true;
    }

    @Override
    public void onInventoryPostOpen(Player player, BaseInventory inventory) {
        var tracker = players.get(player.getUniqueId());
        var actual = player.getOpenInventory().getTopInventory().getHolder();
        if (actual != inventory) {
            if (tracker != null) tracker.abort(inventory, actual);
            player.updateInventory();
            return;
        }
        if (tracker != null) tracker.opened(inventory);
        sendProjection(player, inventory);
    }

    @Override
    public void onButtonClick(Player player, ItemButton button) {
        BaseInventory owner = button.getBaseInventory();
        if (owner.getClearInvType() != ClearInvType.PACKET_EVENT) return;
        plugin.getScheduler().runAtEntityLater(player, () -> {
            if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == owner) {
                sendProjection(player, owner);
            }
        }, 1);
    }

    private void sendProjection(Player player, BaseInventory owner) {
        var tracker = players.get(player.getUniqueId());
        var view = tracker == null ? null : tracker.view();
        if (view == null || view.owner() != owner) return;
        for (int slot = 0; slot < 36; slot++) sendSlot(player, slot, view.item(slot));
        sendSlot(player, 40, view.item(40));
    }

    private void sendSlot(Player player, int slot, ItemStack item) {
        if (item != null) {
            PacketEvents.getAPI().getPlayerManager().sendPacket(player,
                new WrapperPlayServerSetPlayerInventory(slot, item));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        var tracker = players.get(player.getUniqueId());
        if (tracker == null || !tracker.close(event.getInventory().getHolder())) return;
        plugin.getScheduler().runAtEntityLater(player, () -> {
            if (player.isOnline() && tracker.view() == null) player.updateInventory();
        }, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        players.put(event.getPlayer().getUniqueId(), new PlayerInventoryProjection());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        players.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        PacketEvents.getAPI().getEventManager().unregisterListener(packetListener);
        players.clear();
        HandlerList.unregisterAll(this);
        plugin.getServer().getOnlinePlayers().forEach(Player::updateInventory);
        ClearInvType.PACKET_EVENT.setOnButtonClear((player, slot) -> {});
        ClearInvType.PACKET_EVENT.setRemoveItem((player, slot, inventory) -> {});
        ClearInvType.PACKET_EVENT.setOnInventoryClose((inventories, player) -> {});
    }
}
