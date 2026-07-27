# Practical Patterns — Deep Dive

Not in the GoF book, but these come up constantly in SDE-2 / senior LLD rounds — usually as the
follow-up questions ("where does the database fit?", "how would you test this?", "how do you handle
concurrency?"). Knowing them separates a candidate who can name patterns from one who can build systems.

Code: [`src/com/lld/practical`](../../src/com/lld/practical)

---

## Repository

**Intent:** Mediate between the domain and the data-mapping layer using a collection-like interface
for accessing domain objects.

**The problem it solves.** Without it, SQL strings, `ResultSet` loops and vendor exceptions leak into
business logic. Your `OrderService` becomes untestable without a database, and swapping Postgres for
DynamoDB means rewriting the service.

**Structure.** A repository *interface* expressed in domain terms (`findByEmail`, `save`,
`deleteById`) and one or more implementations (JDBC, JPA, in-memory). Services depend on the
interface only — that's Dependency Inversion in its most common real-world form.

**Design decisions worth voicing.**
- **One repository per aggregate root**, not per table. `OrderRepository` handles `Order` *and* its
  `OrderLine`s, because you never load an order line on its own. This is the single most common
  mistake — a repository per table produces anemic, chatty code.
- **Return domain objects, not rows.** Mapping is the repository's job.
- **Generic base + specific interface.** `Repository<T, ID>` for CRUD, `UserRepository extends
  Repository<User, Long>` for domain queries.
- **Avoid the leaky `findAll()` on large tables.** Prefer paginated or specification-based queries.
- **Query explosion is the trade-off.** `findByNameAndStatusAndCreatedAfter...` doesn't scale. Fix it
  by passing a **Specification** to `findAll(spec)`, which is why the two patterns pair so often.

**Repository vs DAO.** A DAO is table-oriented and closer to the database; a Repository is
aggregate-oriented and speaks the domain's language. In practice teams use the terms
interchangeably — but knowing the distinction is a point in your favour.

**Related: Unit of Work.** Repositories save individual aggregates; a Unit of Work tracks everything
changed in a business transaction and commits it atomically. In Java this is what JPA's
`EntityManager` / a Spring `@Transactional` boundary gives you. Mention it when the interviewer asks
about multi-repository consistency.

**In the wild:** Spring Data JPA repositories, Hibernate `Session`, `EntityManager`, MyBatis mappers.

**When NOT to use it.** Trivial CRUD scripts, or when you're already using an ORM whose API *is* a
repository — wrapping it again adds a layer with no benefit.

**Interview soundbite.** *"I'll define `BookingRepository` in the domain layer with methods expressed
in domain terms, and keep the JDBC implementation in an infrastructure layer. Services depend on the
interface, so my unit tests inject an in-memory implementation and run without a database. For complex
search I'd pass a Specification rather than growing a `findByXAndYAndZ` method per screen."*

**Follow-ups you'll get.**
- *"How do you handle transactions spanning two repositories?"* → Unit of Work / a transactional
  service boundary. Across services, you can't — that's a Saga with compensating actions.
- *"Doesn't the interface leak persistence concerns?"* → It shouldn't. If `Pageable` or `Criteria`
  types appear in the interface, the abstraction is leaking.

---

## Dependency Injection

**Intent:** Supply a class's dependencies from outside rather than having the class construct or look
them up itself.

**The problem it solves.** `new` is glue. `private final EmailSender sender = new EmailSender();`
welds the class to a concrete collaborator: you can't test it without sending real email, can't swap
implementations, and can't see its dependencies without reading the whole body.

**DI vs the Dependency Inversion Principle.** DIP is the *principle* (depend on abstractions); DI is
the *technique* that makes it possible; an IoC container is one *tool* for doing DI at scale. Getting
these three right is a nice senior-level distinction.

**The three forms, ranked.**
1. **Constructor injection (preferred).** Dependencies are explicit, mandatory, and can be `final`,
   so the object is never in a half-configured state. If the constructor has 8 parameters, that's a
   *useful signal* the class does too much — don't hide it with field injection.
2. **Setter injection.** For genuinely optional dependencies with sensible defaults. Leaves a window
   where the object exists but isn't configured.
3. **Field/reflection injection.** Convenient in frameworks, but hides dependencies, prevents `final`,
   and makes plain-`new` unit testing impossible. Avoid in interview code.

**The composition root.** There should be exactly one place — `main()`, a factory, or the container
configuration — that knows about concrete classes and wires the object graph. Everything else deals in
interfaces. Showing an explicit composition root in an LLD answer is a strong signal.

**Why interviewers care.** DI is the difference between "I'd write unit tests" and code that is
*actually* testable. Injecting a fake or stub with no mocking framework demonstrates it concretely.

**In the wild:** Spring, Guice, Dagger, CDI, `ServiceLoader` (a lookup-based cousin).

**When NOT to use it.** Value objects and pure data holders don't need injected collaborators. And
don't introduce a container in an interview — hand-wiring in `main()` is clearer and faster.

**Interview soundbite.** *"`OrderNotifier` takes a `MessageSender` and an `AuditLog` in its
constructor rather than creating them. `main()` is my composition root and the only place that names
concrete classes. In tests I pass a fake sender and assert on it — no mocking framework, no network."*

**Follow-ups you'll get.**
- *"Circular dependencies?"* → With constructor injection they're impossible to construct, which is a
  feature: the cycle is a design smell. Break it by extracting the shared concern or introducing an
  event/callback.
- *"DI vs Service Locator?"* → A locator hides dependencies (the class asks the container for what it
  needs), so you can't see them from the signature and can't compile-time verify them. DI is
  preferred; Service Locator is widely considered an anti-pattern.

---

## Null Object

**Intent:** Provide an object with neutral ("do nothing") behaviour in place of `null`.

**The problem it solves.** Null checks metastasize. Every caller of `getLogger()` writes
`if (logger != null)`, and the one that forgets ships an NPE to production. The check is also
*duplicated knowledge*: every call site has to know that absence is possible.

**Structure.** The same interface, implemented by a class whose methods do nothing (or return a
neutral value: empty list, zero, `false`). Factories return it instead of `null`.

**When it's right vs wrong.**
- ✅ Right when "do nothing" is a **legitimate, expected behaviour** — a disabled logger, a guest user
  with no permissions, a no-op metrics sink, an empty discount.
- ❌ Wrong when absence is an **error**. Silently doing nothing hides bugs and produces the worst kind
  of failure: no exception, no log, wrong result. In that case fail fast.

**Java-specific notes.**
- A stateless null object should be a **singleton** — there's no reason to allocate more than one.
- `Optional<T>` is the modern alternative for *return values*: it makes absence explicit in the type
  system and forces the caller to handle it. Null Object is better when you want callers to *not* care
  at all. Use `Optional` at API boundaries, Null Object for injected collaborators.
- Related idioms: `Collections.emptyList()`, `Optional.empty()`, `Comparator` naturalOrder defaults.

**In the wild:** `Collections.emptyList()/emptyMap()`, SLF4J's `NOPLogger`, `java.util.logging`'s
no-op handlers, Spring's `NullValue` placeholders.

**When NOT to use it.** When callers legitimately need to distinguish "absent" from "present but
empty" — a null object erases that distinction.

**Interview soundbite.** *"`LoggerFactory` returns a `NoOpLogger` singleton rather than null for
unconfigured modules, so no call site needs a null check and disabling logging is a configuration
change, not a code change. For values that are genuinely optional I'd return `Optional` instead so
the caller is forced to handle absence."*

**Follow-ups you'll get.**
- *"Doesn't this hide bugs?"* → Yes, if you apply it where absence is an error. It's only correct when
  no-op is a valid behaviour — say so explicitly.

---

## Specification

**Intent:** Encapsulate a business rule in an object that can be evaluated against a candidate,
combined with other rules, and reused across validation, selection, and construction.

**The problem it solves.** Business rules smeared across the codebase as compound conditionals:

```java
if (p.getCategory().equals("electronics") && p.getPrice() < 500
        && p.isInStock() && !p.isDiscontinued() && p.getRating() > 4)
```

That condition is untestable in isolation, unnamed (nobody knows *why* those five things), and
copy-pasted into three other places where it slowly diverges.

**Structure.** A `Specification<T>` with `isSatisfiedBy(T)`, plus `and`/`or`/`not` combinators
(Java `default` methods make this free). Each rule is a small named class you can unit-test alone, and
complex rules are composed at runtime — even from configuration.

**Why it's more than a `Predicate`.** A `Predicate<T>` is the mechanism; a Specification is a *named
domain concept* (`PremiumCustomerSpec`, `EligibleForFreeShippingSpec`). The name is the value — it
turns an anonymous boolean into vocabulary the business can review. Expose `toPredicate()` so it still
plugs into streams.

**Three uses of one specification (this is the payoff).**
1. **Validation** — is this candidate valid?
2. **Selection/query** — filter a collection, or translate the spec into a SQL `WHERE` clause
   (`toSqlClause()`) so the same rule runs in the database. This is the advanced move worth mentioning.
3. **Construction** — build an object that satisfies the spec (test data, defaults).

**In the wild:** Spring Data JPA `Specification`, `Predicate` composition, Hibernate Criteria,
rules engines like Drools (a heavyweight version of the same idea).

**When NOT to use it.** One simple condition used once — a plain `if` is clearer. Specification pays
off when rules are named, reused, combined, or configured.

**Interview soundbite.** *"Discount eligibility is a moving target, so I'll model each rule as a
Specification — `MinCartValueSpec`, `FirstOrderSpec`, `CategorySpec` — and compose them with
`and`/`or`. Marketing's campaign becomes a composition rather than a code change, each rule is unit-
testable on its own, and I can translate the same spec into a SQL predicate so filtering happens in
the database rather than in memory."*

**Follow-ups you'll get.**
- *"How do you run this against a database instead of a list?"* → Give the spec a second method that
  emits a criteria/SQL fragment, so the same rule object drives both in-memory and DB filtering.
- *"Specification vs Strategy?"* → Specification is a Strategy specialised to boolean predicates, with
  composition operators. Same shape, narrower purpose.

---

## Producer–Consumer

**Intent:** Decouple work *creation* from work *processing* using a shared, bounded buffer, letting
each side run at its own rate.

**The problem it solves.** Direct calls couple producer throughput to consumer speed. If a request
handler writes logs synchronously to disk, disk latency becomes request latency. A queue lets the
producer hand off and move on — and, crucially, **bounds** how far ahead it can get.

**Why bounded matters (the most important point).** An unbounded queue converts a throughput problem
into an out-of-memory crash: if producers outpace consumers, the queue grows until the JVM dies. A
bounded queue applies **back-pressure** — producers block, which propagates the slowdown upstream
where it can be handled. Saying "bounded, for back-pressure" is a strong senior signal.

**The concurrency details interviewers probe.**
- **`while`, not `if`, around `wait()`.** Threads wake spuriously, and between `notifyAll()` and
  reacquiring the lock another consumer may have taken the item. Re-check the condition in a loop.
- **`notifyAll()` vs `notify()`.** With producers *and* consumers waiting on the same monitor,
  `notify()` can wake the wrong kind of thread and deadlock the system. Use `notifyAll()`, or use two
  `Condition` objects on a `ReentrantLock` (`notFull`, `notEmpty`) for precise signalling.
- **Graceful shutdown.** A blocked consumer never exits. Use a **poison pill** sentinel, or
  interruption plus `ExecutorService.shutdown()` / `awaitTermination()`.
- **Exception handling.** A consumer that dies on a bad message silently reduces throughput to zero.
  Catch per-item, and route failures to a dead-letter queue.
- **Ordering.** A single consumer preserves order; multiple consumers don't. If order matters, shard
  by key so each key has one consumer.

**What to actually ship.** `ArrayBlockingQueue` / `LinkedBlockingQueue` (bounded) plus an
`ExecutorService`. Hand-rolled `wait/notify` is for demonstrating you understand the primitives —
say that explicitly, then use the JDK.

**In the wild:** `BlockingQueue` implementations, `ThreadPoolExecutor` (its work queue *is* this
pattern), `Disruptor`, Kafka/RabbitMQ (the distributed version), async logging appenders.

**When NOT to use it.** When processing is fast and synchronous is simpler — a queue adds latency,
ordering questions, failure modes, and monitoring burden. Don't make things async by reflex.

**Interview soundbite.** *"Log writes shouldn't sit on the request path, so appenders publish to a
bounded `BlockingQueue` drained by a small pool of writer threads. Bounded is deliberate: if disk
stalls, producers block rather than the heap filling up. Shutdown uses a poison pill so writers drain
the queue before exiting, and each item is processed in a try/catch so one bad record can't kill a
writer."*

**Follow-ups you'll get.**
- *"What if the queue fills up?"* → Choose a policy and justify it: block (back-pressure), drop oldest
  (metrics), drop newest, or run the task on the caller's thread (`CallerRunsPolicy` — self-throttling).
- *"How many consumers?"* → CPU-bound: ~number of cores. I/O-bound: higher, tuned by
  `cores × (1 + wait/service)`. Measure, don't guess.
- *"How do you know it's healthy?"* → Monitor queue depth and consumer lag; a steadily growing queue
  means consumers are undersized.

---

## Honourable mentions (know the idea, rarely coded in an interview)

**Unit of Work.** Tracks every object changed during a business transaction and coordinates writing
them out atomically. Answers "how do two repository saves stay consistent?" — JPA's `EntityManager`
and Spring's `@Transactional` are the Java realisation.

**DTO (Data Transfer Object).** A flat, behaviour-free carrier for moving data across a boundary
(API, network, layer). Keeps your domain model out of your wire contract so the two can evolve
independently. Java `record`s make these one-liners. Mention it when asked "what does your API
return?"

**Service Locator.** A registry classes query for their dependencies. Widely considered an
anti-pattern versus DI because dependencies become invisible in the signature and failures move from
compile time to runtime. Worth knowing so you can explain *why you'd use DI instead*.

**CQRS.** Separate the model that handles writes from the model that serves reads, so each can be
optimised (and scaled) independently. Leans HLD, but comes up when read and write shapes diverge badly.

**Event Sourcing.** Persist the sequence of state-changing events rather than the current state;
rebuild state by replaying them. Gives a perfect audit trail and time-travel debugging, at the cost of
replay complexity and schema evolution pain. It's Command persisted, taken to its logical conclusion.

**Saga / Compensating Transaction.** The answer to "payment succeeded but shipping failed" across
services: a sequence of local transactions where each step has a compensating action to undo it.
Bring this up whenever a facade orchestrates steps that can't share an ACID transaction.
