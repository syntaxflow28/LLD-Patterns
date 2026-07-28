# Structural Patterns — Deep Dive

Detailed reference for the 7 structural patterns: how objects and classes are composed into larger
structures while keeping those structures flexible.

Code: [`src/com/lld/patterns/structural`](../../src/com/lld/patterns/structural)

---

## Adapter

**Intent:** Convert the interface of a class into another interface clients expect, letting classes
work together that otherwise couldn't due to incompatible interfaces.

**The problem it solves.** You own `PaymentGateway`. Stripe ships `makePayment(currency, cents, ref)`.
You cannot change Stripe, and you don't want Stripe's vocabulary leaking into your domain. Without an
adapter, Stripe-specific types appear in your service layer, and swapping to Razorpay becomes a
rewrite.

**Structure.** *Target* (the interface your code wants), *Adaptee* (the incompatible existing class),
*Adapter* (implements Target, holds an Adaptee, translates calls).

**Two forms.**
- **Object adapter** (preferred): the adapter *holds* the adaptee via composition. Works with any
  adaptee, can adapt subclasses, and Java's single inheritance doesn't get in the way.
- **Class adapter**: the adapter *extends* the adaptee. Impossible in Java when you also need to
  extend something else; also couples you to the adaptee's implementation.

**What the adapter actually does.** More than renaming methods — it translates *data shapes*
(`double dollars` → `long cents`), *error models* (`StripeException` → your `PaymentFailedException`),
and *lifecycle* (stateful SDK client → stateless method call).

**In the wild:** `Arrays.asList()`, `InputStreamReader` (byte stream → char stream),
`Collections.enumeration()`, `java.io.StreamEncoder`.

**When NOT to use it.** When you control both sides — just fix the interface. And don't adapt
"just in case"; an adapter with one implementation and no prospect of a second is indirection
without benefit.

**Interview soundbite.** *"I'll define a `PaymentGateway` interface in my domain and write a thin
`StripeAdapter` that implements it. My services depend only on `PaymentGateway`, so the vendor is a
one-line swap and I can inject a fake in tests. The adapter is also where I normalise their error
codes into my exception hierarchy."*

**Follow-ups you'll get.**
- *"What if the adaptee can't support part of your interface?"* → Either throw a documented
  `UnsupportedOperationException` (an LSP smell — flag it), or narrow the target interface so it only
  promises what every adapter can deliver.
- *"Adapter vs wrapper vs facade?"* → All wrap. Adapter changes the interface to a *specified* one;
  Facade invents a *new simpler* one over many objects; Decorator keeps the *same* one.

---

## Decorator

**Intent:** Attach additional responsibilities to an object dynamically, providing a flexible
alternative to subclassing for extending functionality.

**The problem it solves.** Combinatorial subclass explosion. With coffee add-ons (milk, sugar, whip,
soy, caramel) you'd need $2^5 = 32$ subclasses to cover every combination — and 64 when marketing adds
one more. Decorators compose at runtime: 5 classes cover all 32 combinations.

**Structure.** A *Component* interface; a *ConcreteComponent* (the base object); an abstract
*Decorator* that implements Component and holds a Component; *ConcreteDecorators* that override
methods, call `inner.method()`, and add their own behaviour.

**The mechanism.** Every decorator *is-a* Component *and* *has-a* Component. That's why they nest
arbitrarily deep and why the client can't tell a decorated object from a plain one.

**Order matters — and interviewers test this.** `tax(discount(price))` ≠ `discount(tax(price))`.
When behaviour isn't commutative, the composition order is part of the specification, so document it
or enforce it in a factory.

**In the wild:** the entire `java.io` package (`new BufferedReader(new InputStreamReader(new
FileInputStream(f)))`), `Collections.unmodifiableList()` / `synchronizedList()`,
`HttpServletRequestWrapper`, Spring's `TransactionAwareDataSourceProxy`.

**When NOT to use it.**
- When you need to add *state-dependent* behaviour → that's State.
- When behaviour must sometimes *stop* the chain → that's Chain of Responsibility.
- When there are only two combinations — just write two classes.
- When clients need to reach the concrete type: decorators break `instanceof` and `equals`.

**Interview soundbite.** *"Pizza toppings are additive and combinable, so rather than a class per
combination I'll use decorators: each topping wraps the pizza, adds its cost and appends to the
description. Adding 'extra cheese' is a new 8-line class and zero changes anywhere else."*

**Follow-ups you'll get.**
- *"How do you remove a decorator?"* → You don't, easily. That's a real weakness. Rebuild the chain
  without it, or keep the add-ons in a list and re-apply.
- *"Debugging a 6-deep wrapper stack?"* → Painful, and worth admitting. Stack traces get noisy and
  identity comparisons fail. Give decorators meaningful `toString()`s.
- *"Decorator vs inheritance?"* → Inheritance is compile-time and static; decoration is runtime and
  composable. Favour composition over inheritance.

---

## Facade

**Intent:** Provide a unified, higher-level interface to a set of interfaces in a subsystem, making
the subsystem easier to use.

**The problem it solves.** Placing an order touches inventory, pricing, payment, shipping,
notification, and analytics — six subsystems, a required call order, and error handling between each
step. Without a facade, every caller (web controller, mobile API, admin tool, batch job) reimplements
that orchestration, and they drift apart.

**Structure.** One facade class holding references to subsystem objects, exposing coarse-grained
operations (`placeOrder(...)`) that encapsulate the sequencing.

**Important nuances.**
- A facade **does not forbid** direct subsystem access. Power users can still bypass it — that's by
  design, unlike a strict layer boundary.
- A facade should contain **orchestration**, not business rules. When it starts making pricing
  decisions it's turning into a god object. That's the failure mode to watch for.
- The facade is a natural **transaction boundary** and a natural place for a **Unit of Work**.

**Facade vs Mediator.** A facade is *one-directional*: clients → subsystem. The subsystem doesn't
know the facade exists. A mediator is *bidirectional*: colleagues actively talk *through* it.

**In the wild:** `javax.faces.context.FacesContext`, SLF4J's `LoggerFactory`, Spring's `JdbcTemplate`
(hides `Connection`/`Statement`/`ResultSet`/exception translation), `java.net.URL.openStream()`.

**When NOT to use it.** When the subsystem is already simple — a facade over two method calls is
noise. Also avoid the "facade that just forwards": if every method is a one-line delegate, delete it.

**Interview soundbite.** *"I'll add an `OrderService` facade that owns the checkout choreography:
reserve inventory, charge payment, create shipment, publish the event. Controllers call one method,
so the sequencing and compensation logic exists in exactly one place — and it gives me a clean
transaction boundary."*

**Follow-ups you'll get.**
- *"What if payment succeeds but shipping fails?"* → This is the question. Answer with compensating
  transactions / the Saga pattern: the facade must undo prior steps or enqueue a compensation event.
  Distributed steps can't share an ACID transaction.
- *"Does the facade become a god class?"* → It can. Keep it to orchestration; push rules down into
  domain objects; split per use-case if it grows (`CheckoutFacade`, `ReturnsFacade`).

---

## Composite

**Intent:** Compose objects into tree structures to represent part-whole hierarchies, letting clients
treat individual objects and compositions of objects uniformly.

**The problem it solves.** Client code littered with `if (node instanceof Folder) { recurse } else
{ handle }`. Composite pushes the recursion into the structure itself: `node.size()` works whether
`node` is a file or a 12-level directory tree.

**Structure.** A *Component* interface with the shared operations; *Leaf* implementations with no
children; *Composite* implementations holding `List<Component>` and delegating operations to children.

**The classic design debate — where do `add()`/`remove()` go?**
- **On the Component** (GoF's "transparency"): uniform interface, but `File.add()` is meaningless and
  must throw. Violates LSP/ISP.
- **On the Composite only** ("safety"): type-safe, but clients must downcast to add children.

Say the trade-off out loud and pick safety unless the interviewer wants uniformity. There's no free
answer, and knowing that *is* the answer.

**Real LLD uses.** File systems, org charts, UI component trees, nested menus, XML/JSON/AST nodes,
bill-of-materials, grouped discounts, permission groups containing groups.

**In the wild:** `java.awt.Container` (holds `Component`s, is itself a `Component`), Swing's
`JPanel`, DOM `Node`, `CompositeName` in JNDI.

**When NOT to use it.** When the hierarchy isn't genuinely uniform — if leaves and composites need
substantially different operations, forcing one interface produces a bag of unsupported methods.

**Interview soundbite.** *"Directories and files both implement `FileSystemNode` with `size()` and
`accept(visitor)`. `Directory.size()` sums its children recursively, so client code computes the size
of the whole tree with a single polymorphic call and never type-checks."*

**Follow-ups you'll get.**
- *"How do you handle cycles?"* → A tree shouldn't have them; guard `add()` against adding an
  ancestor, or track visited nodes if the structure is really a graph.
- *"Very deep trees?"* → Recursion risks `StackOverflowError`; convert to an explicit stack/queue
  traversal. Also consider caching computed aggregates and invalidating up the parent chain.
- *"How do you add new operations?"* → Either add to the Component interface (touches every class) or
  pair Composite with **Visitor**, which is exactly why those two patterns are so often taught together.

---

## Proxy

**Intent:** Provide a surrogate or placeholder for another object to control access to it.

**The problem it solves.** You want to add caching, authorization, lazy loading, logging, or
throttling around an object — without changing the object and without changing its clients. The proxy
implements the same interface, so it's a drop-in substitute.

**The five flavours (name them — it shows depth).**
| Flavour | Purpose | Example |
|---|---|---|
| **Virtual** | Delay expensive creation until first use | Lazy-loading a 4 MB image or an ORM association |
| **Protection** | Enforce access control | Reject the call unless the caller has the role |
| **Caching** | Memoize results | Return cached response, skip the network |
| **Remote** | Represent an object in another address space | RMI stub, gRPC client stub |
| **Smart reference** | Extra bookkeeping on access | Reference counting, lock acquisition, metrics |

**Proxy vs Decorator — the distinction interviewers push on.** Structurally identical: both implement
the target interface and hold an instance of it. The difference is **intent and lifecycle**. A
decorator *adds behaviour* and is composed by the client, which supplies the wrapped object. A proxy
*controls access* and typically owns/creates the real subject, and may decide **not to call it at all**
(cache hit, permission denied). "Same shape, different intent" is the correct answer.

**In the wild:** `java.lang.reflect.Proxy` (dynamic proxies), Spring AOP (`@Transactional`, `@Cacheable`
are proxy-based), Hibernate lazy-loading proxies, RMI stubs, `Collections.unmodifiableX` (arguably a
protection proxy).

**When NOT to use it.** When the added concern belongs in the object itself, or when the indirection
hides latency in a way that misleads callers — a "field access" that silently issues a SQL query is
how N+1 problems get shipped.

**Interview soundbite.** *"I'll front the pricing service with a caching proxy implementing the same
`PricingService` interface. Callers don't change; the proxy checks a TTL cache and only hits the real
service on a miss. Because it's the same interface, I can also stack a rate-limiting proxy behind it."*

**Follow-ups you'll get.**
- *"How do you invalidate the cache?"* → The hard part. TTL, explicit invalidation on write, or
  event-driven eviction. Say which and why; unbounded caches are memory leaks — bound the size (LRU).
- *"Thread safety of the cache?"* → `ConcurrentHashMap.computeIfAbsent` gives you atomic
  single-computation semantics; a plain `HashMap` here is a data race.

---

## Bridge

**Intent:** Decouple an abstraction from its implementation so that the two can vary independently.

**The problem it solves.** **Two independent dimensions of variation.** With 4 shapes × 3 renderers,
inheritance forces 12 classes; add a renderer and you write 4 more. Bridge converts M×N into M+N by
making one dimension a *field* of the other.

**How to spot it in an interview.** Listen for "×": *notification type × channel*, *report format ×
data source*, *device × remote control*, *shape × renderer*, *message × transport*. The moment class
names start reading like `UrgentEmailNotification`, you need a bridge.

**Structure.** *Abstraction* (holds a reference to Implementor, exposes high-level ops),
*RefinedAbstraction* (subclasses varying the first dimension), *Implementor* interface,
*ConcreteImplementors* (the second dimension).

**Bridge vs Strategy — genuinely confusing, here's the line.** Structurally near-identical (both
delegate to an injected interface). Strategy swaps **one algorithm** and is usually changed at
runtime; Bridge separates **an entire abstraction hierarchy from an implementation hierarchy** and is
usually fixed at construction. Bridge is architectural; Strategy is tactical.

**Bridge vs Adapter.** Timing and intent. Adapter is *retrofitted* to make existing incompatible code
work. Bridge is designed *up front* so both sides can evolve.

**In the wild:** JDBC (`java.sql.Driver` is the implementor; your code uses the `Connection`
abstraction), SLF4J (API abstraction bridged to Logback/Log4j implementations), AWT peer classes,
`java.util.logging.Handler`.

**When NOT to use it.** When there's only one dimension of variation, or when the second dimension
has exactly one implementation and always will. Bridge adds a layer of indirection — it needs to earn it.

**Interview soundbite.** *"Notifications vary on two axes: severity/type (order shipped, OTP, alert)
and channel (email, SMS, push). Subclassing both gives me a combinatorial mess, so I'll bridge:
`Notification` holds a `Channel` implementor. Adding WhatsApp is one class, and every notification
type immediately supports it."*

**Follow-ups you'll get.**
- *"Can this be Strategy instead?"* → For a single method, yes, and Strategy is simpler. Bridge earns
  its keep when the abstraction side has its own rich hierarchy and state.

---

## Flyweight

**Intent:** Use sharing to support large numbers of fine-grained objects efficiently.

**The problem it solves.** Memory. A million `Character` objects each holding font, size, colour,
typeface and a glyph bitmap is gigabytes. But almost all of them share the same font data — only the
*position* differs.

**The central concept — split the state.**
- **Intrinsic:** shared, immutable, context-independent (glyph bitmap, tree texture, chess piece
  colour+type). Lives *inside* the flyweight.
- **Extrinsic:** unique per usage, context-dependent (x/y coordinates, board square). **Passed in as
  method parameters** — never stored in the flyweight.

If the split isn't clean, the pattern doesn't apply. Getting this articulation right is the whole
interview answer.

**Structure.** A *Flyweight* class holding only intrinsic state; a *FlyweightFactory* with a cache
(`Map<key, Flyweight>`) that guarantees sharing; *Context* objects holding extrinsic state plus a
flyweight reference.

**Non-negotiable constraint.** Flyweights **must be immutable**. They're shared across thousands of
contexts and usually across threads; one mutable field is a correctness disaster.

**In the wild:** `Integer.valueOf()` (caches −128..127 — this is why `==` on small boxed ints
"works" and on large ones doesn't), `String` interning and the string constant pool,
`Character.valueOf()`, `Boolean.valueOf()`.

**When NOT to use it.** When you don't have *many thousands* of objects — the factory's map lookup
and the indirection cost more than you save. Measure first; this is the most commonly
over-applied pattern.

**Interview soundbite.** *"For a map with 100k pins I'll split state: icon, colour and label style are
intrinsic and shared through a `PinTypeFactory`; latitude, longitude and the pin's id are extrinsic
and passed to `draw()`. That turns 100k heavy objects into 100k tiny contexts plus a dozen shared
flyweights."*

**Follow-ups you'll get.**
- *"Is your factory thread-safe?"* → Use `ConcurrentHashMap.computeIfAbsent` so two threads can't
  create duplicate flyweights, which would silently defeat the pattern.
- *"How do you bound the cache?"* → An unbounded flyweight cache is itself a leak. Bound it, or use
  weak references if flyweights can be recreated cheaply.
- *"Flyweight vs Singleton?"* → Singleton: exactly one instance, arbitrary state. Flyweight: many
  instances, one per distinct *intrinsic* value, immutable, obtained through a factory.
