package dev.scriptaframework.core.eventstore;

import static dev.scriptaframework.core.eventstore.ExpectedVersion.any;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryEventStoreTest extends EventStoreContractTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private final InMemoryEventStore store = new InMemoryEventStore(Clock.fixed(NOW, ZoneOffset.UTC));

    @Override
    protected EventStore createStore() {
        return new InMemoryEventStore(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void globalPositionsStartAtZeroWithoutGaps() {
        store.append(StreamId.of("account", "1"), any(), List.of(event("first"), event("second")));
        store.append(StreamId.of("account", "2"), any(), List.of(event("third")));

        assertThat(store.readAll(0, 10)).extracting(RecordedEvent::globalPosition).containsExactly(0L, 1L, 2L);
    }

    @Test
    void recordedAtComesFromTheClock() {
        store.append(StreamId.of("account", "1"), any(), List.of(event("first"), event("second")));

        assertThat(store.readAll(0, 10)).extracting(RecordedEvent::recordedAt).containsOnly(NOW);
    }

    private static NewEvent event(String what) {
        return new NewEvent("Happened", new Happened(what));
    }
}
