# Scripta

Event sourcing and DDD framework for Java. Multi-module Maven build, Java 21.

## Build

```sh
./mvnw verify
```

A change is done only when `./mvnw verify` passes.

## Layout and naming

- groupId is `dev.scriptaframework`.
- Each module's base package is `dev.scriptaframework.<module>` (e.g. `scripta-core` → `dev.scriptaframework.core`).
- Plugin and test-library versions are pinned in the parent `pom.xml`; modules don't declare versions.

## Guiding principle: "without the ceremony"

- No annotation-driven magic, no reflection in core, no mandatory framework integration.
- Every new API must justify itself. Prefer plain Java.

## Dependencies

- `scripta-core` has zero runtime dependencies. Anything that needs a library goes in a separate module.
- Tests use JUnit 5 and AssertJ (test scope only).

## Code style

Write code a thoughtful human maintainer would write and enjoy reading.

- Java 21: prefer records, sealed interfaces and pattern matching.
- Immutable value types everywhere.
- Keep the public API small and deliberate; make implementation classes package-private where possible.
- Formatting follows `.editorconfig`: 4 spaces for Java, 2 for XML/YAML, LF line endings.
- Optimise for the reader. Code is read far more often than written; a newcomer should understand a class top to bottom without jumping around.
- Clear, plain names from the domain. No abbreviations, no Hungarian-style prefixes, no generic names like data, info, helper, manager, util.
- Short methods that do one thing. Prefer early returns over nested ifs.
- Plain loops over clever stream chains when the loop reads more naturally. Streams are fine when they read like a sentence.
- No speculative abstractions: no interface with a single implementation, no factory, builder or strategy unless there is a real need today.
- Don't be over-defensive: validate at public API boundaries, trust the code inside.
- Comments explain why, never what. If a comment restates the code, delete it; if the code needs a comment to explain what it does, rewrite the code first.
- Order members for reading: public API first, then protected, then private helpers, in the order they are called.

### Javadoc

- Every public type has Javadoc.
- Keep it concise and useful: what it is, the contract, the non-obvious rules. No boilerplate like "Returns the id. @return the id".

### Tests

- Every feature has tests (JUnit 5 + AssertJ).
- Tests read like specifications: descriptive method names, one behaviour per test, arrange/act/assert visibly separated, no logic in tests.

### Before finishing

- Reread the change as a reviewer would and simplify anything that feels heavier than the problem it solves.

## Workflow

- Work happens on feature branches and lands via pull requests.
- One logical change per PR.
