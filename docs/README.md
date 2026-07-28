# Documentation

| Folder | Read it when |
|---|---|
| [`1-foundations/`](1-foundations) | Before anything else — how to *run* an LLD round, how to model, and the principles you're graded on |
| [`2-patterns/`](2-patterns) | When a specific pattern hasn't clicked yet |
| [`3-reference/`](3-reference) | During practice and in the last 30 minutes before an interview |

## 1-foundations/

| Doc | Contents |
|---|---|
| [Interview Approach](1-foundations/1-approach.md) | The 6-step framework, clarifying-question bank, entity modelling, concurrency playbook, common mistakes, a fully worked Parking Lot round |
| [Modelling Intuition](1-foundations/2-modelling.md) | **What becomes a class, an interface, or just a field** — the promotion ladder, the four reasons to add an interface, composition vs inheritance, ownership, tell-don't-ask, illegal states. Diagram-heavy, with a smell → move table and drills |
| [SOLID Principles](1-foundations/3-solid.md) | Each principle with the nuance most people miss, fast detection heuristics, an interview soundbite, and the counter-principles (KISS/YAGNI/Rule of Three) |

## 2-patterns/

Deep dives. Every entry follows the same structure:
**intent → the problem it solves → structure → design decisions → real JDK usage → when NOT to use it → interview soundbite → follow-up questions.**

| Doc | Covers |
|---|---|
| [Creational](2-patterns/1-creational.md) | Singleton, Factory Method, Abstract Factory, Builder, Prototype, Object Pool |
| [Structural](2-patterns/2-structural.md) | Adapter, Decorator, Facade, Composite, Proxy, Bridge, Flyweight |
| [Behavioral](2-patterns/3-behavioral.md) | Strategy, Observer, State, Command, Chain of Responsibility, Template Method, Iterator, Mediator, Memento, Visitor, Interpreter |
| [Practical](2-patterns/4-practical.md) | Repository, Dependency Injection, Null Object, Specification, Producer–Consumer, Unit of Work, Circuit Breaker, Idempotency Key, Domain Events, Result/Either, Registry/Plugin, plus DTO, CQRS, Event Sourcing, Saga |

## 3-reference/

| Doc | Contents |
|---|---|
| [Cheat Sheet](3-reference/1-cheatsheet.md) | Symptom → pattern lookup, the 12 confusing pairs, trade-offs worth volunteering, 30-second selection heuristic |
| [Classic Problems](3-reference/2-problems.md) | 26 classic problems: requirements → where the design pressure is → why each pattern was chosen → what interviewers probe |

---

Runnable code lives in [`../src`](../src). Start from the [root README](../README.md).
