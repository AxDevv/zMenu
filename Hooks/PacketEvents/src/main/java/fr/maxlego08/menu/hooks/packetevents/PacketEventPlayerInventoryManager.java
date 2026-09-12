package fr.maxlego08.menu.hooks.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPlayerInventory;
import fr.maxlego08.menu.api.InventoryListener;
import fr.maxlego08.menu.api.MenuPlugin;
import fr.maxlego08.menu.api.engine.BaseInventory;
import fr.maxlego08.menu.api.engine.InventoryEngine;
import fr.maxlego08.menu.api.engine.ItemButton;
import fr.maxlego08.menu.api.inventory.ContainerInventory;
import fr.maxlego08.menu.api.players.inventory.InventoriesPlayer;
import fr.maxlego08.menu.api.utils.ClearInvType;
import fr.maxlego08.menu.common.utils.nms.NMSUtils;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PacketEventPlayerInventoryManager implements InventoryListener {
    private final MenuPlugin plugin;
    private final Map<UUID, Map<Integer,WrapperPlayServerSetPlayerInventory>> pendingInventoryUpdates = new HashMap<>();

    public PacketEventPlayerInventoryManager(MenuPlugin plugin) {
        this.plugin = plugin;
        ClearInvType packetEvent = ClearInvType.PACKET_EVENT;
        packetEvent.setOnButtonClear((player, slot)->this.addItemInstantly(player,slot,new ItemStack(Material.AIR)));
        packetEvent.setOnInventoryClose(this::restoreProjectedInventory);
        packetEvent.setRemoveItem((player, slot, playerInventory) -> this.addItemLater(player, slot, new ItemStack(Material.AIR)));
    }

    public boolean addItem(BaseInventory baseInventory, boolean inPlayerInventory, ItemButton itemButton, boolean enableAntiDupe){
        if (baseInventory.getClearInvType() == ClearInvType.PACKET_EVENT && inPlayerInventory && baseInventory.getPlayer() != null) {
            WrapperPlayServerSetPlayerInventory wrapperPlayServerSetPlayerInventory = new WrapperPlayServerSetPlayerInventory(itemButton.getSlot(), SpigotConversionUtil.fromBukkitItemStack(itemButton.getDisplayItem()));
            this.pendingInventoryUpdates.computeIfAbsent(baseInventory.getPlayer().getUniqueId(), k -> new HashMap<>()).put(itemButton.getSlot(), wrapperPlayServerSetPlayerInventory);
            return true;
        }
        return false;
    }

    public void onInventoryPreOpen(Player player, BaseInventory baseInventory, int page, Object... objects){
        this.pendingInventoryUpdates.remove(player.getUniqueId());
    }

    public void onInventoryPostOpen(Player player, BaseInventory baseInventory){
        UUID playerUniqueId = player.getUniqueId();
        if (this.pendingInventoryUpdates.containsKey(playerUniqueId)) {
            this.plugin.getScheduler().runAtEntityLater(player, () -> {
                Map<Integer,WrapperPlayServerSetPlayerInventory> wrappers = this.pendingInventoryUpdates.get(playerUniqueId);
                if (wrappers != null) {
                    for (WrapperPlayServerSetPlayerInventory wrapper : wrappers.values()) {
                        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapper);
                    }
                    this.pendingInventoryUpdates.remove(playerUniqueId);
                }
            }, 1);
        }
    }

    @Override
    public void onInventorySwitch(Player player, InventoryEngine oldInventoryEngine, InventoryEngine newInventoryEngine) {
        if (usesPacketProjection(oldInventoryEngine) && !usesPacketProjection(newInventoryEngine)) {
            restoreProjectedInventory(this.plugin.getInventoriesPlayer(), player);
        }
    }

    public void onButtonClick(Player player, ItemButton button){
        if (button.isInPlayerInventory() && button.getBaseInventory().getClearInvType() == ClearInvType.PACKET_EVENT) {
            this.addItemInstantly(player, button.getSlot(), button.getDisplayItem());
            BaseInventory clickedInventory = button.getBaseInventory();
            int clickedSlot = button.getSlot();
            this.plugin.getScheduler().runAtEntityLater(player, () -> {
                if (!player.isOnline()
                    || player.getOpenInventory().getTopInventory().getHolder() != clickedInventory) {
                    return;
                }
                ItemButton currentButton = clickedInventory.getPlayerInventoryItems().get(clickedSlot);
                if (currentButton != null) {
                    this.addItemInstantly(player, clickedSlot, currentButton.getDisplayItem());
                }
            }, 1);
        }
    }

    public void addItemInstantly(@NotNull Player player, int slot, @NotNull ItemStack itemStack){
        WrapperPlayServerSetPlayerInventory wrapper = new WrapperPlayServerSetPlayerInventory(slot, itemStack.getType() == Material.AIR ? null : SpigotConversionUtil.fromBukkitItemStack(itemStack));
        PacketEvents.getAPI().getPlayerManager().sendPacket(player, wrapper);
    }

    public void addItemLater(@NotNull Player player, int slot, @NotNull ItemStack itemStack){
        this.pendingInventoryUpdates.computeIfAbsent(player.getUniqueId(), k -> new HashMap<>()).put(slot, new WrapperPlayServerSetPlayerInventory(slot, itemStack.getType() == Material.AIR ? null : SpigotConversionUtil.fromBukkitItemStack(itemStack)));
    }

    private void restoreProjectedInventory(InventoriesPlayer inventoriesPlayer, Player player) {
        UUID playerUuid = player.getUniqueId();
        this.pendingInventoryUpdates.remove(playerUuid);
        var playerInventory = player.getInventory();
        ItemStack[] storageContents = playerInventory.getStorageContents();
        for (int slot = 0; slot < storageContents.length; slot++) {
            ItemStack itemStack = storageContents[slot];
            this.addItemInstantly(player, slot, itemStack == null ? new ItemStack(Material.AIR) : itemStack);
        }
        if (!NMSUtils.isOneHand()) {
            ItemStack offHand = playerInventory.getItemInOffHand();
            this.addItemInstantly(player, 40, offHand.getType() == Material.AIR ? new ItemStack(Material.AIR) : offHand);
        }
        inventoriesPlayer.getPlayerInventory(playerUuid)
            .filter(inventory -> !inventory.isPermanent())
            .ifPresent(inventory -> inventoriesPlayer.clearInventorie(playerUuid));
    }

    private static boolean usesPacketProjection(BaseInventory inventory) {
        return inventory instanceof InventoryEngine inventoryEngine
            && inventoryEngine.getMenuInventory() instanceof ContainerInventory containerInventory
            && containerInventory.clearInventory()
            && containerInventory.getClearInvType() == ClearInvType.PACKET_EVENT;
    }
}
