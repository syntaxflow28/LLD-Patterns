# Behavioral Patterns — Deep Dive

Detailed reference for the 11 behavioral patterns: how objects communicate, distribute
responsibility, and encapsulate varying behaviour. This is where most LLD interviews are won.

Code: [`src/com/lld/patterns/behavioral`](../../src/com/lld/patterns/behavioral)

---

## Strategy

**Intent:** Define a family of algorithms, encapsulate each one, and make them interchangeable.
Strategy lets the algorithm vary independently from the clients that use it.

**The problem it solves.** The growing conditional:

```java
if (type == CREDIT) { /* 20 lines */ }
else if (type == UPI) { /* 25 lines */ }
else if (type == WALLET) { /* 18 lines */ }   // ...and one more every quarter
```

This method is untestable in isolation, gets merge conflicts from three teams at once, and must be
edited for every new payment type. Strategy turns each branch into a class.

**Structure.** A *Strategy* interface with one method; *ConcreteStrategies*; a *Context* that holds a
strategy reference and delegates to it. The client chooses which strategy the context gets.

**Why it's the #1 LLD pattern.** It's the most direct expression of "encapsulate what varies" and it
satisfies OCP, DIP and SRP simultaneously. It also composes with everything: a **Factory** picks the
strategy, a **Map<Enum, Strategy>** registry removes the last conditional, and each strategy is
independently unit-testable.

**Modern Java note.** For single-method strategies, the interface is a functional interface — callers
can pass a lambda or a method reference. Mention that `Comparator` is a Strategy and that
`list.sort(comparator)` is the pattern in the standard library.

**Killing the last `if`.** Register strategies in a map and let the enum carry its own strategy:

```java
private static final Map<PaymentType, PaymentStrategy> STRATEGIES = Map.of(
        PaymentType.CREDIT, new CreditCardPayment(),
        PaymentType.UPI,    new UpiPayment());
```

**In the wild:** `Comparator`, `ThreadPoolExecutor`'s `RejectedExecutionHandler`, `LayoutManager` in
Swing, `Collections.sort(list, cmp)`, `java.util.function.Function` used as injected behaviour.

**When NOT to use it.** Two stable branches that will never grow — an `if` is honest and shorter.
Also avoid it when the "strategies" need wildly different inputs; if the interface accumulates
parameters only one implementation uses, the abstraction is wrong.

**Interview soundbite.** *"Fee calculation varies by vehicle type and will keep changing, so I'll
extract a `FeeStrategy` interface with `calculate(Ticket)`. The `ParkingLot` holds a strategy and
never knows the formula. Adding weekend surge pricing is a new class plus a registry entry — no
existing code is touched, and each strategy is trivially unit-testable."*

**Follow-ups you'll get.**
- *"Where do strategies get created?"* → A factory or a registry map, wired at startup. Don't
  `new` them at the call site — that reintroduces the coupling you removed.
- *"Strategies need different data?"* → Pass a context/parameter object, or accept that the
  abstraction is leaking and reconsider the boundary.
- *"Strategy vs State?"* → See the dedicated comparison below.

---

## Observer

**Intent:** Define a one-to-many dependency between objects so that when one object changes state,
all its dependents are notified and updated automatically.

**The problem it solves.** The subject shouldn't need to know its consumers. When an order ships, you
must email the customer, push a notification, update analytics, and notify the warehouse — and next
quarter, also call a partner webhook. Hard-coding those calls in `Order.ship()` means editing core
domain logic for every new consumer.

**Structure.** *Subject* (maintains `List<Observer>`, exposes `subscribe`/`unsubscribe`/`notify`);
*Observers* implementing a single `update(...)` method.

**Push vs pull — a real design decision.**
- **Push:** the subject sends the changed data (`update(stock, price)`). Simple, but the subject must
  guess what observers need, and the signature changes as needs grow.
- **Pull:** the subject sends only itself (`update(subject)`) and observers query what they want.
  More flexible, but chattier and risks observers reading inconsistent intermediate state.

**Problems you must be ready to discuss.**
1. **Memory leaks / lapsed listeners.** The subject holds strong references; an observer that forgets
   to unsubscribe never gets collected. Fix with explicit lifecycle, weak references, or
   auto-unsubscribing handles.
2. **Notification order is undefined.** Never let observers depend on running in a particular order.
3. **Synchronous notification blocks the subject.** One slow observer stalls the publisher — and an
   exception in one observer can prevent the rest from being notified. Wrap each callback in
   try/catch, or dispatch asynchronously via a queue.
4. **Concurrent modification.** An observer that unsubscribes during notification mutates the list
   you're iterating. Use `CopyOnWriteArrayList` or iterate over a snapshot.
5. **Cascading updates.** Observer A updates B, which notifies C, which updates A. Guard against
   re-entrancy.

**In the wild:** `java.util.EventListener` (all Swing/AWT listeners), `PropertyChangeListener`,
Reactive Streams / RxJava (Observer with back-pressure), Spring's `ApplicationEventPublisher`,
Kafka consumers (the distributed version). `java.util.Observer` was **deprecated in Java 9** — say
this; it signals currency.

**When NOT to use it.** When there's exactly one consumer forever (just call it), or when you need
*coordination* rather than *broadcast* (that's Mediator), or when delivery must be guaranteed across
process boundaries (that needs a real message broker, not in-process observers).

**Interview soundbite.** *"Order status changes fan out to several consumers, so `Order` will publish
events rather than call services directly. Observers register at startup. I'd notify asynchronously
through a queue so a slow analytics sink can't add latency to checkout, and I'd wrap each listener
call in try/catch so one failure doesn't suppress the rest."*

**Follow-ups you'll get.**
- *"Sync or async notification?"* → Sync gives ordering and simple error handling; async gives
  isolation and latency. State the trade-off, then pick based on whether observers are on the
  critical path.
- *"Guaranteed delivery?"* → In-process observers give none. If it matters, persist the event first
  (outbox pattern) and let a relay deliver it.

---

## State

**Intent:** Allow an object to alter its behaviour when its internal state changes. The object will
appear to change its class.

**The problem it solves.** State machines implemented as flags produce this:

```java
if (status == IDLE && !hasCoin) { ... }
else if (status == HAS_COIN && !dispensing) { ... }   // repeated in every method
```

Every method re-derives the same conditions, illegal transitions are only caught at runtime (if at
all), and adding a state means auditing every method in the class.

**Structure.** A *State* interface declaring one method per event; *ConcreteStates* implementing the
behaviour **and the transitions**; a *Context* holding the current state and delegating every public
method to it.

**The defining characteristic.** States own their transitions — `HasCoinState.selectProduct()` calls
`context.setState(dispensing)`. Behaviour and the transition table live together, so the machine is
readable state by state, and an illegal transition is simply a method that says "you can't do that
now."

**Design decisions worth voicing.**
- **Stateless states can be singletons** (share one instance); stateful ones must be per-context.
- **Who transitions — state or context?** State-driven (as above) keeps rules local; context-driven
  centralizes a transition table. State-driven scales better.
- **Entry/exit actions:** add `onEnter(ctx)`/`onExit(ctx)` hooks when transitions have side effects
  (start a timer, log an audit record).
- **For simple machines**, a Java `enum` with abstract methods per constant is an elegant, compact
  implementation — worth mentioning.

**Strategy vs State — the comparison you will be asked.**

| | Strategy | State |
|---|---|---|
| Who chooses? | The **client** injects it | The object **transitions itself** |
| Do implementations know each other? | No — independent | Yes — each knows its successors |
| Changes over lifetime? | Usually set once | Changes constantly, by design |
| Intent | Vary an algorithm | Vary behaviour by lifecycle |

Same UML, opposite intent. Say exactly that.

**In the wild:** `Thread.State`, TCP connection states, Spring Statemachine, `Matcher` internals,
order/booking lifecycles in every commerce system.

**When NOT to use it.** Two states and three methods — a boolean is fine. State pays off when the
number of (state × event) pairs is large enough that conditionals become unreadable.

**Interview soundbite.** *"The ticket lifecycle — issued, paid, exited, lost — has different valid
operations in each state, so I'll model it with the State pattern instead of a status enum plus
guards. Each state class implements only the events it allows and performs its own transitions, so
illegal operations fail in one obvious place and adding a 'disputed' state doesn't touch existing
states."*

**Follow-ups you'll get.**
- *"How do you persist state?"* → Store a state *identifier* (enum/string) and rehydrate the state
  object via a factory. Never serialize the state object itself.
- *"Concurrent transitions?"* → Guard the transition with a lock or a CAS on an
  `AtomicReference<State>` so two threads can't both move out of the same state.
- *"How do you test it?"* → Table-driven tests over (current state, event) → (next state, effect).

---

## Command

**Intent:** Encapsulate a request as an object, thereby letting you parameterize clients with
different requests, queue or log requests, and support undoable operations.

**The problem it solves.** A method call is ephemeral — you can't store it, queue it, retry it, log
it, or reverse it. Turning the call into an object makes all of that possible.

**Structure.** *Command* interface (`execute()`, often `undo()`); *ConcreteCommands* holding a
receiver plus the parameters needed; *Receiver* (the object doing real work); *Invoker* (triggers
commands, owns history); *Client* (creates and wires commands).

**What the pattern unlocks (this is the "why").**
- **Undo/redo** — an undo stack and a redo stack of commands.
- **Queuing & scheduling** — commands are serializable work units for a thread pool or job queue.
- **Logging & replay** — persist commands and re-execute to rebuild state (this is Event Sourcing's ancestor).
- **Macros** — a `CompositeCommand` holding a list of commands (Command + Composite).
- **Transactions** — execute all, or undo the ones that succeeded.

**Undo strategies — know both.**
- **Inverse operation:** the command stores the *delta* and reverses it (`delete(n)` undoes
  `append(text)`). Memory-cheap, but every command needs correct inverse logic and not all operations
  are invertible.
- **Snapshot (Memento):** capture full state before executing, restore on undo. Simple and always
  correct, but memory-heavy.

Real editors use both: deltas for typing, snapshots at checkpoints.

**In the wild:** `Runnable` (the canonical command — `executor.submit(runnable)` is Command +
thread pool), `Callable`, Swing's `Action`, `javax.swing.undo.UndoManager`.

**When NOT to use it.** Simple direct calls with no need for undo, queuing, or logging. Command adds
a class per operation — that's a real cost when you get nothing back for it.

**Interview soundbite.** *"Each chess move becomes a `MoveCommand` holding the piece, source, target
and any captured piece. The invoker keeps an undo stack, so takeback is popping a command and calling
`undo()`. The same objects serialize into a move log for replay and PGN export — one abstraction, three
features."*

**Follow-ups you'll get.**
- *"Redo?"* → Two stacks. Undo pops from undo → pushes to redo; a *new* command clears the redo stack.
- *"How much history do you keep?"* → Bound it (last N commands) or snapshot periodically and discard
  older deltas; unbounded history is a memory leak.
- *"Command vs Strategy?"* → Command encapsulates *what to do* including its arguments and receiver;
  Strategy encapsulates *how to do* one thing. Command is typically one-shot; Strategy is reusable.

---

## Chain of Responsibility

**Intent:** Avoid coupling the sender of a request to its receiver by giving more than one object a
chance to handle it. Chain the receivers and pass the request along until one handles it.

**The problem it solves.** A sender that must know every possible handler and their selection rules:
`if (amount <= 1000) lead.approve() else if (amount <= 10000) manager.approve() else ...`. That logic
duplicates everywhere a request is raised, and reordering approvers means editing every call site.

**Structure.** An abstract *Handler* with a `next` reference and a `handle(request)` that either
processes or forwards; *ConcreteHandlers* implementing the decision; a *Client* that builds the chain
and hands the request to the head.

**Two chain semantics — be explicit about which you're building.**
- **Pure / "first match wins":** exactly one handler processes it and the chain stops. Approvals,
  exception handlers, dispatchers.
- **Impure / "pipeline":** every handler contributes and passes along. Middleware — auth → rate limit
  → logging → handler.

**Design points that earn credit.**
- **What if nobody handles it?** Define the policy: throw, return a default, or log. Silent
  swallowing is a bug factory.
- **Chain construction** is a separate concern — build it in a factory/config so ordering is data,
  not code.
- Handlers can be **reordered or inserted** at runtime, which is the pattern's main payoff.
- Debugging is harder: a request that silently falls off the end gives you no stack trace. Log the
  traversal.

**In the wild:** `javax.servlet.Filter` chains, Spring Security's filter chain, OkHttp/Retrofit
interceptors, `java.util.logging.Logger` parent delegation, exception handling itself (`catch` blocks
are a compiler-generated chain), Netty's `ChannelPipeline`.

**When NOT to use it.** When exactly one handler is always correct and known — just call it. And when
handling is mandatory: CoR doesn't *guarantee* anyone handles the request, which is either a feature
or a bug depending on your problem.

**Interview soundbite.** *"ATM dispensing is a natural chain: the ₹2000 handler dispenses as many
notes as it can, then delegates the remainder to ₹500, then ₹100. Each handler knows only its own
denomination and its successor, so adding a ₹200 note is one class and one link — and I can reorder
the chain from config."*

**Follow-ups you'll get.**
- *"CoR vs Decorator?"* → Both wrap and delegate. A CoR handler may **stop** the chain and is chosen
  dynamically; a decorator always delegates onward and always adds behaviour.
- *"How do you avoid a long chain's latency?"* → Order handlers by likelihood/cheapness (cheap
  rejections first), or index handlers by request type instead of walking linearly.

---

## Template Method

**Intent:** Define the skeleton of an algorithm in an operation, deferring some steps to subclasses.
Template Method lets subclasses redefine certain steps without changing the algorithm's structure.

**The problem it solves.** Several classes run the *same sequence* with a couple of different steps.
Copy-paste gives you five near-identical methods that drift apart; the sixth developer forgets to call
`validate()`.

**Structure.** An abstract base class with a `final` template method that calls: concrete steps
(shared), abstract steps (must be overridden), and **hooks** (optional overrides with a default).

**Details that matter.**
- **Make the template method `final`.** Otherwise a subclass can reorder or skip steps, defeating the
  point.
- **Hooks vs abstract steps.** Abstract = "you must supply this"; hook = "you may customize this."
  A `boolean shouldNotify()` returning `false` by default is a hook.
- **Keep the number of abstract steps small.** Six abstract methods means subclasses are really
  writing the whole algorithm and you've gained nothing.
- **The Hollywood Principle:** "Don't call us, we'll call you." The framework calls your steps, not
  the reverse. This is the essence of a framework versus a library.

**Template Method vs Strategy — the trade-off.**

| | Template Method | Strategy |
|---|---|---|
| Mechanism | Inheritance (compile-time) | Composition (runtime) |
| Granularity | Varies **steps** of one algorithm | Swaps the **whole** algorithm |
| Changeable at runtime? | No | Yes |
| Reuse of shared code | Automatic (in the base class) | Must be duplicated or extracted |
| Coupling | Tight (subclass ↔ base) | Loose |

Modern advice favours Strategy (composition over inheritance), but Template Method is the better fit
when the steps genuinely share a lot of base-class state and the algorithm is fixed.

**In the wild:** `java.util.AbstractList` / `AbstractMap` (implement `get`/`size`, inherit the rest),
`InputStream.read()`, `HttpServlet.service()` dispatching to `doGet`/`doPost`, JUnit's
`setUp`/`test`/`tearDown` lifecycle, Spring's `JdbcTemplate`.

**When NOT to use it.** When the "shared skeleton" is one line, or when you need to vary behaviour at
runtime — inheritance is fixed at compile time.

**Interview soundbite.** *"Every payment flow does validate → authorize → capture → record, but the
authorize and capture steps differ per provider. I'll put the sequence in a `final` template method on
an abstract `PaymentProcessor`, keep validate and record concrete, and make authorize/capture abstract.
A new provider can't accidentally skip validation."*

**Follow-ups you'll get.**
- *"How is this different from just calling four methods?"* → The base class *guarantees* the order
  and that no step is skipped; subclasses can't restructure it.
- *"What if a subclass needs a different order?"* → Then Template Method is the wrong pattern — move
  to Strategy or a pipeline of composable steps.

---

## Iterator

**Intent:** Provide a way to access the elements of an aggregate object sequentially without exposing
its underlying representation.

**The problem it solves.** Returning your internal `List` lets callers mutate it and welds your API
to that data structure forever. Exposing `get(i)` forces index-based traversal even for a tree or a
linked list. An iterator gives sequential access while keeping the structure private and swappable.

**Structure.** An *Iterator* interface (`hasNext`, `next`) and an *Aggregate* that produces iterators.
In Java: implement `Iterable<T>` to get for-each support for free.

**Points worth making.**
- **Multiple simultaneous traversals.** Each `iterator()` call returns an independent cursor with its
  own position — that's why the state lives in the iterator, not the collection.
- **Multiple traversal orders** over one structure (in-order/pre-order/level-order for a tree;
  forward/reverse for a playlist) without exposing the nodes.
- **Fail-fast vs fail-safe.** JDK collections are fail-fast: a `modCount` check throws
  `ConcurrentModificationException` if the collection changes mid-iteration. `CopyOnWriteArrayList`
  is fail-safe — it iterates a snapshot. Knowing this distinction is a strong Java signal.
- **Internal vs external.** External = the client drives (`while (it.hasNext())`). Internal = the
  collection drives (`forEach(consumer)`, streams). Java offers both.
- **Lazy iterators** let you stream infinite or paginated sources — an iterator over a REST API that
  fetches the next page inside `hasNext()` is a great LLD answer.

**In the wild:** `java.util.Iterator`, `ListIterator` (bidirectional), `Scanner`, `Spliterator` (the
parallel-friendly evolution), `Stream` (internal iteration), `Enumeration` (the legacy version).

**When NOT to use it.** When a plain `List` return is fine and immutability is guaranteed
(`List.copyOf(...)`). Don't hand-roll an iterator when the JDK's collection already gives you one.

**Interview soundbite.** *"I'll make `Playlist` implement `Iterable<Song>` so callers use for-each and
never see the backing list, and expose a separate `shuffledIterator()`. If the source is paginated I'd
make the iterator lazy so `hasNext()` fetches the next page on demand — callers get a uniform loop
regardless of the transport."*

**Follow-ups you'll get.**
- *"How do you support concurrent modification?"* → Fail-fast with `modCount` (detect and throw), or
  fail-safe by iterating a snapshot. Pick based on whether stale reads are acceptable.
- *"Iterator vs Stream?"* → Iterator is external, stateful, reusable per call; Stream is internal,
  single-use, and composes operations. Streams are usually the better public API in modern Java.

---

## Mediator

**Intent:** Define an object that encapsulates how a set of objects interact. Mediator promotes loose
coupling by keeping objects from referring to each other explicitly.

**The problem it solves.** N objects that each hold references to the others form N×N coupling. Ten
components means up to 90 references, and every new component requires touching the existing ones.
Mediator collapses that to N×1: everyone knows the mediator; nobody knows anybody else.

**Structure.** A *Mediator* interface; a *ConcreteMediator* holding references to all colleagues and
implementing the interaction logic; *Colleagues* that hold only a mediator reference and communicate
exclusively through it.

**What the mediator legitimately does.** Route messages, transform them, enforce policy (bans,
permissions, rate limits), maintain the roster, and sequence multi-party interactions. Because all
coordination is in one class, you can read the system's interaction rules in one place.

**The failure mode — say it before they ask.** The mediator becomes a **god object**. All the
complexity you removed from colleagues has to live somewhere. Mitigations: split by concern
(`ChatRoutingMediator` vs `ModerationMediator`), keep colleague-local logic in the colleague, and
consider an event bus when the coordination is genuinely just broadcast.

**Mediator vs Observer.** Observer is a one-way broadcast: the subject doesn't care who listens and
has no logic about them. Mediator is bidirectional and *opinionated* — it decides who hears what,
whether to transform it, and whether to block it. Many real systems use a mediator implemented with
observers internally.

**Mediator vs Facade.** Facade is one-directional (client → subsystem) and the subsystem doesn't know
it exists. Mediator's colleagues actively depend on it and send messages through it.

**In the wild:** `java.util.Timer` (coordinates scheduled tasks), `ExecutorService` (mediates between
task submitters and worker threads), Spring MVC's `DispatcherServlet`, air-traffic-control and
matching-engine designs, the MVC "Controller" role.

**When NOT to use it.** With only two or three colleagues — direct references are simpler. And when
you actually need broadcast without coordination — use Observer.

**Interview soundbite.** *"Riders and drivers shouldn't hold references to each other, so a
`RideMatchingMediator` owns the interaction: drivers publish availability, riders publish requests,
and the mediator applies the matching, surge and cancellation rules. Adding a new participant type —
say, a fleet dispatcher — doesn't touch riders or drivers at all."*

**Follow-ups you'll get.**
- *"How do you stop the mediator becoming a god class?"* → Split by bounded concern, push
  colleague-local rules back into colleagues, and extract the matching algorithm into a Strategy the
  mediator delegates to.

---

## Memento

**Intent:** Without violating encapsulation, capture and externalize an object's internal state so
the object can be restored to this state later.

**The problem it solves.** To implement undo you need the object's private state — but exposing
getters/setters for everything to enable undo destroys encapsulation and lets any caller corrupt the
object.

**Structure — three strictly separated roles.**
- **Originator:** the object being snapshotted. Only it can create a memento (`save()`) and interpret
  one (`restore(m)`).
- **Memento:** the snapshot. **Opaque** — the caretaker can hold it but not read it. In Java, achieve
  this with a private nested class, package-private accessors, or an interface exposing nothing.
- **Caretaker:** stores mementos (usually a stack) and hands them back. Never inspects them.

That opacity is the whole point of the pattern; if the caretaker can read the memento's fields, you've
just leaked the originator's internals with extra steps.

**Design considerations.**
- **Memory cost.** Full snapshots of a large object are expensive. Mitigate with incremental mementos
  (store the diff), snapshot intervals, or compression. Bound the history.
- **Mementos should be immutable** — a mutated snapshot is a corrupted restore point.
- **Deep vs shallow.** If the state includes mutable references, the memento must deep-copy them or
  the "snapshot" changes underneath you.

**Memento vs Command for undo.**

| | Memento | Command undo |
|---|---|---|
| Mechanism | Restore full state | Apply the inverse operation |
| Memory | High (state per step) | Low (delta per step) |
| Complexity | Low — always correct | High — every op needs a correct inverse |
| Works when ops aren't invertible? | Yes | No |

Real editors combine them: commands for typing (cheap deltas), mementos at checkpoints.

**In the wild:** `java.io.Serializable` (serialization is a general-purpose memento mechanism),
`javax.faces.component.StateHolder`, database savepoints, `Date`'s defensive copies.

**When NOT to use it.** When the state is small and public anyway (just copy it), or when the
operation has a trivial inverse (use Command).

**Interview soundbite.** *"For undo I'll add `save()`/`restore()` on the editor returning an opaque
`EditorSnapshot` whose fields aren't visible to the history stack. The caretaker just pushes and pops
them. To bound memory I'd snapshot every N operations and use command deltas in between."*

**Follow-ups you'll get.**
- *"Isn't this just serialization?"* → Serialization is one *implementation*; Memento is the role
  structure that preserves encapsulation. Serialization also breaks on transient/derived state.
- *"How do you bound memory?"* → Cap the stack depth, store diffs, or persist older mementos to disk.

---

## Visitor

**Intent:** Represent an operation to be performed on the elements of an object structure. Visitor
lets you define a new operation without changing the classes of the elements on which it operates.

**The problem it solves.** You have a stable hierarchy (cart items, AST nodes, file-system nodes) and
a growing list of *operations* over it (tax, shipping, export to PDF/HTML/Markdown, type-check,
optimize). Adding each operation as a method means editing every element class — and unrelated
concerns (tax rules, PDF layout) pile up inside your domain objects, wrecking SRP.

**Structure.** A *Visitor* interface with one `visit(ConcreteElement)` overload per element type;
*ConcreteVisitors* (one per operation); an *Element* interface with `accept(Visitor)`; each element's
`accept` calls `visitor.visit(this)`.

**Double dispatch — the mechanism, and the thing to explain.** Java dispatches on the runtime type of
the *receiver* only. `visitor.visit(element)` would pick the overload by the element's *static* type —
wrong. So you dispatch twice: `element.accept(visitor)` resolves the element's real type, then inside
that method `visitor.visit(this)` resolves the visitor's real type with `this` now statically typed
correctly. Being able to explain this cleanly is the whole point of the question.

**The trade-off — state it immediately.**
- ✅ Adding a new **operation** = one new visitor class, zero changes to elements.
- ❌ Adding a new **element type** = every existing visitor must be updated (and won't compile until
  it is — which is at least a *safe* failure).

So: **use Visitor when the element hierarchy is stable and operations churn.** If element types churn,
Visitor is actively the wrong choice. This is the "expression problem," and naming it is a strong signal.

**Modern Java alternative.** Sealed interfaces + pattern-matching `switch` (Java 21) give you the same
exhaustiveness checking with far less ceremony:

```java
sealed interface CartItem permits Book, Electronics, Groceries {}
double tax = switch (item) {
    case Book b -> 0;
    case Electronics e -> e.price() * 0.18;
    case Groceries g -> g.price() * 0.05;
};   // compiler enforces exhaustiveness
```
Mentioning this shows you know *why* Visitor existed and that the language has since addressed it.

**In the wild:** `javax.lang.model.element.ElementVisitor` (annotation processing),
`java.nio.file.FileVisitor` (`Files.walkFileTree`), compiler/AST passes, `DocumentVisitor` in
document-processing libraries.

**When NOT to use it.** When element types are still being added, when there's only one operation, or
when a simple polymorphic method on the elements is more natural. Visitor is heavy ceremony — it needs
several operations to pay for itself.

**Interview soundbite.** *"Cart item types are stable, but the operations over them keep growing —
tax, shipping, loyalty points, invoice lines. I'll use Visitor so each operation is a self-contained
class and my domain objects stay free of tax logic. The cost is that a new item type breaks every
visitor; since item types rarely change, that's the right trade."*

**Follow-ups you'll get.**
- *"What if you can't modify the element classes to add `accept()`?"* → Then Visitor isn't available;
  fall back to `instanceof` chains, pattern matching, or a `Map<Class<?>, Handler>` dispatch table.
- *"Visitor + Composite?"* → The canonical pairing: Composite gives the tree, Visitor gives operations
  over it, and `accept()` on a composite recurses into children.

---

## Interpreter

**Intent:** Given a language, define a representation for its grammar along with an interpreter that
uses the representation to interpret sentences in the language.

**The problem it solves.** Business rules that must be **defined by non-developers or changed without
a deploy**: discount conditions, alert thresholds, search filters, feature-flag targeting. Hard-coding
them means a release for every rule change.

**Structure.** An *AbstractExpression* with `interpret(context)`; *TerminalExpressions* (literals,
variables); *NonterminalExpressions* (and/or/not, +, ×) that hold child expressions and recurse; a
*Context* carrying the variable bindings. The result is a tree; interpretation is a post-order walk.

**The honest framing.** Interpreter is really "Composite applied to a grammar." Each grammar rule
becomes a class, so the pattern only scales to **small, stable grammars** — a class per rule gets
unmanageable fast. For anything real, use ANTLR, a Pratt parser, or an embedded expression language
(SpEL, MVEL, CEL). Say this; it shows judgment.

**Note what's missing.** Interpreter covers *evaluation*, not *parsing*. Turning `"price > 100 AND
category = books"` into the tree is a separate parser you also have to write. Interviewers who ask for
a rule engine usually want you to name that split.

**Real LLD uses.** Rule/discount engines, alerting DSLs (`cpu > 80 FOR 5m`), search query filters,
spreadsheet formula evaluation, permission expressions, calculators, simple regex matchers.

**In the wild:** `java.util.regex.Pattern`, `java.text.Format` subclasses, EL/SpEL, `Predicate`
composition via `and`/`or`/`negate` (a lightweight interpreter over boolean grammar).

**When NOT to use it.** Anything beyond ~10 grammar rules, performance-critical evaluation (tree
walking is slow — compile to bytecode or a closure instead), or when a config file and a Specification
would do the job.

**Interview soundbite.** *"For a rule engine I'd represent each condition as an `Expression` node —
comparisons as terminals, AND/OR/NOT as non-terminals — and evaluate against a context map. Rules then
live in the database as trees rather than in code. I'd keep the grammar deliberately small, and for
anything richer I'd reach for a real expression language rather than growing a class per rule."*

**Follow-ups you'll get.**
- *"How do you parse the input?"* → Recursive-descent parser or a parser generator; Interpreter only
  covers evaluation once you have the tree.
- *"Performance?"* → Tree walking is slow for hot paths. Compile the tree once into a
  `Predicate<Context>` (a closure) and reuse it — same structure, much faster.
- *"Interpreter vs Specification?"* → Specification is the pragmatic 90% answer: composable predicate
  objects, no grammar, no parser. Reach for Interpreter only when rules must be *authored as text*.

---

## Quick comparison table

| Pair | The one-line distinction |
|---|---|
| Strategy / State | Client picks vs object transitions itself |
| Strategy / Template Method | Composition & whole algorithm vs inheritance & individual steps |
| Observer / Mediator | Broadcast without opinion vs opinionated coordination |
| Command / Memento | Inverse operation vs full snapshot |
| CoR / Decorator | May stop the chain vs always delegates onward |
| Visitor / Strategy | Dispatch on element type vs one uniform algorithm |
| Iterator / Stream | External & reusable vs internal & single-use |
| Interpreter / Specification | Text-authored grammar vs composable predicate objects |
