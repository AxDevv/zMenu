package fr.maxlego08.menu.inventory;

import fr.maxlego08.menu.ZMenuPlugin;
import fr.maxlego08.menu.api.VInvManager;
import fr.maxlego08.menu.api.configuration.Configuration;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.engine.InventoryResult;
import fr.maxlego08.menu.api.engine.ItemButton;
import fr.maxlego08.menu.api.exceptions.InventoryAlreadyExistException;
import fr.maxlego08.menu.api.exceptions.InventoryOpenException;
import fr.maxlego08.menu.api.inventory.ContainerInventory;
import fr.maxlego08.menu.api.players.inventory.InventoriesPlayer;
import fr.maxlego08.menu.api.players.inventory.InventoryPlayer;
import fr.maxlego08.menu.api.utils.ClearInvType;
import fr.maxlego08.menu.api.utils.CompatibilityUtil;
import fr.maxlego08.menu.api.utils.EnumInventory;
import fr.maxlego08.menu.api.utils.Message;
import fr.maxlego08.menu.inventory.inventories.InventoryDefault;
import fr.maxlego08.menu.listener.ListenerAdapter;
import fr.maxlego08.menu.common.utils.nms.ItemStackUtils;
import org.bukkit.Bukkit;
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
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Predicate;

public class VInventoryManager extends ListenerAdapter implements VInvManager {

    private final Map<Integer, Map<InventoryType, VInventory>> inventories = new HashMap<>();
    private final ZMenuPlugin plugin;
    private final Map<UUID, Long> cooldownClick = new HashMap<>();


    public VInventoryManager(ZMenuPlugin plugin) {
        super();
        this.plugin = plugin;
    }

    public void registerInventory(EnumInventory enumInventory, VInventory inventory) {
        this.registerInventory(enumInventory.getId(), InventoryType.CHEST, inventory);
    }

    public void registerInventory(int id, InventoryType type, VInventory inventory) {
        Map<InventoryType, VInventory> typeMap = this.inventories.computeIfAbsent(id, k -> new EnumMap<>(InventoryType.class));
        if (typeMap.containsKey(type)) {
            throw new InventoryAlreadyExistException("Inventory with id " + inventory.getId() + " already exist !");
        }
        typeMap.put(type, inventory);
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
        ContainerInventory containerInventory = (ContainerInventory) objects[0];

        Optional<VInventory> optional = this.getInventoryOrDefault(id, containerInventory.getType());

        if (optional.isEmpty()) {
            this.message(this.plugin, player, Message.VINVENTORY_ERROR, "%id%", id);
            return;
        }

        VInventory inventory = optional.get();

        // We need to clone the object to have one object per open inventory.
        // An inventory will remain open for several seconds, during this time
        // the inventories of the inventory must be correctly saved according to
        // the player.
        VInventory clonedInventory = inventory.clone();

        if (clonedInventory == null) {
            this.message(this.plugin, player, Message.VINVENTORY_ERROR, "%id%", id);
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

                clonedInventory.onPostOpen(player, this.plugin, page, objects);

                this.plugin.getInventoryManager().getInventoryListeners().forEach(listener -> listener.onInventoryPostOpen(player, clonedInventory));

            } else if (result == InventoryResult.SUCCESS_ASYNC) {

                clonedInventory.postOpen(this.plugin, player, page, objects);
            } else if (result == InventoryResult.ERROR) {

                this.message(this.plugin, player, Message.VINVENTORY_ERROR, "%id%", id);
            }
        } catch (InventoryOpenException exception) {
            this.message(this.plugin, player, Message.VINVENTORY_ERROR, "%id%", id);
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

            boolean alreadyCancelled = event.isCancelled();

            if (event.getClickedInventory().getType().equals(InventoryType.PLAYER)) {

                event.setCancelled(InventoryClickPolicy.shouldCancel(alreadyCancelled, inventory.isDisablePlayerInventoryClick()));
                if (alreadyCancelled) return;

                inventory.onInventoryClick(event, this.plugin, player);
                this.handleClick(true, player, inventory, event);

            } else {

                event.setCancelled(InventoryClickPolicy.shouldCancel(alreadyCancelled, inventory.isDisableClick()));
                if (alreadyCancelled) return;

                inventory.onInventoryClick(event, this.plugin, player);
                this.handleClick(false, player, inventory, event);
            }
        }
    }

    private void handleClick(boolean inPlayerInventory, Player player, VInventory inventory, InventoryClickEvent event) {

        if (Configuration.enableCooldownClick && this.cooldownClick.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis()) {
            this.message(this.plugin, player, Message.CLICK_COOLDOWN);
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
        if (holder instanceof InventoryDefault oldInventoryEngine) {
            this.plugin.getScheduler().runAtEntityLater(player, () -> {
                InventoryHolder newHolder = CompatibilityUtil.getTopInventory(player).getHolder();
                if (newHolder instanceof InventoryDefault newInventoryEngine) {
                    this.plugin.getInventoryManager().getInventoryListeners().forEach(listener -> listener.onInventorySwitch(player, oldInventoryEngine, newInventoryEngine));
                    oldInventoryEngine.onInventorySwitch(event, player, newInventoryEngine);
                } else {
                    this.plugin.getInventoryManager().getInventoryListeners().forEach(listener -> listener.onInventoryClose(player, oldInventoryEngine));
                    oldInventoryEngine.onPreClose(event, this.plugin, player);
                }
            }, 1);
        }
    }

    @Override
    protected void onInventoryDrag(InventoryDragEvent event, Player player) {
        InventoryHolder holder = CompatibilityUtil.getTopInventory(event).getHolder();
        if (holder instanceof VInventory inventory) {
            event.setCancelled(true);
            inventory.onDrag(event, this.plugin, player);
        }
    }

    @Override
    public void onPickUp(EntityPickupItemEvent event, Player player) {
        InventoryHolder holder = CompatibilityUtil.getTopInventory(player).getHolder();
        if (holder instanceof InventoryEngine inventoryEngine) {
            if (inventoryEngine.getMenuInventory() instanceof ContainerInventory containerInventory && (containerInventory.shouldCancelItemPickup() || containerInventory.cleanInventory())) {
                event.setCancelled(true);
            }
        }
    }

    @Override
    protected void onDeath(PlayerDeathEvent event, Player player) {
        InventoryHolder holder = CompatibilityUtil.getTopInventory(player).getHolder();
        if (holder instanceof VInventory vInventory) {
            if (vInventory instanceof InventoryDefault inventoryDefault) {
                if (inventoryDefault.getMenuInventory() instanceof ContainerInventory containerInventory && containerInventory.clearInventory() && containerInventory.getClearInvType() == ClearInvType.DEFAULT) {
                    InventoriesPlayer inventoriesPlayer = this.plugin.getInventoriesPlayer();
                    Optional<InventoryPlayer> playerInventory = inventoriesPlayer.getPlayerInventory(player.getUniqueId());
                    if (playerInventory.isEmpty() || !playerInventory.get().isPermanent()) {
                        this.plugin.getLogger().severe("Ignored zMenu death recovery without a validated permanent snapshot for "
                            + player.getUniqueId());
                        return;
                    }

                    Map<Integer, ItemStack> restoredItems = new HashMap<>();
                    try {
                        for (var entry : playerInventory.get().getItems().entrySet()) {
                            int slot = entry.getKey();
                            if (slot < 0 || slot >= player.getInventory().getSize()) {
                                throw new IllegalStateException("invalid slot " + slot);
                            }
                            ItemStack itemStack = ItemStackUtils.deserializeItemStack(entry.getValue());
                            if (itemStack == null) throw new IllegalStateException("invalid item at slot " + slot);
                            restoredItems.put(slot, itemStack);
                        }
                    } catch (RuntimeException error) {
                        this.plugin.getLogger().severe("Quarantined invalid zMenu death recovery for "
                            + player.getUniqueId() + ": " + error.getMessage());
                        return;
                    }

                    List<ItemStack> drops = event.getDrops();
                    List<ItemStack> originalDrops = new ArrayList<>(drops);
                    ItemStack[] originalContents = cloneItems(player.getInventory().getContents());
                    ItemStack[] armorContents = cloneItems(player.getInventory().getArmorContents());
                    try {
                        drops.clear();
                        if (event.getKeepInventory()) {
                            player.getInventory().clear();
                            for (var entry : restoredItems.entrySet()) {
                                player.getInventory().setItem(entry.getKey(), entry.getValue().clone());
                            }
                            player.getInventory().setArmorContents(cloneItems(armorContents));
                        } else {
                            restoredItems.values().forEach(item -> drops.add(item.clone()));
                            Arrays.stream(armorContents).filter(Objects::nonNull).forEach(item -> drops.add(item.clone()));
                        }
                        inventoriesPlayer.clearInventorie(player.getUniqueId());
                    } catch (RuntimeException error) {
                        drops.clear();
                        drops.addAll(originalDrops);
                        player.getInventory().setContents(originalContents);
                        this.plugin.getLogger().severe("Rolled back failed zMenu death recovery for "
                            + player.getUniqueId() + ": " + error.getMessage());
                    }
                }
            }
        }
    }

    private ItemStack[] cloneItems(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index] == null ? null : source[index].clone();
        }
        return copy;
    }

    /**
     * @param id - Inventory I'd
     * @return Optional - Allows returning the inventory in an optional
     */
    private Optional<VInventory> getInventory(int id) {
        Map<InventoryType, VInventory> typeMap = this.inventories.get(id);
        return typeMap == null ? Optional.empty() : Optional.ofNullable(typeMap.get(InventoryType.CHEST));
    }

    private Optional<VInventory> getInventoryOrDefault(int id, @NotNull InventoryType type) {
        Map<InventoryType, VInventory> typeMap = this.inventories.get(id);
        if (typeMap == null) {
            return Optional.empty();
        }
        VInventory inventory = typeMap.get(type);
        if (inventory == null) {
            inventory = typeMap.get(InventoryType.CHEST);
        }
        return Optional.ofNullable(inventory);
    }

    public void close() {
        this.close(v -> !v.isClose());
    }

    public void close(Predicate<VInventory> predicate) {
        if (!this.plugin.isEnabled()) {
            Bukkit.getOnlinePlayers().forEach(player -> this.needClose(player, predicate));
            return;
        }
        Bukkit.getOnlinePlayers().forEach(player -> this.plugin.getScheduler().runAtEntity(player, task -> this.needClose(player, predicate)));
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
            this.plugin.getScheduler().runAtEntityLater(player, w -> this.message(this.plugin, player, "&aLe serveur utilise &2zMenu v" + this.plugin.getDescription().getVersion()), 20);
        }
    }

    @Override
    protected void onQuit(PlayerQuitEvent event, Player player) {
        Inventory topInventory = CompatibilityUtil.getTopInventory(player);
        if (topInventory != null && topInventory.getHolder() instanceof VInventory vInventory) {
            vInventory.onPreClose(null, this.plugin, player);
        }
        this.cooldownClick.remove(player.getUniqueId());
        this.plugin.getInventoryManager().getPaginationManager().removePlayerStates(player.getUniqueId());
    }
}
