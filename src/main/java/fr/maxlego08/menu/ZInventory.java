package fr.maxlego08.menu;

import fr.maxlego08.menu.api.Inventory;
import fr.maxlego08.menu.api.MenuItemStack;
import fr.maxlego08.menu.api.animation.TitleAnimation;
import fr.maxlego08.menu.api.button.Button;
import fr.maxlego08.menu.api.button.PaginateButton;
import fr.maxlego08.menu.api.configuration.Configuration;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.engine.InventoryResult;
import fr.maxlego08.menu.api.pattern.Pattern;
import fr.maxlego08.menu.api.players.inventory.InventoriesPlayer;
import fr.maxlego08.menu.api.requirement.Action;
import fr.maxlego08.menu.api.requirement.ConditionalName;
import fr.maxlego08.menu.api.requirement.Requirement;
import fr.maxlego08.menu.api.utils.ClearInvType;
import fr.maxlego08.menu.api.utils.CompatibilityUtil;
import fr.maxlego08.menu.api.utils.OpenWithItem;
import fr.maxlego08.menu.api.utils.Placeholders;
import fr.maxlego08.menu.common.utils.ZUtils;
import fr.maxlego08.menu.inventory.inventories.InventoryDefault;
import fr.maxlego08.menu.zcore.logger.Logger;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class ZInventory extends ZUtils implements Inventory {

    private static final AtomicLong PLAYER_INV_DEBUG_SEQUENCE = new AtomicLong();
    private static final AtomicLong PLAYER_INV_OPEN_SEQUENCE = new AtomicLong();
    private static final Map<InventoryEngine, Long> PLAYER_INV_ENGINE_GENERATIONS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<UUID, Long> PLAYER_INV_ACTIVE_GENERATIONS = new ConcurrentHashMap<>();

    private final Plugin plugin;
    private final String name;
    private final String fileName;
    private final int size;
    private final List<Button> buttons;
    private final List<ConditionalName> conditionalNames = new ArrayList<>();
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
    private String targetPlayerNamePlaceholder;
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
    public String getName(Player player, InventoryEngine inventoryDefault, Placeholders placeholders) {

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

        String locale = findPlayerLocale(player);
        return locale == null ? this.name : this.translatedNames.getOrDefault(locale, this.name);
    }

    @Override
    public InventoryType getType() {
        return type;
    }

    public void setType(InventoryType type) {
        this.type = type;
    }

    @Override
    public boolean shouldCancelItemPickup() {
        return ItemPickupDisabled;
    }

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

        InventoriesPlayer inventoriesPlayer = inventoryDefault.getPlugin().getInventoriesPlayer();
        InventoryHolder holder = CompatibilityUtil.getTopInventory(player).getHolder();
        UUID playerUuid = player.getUniqueId();
        long openGeneration = PLAYER_INV_OPEN_SEQUENCE.incrementAndGet();
        PLAYER_INV_ENGINE_GENERATIONS.put(inventoryDefault, openGeneration);
        PLAYER_INV_ACTIVE_GENERATIONS.put(playerUuid, openGeneration);
        logPlayerInv("OPEN start player=" + player.getName()
            + " uuid=" + playerUuid
            + " openGeneration=" + openGeneration
            + " target=" + menuName(this)
            + " clearInventory=" + this.clearInventory
            + " clearInvType=" + this.clearInvType
            + " holder=" + holderName(holder)
            + " savedBefore=" + inventoriesPlayer.hasSavedInventory(playerUuid)
            + " targetConfiguredPlayerSlots=" + configuredPlayerSlots(this.buttons));

        if (holder instanceof InventoryDefault inventoryHolder) {
            logPlayerInv("OPEN switch detected player=" + player.getName()
                + " from=" + menuName(inventoryHolder.getMenuInventory())
                + " to=" + menuName(this)
                + " fromClearInventory=" + inventoryHolder.getMenuInventory().cleanInventory()
                + " fromClearInvType=" + inventoryHolder.getMenuInventory().getClearInvType()
                + " fromRuntimePlayerSlots=" + runtimePlayerSlots(inventoryHolder)
                + " savedBeforeClear=" + inventoriesPlayer.hasSavedInventory(playerUuid));
            clearPlayerInventoryButtons(player, inventoryHolder);
            logPlayerInv("OPEN after clearing previous player-inv buttons player=" + player.getName()
                + " from=" + menuName(inventoryHolder.getMenuInventory())
                + " savedAfterClear=" + inventoriesPlayer.hasSavedInventory(playerUuid));

            if (inventoryHolder.getMenuInventory().cleanInventory() && !this.clearInventory) {
                logPlayerInv("OPEN restoring saved inventory because previous menu cleared inventory and target does not player="
                    + player.getName() + " from=" + menuName(inventoryHolder.getMenuInventory())
                    + " to=" + menuName(this));
                inventoriesPlayer.giveInventory(player);
            } else if (this.clearInventory) {
                logPlayerInv("OPEN storing inventory for target menu player=" + player.getName()
                    + " target=" + menuName(this)
                    + " storage=" + (this.clearInvType == ClearInvType.DEFAULT ? "permanent" : "temporary")
                    + " savedBeforeStore=" + inventoriesPlayer.hasSavedInventory(playerUuid));
                if (this.clearInvType == ClearInvType.DEFAULT){
                    inventoriesPlayer.storeInventory(player);
                } else {
                    inventoriesPlayer.storeInventoryTemporary(player);
                }
                logPlayerInv("OPEN after store player=" + player.getName()
                    + " target=" + menuName(this)
                    + " savedAfterStore=" + inventoriesPlayer.hasSavedInventory(playerUuid));
            }
        } else if (this.clearInventory) {
            logPlayerInv("OPEN storing inventory from non-zMenu holder player=" + player.getName()
                + " target=" + menuName(this)
                + " holder=" + holderName(holder)
                + " storage=" + (this.clearInvType == ClearInvType.DEFAULT ? "permanent" : "temporary")
                + " savedBeforeStore=" + inventoriesPlayer.hasSavedInventory(playerUuid));
            if (this.clearInvType == ClearInvType.DEFAULT) {
                inventoriesPlayer.storeInventory(player);
            } else {
                inventoriesPlayer.storeInventoryTemporary(player);
            }
            logPlayerInv("OPEN after non-zMenu store player=" + player.getName()
                + " target=" + menuName(this)
                + " savedAfterStore=" + inventoriesPlayer.hasSavedInventory(playerUuid));
        }

        var placeholders = new Placeholders();
        this.openActions.forEach(action -> action.preExecute(player, null, inventoryDefault, placeholders));

        logPlayerInv("OPEN end player=" + player.getName()
            + " target=" + menuName(this)
            + " openGeneration=" + openGeneration
            + " activeGeneration=" + PLAYER_INV_ACTIVE_GENERATIONS.get(playerUuid)
            + " savedEnd=" + inventoriesPlayer.hasSavedInventory(playerUuid));
        return InventoryResult.SUCCESS;
    }

    private void clearPlayerInventoryButtons(Player player, InventoryEngine inventoryDefault) {
        ClearInvType clearInvType = inventoryDefault.getMenuInventory().getClearInvType();
        logPlayerInv("CLEAR player-inv buttons start player=" + player.getName()
            + " menu=" + menuName(inventoryDefault.getMenuInventory())
            + " clearInvType=" + clearInvType
            + " slots=" + runtimePlayerSlots(inventoryDefault));
        for (Button button : inventoryDefault.getButtons()) {
            if (button.isPlayerInventory()) {
                for (int slot : button.getSlots()) {
                    if (slot >= 0 && slot <= 36) {
                        logPlayerInv("CLEAR slot player=" + player.getName()
                            + " menu=" + menuName(inventoryDefault.getMenuInventory())
                            + " button=" + button.getName()
                            + " slot=" + slot
                            + " before=" + itemDescription(player.getInventory().getItem(slot))
                            + " clearInvType=" + clearInvType);
                        clearInvType.getOnButtonClear().accept(player, slot);
                        logPlayerInv("CLEAR slot done player=" + player.getName()
                            + " menu=" + menuName(inventoryDefault.getMenuInventory())
                            + " button=" + button.getName()
                            + " slot=" + slot
                            + " after=" + itemDescription(player.getInventory().getItem(slot))
                            + " clearInvType=" + clearInvType);
                    }
                }
            }
        }
        logPlayerInv("CLEAR player-inv buttons end player=" + player.getName()
            + " menu=" + menuName(inventoryDefault.getMenuInventory())
            + " clearInvType=" + clearInvType);
    }

    @Override
    public void postOpenInventory(Player player, InventoryEngine inventoryDefault) {

    }

    @Override
    public void closeInventory(Player player, InventoryEngine inventoryDefault) {

        UUID playerUuid = player.getUniqueId();
        long sequence = PLAYER_INV_DEBUG_SEQUENCE.incrementAndGet();
        Long closeGeneration = PLAYER_INV_ENGINE_GENERATIONS.get(inventoryDefault);
        Long activeGenerationAtSchedule = PLAYER_INV_ACTIVE_GENERATIONS.get(playerUuid);
        boolean savedAtSchedule = inventoryDefault.getPlugin().getInventoriesPlayer().hasSavedInventory(playerUuid);
        logPlayerInv("CLOSE schedule player=" + player.getName()
            + " uuid=" + playerUuid
            + " seq=" + sequence
            + " closeGeneration=" + closeGeneration
            + " activeGenerationAtSchedule=" + activeGenerationAtSchedule
            + " menu=" + menuName(this)
            + " clearInventory=" + this.clearInventory
            + " clearInvType=" + this.clearInvType
            + " runtimePlayerSlots=" + runtimePlayerSlots(inventoryDefault)
            + " holderNow=" + holderName(CompatibilityUtil.getTopInventory(player).getHolder())
            + " savedAtSchedule=" + savedAtSchedule);
        ZMenuPlugin.getInstance().getScheduler().runAtEntityLater(player, task -> {
            InventoryHolder newHolder = CompatibilityUtil.getTopInventory(player).getHolder();
            boolean isInNewzMenuInventory = newHolder instanceof InventoryDefault;
            InventoriesPlayer inventoriesPlayer = inventoryDefault.getPlugin().getInventoriesPlayer();
            Long activeGenerationAtDelayed = PLAYER_INV_ACTIVE_GENERATIONS.get(playerUuid);
            boolean supersededClose = closeGeneration != null && !Objects.equals(closeGeneration, activeGenerationAtDelayed);
            boolean savedBeforeDelayed = inventoriesPlayer.hasSavedInventory(playerUuid);
            logPlayerInv("CLOSE delayed start player=" + player.getName()
                + " seq=" + sequence
                + " closeGeneration=" + closeGeneration
                + " activeGenerationAtDelayed=" + activeGenerationAtDelayed
                + " supersededClose=" + supersededClose
                + " menu=" + menuName(this)
                + " newHolder=" + holderName(newHolder)
                + " isInNewzMenuInventory=" + isInNewzMenuInventory
                + " savedBeforeDelayed=" + savedBeforeDelayed);
            if (supersededClose) {
                logPlayerInv("CLOSE delayed superseded skip cleanup player=" + player.getName()
                    + " seq=" + sequence
                    + " menu=" + menuName(this)
                    + " closeGeneration=" + closeGeneration
                    + " activeGenerationAtDelayed=" + activeGenerationAtDelayed
                    + " slotsNotCleared=" + runtimePlayerSlots(inventoryDefault)
                    + " savedSnapshotKept=" + inventoriesPlayer.hasSavedInventory(playerUuid));
                var placeholders = new Placeholders();
                logPlayerInv("CLOSE delayed executing switch close-actions player=" + player.getName()
                    + " seq=" + sequence
                    + " menu=" + menuName(this)
                    + " reason=superseded-generation"
                    + " skippedTypes=" + Configuration.skipCloseActionsOnInventorySwitch);
                for (Action action : this.closeActions) {
                    if (!Configuration.skipCloseActionsOnInventorySwitch.contains(action.getType())) {
                        action.preExecute(player, null, inventoryDefault, placeholders);
                    }
                }
                PLAYER_INV_ENGINE_GENERATIONS.remove(inventoryDefault);
                logPlayerInv("CLOSE delayed end player=" + player.getName()
                    + " seq=" + sequence
                    + " menu=" + menuName(this)
                    + " reason=superseded-generation"
                    + " activeGenerationEnd=" + PLAYER_INV_ACTIVE_GENERATIONS.get(playerUuid)
                    + " savedEnd=" + inventoriesPlayer.hasSavedInventory(playerUuid));
                return;
            }
            if (newHolder != null && !(newHolder instanceof InventoryDefault)) {
                logPlayerInv("CLOSE delayed final-close branch player=" + player.getName()
                    + " seq=" + sequence
                    + " closeGeneration=" + closeGeneration
                    + " menu=" + menuName(this)
                    + " slotsToClear=" + runtimePlayerSlots(inventoryDefault)
                    + " willRestore=" + this.clearInventory);
                clearPlayerInventoryButtons(player, inventoryDefault);

                if (this.clearInventory) {
                    logPlayerInv("CLOSE delayed restoring saved inventory player=" + player.getName()
                        + " seq=" + sequence
                        + " menu=" + menuName(this)
                        + " savedBeforeRestore=" + inventoriesPlayer.hasSavedInventory(player.getUniqueId())
                        + " clearInvType=" + this.clearInvType);
                    this.clearInvType.getOnInventoryClose().accept(inventoriesPlayer, player);
                    logPlayerInv("CLOSE delayed restore finished player=" + player.getName()
                        + " seq=" + sequence
                        + " menu=" + menuName(this)
                        + " savedAfterRestore=" + inventoriesPlayer.hasSavedInventory(player.getUniqueId()));
                }
            } else {
                logPlayerInv("CLOSE delayed skipped final cleanup player=" + player.getName()
                    + " seq=" + sequence
                    + " closeGeneration=" + closeGeneration
                    + " menu=" + menuName(this)
                    + " reason=" + (newHolder == null ? "null-holder" : "new-zmenu-holder"));
            }
            var placeholders = new Placeholders();
            if (isInNewzMenuInventory) {
                logPlayerInv("CLOSE delayed executing switch close-actions player=" + player.getName()
                    + " seq=" + sequence
                    + " menu=" + menuName(this)
                    + " skippedTypes=" + Configuration.skipCloseActionsOnInventorySwitch);
                for (Action action : this.closeActions) {
                    if (!Configuration.skipCloseActionsOnInventorySwitch.contains(action.getType())) {
                        action.preExecute(player, null, inventoryDefault, placeholders);
                    }
                }
            } else {
                logPlayerInv("CLOSE delayed executing final close-actions player=" + player.getName()
                    + " seq=" + sequence
                    + " menu=" + menuName(this)
                    + " actionCount=" + this.closeActions.size());
                this.closeActions.forEach(action -> action.preExecute(player, null, inventoryDefault, placeholders));
            }
            if (!isInNewzMenuInventory && closeGeneration != null) {
                PLAYER_INV_ACTIVE_GENERATIONS.remove(playerUuid, closeGeneration);
            }
            PLAYER_INV_ENGINE_GENERATIONS.remove(inventoryDefault);
            logPlayerInv("CLOSE delayed end player=" + player.getName()
                + " seq=" + sequence
                + " menu=" + menuName(this)
                + " activeGenerationEnd=" + PLAYER_INV_ACTIVE_GENERATIONS.get(playerUuid)
                + " savedEnd=" + inventoriesPlayer.hasSavedInventory(player.getUniqueId()));
        }, 1);

    }

    private void logPlayerInv(String message) {
        Logger.info("[PlayerInvDebug] " + message, Logger.LogType.WARNING);
    }

    private String menuName(Inventory inventory) {
        if (inventory == null) {
            return "null";
        }
        return inventory.getFileName() + "/" + inventory.getName();
    }

    private String holderName(InventoryHolder holder) {
        if (holder == null) {
            return "null";
        }
        if (holder instanceof InventoryDefault inventoryDefault) {
            return "InventoryDefault(" + menuName(inventoryDefault.getMenuInventory()) + ",page=" + inventoryDefault.getPage() + ")";
        }
        return holder.getClass().getName();
    }

    private String runtimePlayerSlots(InventoryEngine inventoryDefault) {
        TreeSet<Integer> slots = new TreeSet<>();
        for (Button button : inventoryDefault.getButtons()) {
            if (button.isPlayerInventory()) {
                slots.addAll(button.getSlots());
            }
        }
        return slots.toString();
    }

    private String configuredPlayerSlots(Collection<Button> buttons) {
        TreeSet<Integer> slots = new TreeSet<>();
        for (Button button : buttons) {
            if (button.isPlayerInventory()) {
                slots.addAll(button.getSlots());
            }
        }
        return slots.toString();
    }

    private String itemDescription(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "AIR";
        }
        return itemStack.getType().name() + "x" + itemStack.getAmount();
    }

    @Override
    public MenuItemStack getFillItemStack() {
        return this.fillItemStack;
    }

    public void setFillItemStack(MenuItemStack fillItemStack) {
        this.fillItemStack = fillItemStack;
    }

    @Override
    public int getUpdateInterval() {
        return this.updateInterval;
    }

    public void setUpdateInterval(int updateInterval) {
        this.updateInterval = updateInterval;
    }

    /**
     * @return the size
     */
    public int getSize() {
        return size;
    }

    /**
     * @return the file
     */
    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    @Override
    public boolean cleanInventory() {
        return clearInventory;
    }

    @Override
    public Requirement getOpenRequirement() {
        return this.openRequirement;
    }

    public void setOpenRequirement(Requirement openRequirement) {
        this.openRequirement = openRequirement;
    }

    @Override
    public OpenWithItem getOpenWithItem() {
        return this.openWithItem;
    }

    public void setOpenWithItem(OpenWithItem openWithItem) {
        this.openWithItem = openWithItem;
    }

    @Override
    public Map<String, String> getTranslatedNames() {
        return translatedNames;
    }

    public void setTranslatedNames(Map<String, String> translatedNames) {
        this.translatedNames = translatedNames;
    }

    @Override
    public List<ConditionalName> getConditionalNames() {
        return this.conditionalNames;
    }

    public void setClearInventory(boolean clearInventory) {
        this.clearInventory = clearInventory;
    }

    @Override
    public List<Pattern> getPatterns() {
        return patterns;
    }

    public void setPatterns(List<Pattern> patterns) {
        this.patterns = patterns;
    }

    @Override
    public String getTargetPlayerNamePlaceholder() {
        return targetPlayerNamePlaceholder;
    }

    @Override
    public void setTitleAnimation(TitleAnimation load) {
        this.titleAnimation = load;
    }

    @Override
    public TitleAnimation getTitleAnimation() {
        return this.titleAnimation;
    }

    public void setTargetPlayerNamePlaceholder(String targetPlaceholder) {
        this.targetPlayerNamePlaceholder = targetPlaceholder;
    }

    @Override
    public List<Action> getOpenActions() {
        return openActions;
    }

    public void setOpenActions(List<Action> openActions) {
        this.openActions = openActions;
    }

    @Override
    public List<Action> getCloseActions() {
        return closeActions;
    }

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

    public void setClickLimiterEnabled(boolean enabled) {
        this.isClickLimiterEnabled = enabled;
    }

    public void setClearInvType(ClearInvType clearInvType) {
        this.clearInvType = clearInvType;
    }
}
