package dev.scriptaframework.core.eventstore;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An {@link EventStore} that keeps events in memory, for tests and prototypes.
 *
 * <p>Global positions have no gaps: the n-th event appended is at position n - 1. All events of
 * one append share the same {@link RecordedEvent#recordedAt() recordedAt}, taken from the clock
 * given at construction. Every method holds the same lock, which makes appends atomic and reads
 * consistent; throughput is not a goal here.
 */
public final class InMemoryEventStore implements EventStore {

    private final Clock clock;
    private final List<RecordedEvent> allEvents = new ArrayList<>();
    private final Map<StreamId, List<RecordedEvent>> streams = new HashMap<>();

    /** A store that timestamps events with the system clock in UTC. */
    public InMemoryEventStore() {
        this(Clock.systemUTC());
    }

    /** A store that timestamps events with the given clock. */
    public InMemoryEventStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public synchronized AppendResult append(StreamId stream, ExpectedVersion expected, List<NewEvent> events) {
        Objects.requireNonNull(stream, "stream");
        Objects.requireNonNull(expected, "expected");
        List<NewEvent> newEvents = List.copyOf(events);
        if (newEvents.isEmpty()) {
            throw new IllegalArgumentException("Cannot append an empty list of events");
        }
        long currentVersion = currentVersion(stream);
        requireExpectedVersion(stream, expected, currentVersion);

        // Build the whole batch before storing any of it, so a failure leaves the store untouched.
        List<RecordedEvent> recorded = toRecordedEvents(stream, newEvents, currentVersion + 1, allEvents.size());
        streams.computeIfAbsent(stream, s -> new ArrayList<>()).addAll(recorded);
        allEvents.addAll(recorded);

        RecordedEvent last = recorded.getLast();
        return new AppendResult(last.streamVersion(), last.globalPosition());
    }

    @Override
    public synchronized List<RecordedEvent> readStream(StreamId stream, long fromVersion) {
        Objects.requireNonNull(stream, "stream");
        if (fromVersion < 0) {
            throw new IllegalArgumentException("fromVersion must not be negative, but was " + fromVersion);
        }
        List<RecordedEvent> events = streams.getOrDefault(stream, List.of());
        if (fromVersion >= events.size()) {
            return List.of();
        }
        return List.copyOf(events.subList((int) fromVersion, events.size()));
    }

    @Override
    public synchronized List<RecordedEvent> readAll(long fromGlobalPosition, int maxCount) {
        if (fromGlobalPosition < 0) {
            throw new IllegalArgumentException(
                    "fromGlobalPosition must not be negative, but was " + fromGlobalPosition);
        }
        if (maxCount < 1) {
            throw new IllegalArgumentException("maxCount must be at least 1, but was " + maxCount);
        }
        if (fromGlobalPosition >= allEvents.size()) {
            return List.of();
        }
        int from = (int) fromGlobalPosition;
        int to = (int) Math.min(allEvents.size(), fromGlobalPosition + maxCount);
        return List.copyOf(allEvents.subList(from, to));
    }

    private long currentVersion(StreamId stream) {
        return streams.getOrDefault(stream, List.of()).size() - 1;
    }

    private static void requireExpectedVersion(StreamId stream, ExpectedVersion expected, long currentVersion) {
        boolean matches = switch (expected) {
            case ExpectedVersion.Any any -> true;
            case ExpectedVersion.NoStream noStream -> currentVersion == -1;
            case ExpectedVersion.Exact exact -> currentVersion == exact.version();
        };
        if (!matches) {
            throw new ConcurrencyException(stream, expected, currentVersion);
        }
    }

    private List<RecordedEvent> toRecordedEvents(StreamId stream, List<NewEvent> events, long firstVersion, long firstPosition) {
        Instant recordedAt = clock.instant();
        List<RecordedEvent> recorded = new ArrayList<>(events.size());
        for (NewEvent event : events) {
            long offset = recorded.size();
            recorded.add(new RecordedEvent(
                    event.eventId(),
                    stream,
                    firstVersion + offset,
                    firstPosition + offset,
                    event.eventType(),
                    recordedAt,
                    event.metadata(),
                    event.payload()));
        }
        return recorded;
    }
}
