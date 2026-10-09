package dev.scriptaframework.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Base class for event-sourced aggregates.
 *
 * <p>An aggregate changes state only by applying events. Command methods on the subclass
 * check invariants and then call {@link #raise(DomainEvent) raise}; {@link #apply(DomainEvent)
 * apply} performs the state change. The same {@code apply} is used when rebuilding the
 * aggregate from its history with {@link #rehydrate(List) rehydrate}, so the current state is
 * always the result of the events applied so far.
 *
 * <pre>{@code
 * public final class BankAccount extends AggregateRoot<AccountId, AccountEvent> {
 *
 *     private String owner;
 *     private BigDecimal balance = BigDecimal.ZERO;
 *
 *     private BankAccount(AccountId id) {
 *         super(id);
 *     }
 *
 *     public static BankAccount open(AccountId id, String owner) {
 *         var account = new BankAccount(id);
 *         account.raise(new AccountOpened(owner));
 *         return account;
 *     }
 *
 *     public void deposit(BigDecimal amount) {
 *         // check invariants, then:
 *         raise(new MoneyDeposited(amount));
 *     }
 *
 *     @Override
 *     protected void apply(AccountEvent event) {
 *         switch (event) {
 *             case AccountOpened e -> owner = e.owner();
 *             case MoneyDeposited e -> balance = balance.add(e.amount());
 *         }
 *     }
 * }
 * }</pre>
 *
 * <h2>Creation</h2>
 * The identifier is passed to the constructor and never changes, so {@link #id()} is never
 * null. Keep the subclass constructor private and create aggregates through static factories
 * named after the domain operation, such as {@code BankAccount.open(id, owner)}, that raise the
 * creation event. That way application code cannot obtain an aggregate that was never created
 * in the domain's terms, and needs no "not yet created" checks. For loading,
 * the subclass can offer a factory that constructs an instance and calls
 * {@link #rehydrate(List) rehydrate}.
 *
 * <h2>Version</h2>
 * {@link #version()} is {@code -1} for a new aggregate and otherwise the 0-based position of
 * the last applied event in the aggregate's history. It counts every applied event, whether
 * raised or rehydrated, and is not reset by {@link #markCommitted()}.
 *
 * <h2>Thread safety</h2>
 * Aggregates are not thread-safe. An instance is meant to be loaded, changed and saved by a
 * single thread; concurrent changes are detected by the persistence layer, not here.
 *
 * @param <ID> the type of the aggregate identifier
 * @param <E>  the type of events this aggregate applies, typically a sealed interface
 */
public abstract class AggregateRoot<ID, E extends DomainEvent> {

    private final ID id;
    private long version = -1;
    private final List<E> uncommittedEvents = new ArrayList<>();

    /**
     * Creates a new aggregate with the given identifier, version {@code -1} and no events.
     *
     * @param id the identifier
     * @throws NullPointerException if {@code id} is null
     */
    protected AggregateRoot(ID id) {
        this.id = Objects.requireNonNull(id, "id");
    }

    /**
     * Applies the event and records it as uncommitted.
     *
     * <p>Call this from command methods after all invariants have been checked. The bookkeeping
     * is atomic: if {@link #apply(DomainEvent) apply} throws, the event is not recorded, the
     * version is unchanged and the exception propagates. The subclass's own
     * fields, however, may have been partially modified by {@code apply} before it threw;
     * {@code apply} should not throw in the first place.
     *
     * @param event the event to raise
     * @throws NullPointerException if {@code event} is null
     */
    protected final void raise(E event) {
        applyAndAdvance(event);
        uncommittedEvents.add(event);
    }

    /**
     * Mutates the aggregate's state to reflect the event.
     *
     * <p>Implementations only change fields: no validation, no side effects, no calls to
     * {@link #raise(DomainEvent) raise}. Events are facts, so applying one must not fail.
     * Use a {@code switch} over the sealed event type so the compiler checks that every event
     * is handled.
     *
     * @param event the event to apply, never null
     */
    protected abstract void apply(E event);

    /**
     * Returns the aggregate identifier.
     *
     * @return the identifier, never null
     */
    public final ID id() {
        return id;
    }

    /**
     * Returns the 0-based version of the last applied event, or {@code -1} if no event has been
     * applied.
     *
     * @return the current version
     */
    public final long version() {
        return version;
    }

    /**
     * Returns the events raised since the aggregate was created, rehydrated or last marked
     * committed, in the order they were raised.
     *
     * @return an unmodifiable snapshot of the uncommitted events
     */
    public final List<E> uncommittedEvents() {
        return List.copyOf(uncommittedEvents);
    }

    /**
     * Clears the uncommitted events once they have been persisted. The version is unchanged.
     *
     * <p>Intended for infrastructure such as repositories and test fixtures, not for
     * application code.
     */
    public final void markCommitted() {
        uncommittedEvents.clear();
    }

    /**
     * Rebuilds the aggregate's state by applying its past events in order. The events are not
     * recorded as uncommitted. An empty history leaves the aggregate new.
     *
     * <p>Intended for infrastructure such as repositories and test fixtures, not for
     * application code.
     *
     * <p>If applying an event throws, rehydration stops and the aggregate is left partially
     * rehydrated; it should be discarded.
     *
     * @param history the aggregate's events, oldest first
     * @throws NullPointerException  if {@code history} or any event in it is null
     * @throws IllegalStateException if the aggregate already has state or uncommitted events
     */
    public final void rehydrate(List<? extends E> history) {
        Objects.requireNonNull(history, "history");
        if (version != -1 || !uncommittedEvents.isEmpty()) {
            throw new IllegalStateException(
                    "Cannot rehydrate an aggregate that already has state (version " + version + ")");
        }
        for (E event : history) {
            applyAndAdvance(event);
        }
    }

    // The single place where the version advances. Snapshot support can later restore the
    // version to a known point and replay the remaining events through here.
    private void applyAndAdvance(E event) {
        Objects.requireNonNull(event, "event");
        apply(event);
        version++;
    }
}
