package com.lld.problems.vendingmachine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The coin hopper: what the machine physically holds, and therefore what change it can give.
 *
 * <p><b>This is the part candidates forget.</b> "Return change" is not arithmetic — the machine can
 * only return coins it actually has. A customer who inserts Rs.50 for a Rs.35 item needs Rs.15 back,
 * which is impossible if the hopper only has fifties. That check has to happen at
 * <em>selection</em> time, not after the item has already dropped.
 *
 * <p>The algorithm is greedy (largest coin first). Greedy is only optimal for
 * <em>canonical</em> coin systems — 5/10/20/50 is canonical, so it is correct here. If the
 * interviewer proposes denominations like {1, 3, 4} and asks for 6, greedy gives 4+1+1 (three
 * coins) where the optimum is 3+3 (two). Then you need the coin-change DP. Naming this limitation
 * unprompted is a strong signal.
 */
public class CoinBank {

    private static final List<Coin> LARGEST_FIRST = Arrays.stream(Coin.values())
            .sorted(Comparator.comparingInt(Coin::value).reversed())
            .toList();

    private final Map<Coin, Integer> counts = new EnumMap<>(Coin.class);

    public void deposit(Coin coin) {
        counts.merge(coin, 1, Integer::sum);
    }

    public void deposit(Coin coin, int howMany) {
        counts.merge(coin, howMany, Integer::sum);
    }

    public boolean canMake(int amount) {
        return plan(amount).isPresent();
    }

    /** Removes coins totalling {@code amount}, or removes nothing and returns empty. */
    public Optional<List<Coin>> withdraw(int amount) {
        Optional<List<Coin>> plan = plan(amount);
        plan.ifPresent(coins -> coins.forEach(coin -> counts.merge(coin, -1, Integer::sum)));
        return plan;
    }

    public int total() {
        return counts.entrySet().stream()
                .mapToInt(e -> e.getKey().value() * e.getValue())
                .sum();
    }

    /** Pure: works out <em>whether</em> the payout is possible without mutating the hopper. */
    private Optional<List<Coin>> plan(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (amount == 0) {
            return Optional.of(List.of());
        }

        List<Coin> chosen = new ArrayList<>();
        Map<Coin, Integer> available = new EnumMap<>(counts);
        int remaining = amount;

        for (Coin coin : LARGEST_FIRST) {
            while (remaining >= coin.value() && available.getOrDefault(coin, 0) > 0) {
                remaining -= coin.value();
                available.merge(coin, -1, Integer::sum);
                chosen.add(coin);
            }
        }
        return remaining == 0 ? Optional.of(List.copyOf(chosen)) : Optional.empty();
    }

    @Override
    public String toString() {
        return counts.toString() + " = Rs." + total();
    }
}
