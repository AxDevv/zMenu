package fr.maxlego08.menu.inventory.zinv;

import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.MenuPlugin;
import fr.maxlego08.menu.api.animation.TitleAnimation;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.configuration.Configuration;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.engine.InventoryResult;
import fr.maxlego08.menu.api.inventory.ContainerInventory;
import fr.maxlego08.menu.api.pattern.Pattern;
import fr.maxlego08.menu.api.players.inventory.InventoriesPlayer;
import fr.maxlego08.menu.api.players.inventory.InventoryPlayer;
import fr.maxlego08.menu.api.requirement.Action;
import fr.maxlego08.menu.api.requirement.ConditionalName;
import fr.maxlego08.menu.api.requirement.Requirement;
import fr.maxlego08.menu.api.utils.*;
import fr.maxlego08.menu.common.utils.ZUtils;
import fr.maxlego08.menu.inventory.inventories.InventoryDefault;
import fr.maxlego08.menu.inventory.setter.ContainerInventorySetter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ZInventory extends ZUtils implements ContainerInventorySetter {

    private static final AtomicLong PLAYER_INV_OPEN_SEQUENCE = new AtomicLong();
    private static final Map<InventoryEngine, Long> PLAYER_INV_ENGINE_GENERATIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UUID, Long> PLAYER_INV_ACTIVE_GENERATIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, ClearInventorySession> PLAYER_CLEAR_INVENTORY_SESSIONS = new ConcurrentHashMap<>();

    private record ClearInventorySession(long generation, InventoryEngine engine, List<ItemStack> pendingItems) {

        private ClearInventorySession {
            pendingItems = List.copyOf(pendingItems);
        }
    }

    private final Plugin plugin;
    private final String name;
    private final String fileName;
    private final int size;
    private final List<Button> buttons;
    private final List<ConditionalName> conditionalNames = new ArrayList<>();
    private InventoryReplacement inventoryReplacement;
    private Map<String, String> translatedNames = new HashMap<>();
    private List<Pattern> patterns = new ArrayList<>();
    private MenuItemStack fillItemStack;
    private int updateInterval;
    private File file;
    private boolean clearInventory;
    private ClearInvType clearInvType = ClearInvType.DEFAULT;
    private boolean ItemPickupDisabled;
    private boolean isClickLimiterEnabled = true;
    private Requirement openRequirement;
    private OpenWithItem openWithItem;
    private InventoryType type = InventoryType.CHEST;
    private String targetPlayerNamePlaceholder = null;
    private TitleAnimation titleAnimation;
    private List<Action> openActions = new ArrayList<>();
    private List<Action> closeActions = new ArrayList<>();

    /**
     * @param plugin   The plugin where the inventory comes from
     * @param name     Inventory name
     * @param fileName Inventory file name
     * @param size     Inventory size
     * @param buttons  List of {@link Button}
     */
    public ZInventory(Plugin plugin, String name, String fileName, int size, List<Button> buttons) {
        super();
        this.plugin = plugin;
        this.name = name;
        this.fileName = fileName;
        this.size = size;
        this.buttons = buttons;
    }

    @Override
    public int size() {
        return this.size;
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public String getName(@NotNull Player player, InventoryEngine inventoryDefault, Placeholders placeholders) {

        if (!this.conditionalNames.isEmpty()) {
            ConditionalName selected = null;
            int highestPriority = Integer.MIN_VALUE;
            for (ConditionalName conditionalName : this.conditionalNames) {
                if (conditionalName.hasPermission(player, null, inventoryDefault, placeholders)) {
                    if (selected == null || conditionalName.priority() > highestPriority) {
                        selected = conditionalName;
                        highestPriority = conditionalName.priority();
                    }
                }
            }

            if (selected != null) {
                return selected.name();
            }
        }

        String locale = this.findPlayerLocale(player);
        return locale == null ? this.name : this.translatedNames.getOrDefault(locale, this.name);
    }

    @Override
    public InventoryType getType() {
        return this.type;
    }

    @Override
    public void setType(@NotNull InventoryType type) {
        this.type = type;
    }

    @Override
    public boolean shouldCancelItemPickup() {
        return this.ItemPickupDisabled;
    }

    @Override
    public void setCancelItemPickup(boolean ItemPickupDisabled) {
        this.ItemPickupDisabled = ItemPickupDisabled;
    }

    @Override
    public String getFileName() {
        return this.fileName;
    }

    @Override
    public Collection<Button> getButtons() {
        return Collections.unmodifiableCollection(this.buttons);
    }

    @Override
    public <T extends Button> List<T> getButtons(Class<T> type) {
        List<T> filtered = new ArrayList<>();
        for (Button button : this.getButtons()) {
            if (type.isAssignableFrom(button.getClass())) {
                filtered.add(type.cast(button));
            }
        }
        return filtered;
    }

    @Override
    public Plugin getPlugin() {
        return this.plugin;
    }

    @Override
    public int getMaxPage(Collection<Pattern> patterns, Player player, Object... objects) {
        List<Button> buttons = new ArrayList<>(this.buttons);
        patterns.forEach(pattern -> buttons.addAll(pattern.buttons()));

        int maxSlotInventory = -1;
        for (Button button : buttons) {
            if (!button.isPlayerInventory()) {
                maxSlotInventory = Math.max(maxSlotInventory, button.getSlot());
            }
        }
        int maxPageInventory = (maxSlotInventory >= 0) ? (maxSlotInventory / this.size) + 1 : 1;

        int maxSlotPlayerInventory = -1;
        for (Button button : buttons) {
            if (button.isPlayerInventory()) {
                maxSlotPlayerInventory = Math.max(maxSlotPlayerInventory, button.getSlot());
            }
        }
        int maxPagePlayerInventory = (maxSlotPlayerInventory >= 0) ? (maxSlotPlayerInventory / 36) + 1 : 1;

        final int maxPageFinal = Math.max(maxPageInventory, maxPagePlayerInventory);

        PaginateButton selected = null;
        int maxPaginationSize = Integer.MIN_VALUE;
        for (Button button : buttons) {
            if (button instanceof PaginateButton paginateButton) {
                int paginationSize = paginateButton.getPaginationSize(player);
                if (selected == null || paginationSize > maxPaginationSize) {
                    selected = paginateButton;
                    maxPaginationSize = paginationSize;
                }
            }
        }

        if (selected != null) {
            int slotsSize = selected.getSlots().size();
            if (slotsSize > 0) {
                int calculated = (int) Math.ceil((double) maxPaginationSize / slotsSize);
                return Math.max(maxPageFinal, calculated);
            }
        }
        return maxPageFinal;
    }

    @Override
    public List<Button> sortButtons(int page, Object... objects) {
        List<Button> sorted = new ArrayList<>();
        for (Button button : this.buttons) {
            int size = button.isPlayerInventory() ? 36 : this.size;
            int slot = button.getRealSlot(size, page);
            if (slot >= 0 && slot < size) {
                sorted.add(button);
            }
        }
        return sorted;
    }

    @Override
    public List<Button> sortPatterns(Pattern pattern, int page, Object... objects) {
        if (!pattern.enableMultiPage()) return new ArrayList<>(pattern.buttons());
        List<Button> sorted = new ArrayList<>();
        for (Button button : pattern.buttons()) {
            int slot = button.getRealSlot(this.size, page);
            if (slot >= 0 && slot < this.size) {
                sorted.add(button);
            }
        }
        return sorted;
    }

    @Override
    public InventoryResult openInventory(Player player, InventoryEngine inventoryDefault) {
        if (this.openRequirement != null && !this.openRequirement.execute(player, null, inventoryDefault, new Placeholders())) {
            return InventoryResult.PERMISSION;
        }

        MenuPlugin menuPlugin = inventoryDefault.getPlugin();
        boolean packetProjectionUnavailable = this.clearInvType == ClearInvType.PACKET_EVENT
            && !menuPlugin.getServer().getPluginManager().isPluginEnabled("packetevents");
        if (this.clearInventory && (this.clearInvType == ClearInvType.DEFAULT || packetProjectionUnavailable)) {
            menuPlugin.getLogger().severe("Refused unsafe clear-inventory menu without PacketEvents player="
                + player.getUniqueId()
                + " inventory=" + this.fileName);
            return InventoryResult.ERROR;
        }
        InventoriesPlayer inventoriesPlayer = menuPlugin.getInventoriesPlayer();
        InventoryHolder holder = CompatibilityUtil.getTopInventory(player).getHolder();
        UUID playerUuid = player.getUniqueId();
        long openGeneration = PLAYER_INV_OPEN_SEQUENCE.incrementAndGet();
        PLAYER_INV_ACTIVE_GENERATIONS.put(playerUuid, openGeneration);
        PLAYER_INV_ENGINE_GENERATIONS.put(inventoryDefault, openGeneration);
        boolean savedBeforeOpen = inventoriesPlayer.hasSavedInventory(playerUuid);
        InventoryEngine previousEngine = holder instanceof InventoryDefault inventoryHolder ? inventoryHolder : null;
        boolean previousStoredClear = isStoredClearInventory(previousEngine);
        boolean previousPacketClear = isPacketClearInventory(previousEngine);
        boolean targetStoredClear = this.clearInventory && this.clearInvType == ClearInvType.DEFAULT;
        ClearInventorySession session = PLAYER_CLEAR_INVENTORY_SESSIONS.get(playerUuid);

        if (session == null && previousStoredClear && savedBeforeOpen) {
            Long previousGeneration = PLAYER_INV_ENGINE_GENERATIONS.get(previousEngine);
            session = new ClearInventorySession(previousGeneration == null ? openGeneration : previousGeneration, previousEngine, List.of());
            PLAYER_CLEAR_INVENTORY_SESSIONS.put(playerUuid, session);
        }

        if (targetStoredClear) {
            if (session != null) {
                List<ItemStack> pendingItems = new ArrayList<>(session.pendingItems());
                pendingItems.addAll(collectSessionItems(menuPlugin, player, session.engine()));
                clearTemporaryInventory(player);
                session = new ClearInventorySession(openGeneration, inventoryDefault, pendingItems);
            } else {
                if (previousStoredClear) {
                    finishStoredClearInventory(menuPlugin, inventoriesPlayer, player, previousEngine, List.of(), "untracked-clear-menu-switch");
                } else if (previousEngine != null) {
                    clearPlayerInventoryButtons(player, previousEngine);
                }
                if (savedBeforeOpen) {
                    recoverOrphanedSavedInventory(menuPlugin, inventoriesPlayer, player, previousEngine, "clear-menu-open");
                }
                inventoriesPlayer.storeInventory(player);
            }

            PLAYER_CLEAR_INVENTORY_SESSIONS.put(
                playerUuid,
                session == null ? new ClearInventorySession(openGeneration, inventoryDefault, List.of()) : session);
        } else {
            if (session != null) {
                PLAYER_CLEAR_INVENTORY_SESSIONS.remove(playerUuid, session);
                finishStoredClearInventory(menuPlugin, inventoriesPlayer, player, session.engine(), session.pendingItems(), "menu-switch");
            } else {
                if (previousStoredClear) {
                    finishStoredClearInventory(menuPlugin, inventoriesPlayer, player, previousEngine, List.of(), "untracked-menu-switch");
                } else if (previousEngine != null) {
                    clearPlayerInventoryButtons(player, previousEngine);
                }
                if (savedBeforeOpen && !previousStoredClear && !previousPacketClear) {
                    recoverOrphanedSavedInventory(menuPlugin, inventoriesPlayer, player, previousEngine, "menu-open");
                }
            }

            if (this.clearInventory && this.clearInvType == ClearInvType.PACKET_EVENT) {
                if (previousEngine == null) {
                    inventoriesPlayer.storeInventoryTemporary(player);
                } else {
                    inventoriesPlayer.storeInventoryTemporaryOrClear(player);
                }
            } else if (previousPacketClear) {
                inventoriesPlayer.giveInventory(player);
            }
        }

        if (targetStoredClear && !inventoriesPlayer.hasSavedInventory(playerUuid)) {
            menuPlugin.getLogger().warning("[ClearInventoryAnomaly] Unable to save player inventory for clear-inventory menu player="
                + playerUuid
                + " inventory=" + this.fileName
                + " holder=" + describeHolder(holder)
                + " savedBeforeOpen=" + savedBeforeOpen
                + " previousStoredClear=" + previousStoredClear
                + " clearType=" + this.clearInvType);
        }

        var placeholders = new Placeholders();
        this.openActions.forEach(action -> action.preExecute(player, null, inventoryDefault, placeholders));

        return InventoryResult.SUCCESS;
    }

    private static void clearPlayerInventoryButtons(Player player, InventoryEngine inventoryDefault) {
        if (inventoryDefault == null) return;
        ClearInvType buttonClearType = resolveClearInvType(inventoryDefault);
        for (Button button : inventoryDefault.getButtons()) {
            if (button.isPlayerInventory()) {
                for (int slot : button.getSlots()) {
                    if (slot >= 0 && slot < 36) {
                        buttonClearType.getOnButtonClear().accept(player, slot);
                    }
                }
            }
        }
    }

    private static Set<Integer> getMenuSlots(InventoryEngine inventoryDefault) {
        if (inventoryDefault == null) return Collections.emptySet();
        Set<Integer> buttonSlots = new HashSet<>(inventoryDefault.getPlayerInventoryItems().keySet());
        for (Button button : inventoryDefault.getButtons()) {
            if (button.isPlayerInventory()) {
                for (int slot : button.getSlots()) {
                    if ((slot >= 0 && slot < 36) || slot == 40) {
                        buttonSlots.add(slot);
                    }
                }
            }
        }
        return buttonSlots;
    }

    private static List<ItemStack> collectSessionItems(MenuPlugin menuPlugin, Player player, InventoryEngine inventoryDefault) {
        var playerInventory = player.getInventory();
        return ClearInventoryReconciler.collectSessionItems(
            playerInventory.getStorageContents(),
            playerInventory.getItemInOffHand(),
            getMenuSlots(inventoryDefault),
            menuPlugin.getDupeManager()::isDupeItem);
    }

    private static void restoreSessionItems(Player player, List<ItemStack> sessionItems) {
        if (sessionItems.isEmpty()) return;
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(sessionItems.toArray(new ItemStack[0]));
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private static void clearTemporaryInventory(Player player) {
        var playerInventory = player.getInventory();
        for (int slot = 0; slot < playerInventory.getStorageContents().length; slot++) {
            playerInventory.setItem(slot, null);
        }
        playerInventory.setItemInOffHand(null);
    }

    private static void finishStoredClearInventory(MenuPlugin menuPlugin,
                                                   InventoriesPlayer inventoriesPlayer,
                                                   Player player,
                                                   InventoryEngine inventoryDefault,
                                                   Collection<ItemStack> pendingItems,
                                                   String reason) {
        UUID playerUuid = player.getUniqueId();
        List<ItemStack> sessionItems = new ArrayList<>(pendingItems);
        sessionItems.addAll(collectSessionItems(menuPlugin, player, inventoryDefault));
        clearTemporaryInventory(player);

        if (inventoriesPlayer.hasSavedInventory(playerUuid)) {
            inventoriesPlayer.forceGiveInventoryDirect(player);
        } else {
            menuPlugin.getLogger().warning("[ClearInventoryAnomaly] Clear-inventory session had no saved inventory player="
                + playerUuid
                + " inventory=" + menuFileName(inventoryDefault, "unknown")
                + " preservedItems=" + sessionItems.size()
                + " reason=" + reason);
        }
        restoreSessionItems(player, sessionItems);
        removeTemporaryCursorItem(menuPlugin, player);
        player.updateInventory();
    }

    private static void recoverOrphanedSavedInventory(MenuPlugin menuPlugin,
                                                       InventoriesPlayer inventoriesPlayer,
                                                       Player player,
                                                       InventoryEngine inventoryDefault,
                                                       String reason) {
        UUID playerUuid = player.getUniqueId();
        Optional<InventoryPlayer> savedInventory = inventoriesPlayer.getPlayerInventory(playerUuid);
        if (savedInventory.isEmpty()) return;

        var playerInventory = player.getInventory();
        List<ItemStack> savedItems = savedInventory.get().getItemStacks();
        List<ItemStack> orphanedItems = ClearInventoryReconciler.collectOrphanedSessionItems(
            playerInventory.getStorageContents(),
            playerInventory.getItemInOffHand(),
            getMenuSlots(inventoryDefault),
            menuPlugin.getDupeManager()::isDupeItem,
            savedItems);

        clearTemporaryInventory(player);
        inventoriesPlayer.forceGiveInventoryDirect(player);
        restoreSessionItems(player, orphanedItems);
        removeTemporaryCursorItem(menuPlugin, player);
        player.updateInventory();
        menuPlugin.getLogger().warning("[ClearInventoryAnomaly] Reconciled stale saved inventory player="
            + playerUuid
            + " inventory=" + menuFileName(inventoryDefault, "none")
            + " savedItems=" + savedItems.size()
            + " preservedItems=" + orphanedItems.size()
            + " reason=" + reason);
    }

    private static void removeTemporaryCursorItem(MenuPlugin menuPlugin, Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (cursor != null && menuPlugin.getDupeManager().isDupeItem(cursor)) {
            player.setItemOnCursor(null);
        }
    }

    public static boolean restorePlayerInventoryBeforeExternalSave(MenuPlugin menuPlugin, Player player, String reason) {
        UUID playerUuid = player.getUniqueId();
        InventoriesPlayer inventoriesPlayer = menuPlugin.getInventoriesPlayer();
        ClearInventorySession session = PLAYER_CLEAR_INVENTORY_SESSIONS.remove(playerUuid);
        if (session != null) {
            finishStoredClearInventory(
                menuPlugin,
                inventoriesPlayer,
                player,
                session.engine(),
                session.pendingItems(),
                reason);
            PLAYER_INV_ACTIVE_GENERATIONS.remove(playerUuid);
            return true;
        }

        Optional<InventoryPlayer> savedInventory = inventoriesPlayer.getPlayerInventory(playerUuid);
        if (savedInventory.isEmpty() || !savedInventory.get().isPermanent()) {
            PLAYER_INV_ACTIVE_GENERATIONS.remove(playerUuid);
            return false;
        }

        InventoryHolder holder = CompatibilityUtil.getTopInventory(player).getHolder();
        InventoryEngine visibleEngine = holder instanceof InventoryDefault inventoryDefault ? inventoryDefault : null;
        if (isStoredClearInventory(visibleEngine)) {
            finishStoredClearInventory(
                menuPlugin,
                inventoriesPlayer,
                player,
                visibleEngine,
                List.of(),
                reason);
        } else {
            recoverOrphanedSavedInventory(menuPlugin, inventoriesPlayer, player, visibleEngine, reason);
        }
        PLAYER_INV_ACTIVE_GENERATIONS.remove(playerUuid);
        return true;
    }

    @Override
    public void postOpenInventory(Player player, InventoryEngine inventoryDefault) {

    }

    @Override
    public void closeInventory(Player player, InventoryEngine inventoryDefault) {

        MenuPlugin menuPlugin = inventoryDefault.getPlugin();
        UUID playerUuid = player.getUniqueId();
        Long closeGeneration = PLAYER_INV_ENGINE_GENERATIONS.get(inventoryDefault);
        menuPlugin.getScheduler().runAtEntityLater(player, task -> {
            InventoryHolder newHolder = CompatibilityUtil.getTopInventory(player).getHolder();
            boolean isInNewzMenuInventory = newHolder instanceof InventoryDefault;
            boolean supersededClose = isSupersededClose(playerUuid, closeGeneration);

            if (supersededClose) {
                executeCloseActions(player, inventoryDefault, true);
                PLAYER_INV_ENGINE_GENERATIONS.remove(inventoryDefault);
                return;
            }

            if (!isInNewzMenuInventory) {
                ClearInvType closeClearInvType = resolveClearInvType(inventoryDefault);
                InventoriesPlayer inventoriesPlayer = menuPlugin.getInventoriesPlayer();
                if (this.clearInventory && closeClearInvType == ClearInvType.DEFAULT) {
                    ClearInventorySession session = PLAYER_CLEAR_INVENTORY_SESSIONS.get(playerUuid);
                    if (session != null && (closeGeneration == null || session.generation() == closeGeneration)) {
                        PLAYER_CLEAR_INVENTORY_SESSIONS.remove(playerUuid, session);
                        finishStoredClearInventory(menuPlugin, inventoriesPlayer, player, session.engine(), session.pendingItems(), "menu-close");
                    } else if (inventoriesPlayer.hasSavedInventory(playerUuid)) {
                        PLAYER_CLEAR_INVENTORY_SESSIONS.remove(playerUuid);
                        finishStoredClearInventory(menuPlugin, inventoriesPlayer, player, inventoryDefault, List.of(), "untracked-menu-close");
                    } else {
                        finishStoredClearInventory(menuPlugin, inventoriesPlayer, player, inventoryDefault, List.of(), "missing-session-menu-close");
                    }
                } else {
                    clearPlayerInventoryButtons(player, inventoryDefault);
                    if (this.clearInventory) {
                        closeClearInvType.getOnInventoryClose().accept(inventoriesPlayer, player);
                    }
                }
            } else if (this.clearInventory) {
                menuPlugin.getLogger().warning("[ClearInventoryAnomaly] Clear-inventory close still sees a zMenu inventory without a newer generation player="
                    + playerUuid
                    + " inventory=" + menuFileName(inventoryDefault)
                    + " newHolder=" + describeHolder(newHolder)
                    + " closeGeneration=" + closeGeneration
                    + " activeGeneration=" + PLAYER_INV_ACTIVE_GENERATIONS.get(playerUuid));
            }

            executeCloseActions(player, inventoryDefault, isInNewzMenuInventory);

            if (!isInNewzMenuInventory && closeGeneration != null) {
                PLAYER_INV_ACTIVE_GENERATIONS.remove(playerUuid, closeGeneration);
            }
            PLAYER_INV_ENGINE_GENERATIONS.remove(inventoryDefault);
        }, 1);

    }

    private String describeHolder(InventoryHolder holder) {
        if (holder instanceof InventoryDefault inventoryDefault) {
            return "zmenu:" + menuFileName(inventoryDefault);
        }
        return holder == null ? "none" : holder.getClass().getSimpleName();
    }

    private String menuFileName(InventoryEngine inventoryDefault) {
        return inventoryDefault.getMenuInventory() == null ? this.fileName : inventoryDefault.getMenuInventory().getFileName();
    }

    private static String menuFileName(InventoryEngine inventoryDefault, String fallback) {
        return inventoryDefault == null || inventoryDefault.getMenuInventory() == null
            ? fallback
            : inventoryDefault.getMenuInventory().getFileName();
    }

    private static boolean isSupersededClose(UUID playerUuid, Long closeGeneration) {
        return closeGeneration != null && !Objects.equals(closeGeneration, PLAYER_INV_ACTIVE_GENERATIONS.get(playerUuid));
    }

    private void executeCloseActions(Player player, InventoryEngine inventoryDefault, boolean inventorySwitch) {
        var placeholders = new Placeholders();
        if (inventorySwitch) {
            for (Action action : this.closeActions) {
                if (!Configuration.skipCloseActionsOnInventorySwitch.contains(action.getType())) {
                    action.preExecute(player, null, inventoryDefault, placeholders);
                }
            }
        } else {
            this.closeActions.forEach(action -> action.preExecute(player, null, inventoryDefault, placeholders));
        }
    }

    private static ClearInvType resolveClearInvType(InventoryEngine inventoryDefault) {
        if (inventoryDefault.getMenuInventory() instanceof ContainerInventory containerInventory) {
            return containerInventory.getClearInvType();
        }
        return ClearInvType.DEFAULT;
    }

    private static boolean isStoredClearInventory(InventoryEngine inventoryDefault) {
        return inventoryDefault != null
            && inventoryDefault.getMenuInventory() instanceof ContainerInventory containerInventory
            && containerInventory.clearInventory()
            && containerInventory.getClearInvType() == ClearInvType.DEFAULT;
    }

    private static boolean isPacketClearInventory(InventoryEngine inventoryDefault) {
        return inventoryDefault != null
            && inventoryDefault.getMenuInventory() instanceof ContainerInventory containerInventory
            && containerInventory.clearInventory()
            && containerInventory.getClearInvType() == ClearInvType.PACKET_EVENT;
    }

    @Override
    public MenuItemStack getFillItemStack() {
        return this.fillItemStack;
    }

    @Override
    public void setFillItemStack(MenuItemStack fillItemStack) {
        this.fillItemStack = fillItemStack;
    }

    @Override
    public int getUpdateInterval() {
        return this.updateInterval;
    }

    @Override
    public void setUpdateInterval(int updateInterval) {
        this.updateInterval = updateInterval;
    }

    /**
     * @return the size
     */
    public int getSize() {
        return this.size;
    }

    /**
     * @return the file
     */
    public File getFile() {
        return this.file;
    }

    @Override
    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public boolean cleanInventory() {
        return this.clearInventory;
    }

    @Override
    public Requirement getOpenRequirement() {
        return this.openRequirement;
    }

    @Override
    public void setOpenRequirement(Requirement openRequirement) {
        this.openRequirement = openRequirement;
    }

    @Override
    public OpenWithItem getOpenWithItem() {
        return this.openWithItem;
    }

    @Override
    public void setOpenWithItem(OpenWithItem openWithItem) {
        this.openWithItem = openWithItem;
    }

    @Override
    public Map<String, String> getTranslatedNames() {
        return this.translatedNames;
    }

    @Override
    public void setTranslatedNames(Map<String, String> translatedNames) {
        this.translatedNames = translatedNames;
    }

    @Override
    public List<ConditionalName> getConditionalNames() {
        return this.conditionalNames;
    }

    @Override
    public void setClearInventory(boolean clearInventory) {
        this.clearInventory = clearInventory;
    }

    @Override
    public List<Pattern> getPatterns() {
        return this.patterns;
    }

    @Override
    public void setPatterns(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    @Override
    public String getTargetPlayerNamePlaceholder() {
        return this.targetPlayerNamePlaceholder;
    }

    @Override
    public void setTitleAnimation(TitleAnimation load) {
        this.titleAnimation = load;
    }

    @Override
    public TitleAnimation getTitleAnimation() {
        return this.titleAnimation;
    }

    @Override
    public void setTargetPlayerNamePlaceholder(String targetPlaceholder) {
        this.targetPlayerNamePlaceholder = targetPlaceholder;
    }

    @Override
    public List<Action> getOpenActions() {
        return this.openActions;
    }

    @Override
    public void setOpenActions(List<Action> openActions) {
        this.openActions = openActions;
    }

    @Override
    public List<Action> getCloseActions() {
        return this.closeActions;
    }

    @Override
    public void setCloseActions(List<Action> closeActions) {
        this.closeActions = closeActions;
    }

    @Override
    public ClearInvType getClearInvType() {
        return this.clearInvType;
    }

    @Override
    public boolean isClickLimiterEnabled() {
        return this.isClickLimiterEnabled;
    }

    @Override
    public @Nullable InventoryReplacement getInventoryReplacement() {
        return this.inventoryReplacement;
    }

    @Override
    public void setInventoryReplacement(InventoryReplacement inventoryReplacement) {
        this.inventoryReplacement = inventoryReplacement;
    }

    @Override
    public void setClickLimiterEnabled(boolean enabled) {
        this.isClickLimiterEnabled = enabled;
    }

    @Override
    public void setClearInvType(ClearInvType clearInvType) {
        this.clearInvType = clearInvType;
    }
}
