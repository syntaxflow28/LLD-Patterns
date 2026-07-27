# Classic LLD Problems — Why These Patterns?

The cheat-sheet table tells you *which* patterns fit each classic problem. This page tells you **why**.

For every problem you get:
- **The requirements** that actually drive the design
- **Where the design pressure is** — the specific things that vary, and therefore need an abstraction
- **Why each pattern**, including the alternative you rejected
- **The one thing interviewers probe** on that problem

> The reasoning matters more than the mapping. In an interview, "I'll use Strategy here" scores far
> less than "fee rules are the thing that changes, so I'll isolate them behind an interface — that's
> where Strategy comes in." Every entry below is written so you can say the *why* out loud.

---

## Parking Lot

> **Full implementation:** [`src/com/lld/problems/parkinglot/`](../../src/com/lld/problems/parkinglot/ParkingLotDemo.java)

**Requirements.** Multiple floors; spot types (motorcycle/compact/large); park and unpark; issue a
ticket; compute a fee by duration; show availability.

**Where the design pressure is.**
1. **Pricing will change** — hourly today, then weekend surge, then monthly passes.
2. **Spot types will grow** — EV charging spots are the interviewer's favourite follow-up.
3. **A ticket has a lifecycle** — issued → paid → exited, with illegal transitions to reject.
4. **Concurrency** — several entry gates allocate from the same pool simultaneously.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Strategy** | `FeeStrategy` | Pricing is the *named* axis of variation. A `switch` on rate type would be edited every time finance changes a rule; a strategy makes each rule a class you can unit-test in isolation. |
| **Factory** | `SpotFactory` | Callers ask for a spot by `SpotType` and never name a concrete class, so adding `ChargingSpot` touches one registry line. Alternative — `new` at the call site — spreads the type decision everywhere. |
| **Singleton** | `ParkingLot` | One coordinator owns the free-spot pool; two instances would split state and double-allocate. **But inject it** rather than calling `getInstance()` everywhere, or you lose testability. |
| **State** | `TicketStatus` | Different operations are legal in each status. A status enum plus guard clauses repeats the same conditions in every method; state classes put each rule in exactly one place. |
| **Strategy** (2nd) | `SpotAllocator` | Nearest-to-entrance vs first-fit vs floor-balancing is a second, *independent* algorithm. Worth naming separately — it shows you can spot more than one variation axis. |
| **Observer** *(optional)* | Availability displays | Multiple gate displays react to occupancy changes without the lot knowing they exist. |

**The one thing interviewers probe.** *"Two gates park at the same instant — what stops both getting
spot 12?"* Answer with the specific race (check-then-act) and a concrete fix: a
`Map<SpotType, ConcurrentLinkedQueue<Spot>>` where `poll()` is atomic, giving race-free O(1)
allocation without a global lock.

---

## Vending Machine

> **Full implementation:** [`src/com/lld/problems/vendingmachine/`](../../src/com/lld/problems/vendingmachine/VendingMachineDemo.java)

**Requirements.** Insert coins, select a product, dispense, return change, refund on cancel, handle
sold-out.

**Where the design pressure is.**
1. **It is literally a state machine** — the same button means different things at different times.
2. **Every operation is state-dependent** — `selectProduct()` must be rejected before payment.
3. **Change calculation** is an algorithm that can vary (greedy vs exact-denomination).

| Pattern | Applied to | Why this one |
|---|---|---|
| **State** | `Idle`, `HasMoney`, `Dispensing`, `SoldOut` | This is *the* answer to this problem. Without it, every method starts with the same four-way conditional and adding a state means auditing every method. With it, each state class implements only the events it allows and performs its own transitions. |
| **Strategy** | `ChangeCalculator` | Greedy change vs minimum-coin-count vs "no change available" policy is a swappable algorithm. |
| **Singleton** | The machine / inventory | One physical machine, one inventory. Low-value on its own — don't lead with it. |
| **Command** *(optional)* | Button presses | Only if the interviewer asks for a transaction log or replay. Don't volunteer it; it's over-engineering here. |

**The one thing interviewers probe.** *"Draw the state transition table."* Have it ready:
(state × event) → next state. Also: *what happens on power loss mid-dispense?* — persist the state
identifier, not the state object, and recover into a safe state.

**Why not Strategy for the states?** Because the machine transitions *itself* based on events — the
client doesn't choose the mode. That's the State/Strategy distinction, and this problem exists to
test it.

---

## Elevator System

> **Full implementation:** [`src/com/lld/problems/elevator/`](../../src/com/lld/problems/elevator/ElevatorDemo.java)

**Requirements.** N elevators, M floors; internal (cabin) and external (hall) requests; move, open,
close; scheduling across multiple cars.

**Where the design pressure is.**
1. **Scheduling is the whole problem** — FCFS vs SCAN vs LOOK vs nearest-car, and it *will* change.
2. **Each car is a state machine** — moving up, moving down, idle, doors open, maintenance.
3. **Requests must be queued, prioritized, and possibly cancelled.**
4. **Displays and controllers must react** to every position change.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Strategy** | `SchedulingStrategy` | The interviewer's real question is "how do you pick which elevator responds?" Isolating it means you can discuss and swap algorithms without restructuring anything. This is the highest-value abstraction in the problem. |
| **State** | `ElevatorState` | `MovingUp` handles a floor-reached event completely differently from `DoorsOpen`. Conditionals here get unreadable fast. |
| **Command** | `Request` objects | Requests must be stored in a queue, sorted, deduplicated, and cancelled — a method call can't do any of that. Turning the request into an object is what makes the scheduler possible. |
| **Observer** | Floor displays, logging | Many consumers react to position changes; the elevator shouldn't know about any of them. |
| **Singleton** | `ElevatorController` | One dispatcher owns the fleet view needed for scheduling. |

**The one thing interviewers probe.** *"An elevator is going up past floor 5 and someone on floor 3
presses down — what happens?"* This tests whether your scheduler models *direction* and request type,
not just floor numbers. Keep separate up/down request sets per car.

---

## Notification Service

> **Full implementation:** [`src/com/lld/problems/notification/`](../../src/com/lld/problems/notification/NotificationDemo.java)

**Requirements.** Send notifications over email/SMS/push/WhatsApp; different notification types
(OTP, order update, marketing); user preferences; retries; templating.

**Where the design pressure is.** **Two independent axes** — notification *type* and delivery
*channel*. This is the classic Bridge setup, and spotting it is the point of the problem.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Bridge** | `Notification` (type) × `Channel` (transport) | Subclassing both gives `UrgentEmailNotification`, `MarketingSmsNotification`… — M×N classes. Bridging makes it M+N: adding WhatsApp is one class and *every* notification type supports it immediately. If you only name Strategy here, you've missed the structure of the problem. |
| **Factory** | `ChannelFactory` | Channel is chosen at runtime from user preferences; a registry keeps that decision in one place. |
| **Strategy** | `RetryPolicy` | Exponential backoff vs fixed vs no-retry is a genuinely separate, swappable algorithm. |
| **Decorator** | Rate limiting, encryption, logging around a channel | These are additive, combinable concerns. Subclassing every combination explodes; decorators compose at runtime. |
| **Observer** | Delivery-status events | Analytics, audit, and the retry scheduler all react to send/fail without the sender knowing them. |
| **Template Method** | Send lifecycle | `validate → render template → transmit → record` is fixed; only `transmit` varies. Guarantees no channel skips validation. |

**The one thing interviewers probe.** *"How do you add WhatsApp?"* If your answer touches more than
one new class plus a registration line, your abstraction is in the wrong place.

---

## Splitwise (Expense Sharing)

> **Full implementation:** [`src/com/lld/problems/splitwise/`](../../src/com/lld/problems/splitwise/SplitwiseDemo.java)

**Requirements.** Users and groups; add an expense; split it equally / by exact amounts / by
percentage / by shares; track who owes whom; simplify debts; settle up.

**Where the design pressure is.**
1. **Split types are the explicit variation** — the requirement names four and implies more.
2. **Balance maintenance** is the real data-structure question hiding inside a "patterns" problem.
3. **Debt simplification** is a separate algorithm from splitting.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Strategy** | `SplitStrategy` (equal/exact/percent/share) | The requirement hands you the variation axis. Each strategy validates its own input (percentages sum to 100; exact amounts sum to the total) — that validation is *why* they're separate classes rather than one method with flags. |
| **Factory** | `SplitStrategyFactory` | Split type arrives as user input; map it to a strategy in one place. |
| **Observer** | Balance updates, notifications | Adding an expense must update balances and notify members; the expense shouldn't call notification code directly. |
| **Strategy** (2nd) | `SettlementStrategy` | Naive pairwise settlement vs minimum-transaction simplification is an independent algorithm worth its own abstraction. |
| **Repository** | `ExpenseRepository`, `UserRepository` | The follow-up is always "where is this stored?" — have the answer ready. |

**The one thing interviewers probe.** *"How do you store balances?"* A `Map<UserId, Map<UserId,
Amount>>` keeping the invariant `balance[a][b] == -balance[b][a]`. And then: *"minimize the number of
transactions to settle a group"* — that's a greedy heap-based algorithm, and it's the real test.

---

## Tic-Tac-Toe and Chess

**Requirements.** Board, players, turn-taking, move validation, win/draw detection, undo.

**Where the design pressure is.**
1. **Each piece moves differently** — polymorphism, not a `switch` on piece type.
2. **Moves must be reversible** for undo/takeback.
3. **Win detection** varies by game variant and board size.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Strategy** | `MoveValidationStrategy` per piece; `WinningStrategy` | Row/column/diagonal checks in Tic-Tac-Toe, and per-piece movement rules in chess, are independent algorithms. A `switch (piece.type)` in `isValidMove()` is the anti-pattern this replaces. |
| **Command** | `MoveCommand` | A move must be stored (history), reversed (takeback), serialized (PGN export), and replayed. A plain method call gives you none of that. One abstraction, four features. |
| **Memento** | Board snapshot | For chess, restoring a full board snapshot is simpler and safer than inverting a move that involved a capture, castling, *and* en-passant. Real engines use commands for simple moves and snapshots at checkpoints. |
| **State** | `GameState` (in-progress, check, checkmate, stalemate, draw) | Legal actions differ per state — you can't move into check. |
| **Factory** | `PieceFactory` | Board setup and pawn promotion both need to create pieces by type. |
| **Observer** | Board renderer, move log, clock | Multiple views react to each move. |

**The one thing interviewers probe.** *"Implement undo."* Then: *"undo a capture"* and *"undo
castling."* This is exactly where the Command-vs-Memento trade-off becomes concrete — be ready to
explain why you'd pick one, or use both.

---

## Logging Framework

> **Full implementation:** [`src/com/lld/problems/logger/`](../../src/com/lld/problems/logger/LoggerDemo.java)

**Requirements.** Levels (DEBUG/INFO/WARN/ERROR); multiple destinations (console/file/network);
formatting; async writes; per-module configuration.

**Where the design pressure is.**
1. **Level filtering is naturally a chain** — each handler decides whether it's responsible.
2. **Writes must not block the caller.**
3. **Destinations and formats vary independently.**

| Pattern | Applied to | Why this one |
|---|---|---|
| **Chain of Responsibility** | Level handlers / logger hierarchy | A message enters at DEBUG and passes up until a handler at or above its level processes it. This is exactly how `java.util.logging` parent delegation works — cite it. |
| **Strategy** | `Formatter` (plain/JSON/XML) | Format is orthogonal to destination and swappable per appender. |
| **Producer–Consumer** | Async appender | Synchronous disk writes put I/O latency on the request path. A **bounded** queue drained by writer threads fixes it — and bounded matters: unbounded turns a slow disk into an OOM. |
| **Singleton** | `LoggerFactory` | One registry of configured loggers. Paired with **Null Object** — return a `NoOpLogger` for unconfigured modules so no call site needs a null check. |
| **Decorator** | Wrapping appenders with buffering/encryption | Additive, combinable concerns. Mirrors `java.io` exactly. |

**The one thing interviewers probe.** *"What happens when the log queue fills up?"* You must have a
policy: block (back-pressure), drop oldest, or drop newest — and a reason. Logging that takes down
production is a real incident class.

---

## Rate Limiter

> **Full implementation:** [`src/com/lld/problems/ratelimiter/`](../../src/com/lld/problems/ratelimiter/RateLimiterDemo.java)

**Requirements.** Limit requests per user/API key; algorithms (token bucket, leaky bucket, fixed
window, sliding window log/counter); distributed operation.

**Where the design pressure is.**
1. **The algorithm *is* the requirement** — and there are four standard ones.
2. **It must wrap existing services** without changing them.
3. **It is inherently concurrent** — every request mutates shared counters.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Strategy** | `RateLimitingAlgorithm` | Token bucket vs sliding window is *the* variation, and the interviewer wants to compare them. Isolating each behind one interface lets you discuss trade-offs concretely instead of hypothetically. |
| **Proxy** | `RateLimitedService` | The limiter must be transparent: same interface, decides whether to call the real service at all. That "may not delegate" behaviour is what makes it a Proxy rather than a Decorator. |
| **Singleton** | Counter store | One shared counter store per node, or the limits mean nothing. |
| **Factory** | Per-client limiter creation | Different tiers (free/pro/enterprise) get different configured limiters. |

**The one thing interviewers probe.** *"Make it thread-safe, then make it distributed."* In-process:
`ConcurrentHashMap` + atomic operations, or a lock per key. Distributed: Redis with an atomic Lua
script, because check-then-set across a network is a race. Also expect *"why is fixed window bad?"* —
the boundary burst problem (2× the limit across a window edge).

---

## Food Delivery and Ride Hailing

**Requirements.** Riders/customers, drivers/restaurants; matching; pricing with surge; order/trip
lifecycle; live tracking.

**Where the design pressure is.**
1. **Matching is the core algorithm** and evolves constantly.
2. **Two populations must not reference each other** — riders and drivers both scale independently.
3. **Long lifecycle** — requested → matched → picked up → in transit → delivered → rated.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Mediator** | `MatchingService` | If riders hold driver references and vice versa, you get N×N coupling and matching logic smeared across both. A mediator makes the matching, surge and cancellation rules readable in one place, and adding a third participant (fleet dispatcher) touches neither existing type. |
| **Strategy** | `MatchingStrategy`, `PricingStrategy` | Nearest-driver vs highest-rating vs batched matching; base fare vs surge vs subscription. Two clearly separate variation axes. |
| **State** | `TripState` / `OrderState` | Cancellation rules, refunds and permitted actions differ at every stage. This is where the business rules actually live. |
| **Observer** | Location updates | Customer app, driver app, support dashboard and ETA service all consume the same stream. |
| **Repository** | Trips, drivers, orders | The inevitable persistence follow-up. |

**The one thing interviewers probe.** *"How do you find nearby drivers efficiently?"* Scanning all
drivers is O(n). Answer: geospatial indexing — geohash, quadtree, or S2 cells — so lookup is
proportional to nearby drivers only. Patterns won't save you here; the data structure will.

---

## Cache with LRU or LFU eviction

> **Full implementation:** [`src/com/lld/problems/cache/`](../../src/com/lld/problems/cache/CacheDemo.java)

**Requirements.** `get`/`put` in O(1); fixed capacity; pluggable eviction; TTL; thread safety.

**Where the design pressure is.**
1. **Eviction policy is the named variation** (LRU, LFU, FIFO, random).
2. **O(1) is a hard requirement** — this is a data-structure problem wearing a design-patterns hat.
3. **Concurrency** — caches are shared by definition.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Strategy** | `EvictionPolicy` | The interviewer will ask "now make it LFU." If eviction is an interface, that's one new class; if it's baked into the map, it's a rewrite. |
| **Proxy** | Caching proxy over the real data source | Lets you add caching to any service without touching it or its callers — same interface, may skip the real call entirely. |
| **Singleton** | Shared cache instance | One cache, or you're not caching. |
| **Observer** *(optional)* | Eviction/expiry listeners | Only if the requirements mention write-back or metrics. |

**The one thing interviewers probe.** *"Get O(1) for both operations."* `HashMap` + doubly-linked
list: the map gives O(1) lookup, the list gives O(1) reorder and eviction, and each map value holds a
direct node reference so you never scan the list. Then: *"make it thread-safe"* — segment locking or
`ConcurrentHashMap` plus careful list synchronization, and mention that `LinkedHashMap` with
`removeEldestEntry` gives you LRU for free in Java.

---

## Gaming Leaderboard

> **Full implementation:** [`src/com/lld/problems/leaderboard/`](../../src/com/lld/problems/leaderboard/LeaderboardDemo.java)

**Requirements.** Millions of players; scores update in real time; return the top K; return *a given
player's* rank; daily, weekly and all-time boards.

**Where the design pressure is.**
1. **Three operations, no single structure that does all three.** Submit, top-K and rank-of-player
   each have an obvious data structure, and they are three *different* data structures.
2. **"What rank am I?" is the hot query** and it is the one that quietly stays O(n).
3. **Ties are guaranteed**, not an edge case — and ties are where the implementation silently breaks.
4. **"Submit a score" is underspecified.** Add to a total, keep the best, or overwrite?
5. **Time windows** must not be implemented as a filter over history.
6. **Concurrency** — score submission is a read-modify-write across several structures.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Strategy** | `RankIndex` | *The* axis of this problem. You write the O(n) version first and the interviewer asks you to scale it; behind an interface that's a new class, and you can run both and compare. |
| **Strategy** (2nd) | `ScoringRule` | Accumulate / personal-best / latest are three different products from the same code. An interface (not an enum) keeps it open for "now add weekly decay". |
| **Strategy as enum** | `TimeWindow` | Daily/weekly/all-time is a genuinely closed set, so an enum is simpler — and `values()` gives you the write fan-out loop for free. |
| **Observer** | `LeaderboardListener` | "Push a notification when someone breaks into the top 10" without the board importing a push SDK, and without a notification outage failing score submission. |
| **Facade** | `LeaderboardService` | One gameplay result must reach three boards. Without the facade, every caller duplicates the fan-out and the bucket-key rules. |
| **DTO** | `RankedPlayer` | Keeps the internal tie-break `sequence` out of the wire contract, so the tie-break rule can change without breaking clients. |

**The structure, and why the obvious ones fail.**

| Structure | submit | top K | rank of player |
|---|---|---|---|
| `HashMap` only | O(1) | O(n log n) — sort everything | O(n) |
| Sorted `ArrayList` | O(n) — shift on insert | O(k) | O(log n) |
| Max-heap | O(log n) | O(k log n), destructive | O(n) |
| `HashMap` + `TreeSet` | O(log n) | O(k) | **O(n) — still!** |
| …+ Fenwick rank index | O(log n) | O(k) | O(log range) |

The fourth row is where most candidates stop. A `TreeSet` looks like it solves everything until you
ask it for a rank *number* — `ranked.headSet(me).size()` is O(n), because `size()` on a `SortedSet`
view counts every time. So the design carries a third structure whose only job is counting: a
**Fenwick tree (BIT) indexed by score**, where "players above me" is `total - prefixSum(score)` in
O(log range). Volunteer its cost too: memory scales with the score *range*, not the player count, so
bucket or coordinate-compress if scores are huge or fractional.

**The one thing interviewers probe.** *"Two players have the same score — what happens?"* This is a
trap with a specific, demonstrable failure. The natural comparator is "by score, descending", and
`TreeSet` treats *compare returns 0* as *this element is already present* — so the second player on
940 is **never added**. Nothing throws, `add()` just returns `false`, and your board shows 4 of 6
players. The fix is a **total order consistent with equals**: score → tie-break (earliest to reach it
wins) → player id as the final uniqueness guarantee. The runnable demo reproduces the loss and the
fix side by side.

Two follow-ups arrive almost every time:
- *"What rank do the tied players get?"* — **Competition rank** (1, 2, 2, **4**) is what a scoreboard
  shows; **dense rank** (1, 2, 2, **3**) is what game UIs usually want. Ask which; picking silently is
  a coin flip you don't need to take.
- *"How would you update a player's score?"* — **Remove, build a new entry, re-insert.** Mutating an
  entry that is sitting in a `TreeSet` leaves it filed under its old score: `contains()` returns false
  for an element physically in the set, `remove()` silently fails, and it sorts in the wrong place
  forever. Making the entry an immutable `record` is what forces callers down the safe path.

**Scaling notes worth 30 seconds at the end.** In production this class is a **Redis sorted set** —
`ZADD` O(log n), `ZREVRANGE` O(log n + k), `ZREVRANK` O(log n) — a skip list giving you the `TreeSet`
and the rank index in one primitive. Shard by game mode and window (natural partitions) rather than
splitting one board, because rank is global. And only the top ~1000 needs to be exact: below that, an
approximate rank from a periodically rebuilt histogram is indistinguishable to the player and removes
the write-path contention entirely.

---

## Text Editor

**Requirements.** Insert/delete/format text; unlimited undo/redo; a document structure of
sections/paragraphs/runs; render efficiently for large documents.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Command** | Every edit operation | Undo/redo requires operations to be objects. Typing produces cheap deltas that invert exactly (`delete(n)` undoes `insert(text)`), which is why Command handles the common case better than snapshots. |
| **Memento** | Periodic checkpoints | Some operations (find-and-replace-all, reformat) are painful to invert. Snapshot at intervals, use command deltas between them — that hybrid is what real editors do, and saying so shows practical judgment. |
| **Composite** | Document → sections → paragraphs → runs | Rendering and word-count recurse uniformly over a tree; the client never type-checks a node. |
| **Flyweight** | Character/glyph formatting | A million characters each holding font, size and colour is gigabytes. Font data is intrinsic and shared; position is extrinsic and passed in. This is the textbook Flyweight example — the GoF book uses exactly this case. |
| **Iterator** | Traversing the document | Multiple traversal orders (by character, by word, by paragraph) over one structure without exposing nodes. |
| **Visitor** *(optional)* | Export to PDF/HTML/Markdown | Node types are stable; export formats keep growing — exactly Visitor's sweet spot. |

**The one thing interviewers probe.** *"Undo and redo together."* Two stacks: undo pops → pushes to
redo; **a new edit clears the redo stack**. Candidates forget that last rule constantly. Then:
*"bound the memory"* — cap history depth or snapshot-and-discard.

---

## Snake and Ladder

**Requirements.** Board with snakes and ladders; multiple players; dice; turn order; win condition.

**Where the design pressure is.** Honestly, not much — which is the trap. This problem tests whether
you can **resist over-engineering**.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Strategy** | `DiceStrategy` | Single die vs two dice vs crooked dice is a real, stated variation and trivially swappable for deterministic tests — that testing argument is the strongest justification. |
| **Factory** | `BoardFactory` / entity creation | Building boards from configuration (random vs fixed layouts). |
| **Observer** | Game events | Only if there's a UI or a move log consuming events. |
| **State** | `GameState` (waiting/in-progress/finished) | Only if the requirements include lobbies or pause/resume. |

**The one thing interviewers probe.** Whether you keep it simple. A clean `Board` with a
`Map<Integer, Integer> jumps` (covering snakes *and* ladders with one structure, since both are just
"move from A to B") plus a queue of players is a *better* answer than five patterns. Say out loud:
*"I'm deliberately not adding a Strategy for snakes and ladders — they're the same operation with a
different sign."*

---

## ATM Machine

**Requirements.** Card insert, PIN auth, balance/withdraw/deposit, cash dispensing by denomination,
receipt.

| Pattern | Applied to | Why this one |
|---|---|---|
| **State** | `Idle`, `CardInserted`, `Authenticated`, `Dispensing` | Strict operation ordering — you cannot withdraw before authenticating. State classes make illegal operations impossible to invoke rather than merely guarded. |
| **Chain of Responsibility** | Note dispensing by denomination | ₹2000 handler dispenses what it can, passes the remainder to ₹500, then ₹100. Each handler knows only its denomination and successor, so adding a ₹200 note is one class and one link — and you can reorder the chain from config. This is the single most elegant use of CoR in any classic problem. |
| **Strategy** | Transaction types (withdraw/deposit/transfer) | Each has different validation and limits. |
| **Proxy / Chain** | Authentication and authorization | Access control before the real operation runs. |
| **Template Method** | Transaction lifecycle | `authenticate → validate → execute → print receipt → log` is fixed; the middle step varies. Guarantees no transaction skips the audit log. |

**The one thing interviewers probe.** *"The machine can't make the exact amount — what happens?"* The
chain must be **transactional**: compute the full dispensing plan first, and only physically dispense
if the whole amount can be satisfied. Dispensing partially and then failing is a real-world bug.

---

## Online Shopping Cart

**Requirements.** Add/remove items; pricing; discounts and coupons; tax and shipping; multiple
payment methods; checkout.

**Where the design pressure is.** Everything varies, and each concern varies *independently*. That's
what makes this a good multi-pattern problem rather than a forced one.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Strategy** | `PaymentStrategy`, `DiscountStrategy` | Two independent, explicitly stated variation axes. |
| **Specification** | Offer eligibility | Rules like "electronics AND over ₹5000 AND first order" are compound, named, reusable, and marketing changes them weekly. Composable specs turn a campaign into a configuration rather than a deploy — and each rule is unit-testable alone. |
| **Visitor** | Tax and shipping per item type | Item types (book/electronics/grocery) are stable; the operations over them keep growing (tax, shipping, loyalty points, invoice lines). That asymmetry is precisely when Visitor pays off — and keeps tax law out of your domain objects. |
| **Decorator** | Gift wrap, extended warranty, express handling | Additive, combinable per-item add-ons. |
| **Repository** | Cart, order, product persistence | The persistence follow-up. |
| **Facade** | `CheckoutService` | Checkout touches inventory, pricing, payment, shipping and notification in a required order. One entry point keeps that choreography in a single place and gives you a transaction boundary. |
| **Command** *(optional)* | Cart operations | Only if "undo remove from cart" is a requirement. |

**The one thing interviewers probe.** *"Discounts don't compose — 10% off then ₹100 off gives a
different total than the reverse."* You need an explicit ordering/priority for discounts and a rule
about stacking. Also: *"payment succeeded but inventory ran out"* → compensating transaction / Saga.

---

## File System

**Requirements.** Files and directories; nested structure; size calculation; search; permissions.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Composite** | `File` (leaf) and `Directory` (composite) | The defining pattern here. `directory.size()` recurses; client code never writes `if (node instanceof Directory)`. Without it, every operation reimplements traversal. |
| **Visitor** | Search, size report, permission audit, virus scan | Node types are fixed at two forever; operations over the tree keep growing. Textbook Visitor conditions — and it keeps search logic out of `File`. |
| **Iterator** | Directory traversal | Depth-first vs breadth-first over the same structure, without exposing children. Mirrors `Files.walkFileTree`. |
| **Proxy** | Lazy loading file contents | Don't read a 4 GB file into memory to display its name. Virtual proxy loads on first access. |
| **Decorator** | Compression, encryption on a stream | Exactly what `java.io` does — cite `new GZIPOutputStream(new FileOutputStream(f))`. |

**The one thing interviewers probe.** *"Compute directory size efficiently for a huge tree."* Naive
recursion is O(n) per call and risks `StackOverflowError`. Answer: cache the size on each directory
and invalidate up the parent chain on write, or traverse iteratively with an explicit stack.

---

## Message Broker and Task Queue

**Requirements.** Publish messages; topics/queues; multiple consumers; at-least-once delivery;
retries; dead-letter queue; ordering.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Producer–Consumer** | The core queue | This *is* the problem. The key insight to state is **bounded** queues for back-pressure: unbounded turns a slow consumer into an OOM crash rather than a throughput warning. |
| **Observer** | Topic subscriptions | Pub/sub is Observer at its purest — publishers don't know subscribers. |
| **Command** | Messages/tasks as objects | A task must be queued, serialized, retried and dead-lettered. Only an object can do that; a method call can't. |
| **Object Pool** | Worker threads / connections | Bounds concurrency, which protects downstream systems — the bounding is often more valuable than the allocation savings. |
| **Strategy** | Retry and partitioning policies | Exponential backoff vs fixed; round-robin vs key-hash partitioning. |

**The one thing interviewers probe.** *"How do you guarantee ordering with multiple consumers?"* You
can't — globally. Shard by key so all messages for one key go to one consumer (that's exactly what
Kafka partitions do). Also expect *"exactly-once?"* — the honest answer is at-least-once delivery plus
idempotent consumers.

---

## Library Management

**Requirements.** Catalog books; members; borrow/return; due dates and fines; reservations and
waitlists; search.

| Pattern | Applied to | Why this one |
|---|---|---|
| **State** | `BookCopyState` (available, borrowed, reserved, lost) | Available actions differ per state, and transitions have rules ("reserved copies can only be borrowed by the reserver"). |
| **Observer** | Waitlist notifications | When a copy is returned, everyone waiting must be notified. The book shouldn't know about email or push. |
| **Specification** | Search filters | "Fiction AND published after 2015 AND currently available" — composable, named, reusable rules that can also translate into a SQL `WHERE` clause so filtering happens in the database, not in memory. |
| **Repository** | Books, members, loans | Aggregate roots are `Book` and `Member`; a `Loan` belongs to a member. **One repository per aggregate root, not per table** — say this. |
| **Strategy** | `FineCalculationStrategy` | Per-day fines vs grace periods vs member-tier rules will change. |

**The one thing interviewers probe.** *"Model book vs copy."* Candidates conflate them. A `Book` is
the title (ISBN, author); a `BookCopy` is the physical item with its own state and barcode. You borrow
a *copy*, you reserve a *book*. Getting this modelling right matters more than any pattern here.

---

## Hotel and Movie Booking

> **Full implementation:** [`src/com/lld/problems/booking/`](../../src/com/lld/problems/booking/BookingDemo.java)

**Requirements.** Search availability; hold a seat/room; book; pay; cancel and refund; prevent
double-booking.

| Pattern | Applied to | Why this one |
|---|---|---|
| **State** | `BookingState` (held, confirmed, cancelled, completed, expired) | Refund and cancellation rules differ per state, and holds expire — a timed transition. This is where the business rules live. |
| **Strategy** | `PricingStrategy` | Dynamic pricing, seasonal rates, seat-class multipliers, loyalty discounts. |
| **Facade** | `BookingService` | Search → hold → pay → confirm → notify spans several subsystems in a required order, and the sequence must exist once, not per client. Also your transaction boundary. |
| **Repository** | Inventory and bookings | Persistence, and the place concurrency is actually resolved. |
| **Observer** | Confirmations, reminders | Multiple consumers of a booking-confirmed event. |
| **Singleton** | Inventory manager | One authoritative view of what's available. |

**The one thing interviewers probe.** *"Two users click the last seat simultaneously."* The answer
that lands: a **temporary hold with a TTL** taken atomically (DB row lock, optimistic version column,
or `SETNX` in Redis), so the seat is removed from inventory during payment and auto-released if
payment doesn't complete. Saying "I'd use `synchronized`" is a weak answer when state lives in a
database.

---

## Traffic Light Controller

**Requirements.** Signals cycle red → green → yellow; timing per direction; emergency override;
pedestrian requests.

| Pattern | Applied to | Why this one |
|---|---|---|
| **State** | `Red`, `Green`, `Yellow` | The canonical minimal state machine. Each state knows its duration and its successor, so the cycle is data, not a conditional. |
| **Observer** | Signals, displays, sensors | Direction controllers and displays react to phase changes. |
| **Singleton** | Intersection controller | One controller per intersection, coordinating directions so two greens never conflict. |
| **Strategy** *(optional)* | Timing policy | Fixed-timer vs sensor-adaptive vs time-of-day scheduling. |
| **Command** *(optional)* | Emergency override | Only if pre-emption is a requirement. |

**The one thing interviewers probe.** *"Guarantee two perpendicular directions are never green
together."* That safety invariant belongs in the intersection controller, not in individual lights —
the controller owns the phase, and lights only reflect it. This is a good example of putting an
invariant at the level that can actually enforce it.

---

## Document Converter

**Requirements.** Read documents from multiple sources; convert to PDF/HTML/Markdown/DOCX; preserve
structure; extensible in both directions.

**Where the design pressure is.** **Two axes again** — source formats × target formats — plus a
growing set of operations over a stable node hierarchy.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Visitor** | Export operations over the document tree | Node types (heading, paragraph, table, image) are stable; export targets keep growing. Each exporter is one self-contained visitor and the node classes never learn about PDF layout. Exactly the expression-problem trade-off Visitor is built for. |
| **Composite** | The document node tree | Nested structure traversed uniformly; pairs with Visitor by design. |
| **Bridge** | Source format × target format | Prevents `MarkdownToPdfConverter`, `HtmlToPdfConverter`, `MarkdownToDocxConverter`… M×N classes become M+N. |
| **Strategy** | Style/formatting rules | Theme and formatting options swap independently of the export target. |
| **Template Method** | Conversion pipeline | `parse → build tree → transform → render` is fixed; parse and render vary. |

**The one thing interviewers probe.** *"Add a new node type — say, footnotes."* Now every visitor
breaks. That's Visitor's known cost, and the right answer is to acknowledge it: element types are
stable *by assumption*, and if that assumption is wrong, Visitor is the wrong pattern.

---

## Undo and Redo System

**Requirements.** Undo/redo arbitrary operations; bounded history; grouped (macro) operations.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Command** | Each operation | The foundational choice: operations must be objects to be stored and reversed. Deltas are memory-cheap. |
| **Memento** | Snapshots for non-invertible operations | Some operations can't be cleanly inverted (replace-all, sort, reformat). Snapshot before, restore on undo. |
| **Composite** | Macro / grouped commands | "Undo the whole paste" should reverse ten operations as one. A `CompositeCommand` holding a list of commands undoes them in reverse order — Command + Composite, a very clean pairing. |

**The design conversation, which is the actual point of this problem.**

| | Command (inverse op) | Memento (snapshot) |
|---|---|---|
| Memory | Low — stores a delta | High — stores full state |
| Complexity | High — every op needs a correct inverse | Low — restore always works |
| Non-invertible ops | Can't handle | Handles fine |

Real systems use both: deltas for the common case, snapshots at checkpoints. Being able to explain
*why* both exist is what's being tested.

**The one thing interviewers probe.** *"What clears the redo stack?"* Performing a new command after
an undo. Nearly everyone forgets this. Second probe: *"bound the memory"* — cap depth, or snapshot
periodically and discard older deltas.

---

## Rule and Discount Engine

**Requirements.** Business users define rules; rules combine with AND/OR/NOT; evaluate against
orders/users; changeable without a deploy.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Specification** | Individual rules + `and`/`or`/`not` combinators | The pragmatic 90% answer. Each rule is a small named class you can unit-test alone; complex rules compose at runtime, even from configuration. The name is the value — `EligibleForFreeShippingSpec` is vocabulary the business can review. |
| **Strategy** | Discount calculation | *Whether* a discount applies (Specification) is a different question from *how much* it is (Strategy). Keeping them separate is the key insight in this problem. |
| **Chain of Responsibility** | Sequential rule application | When rules apply in priority order and one may terminate evaluation ("best single discount wins"). |
| **Interpreter** | Only if rules are authored as *text* | If business users type `price > 100 AND category = "books"`, you need a grammar. Otherwise Specification is strictly better — and note that Interpreter covers *evaluation* only; parsing is a separate parser you also have to write. |
| **Composite** | Nested rule groups | Rule groups containing rule groups, evaluated uniformly. |

**The one thing interviewers probe.** *"Multiple discounts apply — what happens?"* You need an
explicit policy: best-only, stacking with a defined order, or a cap. And *"how do rules get updated
without a deploy?"* — persist specs as data and reconstruct the composition at load time.

---

## Game World

**Requirements.** Thousands of entities (units, trees, projectiles); each has behaviour and state;
spawning; rendering; collision.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Flyweight** | Shared entity data (mesh, texture, base stats) | 100k trees each holding a texture blob is gigabytes. Intrinsic data is shared through a factory; position and health are extrinsic per-instance. The scale requirement is what makes Flyweight correct here rather than premature. |
| **Prototype** | Spawning from templates | Cloning a configured enemy is cheaper and clearer than re-specifying forty fields per spawn. |
| **Object Pool** | Projectiles, particles, effects | Objects created and destroyed thousands of times per second; pooling avoids GC pressure spikes that show up as frame stutter. This is one of the few places pooling is genuinely justified. |
| **State** | Entity AI (idle, patrol, chase, attack, flee) | Behaviour differs completely per mode with defined transitions. |
| **Composite** | Scene graph | Transforms and rendering cascade down the hierarchy. |
| **Observer** | Game events | Damage, death and pickup events consumed by UI, sound, scoring and achievements. |

**The one thing interviewers probe.** *"Your flyweight is shared across threads — what breaks?"*
Nothing, **if it's immutable**. That's the non-negotiable constraint. If shared data becomes mutable,
the pattern is a correctness bug, not an optimization. Also expect *"how do you find collisions
without checking every pair?"* — spatial partitioning (quadtree/grid), not a pattern.

---

## Air Traffic Control

**Requirements.** Many aircraft; collision avoidance; landing slot allocation; communication;
emergency priority.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Mediator** | `ControlTower` | The textbook example, and for a real reason: aircraft must **never** communicate directly. With 50 aircraft, direct references are 2450 relationships and no single place enforces separation minima. The tower centralizes all coordination — and safety rules must be centralized to be verifiable. |
| **Observer** | Position broadcasts | Radar displays, logging and conflict detection all consume position updates. |
| **State** | Flight phase (taxiing, takeoff, cruising, approach, landed) | Permitted instructions differ entirely by phase. |
| **Strategy** | Landing-slot scheduling | FCFS vs fuel-priority vs emergency-first. |
| **Command** | Instructions to aircraft | Instructions must be queued, acknowledged, logged and possibly cancelled. |

**The one thing interviewers probe.** *"Doesn't the tower become a god object?"* Yes — that's
Mediator's known failure mode, and the interviewer wants to hear you name it. Mitigate by splitting
by concern (`SeparationMediator`, `LandingScheduler`, `CommsMediator`) and delegating the actual
algorithms to strategies the mediator calls.

---

## Stock Trading Platform

**Requirements.** Order types (market/limit/stop); order matching; portfolio; price feeds; alerts.

| Pattern | Applied to | Why this one |
|---|---|---|
| **Observer** | Price feeds → subscribers | Many consumers (user watchlists, alert engine, charting, risk) react to each tick without the feed knowing them. Classic pub/sub. |
| **Strategy** | Order types and execution algorithms | Market vs limit vs stop-loss execute under different conditions; TWAP/VWAP are separate execution algorithms. |
| **Command** | Orders | Orders are queued, matched, cancelled, amended and audited. Regulators require the full log — only objects give you that. |
| **State** | Order lifecycle (new, partially filled, filled, cancelled, rejected) | Permitted actions differ per state; partial fills make this genuinely non-trivial. |
| **Producer–Consumer** | Order intake → matching engine | Decouples burst intake from matching throughput, with a bounded queue for back-pressure. |
| **Specification** | Alert conditions and risk checks | "Price > X AND volume > Y" — composable, user-authored rules. |

**The one thing interviewers probe.** *"Design the order book."* Two priority structures (bids
descending, asks ascending) with **price-time priority** — a map from price level to a FIFO queue of
orders. This is a data-structure question, and it's the part that actually distinguishes candidates.
Also: *"the observer notification is on the hot path"* — synchronous notification adds latency to
matching, so dispatch asynchronously.

---

## Cross-cutting lessons

Reading across all 25 problems, the same reasoning recurs:

1. **Strategy appears almost everywhere** because "this algorithm will change" is the most common
   requirement in existence. If you learn one pattern deeply, learn this one.
2. **State appears whenever there's a lifecycle** — order, ticket, booking, trip, game. Look for the
   word "status" in the requirements; it's a state machine wearing a disguise.
3. **Observer appears whenever the requirements say "notify" or "update the display."**
4. **Bridge appears whenever you can write the problem as "X × Y."** Notification type × channel,
   source × target format, shape × renderer. Listen for the "×".
5. **Composite + Visitor travel together** whenever there's a tree with growing operations.
6. **The interviewer's hardest question is usually not about patterns.** It's about concurrency
   (double-booking, race for the last seat) or data structures (O(1) cache, geospatial lookup, order
   book). Patterns organize the code; these decide whether it works.
7. **Knowing when to stop matters.** Snake & Ladder rewards restraint. Naming a pattern you
   deliberately *rejected*, with the reason, is one of the strongest signals available to you.
