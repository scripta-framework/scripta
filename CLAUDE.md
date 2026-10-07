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

- Java 21: prefer records, sealed interfaces and pattern matching.
- Immutable value types everywhere.
- Keep the public API small and deliberate; make implementation classes package-private where possible.
- Every public type has Javadoc.
- Every feature has tests (JUnit 5 + AssertJ).
- Formatting follows `.editorconfig`: 4 spaces for Java, 2 for XML/YAML, LF line endings.

## Workflow

- Work happens on feature branches and lands via pull requests.
- One logical change per PR.
