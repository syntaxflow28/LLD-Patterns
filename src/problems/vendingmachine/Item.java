package problems.vendingmachine;

/**
 * A product. {@code code} is the keypad code ("A1"), {@code price} is in whole rupees.
 */
public record Item(String code, String name, int price) {

    public Item {
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }
}
