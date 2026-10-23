package dev.scriptaframework.core.repository;

import dev.scriptaframework.core.AggregateRoot;
import dev.scriptaframework.core.DomainEvent;
import dev.scriptaframework.core.eventstore.AppendResult;
import dev.scriptaframework.core.eventstore.EventMetadata;
import dev.scriptaframework.core.eventstore.EventStore;
import dev.scriptaframework.core.eventstore.ExpectedVersion;
import dev.scriptaframework.core.eventstore.NewEvent;
import dev.scriptaframework.core.eventstore.RecordedEvent;
import dev.scriptaframework.core.eventstore.StreamId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Loads event-sourced aggregates from an {@link EventStore} and saves their uncommitted events.
 *
 * <p>Each aggregate lives in its own stream, named {@code <category>-<id as text>}. An aggregate
 * exists exactly when its stream has events: loading rebuilds it from them through the restore
 * factory, and an aggregate with no history is never handed out.
 *
 * <pre>{@code
 * var accounts = new AggregateRepository<>(
 *         eventStore,
 *         "account",
 *         id -> id.value().toString(),
 *         AccountEvent.class,
 *         event -> switch (event) {
 *             case AccountOpened e -> "account.opened";
 *             case MoneyDeposited e -> "account.money-deposited";
 *         },
 *         BankAccount::restore);
 *
 * var account = accounts.get(accountId);
 * account.deposit(amount);
 * accounts.save(account);
 * }</pre>
 *
 * <h2>Saving</h2>
 * {@link #save(AggregateRoot, EventMetadata) save} appends all uncommitted events in one batch,
 * expecting the stream to be at the version the aggregate had before those events. If someone
 * else has changed the stream in the meantime, the store's {@link
 * dev.scriptaframework.core.eventstore.ConcurrencyException ConcurrencyException} propagates
 * unchanged and the aggregate keeps its uncommitted events. Only after a successful append are
 * they marked committed.
 *
 * <p>The repository holds no state of its own besides its configuration, so it is as
 * thread-safe as the store. Aggregates themselves are not; see {@link AggregateRoot}.
 *
 * @param <A>  the aggregate type
 * @param <ID> the type of the aggregate identifier
 * @param <E>  the type of events the aggregate applies
 */
public final class AggregateRepository<A extends AggregateRoot<ID, E>, ID, E extends DomainEvent> {

    private final EventStore store;
    private final String category;
    private final Function<? super ID, String> idToText;
    private final Class<E> eventClass;
    private final Function<? super E, String> eventTypeName;
    private final BiFunction<ID, List<E>, A> restore;

    /**
     * @param store         where the events are stored
     * @param category      the stream category, such as {@code "account"}; non-blank and
     *                      without {@code '-'}
     * @param idToText      turns an id into the id part of the stream name, such as
     *                      {@code id -> id.value().toString()}; a null or blank result fails the
     *                      load or save with an error naming {@code idToText}
     * @param eventClass    the aggregate's event type; every stored payload must be an
     *                      instance of it
     * @param eventTypeName the name each event is stored under. <strong>Names are stored
     *                      forever</strong> and must stay stable for the lifetime of the data:
     *                      changing one makes existing events unreadable under the old name.
     *                      Derive them from something you control, such as a switch over the
     *                      sealed event type returning names like {@code "account.opened"}, and
     *                      not from class names, which a harmless-looking rename would change.
     * @param restore       rebuilds an aggregate from its id and its non-empty history, such as
     *                      {@code BankAccount::restore}; it must construct the instance and call
     *                      {@link AggregateRoot#rehydrate(List) rehydrate}
     * @throws NullPointerException     if any argument is null
     * @throws IllegalArgumentException if {@code category} is blank or contains {@code '-'}
     */
    public AggregateRepository(
            EventStore store,
            String category,
            Function<? super ID, String> idToText,
            Class<E> eventClass,
            Function<? super E, String> eventTypeName,
            BiFunction<ID, List<E>, A> restore) {
        this.store = Objects.requireNonNull(store, "store");
        this.category = requireValidCategory(category);
        this.idToText = Objects.requireNonNull(idToText, "idToText");
        this.eventClass = Objects.requireNonNull(eventClass, "eventClass");
        this.eventTypeName = Objects.requireNonNull(eventTypeName, "eventTypeName");
        this.restore = Objects.requireNonNull(restore, "restore");
    }

    /**
     * Loads the aggregate with the given id.
     *
     * @return the aggregate rebuilt from its events, or empty if its stream has no events
     * @throws UnexpectedEventException if the stream holds an event that is not an instance of
     *                                  the repository's event class
     * @throws IllegalStateException    if the restore factory returns an aggregate with a
     *                                  different id or version than the stream's
     */
    public Optional<A> load(ID id) {
        StreamId stream = streamOf(id);
        List<RecordedEvent> recorded = store.readStream(stream, 0);
        if (recorded.isEmpty()) {
            return Optional.empty();
        }
        A aggregate = restore.apply(id, payloadsOf(recorded));
        requireConsistent(aggregate, id, stream, recorded.getLast().streamVersion());
        return Optional.of(aggregate);
    }

    /**
     * Loads the aggregate with the given id, which must exist.
     *
     * @throws AggregateNotFoundException if the aggregate's stream has no events
     * @see #load(Object)
     */
    public A get(ID id) {
        return load(id).orElseThrow(() -> new AggregateNotFoundException(streamOf(id)));
    }

    /**
     * Saves the aggregate's uncommitted events with no metadata.
     *
     * @see #save(AggregateRoot, EventMetadata)
     */
    public void save(A aggregate) {
        save(aggregate, EventMetadata.empty());
    }

    /**
     * Appends the aggregate's uncommitted events in one batch, each with the given metadata, and
     * marks them committed. Does nothing if there are none.
     *
     * @throws dev.scriptaframework.core.eventstore.ConcurrencyException if the stream changed
     *         since the aggregate was loaded; the aggregate keeps its uncommitted events
     * @throws IllegalStateException if the store reports a stream version that does not match
     *         the aggregate's, which indicates a bug in the store. The events may already be
     *         stored, so the aggregate is out of step with its stream and must be discarded, not
     *         saved again.
     * @throws NullPointerException if any argument is null
     */
    public void save(A aggregate, EventMetadata metadata) {
        Objects.requireNonNull(aggregate, "aggregate");
        Objects.requireNonNull(metadata, "metadata");
        List<E> uncommitted = aggregate.uncommittedEvents();
        if (uncommitted.isEmpty()) {
            return;
        }
        StreamId stream = streamOf(aggregate.id());
        long versionBeforeChanges = aggregate.version() - uncommitted.size();
        ExpectedVersion expected = versionBeforeChanges == -1
                ? ExpectedVersion.noStream()
                : ExpectedVersion.exact(versionBeforeChanges);

        AppendResult result = store.append(stream, expected, toNewEvents(uncommitted, metadata));

        if (result.streamVersion() != aggregate.version()) {
            throw new IllegalStateException("Stream " + stream.value() + " is at version "
                    + result.streamVersion() + " after saving, but the aggregate is at version "
                    + aggregate.version() + ". The events may already be stored; discard this aggregate"
                    + " instead of saving it again.");
        }
        aggregate.markCommitted();
    }

    private static String requireValidCategory(String category) {
        // Builds a throwaway stream name so the rule lives in StreamId alone.
        StreamId.of(category, "id");
        return category;
    }

    private StreamId streamOf(ID id) {
        Objects.requireNonNull(id, "id");
        String text = idToText.apply(id);
        Objects.requireNonNull(text, () -> "idToText returned null for id " + id);
        if (text.isBlank()) {
            throw new IllegalStateException("idToText returned blank text for id " + id);
        }
        return StreamId.of(category, text);
    }

    private List<E> payloadsOf(List<RecordedEvent> recorded) {
        List<E> payloads = new ArrayList<>(recorded.size());
        for (RecordedEvent event : recorded) {
            if (!eventClass.isInstance(event.payload())) {
                throw new UnexpectedEventException(event, eventClass);
            }
            payloads.add(eventClass.cast(event.payload()));
        }
        return payloads;
    }

    // A factory that forgets to rehydrate, or builds the wrong instance, would otherwise hand
    // out an aggregate whose next save fails with a misleading concurrency error.
    private void requireConsistent(A aggregate, ID id, StreamId stream, long streamVersion) {
        Objects.requireNonNull(aggregate, () -> "The restore factory returned null for stream " + stream.value());
        if (!aggregate.id().equals(id) || aggregate.version() != streamVersion) {
            throw new IllegalStateException("The restore factory returned an inconsistent aggregate for stream "
                    + stream.value() + ": expected id " + id + " at version " + streamVersion
                    + ", but got id " + aggregate.id() + " at version " + aggregate.version()
                    + ". Does it call rehydrate with the given history?");
        }
    }

    private List<NewEvent> toNewEvents(List<E> events, EventMetadata metadata) {
        List<NewEvent> newEvents = new ArrayList<>(events.size());
        for (E event : events) {
            newEvents.add(new NewEvent(eventTypeName.apply(event), event, metadata));
        }
        return newEvents;
    }
}
