package dev.scriptaframework.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Mechanics of the base class, independent of any realistic domain. */
class AggregateRootTest {

    sealed interface CounterEvent extends DomainEvent {
        record Incremented() implements CounterEvent {}

        /** Partially mutates state, then fails. */
        record Faulty() implements CounterEvent {}
    }

    static final class Counter extends AggregateRoot<String, CounterEvent> {

        final List<String> log = new ArrayList<>();
        int count;

        Counter(String id) {
            super(id);
        }

        void handle(CounterEvent event) {
            raise(event);
        }

        @Override
        protected void apply(CounterEvent event) {
            switch (event) {
                case CounterEvent.Incremented e -> count++;
                case CounterEvent.Faulty e -> {
                    log.add("partially applied");
                    throw new IllegalStateException("boom");
                }
            }
        }
    }

    @Test
    void newAggregateHasItsIdButNoVersionAndNoEvents() {
        var counter = new Counter("c-1");

        assertThat(counter.id()).isEqualTo("c-1");
        assertThat(counter.version()).isEqualTo(-1);
        assertThat(counter.uncommittedEvents()).isEmpty();
    }

    @Test
    void idIsRequired() {
        assertThatThrownBy(() -> new Counter(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("id");
    }

    @Test
    void raiseAppliesAndRecordsTheEvent() {
        var counter = new Counter("c-1");

        counter.handle(new CounterEvent.Incremented());

        assertThat(counter.count).isEqualTo(1);
        assertThat(counter.version()).isZero();
        assertThat(counter.uncommittedEvents()).containsExactly(new CounterEvent.Incremented());
    }

    @Test
    void raiseIsAtomicWhenApplyThrows() {
        var counter = new Counter("c-1");
        counter.handle(new CounterEvent.Incremented());

        assertThatThrownBy(() -> counter.handle(new CounterEvent.Faulty()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(counter.uncommittedEvents()).containsExactly(new CounterEvent.Incremented());
        assertThat(counter.version()).isZero();
        // The subclass's own state is not rolled back, as documented.
        assertThat(counter.log).containsExactly("partially applied");
    }

    @Test
    void failedFirstEventLeavesAggregateNew() {
        var counter = new Counter("c-1");

        assertThatThrownBy(() -> counter.handle(new CounterEvent.Faulty())).hasMessage("boom");

        assertThat(counter.id()).isEqualTo("c-1");
        assertThat(counter.version()).isEqualTo(-1);
        assertThat(counter.uncommittedEvents()).isEmpty();
    }

    @Test
    void uncommittedEventsIsAnUnmodifiableSnapshot() {
        var counter = new Counter("c-1");
        counter.handle(new CounterEvent.Incremented());

        var events = counter.uncommittedEvents();
        counter.handle(new CounterEvent.Incremented());

        assertThat(events).hasSize(1);
        assertThatThrownBy(() -> events.add(new CounterEvent.Incremented()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void emptyHistoryLeavesAggregateNew() {
        var counter = new Counter("c-1");

        counter.rehydrate(List.of());

        assertThat(counter.id()).isEqualTo("c-1");
        assertThat(counter.version()).isEqualTo(-1);
    }

    @Test
    void cannotRehydrateAggregateWithState() {
        var counter = new Counter("c-1");
        counter.handle(new CounterEvent.Incremented());
        counter.markCommitted();

        assertThatThrownBy(() -> counter.rehydrate(List.of(new CounterEvent.Incremented())))
                .isInstanceOf(IllegalStateException.class);
        assertThat(counter.count).isEqualTo(1);
        assertThat(counter.version()).isZero();
    }

    @Test
    void cannotRehydrateAggregateWithUncommittedEvents() {
        var counter = new Counter("c-1");
        counter.handle(new CounterEvent.Incremented());

        assertThatThrownBy(() -> counter.rehydrate(List.of(new CounterEvent.Incremented())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void cannotRehydrateTwice() {
        var counter = new Counter("c-1");
        counter.rehydrate(List.of(new CounterEvent.Incremented()));

        assertThatThrownBy(() -> counter.rehydrate(List.of(new CounterEvent.Incremented())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNulls() {
        var counter = new Counter("c-1");

        assertThatThrownBy(() -> counter.handle(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> counter.rehydrate(null)).isInstanceOf(NullPointerException.class);
        assertThat(counter.version()).isEqualTo(-1);
    }
}
