package dev.scriptaframework.core;

/**
 * Marker for something that happened in the domain.
 *
 * <p>Events are facts: immutable and named in the past tense. The usual shape is a sealed
 * interface per aggregate with one record per event, which lets the aggregate's
 * {@link AggregateRoot#apply(DomainEvent) apply} method switch over them exhaustively:
 *
 * <pre>{@code
 * public sealed interface AccountEvent extends DomainEvent {
 *     record AccountOpened(AccountId accountId, String owner) implements AccountEvent {}
 *     record MoneyDeposited(BigDecimal amount) implements AccountEvent {}
 * }
 * }</pre>
 */
public interface DomainEvent {
}
