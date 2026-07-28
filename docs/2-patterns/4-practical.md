# Practical Patterns — Deep Dive

Not in the GoF book, but these come up constantly in SDE-2 / senior LLD rounds — usually as the
follow-up questions ("where does the database fit?", "how would you test this?", "how do you handle
concurrency?"). Knowing them separates a candidate who can name patterns from one who can build systems.

Code: [`src/com/lld/patterns/practical`](../../src/com/lld/patterns/practical)

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

## Unit of Work

**Intent:** Track every object touched during a business transaction and coordinate writing the whole
set out as one atomic flush.

**The problem it solves.** Repositories save one aggregate at a time, which raises the obvious
question — *how do two `repository.save()` calls stay consistent?* Save-as-you-go means three objects
cost three round trips, and if the third fails the first two are already committed. Your domain now
contains a state it believes is impossible: an order with no payment, a transfer that debited but
never credited. Compensating that after the fact means writing undo statements and hoping *those*
succeed.

**Structure.** Three collections — `newObjects`, `dirtyObjects`, `removedObjects` — plus
`registerNew` / `registerDirty` / `registerRemoved` and a single `commit()` that flushes them inside
one transaction. Repositories register with the Unit of Work instead of writing directly.

**Design decisions worth voicing.**
- **Deferring the writes is what buys you everything else.** Because nothing has been sent, the Unit
  of Work can *optimise* the batch before it goes: an object created and then deleted in the same
  transaction never reaches the database at all, and an object created then modified emits one
  `INSERT`, not `INSERT` + `UPDATE`. The naive implementation emits both.
- **Statement order is deliberate: inserts, then updates, then deletes** — not registration order.
  Deleting first can break a foreign key that a pending insert was about to satisfy.
- **Rollback becomes free.** Discard the three collections. Nothing was written, so nothing needs
  undoing.
- **One Unit of Work per request, never a singleton.** It is mutable per-transaction state. A shared
  one leaks another user's pending changes into your commit. This is exactly why JPA's
  `EntityManager` is request-scoped.
- **How does it know an object is dirty?** Explicit `registerDirty()` (simple, easy to forget) or
  automatic change tracking by snapshotting on load and diffing at commit (what JPA does — invisible
  and magical right up until it is neither).

**In the wild:** JPA's `EntityManager` *is* a Unit of Work — the persistence context is the identity
map, `flush()` is the commit, and Spring's `@Transactional` sets its boundary. Hibernate's
"write-behind" and `DbContext.SaveChanges()` in EF Core are the same idea.

**When NOT to use it.** Single-aggregate operations don't need one — a plain repository save is
clearer. And it does not extend across services: two databases cannot share a transaction, which is
where **Saga** takes over.

**Interview soundbite.** *"Repositories save aggregates; a Unit of Work decides when. It buffers
inserts, updates and deletes, then flushes them in one transaction so the write is all-or-nothing.
Deferring also lets it collapse redundant work — an object created and deleted in the same
transaction never hits the database. I'd scope it to the request, because it's mutable per-transaction
state."*

**Follow-ups you'll get.**
- *"How is this different from a transaction?"* → A transaction is the database's atomicity
  primitive; the Unit of Work is the application-side bookkeeping that decides what goes inside one.
- *"Two users edit the same record — who wins?"* → Add a version column and check it in the `WHERE`
  clause at flush time (optimistic locking). Zero rows updated means someone else committed first.
- *"Does it work across microservices?"* → No. One database, one transaction. Across services you
  need a Saga with compensating actions.

> **Runnable demo:** [`src/com/lld/patterns/practical/unitofwork/`](../../src/com/lld/patterns/practical/unitofwork/UnitOfWorkDemo.java)

---

## Circuit Breaker

**Intent:** Stop calling a dependency that is clearly failing, so the failure doesn't propagate back
into you.

**The problem it solves.** A downstream service slows to a crawl. Every request to it occupies one of
your threads for the full 30-second timeout. Your pool exhausts, requests that had nothing to do with
that dependency start queuing, and your service goes down too. The dependency's outage became your
outage. Worse, your retries pile more load onto something already struggling, so it can never
recover. This cascade is *the* reason the pattern exists, and naming the cascade is what makes the
answer sound experienced.

**Structure.** A three-state machine wrapping the call:

| State | Behaviour | Exit condition |
| --- | --- | --- |
| **CLOSED** | Calls pass through; failures are counted | Threshold consecutive failures → OPEN |
| **OPEN** | Calls rejected instantly, dependency never touched | Cooldown elapses → HALF_OPEN |
| **HALF_OPEN** | *One* trial call allowed | Success → CLOSED (after N probes) · Failure → OPEN |

**Design decisions worth voicing.**
- **HALF_OPEN is the state candidates forget, and it is the important one.** Without it you go
  straight from OPEN back to full production traffic and knock over a service that has been down for
  five seconds. HALF_OPEN sends exactly one probe and asks permission before resuming.
- **Failing fast is the feature.** Rejecting in microseconds instead of timing out in 30 seconds is
  what frees your threads. The circuit breaker is a *thread-pool protection* mechanism as much as a
  dependency one.
- **Don't hold a lock across the network call.** Synchronise the counter updates and the state
  transition; leave the actual call outside. A `synchronized` method around the whole thing serialises
  every request through one dependency and creates the bottleneck you were trying to avoid.
- **Inject the clock.** Otherwise testing a five-second cooldown takes five seconds, and nobody
  writes that test twice.
- **Consecutive failures vs a failure rate.** Consecutive count is trivial to implement and explain;
  a sliding-window error *rate* (Resilience4j's default) is more accurate under mixed traffic. Say
  which you chose and why.
- **Pair it with a fallback.** A cached, stale or queued response beats a 500. The breaker is what
  makes taking that decision cheap enough to do on every request.
- **One breaker per dependency, not per service** — and combine with a bulkhead so a slow dependency
  can only ever consume its own slice of the thread pool.

**In the wild:** Resilience4j (the current standard), Netflix Hystrix (the pattern's popularizer, now
retired), Polly on .NET, and Envoy/Istio outlier detection at the mesh layer.

**When NOT to use it.** In-process calls that can't hang, and operations where failing fast is worse
than waiting. Also skip it when the correct response to failure is a bounded retry with jittered
backoff — retry handles the *transient* blip, the breaker handles the *sustained* outage. Real systems
use both, in that order.

**Interview soundbite.** *"I'd wrap the payment gateway in a circuit breaker. After three consecutive
failures it opens and rejects instantly, so a gateway outage can't exhaust my thread pool and take
the whole service down. After a cooldown it half-opens and lets one probe through — if that succeeds
it closes, if not it re-opens immediately. While open, callers get a fallback rather than an error."*

**Follow-ups you'll get.**
- *"Where do retries fit?"* → Inside the breaker, and bounded with exponential backoff plus jitter.
  Unjittered retries from many clients re-synchronise into a thundering herd.
- *"How does this work across ten instances?"* → Each keeps its own state by default. That's usually
  fine — every instance learns independently within a few requests — and shared state adds a network
  hop to the hot path.
- *"What counts as a failure?"* → Timeouts and 5xx, yes. A 400 is *your* bug, not the dependency's,
  and tripping on it hides the real problem.

> **Runnable demo:** [`src/com/lld/patterns/practical/circuitbreaker/`](../../src/com/lld/patterns/practical/circuitbreaker/CircuitBreakerDemo.java)

---

## Idempotency Key

**Intent:** Make retrying a request safe, so "do it again" never means "charge them again".

**The problem it solves.** A response is lost in the network. The client cannot distinguish "the
server never got it" from "the server did it and the reply vanished" — that ambiguity is genuinely
unsolvable at the client. So retrying is the only sane client behaviour, which means the *server* has
to make retries harmless. Anything with at-least-once delivery — HTTP retries, message queues, mobile
clients on flaky networks — forces this on you.

**Structure.** The client generates a unique key per logical *operation* (not per attempt) and sends
it with every attempt. The server stores key → response on first execution and, on seeing the key
again, replays the stored response without re-running anything.

**Design decisions worth voicing.** Four details separate a real answer from a hand-wave:
- **Never cache failures.** Storing an error permanently poisons the key and the client can never
  succeed no matter how many times they retry. (In Java, doing the work inside
  `ConcurrentHashMap.computeIfAbsent` gives you this for free — if the mapping function throws, no
  mapping is recorded.)
- **Concurrent duplicates must collapse to one execution.** Two retries arriving together is the
  normal case, not the edge case, and a check-then-act implementation (`containsKey` then `put`)
  charges the card twice. You need an atomic reserve — `computeIfAbsent`, a per-key lock, or a unique
  constraint.
- **Same key with a different payload is a client bug — reject it.** Replaying the old response would
  tell the client their *second, different* request succeeded when it was never attempted. Store a
  fingerprint of the body and return 422. Stripe does exactly this.
- **Keys expire.** Retention must outlive the client's retry window — 24 hours is the usual choice.
  Keeping them forever is an unbounded, permanently growing table.

**The answer that beats all of it, when available:** make the operation *naturally* idempotent.
`SET balance = 100` is idempotent; `balance += 10` is not. And prefer a `UNIQUE` constraint on
`(customer_id, idempotency_key)` written inside the same transaction as the charge — then the database
enforces exactly-once across every server and across restarts, which an in-memory map cannot.

**In the wild:** Stripe, PayPal and AWS all expose an idempotency key (AWS calls it a client token) on
every mutating API. Kafka's `enable.idempotence` and every "exactly-once" queue consumer are the same
idea one layer down.

**When NOT to use it.** Reads and naturally idempotent writes (`PUT`, `DELETE`) don't need it — HTTP
already defines them as idempotent, and adding a key is ceremony. It's `POST` that needs the help.

**Interview soundbite.** *"The client sends an idempotency key with every attempt of the same logical
operation. The first request executes and I store the key with its response; every retry replays that
response without re-charging. I reserve the key atomically so two simultaneous retries can't both
execute, I don't cache failures — that would poison the key forever — and I reject a reused key that
arrives with a different body, because that's a client bug, not a retry."*

**Follow-ups you'll get.**
- *"Who generates the key?"* → The client, once per logical operation, and it reuses it across
  retries. A server-generated key can't help, because the client can't tell you which attempt it is.
- *"The server crashes mid-charge — now what?"* → This is why the key belongs in the database in the
  same transaction as the effect. In-memory state dies with the process and the retry double-charges.
- *"What about the third-party gateway?"* → Forward your key to it. Every serious payment API accepts
  one, which extends the guarantee past your own boundary.

> **Runnable demo:** [`src/com/lld/patterns/practical/idempotency/`](../../src/com/lld/patterns/practical/idempotency/IdempotencyDemo.java)

---

## Domain Events

**Intent:** Let an aggregate announce that something meaningful happened, without knowing or caring
who reacts.

**The problem it solves.** `placeOrder()` starts as five lines and ends as fifty: send a confirmation
email, reserve stock, award loyalty points, notify analytics, queue a fraud check. `OrderService` now
imports a mailer, an inventory client, a loyalty client and an analytics SDK; it can't be unit-tested
without mocking all four; and the analytics service being down means the customer can't buy anything.

**Structure.** An immutable event (a `record` — `OrderPlaced(orderId, customerId, total, occurredAt)`),
a bus mapping event type → subscribers, and handlers registered at startup. The aggregate *records*
events; something else *publishes* them.

**Design decisions worth voicing.**
- **Record in the aggregate, publish after the commit.** This is the detail that separates a correct
  implementation from a broken one. Publishing inside the transaction means a rollback still sent the
  confirmation email for an order that does not exist. Collect events on the aggregate, drain them
  with a `releaseEvents()` that clears as it returns (so a second flush can't double-send), and
  dispatch only once the commit succeeds.
- **Isolate handler failures.** Wrap each subscriber in its own try/catch. Analytics being unreachable
  is not a reason to fail the customer's order — and one handler throwing must not silently skip the
  handlers registered after it.
- **Events are immutable facts in the past tense.** `OrderPlaced`, not `PlaceOrder`. A command can be
  rejected; an event already happened. Include the timestamp and the data handlers need, so they
  aren't forced to call back into you.
- **Synchronous vs asynchronous is a real trade-off, not an upgrade.** In-process synchronous dispatch
  is far easier to debug and keeps the stack trace intact; a slow handler slows the request. Move to a
  queue when handlers do I/O — and accept what that costs: ordering guarantees, at-least-once delivery,
  and handlers that must be **idempotent** because they *will* be re-run.
- **The debugging cost is the honest downside.** "What happens when an order is placed?" stops being
  answerable by reading one method. Keep the subscriber list explicit and in one place.

**Domain Events vs Observer.** Same mechanics, different scope. Observer is one subject notifying its
own listeners, usually about state. Domain Events are typed, self-describing facts routed through a
shared bus, decoupled enough that publisher and subscriber never reference each other — which is what
lets you move a handler out to another service later without touching the publisher.

**In the wild:** Spring's `ApplicationEventPublisher` (with `@TransactionalEventListener(phase =
AFTER_COMMIT)` implementing exactly the commit-first rule above), Guava's `EventBus`, and the outbox
pattern when the events must cross a service boundary reliably.

**When NOT to use it.** When the "reaction" is really a required step of the operation. If the order
is invalid unless stock was reserved, that's a direct call inside the transaction, not an event. Using
events for mandatory sequencing turns a compile-time guarantee into a runtime hope.

**Interview soundbite.** *"The `Order` aggregate records an `OrderPlaced` event rather than calling
the mailer, the inventory service and analytics directly. After the transaction commits I drain the
events and publish them; each handler runs in its own try/catch so a broken analytics integration
can't fail the order. Adding a fraud check later is a new subscriber and zero edits to `OrderService`."*

**Follow-ups you'll get.**
- *"Where do you publish from?"* → After commit, never inside the transaction — otherwise a rollback
  has already sent the email.
- *"A handler fails. Now what?"* → Synchronous: log and continue, so one integration can't fail the
  business operation. Asynchronous: retry with backoff, then a dead-letter queue.
- *"How is this different from Observer?"* → Observer couples a subject to its listeners; a domain
  event is a typed fact on a bus, so publisher and subscriber never know each other exists.

> **Runnable demo:** [`src/com/lld/patterns/practical/domainevents/`](../../src/com/lld/patterns/practical/domainevents/DomainEventsDemo.java)

---

## Result / Either

**Intent:** Return failure as a value the compiler can see, instead of a thrown exception or a `null`.

**The problem it solves.** A method that can fail has four bad options and one good one. `null` tells
the caller nothing about *why* and the compiler says nothing at all. `Optional` says "it might be
empty" but still can't say why. An unchecked exception is invisible in the signature and gets
forgotten until production. A checked exception is visible but doesn't compose — you cannot `flatMap`
a `throw`, so chaining five fallible steps means five nested try/catch blocks. `Result<T, E>` is
visible, composable, and impossible to ignore.

**Structure.** A sealed interface with two implementations — `Ok<T, E>(T value)` and
`Err<T, E>(E error)` — plus `map` (transform the success), `flatMap` (chain another fallible step,
short-circuiting on the first error) and `fold` (finally handle both branches). In Java 17+ this is a
`sealed interface` with two `record`s, and the sealing is what lets the compiler prove you've handled
every case.

**Design decisions worth voicing.**
- **The value is never unwrapped until `fold()`.** There is no window in which you can use the result
  without having handled the error — that's the entire safety argument, and it's stronger than
  anything an exception gives you.
- **`flatMap` short-circuits.** Chain `parseEmail → parseAge → parseCountry` and the first failure
  stops the pipeline; the later steps never run, with no try/catch and no null checks in the caller.
- **Short-circuit *or* accumulate — pick per use case.** Short-circuit when later steps depend on
  earlier ones. Accumulate when validating a form: fixing one field per round trip is miserable UX.
- **`Result` does not replace exceptions.** Use it for outcomes the business already expects —
  validation failures, "not found", "insufficient funds". Keep exceptions for programmer errors and
  genuine infrastructure failure. A codebase that returns `Result` for `OutOfMemoryError` has missed
  the point.
- **The cost is honesty about ceremony.** Java has no `do`-notation, so deep chains get verbose, and
  every caller must engage with the type. That verbosity *is* the feature, but say so out loud rather
  than pretending it's free.
- **Beware record component name clashes.** If `Ok` has a `value()` component, an interface method
  named `value()` with a different return type won't compile. Records reserve their component names.

**In the wild:** Rust's `Result`, Kotlin's `runCatching`, Scala's `Either`, Vavr's `Either` and
`Try` in Java, and gRPC's status-plus-payload responses. Spring's `ProblemDetail` is the same idea at
the HTTP boundary.

**When NOT to use it.** Deep in code where the only failures are bugs — a `NullPointerException`
there is correct and should crash. And be wary of retrofitting it into an exception-based codebase
piecemeal; two error styles at once is worse than either alone.

**Interview soundbite.** *"For expected failures I'd return `Result<User, ValidationError>` rather
than throwing. It's a sealed interface with `Ok` and `Err` records, so the compiler forces the caller
to handle both. `flatMap` chains the validation steps and short-circuits on the first error, so the
happy path reads as if it can't fail. Exceptions stay for programmer errors and infrastructure
failures — things the business logic isn't supposed to expect."*

**Follow-ups you'll get.**
- *"Isn't this just `Optional`?"* → `Optional` models *absence*. `Result` models *failure with a
  reason*, and the reason is what the caller needs to build a 400 response.
- *"How do you collect all validation errors instead of the first?"* → A second combinator that
  evaluates every branch and gathers the `Err`s into a list — deliberately not short-circuiting.
- *"Doesn't this get verbose?"* → Yes, in Java it does. That's the trade: verbosity at the call site
  in exchange for failures that can't be silently skipped.

> **Runnable demo:** [`src/com/lld/patterns/practical/result/`](../../src/com/lld/patterns/practical/result/ResultDemo.java)

---

## Registry / Plugin

**Intent:** Replace the `switch` everyone keeps editing with a map from key to implementation,
populated at startup.

**The problem it solves.** Almost every design grows a `switch (format) { case "csv" ... case "json"
... }`. It works — the problem is what happens next. Every new format edits that method, so every new
format re-tests and can re-break every existing one. That is the Open-Closed violation interviewers
are listening for.

**Structure.** An interface whose implementations declare their own key (`String format()`), a
registry holding `Map<String, Impl>` with `register` / `lookup` / `supportedKeys`, and one composition
root that registers everything at startup. The consuming service does a lookup and nothing else.

**Design decisions worth voicing.**
- **Duplicate registration policy is a real decision, not a detail.** Reject (two plugins claiming
  `"csv"` is a packaging mistake, and failing at startup beats silently exporting the wrong format
  where the winner depends on classpath order) or last-one-wins (correct when overriding *is* the
  point — test doubles, per-tenant plugins). Pick one and justify it; if you allow overriding, make it
  an explicit `register(impl, ALLOW_OVERRIDE)` rather than the silent default.
- **Return `Optional` from `lookup`, not `null` and not a throw.** The caller decides whether an
  unknown key is fatal — and when it throws, the message should list what *is* supported. A bare
  "unknown format: xlsx" makes the caller go read your source.
- **Self-registration in a static block is tempting and usually wrong.** Static initialisers only run
  when the class is first loaded, so if nothing references the class it never loads, never registers,
  and the format silently doesn't exist. Explicit registration in one place is boring and always works.
- **Registry vs Factory.** A Factory *decides* which object to create, so it changes when the product
  set changes. A Registry *looks up* something handed to it and never knows the concrete types, so it
  never changes. If your factory is a bare switch over a string, it wants to be a registry.
- **Registry vs Service Locator — same `Map`, opposite verdicts.** The test: *could the caller have
  known this at construction time?* The export format arrives in the HTTP request, so nobody could
  have injected it — lookup is the only option, and that's fine. A class reaching into a global
  registry for its own fixed collaborators has hidden its dependencies: the constructor lies, the
  compiler can't help, and every test needs global setup. That one is the anti-pattern.

**In the wild:** `java.util.ServiceLoader` plus `META-INF/services` is the JDK's own version — it's
how JDBC finds drivers, how SLF4J finds a backend, and how `Charset` and `FileSystem` providers are
discovered. With it, `ServiceLoader.load(Exporter.class).forEach(registry::register)` means dropping a
jar on the classpath adds a format with no code change at all. Spring bean-name lookup, Jackson module
registration and servlet filter chains are registries too.

**When NOT to use it.** A fixed, small, never-changing set of options. Two payment types that have
existed for a decade don't need plugin infrastructure — a switch is more readable, and a registry is
indirection you pay for at every read.

**Interview soundbite.** *"Instead of a switch on the export format, exporters implement a common
interface and register themselves in a registry at startup keyed by the format they handle. Adding
PDF support becomes one new class and one registration line, with zero edits to `ReportService` —
Open-Closed with a diff to prove it. I'd reject duplicate keys at startup, because a silent override
is a bug you only find in production."*

**Follow-ups you'll get.**
- *"How do plugins get discovered without editing the composition root?"* → `ServiceLoader` and
  `META-INF/services`, or `provides ... with ...` in `module-info`.
- *"Isn't this Service Locator?"* → Only if classes use it to fetch their *own* dependencies. Looking
  up an implementation chosen by request data is exactly what a registry is for.
- *"Two plugins register the same key."* → Startup failure by default. Overriding should be an
  explicit, opt-in flag.

> **Runnable demo:** [`src/com/lld/patterns/practical/registry/`](../../src/com/lld/patterns/practical/registry/PluginRegistryDemo.java)

---

## Honourable mentions (know the idea, rarely coded in an interview)

**DTO (Data Transfer Object).** A flat, behaviour-free carrier for moving data across a boundary
(API, network, layer). Keeps your domain model out of your wire contract so the two can evolve
independently. Java `record`s make these one-liners. Mention it when asked "what does your API
return?"

**Service Locator.** A registry classes query for their dependencies. Widely considered an
anti-pattern versus DI because dependencies become invisible in the signature and failures move from
compile time to runtime. Worth knowing so you can explain *why you'd use DI instead* — and see
[Registry / Plugin](#registry--plugin) above for the line between the legitimate use and this one.

**CQRS.** Separate the model that handles writes from the model that serves reads, so each can be
optimised (and scaled) independently. Leans HLD, but comes up when read and write shapes diverge badly.

**Event Sourcing.** Persist the sequence of state-changing events rather than the current state;
rebuild state by replaying them. Gives a perfect audit trail and time-travel debugging, at the cost of
replay complexity and schema evolution pain. It's Command persisted, taken to its logical conclusion.

**Saga / Compensating Transaction.** The answer to "payment succeeded but shipping failed" across
services: a sequence of local transactions where each step has a compensating action to undo it.
Bring this up whenever a facade orchestrates steps that can't share an ACID transaction.
