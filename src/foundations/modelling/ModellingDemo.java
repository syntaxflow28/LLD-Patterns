package foundations.modelling;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Modelling intuition, reproduced rather than described.
 *
 * <p>Companion to <a href="../../../docs/1-foundations/2-modelling.md">docs/1-foundations/2-modelling.md</a>.
 * Each section breaks a modelling rule and shows the failure actually happening, then shows the model
 * that makes the failure impossible instead of merely detected.
 */
public final class ModellingDemo {

    private ModellingDemo() {
    }

    public static void main(String[] args) {
        primitiveObsession();
        substitutability();
        subclassExplosion();
        whenAnInterfaceEarnsItsPlace();
        tellDontAsk();
        illegalStates();
        ownership();
        summary();
        System.out.println("\nDone.");
    }

    // ---------------------------------------------------------------- 1

    private static void primitiveObsession() {
        section("1. Two primitives that should have been one class");

        long usdMinorUnits = 350;    // $3.50
        long inrMinorUnits = 25_000; // Rs 250.00

        long nonsense = usdMinorUnits + inrMinorUnits;
        System.out.println("  loose primitives:  350 (USD cents) + 25000 (INR paise) = " + nonsense);
        System.out.printf("  printed as money:  $%d.%02d   <- the compiler was happy, the number is meaningless%n",
                nonsense / 100, nonsense % 100);

        Money coffee = Money.of("USD", 3, 50);
        Money chai = Money.of("INR", 250, 0);
        System.out.println("  value objects:     " + coffee + " + " + chai);
        try {
            coffee.plus(chai);
            System.out.println("  ERROR: the mixed-currency addition was allowed");
        } catch (IllegalArgumentException rejected) {
            System.out.println("  rejected:          " + rejected.getMessage());
        }
        System.out.println("  same currency:     " + coffee + " + " + Money.of("USD", 1, 25)
                + " = " + coffee.plus(Money.of("USD", 1, 25)));

        System.out.println("""

                  The trigger for promoting primitives to a value object: two of them always travel
                  together, OR there is a rule about them with nowhere to live. Both were true here.

                  Say the field names out loud. If they form a phrase - amount + currency = "money",
                  start + end = "a time slot", street + city + zip = "an address" - the phrase is the
                  class you are missing.

                  Note what changed: the check did not move, it stopped being a check. Nobody has to
                  remember to validate, because the meaningless state cannot be constructed.\
                """);
    }

    // ---------------------------------------------------------------- 2

    private static void substitutability() {
        section("2. Is-a in English, is-not-a in code");

        System.out.println("  A caller written correctly against Rectangle:");
        System.out.println("      void resize(Rectangle r) { r.setWidth(5); r.setHeight(4); }");
        System.out.println("      ...and it expects area == 20.\n");

        Rectangle rectangle = new Rectangle();
        resize(rectangle);
        System.out.printf("      Rectangle -> %d x %d = %d%n",
                rectangle.width(), rectangle.height(), rectangle.area());

        Square square = new Square();
        resize(square);
        System.out.printf("      Square    -> %d x %d = %d   <- the caller is not wrong, the hierarchy is%n",
                square.width(), square.height(), square.area());

        System.out.println("""

                  Rectangle makes a promise that appears in no signature: width and height move
                  independently. Square cannot keep it. Inheritance inherits the promises, not just
                  the fields - which is why "a square is a rectangle" being true in geometry is
                  irrelevant.

                  The one-second test: can I hand this subclass to EVERY piece of code that expects
                  the parent, with no caller ever checking the type? One 'instanceof' needed anywhere
                  means no. That is the Liskov Substitution Principle as something you can check.

                  The fix is composition: a square HAS-A side length. Give both a Shape interface
                  exposing area() and no mutable width, and the impossible promise is never made.\
                """);
    }

    /** Perfectly correct against the published contract of {@link Rectangle}. */
    private static void resize(Rectangle rectangle) {
        rectangle.setWidth(5);
        rectangle.setHeight(4);
    }

    // ---------------------------------------------------------------- 3

    private static void subclassExplosion() {
        section("3. n independent options: 2^n subclasses, or n classes");

        System.out.println("      options   subclasses (2^n)   decorator classes (n+2)");
        for (int n = 1; n <= 6; n++) {
            System.out.printf("      %5d   %16d   %23d%n", n, 1 << n, n + 2);
        }

        Beverage order = new AddOn(
                new AddOn(
                        new AddOn(new Coffee(Money.of("USD", 2, 0)), "milk", Money.of("USD", 0, 50)),
                        "sugar", Money.of("USD", 0, 20)),
                "cream", Money.of("USD", 0, 75));
        System.out.println("\n      built at runtime: " + order.description() + " = " + order.cost());
        System.out.println("      classes written for this: Beverage, Coffee, AddOn = 3, for any"
                + " number of options");

        System.out.println("""

                  The inheritance version declares combinations at compile time, so every new option
                  doubles the class count and whipped cream is the 32nd class. Composition builds
                  them at runtime, so a new option is one class - or here, zero, because AddOn is
                  data-driven.

                  That is Decorator, but notice the route: nobody needed its name. Asking "what
                  changes independently of what?" produced it. The pattern is the destination, not
                  the route, and being able to derive it beats being able to recite it.

                  The shape worth memorising is "implements X and holds an X". Recognise it and you
                  have recognised Decorator, Composite and Proxy at the same time.\
                """);
    }

    // ---------------------------------------------------------------- 4

    private static void whenAnInterfaceEarnsItsPlace() {
        section("4. When an interface earns its place");

        Instant now = Instant.parse("2026-03-02T10:00:00Z");
        Instant expiresAt = now.plus(Duration.ofMinutes(15));

        Clock atBooking = Clock.fixed(now, ZoneOffset.UTC);
        Clock sixteenMinutesLater = Clock.fixed(now.plus(Duration.ofMinutes(16)), ZoneOffset.UTC);

        long startedAt = System.nanoTime();
        boolean expiredNow = isExpired(expiresAt, atBooking);
        boolean expiredLater = isExpired(expiresAt, sixteenMinutesLater);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        System.out.println("  a 15-minute seat hold, tested at both ends of its life:");
        System.out.println("      at booking time      expired = " + expiredNow);
        System.out.println("      16 minutes later     expired = " + expiredLater);
        System.out.println("      wall-clock time spent: " + elapsedMillis + " ms");
        System.out.println("  With Clock.systemUTC() hard-coded inside, that second assertion costs"
                + " 16 real minutes.");

        System.out.println("""

                  Four reasons justify an interface, and they are the only four:
                    1. two or more real implementations TODAY
                    2. the requirements named a second one  ("later we'll add UPI")
                    3. it crosses a boundary you must fake in a test  (clock, network, DB, payment)
                    4. it is the seam keeping the domain from seeing infrastructure  (Repository)

                  Clock qualifies on reason 3 with one implementation, because the seam is the point.
                  A SlugGenerator used only inside the domain, with one implementation and no second
                  hinted at, qualifies on none - that interface is speculative generality.

                  Saying NO out loud is the stronger signal:
                    "I could put a MoveStrategy behind this, but there's one rule and none was
                     hinted at. I'll keep it concrete and extract an interface the moment there's a
                     second - that refactor is ten seconds, and a wrong abstraction costs far more
                     than a late one."

                  Name interfaces after the role: FeeStrategy, SpotAllocator. If you cannot name one
                  without "manager", "helper" or "util", you have not found the abstraction yet.\
                """);
    }

    private static boolean isExpired(Instant expiresAt, Clock clock) {
        return !clock.instant().isBefore(expiresAt);
    }

    // ---------------------------------------------------------------- 5

    private static void tellDontAsk() {
        section("5. Tell, don't ask - and what the getter chain really costs");

        Customer withAddress = new Customer("Priya", new Address("12 MG Road", "Bengaluru", "IN"));
        Customer notYetOnboarded = new Customer("Rahul", null);

        Order domestic = new Order(withAddress, Money.of("INR", 1200, 0));
        Order incomplete = new Order(notYetOnboarded, Money.of("INR", 900, 0));

        System.out.println("  ask:   order.customer().address().country().equals(\"IN\")");
        System.out.println("      complete customer   -> "
                + domestic.customer().address().country().equals("IN"));
        try {
            boolean result = incomplete.customer().address().country().equals("IN");
            System.out.println("      incomplete customer -> " + result);
        } catch (NullPointerException broken) {
            System.out.println("      incomplete customer -> NullPointerException"
                    + "   <- four dots, four chances to be null");
        }

        System.out.println("\n  tell:  order.isDomestic()");
        System.out.println("      complete customer   -> " + domestic.isDomestic());
        System.out.println("      incomplete customer -> " + incomplete.isDomestic()
                + "   <- the null check lives once, on the object that owns the field");

        System.out.println("""

                  Law of Demeter, stated usefully: a method may call methods on itself, its own
                  fields, its parameters, and objects it just created. One dot of navigation into
                  someone else's object graph, not four.

                  The chain breaks if Customer changes how it stores an address, if Address renames
                  country(), or if any link is null. isDomestic() breaks only if the CONCEPT of
                  domestic changes. The first is coupled to three classes; the second to one.

                  It is a heuristic, not a law - fluent builders and streams break it happily,
                  because they return the same conceptual thing. The rule is about navigating other
                  people's graphs.

                  The payoff: once nobody calls address(), you can delete it. Every getter you
                  delete is a coupling you delete.\
                """);
    }

    // ---------------------------------------------------------------- 6

    private static void illegalStates() {
        section("6. Three booleans encode eight states; you meant four");

        System.out.println("      isSubmitted  isApproved  isPaid   meaningful?");
        boolean[] values = {false, true};
        int legal = 0;
        int total = 0;
        for (boolean submitted : values) {
            for (boolean approved : values) {
                for (boolean paid : values) {
                    total++;
                    boolean meaningful = (!paid || approved) && (!approved || submitted);
                    if (meaningful) {
                        legal++;
                    }
                    System.out.printf("      %-11b  %-10b  %-7b  %s%n",
                            submitted, approved, paid, meaningful ? "yes" : "NO - unrepresentable state");
                }
            }
        }
        System.out.println("      " + total + " combinations, " + legal + " meaningful, "
                + (total - legal) + " that every method reading them has to interpret");

        Order order = new Order(new Customer("Meera", new Address("7 Park St", "Kolkata", "IN")),
                Money.of("INR", 4500, 0));
        System.out.println("\n  with an enum, the illegal states stop existing:");
        System.out.println("      status: " + order.status());
        order.advanceTo(Order.Status.SUBMITTED);
        System.out.println("      status: " + order.status());
        try {
            order.advanceTo(Order.Status.PAID);
            System.out.println("      ERROR: paid without approval was allowed");
        } catch (IllegalStateException rejected) {
            System.out.println("      rejected: " + rejected.getMessage());
        }
        order.advanceTo(Order.Status.APPROVED);
        order.advanceTo(Order.Status.PAID);
        System.out.println("      status: " + order.status());

        System.out.println("""

                  n booleans on a class is a question about which combinations are legal. If the
                  answer is not "all of them", they wanted to be an enum or a sealed hierarchy.

                  The general rule this is an instance of: make illegal states unrepresentable. The
                  strongest encapsulation is not a private field, it is a design where the bad state
                  cannot be constructed - a compact constructor that rejects end <= start, an enum
                  instead of a String status, a constructor that requires the field you kept
                  null-checking.\
                """);
    }

    // ---------------------------------------------------------------- 7

    private static void ownership() {
        section("7. Ownership: who constructs, who validates, who may mutate");

        Floor leaky = new Floor(1, 5);
        System.out.println("  floor built with " + leaky.size() + " spots: " + leaky.spots());
        List<Spot> live = leaky.spotsUnsafe();
        live.clear();
        System.out.println("  after a caller does floor.spotsUnsafe().clear():");
        System.out.println("      floor.size()        = " + leaky.size() + "   <- the floor lost its"
                + " spots and could not stop it");
        System.out.println("      floor.findFreeSpot() = " + leaky.findFreeSpot());

        Floor safe = new Floor(2, 5);
        try {
            safe.spots().clear();
            System.out.println("  ERROR: the defensive copy was mutable");
        } catch (UnsupportedOperationException rejected) {
            System.out.println("  with List.copyOf, the same call fails at the call site: "
                    + rejected.getClass().getSimpleName());
        }
        System.out.println("  best of all, no collection escapes: floor.claimFreeSpot() -> "
                + safe.claimFreeSpot().orElseThrow());
        System.out.println("      remaining free: " + safe.findFreeSpot().orElseThrow());

        System.out.println("""

                  Composition (Floor owns Spot) is not just an arrowhead on a diagram. It decides
                  who constructs it, who validates it, and who may mutate it - and all three
                  collapse the moment the internal list escapes. Note that Spot's constructor and
                  occupy() are package-private: outside this package, only Floor can make or take a
                  spot.

                  Aggregation is the other case: a Playlist references Songs it does not own, so the
                  songs are passed in and survive the playlist. Interviewers probe this with "what
                  happens to X when Y is deleted?" - have the answer.

                  And the best accessor is the one you never write. findFreeSpot() is what every
                  caller actually wanted; returning the collection was an answer to a question
                  nobody asked.\
                """);
    }

    // ---------------------------------------------------------------- 8

    private static void summary() {
        section("8. The decisions, in the order you make them");

        System.out.println("""
                  1. Nouns and verbs -> candidates. Verbs with their own data and lifecycle are
                     entities too: "borrow" becomes a Loan, and that is where the due date lives.

                  2. Entity or value object? If changing a field means it is a different thing, it
                     is a value object. If identity survives the change, it is an entity.

                  3. Where does behaviour go? Onto the class owning the data it needs. Off it only
                     when it coordinates several objects or talks to the outside world.

                  4. Interface? Only for: two impls today, a stated second, a test seam, or an
                     architectural boundary. Otherwise concrete - and say why out loud.

                  5. Extends or a field? Extends only if EVERY caller of the parent works with the
                     child and no one needs instanceof. Otherwise a field.

                  6. Who owns it? Owned parts are created inside and never leaked. Referenced things
                     are passed in and outlive you.

                  7. Can an illegal state be constructed? If yes, change the model, not the checks.

                  Underneath all seven is one question: WHAT CHANGES INDEPENDENTLY OF WHAT?
                  Things that change together belong together; things that change apart must be
                  separable. Cohesion, coupling, Open/Closed and Strategy are all that sentence
                  applied to different situations.\
                """);
    }

    // ---------------------------------------------------------------- helpers

    private static void section(String title) {
        System.out.println("\n--- " + title + " ---");
    }
}
