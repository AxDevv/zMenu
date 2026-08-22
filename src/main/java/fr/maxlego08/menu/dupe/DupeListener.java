package fr.maxlego08.menu.dupe;

import fr.maxlego08.menu.api.dupe.DupeItem;
import fr.maxlego08.menu.api.dupe.DupeManager;
import fr.maxlego08.menu.api.MenuPlugin;
import fr.maxlego08.menu.api.utils.CompatibilityUtil;
import fr.maxlego08.menu.inventory.VInventory;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DupeListener implements Listener {

    private final MenuPlugin plugin;
    private final DupeManager dupeManager;
    private final Set<UUID> pendingResyncs = ConcurrentHashMap.newKeySet();

    public DupeListener(MenuPlugin plugin, DupeManager dupeManager) {
        this.plugin = plugin;
        this.dupeManager = dupeManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {

        if (event.isCancelled()) return;

        try {
            ItemStack itemStack = event.getItem();
            if (itemStack != null && this.dupeManager.isDupeItem(itemStack)) {
                event.setCancelled(true);
                event.getPlayer().getInventory().setItem(event.getHand(), new ItemStack(Material.AIR));
                this.sendInformation(new DupeItem(itemStack, event.getPlayer()));
            }
        } catch (Exception exception) {
            ItemStack itemStack = event.getPlayer().getItemInHand();
            if (this.dupeManager.isDupeItem(itemStack)) {
                event.setCancelled(true);
                event.getPlayer().setItemInHand(new ItemStack(Material.AIR));
                this.sendInformation(new DupeItem(itemStack, event.getPlayer()));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerDropItemEvent event) {

        if (event.isCancelled()) return;

        Item item = event.getItemDrop();
        ItemStack itemStack = item.getItemStack();
        if (this.dupeManager.isDupeItem(itemStack)) {
            item.remove();
            this.sendInformation(new DupeItem(itemStack, event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(BlockPlaceEvent event) {

        if (event.isCancelled()) return;

        try {
            ItemStack itemStack = event.getItemInHand();
            if (this.dupeManager.isDupeItem(itemStack)) {
                event.getPlayer().getInventory().setItem(event.getHand(), new ItemStack(Material.AIR));
                event.setCancelled(true);
                this.sendInformation(new DupeItem(itemStack, event.getPlayer()));
            }
        } catch (Exception ignored) {

        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrop(PlayerPickupItemEvent event) {

        if (event.isCancelled()) return;

        Item item = event.getItem();
        ItemStack itemStack = item.getItemStack();
        if (this.dupeManager.isDupeItem(itemStack)) {
            item.remove();
            event.setCancelled(true);
            this.sendInformation(new DupeItem(itemStack, event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {

        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack itemStack = event.getCurrentItem();
        boolean currentDupe = isDupeItem(itemStack);
        boolean cursorDupe = isDupeItem(event.getCursor());
        boolean hotbarDupe = event.getHotbarButton() >= 0
            && isDupeItem(player.getInventory().getItem(event.getHotbarButton()));
        boolean offhandDupe = isDupeItem(player.getInventory().getItemInOffHand());

        if (touchesDupeItem(event.getClick(), currentDupe, cursorDupe, hotbarDupe, offhandDupe)) {
            event.setCancelled(true);
            if (cursorDupe) event.setCursor(new ItemStack(Material.AIR));
            resync(player);
            return;
        }

        if (event.isCancelled()) return;

        if (itemStack != null) {
            if (currentDupe) {
                event.setCurrentItem(new ItemStack(Material.AIR));
                event.setCancelled(true);
                this.sendInformation(new DupeItem(itemStack, player));
            }
        }

        itemStack = event.getCursor();
        if (itemStack != null) {
            if (this.dupeManager.isDupeItem(itemStack)) {
                event.setCursor(new ItemStack(Material.AIR));
                event.setCancelled(true);
                this.sendInformation(new DupeItem(itemStack, player));
            }
        }

    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean cursorDupe = isDupeItem(event.getOldCursor());
        boolean newItemDupe = event.getNewItems().values().stream().anyMatch(this::isDupeItem);
        if (!cursorDupe && !newItemDupe) return;

        event.setCancelled(true);
        if (cursorDupe) player.setItemOnCursor(null);
        resync(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        boolean mainHandDupe = isDupeItem(player.getInventory().getItemInMainHand())
            || isDupeItem(event.getMainHandItem());
        boolean offHandDupe = isDupeItem(player.getInventory().getItemInOffHand())
            || isDupeItem(event.getOffHandItem());
        if (!protectsHandSwap(mainHandDupe, offHandDupe)) return;

        event.setCancelled(true);
        resync(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::isDupeItem);
    }

    static boolean touchesDupeItem(ClickType click,
                                   boolean currentDupe,
                                   boolean cursorDupe,
                                   boolean hotbarDupe,
                                   boolean offhandDupe) {
        return cursorDupe
            || (click == ClickType.NUMBER_KEY && (currentDupe || hotbarDupe))
            || (click == ClickType.SWAP_OFFHAND && (currentDupe || offhandDupe));
    }

    static boolean protectsHandSwap(boolean mainHandDupe, boolean offHandDupe) {
        return mainHandDupe || offHandDupe;
    }

    private boolean isDupeItem(ItemStack itemStack) {
        return itemStack != null && this.dupeManager.isDupeItem(itemStack);
    }

    private void resync(Player player) {
        UUID playerUuid = player.getUniqueId();
        if (!this.pendingResyncs.add(playerUuid)) return;
        this.plugin.getScheduler().runAtEntityLater(player, () -> {
            this.pendingResyncs.remove(playerUuid);
            if (!player.isOnline()) return;
            InventoryHolder holder = CompatibilityUtil.getTopInventory(player).getHolder();
            if (!(holder instanceof VInventory)) purgeEscapedItems(player);
            player.updateInventory();
        }, 1);
    }

    private void purgeEscapedItems(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (isDupeItem(itemStack)) player.getInventory().setItem(slot, null);
        }
        if (isDupeItem(player.getItemOnCursor())) player.setItemOnCursor(null);
    }

    private void sendInformation(DupeItem dupeItem) {

        // Rework with new discord webhook class

        /*DiscordWebhook discordWebhook = new DiscordWebhook(Config.antiDupeDiscordWebhookUrl);
        discordWebhook.setContent(Config.antiDupeMessage);

        Logger.info(discordWebhook.replaceString(Config.antiDupeMessage, dupeItem), Logger.LogType.WARNING);
        if (Config.enableAntiDupeDiscordNotification) {
            schedule.runTaskAsynchronously(() -> {
                try {
                    discordWebhook.execute(dupeItem);
                } catch (IOException exception) {
                    exception.printStackTrace();
                }
            });
        }*/
    }

}
