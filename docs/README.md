# Documentation

| Folder | Read it when |
|---|---|
| [`foundations/`](foundations) | Before anything else — how to *run* an LLD round, and the principles you're graded on |
| [`reference/`](reference) | During practice and in the last 30 minutes before an interview |
| [`patterns/`](patterns) | When a specific pattern hasn't clicked yet |

## foundations/

| Doc | Contents |
|---|---|
| [APPROACH.md](foundations/APPROACH.md) | The 6-step framework, clarifying-question bank, entity modelling, concurrency playbook, common mistakes, a fully worked Parking Lot round |
| [MODELLING.md](foundations/MODELLING.md) | **What becomes a class, an interface, or just a field** — the promotion ladder, the four reasons to add an interface, composition vs inheritance, ownership, tell-don't-ask, illegal states. Diagram-heavy, with a smell → move table and drills |
| [SOLID.md](foundations/SOLID.md) | Each principle with the nuance most people miss, fast detection heuristics, an interview soundbite, and the counter-principles (KISS/YAGNI/Rule of Three) |

## reference/

| Doc | Contents |
|---|---|
| [CHEATSHEET.md](reference/CHEATSHEET.md) | Symptom → pattern lookup, the 12 confusing pairs, trade-offs worth volunteering, 30-second selection heuristic |
| [PROBLEMS.md](reference/PROBLEMS.md) | 26 classic problems: requirements → where the design pressure is → why each pattern was chosen → what interviewers probe |

## patterns/

Deep dives. Every entry follows the same structure:
**intent → the problem it solves → structure → design decisions → real JDK usage → when NOT to use it → interview soundbite → follow-up questions.**

| Doc | Covers |
|---|---|
| [CREATIONAL.md](patterns/CREATIONAL.md) | Singleton, Factory Method, Abstract Factory, Builder, Prototype, Object Pool |
| [STRUCTURAL.md](patterns/STRUCTURAL.md) | Adapter, Decorator, Facade, Composite, Proxy, Bridge, Flyweight |
| [BEHAVIORAL.md](patterns/BEHAVIORAL.md) | Strategy, Observer, State, Command, Chain of Responsibility, Template Method, Iterator, Mediator, Memento, Visitor, Interpreter |
| [PRACTICAL.md](patterns/PRACTICAL.md) | Repository, Dependency Injection, Null Object, Specification, Producer–Consumer, Unit of Work, Circuit Breaker, Idempotency Key, Domain Events, Result/Either, Registry/Plugin, plus DTO, CQRS, Event Sourcing, Saga |

---

Runnable code lives in [`../src`](../src). Start from the [root README](../README.md).
