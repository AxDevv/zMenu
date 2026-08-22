package fr.maxlego08.menu.inventory.zinv;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClearInventoryReconcilerTest {

    @Test
    void staleSnapshotDoesNotDuplicateAlreadyRestoredItems() {
        List<Integer> result = subtract(
            List.of(new TestItem("diamond", 12)),
            List.of(new TestItem("diamond", 12)));

        assertEquals(List.of(0), result);
    }

    @Test
    void staleSnapshotKeepsOnlyTheAdditionalRewardAmount() {
        List<Integer> result = subtract(
            List.of(new TestItem("potion", 7)),
            List.of(new TestItem("potion", 4)));

        assertEquals(List.of(3), result);
    }

    @Test
    void staleSnapshotReconcilesItemsEvenWhenTheirSlotsChanged() {
        List<Integer> result = subtract(
            List.of(new TestItem("emerald", 2), new TestItem("gold", 5)),
            List.of(new TestItem("gold", 5)));

        assertEquals(List.of(2, 0), result);
    }

    @Test
    void consumesSavedStacksAcrossMultipleVisibleStacks() {
        List<Integer> result = subtract(
            List.of(new TestItem("potion", 3), new TestItem("potion", 4)),
            List.of(new TestItem("potion", 5)));

        assertEquals(List.of(0, 2), result);
    }

    @Test
    void differentRewardsAreAlwaysPreserved() {
        List<Integer> result = subtract(
            List.of(new TestItem("heal", 3)),
            List.of(new TestItem("mana", 3)));

        assertEquals(List.of(3), result);
    }

    private static List<Integer> subtract(List<TestItem> visibleItems, List<TestItem> savedItems) {
        return ClearInventoryReconciler.subtractSavedAmounts(
            visibleItems,
            savedItems,
            TestItem::amount,
            (first, second) -> first.type().equals(second.type()));
    }

    private record TestItem(String type, int amount) {
    }
}
