# LLD Patterns — Java Playbook for SDE 2 / Senior Interviews

A practical, runnable reference for Low-Level Design (LLD) interviews. Every pattern is a
self-contained Java file with a `Demo` class you can run to see it in action.

Covers all **23 GoF patterns** plus the **practical patterns** senior interviews actually probe.

## How to use this repo

1. Start with the **[Interview Approach](docs/1-foundations/1-approach.md)** — a repeatable framework to drive any LLD round.
2. Build **[modelling intuition](docs/1-foundations/2-modelling.md)** — how to decide what becomes a class, an interface, or just a field. This is the skill patterns sit on top of.
3. Internalize the **[SOLID Principles](docs/1-foundations/3-solid.md)** — interviewers grade you on these, not on trivia.
4. Learn the **patterns** below. For each, know: *the problem it solves*, *when to use it*, and *one real LLD example*.
5. Use the **[Pattern Cheat Sheet](docs/3-reference/1-cheatsheet.md)** to pick the right tool under pressure.
6. Go deep with the per-category references when a pattern doesn't click yet.
7. Finish on the **[worked problems](#worked-end-to-end-problems)** — full implementations of real interview questions where several patterns have to cooperate.

### Documentation map

All docs live under [`docs/`](docs), grouped by how you use them:

| Doc | What it gives you |
|---|---|
| **`1-foundations/`** | |
| [Interview Approach](docs/1-foundations/1-approach.md) | The 6-step interview framework, question bank, concurrency playbook, worked Parking Lot example |
| [Modelling Intuition](docs/1-foundations/2-modelling.md) | **What becomes a class, an interface, or just a field — and why.** Composition vs inheritance, ownership, tell-don't-ask, illegal states. Diagram-heavy, with a smell → move table |
| [SOLID Principles](docs/1-foundations/3-solid.md) | Each principle with nuance, detection heuristics, and what to say out loud |
| **`2-patterns/`** | |
| [Creational](docs/2-patterns/1-creational.md) | Deep dive: Singleton, Factory, Abstract Factory, Builder, Prototype, Object Pool |
| [Structural](docs/2-patterns/2-structural.md) | Deep dive: Adapter, Decorator, Facade, Composite, Proxy, Bridge, Flyweight |
| [Behavioral](docs/2-patterns/3-behavioral.md) | Deep dive: Strategy, Observer, State, Command, CoR, Template, Iterator, Mediator, Memento, Visitor, Interpreter |
| [Practical](docs/2-patterns/4-practical.md) | Deep dive: Repository, DI, Null Object, Specification, Producer–Consumer, Unit of Work, Circuit Breaker, Idempotency, Domain Events, Result, Registry + DTO, CQRS, Saga |
| **`3-reference/`** | |
| [Cheat Sheet](docs/3-reference/1-cheatsheet.md) | Fast symptom → pattern lookup, confusing pairs, 26 classic problems |
| [Classic Problems](docs/3-reference/2-problems.md) | **Why** each pattern fits each of the 26 classic problems — variation points, rejected alternatives, and the question interviewers always ask |

Each deep-dive entry follows the same structure: **intent → the problem it solves → structure →
design decisions → real JDK usage → when NOT to use it → interview soundbite → follow-up questions**.

### Repository layout

The docs are numbered in reading order, and `src/` mirrors `docs/` one-to-one.

```
docs/
├── 1-foundations/     approach, modelling, SOLID     ← read in this order
├── 2-patterns/        creational, structural, behavioral, practical
└── 3-reference/       cheatsheet, classic problems

src/com/lld/
├── foundations/       runnable companion to the modelling doc
├── patterns/
│   ├── creational/    one self-contained *Demo.java per pattern
│   ├── structural/
│   ├── behavioral/
│   └── practical/
└── problems/          multi-file end-to-end implementations
```

Each `docs/2-patterns/*.md` deep dive maps to the `src/com/lld/patterns/*` package of the same name,
and each section of [Classic Problems](docs/3-reference/2-problems.md) maps to a `src/com/lld/problems/*` package.

## Patterns covered

### Creational — *how objects get created*
| Pattern | Solves | Classic LLD use |
|---|---|---|
| [Singleton](src/com/lld/patterns/creational/singleton/SingletonDemo.java) | One shared instance | Config, logger, connection pool |
| [Factory Method](src/com/lld/patterns/creational/factory/FactoryDemo.java) | Create without naming concrete class | Notification/payment factories |
| [Abstract Factory](src/com/lld/patterns/creational/abstractfactory/AbstractFactoryDemo.java) | Families of related objects | Cross-platform UI kits |
| [Builder](src/com/lld/patterns/creational/builder/BuilderDemo.java) | Complex object, many optional fields | Building a `Pizza`, `HttpRequest` |
| [Prototype](src/com/lld/patterns/creational/prototype/PrototypeDemo.java) | Clone instead of rebuild | Game entities, document templates |

### Structural — *how objects are composed*
| Pattern | Solves | Classic LLD use |
|---|---|---|
| [Adapter](src/com/lld/patterns/structural/adapter/AdapterDemo.java) | Incompatible interfaces | Wrapping 3rd-party SDKs |
| [Decorator](src/com/lld/patterns/structural/decorator/DecoratorDemo.java) | Add behavior at runtime | Coffee add-ons, I/O streams |
| [Facade](src/com/lld/patterns/structural/facade/FacadeDemo.java) | Simplify a subsystem | Order/checkout orchestration |
| [Composite](src/com/lld/patterns/structural/composite/CompositeDemo.java) | Tree of part-whole objects | File system, org chart |
| [Proxy](src/com/lld/patterns/structural/proxy/ProxyDemo.java) | Control access / lazy load | Caching, rate limiting, auth |
| [Bridge](src/com/lld/patterns/structural/bridge/BridgeDemo.java) | Two dimensions varying independently | Shape × Renderer, Alert × Channel |
| [Flyweight](src/com/lld/patterns/structural/flyweight/FlyweightDemo.java) | Share state across many objects | Game tiles, text glyphs, map pins |

### Behavioral — *how objects interact*
| Pattern | Solves | Classic LLD use |
|---|---|---|
| [Strategy](src/com/lld/patterns/behavioral/strategy/StrategyDemo.java) | Swap algorithms at runtime | Payment/sorting/pricing strategies |
| [Observer](src/com/lld/patterns/behavioral/observer/ObserverDemo.java) | Publish/subscribe | Notifications, stock tickers |
| [State](src/com/lld/patterns/behavioral/state/StateDemo.java) | Behavior changes with state | Vending machine, order lifecycle |
| [Command](src/com/lld/patterns/behavioral/command/CommandDemo.java) | Encapsulate a request | Undo/redo, task queues |
| [Chain of Responsibility](src/com/lld/patterns/behavioral/chainofresponsibility/ChainDemo.java) | Pass request along handlers | Middleware, approval flows |
| [Template Method](src/com/lld/patterns/behavioral/template/TemplateDemo.java) | Fixed skeleton, variable steps | Data pipelines, game turns |
| [Iterator](src/com/lld/patterns/behavioral/iterator/IteratorDemo.java) | Traverse without exposing internals | Custom collections, pagination |
| [Mediator](src/com/lld/patterns/behavioral/mediator/MediatorDemo.java) | Collapse N×N coupling into N×1 | Chat room, air-traffic control |
| [Memento](src/com/lld/patterns/behavioral/memento/MementoDemo.java) | Snapshot & restore state | Undo, checkpoints, save games |
| [Visitor](src/com/lld/patterns/behavioral/visitor/VisitorDemo.java) | New ops over a stable hierarchy | Tax/shipping over a cart, AST passes |
| [Interpreter](src/com/lld/patterns/behavioral/interpreter/InterpreterDemo.java) | Evaluate a small grammar | Rule engines, formula/query eval |

### Practical — *not GoF, but interview gold*
| Pattern | Solves | Classic LLD use |
|---|---|---|
| [Repository](src/com/lld/patterns/practical/repository/RepositoryDemo.java) | Hide persistence from the domain | "Where does the DB fit?" |
| [Dependency Injection](src/com/lld/patterns/practical/di/DependencyInjectionDemo.java) | Wire collaborators from outside | Testability, DIP in practice |
| [Null Object](src/com/lld/patterns/practical/nullobject/NullObjectDemo.java) | Remove null checks | Optional logger, guest user |
| [Specification](src/com/lld/patterns/practical/specification/SpecificationDemo.java) | Composable business rules | Search filters, eligibility rules |
| [Object Pool](src/com/lld/patterns/practical/objectpool/ObjectPoolDemo.java) | Reuse expensive objects | Connection/thread pools |
| [Producer–Consumer](src/com/lld/patterns/practical/producerconsumer/ProducerConsumerDemo.java) | Decouple work creation from processing | Task queues, async logging |
| [Unit of Work](src/com/lld/patterns/practical/unitofwork/UnitOfWorkDemo.java) | Flush a whole transaction atomically | "How do two repository saves stay consistent?" |
| [Circuit Breaker](src/com/lld/patterns/practical/circuitbreaker/CircuitBreakerDemo.java) | Stop calling a dying dependency | Payment gateway outage, cascading failure |
| [Idempotency Key](src/com/lld/patterns/practical/idempotency/IdempotencyDemo.java) | Make retries safe | "The client retried — did they pay twice?" |
| [Domain Events](src/com/lld/patterns/practical/domainevents/DomainEventsDemo.java) | Announce facts, don't call collaborators | Order placed → email + stock + analytics |
| [Result / Either](src/com/lld/patterns/practical/result/ResultDemo.java) | Failure as a value, not a throw | Validation pipelines, expected errors |
| [Registry / Plugin](src/com/lld/patterns/practical/registry/PluginRegistryDemo.java) | Delete the switch everyone edits | Export formats, payment providers |

## Worked end-to-end problems

Single-pattern demos teach the pattern; they don't teach the hard part, which is **choosing between
patterns under time pressure and making them cooperate**. These thirteen are complete, runnable
implementations of the questions that actually get asked — multi-file packages, real validation,
real edge cases, and comments explaining *why* each decision beat the alternative.

> **The last three are the 45-minute ones.** Most designs on this list are honestly 60–90 minutes of
> work; in a real 45-minute round you get a slice. Tic-Tac-Toe, the meeting room scheduler and the
> text editor are sized to be written end to end in the time you actually have, and each demo opens
> with a minute-by-minute budget and an explicit list of **what to cut and what to say instead**.

| Problem | Patterns combined | The hard part it drills |
|---|---|---|
| [Parking Lot](src/com/lld/problems/parkinglot/ParkingLotDemo.java) | Strategy ×2, Decorator, Observer, Builder, Facade | Lock-free spot claiming (CAS), `BigDecimal` money, why *not* Singleton |
| [Vending Machine](src/com/lld/problems/vendingmachine/VendingMachineDemo.java) | State, Facade, Flyweight-ish shared states | State vs Strategy, illegal transitions, **can the hopper actually make change?** |
| [Splitwise](src/com/lld/problems/splitwise/SplitwiseDemo.java) | Strategy, Observer, Facade | Rounding so shares sum *exactly*, materialised ledger, greedy min-cash-flow |
| [Elevator System](src/com/lld/problems/elevator/ElevatorDemo.java) | Strategy, Mediator, Facade, sealed hierarchy | SCAN vs FIFO, hall calls ≠ car calls, dispatch cost functions |
| [LRU / LFU Cache](src/com/lld/problems/cache/CacheDemo.java) | Strategy, Facade | O(1) get *and* put (HashMap + doubly linked list), "now make it LFU", TTL, why `ConcurrentHashMap` alone isn't enough |
| [Rate Limiter](src/com/lld/problems/ratelimiter/RateLimiterDemo.java) | Strategy | Four algorithms compared, and the **fixed-window boundary burst reproduced live** (10 requests through a 5/sec limit) |
| [Logging Framework](src/com/lld/problems/logger/LoggerDemo.java) | Chain of Responsibility, Template Method, Strategy ×2, Singleton, Factory | Broadcast vs stop-at-first CoR, format ⟂ destination, lazy message construction |
| [Notification Service](src/com/lld/problems/notification/NotificationDemo.java) | **Bridge**, Template Method, Observer, Strategy | M types × N channels → M + N, exponential backoff, why an OTP ignores opt-out |
| [Movie Ticket Booking](src/com/lld/problems/booking/BookingDemo.java) | Strategy, Decorator, Facade, State (via status) | **Seat holds with a TTL** — 20 threads race one seat, all-or-nothing locking, late payment rejected |
| [Gaming Leaderboard](src/com/lld/problems/leaderboard/LeaderboardDemo.java) | Strategy ×3, Observer, Facade, DTO | Three ops, three structures; a **Fenwick tree** for O(log) rank, and the tie-break comparator that silently loses players |
| [Tic-Tac-Toe (n×n, k players)](src/com/lld/problems/tictactoe/TicTacToeDemo.java) ⏱ | *Almost none — that's the point* | **Restraint**, and O(1) win detection via per-line counters (2000×2000 board: 0.4 ms vs 20.7 ms); win-before-draw ordering; the out-of-turn move an inferred-player API cannot catch |
| [Meeting Room Scheduler](src/com/lld/problems/meetingscheduler/MeetingSchedulerDemo.java) ⏱ | Facade, value objects, Strategy (named, not built) | **Half-open intervals** (closed ones ban back-to-back meetings), `TreeMap` floor+ceiling conflict detection (9 ms vs 1,531 ms over 50k meetings), and 20 threads racing one slot — exactly one wins |
| [Text Editor Undo/Redo](src/com/lld/problems/texteditor/TextEditorDemo.java) ⏱ | Command, Composite (macros) | **Command vs Memento measured** (38,574× less retained), the redo-stack bug reproduced until it throws, bounded history, typing coalescing, replace-all as one undo |

⏱ = sized to be written end to end inside a 45-minute round.

Run them the same way as any other demo:

```powershell
java -cp out com.lld.problems.parkinglot.ParkingLotDemo
java -cp out com.lld.problems.vendingmachine.VendingMachineDemo
java -cp out com.lld.problems.splitwise.SplitwiseDemo
java -cp out com.lld.problems.elevator.ElevatorDemo
java -cp out com.lld.problems.cache.CacheDemo
java -cp out com.lld.problems.ratelimiter.RateLimiterDemo
java -cp out com.lld.problems.logger.LoggerDemo
java -cp out com.lld.problems.notification.NotificationDemo
java -cp out com.lld.problems.booking.BookingDemo
java -cp out com.lld.problems.leaderboard.LeaderboardDemo
java -cp out com.lld.problems.tictactoe.TicTacToeDemo
java -cp out com.lld.problems.meetingscheduler.MeetingSchedulerDemo
java -cp out com.lld.problems.texteditor.TextEditorDemo
```

Each demo prints a narrated trace, including the failure cases — rejected inputs, races, sold-out
machines, expired seat holds, and the change-making dead end. Read [the problem write-ups](docs/3-reference/2-problems.md)
first for the reasoning, then the code for the execution.

## Running the code

No build tool needed — plain Java (17+ required for records and sealed interfaces).
Verified on **JDK 25**: all 49 demos compile and run clean, with zero `-Xlint:all` warnings.

```powershell
# from the repo root
javac -d out (Get-ChildItem -Recurse -Filter *.java src).FullName
java -cp out com.lld.patterns.behavioral.strategy.StrategyDemo
```

Swap the final class name for any `*Demo` you want to run.

## Suggested study order

1. SOLID + Approach docs, then [Modelling Intuition](docs/1-foundations/2-modelling.md) — run `com.lld.foundations.modelling.ModellingDemo` alongside it
2. Strategy, Observer, Factory, Builder (appear in ~80% of interviews)
3. State, Decorator, Singleton, Adapter, Facade
4. Command, Composite, Chain of Responsibility, Template Method, Iterator
5. Repository, Dependency Injection, Null Object, Specification, Producer–Consumer
6. Bridge, Flyweight, Mediator, Memento, Visitor, Prototype, Abstract Factory, Interpreter
   (know the intent and the trade-off even if you never code them)

For each pattern, work in this order: read the **deep dive** → run the **demo** → close the file and
re-derive the structure from the problem statement alone. If you can't re-derive it, you've memorized
it rather than understood it.

## What to be able to say about any pattern

If you can answer these five in under a minute, you know the pattern well enough for an interview:

1. **What problem does it solve?** (Not "what is it" — what pain does it remove?)
2. **What's the alternative, and why is this better here?**
3. **What does it cost?** (Every pattern has a downside; naming it is a senior signal.)
4. **Where does the JDK use it?** (Proves it's real, not academic.)
5. **When would you deliberately *not* use it?**

> **Interview tip:** Patterns are a means, not the goal. Reach for one only when it removes duplication,
> isolates change, or clarifies intent. Naming a pattern that fits earns points; forcing one loses them.
