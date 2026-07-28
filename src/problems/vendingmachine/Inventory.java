package problems.vendingmachine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Slot code &rarr; product and remaining quantity.
 *
 * <p>Kept as a separate collaborator rather than fields on {@code VendingMachine} so the machine
 * stays a state machine and the inventory stays a data structure. Two responsibilities, two
 * classes — that is SRP with a concrete justification (restocking rules will change on a different
 * schedule from the purchase flow).
 */
public class Inventory {

    private final Map<String, Item> items = new LinkedHashMap<>();
    private final Map<String, Integer> quantity = new LinkedHashMap<>();

    public void load(Item item, int qty) {
        items.put(item.code(), item);
        quantity.merge(item.code(), qty, Integer::sum);
    }

    public Optional<Item> find(String code) {
        return Optional.ofNullable(items.get(code));
    }

    public int stock(String code) {
        return quantity.getOrDefault(code, 0);
    }

    public void decrement(String code) {
        int left = stock(code);
        if (left <= 0) {
            throw new IllegalStateException("Slot " + code + " is empty");
        }
        quantity.put(code, left - 1);
    }

    /** True when every slot is empty — the trigger for OUT_OF_SERVICE. */
    public boolean isSoldOut() {
        return quantity.values().stream().allMatch(q -> q == 0);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Item item : items.values()) {
            sb.append(String.format("%-3s %-14s Rs.%-4d x%d%n",
                    item.code(), item.name(), item.price(), stock(item.code())));
        }
        return sb.toString();
    }
}
