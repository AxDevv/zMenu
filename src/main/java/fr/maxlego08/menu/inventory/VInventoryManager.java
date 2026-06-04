package fr.maxlego08.menu.inventory;

import fr.maxlego08.menu.ZMenuPlugin;
import fr.maxlego08.menu.api.VInvManager;
import fr.maxlego08.menu.api.configuration.Configuration;
import fr.maxlego08.menu.api.engine.InventoryResult;
import fr.maxlego08.menu.api.engine.ItemButton;
import fr.maxlego08.menu.api.exceptions.InventoryAlreadyExistException;
import fr.maxlego08.menu.api.exceptions.InventoryOpenException;
import fr.maxlego08.menu.api.players.inventory.InventoriesPlayer;
import fr.maxlego08.menu.api.players.inventory.InventoryPlayer;
import fr.maxlego08.menu.api.utils.CompatibilityUtil;
import fr.maxlego08.menu.api.utils.ClearInvType;
import fr.maxlego08.menu.api.utils.EnumInventory;
import fr.maxlego08.menu.api.utils.Message;
import fr.maxlego08.menu.common.utils.nms.ItemStackUtils;
import fr.maxlego08.menu.common.utils.nms.NMSUtils;
import fr.maxlego08.menu.inventory.inventories.InventoryDefault;
import fr.maxlego08.menu.listener.ListenerAdapter;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.function.Predicate;

public class VInventoryManager extends ListenerAdapter implements VInvManager {

    private static final int PLAYER_STORAGE_SIZE = 36;
    private static final int OFF_HAND_SLOT = 40;

    private final Map<Integer, VInventory> inventories = new HashMap<>();
    private final ZMenuPlugin plugin;
    private final Map<UUID, Long> cooldownClick = new HashMap<>();


    public VInventoryManager(ZMenuPlugin plugin) {
        super();
        this.plugin = plugin;
    }

    public void registerInventory(EnumInventory enumInventory, VInventory inventory) {
        if (!this.inventories.containsKey(enumInventory.getId())) {
            this.inventories.put(enumInventory.getId(), inventory);
        } else {
            throw new InventoryAlreadyExistException("Inventory with id " + inventory.getId() + " already exist !");
        }
    }

    /**
     * Allows you to open an inventory
     *
     * @param enumInventory - Inventory enum for get the ID
     * @param player        - Player that will open the inventory
     * @param page          - The inventory page
     * @param objects       - The arguments used to make the inventory work
     */
    @Override
    public void createInventory(EnumInventory enumInventory, Player player, int page, Object... objects) {
        this.createInventory(enumInventory.getId(), player, page, objects);
    }

    /**
     * Allows you to open an inventory When opening the inventory will be cloned
     *
     * @param id      - Inventory ID
     * @param player  - Player that will open the inventory
     * @param page    - The inventory page
     * @param objects - The arguments used to make the inventory work
     */
    @Override
    public void createInventory(int id, Player player, int page, Object... objects) {
        Optional<VInventory> optional = this.getInventory(id);

        if (optional.isEmpty()) {
            message(this.plugin, player, Message.VINVENTORY_ERROR, "%id%", id);
            return;
        }

        VInventory inventory = optional.get();

        // We need to clone the object to have one object per open inventory.
        // An inventory will remain open for several seconds, during this time
        // the inventories of the inventory must be correctly saved according to
        // the player.
        VInventory clonedInventory = inventory.clone();

        if (clonedInventory == null) {
            message(this.plugin, player, Message.VINVENTORY_ERROR, "%id%", id);
            return;
        }

        clonedInventory.setId(id);
        try {

            this.plugin.getInventoryManager().getInventoryListeners().forEach(listener -> listener.onInventoryPreOpen(player, clonedInventory, page, objects));

            InventoryResult result = clonedInventory.preOpenInventory(this.plugin, player, page, objects);
            if (result == InventoryResult.SUCCESS) {

                clonedInventory.postOpen(this.plugin, player, page, objects);

                Inventory spigotInventory = clonedInventory.getSpigotInventory();
                player.openInventory(spigotInventory);
                resyncInventoryViewSlot(player, RAW_SLOT_VISUAL_RESYNC);

                this.plugin.getInventoryManager().getInventoryListeners().forEach(listener -> listener.onInventoryPostOpen(player, clonedInventory));

            } else if (result == InventoryResult.SUCCESS_ASYNC) {

                clonedInventory.postOpen(this.plugin, player, page, objects);
            } else if (result == InventoryResult.ERROR) {

                message(this.plugin, player, Message.VINVENTORY_ERROR, "%id%", id);
            }
        } catch (InventoryOpenException exception) {
            message(this.plugin, player, Message.VINVENTORY_ERROR, "%id%", id);
            exception.printStackTrace();
        }
    }

    @Override
    protected void onInventoryClick(InventoryClickEvent event, Player player) {

        if (event.getClickedInventory() == null) {
            return;
        }

        InventoryHolder holder = CompatibilityUtil.getTopInventory(player).getHolder();

        if (holder instanceof VInventory inventory) {

            event.setCancelled(inventory.isDisableClick());

            if (event.getClickedInventory().getType().equals(InventoryType.PLAYER)) {

                event.setCancelled(inventory.isDisablePlayerInventoryClick());

                inventory.onInventoryClick(event, this.plugin, player);
                handleClick(true, player, inventory, event);

            } else {

                inventory.onInventoryClick(event, this.plugin, player);
                handleClick(false, player, inventory, event);
            }
        }
    }

    private void handleClick(boolean inPlayerInventory, Player player, VInventory inventory, InventoryClickEvent event) {

        if (Configuration.enableCooldownClick && this.cooldownClick.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) {
            message(this.plugin, player, Message.CLICK_COOLDOWN);
            return;
        }

        this.cooldownClick.put(player.getUniqueId(), System.currentTimeMillis() +Configuration.cooldownClickMilliseconds);

        ItemButton button = (inPlayerInventory ? inventory.getPlayerInventoryItems() : inventory.getItems()).getOrDefault(event.getSlot(), null);
        if (button != null) {

            this.plugin.getInventoryManager().getInventoryListeners().forEach(listener -> listener.onButtonClick(player, button));

            button.onClick(event);
        }
    }

    @Override
    protected void onInventoryClose(InventoryCloseEvent event, Player player) {
        if (player.isDead()) return;
        InventoryHolder holder = CompatibilityUtil.getTopInventory(event).getHolder();
        if (holder instanceof VInventory inventory) {
            this.plugin.getInventoryManager().getInventoryListeners().forEach(listener -> listener.onInventoryClose(player, inventory));
            inventory.onPreClose(event, this.plugin, player);
        }
    }

    @Override
    protected void onInventoryDrag(InventoryDragEvent event, Player player) {
        InventoryHolder holder = CompatibilityUtil.getTopInventory(event).getHolder();
        if (holder instanceof VInventory vInventory) {
            vInventory.onDrag(event, this.plugin, player);
        }
    }

    @Override
    public void onPickUp(EntityPickupItemEvent event, Player player) {
        InventoryHolder holder = CompatibilityUtil.getTopInventory(player).getHolder();
        if (holder instanceof VInventory vInventory) {
            if (vInventory instanceof InventoryDefault inventoryDefault) {
                fr.maxlego08.menu.api.Inventory menu = inventoryDefault.getMenuInventory();
                if (menu != null && (menu.shouldCancelItemPickup() || menu.cleanInventory())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @Override
    protected void onDeath(PlayerDeathEvent event, Player player) {
        InventoryHolder holder = CompatibilityUtil.getTopInventory(player).getHolder();
        if (holder instanceof VInventory vInventory) {
            if (vInventory instanceof InventoryDefault inventoryDefault) {
                fr.maxlego08.menu.api.Inventory menu = inventoryDefault.getMenuInventory();
                if (menu != null && menu.cleanInventory()) {
                    InventoriesPlayer inventoriesPlayer = plugin.getInventoriesPlayer();
                    Optional<InventoryPlayer> savedInventory = inventoriesPlayer.getPlayerInventory(player.getUniqueId());
                    if (savedInventory.isEmpty()) {
                        return;
                    }
                    InventoryPlayer inventoryPlayer = savedInventory.get();
                    if (menu.getClearInvType() == ClearInvType.PACKET_EVENT) {
                        inventoriesPlayer.clearInventorie(player.getUniqueId());
                        return;
                    }

                    inventoriesPlayer.clearInventorie(player.getUniqueId());
                    if (event.getKeepInventory()) {
                        event.getDrops().clear();
                        restoreSavedInventory(player, inventoryPlayer);
                        return;
                    }

                    List<ItemStack> armorDrops = getStoredArmor(player);
                    event.getDrops().clear();
                    addSavedDrops(event.getDrops(), inventoryPlayer);
                    event.getDrops().addAll(armorDrops);
                }
            }
        }
    }

    private void restoreSavedInventory(Player player, InventoryPlayer inventoryPlayer) {
        org.bukkit.inventory.PlayerInventory playerInventory = player.getInventory();
        for (int slot = 0; slot < PLAYER_STORAGE_SIZE; slot++) {
            playerInventory.clear(slot);
        }
        if (!NMSUtils.isOneHand()) {
            playerInventory.clear(OFF_HAND_SLOT);
        }

        inventoryPlayer.getItems().forEach((slot, encodedItemStack) -> {
            if (!isManagedPlayerSlot(slot)) {
                return;
            }
            ItemStack itemStack = ItemStackUtils.deserializeItemStack(encodedItemStack);
            if (isRealItem(itemStack)) {
                playerInventory.setItem(slot, itemStack);
            }
        });
    }

    private void addSavedDrops(List<ItemStack> drops, InventoryPlayer inventoryPlayer) {
        inventoryPlayer.getItems().values().forEach(encodedItemStack -> {
            ItemStack itemStack = ItemStackUtils.deserializeItemStack(encodedItemStack);
            if (isRealItem(itemStack)) {
                drops.add(itemStack);
            }
        });
    }

    private List<ItemStack> getStoredArmor(Player player) {
        List<ItemStack> armorDrops = new ArrayList<>();
        for (ItemStack itemStack : player.getInventory().getArmorContents()) {
            if (isRealItem(itemStack)) {
                armorDrops.add(itemStack.clone());
            }
        }
        return armorDrops;
    }

    private boolean isManagedPlayerSlot(int slot) {
        return slot >= 0 && slot < PLAYER_STORAGE_SIZE || !NMSUtils.isOneHand() && slot == OFF_HAND_SLOT;
    }

    private boolean isRealItem(ItemStack itemStack) {
        return itemStack != null && itemStack.getType() != Material.AIR;
    }

    /**
     * @param id - Inventory I'd
     * @return Optional - Allows returning the inventory in an optional
     */
    private Optional<VInventory> getInventory(int id) {
        return Optional.ofNullable(this.inventories.getOrDefault(id, null));
    }

    public void close() {
        this.close(v -> !v.isClose());
    }

    public void close(Predicate<VInventory> predicate) {
        if (!this.plugin.isEnabled()) {
            Bukkit.getOnlinePlayers().forEach(player -> needClose(player, predicate));
            return;
        }
        Bukkit.getOnlinePlayers().forEach(player -> this.plugin.getScheduler().runAtEntity(player, task -> needClose(player, predicate)));
    }

    private void needClose(Player player, Predicate<VInventory> predicate) {
        var inventory = CompatibilityUtil.getTopInventory(player);
        if (inventory != null && inventory.getHolder() != null) {
            InventoryHolder holder = inventory.getHolder();
            if (holder instanceof VInventory vInventory && predicate.test(vInventory)) {
                if (player.isOnline()) {
                    player.closeInventory();
                }
            }
        }
    }

    @Override
    protected void onConnect(PlayerJoinEvent event, Player player) {
        // Send information to me, because I like to know
        if (player.getName().equals("Maxlego08")) {
            this.plugin.getScheduler().runAtEntityLater(player, w -> message(this.plugin, player, "&aLe serveur utilise &2zMenu v" + this.plugin.getDescription().getVersion()), 20);
        }
    }

    @Override
    protected void onQuit(PlayerQuitEvent event, Player player) {
        this.cooldownClick.remove(player.getUniqueId());
        this.plugin.getInventoryManager().getPaginationManager().removePlayerStates(player.getUniqueId());
    }
}
