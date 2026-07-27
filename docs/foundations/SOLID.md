# SOLID Principles

The five principles interviewers actually grade. For each: the rule, the smell it fixes, the nuance
most candidates miss, and a line you can say out loud.

**Why they matter more than patterns.** Patterns are *solutions*; SOLID tells you *when a solution is
needed*. An interviewer who hears "I'll extract a `FeeStrategy` because pricing is the part that
changes — that keeps `ParkingLot` closed for modification" learns far more about you than one who
hears "I'll use the Strategy pattern." Always give the principle as the reason and the pattern as the
mechanism.

---

## S — Single Responsibility Principle (SRP)
> A class should have one, and only one, reason to change.

**Smell:** a class that parses input *and* applies business rules *and* writes to a DB.
**Fix:** split by responsibility (`OrderValidator`, `OrderRepository`, `OrderService`).
**Cue:** if you use "and" to describe a class, it probably does too much.

```java
// Bad: mixes formatting + persistence
class Invoice {
    double total() { /* ... */ return 0; }
    void saveToDb() { /* ... */ }      // persistence concern
    String toPdf() { /* ... */ return ""; } // presentation concern
}

// Good: one reason to change each
class Invoice { double total() { return 0; } }
class InvoiceRepository { void save(Invoice i) { } }
class InvoicePrinter { String toPdf(Invoice i) { return ""; } }
```

**The nuance most people miss.** "One responsibility" is vague and leads to arguments. Uncle Bob's
sharper formulation is: **a class should have one reason to change, where a "reason" is a person or
role that requests the change.** `Invoice` above changes when *accounting* changes the total formula,
when *the DBA* changes the schema, and when *design* changes the PDF layout — three roles, three
classes. Framing it as "who asks for the change?" makes the split objective.

**How to detect a violation fast.**
- Describing the class needs "and" or a comma.
- The imports span unrelated concerns (`java.sql` + `javax.mail` in one file).
- Two different teams keep editing the same file for unrelated reasons.
- The test setup is huge because you must stub four unrelated collaborators.

**The counter-force.** SRP taken to an extreme produces hundreds of one-method classes and a design
you can't navigate. Cohesion is the balance: things that change *together* should live together.
Splitting a class whose parts always change in lockstep makes the code worse, not better.

**Interview soundbite.** *"`ParkingLot` allocates spots and issues tickets; it should not also compute
fees, because pricing changes for completely different reasons than allocation does. I'll extract a
`FeeStrategy` — that's SRP, and it conveniently gives me the Open/Closed extension point too."*

---

## O — Open/Closed Principle (OCP)
> Open for extension, closed for modification.

**Smell:** a growing `switch`/`if-else` on a type that you edit for every new case.
**Fix:** introduce an interface; add new behavior via new classes (Strategy, Factory).
**Cue:** adding a feature should mean *adding* code, not *editing* a giant method.

```java
// Bad: edit this method for every new shape
double area(Object shape) {
    if (shape instanceof Circle c) return Math.PI * c.r * c.r;
    else if (shape instanceof Square s) return s.side * s.side;
    // ...keeps growing
    return 0;
}

// Good: each shape owns its area; new shapes add a class, don't touch existing code
interface Shape { double area(); }
class Circle implements Shape { double r; public double area() { return Math.PI * r * r; } }
class Square implements Shape { double side; public double area() { return side * side; } }
```

**Why "closed" matters.** Code you don't modify is code you don't re-test and can't regress. OCP isn't
about aesthetics — it's about limiting the blast radius of a change. That's the business case, and
it's what to say when asked "why does that matter?"

**The nuance most people miss.** You **cannot** be open to every axis of change, and trying to is
over-engineering. OCP is about predicting the *one or two* axes that will actually vary and making
*those* extensible. If you don't know the axis yet, write the simple version — then refactor when the
second case arrives. "Open/Closed applies to the variation you can name" is a mature take.

**How to detect a violation fast.**
- A `switch`/`if-else` chain on a type or enum that has grown before and will grow again.
- `instanceof` checks driving behaviour.
- A merge conflict in the same method every sprint.
- "To add X, edit these four files" — shotgun surgery.

**Caveat worth voicing.** A factory with a `switch` still gets edited for each new type. That's an
accepted, *localized* violation — one line in one file instead of edits scattered across call sites.
Eliminate even that with a `Map<Type, Supplier<T>>` registry if the interviewer pushes.

**Interview soundbite.** *"Right now there are two notification channels, but the requirement says more
are coming — so channel is a real axis of variation and I'll make it extensible with an interface plus
a registry. I'm deliberately *not* abstracting the storage layer, because nothing suggests it'll change."*

---

## L — Liskov Substitution Principle (LSP)
> Subtypes must be usable anywhere their base type is expected, without breaking behavior.

**Smell:** a subclass that throws `UnsupportedOperationException` or ignores a method.
**Fix:** don't force an is-a that isn't true. Prefer composition or narrower interfaces.
**Cue:** the classic `Square extends Rectangle` trap — setting width shouldn't corrupt height.

```java
// Bad: Ostrich is-a Bird but can't fly -> violates LSP
class Bird { void fly() { } }
class Ostrich extends Bird { void fly() { throw new UnsupportedOperationException(); } }

// Good: separate the capability
interface Bird { }
interface Flying { void fly(); }
class Sparrow implements Bird, Flying { public void fly() { } }
class Ostrich implements Bird { } // simply has no fly capability
```

**LSP is about behaviour, not signatures.** The compiler already enforces the signatures. LSP governs
the **contract**: a subtype may not strengthen preconditions, weaken postconditions, or violate
invariants of the base type. Saying that sentence correctly is a genuine senior signal.

**The three concrete rules.**
| Rule | Meaning | Violation example |
|---|---|---|
| **Preconditions cannot be strengthened** | The subtype must accept everything the base accepts | Base accepts any `int`; subtype rejects negatives |
| **Postconditions cannot be weakened** | The subtype must guarantee at least what the base guarantees | Base guarantees a sorted result; subtype returns unsorted |
| **Invariants must be preserved** | Base-class truths stay true | `Rectangle`'s "width and height are independent" broken by `Square` |

**Why `Square extends Rectangle` is the canonical failure.** Any code holding a `Rectangle` may
reasonably assume `setWidth(5); setHeight(4);` yields area 20. `Square` must break that to stay square.
The subtype is mathematically an is-a but behaviourally not substitutable — which proves that
"is-a" in English is not the test. Substitutability is.

**How to detect a violation fast.**
- Overrides that throw `UnsupportedOperationException` or silently no-op.
- Callers doing `if (x instanceof SubType)` to work around a subtype's quirks.
- Base-class tests that fail when run against a subclass. *(Great answer to "how would you test LSP?"
  — run the base type's test suite against every subtype.)*
- Overrides that tighten argument validation.

**The fix is almost always composition.** If the subtype can't honour the contract, it isn't a subtype.
Give it a field, or split the interface (ISP) so each type implements only what it can deliver.

**Interview soundbite.** *"I won't make `ElectricCar extends Car` here — it needs charging behaviour a
plain `Car` client wouldn't expect, and I'd end up with unsupported operations. I'll model the
capability as a separate interface so every `Car` reference stays fully substitutable."*

---

## I — Interface Segregation Principle (ISP)
> Clients shouldn't be forced to depend on methods they don't use.

**Smell:** one fat interface; implementers stub out half the methods.
**Fix:** split into small, role-based interfaces.
**Cue:** many small interfaces beat one universal one.

```java
// Bad: a Robot worker is forced to implement eat()
interface Worker { void work(); void eat(); }

// Good: segregate roles
interface Workable { void work(); }
interface Eatable { void eat(); }
class Human implements Workable, Eatable { public void work() { } public void eat() { } }
class Robot implements Workable { public void work() { } }
```

**Why it's not just tidiness.** A dependency on a method you never call is still a dependency: when
that method's signature changes, **you recompile and re-test** for no reason. Fat interfaces couple
unrelated clients to each other through the interface.

**The nuance most people miss.** ISP is about the **client's** view, not the implementer's. The right
split is by *role* — who uses which subset — not by arbitrary size. A single class can implement
several role interfaces; that's the intended outcome, not a problem.

**ISP and LSP are the same failure seen from two sides.** A class forced to implement a method it
can't support (ISP violation) will stub or throw — which is exactly an LSP violation. Fixing the
interface split fixes both.

**How to detect a violation fast.**
- Implementations full of empty bodies, `return null`, or `throw new UnsupportedOperationException()`.
- An interface with 10+ methods where no single client uses more than three.
- Test doubles that must stub 8 methods to exercise one.

**Java note.** `default` methods let you add to an interface without breaking implementers — useful,
but don't use them to justify keeping a fat interface. A default that throws is still an ISP violation
with extra steps.

**Interview soundbite.** *"Rather than one `ParkingSpot` interface with charge/reserve/clean, I'll
split `Reservable` and `Chargeable`. Only EV spots implement `Chargeable`, so a regular spot never has
a method it can't honour — and callers depend only on the capability they actually need."*

---

## D — Dependency Inversion Principle (DIP)
> Depend on abstractions, not concretions. High-level modules shouldn't depend on low-level ones.

**Smell:** a service that `new`s a concrete `MySqlDatabase` inside itself.
**Fix:** depend on an interface; inject the implementation (constructor injection).
**Cue:** "new is glue" — every `new` of a dependency welds classes together.

```java
// Bad: OrderService is welded to MySQL
class OrderService {
    private final MySqlDatabase db = new MySqlDatabase(); // hard to test/swap
}

// Good: depend on abstraction, inject the concrete type
interface Database { void save(String data); }
class MySqlDatabase implements Database { public void save(String d) { } }
class OrderService {
    private final Database db;
    OrderService(Database db) { this.db = db; } // inject: swap for a mock or Postgres freely
}
```

**The "inversion" is the point — and it's usually explained wrong.** It's not just "use interfaces."
Normally high-level policy depends downward on low-level detail. DIP inverts that by having the
**high-level module own the abstraction**: `OrderService` (domain) *defines* the `Database` interface,
and the infrastructure package *implements* it. The arrow of dependency now points **from**
infrastructure **to** the domain — the opposite of the naive layering. That's the inversion, and
explaining it this way is a strong differentiator.

> Rule of thumb: the interface belongs in the package of the **client** that needs it, not the package
> of the class that implements it.

**DIP vs DI vs IoC — three different things.**
| Term | What it is |
|---|---|
| **DIP** | The *principle*: depend on abstractions owned by the high-level module |
| **DI** | The *technique*: pass dependencies in (usually via the constructor) |
| **IoC container** | A *tool*: Spring/Guice wiring the graph for you |

**How to detect a violation fast.**
- `new` of a *collaborator* (not a value object) inside a class body.
- A domain class importing `java.sql`, an HTTP client, or a framework annotation.
- A test that can't run without a database, network, or clock.
- Static utility calls to infrastructure (`EmailUtil.send(...)`) — untestable and unswappable.

**Not everything must be inverted.** `new ArrayList<>()`, `new Ticket(...)`, `LocalDate.now()` inside a
value object — fine. Invert *volatile* dependencies: things that touch I/O, that you'd want to fake in
tests, or that have plausible alternative implementations. Injecting `Clock` is the classic example
that makes time-dependent logic testable.

**Interview soundbite.** *"`BookingService` will define the `BookingRepository` interface itself, and
the JDBC implementation lives in the infrastructure package implementing that domain-owned interface.
So the dependency points inward: my domain has zero knowledge of the database, and my unit tests inject
an in-memory implementation and a fixed `Clock`."*

---

## How SOLID maps to patterns

| Principle | Patterns that help you honor it |
|---|---|
| OCP | Strategy, Factory, Decorator, Template Method |
| DIP | Factory, Abstract Factory, Strategy (via interfaces) |
| SRP | Facade, Command, most patterns via clear roles |
| ISP | Adapter, role interfaces |
| LSP | Careful hierarchy design; favor composition |

**Bonus mnemonics:** KISS (keep it simple), DRY (don't repeat yourself), YAGNI (you aren't gonna need it).
Cite these when you *resist* over-engineering — that too is a senior signal.

---

## The counter-principles — knowing when to stop

SOLID applied without limit produces the other failure mode: a codebase of 400 one-method classes
where no one can find anything. Interviewers who've seen that value candidates who know the brakes.

- **KISS** — the simplest thing that correctly solves the stated problem.
- **YAGNI** — don't build the extension point for a requirement nobody has stated. Abstractions built
  on guesses are usually the *wrong* abstractions, and wrong abstractions are more expensive than
  duplication.
- **DRY — with care.** DRY is about duplicated *knowledge*, not duplicated *characters*. Two methods
  that look alike but change for different reasons should stay separate. Premature deduplication
  couples things that ought to be independent.
- **Rule of Three** — the practical trigger. First occurrence: write it. Second: note the duplication.
  Third: now you can see the real axis of variation, so abstract.

**Say this at least once in an interview:**
> *"I could put a Strategy here, but there's exactly one pricing rule and nothing suggests a second.
> I'll write it directly and extract the interface the moment a second rule appears — by then I'll know
> the right shape for the abstraction."*

That sentence demonstrates you know the principles *and* their cost, which is the actual senior bar.

---

## Quick self-audit

Run this over any class you write in an interview:

| Question | Principle | If "yes"... |
|---|---|---|
| Does describing it need "and"? | SRP | Split it |
| Will a new feature require editing this method? | OCP | Extract an interface |
| Does any override throw or no-op? | LSP | Use composition instead of inheritance |
| Does any implementer leave methods empty? | ISP | Split the interface |
| Does it `new` a collaborator, or touch I/O directly? | DIP | Inject it |
| Am I abstracting something that has exactly one case? | YAGNI | Don't |
