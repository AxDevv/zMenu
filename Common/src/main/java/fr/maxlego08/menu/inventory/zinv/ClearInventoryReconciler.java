package fr.maxlego08.menu.inventory.zinv;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

final class ClearInventoryReconciler {

    private static final int OFF_HAND_SLOT = 40;

    private ClearInventoryReconciler() {
    }

    static List<ItemStack> collectSessionItems(ItemStack[] storageContents,
                                               ItemStack offHand,
                                               Set<Integer> menuSlots,
                                               Predicate<ItemStack> menuItemPredicate) {
        List<ItemStack> sessionItems = collectVisibleItems(storageContents, offHand, menuSlots, menuItemPredicate);
        return sessionItems.stream().map(ItemStack::clone).toList();
    }

    static List<ItemStack> collectOrphanedSessionItems(ItemStack[] storageContents,
                                                       ItemStack offHand,
                                                       Set<Integer> menuSlots,
                                                       Predicate<ItemStack> menuItemPredicate,
                                                       Collection<ItemStack> savedItems) {
        List<ItemStack> visibleItems = collectVisibleItems(storageContents, offHand, menuSlots, menuItemPredicate)
            .stream()
            .map(ItemStack::clone)
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        List<Integer> remainingAmounts = subtractSavedAmounts(
            visibleItems,
            savedItems.stream().filter(itemStack -> !isEmpty(itemStack)).toList(),
            ItemStack::getAmount,
            ItemStack::isSimilar);

        List<ItemStack> orphanedItems = new ArrayList<>();
        for (int index = 0; index < visibleItems.size(); index++) {
            int remainingAmount = remainingAmounts.get(index);
            if (remainingAmount > 0) {
                ItemStack orphanedItem = visibleItems.get(index);
                orphanedItem.setAmount(remainingAmount);
                orphanedItems.add(orphanedItem);
            }
        }
        return orphanedItems;
    }

    static <T> List<Integer> subtractSavedAmounts(List<T> visibleItems,
                                                  Collection<T> savedItems,
                                                  ToIntFunction<T> amountFunction,
                                                  BiPredicate<T, T> similarityPredicate) {
        List<Integer> remainingAmounts = visibleItems.stream().map(amountFunction::applyAsInt).collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        for (T savedItem : savedItems) {
            int remainingSavedAmount = amountFunction.applyAsInt(savedItem);
            for (int index = 0; index < visibleItems.size() && remainingSavedAmount > 0; index++) {
                int visibleAmount = remainingAmounts.get(index);
                if (visibleAmount == 0 || !similarityPredicate.test(visibleItems.get(index), savedItem)) continue;
                int matchedAmount = Math.min(visibleAmount, remainingSavedAmount);
                remainingAmounts.set(index, visibleAmount - matchedAmount);
                remainingSavedAmount -= matchedAmount;
            }
        }
        return remainingAmounts;
    }

    private static List<ItemStack> collectVisibleItems(ItemStack[] storageContents,
                                                       ItemStack offHand,
                                                       Set<Integer> menuSlots,
                                                       Predicate<ItemStack> menuItemPredicate) {
        List<ItemStack> visibleItems = new ArrayList<>();
        for (int slot = 0; slot < storageContents.length; slot++) {
            if (menuSlots.contains(slot)) continue;
            addIfPlayerItem(visibleItems, storageContents[slot], menuItemPredicate);
        }
        if (!menuSlots.contains(OFF_HAND_SLOT)) {
            addIfPlayerItem(visibleItems, offHand, menuItemPredicate);
        }
        return visibleItems;
    }

    private static void addIfPlayerItem(List<ItemStack> items,
                                        ItemStack itemStack,
                                        Predicate<ItemStack> menuItemPredicate) {
        if (!isEmpty(itemStack) && !menuItemPredicate.test(itemStack)) {
            items.add(itemStack);
        }
    }

    private static boolean isEmpty(ItemStack itemStack) {
        return itemStack == null || itemStack.getType().isAir();
    }
}
