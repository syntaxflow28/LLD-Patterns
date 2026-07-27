# LLD Patterns — Java Playbook for SDE 2 / Senior Interviews

A practical, runnable reference for Low-Level Design (LLD) interviews. Every pattern is a
self-contained Java file with a `Demo` class you can run to see it in action.

Covers all **23 GoF patterns** plus the **practical patterns** senior interviews actually probe.

## How to use this repo

1. Start with the **[Interview Approach](docs/foundations/APPROACH.md)** — a repeatable framework to drive any LLD round.
2. Internalize the **[SOLID Principles](docs/foundations/SOLID.md)** — interviewers grade you on these, not on trivia.
3. Learn the **patterns** below. For each, know: *the problem it solves*, *when to use it*, and *one real LLD example*.
4. Use the **[Pattern Cheat Sheet](docs/reference/CHEATSHEET.md)** to pick the right tool under pressure.
5. Go deep with the per-category references when a pattern doesn't click yet.
6. Finish on the **[worked problems](#worked-end-to-end-problems)** — full implementations of real interview questions where several patterns have to cooperate.

### Documentation map

All docs live under [`docs/`](docs), grouped by how you use them:

| Doc | What it gives you |
|---|---|
| **`foundations/`** | |
| [APPROACH.md](docs/foundations/APPROACH.md) | The 6-step interview framework, question bank, concurrency playbook, worked Parking Lot example |
| [SOLID.md](docs/foundations/SOLID.md) | Each principle with nuance, detection heuristics, and what to say out loud |
| **`reference/`** | |
| [CHEATSHEET.md](docs/reference/CHEATSHEET.md) | Fast symptom → pattern lookup, confusing pairs, 25 classic problems |
| [PROBLEMS.md](docs/reference/PROBLEMS.md) | **Why** each pattern fits each of the 25 classic problems — variation points, rejected alternatives, and the question interviewers always ask |
| **`patterns/`** | |
| [CREATIONAL.md](docs/patterns/CREATIONAL.md) | Deep dive: Singleton, Factory, Abstract Factory, Builder, Prototype, Object Pool |
| [STRUCTURAL.md](docs/patterns/STRUCTURAL.md) | Deep dive: Adapter, Decorator, Facade, Composite, Proxy, Bridge, Flyweight |
| [BEHAVIORAL.md](docs/patterns/BEHAVIORAL.md) | Deep dive: Strategy, Observer, State, Command, CoR, Template, Iterator, Mediator, Memento, Visitor, Interpreter |
| [PRACTICAL.md](docs/patterns/PRACTICAL.md) | Deep dive: Repository, DI, Null Object, Specification, Producer–Consumer, Unit of Work, Circuit Breaker, Idempotency, Domain Events, Result, Registry + DTO, CQRS, Saga |

Each deep-dive entry follows the same structure: **intent → the problem it solves → structure →
design decisions → real JDK usage → when NOT to use it → interview soundbite → follow-up questions**.

## Patterns covered

### Creational — *how objects get created*
| Pattern | Solves | Classic LLD use |
|---|---|---|
| [Singleton](src/com/lld/creational/singleton/SingletonDemo.java) | One shared instance | Config, logger, connection pool |
| [Factory Method](src/com/lld/creational/factory/FactoryDemo.java) | Create without naming concrete class | Notification/payment factories |
| [Abstract Factory](src/com/lld/creational/abstractfactory/AbstractFactoryDemo.java) | Families of related objects | Cross-platform UI kits |
| [Builder](src/com/lld/creational/builder/BuilderDemo.java) | Complex object, many optional fields | Building a `Pizza`, `HttpRequest` |
| [Prototype](src/com/lld/creational/prototype/PrototypeDemo.java) | Clone instead of rebuild | Game entities, document templates |

### Structural — *how objects are composed*
| Pattern | Solves | Classic LLD use |
|---|---|---|
| [Adapter](src/com/lld/structural/adapter/AdapterDemo.java) | Incompatible interfaces | Wrapping 3rd-party SDKs |
| [Decorator](src/com/lld/structural/decorator/DecoratorDemo.java) | Add behavior at runtime | Coffee add-ons, I/O streams |
| [Facade](src/com/lld/structural/facade/FacadeDemo.java) | Simplify a subsystem | Order/checkout orchestration |
| [Composite](src/com/lld/structural/composite/CompositeDemo.java) | Tree of part-whole objects | File system, org chart |
| [Proxy](src/com/lld/structural/proxy/ProxyDemo.java) | Control access / lazy load | Caching, rate limiting, auth |
| [Bridge](src/com/lld/structural/bridge/BridgeDemo.java) | Two dimensions varying independently | Shape × Renderer, Alert × Channel |
| [Flyweight](src/com/lld/structural/flyweight/FlyweightDemo.java) | Share state across many objects | Game tiles, text glyphs, map pins |

### Behavioral — *how objects interact*
| Pattern | Solves | Classic LLD use |
|---|---|---|
| [Strategy](src/com/lld/behavioral/strategy/StrategyDemo.java) | Swap algorithms at runtime | Payment/sorting/pricing strategies |
| [Observer](src/com/lld/behavioral/observer/ObserverDemo.java) | Publish/subscribe | Notifications, stock tickers |
| [State](src/com/lld/behavioral/state/StateDemo.java) | Behavior changes with state | Vending machine, order lifecycle |
| [Command](src/com/lld/behavioral/command/CommandDemo.java) | Encapsulate a request | Undo/redo, task queues |
| [Chain of Responsibility](src/com/lld/behavioral/chainofresponsibility/ChainDemo.java) | Pass request along handlers | Middleware, approval flows |
| [Template Method](src/com/lld/behavioral/template/TemplateDemo.java) | Fixed skeleton, variable steps | Data pipelines, game turns |
| [Iterator](src/com/lld/behavioral/iterator/IteratorDemo.java) | Traverse without exposing internals | Custom collections, pagination |
| [Mediator](src/com/lld/behavioral/mediator/MediatorDemo.java) | Collapse N×N coupling into N×1 | Chat room, air-traffic control |
| [Memento](src/com/lld/behavioral/memento/MementoDemo.java) | Snapshot & restore state | Undo, checkpoints, save games |
| [Visitor](src/com/lld/behavioral/visitor/VisitorDemo.java) | New ops over a stable hierarchy | Tax/shipping over a cart, AST passes |
| [Interpreter](src/com/lld/behavioral/interpreter/InterpreterDemo.java) | Evaluate a small grammar | Rule engines, formula/query eval |

### Practical — *not GoF, but interview gold*
| Pattern | Solves | Classic LLD use |
|---|---|---|
| [Repository](src/com/lld/practical/repository/RepositoryDemo.java) | Hide persistence from the domain | "Where does the DB fit?" |
| [Dependency Injection](src/com/lld/practical/di/DependencyInjectionDemo.java) | Wire collaborators from outside | Testability, DIP in practice |
| [Null Object](src/com/lld/practical/nullobject/NullObjectDemo.java) | Remove null checks | Optional logger, guest user |
| [Specification](src/com/lld/practical/specification/SpecificationDemo.java) | Composable business rules | Search filters, eligibility rules |
| [Object Pool](src/com/lld/practical/objectpool/ObjectPoolDemo.java) | Reuse expensive objects | Connection/thread pools |
| [Producer–Consumer](src/com/lld/practical/producerconsumer/ProducerConsumerDemo.java) | Decouple work creation from processing | Task queues, async logging |
| [Unit of Work](src/com/lld/practical/unitofwork/UnitOfWorkDemo.java) | Flush a whole transaction atomically | "How do two repository saves stay consistent?" |
| [Circuit Breaker](src/com/lld/practical/circuitbreaker/CircuitBreakerDemo.java) | Stop calling a dying dependency | Payment gateway outage, cascading failure |
| [Idempotency Key](src/com/lld/practical/idempotency/IdempotencyDemo.java) | Make retries safe | "The client retried — did they pay twice?" |
| [Domain Events](src/com/lld/practical/domainevents/DomainEventsDemo.java) | Announce facts, don't call collaborators | Order placed → email + stock + analytics |
| [Result / Either](src/com/lld/practical/result/ResultDemo.java) | Failure as a value, not a throw | Validation pipelines, expected errors |
| [Registry / Plugin](src/com/lld/practical/registry/PluginRegistryDemo.java) | Delete the switch everyone edits | Export formats, payment providers |

## Worked end-to-end problems

Single-pattern demos teach the pattern; they don't teach the hard part, which is **choosing between
patterns under time pressure and making them cooperate**. These nine are complete, runnable
implementations of the questions that actually get asked — multi-file packages, real validation,
real edge cases, and comments explaining *why* each decision beat the alternative.

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
```

Each demo prints a narrated trace, including the failure cases — rejected inputs, races, sold-out
machines, expired seat holds, and the change-making dead end. Read [PROBLEMS.md](docs/reference/PROBLEMS.md)
first for the reasoning, then the code for the execution.

## Running the code

No build tool needed — plain Java (17+ required for records and sealed interfaces).
Verified on **JDK 25**: all 44 demos compile and run clean, with zero `-Xlint:all` warnings.

```powershell
# from the repo root
javac -d out (Get-ChildItem -Recurse -Filter *.java src).FullName
java -cp out com.lld.behavioral.strategy.StrategyDemo
```

Swap the final class name for any `*Demo` you want to run.

## Suggested study order

1. SOLID + Approach docs
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
